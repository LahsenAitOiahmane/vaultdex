package com.vaultdex.app.data.backup

import android.content.Context
import com.google.gson.GsonBuilder
import com.vaultdex.app.data.cache.ProfileCacheManager
import com.vaultdex.app.data.db.NoteEntity
import com.vaultdex.app.data.db.TransactionEntity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * VULN-C (Advanced): Account backup written as plaintext JSON to internal storage.
 *
 * The backup file is written to:
 *   /data/data/com.vaultdex.app/files/backup/account_backup_YYYYMMDD_HHmmss.json
 *
 * The JSON contains ALL sensitive data in plaintext:
 *   - username, password, session token (from SharedPreferences)
 *   - PIN (from pin_prefs)
 *   - All secret notes with titles and contents
 *   - Full transaction history with account numbers
 *   - Balance, card info, credit score (from profile cache)
 *
 * An attacker with adb access can extract this file with:
 *   adb shell run-as com.vaultdex.app ls files/backup/
 *   adb pull /data/data/com.vaultdex.app/files/backup/account_backup_*.json
 *
 * Secure alternative (NOT used here intentionally):
 *   Encrypt the backup with a user-derived key (PBKDF2) before writing to disk.
 */
class BackupManager(private val context: Context) {

    private val gson = GsonBuilder().setPrettyPrinting().create()

    // VULN-C: Backup stored in internal files/backup/ — no encryption
    private val backupDir: File
        get() {
            val dir = File(context.filesDir, "backup")
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

    data class AccountBackup(
        val backupVersion: String = "1.0",
        val exportedAt: String,
        // VULN-C: All credentials in plaintext
        val credentials: CredentialsBackup,
        val profile: ProfileBackup?,
        val notes: List<NoteBackup>,
        val transactions: List<TransactionBackup>
    )

    data class CredentialsBackup(
        val username: String,       // Plaintext — VULN-C
        val password: String,       // Plaintext password — VULN-C (CRITICAL)
        val sessionToken: String,   // Plaintext session token — VULN-C
        val accountNumber: String   // Full account number — VULN-C
    )

    data class ProfileBackup(
        val displayName: String,
        val email: String,
        val balance: Double,        // Financial data — VULN-C
        val cardNumber: String,     // Card number — VULN-C
        val cardExpiry: String,
        val creditScore: Int
    )

    data class NoteBackup(
        val title: String,
        val content: String,        // Secret note content — VULN-C
        val category: String
    )

    data class TransactionBackup(
        val type: String,
        val amount: Double,
        val description: String,
        val recipientAccount: String  // Full account number — VULN-C
    )

    /**
     * Creates a full plaintext JSON backup of all app data.
     * Returns the backup file path on success, or null on failure.
     */
    fun createBackup(
        username: String,
        password: String,
        sessionToken: String,
        accountNumber: String,
        cachedProfile: ProfileCacheManager.CachedProfile?,
        notes: List<NoteEntity>,
        transactions: List<TransactionEntity>
    ): String? {
        return try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val backupFile = File(backupDir, "account_backup_$timestamp.json")

            val backup = AccountBackup(
                exportedAt = timestamp,
                credentials = CredentialsBackup(
                    username      = username,     // VULN-C: Plaintext
                    password      = password,     // VULN-C: Plaintext password in backup!
                    sessionToken  = sessionToken, // VULN-C: Active session token
                    accountNumber = accountNumber
                ),
                profile = cachedProfile?.let {
                    ProfileBackup(
                        displayName = it.displayName,
                        email       = it.email,
                        balance     = it.balance,      // VULN-C: Financial data
                        cardNumber  = it.cardNumber,
                        cardExpiry  = it.cardExpiry,
                        creditScore = it.creditScore
                    )
                },
                notes = notes.map { note ->
                    NoteBackup(
                        title    = note.title,
                        content  = note.content,   // VULN-C: Secret note content
                        category = note.category
                    )
                },
                transactions = transactions.map { tx ->
                    TransactionBackup(
                        type             = tx.type,
                        amount           = tx.amount,
                        description      = tx.description,
                        recipientAccount = tx.recipientAccount  // VULN-C: Account numbers
                    )
                }
            )

            // VULN-C: Write full sensitive backup as plaintext JSON — no encryption
            val jsonContent = gson.toJson(backup)
            backupFile.writeText(jsonContent)

            backupFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun listBackups(): List<File> {
        return backupDir.listFiles()
            ?.filter { it.name.startsWith("account_backup_") && it.name.endsWith(".json") }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }
}
