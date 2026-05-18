package com.vaultguard.framework.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultguard.framework.VaultGuard
import com.vaultguard.framework.core.FindingCategory
import com.vaultguard.framework.core.SecurityFinding
import com.vaultguard.framework.core.SecurityGrader
import com.vaultguard.framework.core.SeverityLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the VaultGuard dashboard.
 * Coordinates scan execution and provides filtered, graded results to the UI.
 */
class VaultGuardViewModel : ViewModel() {

    data class DashboardState(
        val findings: List<SecurityFinding> = emptyList(),
        val filteredFindings: List<SecurityFinding> = emptyList(),
        val grade: SecurityGrader.GradeResult = SecurityGrader.calculateGrade(emptyList()),
        val selectedCategory: FindingCategory? = null,  // null = ALL
        val selectedSeverity: SeverityLevel? = null,     // null = ALL
        val isScanning: Boolean = false,
        val scanTimeMs: Long = 0,
        val lastScanTimestamp: Long = 0,
        val scanEvents: List<String> = emptyList(),
        val expandedFindingIds: Set<String> = emptySet(),
        val packageName: String = "",
        val discoveryStats: String = ""
    )

    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state.asStateFlow()

    private val orchestrator = VaultGuard.orchestrator

    init {
        // Observe scan results from orchestrator
        viewModelScope.launch {
            orchestrator?.scanResult?.collect { result ->
                val grade = SecurityGrader.calculateGrade(result.findings)
                val stats = result.sandboxMap?.let { map ->
                    "Package: ${map.packageName}\n" +
                    "SharedPrefs: ${map.sharedPrefsFiles.size} files\n" +
                    "Databases: ${map.databaseFiles.size} files\n" +
                    "Internal files: ${map.internalFiles.size} files\n" +
                    "Cache files: ${map.cacheFiles.size} files"
                } ?: ""

                _state.value = _state.value.copy(
                    findings          = result.findings,
                    grade             = grade,
                    isScanning        = result.isScanning,
                    scanTimeMs        = result.scanTimeMs,
                    lastScanTimestamp  = result.lastScanTimestamp,
                    packageName       = result.sandboxMap?.packageName ?: "",
                    discoveryStats    = stats
                )
                applyFilters()
            }
        }

        // Observe scan log events
        viewModelScope.launch {
            orchestrator?.scanEvents?.collect { event ->
                val currentEvents = _state.value.scanEvents.takeLast(50) + event
                _state.value = _state.value.copy(scanEvents = currentEvents)
            }
        }

        // Trigger initial full scan
        startScan()
    }

    fun startScan() {
        viewModelScope.launch {
            orchestrator?.fullScan()
        }
    }

    fun selectCategory(category: FindingCategory?) {
        _state.value = _state.value.copy(selectedCategory = category)
        applyFilters()
    }

    fun selectSeverity(severity: SeverityLevel?) {
        _state.value = _state.value.copy(selectedSeverity = severity)
        applyFilters()
    }

    fun toggleFindingExpanded(findingId: String) {
        val current = _state.value.expandedFindingIds
        val updated = if (findingId in current) current - findingId else current + findingId
        _state.value = _state.value.copy(expandedFindingIds = updated)
    }

    private fun applyFilters() {
        val category = _state.value.selectedCategory
        val severity = _state.value.selectedSeverity

        val filtered = _state.value.findings.filter { finding ->
            (category == null || finding.category == category) &&
            (severity == null || finding.severity == severity)
        }

        _state.value = _state.value.copy(filteredFindings = filtered)
    }
}
