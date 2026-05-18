package com.vaultguard.framework.scanner

import android.database.sqlite.SQLiteDatabase
import com.vaultguard.framework.core.FindingCategory
import com.vaultguard.framework.core.SecurityFinding
import com.vaultguard.framework.core.SeverityLevel
import com.vaultguard.framework.heuristics.HeuristicsEngine
import java.io.File

/**
 * Dynamically probes all discovered SQLite database files in the host app's sandbox.
 *
 * Strategy:
 *  1. Attempt to open each .db file with SQLiteDatabase.openDatabase() in read-only mode
 *  2. If it opens successfully → the database is UNENCRYPTED (finding!)
 *  3. Query sqlite_master to discover all table names dynamically
 *  4. For each table, read up to N rows and run heuristics on cell values
 *  5. If it fails to open → likely encrypted with SQLCipher (secure)
 */
class DatabaseScanner {

    companion object {
        private const val MAX_ROWS_PER_TABLE = 50
        private const val MAX_TABLES = 20
    }

    /**
     * Scans all database files and returns security findings.
     */
    fun scan(databaseFiles: List<File>): List<SecurityFinding> {
        val allFindings = mutableListOf<SecurityFinding>()

        for (dbFile in databaseFiles) {
            val findings = scanSingleDatabase(dbFile)
            allFindings.addAll(findings)
        }

        return allFindings
    }

    private fun scanSingleDatabase(dbFile: File): List<SecurityFinding> {
        val findings = mutableListOf<SecurityFinding>()
        val dbName = dbFile.name
        val filePath = dbFile.absolutePath

        var db: SQLiteDatabase? = null
        try {
            // Attempt to open without a password — if this succeeds, the DB is unencrypted
            db = SQLiteDatabase.openDatabase(
                filePath,
                null,
                SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
            )

            // If we reach here, the database is UNENCRYPTED — this is a finding
            findings.add(
                SecurityFinding(
                    category    = FindingCategory.DATABASE,
                    severity    = SeverityLevel.HIGH,
                    title       = "Unencrypted database: $dbName",
                    description = "Database file can be opened without a password. All data is stored in plaintext SQLite format and can be read by any process with file access.",
                    evidence    = "File: $dbName\nPath: $filePath\nSize: ${dbFile.length()} bytes\nStatus: Opened successfully WITHOUT encryption",
                    filePath    = filePath,
                    source      = dbName,
                    recommendation = "Encrypt the database using SQLCipher with a key derived from Android KeyStore. Use Room's SupportFactory for transparent encryption."
                )
            )

            // Discover tables dynamically via sqlite_master
            val tables = discoverTables(db)

            for (tableName in tables) {
                val tableFindings = scanTable(db, tableName, dbName, filePath)
                findings.addAll(tableFindings)
            }

        } catch (e: Exception) {
            // Failed to open — likely encrypted with SQLCipher or corrupted
            val isEncrypted = e.message?.contains("not a database", ignoreCase = true) == true ||
                    e.message?.contains("file is not a database", ignoreCase = true) == true

            if (isEncrypted) {
                findings.add(
                    SecurityFinding(
                        category    = FindingCategory.DATABASE,
                        severity    = SeverityLevel.SECURE,
                        title       = "$dbName appears encrypted",
                        description = "Database cannot be opened without a password — likely encrypted with SQLCipher or similar.",
                        evidence    = "File: $dbName\nPath: $filePath\nSize: ${dbFile.length()} bytes\nError: ${e.message}",
                        filePath    = filePath,
                        source      = dbName,
                        recommendation = "No action needed — database appears to be properly encrypted."
                    )
                )
            }
        } finally {
            try { db?.close() } catch (_: Exception) {}
        }

        return findings
    }

    /**
     * Discovers all user-created tables by querying sqlite_master.
     * Excludes Android internal tables (android_metadata, room_master_table, sqlite_*).
     */
    private fun discoverTables(db: SQLiteDatabase): List<String> {
        val tables = mutableListOf<String>()
        val internalTables = setOf(
            "android_metadata", "room_master_table", "sqlite_sequence"
        )

        try {
            val cursor = db.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' ORDER BY name",
                null
            )
            cursor.use {
                while (it.moveToNext() && tables.size < MAX_TABLES) {
                    val name = it.getString(0)
                    if (!name.startsWith("sqlite_") && name !in internalTables) {
                        tables.add(name)
                    }
                }
            }
        } catch (e: Exception) {
            // Silently continue — some tables might not be readable
        }

        return tables
    }

    /**
     * Scans a single table by reading rows and running heuristic analysis on cell values.
     */
    private fun scanTable(
        db: SQLiteDatabase,
        tableName: String,
        dbName: String,
        filePath: String
    ): List<SecurityFinding> {
        val findings = mutableListOf<SecurityFinding>()

        try {
            val cursor = db.rawQuery("SELECT * FROM \"$tableName\" LIMIT $MAX_ROWS_PER_TABLE", null)
            cursor.use {
                val columnNames = it.columnNames
                var rowCount = 0

                // Track which columns have already generated findings to avoid duplicates
                val flaggedColumns = mutableSetOf<String>()

                while (it.moveToNext()) {
                    rowCount++
                    for (colIdx in columnNames.indices) {
                        val colName = columnNames[colIdx]
                        if (colName in flaggedColumns) continue

                        val cellValue = try { it.getString(colIdx) } catch (_: Exception) { null }
                        if (cellValue.isNullOrBlank()) continue

                        val cellFindings = HeuristicsEngine.analyzeDatabaseCell(
                            value      = cellValue,
                            columnName = colName,
                            tableName  = tableName,
                            dbName     = dbName,
                            filePath   = filePath
                        )

                        if (cellFindings.isNotEmpty()) {
                            findings.addAll(cellFindings)
                            flaggedColumns.add(colName) // Don't re-flag same column
                        }
                    }
                }

                // Add table-level info finding
                if (rowCount > 0) {
                    findings.add(
                        SecurityFinding(
                            category    = FindingCategory.DATABASE,
                            severity    = SeverityLevel.MEDIUM,
                            title       = "Table \"$tableName\" has $rowCount readable rows",
                            description = "Table \"$tableName\" in unencrypted database \"$dbName\" contains $rowCount rows with ${columnNames.size} columns, all readable without authentication.",
                            evidence    = "Database: $dbName\nTable: $tableName\nRows: $rowCount\nColumns: ${columnNames.joinToString(", ")}",
                            filePath    = filePath,
                            source      = "$dbName → $tableName",
                            recommendation = "Encrypt the database with SQLCipher to prevent unauthorized access to table data."
                        )
                    )
                }
            }
        } catch (e: Exception) {
            // Table might have unusual schema — skip it
        }

        return findings
    }
}
