package com.vaultguard.framework.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaultguard.framework.core.SecurityGrader
import com.vaultguard.framework.core.SeverityLevel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Hero card showing the overall security health grade (A-F),
 * severity breakdown counts, and scan metadata.
 */
@Composable
fun SecurityScoreCard(
    grade: SecurityGrader.GradeResult,
    scanTimeMs: Long,
    lastScanTimestamp: Long,
    packageName: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF0D1117),
                            Color(0xFF161B22),
                            Color(0xFF1C2333)
                        )
                    )
                )
                .padding(24.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text  = "Security Health",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color(0xFF8B949E)
                        )
                        if (packageName.isNotBlank()) {
                            Text(
                                text  = packageName,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF8B949E).copy(alpha = 0.6f)
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint     = grade.color.copy(alpha = 0.6f),
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Grade circle
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(grade.color.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(grade.color.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text  = grade.grade,
                                fontSize = 48.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = grade.color
                            )
                            Text(
                                text  = grade.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = grade.color.copy(alpha = 0.8f)
                            )
                        }
                    }
                }

                // Score
                Text(
                    text  = "Risk Score: ${grade.score}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF8B949E)
                )

                // Severity breakdown
                Divider(color = Color(0xFF30363D))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    SeverityCounter("Critical", grade.criticalCount, SeverityLevel.CRITICAL.color)
                    SeverityCounter("High", grade.highCount, SeverityLevel.HIGH.color)
                    SeverityCounter("Medium", grade.mediumCount, SeverityLevel.MEDIUM.color)
                    SeverityCounter("Low", grade.lowCount, SeverityLevel.LOW.color)
                    SeverityCounter("Secure", grade.secureCount, SeverityLevel.SECURE.color)
                }

                // Scan metadata
                Divider(color = Color(0xFF30363D))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text  = "${grade.totalFindings} findings",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF8B949E)
                    )
                    Text(
                        text  = "Scan: ${scanTimeMs}ms",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF8B949E)
                    )
                    if (lastScanTimestamp > 0) {
                        Text(
                            text  = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(lastScanTimestamp)),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF8B949E)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SeverityCounter(label: String, count: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text  = count.toString(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = if (count > 0) color else Color(0xFF30363D)
        )
        Text(
            text  = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF8B949E),
            textAlign = TextAlign.Center
        )
    }
}
