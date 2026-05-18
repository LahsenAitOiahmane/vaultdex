package com.vaultguard.framework.scanner

import android.content.Context
import com.vaultguard.framework.core.SecurityFinding
import com.vaultguard.framework.discovery.SandboxDiscovery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Orchestrates all four scanners and coordinates the full audit workflow.
 *
 * Flow:
 *  1. SandboxDiscovery maps the host app's data directories
 *  2. All four scanners run in parallel via coroutines
 *  3. Results are merged and emitted via StateFlow
 *  4. SandboxFileObserver triggers micro-audits for live updates
 */
class ScanOrchestrator(private val context: Context) {

    private val discovery      = SandboxDiscovery(context)
    private val prefsScanner   = SharedPrefsScanner(context)
    private val dbScanner      = DatabaseScanner()
    private val fileScanner    = FileScanner()
    private val cacheScanner   = CacheScanner()

    data class ScanResult(
        val findings: List<SecurityFinding> = emptyList(),
        val sandboxMap: SandboxDiscovery.SandboxMap? = null,
        val scanTimeMs: Long = 0,
        val isScanning: Boolean = false,
        val lastScanTimestamp: Long = 0
    )

    private val _scanResult = MutableStateFlow(ScanResult())
    val scanResult: StateFlow<ScanResult> = _scanResult.asStateFlow()

    private val _scanEvents = MutableSharedFlow<String>(extraBufferCapacity = 50)
    val scanEvents: SharedFlow<String> = _scanEvents.asSharedFlow()

    /**
     * Performs a full security audit of the host app's sandbox.
     * All four scanners run in parallel for performance.
     */
    suspend fun fullScan() {
        _scanResult.value = _scanResult.value.copy(isScanning = true)
        _scanEvents.emit("Starting full sandbox audit...")

        val startTime = System.currentTimeMillis()

        try {
            // Phase 1: Discovery
            _scanEvents.emit("Phase 1: Discovering sandbox layout...")
            val sandboxMap = withContext(Dispatchers.IO) { discovery.discover() }

            _scanEvents.emit("Found: ${sandboxMap.sharedPrefsFiles.size} SharedPrefs, ${sandboxMap.databaseFiles.size} databases, ${sandboxMap.internalFiles.size} files, ${sandboxMap.cacheFiles.size} cache files")

            // Phase 2: Parallel scanning
            _scanEvents.emit("Phase 2: Running security scanners...")

            val allFindings = coroutineScope {
                val prefsJob = async(Dispatchers.IO) {
                    _scanEvents.emit("  → Scanning SharedPreferences...")
                    prefsScanner.scan(sandboxMap.sharedPrefsFiles)
                }
                val dbJob = async(Dispatchers.IO) {
                    _scanEvents.emit("  → Probing databases...")
                    dbScanner.scan(sandboxMap.databaseFiles)
                }
                val filesJob = async(Dispatchers.IO) {
                    _scanEvents.emit("  → Scanning internal files...")
                    fileScanner.scan(sandboxMap.internalFiles)
                }
                val cacheJob = async(Dispatchers.IO) {
                    _scanEvents.emit("  → Scanning cache...")
                    cacheScanner.scan(sandboxMap.cacheFiles)
                }

                // Await all results
                val prefsFindings  = prefsJob.await()
                val dbFindings     = dbJob.await()
                val fileFindings   = filesJob.await()
                val cacheFindings  = cacheJob.await()

                // Merge all findings, deduplicate by title + source
                (prefsFindings + dbFindings + fileFindings + cacheFindings)
                    .distinctBy { "${it.title}|${it.source}" }
                    .sortedByDescending { it.severity.weight }
            }

            val elapsed = System.currentTimeMillis() - startTime
            _scanEvents.emit("Audit complete: ${allFindings.size} findings in ${elapsed}ms")

            _scanResult.value = ScanResult(
                findings          = allFindings,
                sandboxMap        = sandboxMap,
                scanTimeMs        = elapsed,
                isScanning        = false,
                lastScanTimestamp = System.currentTimeMillis()
            )

        } catch (e: Exception) {
            val elapsed = System.currentTimeMillis() - startTime
            _scanEvents.emit("Audit failed: ${e.message}")
            _scanResult.value = _scanResult.value.copy(
                isScanning        = false,
                scanTimeMs        = elapsed,
                lastScanTimestamp = System.currentTimeMillis()
            )
        }
    }

    /**
     * Micro-audit: re-scans only the specific area that changed.
     * Called by SandboxFileObserver when a file modification is detected.
     */
    suspend fun microAudit(changedPath: String) {
        _scanEvents.emit("Micro-audit triggered: $changedPath")

        try {
            val sandboxMap = _scanResult.value.sandboxMap ?: withContext(Dispatchers.IO) { discovery.discover() }
            val newFindings = withContext(Dispatchers.IO) {
                when {
                    changedPath.contains("shared_prefs") -> {
                        _scanEvents.emit("  → Re-scanning SharedPreferences...")
                        prefsScanner.scan(sandboxMap.sharedPrefsFiles)
                    }
                    changedPath.contains("databases") -> {
                        _scanEvents.emit("  → Re-scanning databases...")
                        dbScanner.scan(sandboxMap.databaseFiles)
                    }
                    changedPath.contains(sandboxMap.cacheDir.absolutePath) -> {
                        _scanEvents.emit("  → Re-scanning cache...")
                        cacheScanner.scan(sandboxMap.cacheFiles)
                    }
                    else -> {
                        _scanEvents.emit("  → Re-scanning files...")
                        fileScanner.scan(sandboxMap.internalFiles)
                    }
                }
            }

            // Merge new findings with existing ones from other categories
            val existingOtherCategory = _scanResult.value.findings.filter { existing ->
                newFindings.none { "${it.title}|${it.source}" == "${existing.title}|${existing.source}" }
            }

            val merged = (existingOtherCategory + newFindings)
                .distinctBy { "${it.title}|${it.source}" }
                .sortedByDescending { it.severity.weight }

            _scanResult.value = _scanResult.value.copy(
                findings          = merged,
                lastScanTimestamp = System.currentTimeMillis()
            )

            _scanEvents.emit("Micro-audit complete: ${newFindings.size} findings updated")

        } catch (e: Exception) {
            _scanEvents.emit("Micro-audit failed: ${e.message}")
        }
    }
}
