package com.vaultguard.framework.monitor

import android.os.FileObserver
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.File

/**
 * Real-time file system watcher for the host app's data directories.
 *
 * Uses Android's native FileObserver API to monitor:
 *  - shared_prefs/ directory (for SharedPreferences changes)
 *  - databases/ directory (for database writes)
 *  - filesDir (for internal file changes)
 *  - cacheDir (for cache modifications)
 *
 * When a file change is detected, it emits the changed path via SharedFlow,
 * which the ScanOrchestrator uses to trigger a micro-audit.
 */
class SandboxFileObserver(
    private val dataDir: String,
    private val filesDir: String,
    private val cacheDir: String
) {
    data class FileChangeEvent(
        val path: String,
        val eventType: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    private val _fileChanges = MutableSharedFlow<FileChangeEvent>(extraBufferCapacity = 100)
    val fileChanges: SharedFlow<FileChangeEvent> = _fileChanges.asSharedFlow()

    private val observers = mutableListOf<FileObserver>()
    private var isWatching = false

    // Debounce: ignore rapid successive events on the same path
    private val recentEvents = mutableMapOf<String, Long>()
    private val debounceMs = 1000L // 1 second debounce window

    // Events we care about
    private val WATCH_MASK = FileObserver.CREATE or
            FileObserver.MODIFY or
            FileObserver.MOVED_TO or
            FileObserver.CLOSE_WRITE

    /**
     * Starts monitoring all data directories.
     * Call this when VaultGuard initializes.
     */
    fun startWatching() {
        if (isWatching) return
        isWatching = true

        val dirsToWatch = listOf(
            File(dataDir, "shared_prefs"),
            File(dataDir, "databases"),
            File(filesDir),
            File(cacheDir)
        )

        for (dir in dirsToWatch) {
            if (!dir.exists()) continue

            try {
                val observer = object : FileObserver(dir.absolutePath, WATCH_MASK) {
                    override fun onEvent(event: Int, path: String?) {
                        if (path == null) return
                        val fullPath = "${dir.absolutePath}/$path"

                        // Debounce rapid events
                        val now = System.currentTimeMillis()
                        val lastEvent = recentEvents[fullPath] ?: 0L
                        if (now - lastEvent < debounceMs) return
                        recentEvents[fullPath] = now

                        val eventName = when (event and 0xFFF) {
                            CREATE      -> "CREATE"
                            MODIFY      -> "MODIFY"
                            MOVED_TO    -> "MOVED_TO"
                            CLOSE_WRITE -> "CLOSE_WRITE"
                            else        -> "OTHER"
                        }

                        _fileChanges.tryEmit(FileChangeEvent(fullPath, eventName))
                    }
                }
                observer.startWatching()
                observers.add(observer)
            } catch (e: Exception) {
                // Some directories may not be watchable — continue
            }
        }

        // Also watch subdirectories of filesDir (like backup/)
        try {
            val filesRoot = File(filesDir)
            filesRoot.listFiles()?.filter { it.isDirectory }?.forEach { subDir ->
                try {
                    val observer = object : FileObserver(subDir.absolutePath, WATCH_MASK) {
                        override fun onEvent(event: Int, path: String?) {
                            if (path == null) return
                            val fullPath = "${subDir.absolutePath}/$path"
                            val now = System.currentTimeMillis()
                            val lastEvent = recentEvents[fullPath] ?: 0L
                            if (now - lastEvent < debounceMs) return
                            recentEvents[fullPath] = now
                            _fileChanges.tryEmit(FileChangeEvent(fullPath, "MODIFY"))
                        }
                    }
                    observer.startWatching()
                    observers.add(observer)
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
    }

    /**
     * Stops all file observers.
     * Call this when VaultGuard is being destroyed.
     */
    fun stopWatching() {
        isWatching = false
        observers.forEach { observer ->
            try { observer.stopWatching() } catch (_: Exception) {}
        }
        observers.clear()
        recentEvents.clear()
    }
}
