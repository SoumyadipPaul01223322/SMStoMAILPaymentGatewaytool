package com.appmine.bankmail.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.appmine.bankmail.BankMailApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Restarts [BankMailMonitorService] automatically after a device reboot
 * or app update whenever forwarding is enabled.
 */
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (
            action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED &&
            action != "android.intent.action.QUICKBOOT_POWERON"
        ) {
            return
        }

        val pendingResult = goAsync()
        val app = context.applicationContext as BankMailApp

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val config = app.repository.getConfigOrDefault()
                BankMailMonitorService.syncState(context.applicationContext, config.forwardingEnabled)
                app.repository.retryAllPendingOrFailed()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
