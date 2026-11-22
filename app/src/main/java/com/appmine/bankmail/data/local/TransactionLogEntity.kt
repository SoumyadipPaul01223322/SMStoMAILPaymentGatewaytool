package com.appmine.bankmail.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

object DeliveryStatus {
    const val PENDING = "PENDING"
    const val SENDING = "SENDING"
    const val SENT = "SENT"
    const val FAILED = "FAILED"
}

object TransactionDirection {
    const val DEBIT = "DEBIT"
    const val CREDIT = "CREDIT"
    const val OTHER = "OTHER"
}

@Entity(
    tableName = "transaction_logs",
    indices = [
        Index(value = ["fingerprint"], unique = true),
        Index(value = ["receivedAt"])
    ]
)
data class TransactionLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val smsSender: String,
    val smsBody: String,
    val receivedAt: Long,
    val direction: String,
    val amount: String?,
    val accountMask: String?,
    val paymentMode: String?,
    val referenceNumber: String?,
    val availableBalance: String?,
    val status: String = DeliveryStatus.PENDING,
    val errorMessage: String? = null,
    val sentAt: Long? = null,
    val retryCount: Int = 0,
    val fingerprint: String,
    val isSimulated: Boolean = false
)
