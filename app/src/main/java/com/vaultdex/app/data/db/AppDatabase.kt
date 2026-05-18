package com.vaultdex.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * VULN-B (Core): The entire database is completely unencrypted.
 *
 * Real-world vulnerability: Developers often use Room's default configuration,
 * meaning data is written directly to an SQLite file (`vault_data.db`). Anyone
 * with physical access (rooted device) or logical access (ADB backup) can open
 * this file using any SQLite viewer.
 *
 * It should use SQLCipher (`net.zetetic:android-database-sqlcipher`) with a key
 * derived from the Android KeyStore.
 */
@Database(
    entities = [
        NoteEntity::class,
        TransactionEntity::class,
        PaymentCardEntity::class,
        MessageEntity::class,
        HealthRecordEntity::class,
        LocationHistoryEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun noteDao(): NoteDao
    abstract fun transactionDao(): TransactionDao
    abstract fun paymentCardDao(): PaymentCardDao
    abstract fun messageDao(): MessageDao
    abstract fun healthRecordDao(): HealthRecordDao
    abstract fun locationHistoryDao(): LocationHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                // VULN-B: Plain databaseBuilder — no encryption applied
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "vault_data.db"   // Plaintext database name — visible on disk
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
