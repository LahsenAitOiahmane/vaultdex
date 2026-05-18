package com.vaultguard.framework.heuristics

import android.util.Base64

/**
 * Verifies encryption and KeyStore usage in discovered storage.
 *
 * Checks for:
 *  1. EncryptedSharedPreferences backing (AndroidX Security Crypto)
 *  2. Weak/static encryption indicators (short ciphertext, deterministic output)
 *  3. Hardcoded key patterns in string values
 *  4. Missing encryption on files that should be encrypted
 */
object CryptoVerifier {

    data class CryptoAssessment(
        val isEncrypted: Boolean,
        val encryptionType: EncryptionType,
        val confidence: Double,   // 0.0 to 1.0
        val details: String
    )

    enum class EncryptionType {
        NONE,                          // No encryption detected
        ANDROIDX_ENCRYPTED_PREFS,      // EncryptedSharedPreferences (secure)
        WEAK_CUSTOM_ENCRYPTION,        // Custom AES with suspected hardcoded key
        STRONG_ENCRYPTION,             // Properly encrypted (high entropy, no patterns)
        BASE64_ONLY,                   // Base64 encoded but NOT encrypted (obfuscation)
        UNKNOWN                        // Unclear
    }

    // Keys created by EncryptedSharedPreferences (AndroidX Security Crypto)
    private val ENCRYPTED_PREFS_MARKERS = listOf(
        "__androidx_security_crypto_encrypted_prefs_key_keyset__",
        "__androidx_security_crypto_encrypted_prefs_value_keyset__",
        "MasterKey"
    )

    // Patterns suggesting hardcoded encryption keys in source code
    private val HARDCODED_KEY_INDICATORS = listOf(
        "0123456789", "ABCDEFGHIJ", "abcdefghij",
        "secretkey", "SecretKey", "SECRET_KEY",
        "aeskey", "AESKey", "AES_KEY",
        "encryptionkey", "EncryptionKey",
        "masterkey", "MasterKey",
        "password123", "admin123", "default"
    )

    /**
     * Checks if a SharedPreferences file uses EncryptedSharedPreferences.
     * Looks for the characteristic keyset keys that AndroidX Security Crypto creates.
     *
     * @param allKeys Set of all key names in the SharedPreferences file
     * @return true if EncryptedSharedPreferences markers are found
     */
    fun isEncryptedSharedPreferences(allKeys: Set<String>): Boolean {
        return ENCRYPTED_PREFS_MARKERS.any { marker -> allKeys.contains(marker) }
    }

    /**
     * Assesses whether a stored string value appears to be properly encrypted,
     * weakly encrypted, or completely plaintext.
     */
    fun assessValue(value: String): CryptoAssessment {
        // Empty or very short values can't be meaningfully encrypted
        if (value.length < 4) {
            return CryptoAssessment(false, EncryptionType.NONE, 0.9, "Value too short to be encrypted")
        }

        // Check if it's just Base64-encoded (not encrypted)
        if (isBase64Only(value)) {
            val decoded = tryBase64Decode(value)
            if (decoded != null && decoded.all { it in 32..126 }) {
                return CryptoAssessment(
                    false, EncryptionType.BASE64_ONLY, 0.85,
                    "Base64 encoded but decodes to readable text — NOT encrypted, just obfuscated"
                )
            }
        }

        // High entropy check — potentially encrypted
        val entropy = EntropyAnalyzer.calculateEntropy(value)
        val classification = EntropyAnalyzer.classify(value)

        if (classification == EntropyAnalyzer.EntropyClassification.HIGH_ENTROPY) {
            // Could be properly encrypted or could be a raw secret stored directly
            // Check if it looks like valid Base64 (typical for AES output)
            if (isValidBase64(value) && value.length >= 20) {
                return CryptoAssessment(
                    true, EncryptionType.WEAK_CUSTOM_ENCRYPTION, 0.65,
                    "Value appears Base64-encoded with high entropy (%.2f bits/char) — likely custom AES encryption. Check source for hardcoded keys.".format(entropy)
                )
            }
            return CryptoAssessment(
                false, EncryptionType.NONE, 0.5,
                "High entropy value (%.2f bits/char) stored as plaintext — could be a raw secret/token".format(entropy)
            )
        }

        // Normal/low entropy — definitely not encrypted
        return CryptoAssessment(false, EncryptionType.NONE, 0.9, "Plaintext value (entropy: %.2f bits/char)".format(entropy))
    }

    /**
     * Scans a set of SharedPreferences keys and values for encryption indicators.
     * Returns a summary assessment for the entire preferences file.
     */
    fun assessPreferencesFile(entries: Map<String, Any?>): CryptoAssessment {
        val keys = entries.keys

        // Check for EncryptedSharedPreferences
        if (isEncryptedSharedPreferences(keys)) {
            return CryptoAssessment(
                true, EncryptionType.ANDROIDX_ENCRYPTED_PREFS, 0.95,
                "File uses EncryptedSharedPreferences (AndroidX Security Crypto) — keys and values are encrypted with AES256-SIV/AES256-GCM backed by Android KeyStore"
            )
        }

        // Check if all values look encrypted (uniformly high entropy)
        val stringValues = entries.values.filterIsInstance<String>().filter { it.length >= 8 }
        if (stringValues.isNotEmpty()) {
            val highEntropyCount = stringValues.count {
                EntropyAnalyzer.classify(it) == EntropyAnalyzer.EntropyClassification.HIGH_ENTROPY
            }
            val ratio = highEntropyCount.toDouble() / stringValues.size
            if (ratio > 0.8) {
                return CryptoAssessment(
                    true, EncryptionType.WEAK_CUSTOM_ENCRYPTION, 0.6,
                    "Most values (${(ratio * 100).toInt()}%) have high entropy — possible custom encryption. Verify key management."
                )
            }
        }

        return CryptoAssessment(false, EncryptionType.NONE, 0.85, "No encryption detected — values stored in plaintext")
    }

    /**
     * Checks if a string value looks like it could be a hardcoded encryption key
     * that was accidentally stored alongside encrypted data.
     */
    fun looksLikeHardcodedKey(value: String): Boolean {
        if (value.length < 8 || value.length > 64) return false
        return HARDCODED_KEY_INDICATORS.any { indicator ->
            value.contains(indicator, ignoreCase = true)
        }
    }

    private fun isBase64Only(value: String): Boolean {
        return value.matches(Regex("^[A-Za-z0-9+/]+=*$")) && value.length >= 4
    }

    private fun isValidBase64(value: String): Boolean {
        return try {
            Base64.decode(value, Base64.NO_WRAP)
            value.matches(Regex("^[A-Za-z0-9+/]+=*$"))
        } catch (e: Exception) {
            false
        }
    }

    private fun tryBase64Decode(value: String): String? {
        return try {
            val bytes = Base64.decode(value, Base64.NO_WRAP)
            String(bytes, Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }
}
