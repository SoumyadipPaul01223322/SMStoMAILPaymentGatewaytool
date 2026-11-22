package com.appmine.bankmail.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface GmailConfigDao {
    @Query("SELECT * FROM gmail_config WHERE id = 1 LIMIT 1")
    fun observeConfig(): Flow<GmailConfigEntity?>

    @Query("SELECT * FROM gmail_config WHERE id = 1 LIMIT 1")
    suspend fun getConfig(): GmailConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConfig(config: GmailConfigEntity)

    @Query("UPDATE gmail_config SET forwardingEnabled = :enabled, updatedAt = :updatedAt WHERE id = 1")
    suspend fun updateForwardingEnabled(enabled: Boolean, updatedAt: Long = System.currentTimeMillis())
}

@Dao
interface TransactionLogDao {
    @Query("SELECT * FROM transaction_logs ORDER BY receivedAt DESC, id DESC")
    fun observeAll(): Flow<List<TransactionLogEntity>>

    @Query("SELECT * FROM transaction_logs WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): TransactionLogEntity?

    @Query("SELECT * FROM transaction_logs WHERE status IN ('PENDING', 'FAILED') ORDER BY receivedAt ASC")
    suspend fun getRetryable(): List<TransactionLogEntity>

    @Query(
        """
        SELECT COUNT(*) FROM transaction_logs
        WHERE smsBody = :smsBody
          AND isSimulated = 0
          AND receivedAt >= :sinceMillis
        """
    )
    suspend fun countRecentDuplicate(smsBody: String, sinceMillis: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfNew(entry: TransactionLogEntity): Long

    @Query(
        """
        UPDATE transaction_logs
        SET status = :status,
            errorMessage = :errorMessage,
            sentAt = :sentAt,
            retryCount = :retryCount
        WHERE id = :id
        """
    )
    suspend fun updateDeliveryState(
        id: Long,
        status: String,
        errorMessage: String?,
        sentAt: Long?,
        retryCount: Int
    )

    @Query("DELETE FROM transaction_logs WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM transaction_logs")
    suspend fun clearAll()
}
