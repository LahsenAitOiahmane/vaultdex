package com.vaultdex.app.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * VULN-MSG: Private messages stored in unencrypted database.
 *
 * Real-world vulnerability: Messaging/chat apps that cache messages locally
 * without encryption expose private communications. Sensitive data includes
 * phone numbers, addresses, and personal discussions.
 */
@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val senderPhone: String,          // VULN: Phone number in plaintext
    val recipientPhone: String,       // VULN: Phone number in plaintext
    val messageBody: String,          // VULN: Message content (may contain PII, passwords shared)
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false
)

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages ORDER BY timestamp DESC")
    fun getAllMessages(): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Query("DELETE FROM messages")
    suspend fun deleteAll()
}
