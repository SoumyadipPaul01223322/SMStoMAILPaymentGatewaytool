package com.appmine.bankmail.worker

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.appmine.bankmail.BankMailApp
import com.appmine.bankmail.MainActivity
import com.appmine.bankmail.R
import com.appmine.bankmail.data.local.DeliveryStatus
import com.appmine.bankmail.mail.SmtpCredentials
import com.appmine.bankmail.mail.SmtpSendResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reliable background worker queued by WorkManager whenever an Indian Bank
 * transaction SMS is detected. Runs only when network is available and retries
 * transient failures with exponential backoff.
 */
class ForwardTransactionWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notification = NotificationCompat.Builder(applicationContext, BankMailApp.MONITOR_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("BankMail")
            .setContentText("Forwarding Indian Bank transaction to Gmail…")
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ForegroundInfo(
                EXPEDITED_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            ForegroundInfo(EXPEDITED_NOTIFICATION_ID, notification)
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val app = applicationContext as BankMailApp
        val repository = app.repository
        val transactionId = inputData.getLong(KEY_TRANSACTION_ID, -1L)
        if (transactionId <= 0L) {
            return@withContext Result.failure()
        }

        val entry = repository.getTransactionById(transactionId) ?: return@withContext Result.success()
        if (entry.status == DeliveryStatus.SENT) {
            return@withContext Result.success()
        }

        val config = repository.getConfigOrDefault()
        if (!config.isConfigured) {
            repository.updateTransactionState(
                id = entry.id,
                status = DeliveryStatus.FAILED,
                errorMessage = "Gmail and Google App Password are not saved in Settings yet.",
                sentAt = null,
                retryCount = entry.retryCount + 1
            )
            notifyFailure(entry.id, "Configure your Gmail & App Password in Settings to forward this transaction.")
            return@withContext Result.failure()
        }

        val decryptedPassword = try {
            repository.decryptAppPassword(config.encryptedAppPassword)
        } catch (e: Exception) {
            repository.updateTransactionState(
                id = entry.id,
                status = DeliveryStatus.FAILED,
                errorMessage = "Could not decrypt stored App Password. Please re-save it in Settings.",
                sentAt = null,
                retryCount = entry.retryCount + 1
            )
            return@withContext Result.failure()
        }

        repository.updateTransactionState(
            id = entry.id,
            status = DeliveryStatus.SENDING,
            errorMessage = null,
            sentAt = null,
            retryCount = entry.retryCount
        )

        val verificationKey = runCatching {
            repository.decryptSecret(config.encryptedVerificationKey)
        }.getOrDefault("")

        val credentials = SmtpCredentials(
            senderGmail = config.senderGmail,
            appPassword = decryptedPassword,
            recipientGmail = config.effectiveRecipient,
            verificationKey = verificationKey
        )

        when (val result = app.smtpClient.sendTransactionEmail(credentials, config.subjectPrefix, entry)) {
            is SmtpSendResult.Success -> {
                repository.updateTransactionState(
                    id = entry.id,
                    status = DeliveryStatus.SENT,
                    errorMessage = null,
                    sentAt = System.currentTimeMillis(),
                    retryCount = entry.retryCount + 1
                )
                Result.success()
            }

            is SmtpSendResult.AuthError -> {
                repository.updateTransactionState(
                    id = entry.id,
                    status = DeliveryStatus.FAILED,
                    errorMessage = result.message,
                    sentAt = null,
                    retryCount = entry.retryCount + 1
                )
                notifyFailure(entry.id, result.message)
                Result.failure()
            }

            is SmtpSendResult.TransientError -> {
                val nextAttempt = entry.retryCount + 1
                if (runAttemptCount < MAX_AUTO_RETRIES) {
                    repository.updateTransactionState(
                        id = entry.id,
                        status = DeliveryStatus.PENDING,
                        errorMessage = "Retrying: ${result.message}",
                        sentAt = null,
                        retryCount = nextAttempt
                    )
                    Result.retry()
                } else {
                    repository.updateTransactionState(
                        id = entry.id,
                        status = DeliveryStatus.FAILED,
                        errorMessage = result.message,
                        sentAt = null,
                        retryCount = nextAttempt
                    )
                    notifyFailure(entry.id, "Failed to forward ${entry.amount ?: "transaction"}: ${result.message}")
                    Result.failure()
                }
            }
        }
    }

    private fun notifyFailure(transactionId: Long, message: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val openIntent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            transactionId.toInt(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, BankMailApp.ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("BankMail could not forward SMS")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        runCatching {
            NotificationManagerCompat.from(applicationContext)
                .notify((NOTIFICATION_BASE_ID + (transactionId % 1000)).toInt(), notification)
        }
    }

    companion object {
        const val KEY_TRANSACTION_ID = "transaction_id"
        private const val MAX_AUTO_RETRIES = 4
        private const val EXPEDITED_NOTIFICATION_ID = 4099
        private const val NOTIFICATION_BASE_ID = 4100
    }
}
