package com.vaultdex.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * VULN-B: This entity is stored in a plain, unencrypted Room/SQLite database.
 * The database file (vault_data.db) can be pulled with:
 *   adb pull /data/data/com.vaultdex.app/databases/vault_data.db
 * and opened directly in DB Browser for SQLite — all note content is visible in plaintext.
 */
@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val title: String,          // Stored as plaintext — VULN-B
    val content: String,        // Stored as plaintext — VULN-B (e.g. passwords, secrets)
    val category: String,       // e.g. "Password", "Banking", "Personal"
    val createdAt: Long = System.currentTimeMillis()
)
