package com.vaultguard.framework

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.vaultguard.framework.ui.dashboard.DashboardScreen
import com.vaultguard.framework.ui.theme.VaultGuardTheme

/**
 * Standalone Activity hosting the VaultGuard dashboard UI.
 *
 * Launched by:
 *  - Shaking the device (ShakeDetector)
 *  - Calling VaultGuard.launch(context) programmatically
 *
 * Uses its own self-contained Material 3 theme (VaultGuardTheme)
 * that does not interfere with the host app's theme.
 */
class VaultGuardActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VaultGuardTheme {
                DashboardScreen(
                    onClose = { finish() }
                )
            }
        }
    }
}
