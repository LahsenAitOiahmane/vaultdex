package com.vaultguard.framework.core

import androidx.compose.ui.graphics.Color

/**
 * Severity levels for security findings, ordered from most to least severe.
 * Each level carries a numeric weight used by SecurityGrader for scoring.
 */
enum class SeverityLevel(
    val weight: Int,
    val displayName: String,
    val color: Color,
    val emoji: String
) {
    CRITICAL(
        weight = 100,
        displayName = "Critical",
        color = Color(0xFFFF1744),
        emoji = "🔴"
    ),
    HIGH(
        weight = 40,
        displayName = "High",
        color = Color(0xFFFF6D00),
        emoji = "🟠"
    ),
    MEDIUM(
        weight = 15,
        displayName = "Medium",
        color = Color(0xFFFFAB00),
        emoji = "🟡"
    ),
    LOW(
        weight = 5,
        displayName = "Low",
        color = Color(0xFF2979FF),
        emoji = "🔵"
    ),
    SECURE(
        weight = 0,
        displayName = "Secure",
        color = Color(0xFF00E676),
        emoji = "🟢"
    );
}
