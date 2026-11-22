package com.appmine.bankmail.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Single-row configuration stored in the local Room SQLite database.
 * [encryptedAppPassword] is encrypted via Android Keystore AES-256-GCM.
 */
@Entity(tableName = "gmail_config")
data class GmailConfigEntity(
    @PrimaryKey
    val id: Int = SINGLETON_ID,
    val senderGmail: String = "",
    val encryptedAppPassword: String = "",
    val recipientGmail: String = "",
    val subjectPrefix: String = DEFAULT_SUBJECT_PREFIX,
    val senderKeywords: String = DEFAULT_SENDER_KEYWORDS,
    val blockOtps: Boolean = true,
    val forwardingEnabled: Boolean = true,
    val encryptedVerificationKey: String = "",
    val updatedAt: Long = System.currentTimeMillis()
) {
    val isConfigured: Boolean
        get() = senderGmail.isNotBlank() && encryptedAppPassword.isNotBlank()

    val effectiveRecipient: String
        get() = recipientGmail.ifBlank { senderGmail }

    fun parsedSenderKeywords(): List<String> =
        senderKeywords.split(",", ";", "\n")
            .map { it.trim().uppercase() }
            .filter { it.isNotEmpty() }

    companion object {
        const val SINGLETON_ID = 1
        const val DEFAULT_SUBJECT_PREFIX = "[Indian Bank]"
        const val DEFAULT_SENDER_KEYWORDS = "INDBNK,INDIBK,INDBK,INDIANBANK,IBANK"
    }
}
