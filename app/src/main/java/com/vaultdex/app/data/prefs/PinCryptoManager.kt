package com.vaultdex.app.data.prefs

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * VULN-A2 (Medium): PIN encryption with critical cryptographic misconfigurations.
 *
 * Problems in this implementation:
 *  1. HARDCODED AES KEY: The secret key is a compile-time constant string.
 *     Any attacker who decompiles the APK (e.g. jadx, apktool) can immediately
 *     extract "V@u1tD3xS3cr3tKey" and decrypt all stored PINs.
 *
 *  2. STATIC IV: The Initialization Vector "VaultDexIV123456" never changes.
 *     This makes encryption deterministic — the same PIN always produces the
 *     same ciphertext, enabling frequency analysis attacks.
 *
 *  3. CBC MODE with static IV ≈ ECB mode vulnerability for short inputs like PINs.
 *
 *  4. The encrypted PIN is stored in SharedPreferences alongside the key in code.
 *
 * Stored at:
 *   /data/data/com.vaultdex.app/shared_prefs/pin_prefs.xml
 *   <string name="encrypted_pin">BASE64_OF_AES_CBC(PIN)</string>
 *
 * To decrypt: AES/CBC/PKCS5Padding with key="V@u1tD3xS3cr3tKey", IV="VaultDexIV123456"
 */
class PinCryptoManager(context: Context) {

    companion object {
        private const val PREFS_NAME    = "pin_prefs"
        private const val KEY_PIN       = "encrypted_pin"

        // ======================================================
        // VULN-A2: HARDCODED AES KEY — DO NOT DO THIS IN PRODUCTION
        // Visible to any attacker who runs: jadx -d output app-release.apk
        // ======================================================
        private const val SECRET_KEY = "V@u1tD3xS3cr3tKey"  // 18 chars → will be padded to 16/24

        // ======================================================
        // VULN-A2: STATIC IV — Makes encryption deterministic
        // The same plaintext always produces the same ciphertext
        // ======================================================
        private const val STATIC_IV  = "VaultDexIV123456"  // 16 bytes

        private const val ALGORITHM  = "AES"
        private const val TRANSFORM  = "AES/CBC/PKCS5Padding"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Saves an AES-CBC encrypted PIN to SharedPreferences.
     * The encryption is trivially reversible due to hardcoded key + static IV.
     */
    fun savePin(pin: String) {
        val encryptedPin = encrypt(pin)
        prefs.edit()
            .putString(KEY_PIN, encryptedPin)  // Base64 of AES-CBC(pin) with hardcoded key
            .apply()
    }

    fun verifyPin(inputPin: String): Boolean {
        val stored = prefs.getString(KEY_PIN, null) ?: return false
        return try {
            val decrypted = decrypt(stored)
            decrypted == inputPin
        } catch (e: Exception) {
            false
        }
    }

    fun hasPin(): Boolean = prefs.contains(KEY_PIN)

    fun clearPin() {
        prefs.edit().remove(KEY_PIN).apply()
    }

    // VULN-A2: AES encryption with hardcoded key and static IV
    private fun encrypt(plaintext: String): String {
        val keyBytes = SECRET_KEY.toByteArray(Charsets.UTF_8).copyOf(16)  // Pad/trim to 16 bytes
        val ivBytes  = STATIC_IV.toByteArray(Charsets.UTF_8).copyOf(16)

        val secretKeySpec = SecretKeySpec(keyBytes, ALGORITHM)
        val ivSpec        = IvParameterSpec(ivBytes)

        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, ivSpec)

        val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    // Decrypt using the same hardcoded key and IV
    private fun decrypt(ciphertext: String): String {
        val keyBytes = SECRET_KEY.toByteArray(Charsets.UTF_8).copyOf(16)
        val ivBytes  = STATIC_IV.toByteArray(Charsets.UTF_8).copyOf(16)

        val secretKeySpec = SecretKeySpec(keyBytes, ALGORITHM)
        val ivSpec        = IvParameterSpec(ivBytes)

        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, ivSpec)

        val decoded   = Base64.decode(ciphertext, Base64.NO_WRAP)
        val decrypted = cipher.doFinal(decoded)
        return String(decrypted, Charsets.UTF_8)
    }
}
