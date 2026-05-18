package com.vaultguard.framework.heuristics

/**
 * Regex-based PII and credential detector.
 *
 * Hunts through arbitrary string values for patterns indicating:
 *  - Email addresses
 *  - Credit card numbers (Visa, MC, Amex, Discover)
 *  - JWT tokens (eyJ... three-part base64)
 *  - Phone numbers (international and US formats)
 *  - Password/credential key names
 *  - API key patterns (long hex or base64 strings)
 *  - SSN patterns
 *  - IP addresses
 *
 * All patterns are generic — no app-specific knowledge required.
 */
object PiiDetector {

    data class PiiMatch(
        val type: PiiType,
        val matchedValue: String,
        val description: String
    )

    enum class PiiType(val displayName: String, val severity: String) {
        EMAIL("Email Address", "MEDIUM"),
        CREDIT_CARD("Credit Card Number", "CRITICAL"),
        JWT_TOKEN("JWT Token", "CRITICAL"),
        PHONE_NUMBER("Phone Number", "MEDIUM"),
        PASSWORD_VALUE("Plaintext Password", "CRITICAL"),
        API_KEY("API Key / Secret", "HIGH"),
        SSN("Social Security Number", "CRITICAL"),
        IP_ADDRESS("IP Address", "LOW"),
        BASE64_BLOB("Base64 Encoded Data", "MEDIUM"),
        PRIVATE_KEY("Private Key Material", "CRITICAL")
    }

    // --- Compiled regex patterns ---

    private val EMAIL_PATTERN = Regex(
        "[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}"
    )

    private val CREDIT_CARD_PATTERN = Regex(
        "\\b(?:4[0-9]{12}(?:[0-9]{3})?|5[1-5][0-9]{14}|3[47][0-9]{13}|6(?:011|5[0-9]{2})[0-9]{12})\\b"
    )

    private val JWT_PATTERN = Regex(
        "eyJ[A-Za-z0-9_-]{10,}\\.eyJ[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}"
    )

    private val PHONE_PATTERN = Regex(
        "(?:\\+?1[\\-\\s.]?)?\\(?[0-9]{3}\\)?[\\-\\s.]?[0-9]{3}[\\-\\s.]?[0-9]{4}"
    )

    private val SSN_PATTERN = Regex(
        "\\b[0-9]{3}-[0-9]{2}-[0-9]{4}\\b"
    )

    private val IP_ADDRESS_PATTERN = Regex(
        "\\b(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\b"
    )

    // Long hex strings (32+ chars) that look like API keys or hashes
    private val HEX_KEY_PATTERN = Regex(
        "\\b[0-9a-fA-F]{32,}\\b"
    )

    // Long base64 strings (40+ chars) — potential encoded secrets
    private val BASE64_BLOB_PATTERN = Regex(
        "[A-Za-z0-9+/]{40,}={0,2}"
    )

    // Private key headers
    private val PRIVATE_KEY_PATTERN = Regex(
        "-----BEGIN (?:RSA |EC |DSA )?PRIVATE KEY-----"
    )

    // Key names that suggest credentials (for SharedPreferences key analysis)
    private val SENSITIVE_KEY_NAMES = listOf(
        "password", "passwd", "pwd", "pass",
        "secret", "token", "auth", "credential",
        "api_key", "apikey", "api-key", "access_key",
        "private_key", "privatekey", "session",
        "bearer", "jwt", "refresh_token", "access_token",
        "pin", "passcode", "otp", "mfa",
        "ssn", "social_security", "credit_card", "card_number",
        "cvv", "cvc", "expiry", "client_secret",
        "hash_salt", "salt", "biometric_auth_token", "is_biometric_enrolled",
        "allow_biometric_bypass", "security_question", "security_answer",
        "latitude", "longitude", "address"
    )

    /**
     * Scans a string value for all PII patterns.
     * Returns a list of all matches found.
     */
    fun scanValue(value: String): List<PiiMatch> {
        val matches = mutableListOf<PiiMatch>()

        // Email
        EMAIL_PATTERN.findAll(value).forEach { match ->
            matches.add(PiiMatch(PiiType.EMAIL, match.value, "Email address found in value"))
        }

        // Credit card
        CREDIT_CARD_PATTERN.findAll(value).forEach { match ->
            matches.add(PiiMatch(PiiType.CREDIT_CARD, maskMiddle(match.value), "Credit card number detected"))
        }

        // JWT
        JWT_PATTERN.findAll(value).forEach { match ->
            matches.add(PiiMatch(PiiType.JWT_TOKEN, match.value.take(50) + "...", "JWT token signature detected"))
        }

        // Phone
        PHONE_PATTERN.findAll(value).forEach { match ->
            matches.add(PiiMatch(PiiType.PHONE_NUMBER, match.value, "Phone number pattern detected"))
        }

        // SSN
        SSN_PATTERN.findAll(value).forEach { match ->
            matches.add(PiiMatch(PiiType.SSN, "***-**-${match.value.takeLast(4)}", "SSN pattern detected"))
        }

        // IP Address
        IP_ADDRESS_PATTERN.findAll(value).forEach { match ->
            if (!isLocalhost(match.value)) {
                matches.add(PiiMatch(PiiType.IP_ADDRESS, match.value, "IP address found"))
            }
        }

        // Private key
        PRIVATE_KEY_PATTERN.findAll(value).forEach { _ ->
            matches.add(PiiMatch(PiiType.PRIVATE_KEY, "[PRIVATE KEY DETECTED]", "Private key material found in storage"))
        }

        // Long hex (API keys)
        HEX_KEY_PATTERN.findAll(value).forEach { match ->
            matches.add(PiiMatch(PiiType.API_KEY, match.value.take(16) + "..." + match.value.takeLast(4), "Long hex string — possible API key or hash"))
        }

        // Base64 blobs (only if no JWT already matched)
        if (matches.none { it.type == PiiType.JWT_TOKEN }) {
            BASE64_BLOB_PATTERN.findAll(value).forEach { match ->
                if (match.value.length >= 40) {
                    matches.add(PiiMatch(PiiType.BASE64_BLOB, match.value.take(30) + "...", "Large Base64 blob — possible encoded secret"))
                }
            }
        }

        return matches
    }

    /**
     * Checks if a SharedPreferences key name suggests it holds sensitive data.
     * Returns the matched sensitive keyword, or null if the key looks benign.
     */
    fun isSensitiveKeyName(keyName: String): String? {
        val lower = keyName.lowercase()
        return SENSITIVE_KEY_NAMES.firstOrNull { keyword ->
            lower.contains(keyword)
        }
    }

    /**
     * Checks if a plaintext value looks like it could be a password
     * (not just the key name, but the actual value).
     */
    fun looksLikePlaintextPassword(value: String): Boolean {
        // Too short or too long for typical passwords
        if (value.length < 4 || value.length > 128) return false
        // Contains whitespace — unlikely to be a password
        if (value.contains(" ") && value.count { it == ' ' } > 2) return false
        // Contains mix of character classes typical of passwords
        val hasUpper = value.any { it.isUpperCase() }
        val hasLower = value.any { it.isLowerCase() }
        val hasDigit = value.any { it.isDigit() }
        val hasSpecial = value.any { !it.isLetterOrDigit() }
        val classes = listOf(hasUpper, hasLower, hasDigit, hasSpecial).count { it }
        return classes >= 2 && value.length >= 6
    }

    private fun maskMiddle(value: String): String {
        if (value.length <= 8) return "****"
        return value.take(4) + "*".repeat(value.length - 8) + value.takeLast(4)
    }

    private fun isLocalhost(ip: String): Boolean {
        return ip == "127.0.0.1" || ip == "0.0.0.0" || ip.startsWith("10.0.2.")
    }
}
