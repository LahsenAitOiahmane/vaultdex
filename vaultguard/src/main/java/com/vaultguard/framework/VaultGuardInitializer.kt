package com.vaultguard.framework

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.util.Log

/**
 * Zero-configuration auto-initializer using the ContentProvider trick.
 *
 * Android merges this ContentProvider from the library manifest into the host app's
 * manifest at build time. ContentProviders are instantiated before Application.onCreate(),
 * so VaultGuard initializes automatically without any code changes in the host app.
 *
 * This is the same pattern used by Firebase, WorkManager, and AndroidX Startup.
 */
class VaultGuardInitializer : ContentProvider() {

    override fun onCreate(): Boolean {
        val ctx = context ?: return false
        Log.i("VaultGuard", "Auto-initializing via ContentProvider for: ${ctx.packageName}")
        VaultGuard.init(ctx.applicationContext)
        return true
    }

    // ContentProvider contract stubs — not used for data access
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
                       selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?,
                        selectionArgs: Array<out String>?): Int = 0
}
