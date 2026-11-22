package com.appmine.bankmail.service

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Telephony
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.appmine.bankmail.BankMailApp
import com.appmine.bankmail.MainActivity
import com.appmine.bankmail.R
import com.appmine.bankmail.sms.SmsBroadcastReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Persistent foreground service that keeps BankMail alive even when the user
 * swipes the app away from Recents on aggressive OEM ROMs (Xiaomi HyperOS/MIUI,
 * Realme/Oppo/OnePlus ColorOS, Vivo, Samsung).
 *
 * Uses TWO complementary SMS detection channels (deduplicated in Room DB):
 * 1. Runtime-registered [SmsBroadcastReceiver] for immediate SMS_RECEIVED broadcasts.
 * 2. [ContentObserver] on `content://sms` to catch new inbox messages even if an OEM
 *    throttles broadcast receivers.
 */
class BankMailMonitorService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var dynamicSmsReceiver: SmsBroadcastReceiver? = null
    private var smsObserver: SmsInboxObserver? = null

    @Volatile
    private var lastProcessedSmsId: Long = -1L

    override fun onCreate() {
        super.onCreate()
        _isRunning.value = true
        promoteToForeground()
        registerDynamicSmsReceiver()
        registerSmsInboxObserver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            _isRunning.value = false
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        _isRunning.value = true
        promoteToForeground()
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // Re-assert foreground state if the user swiped the app from Recents
        val app = applicationContext as BankMailApp
        serviceScope.launch {
            val config = app.repository.getConfigOrDefault()
            if (config.forwardingEnabled && hasSmsPermission(applicationContext)) {
                start(applicationContext)
            }
        }
    }

    override fun onDestroy() {
        _isRunning.value = false
        unregisterDynamicSmsReceiver()
        unregisterSmsInboxObserver()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun promoteToForeground() {
        val notification = buildMonitorNotification()
        val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        runCatching {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, fgsType)
        }
    }

    private fun buildMonitorNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, BankMailApp.MONITOR_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("BankMail Active")
            .setContentText("Watching incoming Indian Bank SMS in background")
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun registerDynamicSmsReceiver() {
        if (dynamicSmsReceiver != null) return
        val receiver = SmsBroadcastReceiver()
        val filter = IntentFilter(Telephony.Sms.Intents.SMS_RECEIVED_ACTION).apply {
            priority = 999
        }
        runCatching {
            ContextCompat.registerReceiver(
                this,
                receiver,
                filter,
                Manifest.permission.BROADCAST_SMS,
                null,
                ContextCompat.RECEIVER_EXPORTED
            )
            dynamicSmsReceiver = receiver
        }
    }

    private fun unregisterDynamicSmsReceiver() {
        dynamicSmsReceiver?.let { receiver ->
            runCatching { unregisterReceiver(receiver) }
        }
        dynamicSmsReceiver = null
    }

    private fun registerSmsInboxObserver() {
        if (smsObserver != null || !hasReadSmsPermission(this)) return
        // Record the highest existing inbox SMS id so historical messages are never re-forwarded
        serviceScope.launch {
            lastProcessedSmsId = queryLatestInboxSmsId()
        }
        val observer = SmsInboxObserver(Handler(Looper.getMainLooper()))
        runCatching {
            contentResolver.registerContentObserver(
                Telephony.Sms.CONTENT_URI,
                true,
                observer
            )
            smsObserver = observer
        }
    }

    private fun unregisterSmsInboxObserver() {
        smsObserver?.let { observer ->
            runCatching { contentResolver.unregisterContentObserver(observer) }
        }
        smsObserver = null
    }

    private inner class SmsInboxObserver(handler: Handler) : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            super.onChange(selfChange, uri)
            if (!hasReadSmsPermission(this@BankMailMonitorService)) return
            serviceScope.launch {
                scanNewInboxMessages()
            }
        }
    }

    private fun queryLatestInboxSmsId(): Long {
        if (!hasReadSmsPermission(this)) return -1L
        return runCatching {
            contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf(Telephony.Sms._ID),
                null,
                null,
                "${Telephony.Sms._ID} DESC LIMIT 1"
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getLong(0) else 0L
            } ?: 0L
        }.getOrDefault(0L)
    }

    private suspend fun scanNewInboxMessages() {
        val currentWatermark = lastProcessedSmsId
        if (currentWatermark < 0L) {
            lastProcessedSmsId = queryLatestInboxSmsId()
            return
        }

        val app = applicationContext as BankMailApp
        val newMessages = mutableListOf<Triple<Long, Pair<String, String>, Long>>()

        runCatching {
            contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf(
                    Telephony.Sms._ID,
                    Telephony.Sms.ADDRESS,
                    Telephony.Sms.BODY,
                    Telephony.Sms.DATE
                ),
                "${Telephony.Sms._ID} > ?",
                arrayOf(currentWatermark.toString()),
                "${Telephony.Sms._ID} ASC"
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(0)
                    val address = cursor.getString(1).orEmpty()
                    val body = cursor.getString(2).orEmpty()
                    val date = cursor.getLong(3).takeIf { it > 0L } ?: System.currentTimeMillis()
                    newMessages.add(Triple(id, address to body, date))
                }
            }
        }

        for ((id, senderAndBody, date) in newMessages) {
            if (id > lastProcessedSmsId) {
                lastProcessedSmsId = id
            }
            val (sender, body) = senderAndBody
            app.repository.processIncomingSms(
                sender = sender,
                body = body,
                receivedAt = date,
                isSimulated = false
            )
        }
    }

    companion object {
        private const val NOTIFICATION_ID = 4001
        private const val ACTION_START = "com.appmine.bankmail.action.START_MONITOR"
        private const val ACTION_STOP = "com.appmine.bankmail.action.STOP_MONITOR"

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        fun hasSmsPermission(context: Context): Boolean =
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECEIVE_SMS
            ) == PackageManager.PERMISSION_GRANTED

        fun hasReadSmsPermission(context: Context): Boolean =
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_SMS
            ) == PackageManager.PERMISSION_GRANTED

        fun start(context: Context) {
            if (!hasSmsPermission(context)) return
            val intent = Intent(context, BankMailMonitorService::class.java).apply {
                action = ACTION_START
            }
            runCatching {
                ContextCompat.startForegroundService(context, intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, BankMailMonitorService::class.java).apply {
                action = ACTION_STOP
            }
            runCatching {
                context.stopService(intent)
            }
        }

        fun syncState(context: Context, forwardingEnabled: Boolean) {
            if (forwardingEnabled && hasSmsPermission(context)) {
                start(context)
            } else {
                stop(context)
            }
        }
    }
}
