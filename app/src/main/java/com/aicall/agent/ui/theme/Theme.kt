package com.aicall.agent.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = BrandPurple,
    onPrimary = TextOnGradient,
    primaryContainer = SurfaceOverlay,
    onPrimaryContainer = BrandPurple,
    secondary = BrandCyan,
    onSecondary = TextOnGradient,
    secondaryContainer = SurfaceOverlay,
    onSecondaryContainer = BrandCyan,
    tertiary = BrandMid,
    onTertiary = TextOnGradient,
    background = SurfaceCanvas,
    onBackground = TextPrimary,
    surface = SurfaceCard,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceOverlay,
    onSurfaceVariant = TextSecondary,
    outline = BorderLight,
    outlineVariant = BorderMedium,
    error = ColorInactive,
    onError = TextOnGradient
)

@Composable
fun AICallTheme(
    darkTheme: Boolean = false, // Enforce light Apple HIG theme by default
    content: @Composable () -> Unit
) {
    val colorScheme = LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = SurfaceCanvas.toArgb()
                window.navigationBarColor = SurfaceCanvas.toArgb()
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = true
                insetsController.isAppearanceLightNavigationBars = true
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
