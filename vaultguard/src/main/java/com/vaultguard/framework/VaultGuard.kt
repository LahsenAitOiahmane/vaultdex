package com.vaultguard.framework

import android.content.Context
import android.content.Intent
import android.util.Log
import com.vaultguard.framework.monitor.SandboxFileObserver
import com.vaultguard.framework.monitor.ShakeDetector
import com.vaultguard.framework.scanner.ScanOrchestrator
import kotlinx.coroutines.*

/**
 * VaultGuard — Universal Secure Storage Inspector Framework.
 *
 * Public API singleton. Auto-initialized by VaultGuardInitializer (ContentProvider),
 * or manually via VaultGuard.init(context).
 *
 * Usage from host app:
 *   // Auto-initialized — just shake the device to open the dashboard.
 *   // Or manually:
 *   VaultGuard.launch(context)
 *
 * The framework is completely app-agnostic. It discovers the host app's
 * package name, data directories, SharedPreferences, databases, files,
 * and cache at runtime using only the Context.
 */
object VaultGuard {

    private const val TAG = "VaultGuard"

    private var isInitialized = false
    private var appContext: Context? = null

    // Core components
    internal var orchestrator: ScanOrchestrator? = null
        private set
    internal var fileObserver: SandboxFileObserver? = null
        private set
    private var shakeDetector: ShakeDetector? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /**
     * Initializes VaultGuard with the host application context.
     * Called automatically by VaultGuardInitializer, or manually by the host app.
     *
     * Sets up:
     *  - ScanOrchestrator for running audits
     *  - SandboxFileObserver for real-time monitoring
     *  - ShakeDetector for shake-to-launch
     */
    fun init(context: Context) {
        if (isInitialized) return

        appContext = context.applicationContext
        val ctx = appContext!!

        Log.i(TAG, "Initializing VaultGuard for package: ${ctx.packageName}")

        // Initialize core components
        orchestrator = ScanOrchestrator(ctx)

        fileObserver = SandboxFileObserver(
            dataDir  = ctx.applicationInfo.dataDir,
            filesDir = ctx.filesDir.absolutePath,
            cacheDir = ctx.cacheDir.absolutePath
        )

        shakeDetector = ShakeDetector(ctx)

        // Start file system monitoring
        fileObserver?.startWatching()

        // Wire file observer to micro-audits
        scope.launch {
            fileObserver?.fileChanges?.collect { event ->
                Log.d(TAG, "File change detected: ${event.path} (${event.eventType})")
                orchestrator?.microAudit(event.path)
            }
        }

        // Start shake detection
        shakeDetector?.startListening {
            Log.i(TAG, "Shake detected — launching dashboard")
            launch(ctx)
        }

        isInitialized = true
        Log.i(TAG, "VaultGuard initialized successfully")
    }

    /**
     * Launches the VaultGuard dashboard activity.
     * Can be called from anywhere with a valid Context.
     */
    fun launch(context: Context) {
        val ctx = context.applicationContext
        if (!isInitialized) init(ctx)

        val intent = Intent(ctx, VaultGuardActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(intent)
    }

    /**
     * Triggers a full security audit programmatically.
     * Results are available via orchestrator.scanResult StateFlow.
     */
    fun runAudit() {
        scope.launch {
            orchestrator?.fullScan()
        }
    }

    /**
     * Cleans up all resources. Call when the host app is being destroyed.
     * Usually not needed as VaultGuard is meant to live for the app's lifetime.
     */
    fun destroy() {
        fileObserver?.stopWatching()
        shakeDetector?.stopListening()
        scope.cancel()
        isInitialized = false
        Log.i(TAG, "VaultGuard destroyed")
    }
}
