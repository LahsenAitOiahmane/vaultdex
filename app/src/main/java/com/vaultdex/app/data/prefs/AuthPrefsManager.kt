package com.vaultdex.app.data.prefs

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID

/**
 * VULN-A1 (Beginner): AuthPrefsManager stores credentials and session tokens
 * in plaintext inside SharedPreferences.
 *
 * The resulting XML file is located at:
 *   /data/data/com.vaultdex.app/shared_prefs/auth_prefs.xml
 *
 * It can be read with:
 *   adb shell run-as com.vaultdex.app cat shared_prefs/auth_prefs.xml
 *
 * The XML will contain:
 *   <string name="username">john.doe@example.com</string>
 *   <string name="password">MySecret123!</string>
 *   <string name="session_token">550e8400-e29b-41d4-a716-...</string>
 *
 * Secure alternative (NOT used here intentionally):
 *   Use EncryptedSharedPreferences from androidx.security.crypto
 */
class AuthPrefsManager(context: Context) {

    companion object {
        private const val PREFS_NAME = "auth_prefs"  // Results in auth_prefs.xml

        // VULN-A1: Plaintext keys stored in SharedPreferences
        private const val KEY_USERNAME      = "username"
        private const val KEY_PASSWORD      = "password"       // Stored in plaintext
        private const val KEY_SESSION_TOKEN = "session_token"  // Stored in plaintext
        private const val KEY_IS_LOGGED_IN  = "is_logged_in"
        private const val KEY_DISPLAY_NAME  = "display_name"
        private const val KEY_EMAIL         = "email"
        private const val KEY_ACCOUNT_NUMBER = "account_number"
    }

    // VULN-A1: Using default SharedPreferences (MODE_PRIVATE) — no encryption
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * VULN-A1: Saves username, password, and session token as plaintext strings.
     * Any process with READ access to the app's data directory can extract these.
     */
    fun saveLoginCredentials(username: String, password: String, displayName: String) {
        val sessionToken = generateSessionToken()
        val accountNumber = generateAccountNumber()

        prefs.edit()
            .putString(KEY_USERNAME, username)              // PLAINTEXT username
            .putString(KEY_PASSWORD, password)              // PLAINTEXT password — VULN-A1
            .putString(KEY_SESSION_TOKEN, sessionToken)     // PLAINTEXT session token — VULN-A1
            .putString(KEY_DISPLAY_NAME, displayName)
            .putString(KEY_EMAIL, username)
            .putString(KEY_ACCOUNT_NUMBER, accountNumber)
            .putBoolean(KEY_IS_LOGGED_IN, true)
            .apply()
    }

    fun getUsername(): String = prefs.getString(KEY_USERNAME, "") ?: ""
    fun getPassword(): String = prefs.getString(KEY_PASSWORD, "") ?: ""   // Returns plaintext
    fun getSessionToken(): String = prefs.getString(KEY_SESSION_TOKEN, "") ?: ""
    fun getDisplayName(): String = prefs.getString(KEY_DISPLAY_NAME, "User") ?: "User"
    fun getEmail(): String = prefs.getString(KEY_EMAIL, "") ?: ""
    fun getAccountNumber(): String = prefs.getString(KEY_ACCOUNT_NUMBER, "") ?: ""
    fun isLoggedIn(): Boolean = prefs.getBoolean(KEY_IS_LOGGED_IN, false)

    /**
     * VULN-D (partial): Note — logout clears the session flag but does NOT
     * clear the cache (see ProfileCacheManager). Credentials also remain in prefs
     * unless explicitly removed.
     */
    fun logout() {
        prefs.edit()
            .putBoolean(KEY_IS_LOGGED_IN, false)
            // Intentionally NOT removing username/password/session_token on logout
            // so they remain discoverable in auth_prefs.xml
            .apply()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    private fun generateSessionToken(): String = UUID.randomUUID().toString()
    private fun generateAccountNumber(): String = "VLT-${(1000000..9999999).random()}-ACC"
}
