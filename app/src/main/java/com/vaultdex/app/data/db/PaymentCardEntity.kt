package com.vaultdex.app.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * VULN-CC: Full credit card details stored in unencrypted SQLite database.
 *
 * Real-world vulnerability: Payment and banking apps sometimes cache card details
 * locally for "quick checkout" features. Storing full PAN, CVV, and expiry in
 * an unencrypted database is a PCI-DSS violation and critical security flaw.
 *
 * An attacker can pull vault_data.db and extract all card numbers with:
 *   SELECT card_number, cvv, expiry, cardholder_name FROM payment_cards
 */
@Entity(tableName = "payment_cards")
data class PaymentCardEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val cardholderName: String,       // VULN: Full name in plaintext
    @ColumnInfo(name = "card_number")
    val cardNumber: String,            // VULN: Full PAN (Primary Account Number) — CRITICAL
    val cvv: String,                   // VULN: CVV stored on device — PCI-DSS violation
    val expiry: String,                // VULN: Expiration date
    val cardType: String,              // Visa, Mastercard, Amex
    val billingAddress: String,        // VULN: Full address in plaintext
    val isDefault: Boolean = false
)

@Dao
interface PaymentCardDao {
    @Query("SELECT * FROM payment_cards ORDER BY isDefault DESC")
    fun getAllCards(): Flow<List<PaymentCardEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCard(card: PaymentCardEntity)

    @Query("DELETE FROM payment_cards")
    suspend fun deleteAll()
}
