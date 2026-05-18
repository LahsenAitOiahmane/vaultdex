package com.vaultdex.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * VULN-B: Transaction history stored unencrypted in the same SQLite database.
 * An attacker with device access can read full transaction history including
 * amounts, account numbers, and descriptions — all in plaintext.
 */
@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val type: String,               // "credit" or "debit" — VULN-B
    val amount: Double,             // Transaction amount in plaintext — VULN-B
    val description: String,        // e.g. "Transfer to ACC-***4521" — VULN-B
    val recipientAccount: String,   // Full account number stored unencrypted — VULN-B
    val timestamp: Long = System.currentTimeMillis()
)
