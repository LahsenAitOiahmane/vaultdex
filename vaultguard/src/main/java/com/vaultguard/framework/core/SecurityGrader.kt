package com.vaultguard.framework.core

import androidx.compose.ui.graphics.Color

/**
 * Calculates an overall security health grade (A through F) from a list of findings.
 *
 * Scoring algorithm:
 *  1. Sum all finding severity weights: CRITICAL=100, HIGH=40, MEDIUM=15, LOW=5, SECURE=0
 *  2. Map total score to a letter grade using thresholds
 *
 * Grade thresholds:
 *   A  = 0 points        (no issues found)
 *   B  = 1..20 points    (minor issues only)
 *   C  = 21..60 points   (moderate risk)
 *   D  = 61..120 points  (high risk)
 *   F  = 121+ points     (critical exposure)
 */
object SecurityGrader {

    data class GradeResult(
        val grade: String,
        val score: Int,
        val color: Color,
        val label: String,
        val criticalCount: Int,
        val highCount: Int,
        val mediumCount: Int,
        val lowCount: Int,
        val secureCount: Int,
        val totalFindings: Int
    )

    fun calculateGrade(findings: List<SecurityFinding>): GradeResult {
        val score = findings.sumOf { it.severity.weight }

        val criticalCount = findings.count { it.severity == SeverityLevel.CRITICAL }
        val highCount     = findings.count { it.severity == SeverityLevel.HIGH }
        val mediumCount   = findings.count { it.severity == SeverityLevel.MEDIUM }
        val lowCount      = findings.count { it.severity == SeverityLevel.LOW }
        val secureCount   = findings.count { it.severity == SeverityLevel.SECURE }

        val (grade, color, label) = when {
            score == 0  -> Triple("A", Color(0xFF00E676), "Excellent")
            score <= 20 -> Triple("B", Color(0xFF69F0AE), "Good")
            score <= 60 -> Triple("C", Color(0xFFFFAB00), "Fair")
            score <= 120 -> Triple("D", Color(0xFFFF6D00), "Poor")
            else         -> Triple("F", Color(0xFFFF1744), "Critical")
        }

        return GradeResult(
            grade         = grade,
            score         = score,
            color         = color,
            label         = label,
            criticalCount = criticalCount,
            highCount     = highCount,
            mediumCount   = mediumCount,
            lowCount      = lowCount,
            secureCount   = secureCount,
            totalFindings = findings.size
        )
    }
}
