package com.appmine.bankmail.data.repository

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.appmine.bankmail.data.local.BankMailDatabase
import com.appmine.bankmail.data.local.DeliveryStatus
import com.appmine.bankmail.data.local.GmailConfigEntity
import com.appmine.bankmail.data.local.TransactionLogEntity
import com.appmine.bankmail.domain.IndianBankSmsParser
import com.appmine.bankmail.domain.SmsEvaluation
import com.appmine.bankmail.mail.GmailSmtpClient
import com.appmine.bankmail.mail.SmtpCredentials
import com.appmine.bankmail.mail.SmtpSendResult
import com.appmine.bankmail.security.CryptoManager
import com.appmine.bankmail.service.BankMailMonitorService
import com.appmine.bankmail.worker.ForwardTransactionWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

sealed interface ProcessSmsOutcome {
    data class Queued(val transactionId: Long, val amount: String?, val direction: String) : ProcessSmsOutcome
    data class Duplicate(val reason: String) : ProcessSmsOutcome
    data class Ignored(val reason: String) : ProcessSmsOutcome
    data object Paused : ProcessSmsOutcome
}

class BankMailRepository(
    private val context: Context,
    private val database: BankMailDatabase,
    private val cryptoManager: CryptoManager,
    private val smtpClient: GmailSmtpClient
) {
    private val configDao = database.gmailConfigDao()
    private val transactionDao = database.transactionLogDao()
    private val workManager = WorkManager.getInstance(context)

    fun observeConfig(): Flow<GmailConfigEntity> =
        configDao.observeConfig().map { it ?: GmailConfigEntity() }

    fun observeTransactions(): Flow<List<TransactionLogEntity>> =
        transactionDao.observeAll()

    suspend fun getConfigOrDefault(): GmailConfigEntity =
        configDao.getConfig() ?: GmailConfigEntity()

    suspend fun getTransactionById(id: Long): TransactionLogEntity? =
        transactionDao.getById(id)

    fun decryptSecret(encrypted: String): String =
        if (encrypted.isBlank()) "" else cryptoManager.decrypt(encrypted)

    fun decryptAppPassword(encrypted: String): String = decryptSecret(encrypted)

    fun generateVerificationKey(): String {
        val bytes = ByteArray(12)
        SecureRandom().nextBytes(bytes)
        val hex = bytes.joinToString("") { "%02X".format(it) }
        return "BMK-$hex"
    }

    suspend fun saveConfig(
        senderGmail: String,
        plainAppPasswordInput: String,
        recipientGmail: String,
        subjectPrefix: String,
        senderKeywords: String,
        blockOtps: Boolean,
        forwardingEnabled: Boolean,
        verificationKeyInput: String
    ): GmailConfigEntity = withContext(Dispatchers.IO) {
        val existing = configDao.getConfig() ?: GmailConfigEntity()
        val cleanedPassword = plainAppPasswordInput.replace("\\s+".toRegex(), "")
        val encryptedPassword = if (cleanedPassword.isNotEmpty()) {
            cryptoManager.encrypt(cleanedPassword)
        } else {
            existing.encryptedAppPassword
        }

        val cleanedKey = verificationKeyInput.trim().ifEmpty {
            existing.encryptedVerificationKey
                .takeIf { it.isNotBlank() }
                ?.let { runCatching { cryptoManager.decrypt(it) }.getOrDefault("") }
                ?.ifBlank { generateVerificationKey() }
                ?: generateVerificationKey()
        }
        val encryptedKey = cryptoManager.encrypt(cleanedKey)

        val updated = GmailConfigEntity(
            id = GmailConfigEntity.SINGLETON_ID,
            senderGmail = senderGmail.trim(),
            encryptedAppPassword = encryptedPassword,
            recipientGmail = recipientGmail.trim(),
            subjectPrefix = subjectPrefix.trim().ifEmpty { GmailConfigEntity.DEFAULT_SUBJECT_PREFIX },
            senderKeywords = senderKeywords.trim().ifEmpty { GmailConfigEntity.DEFAULT_SENDER_KEYWORDS },
            blockOtps = blockOtps,
            forwardingEnabled = forwardingEnabled,
            encryptedVerificationKey = encryptedKey,
            updatedAt = System.currentTimeMillis()
        )
        configDao.upsertConfig(updated)
        BankMailMonitorService.syncState(context, updated.forwardingEnabled)
        updated
    }

    suspend fun setForwardingEnabled(enabled: Boolean) = withContext(Dispatchers.IO) {
        val existing = configDao.getConfig()
        if (existing == null) {
            configDao.upsertConfig(GmailConfigEntity(forwardingEnabled = enabled))
        } else {
            configDao.updateForwardingEnabled(enabled)
        }
        BankMailMonitorService.syncState(context, enabled)
    }

    suspend fun sendTestEmail(
        senderGmailOverride: String,
        plainPasswordOverride: String,
        recipientGmailOverride: String,
        subjectPrefixOverride: String,
        verificationKeyOverride: String
    ): SmtpSendResult = withContext(Dispatchers.IO) {
        val saved = getConfigOrDefault()
        val effectiveSender = senderGmailOverride.trim().ifEmpty { saved.senderGmail }
        val effectiveRecipient = recipientGmailOverride.trim().ifEmpty {
            saved.recipientGmail.ifBlank { effectiveSender }
        }
        val cleanedInputPassword = plainPasswordOverride.replace("\\s+".toRegex(), "")
        val effectivePassword = if (cleanedInputPassword.isNotEmpty()) {
            cleanedInputPassword
        } else if (saved.encryptedAppPassword.isNotEmpty()) {
            runCatching { cryptoManager.decrypt(saved.encryptedAppPassword) }.getOrDefault("")
        } else {
            ""
        }
        val effectiveVerifyKey = verificationKeyOverride.trim().ifEmpty {
            runCatching { decryptSecret(saved.encryptedVerificationKey) }.getOrDefault("")
        }

        if (effectiveSender.isBlank()) {
            return@withContext SmtpSendResult.AuthError("Enter your Gmail address first.")
        }
        if (effectivePassword.isBlank()) {
            return@withContext SmtpSendResult.AuthError("Enter your 16-character Google App Password first.")
        }

        smtpClient.sendTestEmail(
            credentials = SmtpCredentials(
                senderGmail = effectiveSender,
                appPassword = effectivePassword,
                recipientGmail = effectiveRecipient,
                verificationKey = effectiveVerifyKey
            ),
            subjectPrefix = subjectPrefixOverride.ifBlank { saved.subjectPrefix }
        )
    }

    suspend fun processIncomingSms(
        sender: String,
        body: String,
        receivedAt: Long = System.currentTimeMillis(),
        isSimulated: Boolean = false
    ): ProcessSmsOutcome = withContext(Dispatchers.IO) {
        val config = getConfigOrDefault()
        if (!isSimulated && !config.forwardingEnabled) {
            return@withContext ProcessSmsOutcome.Paused
        }

        val evaluation = IndianBankSmsParser.evaluate(
            sender = sender,
            body = body,
            senderKeywords = config.parsedSenderKeywords(),
            blockOtps = config.blockOtps
        )

        when (evaluation) {
            is SmsEvaluation.Ignored -> ProcessSmsOutcome.Ignored(evaluation.reason)
            is SmsEvaluation.Matched -> {
                val parsed = evaluation.parsed
                if (!isSimulated) {
                    val fiveMinutesAgo = receivedAt - 5 * 60_000L
                    if (transactionDao.countRecentDuplicate(parsed.body, fiveMinutesAgo) > 0) {
                        return@withContext ProcessSmsOutcome.Duplicate("Already captured this SMS")
                    }
                }
                val fingerprint = if (isSimulated) {
                    IndianBankSmsParser.fingerprint(sender, "$body|$receivedAt", receivedAt)
                } else {
                    IndianBankSmsParser.fingerprint(sender, body, receivedAt)
                }
                val entry = TransactionLogEntity(
                    smsSender = parsed.sender,
                    smsBody = parsed.body,
                    receivedAt = receivedAt,
                    direction = parsed.direction,
                    amount = parsed.amount,
                    accountMask = parsed.accountMask,
                    paymentMode = parsed.paymentMode,
                    referenceNumber = parsed.referenceNumber,
                    availableBalance = parsed.availableBalance,
                    status = DeliveryStatus.PENDING,
                    fingerprint = fingerprint,
                    isSimulated = isSimulated
                )
                val insertedId = transactionDao.insertIfNew(entry)
                if (insertedId == -1L) {
                    return@withContext ProcessSmsOutcome.Duplicate("Already captured this SMS")
                }
                enqueueForwardingWork(insertedId)
                ProcessSmsOutcome.Queued(insertedId, parsed.amount, parsed.direction)
            }
        }
    }

    suspend fun retryTransaction(transactionId: Long) = withContext(Dispatchers.IO) {
        val existing = transactionDao.getById(transactionId) ?: return@withContext
        transactionDao.updateDeliveryState(
            id = existing.id,
            status = DeliveryStatus.PENDING,
            errorMessage = null,
            sentAt = null,
            retryCount = existing.retryCount
        )
        enqueueForwardingWork(existing.id)
    }

    suspend fun retryAllPendingOrFailed(): Int = withContext(Dispatchers.IO) {
        val items = transactionDao.getRetryable()
        items.forEach { item ->
            transactionDao.updateDeliveryState(
                id = item.id,
                status = DeliveryStatus.PENDING,
                errorMessage = null,
                sentAt = null,
                retryCount = item.retryCount
            )
            enqueueForwardingWork(item.id)
        }
        items.size
    }

    suspend fun updateTransactionState(
        id: Long,
        status: String,
        errorMessage: String?,
        sentAt: Long?,
        retryCount: Int
    ) = withContext(Dispatchers.IO) {
        transactionDao.updateDeliveryState(id, status, errorMessage, sentAt, retryCount)
    }

    suspend fun deleteTransaction(id: Long) = withContext(Dispatchers.IO) {
        transactionDao.deleteById(id)
    }

    suspend fun clearAllTransactions() = withContext(Dispatchers.IO) {
        transactionDao.clearAll()
    }

    private fun enqueueForwardingWork(transactionId: Long) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<ForwardTransactionWorker>()
            .setInputData(workDataOf(ForwardTransactionWorker.KEY_TRANSACTION_ID to transactionId))
            .setConstraints(constraints)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        workManager.enqueueUniqueWork(
            "forward_txn_$transactionId",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}
