package com.vaultguard.framework.core

import java.util.UUID

/**
 * Immutable data model representing a single security finding discovered by VaultGuard.
 *
 * Each finding captures:
 * - What was found (title, description)
 * - Where it was found (category, filePath, source)
 * - How severe it is (severity)
 * - The raw evidence proving the issue (evidence)
 * - What should be done about it (recommendation)
 */
data class SecurityFinding(
    val id: String = UUID.randomUUID().toString(),
    val category: FindingCategory,
    val severity: SeverityLevel,
    val title: String,
    val description: String,
    val evidence: String,
    val filePath: String,
    val source: String = "",         // e.g. "key: password", "table: users", etc.
    val recommendation: String = "",
    val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * One-line summary combining severity emoji, title and source location.
     */
    val summary: String
        get() = "${severity.emoji} [${severity.displayName}] $title"
}
