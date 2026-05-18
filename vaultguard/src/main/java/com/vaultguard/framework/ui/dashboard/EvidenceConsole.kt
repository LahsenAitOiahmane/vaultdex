package com.vaultguard.framework.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaultguard.framework.ui.theme.VGMonoFont

/**
 * Monospace evidence console — styled like a terminal/code viewer.
 * Shows raw evidence text from a security finding with a copy button.
 */
@Composable
fun EvidenceConsole(
    evidence: String,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current

    Card(
        modifier = modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(12.dp),
        colors   = CardDefaults.cardColors(
            containerColor = Color(0xFF0D1117)
        )
    ) {
        Column {
            // Terminal header bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF161B22))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Terminal dots
                    Box(Modifier.size(10.dp).background(Color(0xFFFF5F56), RoundedCornerShape(50)))
                    Box(Modifier.size(10.dp).background(Color(0xFFFFBD2E), RoundedCornerShape(50)))
                    Box(Modifier.size(10.dp).background(Color(0xFF27C93F), RoundedCornerShape(50)))
                }

                Text(
                    text  = "Evidence",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF8B949E),
                    fontFamily = VGMonoFont
                )

                TextButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(evidence))
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text  = "Copy",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF58A6FF),
                        fontFamily = VGMonoFont
                    )
                }
            }

            // Evidence content
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                Text(
                    text  = evidence,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily  = VGMonoFont,
                        lineHeight  = 18.sp,
                        letterSpacing = 0.sp
                    ),
                    color = Color(0xFF79C0FF)
                )
            }
        }
    }
}
