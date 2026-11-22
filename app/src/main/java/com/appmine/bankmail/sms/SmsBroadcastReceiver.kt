package com.appmine.bankmail.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.appmine.bankmail.BankMailApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Listens for incoming SMS broadcasts on-device, reassembles multi-part bank SMS
 * messages by sender, evaluates them against Indian Bank transaction rules,
 * persists matched transactions to Room DB, and schedules WorkManager forwarding.
 */
class SmsBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        // Group parts by originating sender so long multi-part SMS messages stay intact
        val groupedBySender = LinkedHashMap<String, StringBuilder>()
        val timestampsBySender = HashMap<String, Long>()

        for (sms in messages) {
            val sender = sms.displayOriginatingAddress?.trim().orEmpty()
            val body = sms.displayMessageBody.orEmpty()
            if (sender.isEmpty() && body.isEmpty()) continue
            groupedBySender.getOrPut(sender) { StringBuilder() }.append(body)
            timestampsBySender.putIfAbsent(
                sender,
                sms.timestampMillis.takeIf { it > 0L } ?: System.currentTimeMillis()
            )
        }

        if (groupedBySender.isEmpty()) return

        val pendingResult = goAsync()
        val app = context.applicationContext as BankMailApp

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                for ((sender, bodyBuilder) in groupedBySender) {
                    val timestamp = timestampsBySender[sender] ?: System.currentTimeMillis()
                    app.repository.processIncomingSms(
                        sender = sender,
                        body = bodyBuilder.toString(),
                        receivedAt = timestamp,
                        isSimulated = false
                    )
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
