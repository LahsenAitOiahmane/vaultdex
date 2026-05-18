package com.vaultdex.app.data.prefs

import android.content.Context
import android.content.SharedPreferences
import java.security.MessageDigest

/**
 * VULN-HASH: Weak password hashing stored in SharedPreferences.
 *
 * Real-world vulnerability: Apps that implement "remember password" or
 * offline verification often hash the password with MD5 or SHA1 and store
 * the hash. Problems:
 *   1. MD5 is broken — rainbow tables can reverse common passwords instantly
 *   2. SHA1 is deprecated — collision attacks are practical
 *   3. No salt (or a hardcoded salt) makes precomputed attacks trivial
 *   4. Storing the hash alongside the salt defeats the purpose
 *
 * File: /data/data/com.vaultdex.app/shared_prefs/password_hash.xml
 *
 * Detection: VaultGuard detects sensitive key name "password_hash" +
 *            the hash value has high entropy (hex string)
 */
class WeakHashManager(context: Context) {

    companion object {
        private const val PREFS_NAME = "password_hash"

        // VULN-HASH: Hardcoded salt — same for all users, visible in decompiled APK
        private const val HARDCODED_SALT = "VaultDex2024Salt!"

        private const val KEY_PASSWORD_MD5    = "password_md5_hash"
        private const val KEY_PASSWORD_SHA1   = "password_sha1_hash"
        private const val KEY_SALT            = "hash_salt"           // VULN: salt stored alongside
        private const val KEY_HASH_ALGORITHM  = "hash_algorithm"
        private const val KEY_SECURITY_ANSWER = "security_answer"     // VULN: plaintext
        private const val KEY_SECURITY_QUESTION = "security_question"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * VULN-HASH: Stores MD5 and SHA1 hashes of the password.
     * Both algorithms are cryptographically broken for password storage.
     */
    fun storePasswordHash(password: String) {
        val md5Hash  = hashMD5(password + HARDCODED_SALT)
        val sha1Hash = hashSHA1(password + HARDCODED_SALT)

        prefs.edit()
            .putString(KEY_PASSWORD_MD5, md5Hash)        // VULN: MD5 is broken
            .putString(KEY_PASSWORD_SHA1, sha1Hash)      // VULN: SHA1 is deprecated
            .putString(KEY_SALT, HARDCODED_SALT)          // VULN: Salt stored in prefs!
            .putString(KEY_HASH_ALGORITHM, "MD5+SHA1")
            // VULN: Security Q&A stored in plaintext — common password reset bypass
            .putString(KEY_SECURITY_QUESTION, "What is your mother's maiden name?")
            .putString(KEY_SECURITY_ANSWER, "Johnson")    // VULN: Plaintext answer
            .apply()
    }

    // VULN: Using MD5 for password hashing — trivially reversible
    private fun hashMD5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    // VULN: Using SHA1 for password hashing — deprecated, collision attacks practical
    private fun hashSHA1(input: String): String {
        val md = MessageDigest.getInstance("SHA-1")
        val digest = md.digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
