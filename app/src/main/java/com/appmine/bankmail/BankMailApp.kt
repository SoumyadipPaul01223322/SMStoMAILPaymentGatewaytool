package com.appmine.bankmail

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.appmine.bankmail.data.local.BankMailDatabase
import com.appmine.bankmail.data.repository.BankMailRepository
import com.appmine.bankmail.mail.GmailSmtpClient
import com.appmine.bankmail.security.CryptoManager

class BankMailApp : Application() {

    lateinit var database: BankMailDatabase
        private set

    lateinit var cryptoManager: CryptoManager
        private set

    lateinit var smtpClient: GmailSmtpClient
        private set

    lateinit var repository: BankMailRepository
        private set

    override fun onCreate() {
        super.onCreate()
        database = BankMailDatabase.getInstance(this)
        cryptoManager = CryptoManager()
        smtpClient = GmailSmtpClient()
        repository = BankMailRepository(
            context = this,
            database = database,
            cryptoManager = cryptoManager,
            smtpClient = smtpClient
        )
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            val alertChannel = NotificationChannel(
                ALERT_CHANNEL_ID,
                "Forwarding Alerts",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Alerts when an Indian Bank transaction SMS cannot be emailed."
            }

            val monitorChannel = NotificationChannel(
                MONITOR_CHANNEL_ID,
                "Background SMS Monitor",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps BankMail listening for Indian Bank SMS even when closed from Recents."
                setShowBadge(false)
            }

            manager.createNotificationChannels(listOf(alertChannel, monitorChannel))
        }
    }

    companion object {
        const val ALERT_CHANNEL_ID = "bankmail_forwarding_alerts"
        const val MONITOR_CHANNEL_ID = "bankmail_background_monitor"
    }
}
