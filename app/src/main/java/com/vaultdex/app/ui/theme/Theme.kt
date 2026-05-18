package com.vaultdex.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary          = VaultPurple80,
    onPrimary        = VaultPurple10,
    primaryContainer = VaultPurple40,
    secondary        = VaultCyan80,
    onSecondary      = VaultGray10,
    secondaryContainer = VaultCyan40,
    background       = VaultGray10,
    onBackground     = VaultGray95,
    surface          = SurfaceDark,
    onSurface        = VaultGray95,
    surfaceVariant   = CardDark,
    error            = ErrorRed
)

private val LightColorScheme = lightColorScheme(
    primary          = VaultPurple40,
    onPrimary        = SurfaceLight,
    primaryContainer = VaultPurple80,
    secondary        = VaultCyan40,
    onSecondary      = SurfaceLight,
    secondaryContainer = VaultCyan80,
    background       = SurfaceLight,
    onBackground     = VaultGray20,
    surface          = SurfaceLight,
    onSurface        = VaultGray20,
    surfaceVariant   = CardLight,
    error            = ErrorRed
)

@Composable
fun VaultDexTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = VaultTypography,
        content     = content
    )
}
