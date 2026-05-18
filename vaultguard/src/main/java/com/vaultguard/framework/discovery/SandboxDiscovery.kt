package com.vaultguard.framework.discovery

import android.content.Context
import java.io.File

/**
 * Dynamically discovers all storage locations within the host app's sandbox.
 *
 * This class is completely app-agnostic — it uses only the host Context to
 * resolve paths at runtime. No hardcoded file names or package references.
 */
class SandboxDiscovery(private val context: Context) {

    /**
     * Complete map of the host app's sandbox storage locations.
     */
    data class SandboxMap(
        val packageName: String,
        val dataDir: File,
        val filesDir: File,
        val cacheDir: File,
        val sharedPrefsDir: File,
        val databasesDir: File,
        val sharedPrefsFiles: List<File>,
        val databaseFiles: List<File>,
        val internalFiles: List<File>,
        val cacheFiles: List<File>
    )

    /**
     * Performs full sandbox discovery using the host application Context.
     * Returns a SandboxMap with all discovered paths and files.
     */
    fun discover(): SandboxMap {
        val packageName  = context.packageName
        val dataDir      = File(context.applicationInfo.dataDir)
        val filesDir     = context.filesDir
        val cacheDir     = context.cacheDir
        val sharedPrefsDir = File(dataDir, "shared_prefs")
        val databasesDir   = File(dataDir, "databases")

        return SandboxMap(
            packageName     = packageName,
            dataDir         = dataDir,
            filesDir        = filesDir,
            cacheDir        = cacheDir,
            sharedPrefsDir  = sharedPrefsDir,
            databasesDir    = databasesDir,
            sharedPrefsFiles = discoverSharedPrefs(sharedPrefsDir),
            databaseFiles    = discoverDatabases(databasesDir),
            internalFiles    = discoverFilesRecursive(filesDir),
            cacheFiles       = discoverFilesRecursive(cacheDir)
        )
    }

    /**
     * Enumerates all .xml files in the shared_prefs/ directory.
     * These are the raw SharedPreferences files created by the host app.
     */
    private fun discoverSharedPrefs(dir: File): List<File> {
        if (!dir.exists() || !dir.isDirectory) return emptyList()
        return dir.listFiles()
            ?.filter { it.isFile && it.extension == "xml" }
            ?.sortedBy { it.name }
            ?: emptyList()
    }

    /**
     * Discovers database files using two strategies:
     *  1. context.databaseList() — the official Android API
     *  2. Direct directory scan for .db / .sqlite / -journal files
     *
     * This catches databases that might not be returned by databaseList()
     * (e.g., databases opened with direct SQLiteDatabase.openOrCreateDatabase).
     */
    private fun discoverDatabases(dir: File): List<File> {
        val dbFiles = mutableSetOf<File>()

        // Strategy 1: Android API
        try {
            context.databaseList().forEach { dbName ->
                val dbFile = context.getDatabasePath(dbName)
                if (dbFile.exists() && !dbName.endsWith("-journal") && !dbName.endsWith("-wal") && !dbName.endsWith("-shm")) {
                    dbFiles.add(dbFile)
                }
            }
        } catch (e: Exception) {
            // Silently continue — some contexts may not support databaseList()
        }

        // Strategy 2: Direct directory scan
        if (dir.exists() && dir.isDirectory) {
            dir.listFiles()?.forEach { file ->
                if (file.isFile && !file.name.endsWith("-journal") &&
                    !file.name.endsWith("-wal") && !file.name.endsWith("-shm")) {
                    val ext = file.extension.lowercase()
                    if (ext == "db" || ext == "sqlite" || ext == "sqlite3" || ext == "") {
                        // Files without extension might be databases too — check magic bytes
                        if (ext.isNotEmpty() || isSqliteFile(file)) {
                            dbFiles.add(file)
                        }
                    }
                }
            }
        }

        return dbFiles.sortedBy { it.name }
    }

    /**
     * Recursively walks a directory tree collecting all files.
     * Limits depth to 5 levels and file count to 500 to prevent excessive scanning.
     */
    private fun discoverFilesRecursive(dir: File, maxDepth: Int = 5, maxFiles: Int = 500): List<File> {
        if (!dir.exists() || !dir.isDirectory) return emptyList()
        val result = mutableListOf<File>()
        walkDirectory(dir, 0, maxDepth, maxFiles, result)
        return result.sortedBy { it.absolutePath }
    }

    private fun walkDirectory(dir: File, depth: Int, maxDepth: Int, maxFiles: Int, accumulator: MutableList<File>) {
        if (depth > maxDepth || accumulator.size >= maxFiles) return
        dir.listFiles()?.forEach { file ->
            if (accumulator.size >= maxFiles) return
            if (file.isFile) {
                accumulator.add(file)
            } else if (file.isDirectory) {
                walkDirectory(file, depth + 1, maxDepth, maxFiles, accumulator)
            }
        }
    }

    /**
     * Checks if a file starts with the SQLite magic bytes: "SQLite format 3\000"
     */
    private fun isSqliteFile(file: File): Boolean {
        return try {
            if (file.length() < 16) return false
            val header = ByteArray(16)
            file.inputStream().use { it.read(header) }
            String(header, 0, 15, Charsets.US_ASCII) == "SQLite format 3"
        } catch (e: Exception) {
            false
        }
    }
}
