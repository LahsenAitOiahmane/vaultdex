package com.vaultguard.framework.scanner

import com.vaultguard.framework.core.FindingCategory
import com.vaultguard.framework.core.SecurityFinding
import com.vaultguard.framework.core.SeverityLevel
import com.vaultguard.framework.heuristics.HeuristicsEngine
import java.io.File

/**
 * Scans the host app's internal files directory (context.filesDir) for sensitive data.
 *
 * Recursively walks the file tree looking for:
 *  - JSON/CSV/TXT/XML files containing PII or credentials
 *  - Plaintext backup files
 *  - Configuration files with secrets
 *  - Any readable file that shouldn't exist in internal storage unencrypted
 */
class FileScanner {

    companion object {
        // File extensions to analyze as text
        private val TEXT_EXTENSIONS = setOf(
            "json", "csv", "txt", "xml", "log", "cfg", "conf",
            "properties", "yaml", "yml", "ini", "pem", "key", "crt"
        )
        // Maximum file size to read (2MB) to prevent OOM on large files
        private const val MAX_FILE_SIZE = 2 * 1024 * 1024L
    }

    /**
     * Scans all files in the internal storage directory.
     */
    fun scan(files: List<File>): List<SecurityFinding> {
        val allFindings = mutableListOf<SecurityFinding>()

        for (file in files) {
            if (!file.isFile || file.length() > MAX_FILE_SIZE) continue
            if (!isTextFile(file)) continue

            val findings = scanSingleFile(file)
            allFindings.addAll(findings)
        }

        return allFindings
    }

    private fun scanSingleFile(file: File): List<SecurityFinding> {
        val findings = mutableListOf<SecurityFinding>()

        try {
            val content = file.readText(Charsets.UTF_8)
            if (content.isBlank()) return findings

            // Check if the file looks like a backup (common pattern)
            val isBackup = file.name.contains("backup", ignoreCase = true) ||
                    file.name.contains("export", ignoreCase = true) ||
                    file.parentFile?.name?.contains("backup", ignoreCase = true) == true

            if (isBackup) {
                findings.add(
                    SecurityFinding(
                        category    = FindingCategory.FILES,
                        severity    = SeverityLevel.HIGH,
                        title       = "Unencrypted backup file: ${file.name}",
                        description = "A file that appears to be a backup/export was found in internal storage without encryption. Backup files typically contain aggregated sensitive data.",
                        evidence    = "File: ${file.name}\nPath: ${file.absolutePath}\nSize: ${file.length()} bytes\nPreview: ${content.take(300)}",
                        filePath    = file.absolutePath,
                        source      = file.name,
                        recommendation = "Encrypt backup files with a user-derived key (PBKDF2/Argon2) before writing to disk. Consider using EncryptedFile from AndroidX Security."
                    )
                )
            }

            // Run content-level heuristic analysis
            val contentFindings = HeuristicsEngine.analyzeTextContent(
                content  = content,
                fileName = file.name,
                filePath = file.absolutePath,
                category = FindingCategory.FILES
            )
            findings.addAll(contentFindings)

            // Check for key/certificate files
            if (file.extension in listOf("pem", "key", "crt", "p12", "jks")) {
                findings.add(
                    SecurityFinding(
                        category    = FindingCategory.FILES,
                        severity    = SeverityLevel.CRITICAL,
                        title       = "Cryptographic key/certificate file: ${file.name}",
                        description = "A file with a cryptographic key/certificate extension was found in internal storage. Private keys stored on-device should be in the Android KeyStore, not as files.",
                        evidence    = "File: ${file.name}\nPath: ${file.absolutePath}\nSize: ${file.length()} bytes\nExtension: ${file.extension}",
                        filePath    = file.absolutePath,
                        source      = file.name,
                        recommendation = "Move private key material to Android KeyStore. Remove key files from internal storage."
                    )
                )
            }

        } catch (e: Exception) {
            // File might be binary or unreadable — skip silently
        }

        return findings
    }

    private fun isTextFile(file: File): Boolean {
        val ext = file.extension.lowercase()
        if (ext in TEXT_EXTENSIONS) return true

        // Try to detect text files without extension by reading first few bytes
        if (ext.isEmpty() || ext.length > 10) {
            return try {
                val header = file.inputStream().use { stream ->
                    val buf = ByteArray(minOf(512, file.length().toInt()))
                    stream.read(buf)
                    buf
                }
                // Check if bytes are mostly printable ASCII
                val printable = header.count { it in 9..13 || it in 32..126 }
                printable.toFloat() / header.size > 0.85f
            } catch (e: Exception) {
                false
            }
        }

        return false
    }
}
