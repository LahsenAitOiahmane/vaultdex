package com.vaultguard.framework.scanner

import android.content.Context
import com.vaultguard.framework.core.FindingCategory
import com.vaultguard.framework.core.SecurityFinding
import com.vaultguard.framework.core.SeverityLevel
import com.vaultguard.framework.heuristics.CryptoVerifier
import com.vaultguard.framework.heuristics.HeuristicsEngine
import java.io.File

/**
 * Dynamically enumerates and analyzes all SharedPreferences files in the host app's sandbox.
 *
 * Discovery strategy:
 *  1. Scan the shared_prefs/ directory for all .xml files
 *  2. Open each one via context.getSharedPreferences() (MODE_PRIVATE)
 *  3. Extract every key-value pair
 *  4. Run HeuristicsEngine analysis on each pair
 *  5. Check for EncryptedSharedPreferences backing
 */
class SharedPrefsScanner(private val context: Context) {

    /**
     * Scans all SharedPreferences files and returns security findings.
     *
     * @param sharedPrefsFiles List of .xml files discovered by SandboxDiscovery
     * @return List of security findings from all SharedPreferences
     */
    fun scan(sharedPrefsFiles: List<File>): List<SecurityFinding> {
        val allFindings = mutableListOf<SecurityFinding>()

        for (xmlFile in sharedPrefsFiles) {
            val prefsName = xmlFile.nameWithoutExtension
            val findings = scanSinglePrefsFile(prefsName, xmlFile.absolutePath)
            allFindings.addAll(findings)
        }

        return allFindings
    }

    private fun scanSinglePrefsFile(prefsName: String, filePath: String): List<SecurityFinding> {
        val findings = mutableListOf<SecurityFinding>()

        try {
            val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            val allEntries = prefs.all

            if (allEntries.isEmpty()) return findings

            // 1. File-level encryption check
            val cryptoAssessment = CryptoVerifier.assessPreferencesFile(
                allEntries.mapValues { it.value }
            )

            if (cryptoAssessment.encryptionType == CryptoVerifier.EncryptionType.ANDROIDX_ENCRYPTED_PREFS) {
                // File uses EncryptedSharedPreferences — add a SECURE finding
                findings.add(
                    SecurityFinding(
                        category    = FindingCategory.SHARED_PREFS,
                        severity    = SeverityLevel.SECURE,
                        title       = "$prefsName uses EncryptedSharedPreferences",
                        description = cryptoAssessment.details,
                        evidence    = "File: $prefsName.xml\nKeys found: ${allEntries.size}\nEncryption: AndroidX Security Crypto (AES256-SIV + AES256-GCM)",
                        filePath    = filePath,
                        source      = "$prefsName.xml",
                        recommendation = "No action needed — file is properly encrypted with Android KeyStore backing."
                    )
                )
                return findings // Skip per-key analysis for encrypted files
            }

            // 2. Per-key analysis for unencrypted files
            for ((key, value) in allEntries) {
                val valueStr = value?.toString() ?: continue
                if (valueStr.isBlank()) continue

                val keyFindings = HeuristicsEngine.analyzeKeyValue(
                    key           = key,
                    value         = valueStr,
                    prefsFileName = "$prefsName.xml",
                    filePath      = filePath,
                    category      = FindingCategory.SHARED_PREFS
                )
                findings.addAll(keyFindings)
            }

            // 3. If no specific findings but file has > 5 entries and is unencrypted,
            //    add a general LOW finding about using unencrypted SharedPreferences
            if (findings.isEmpty() && allEntries.size > 5) {
                findings.add(
                    SecurityFinding(
                        category    = FindingCategory.SHARED_PREFS,
                        severity    = SeverityLevel.LOW,
                        title       = "$prefsName.xml uses unencrypted SharedPreferences",
                        description = "SharedPreferences file contains ${allEntries.size} entries stored in plain XML. While no sensitive data was detected by automated analysis, consider using EncryptedSharedPreferences for defense in depth.",
                        evidence    = "File: $prefsName.xml\nTotal keys: ${allEntries.size}\nKeys: ${allEntries.keys.take(10).joinToString(", ")}${if (allEntries.size > 10) "..." else ""}",
                        filePath    = filePath,
                        source      = "$prefsName.xml",
                        recommendation = "Migrate to EncryptedSharedPreferences for automatic encryption of all key-value pairs."
                    )
                )
            }

        } catch (e: Exception) {
            findings.add(
                SecurityFinding(
                    category    = FindingCategory.SHARED_PREFS,
                    severity    = SeverityLevel.LOW,
                    title       = "Failed to scan $prefsName.xml",
                    description = "Error reading SharedPreferences file: ${e.message}",
                    evidence    = "Exception: ${e.javaClass.simpleName}: ${e.message}",
                    filePath    = filePath,
                    source      = "$prefsName.xml"
                )
            )
        }

        return findings
    }
}
