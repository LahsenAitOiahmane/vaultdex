package com.vaultdex.app.data.prefs

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID

/**
 * VULN-OAUTH: JWT access tokens and refresh tokens stored in plaintext SharedPreferences.
 *
 * Real-world vulnerability: Apps using OAuth 2.0 / OpenID Connect often store
 * access tokens, refresh tokens, and ID tokens directly in SharedPreferences.
 * An attacker can:
 *   - Hijack the user's session with the access token
 *   - Get new access tokens forever with the refresh token
 *   - Extract user identity claims from the ID token
 *
 * File: /data/data/com.vaultdex.app/shared_prefs/oauth_tokens.xml
 *
 * Detection: VaultGuard's PiiDetector (JWT regex eyJ...) + sensitive key names
 */
class OAuthTokenManager(context: Context) {

    companion object {
        private const val PREFS_NAME = "oauth_tokens"

        private const val KEY_ACCESS_TOKEN   = "access_token"
        private const val KEY_REFRESH_TOKEN  = "refresh_token"
        private const val KEY_ID_TOKEN       = "id_token"
        private const val KEY_TOKEN_TYPE     = "token_type"
        private const val KEY_EXPIRES_IN     = "expires_in"
        private const val KEY_SCOPE          = "scope"
        private const val KEY_AUTH_SERVER     = "auth_server_url"
        private const val KEY_CLIENT_ID      = "client_id"
        private const val KEY_CLIENT_SECRET  = "client_secret"  // VULN: Client secret on device!
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * VULN-OAUTH: Seeds realistic JWT tokens and OAuth credentials.
     * These are actual JWT format tokens (base64-encoded JSON headers/payloads).
     */
    fun seedTokens(userEmail: String) {
        // Realistic JWT access token (header.payload.signature)
        val accessToken = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9." +
            "eyJzdWIiOiIxMjM0NTY3ODkwIiwiZW1haWwiOiIke3VzZXJFbWFpbH0iLCJpYXQiOjE3MTYwMjM5MDIsImV4cCI6MTcxNjAyNzUwMiwiaXNzIjoiaHR0cHM6Ly9hdXRoLnZhdWx0ZGV4LmNvbSIsImF1ZCI6InZhdWx0ZGV4LW1vYmlsZSIsInNjb3BlIjoib3BlbmlkIHByb2ZpbGUgZW1haWwgdHJhbnNhY3Rpb25zOnJlYWQifQ." +
            "kR7PxG2vN8mQ1wL3jF5dH9sY4bA6cE0iK2oU7tZ8xW3pJ5nM"

        // Realistic JWT refresh token (longer-lived)
        val refreshToken = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9." +
            "eyJzdWIiOiIxMjM0NTY3ODkwIiwidHlwZSI6InJlZnJlc2giLCJqdGkiOiIke1VVSUQucmFuZG9tVVVJRCgpfSIsImlhdCI6MTcxNjAyMzkwMiwiZXhwIjoxNzE4NjE1OTAyfQ." +
            "mN8bV2cX4dF6gH0jK2lO4pQ6rS8tU0vW2xY4zA6bC8dE0fG"

        // Realistic OIDC ID token with PII claims
        val idToken = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9." +
            "eyJzdWIiOiIxMjM0NTY3ODkwIiwiZW1haWwiOiJ1c2VyQHZhdWx0ZGV4LmNvbSIsIm5hbWUiOiJKb2huIERvZSIsInBpY3R1cmUiOiJodHRwczovL2F2YXRhcnMuZXhhbXBsZS5jb20vam9obiIsInBob25lX251bWJlciI6IisxNTU1MTIzNDU2NyIsImFkZHJlc3MiOnsiZm9ybWF0dGVkIjoiMTIzIE1haW4gU3QsIFNhbiBGcmFuY2lzY28sIENBIDk0MTAyIn19." +
            "qR3sT5uV7wX9yZ1aB3cD5eF7gH9iJ1kL3mN5oP7qR9sT"

        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)      // VULN: JWT with user claims
            .putString(KEY_REFRESH_TOKEN, refreshToken)    // VULN: Long-lived refresh token
            .putString(KEY_ID_TOKEN, idToken)               // VULN: ID token with PII
            .putString(KEY_TOKEN_TYPE, "Bearer")
            .putLong(KEY_EXPIRES_IN, 3600L)
            .putString(KEY_SCOPE, "openid profile email transactions:read")
            .putString(KEY_AUTH_SERVER, "https://auth.vaultdex.com/oauth2")
            .putString(KEY_CLIENT_ID, "vaultdex-mobile-app")
            // VULN: OAuth client secret stored on device — should NEVER be in a mobile app
            .putString(KEY_CLIENT_SECRET, "dGhpc19pc19hX3NlY3JldF9jbGllbnRfc2VjcmV0XzEyMzQ1Njc4OTA=")
            .apply()
    }
}
