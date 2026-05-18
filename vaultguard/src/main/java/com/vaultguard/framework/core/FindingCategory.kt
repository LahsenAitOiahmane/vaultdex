package com.vaultguard.framework.core

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Categories of storage areas that VaultGuard inspects.
 * Used for filtering findings in the dashboard UI.
 */
enum class FindingCategory(
    val displayName: String,
    val icon: ImageVector,
    val description: String
) {
    SHARED_PREFS(
        displayName = "SharedPreferences",
        icon = Icons.Default.Settings,
        description = "XML key-value storage in shared_prefs/"
    ),
    DATABASE(
        displayName = "Databases",
        icon = Icons.Default.Storage,
        description = "SQLite/Room database files in databases/"
    ),
    FILES(
        displayName = "Internal Files",
        icon = Icons.Default.InsertDriveFile,
        description = "Files in the app's internal filesDir"
    ),
    CACHE(
        displayName = "Cache",
        icon = Icons.Default.Cached,
        description = "Cached data in cacheDir"
    );
}
