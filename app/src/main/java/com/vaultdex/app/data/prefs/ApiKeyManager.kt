package com.vaultdex.app.data.prefs

import android.content.Context
import android.content.SharedPreferences

/**
 * VULN-API: Hardcoded third-party API keys stored in plaintext SharedPreferences.
 *
 * Real-world vulnerability: Apps frequently store API keys from services like
 * Firebase, Google Maps, Stripe, AWS, and SendGrid directly in SharedPreferences.
 * These keys can be extracted and used to:
 *   - Make API calls billed to the developer
 *   - Access backend services and user data
 *   - Send emails/SMS as the app
 *
 * File: /data/data/com.vaultdex.app/shared_prefs/api_keys.xml
 *
 * Detection: VaultGuard's PiiDetector (high-entropy + sensitive key name) +
 *            EntropyAnalyzer (API keys have very high Shannon entropy)
 */
class ApiKeyManager(context: Context) {

    companion object {
        private const val PREFS_NAME = "api_keys"  // VULN: descriptive file name

        // VULN-API: All keys stored in plaintext
        private const val KEY_FIREBASE_API      = "firebase_api_key"
        private const val KEY_FIREBASE_PROJECT   = "firebase_project_id"
        private const val KEY_GOOGLE_MAPS        = "google_maps_api_key"
        private const val KEY_STRIPE_SECRET      = "stripe_secret_key"
        private const val KEY_STRIPE_PUBLISHABLE = "stripe_publishable_key"
        private const val KEY_AWS_ACCESS         = "aws_access_key_id"
        private const val KEY_AWS_SECRET         = "aws_secret_access_key"
        private const val KEY_SENDGRID           = "sendgrid_api_key"
        private const val KEY_TWILIO_SID         = "twilio_account_sid"
        private const val KEY_TWILIO_AUTH        = "twilio_auth_token"
        private const val KEY_SENTRY_DSN         = "sentry_dsn"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * VULN-API: Seeds realistic-looking API keys into SharedPreferences.
     * In a real app, these would be actual production keys embedded in the APK
     * or fetched once and cached forever.
     */
    fun seedApiKeys() {
        prefs.edit()
            // VULN: Firebase API key — allows access to Firebase project
            .putString(KEY_FIREBASE_API, "AIzaSyD8f3K7xRjB2v1Nc9mP4wQ5e0hYtUiOpAs")
            .putString(KEY_FIREBASE_PROJECT, "vaultdex-prod-a1b2c")

            // VULN: Google Maps API key — can be used to rack up billing
            .putString(KEY_GOOGLE_MAPS, "AIzaSyC4R6AN7SmujjPUIGKdyj2PQqLi8gNc3Mk")

            // VULN: Stripe SECRET key — can process payments, issue refunds
            .putString(KEY_STRIPE_SECRET, "sk_live_51N7xYzABcDeFgHiJkLmNoPqRsTuVwXyZ0123456789abcdef")
            .putString(KEY_STRIPE_PUBLISHABLE, "pk_live_51N7xYzABcDeFgHiJkLmNoPqRsTuVwXyZ0123456789abcdef")

            // VULN: AWS credentials — full access to cloud infrastructure
            .putString(KEY_AWS_ACCESS, "AKIAIOSFODNN7EXAMPLE")
            .putString(KEY_AWS_SECRET, "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY")

            // VULN: SendGrid API key — can send emails as the app
            .putString(KEY_SENDGRID, "SG.xR3j4kL5mN6oP7qR8sT9uV.wX0yZ1aB2cD3eF4gH5iJ6kL7mN8oP9qR0sT1uV2wX")

            // VULN: Twilio credentials — can send SMS, make calls
            .putString(KEY_TWILIO_SID, "AC1a2b3c4d5e6f7g8h9i0j1k2l3m4n5o6p")
            .putString(KEY_TWILIO_AUTH, "a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6")

            // VULN: Sentry DSN — exposes error tracking infrastructure
            .putString(KEY_SENTRY_DSN, "https://a1b2c3d4e5f6@o123456.ingest.sentry.io/7654321")
            .apply()
    }
}
