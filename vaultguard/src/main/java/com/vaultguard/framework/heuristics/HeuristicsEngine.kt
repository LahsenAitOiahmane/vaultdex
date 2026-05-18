package com.vaultguard.framework.heuristics

import com.vaultguard.framework.core.FindingCategory
import com.vaultguard.framework.core.SecurityFinding
import com.vaultguard.framework.core.SeverityLevel

/**
 * Orchestrates all heuristic analysis passes (entropy, PII, crypto) on discovered data.
 *
 * The HeuristicsEngine is the central brain that takes raw key-value pairs or raw text
 * and runs every available analyzer against them, producing SecurityFinding objects.
 */
object HeuristicsEngine {

    /**
     * Analyzes a single key-value pair from SharedPreferences.
     * Runs PII detection, entropy analysis, crypto checks, and sensitive key name detection.
     *
     * @param key The SharedPreferences key name
     * @param value The stored value (as String)
     * @param prefsFileName The name of the SharedPreferences file
     * @param filePath Full path to the SharedPreferences file
     * @return List of findings for this key-value pair
     */
    fun analyzeKeyValue(
        key: String,
        value: String,
        prefsFileName: String,
        filePath: String,
        category: FindingCategory = FindingCategory.SHARED_PREFS
    ): List<SecurityFinding> {
        val findings = mutableListOf<SecurityFinding>()

        // 1. Check if the key name itself suggests sensitive data
        val sensitiveKeyword = PiiDetector.isSensitiveKeyName(key)
        if (sensitiveKeyword != null) {
            // Key name is sensitive — check if the value is plaintext
            val cryptoAssessment = CryptoVerifier.assessValue(value)
            if (!cryptoAssessment.isEncrypted) {
                val severity = when (sensitiveKeyword) {
                    "password", "passwd", "pwd", "pass", "credential", "client_secret" -> SeverityLevel.CRITICAL
                    "token", "session", "bearer", "jwt", "access_token", "refresh_token" -> SeverityLevel.CRITICAL
                    "api_key", "apikey", "api-key", "access_key", "private_key", "privatekey" -> SeverityLevel.CRITICAL
                    "pin", "passcode", "otp", "mfa" -> SeverityLevel.HIGH
                    "ssn", "social_security", "credit_card", "card_number", "cvv", "cvc", "expiry" -> SeverityLevel.CRITICAL
                    "hash_salt", "salt", "biometric_auth_token", "is_biometric_enrolled", "allow_biometric_bypass" -> SeverityLevel.CRITICAL
                    "security_question", "security_answer", "latitude", "longitude" -> SeverityLevel.HIGH
                    else -> SeverityLevel.MEDIUM
                }

                findings.add(
                    SecurityFinding(
                        category    = category,
                        severity    = severity,
                        title       = "Sensitive key \"$key\" stores plaintext value",
                        description = "SharedPreferences key name contains \"$sensitiveKeyword\" and the value is stored in plaintext without encryption. ${cryptoAssessment.details}",
                        evidence    = "File: $prefsFileName\nKey: $key\nValue: ${truncateEvidence(value)}\n${EntropyAnalyzer.report(value)}",
                        filePath    = filePath,
                        source      = "$prefsFileName → $key",
                        recommendation = "Use EncryptedSharedPreferences from AndroidX Security Crypto, or encrypt values with Android KeyStore-backed keys before storing."
                    )
                )
            } else if (cryptoAssessment.encryptionType == CryptoVerifier.EncryptionType.WEAK_CUSTOM_ENCRYPTION) {
                findings.add(
                    SecurityFinding(
                        category    = category,
                        severity    = SeverityLevel.HIGH,
                        title       = "Sensitive key \"$key\" uses weak custom encryption",
                        description = "Value for \"$key\" appears encrypted but may use a hardcoded or weak key. ${cryptoAssessment.details}",
                        evidence    = "File: $prefsFileName\nKey: $key\nEncrypted value: ${truncateEvidence(value)}\nConfidence: ${(cryptoAssessment.confidence * 100).toInt()}%",
                        filePath    = filePath,
                        source      = "$prefsFileName → $key",
                        recommendation = "Migrate to Android KeyStore-backed encryption. Avoid hardcoded AES keys or static IVs."
                    )
                )
            }
        }

        // 2. PII scan on the value regardless of key name
        val piiMatches = PiiDetector.scanValue(value)
        for (match in piiMatches) {
            val severity = when (match.type) {
                PiiDetector.PiiType.CREDIT_CARD, PiiDetector.PiiType.SSN,
                PiiDetector.PiiType.JWT_TOKEN, PiiDetector.PiiType.PRIVATE_KEY,
                PiiDetector.PiiType.PASSWORD_VALUE -> SeverityLevel.CRITICAL
                PiiDetector.PiiType.API_KEY -> SeverityLevel.HIGH
                PiiDetector.PiiType.EMAIL, PiiDetector.PiiType.PHONE_NUMBER,
                PiiDetector.PiiType.BASE64_BLOB -> SeverityLevel.MEDIUM
                PiiDetector.PiiType.IP_ADDRESS -> SeverityLevel.LOW
            }

            findings.add(
                SecurityFinding(
                    category    = category,
                    severity    = severity,
                    title       = "${match.type.displayName} found in \"$key\"",
                    description = "${match.description}. PII data stored in plaintext in SharedPreferences is accessible to any process with read access to the app's data directory.",
                    evidence    = "File: $prefsFileName\nKey: $key\nPII Type: ${match.type.displayName}\nMatched: ${match.matchedValue}",
                    filePath    = filePath,
                    source      = "$prefsFileName → $key",
                    recommendation = "Remove PII from local storage or encrypt with EncryptedSharedPreferences."
                )
            )
        }

        // 3. Entropy-only flag (if nothing else caught it but entropy is very high)
        if (findings.isEmpty() && value.length >= 16) {
            val classification = EntropyAnalyzer.classify(value)
            if (classification == EntropyAnalyzer.EntropyClassification.HIGH_ENTROPY) {
                findings.add(
                    SecurityFinding(
                        category    = category,
                        severity    = SeverityLevel.MEDIUM,
                        title       = "High-entropy value in \"$key\" — possible secret",
                        description = "The value has unusually high Shannon entropy, which may indicate a leaked API key, token, or cryptographic material that was not detected by pattern matching.",
                        evidence    = "File: $prefsFileName\nKey: $key\nValue: ${truncateEvidence(value)}\n${EntropyAnalyzer.report(value)}",
                        filePath    = filePath,
                        source      = "$prefsFileName → $key",
                        recommendation = "Review this value manually. If it is a secret, encrypt it or move to Android KeyStore."
                    )
                )
            }
        }

        return findings
    }

    /**
     * Analyzes raw text content from files or cache for PII and secrets.
     */
    fun analyzeTextContent(
        content: String,
        fileName: String,
        filePath: String,
        category: FindingCategory
    ): List<SecurityFinding> {
        val findings = mutableListOf<SecurityFinding>()

        // PII scan on the entire content
        val piiMatches = PiiDetector.scanValue(content)
        for (match in piiMatches) {
            val severity = when (match.type) {
                PiiDetector.PiiType.CREDIT_CARD, PiiDetector.PiiType.SSN,
                PiiDetector.PiiType.JWT_TOKEN, PiiDetector.PiiType.PRIVATE_KEY,
                PiiDetector.PiiType.PASSWORD_VALUE -> SeverityLevel.CRITICAL
                PiiDetector.PiiType.API_KEY -> SeverityLevel.HIGH
                PiiDetector.PiiType.EMAIL, PiiDetector.PiiType.PHONE_NUMBER,
                PiiDetector.PiiType.BASE64_BLOB -> SeverityLevel.MEDIUM
                PiiDetector.PiiType.IP_ADDRESS -> SeverityLevel.LOW
            }

            findings.add(
                SecurityFinding(
                    category    = category,
                    severity    = severity,
                    title       = "${match.type.displayName} in file \"$fileName\"",
                    description = "${match.description}. Sensitive data found in ${category.displayName} storage.",
                    evidence    = "File: $fileName\nPII Type: ${match.type.displayName}\nMatched: ${match.matchedValue}",
                    filePath    = filePath,
                    source      = fileName,
                    recommendation = "Encrypt file contents or remove sensitive data from local storage."
                )
            )
        }

        // Check for JSON files with sensitive-sounding keys
        if (content.trimStart().startsWith("{") || content.trimStart().startsWith("[")) {
            val sensitiveJsonKeys = findSensitiveJsonKeys(content)
            for (jsonKey in sensitiveJsonKeys) {
                findings.add(
                    SecurityFinding(
                        category    = category,
                        severity    = SeverityLevel.HIGH,
                        title       = "Sensitive JSON field \"$jsonKey\" in \"$fileName\"",
                        description = "A JSON file contains a field with a sensitive key name (\"$jsonKey\") stored in plaintext.",
                        evidence    = "File: $fileName\nJSON key: $jsonKey\nContent preview: ${truncateEvidence(content)}",
                        filePath    = filePath,
                        source      = "$fileName → JSON.$jsonKey",
                        recommendation = "Encrypt the file or at minimum encrypt individual sensitive fields before writing to disk."
                    )
                )
            }
        }

        return findings
    }

    /**
     * Analyzes a database cell value (from a dynamically discovered SQLite table).
     */
    fun analyzeDatabaseCell(
        value: String,
        columnName: String,
        tableName: String,
        dbName: String,
        filePath: String
    ): List<SecurityFinding> {
        val findings = mutableListOf<SecurityFinding>()

        // Check column name sensitivity
        val sensitiveKeyword = PiiDetector.isSensitiveKeyName(columnName)
        if (sensitiveKeyword != null && value.isNotBlank()) {
            findings.add(
                SecurityFinding(
                    category    = FindingCategory.DATABASE,
                    severity    = SeverityLevel.HIGH,
                    title       = "Sensitive column \"$columnName\" in table \"$tableName\"",
                    description = "Database column name contains \"$sensitiveKeyword\" and stores plaintext data in an unencrypted SQLite database.",
                    evidence    = "Database: $dbName\nTable: $tableName\nColumn: $columnName\nSample value: ${truncateEvidence(value)}",
                    filePath    = filePath,
                    source      = "$dbName → $tableName.$columnName",
                    recommendation = "Use SQLCipher to encrypt the database, or encrypt individual sensitive column values before insertion."
                )
            )
        }

        // PII scan on cell value
        val piiMatches = PiiDetector.scanValue(value)
        for (match in piiMatches) {
            val severity = when (match.type) {
                PiiDetector.PiiType.CREDIT_CARD, PiiDetector.PiiType.SSN,
                PiiDetector.PiiType.JWT_TOKEN, PiiDetector.PiiType.PRIVATE_KEY,
                PiiDetector.PiiType.PASSWORD_VALUE -> SeverityLevel.CRITICAL
                PiiDetector.PiiType.API_KEY -> SeverityLevel.HIGH
                PiiDetector.PiiType.EMAIL, PiiDetector.PiiType.PHONE_NUMBER,
                PiiDetector.PiiType.BASE64_BLOB -> SeverityLevel.MEDIUM
                PiiDetector.PiiType.IP_ADDRESS -> SeverityLevel.LOW
            }

            findings.add(
                SecurityFinding(
                    category    = FindingCategory.DATABASE,
                    severity    = severity,
                    title       = "${match.type.displayName} in $tableName.$columnName",
                    description = "${match.description} found in unencrypted database.",
                    evidence    = "Database: $dbName\nTable: $tableName\nColumn: $columnName\nPII: ${match.matchedValue}",
                    filePath    = filePath,
                    source      = "$dbName → $tableName.$columnName",
                    recommendation = "Encrypt the database with SQLCipher or hash/encrypt sensitive column values."
                )
            )
        }

        return findings
    }

    /**
     * Scans JSON content for keys that look sensitive (password, token, etc.)
     */
    private fun findSensitiveJsonKeys(json: String): List<String> {
        val keyPattern = Regex("\"([^\"]+)\"\\s*:")
        val foundKeys = mutableListOf<String>()
        keyPattern.findAll(json).forEach { match ->
            val key = match.groupValues[1]
            if (PiiDetector.isSensitiveKeyName(key) != null) {
                foundKeys.add(key)
            }
        }
        return foundKeys.distinct()
    }

    private fun truncateEvidence(value: String, maxLen: Int = 200): String {
        return if (value.length <= maxLen) value
        else value.take(maxLen) + "... [truncated, ${value.length} chars total]"
    }
}
