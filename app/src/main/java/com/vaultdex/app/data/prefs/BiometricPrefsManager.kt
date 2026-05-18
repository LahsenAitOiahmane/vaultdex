package com.vaultdex.app.data.prefs

import android.content.Context
import android.content.SharedPreferences

/**
 * VULN-BIO: Biometric enrollment token stored in plaintext SharedPreferences.
 *
 * Real-world vulnerability: Apps implementing fingerprint/face authentication
 * sometimes store the biometric authentication result or enrollment hash
 * in SharedPreferences instead of using the Android KeyStore with
 * setUserAuthenticationRequired(true). An attacker can:
 *   - Edit the prefs XML to bypass biometric checks
 *   - Extract the biometric token to replay authentication
 *   - Set is_biometric_enrolled to true to skip verification
 *
 * File: /data/data/com.vaultdex.app/shared_prefs/biometric_prefs.xml
 */
class BiometricPrefsManager(context: Context) {

    companion object {
        private const val PREFS_NAME = "biometric_prefs"

        private const val KEY_BIOMETRIC_ENROLLED = "is_biometric_enrolled"
        private const val KEY_BIOMETRIC_TOKEN    = "biometric_auth_token"
        private const val KEY_BIOMETRIC_TYPE     = "biometric_type"
        private const val KEY_DEVICE_ID          = "enrolled_device_id"
        private const val KEY_ENROLLMENT_TIME    = "enrollment_timestamp"
        // VULN: bypass flag — attacker can set this to true
        private const val KEY_BYPASS_AUTH        = "allow_biometric_bypass"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * VULN-BIO: Seeds biometric enrollment data in plaintext.
     * A real app should use Android KeyStore with biometric-bound keys.
     */
    fun seedBiometricData() {
        prefs.edit()
            // VULN: Boolean flag editable by attacker to bypass biometric auth
            .putBoolean(KEY_BIOMETRIC_ENROLLED, true)
            // VULN: Biometric token in plaintext — should be in KeyStore
            .putString(KEY_BIOMETRIC_TOKEN, "bio_tkn_9f8e7d6c5b4a3f2e1d0c9b8a7f6e5d4c3b2a1f0e")
            .putString(KEY_BIOMETRIC_TYPE, "FINGERPRINT")
            .putString(KEY_DEVICE_ID, "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
            .putLong(KEY_ENROLLMENT_TIME, System.currentTimeMillis())
            // VULN: Debug bypass flag left in production
            .putBoolean(KEY_BYPASS_AUTH, false)
            .apply()
    }
}
