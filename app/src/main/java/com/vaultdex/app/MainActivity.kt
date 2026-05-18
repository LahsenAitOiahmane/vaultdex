package com.vaultdex.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.vaultdex.app.ui.navigation.VaultDexNavGraph
import com.vaultdex.app.ui.theme.VaultDexTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VaultDexTheme {
                VaultDexNavGraph()
            }
        }
    }
}
