package com.vaultdex.app

import android.app.Application
import com.vaultdex.app.data.db.AppDatabase

class VaultDexApp : Application() {

    // Singleton database instance exposed for use in ViewModels via application context
    val database: AppDatabase by lazy {
        AppDatabase.getDatabase(this)
    }
}
