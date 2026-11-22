package com.appmine.bankmail.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [GmailConfigEntity::class, TransactionLogEntity::class],
    version = 2,
    exportSchema = false
)
abstract class BankMailDatabase : RoomDatabase() {
    abstract fun gmailConfigDao(): GmailConfigDao
    abstract fun transactionLogDao(): TransactionLogDao

    companion object {
        private const val DB_NAME = "bankmail_local.db"

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE gmail_config ADD COLUMN encryptedVerificationKey TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        @Volatile
        private var instance: BankMailDatabase? = null

        fun getInstance(context: Context): BankMailDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    BankMailDatabase::class.java,
                    DB_NAME
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
