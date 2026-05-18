package com.vaultguard.framework.ui.dashboard

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vaultguard.framework.core.FindingCategory
import com.vaultguard.framework.core.SeverityLevel
import com.vaultguard.framework.ui.VaultGuardViewModel
import com.vaultguard.framework.ui.theme.VGMonoFont

/**
 * Main VaultGuard dashboard screen.
 *
 * Features:
 *  - Security grade hero card
 *  - Category filter chips (All / SharedPrefs / Database / Files / Cache)
 *  - Severity filter chips
 *  - Scrollable list of expandable finding cards
 *  - Pull-to-refresh triggers rescan
 *  - Live scan event log
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onClose: () -> Unit,
    viewModel: VaultGuardViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    var showScanLog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text  = "VaultGuard",
                            fontWeight = FontWeight.ExtraBold
                        )
                        if (state.isScanning) {
                            CircularProgressIndicator(
                                modifier    = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color       = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, "Close")
                    }
                },
                actions = {
                    IconButton(onClick = { showScanLog = !showScanLog }) {
                        Icon(
                            Icons.Default.Terminal,
                            "Scan Log",
                            tint = if (showScanLog) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { viewModel.startScan() }) {
                        Icon(Icons.Default.Refresh, "Rescan")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isScanning,
            onRefresh    = { viewModel.startScan() },
            modifier     = Modifier.padding(padding)
        ) {
            LazyColumn(
                modifier            = Modifier.fillMaxSize(),
                contentPadding      = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Scan log (collapsible)
                if (showScanLog) {
                    item(key = "scan_log") {
                        ScanLogCard(events = state.scanEvents)
                    }
                }

                // Score card
                item(key = "score_card") {
                    SecurityScoreCard(
                        grade             = state.grade,
                        scanTimeMs        = state.scanTimeMs,
                        lastScanTimestamp  = state.lastScanTimestamp,
                        packageName       = state.packageName
                    )
                }

                // Category filters
                item(key = "category_filters") {
                    CategoryFilterChips(
                        selectedCategory = state.selectedCategory,
                        onSelect         = { viewModel.selectCategory(it) },
                        findings         = state.findings
                    )
                }

                // Severity filters
                item(key = "severity_filters") {
                    SeverityFilterChips(
                        selectedSeverity = state.selectedSeverity,
                        onSelect         = { viewModel.selectSeverity(it) }
                    )
                }

                // Results header
                item(key = "results_header") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text  = "Findings (${state.filteredFindings.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (state.filteredFindings.size != state.findings.size) {
                            Text(
                                text  = "of ${state.findings.size} total",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Finding cards
                if (state.filteredFindings.isEmpty() && !state.isScanning) {
                    item(key = "empty_state") {
                        EmptyState(hasFilters = state.selectedCategory != null || state.selectedSeverity != null)
                    }
                } else {
                    items(
                        items = state.filteredFindings,
                        key   = { it.id }
                    ) { finding ->
                        FindingCard(
                            finding         = finding,
                            isExpanded      = finding.id in state.expandedFindingIds,
                            onToggleExpand  = { viewModel.toggleFindingExpanded(finding.id) }
                        )
                    }
                }

                // Bottom spacer
                item(key = "spacer") {
                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
private fun CategoryFilterChips(
    selectedCategory: FindingCategory?,
    onSelect: (FindingCategory?) -> Unit,
    findings: List<com.vaultguard.framework.core.SecurityFinding>
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text  = "Category",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // "All" chip
            FilterChip(
                selected = selectedCategory == null,
                onClick  = { onSelect(null) },
                label    = { Text("All (${findings.size})") },
                leadingIcon = if (selectedCategory == null) {
                    { Icon(Icons.Default.Check, null, Modifier.size(14.dp)) }
                } else null
            )

            // Category chips
            FindingCategory.entries.forEach { category ->
                val count = findings.count { it.category == category }
                FilterChip(
                    selected = selectedCategory == category,
                    onClick  = { onSelect(if (selectedCategory == category) null else category) },
                    label    = { Text("${category.displayName} ($count)") },
                    leadingIcon = {
                        Icon(category.icon, null, Modifier.size(14.dp))
                    }
                )
            }
        }
    }
}

@Composable
private fun SeverityFilterChips(
    selectedSeverity: SeverityLevel?,
    onSelect: (SeverityLevel?) -> Unit
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SeverityLevel.entries.filter { it != SeverityLevel.SECURE }.forEach { severity ->
            FilterChip(
                selected = selectedSeverity == severity,
                onClick  = { onSelect(if (selectedSeverity == severity) null else severity) },
                label    = {
                    Text(
                        text  = "${severity.emoji} ${severity.displayName}",
                        style = MaterialTheme.typography.labelSmall
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = severity.color.copy(alpha = 0.2f)
                )
            )
        }
    }
}

@Composable
private fun ScanLogCard(events: List<String>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(16.dp),
        colors   = CardDefaults.cardColors(containerColor = Color(0xFF0D1117))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Terminal, null, tint = Color(0xFF8B949E), modifier = Modifier.size(16.dp))
                    Text("Scan Log", style = MaterialTheme.typography.labelMedium, color = Color(0xFF8B949E), fontFamily = VGMonoFont)
                }
                Text("${events.size} events", style = MaterialTheme.typography.labelSmall, color = Color(0xFF484F58), fontFamily = VGMonoFont)
            }

            Spacer(Modifier.height(8.dp))

            val displayEvents = events.takeLast(15)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                displayEvents.forEach { event ->
                    val color = when {
                        event.contains("CRITICAL", ignoreCase = true) || event.contains("failed", ignoreCase = true) -> Color(0xFFFF7B72)
                        event.contains("complete", ignoreCase = true) -> Color(0xFF7EE787)
                        event.contains("→") -> Color(0xFF79C0FF)
                        else -> Color(0xFF8B949E)
                    }
                    Text(
                        text  = "▸ $event",
                        style = MaterialTheme.typography.labelSmall,
                        color = color,
                        fontFamily = VGMonoFont
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState(hasFilters: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(16.dp),
        colors   = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = if (hasFilters) Icons.Default.FilterAlt else Icons.Default.CheckCircle,
                contentDescription = null,
                tint     = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                modifier = Modifier.size(48.dp)
            )
            Text(
                text  = if (hasFilters) "No findings match filters" else "No issues found",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Text(
                text  = if (hasFilters) "Try removing some filters"
                        else "The host app's storage appears clean. Use the app and rescan.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
        }
    }
}
