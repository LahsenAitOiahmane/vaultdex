package com.vaultguard.framework.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

/**
 * Self-contained Material 3 theme for VaultGuard dashboard.
 * Uses an emerald/green cybersecurity color scheme — visually distinct
 * from any host app theme to clearly identify the inspector overlay.
 */

// --- Colors ---
private val VGGreen       = Color(0xFF00E676)
private val VGGreenDark   = Color(0xFF00C853)
private val VGGreenLight  = Color(0xFF69F0AE)
private val VGCyan        = Color(0xFF18FFFF)
private val VGDarkBg      = Color(0xFF0D1117)
private val VGDarkSurface = Color(0xFF161B22)
private val VGDarkCard    = Color(0xFF1C2333)
private val VGLightBg     = Color(0xFFF6F8FA)
private val VGLightSurface= Color(0xFFFFFFFF)
private val VGLightCard   = Color(0xFFEFF1F5)
private val VGRed         = Color(0xFFFF1744)
private val VGOrange      = Color(0xFFFF6D00)
private val VGYellow      = Color(0xFFFFAB00)
private val VGGray        = Color(0xFF8B949E)

private val DarkScheme = darkColorScheme(
    primary          = VGGreen,
    onPrimary        = VGDarkBg,
    primaryContainer = VGGreenDark,
    secondary        = VGCyan,
    onSecondary      = VGDarkBg,
    background       = VGDarkBg,
    onBackground     = Color(0xFFE6EDF3),
    surface          = VGDarkSurface,
    onSurface        = Color(0xFFE6EDF3),
    surfaceVariant   = VGDarkCard,
    onSurfaceVariant = VGGray,
    error            = VGRed,
    outline          = Color(0xFF30363D)
)

private val LightScheme = lightColorScheme(
    primary          = VGGreenDark,
    onPrimary        = Color.White,
    primaryContainer = VGGreenLight,
    secondary        = Color(0xFF0097A7),
    onSecondary      = Color.White,
    background       = VGLightBg,
    onBackground     = Color(0xFF1F2328),
    surface          = VGLightSurface,
    onSurface        = Color(0xFF1F2328),
    surfaceVariant   = VGLightCard,
    onSurfaceVariant = Color(0xFF656D76),
    error            = VGRed,
    outline          = Color(0xFFD0D7DE)
)

// Monospace font for evidence console
val VGMonoFont = FontFamily.Monospace

private val VGTypography = Typography(
    headlineLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 36.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 32.sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp)
)

@Composable
fun VaultGuardTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkScheme else LightScheme

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
        typography  = VGTypography,
        content     = content
    )
}
