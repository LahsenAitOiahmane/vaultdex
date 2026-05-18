package com.vaultguard.framework.scanner

import com.vaultguard.framework.core.FindingCategory
import com.vaultguard.framework.core.SecurityFinding
import com.vaultguard.framework.core.SeverityLevel
import com.vaultguard.framework.heuristics.HeuristicsEngine
import java.io.File

/**
 * Scans the host app's cache directory (context.cacheDir) for sensitive data.
 *
 * Cache directories are meant for temporary, non-sensitive data. Finding PII, tokens,
 * or credentials in cache is a security risk because:
 *  1. Cache data may persist across sessions (no enforced TTL)
 *  2. Cache is often not cleared on logout
 *  3. On some devices, cache can be accessible without root
 *  4. Backup mechanisms may include cache data
 */
class CacheScanner {

    companion object {
        private val TEXT_EXTENSIONS = setOf(
            "json", "csv", "txt", "xml", "log", "tmp", "cache",
            "html", "htm", "cfg", "conf"
        )
        private const val MAX_FILE_SIZE = 2 * 1024 * 1024L
    }

    /**
     * Scans all cache files and returns security findings.
     */
    fun scan(cacheFiles: List<File>): List<SecurityFinding> {
        val allFindings = mutableListOf<SecurityFinding>()

        if (cacheFiles.isEmpty()) return allFindings

        // Flag that cache directory has files at all — assess whether any are sensitive
        for (file in cacheFiles) {
            if (!file.isFile || file.length() > MAX_FILE_SIZE) continue
            if (!isReadableTextFile(file)) continue

            val findings = scanCacheFile(file)
            allFindings.addAll(findings)
        }

        return allFindings
    }

    private fun scanCacheFile(file: File): List<SecurityFinding> {
        val findings = mutableListOf<SecurityFinding>()

        try {
            val content = file.readText(Charsets.UTF_8)
            if (content.isBlank()) return findings

            // Run content-level heuristic analysis against cache category
            val contentFindings = HeuristicsEngine.analyzeTextContent(
                content  = content,
                fileName = file.name,
                filePath = file.absolutePath,
                category = FindingCategory.CACHE
            )

            // Upgrade severity for cache findings — sensitive data in cache is extra risky
            // because cache may not be cleared on logout
            for (finding in contentFindings) {
                val upgradedFinding = if (finding.severity == SeverityLevel.MEDIUM) {
                    finding.copy(
                        severity    = SeverityLevel.HIGH,
                        description = finding.description + " Additionally, this data is stored in the cache directory which may persist across user sessions and is not cleared on logout."
                    )
                } else {
                    finding.copy(
                        description = finding.description + " This data persists in the cache directory."
                    )
                }
                findings.add(upgradedFinding)
            }

            // If the file contains JSON with sensitive fields, flag specifically
            if (content.trimStart().startsWith("{") || content.trimStart().startsWith("[")) {
                if (contentFindings.isNotEmpty()) {
                    findings.add(
                        SecurityFinding(
                            category    = FindingCategory.CACHE,
                            severity    = SeverityLevel.HIGH,
                            title       = "Sensitive JSON cached: ${file.name}",
                            description = "A JSON payload containing sensitive data is cached in cacheDir. This data is not automatically cleared on logout and may be accessible to other processes or backup tools.",
                            evidence    = "File: ${file.name}\nPath: ${file.absolutePath}\nSize: ${file.length()} bytes\nLast modified: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date(file.lastModified()))}\nPreview: ${content.take(300)}",
                            filePath    = file.absolutePath,
                            source      = file.name,
                            recommendation = "Do not cache sensitive data. If caching is required, encrypt the payload and implement cache invalidation on logout. Set appropriate cache-control headers for network responses."
                        )
                    )
                }
            }

        } catch (e: Exception) {
            // Not readable as text — skip
        }

        return findings
    }

    private fun isReadableTextFile(file: File): Boolean {
        val ext = file.extension.lowercase()
        if (ext in TEXT_EXTENSIONS) return true

        // Try content-based detection for extensionless files
        return try {
            val header = file.inputStream().use { stream ->
                val buf = ByteArray(minOf(512, file.length().toInt()))
                stream.read(buf)
                buf
            }
            val printable = header.count { it in 9..13 || it in 32..126 }
            printable.toFloat() / header.size > 0.85f
        } catch (e: Exception) {
            false
        }
    }
}
