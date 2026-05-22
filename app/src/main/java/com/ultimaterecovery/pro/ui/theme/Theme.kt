package com.ultimaterecovery.pro.ui.theme

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

// ============================================================================
// Light Color Scheme
// ============================================================================
private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    onPrimary = LightOnBackground,
    primaryContainer = Purple80,
    onPrimaryContainer = Purple20,

    secondary = Teal40,
    onSecondary = LightOnBackground,
    secondaryContainer = Teal80,
    onSecondaryContainer = Teal20,

    tertiary = RecoveryGreen,
    onTertiary = LightOnBackground,
    tertiaryContainer = RecoveryGreenLight,
    onTertiaryContainer = RecoveryGreenDark,

    error = LightError,
    onError = LightOnError,
    errorContainer = LightErrorContainer,
    onErrorContainer = LightOnErrorContainer,

    background = LightBackground,
    onBackground = LightOnBackground,

    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,

    outline = LightOutline,
    outlineVariant = LightOutlineVariant,

    inverseSurface = DarkSurface,
    inverseOnSurface = DarkOnSurface,
    inversePrimary = Purple80,

    surfaceTint = Purple40,
)

// ============================================================================
// Dark Color Scheme
// ============================================================================
private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    onPrimary = DarkOnBackground,
    primaryContainer = Purple60,
    onPrimaryContainer = Purple80,

    secondary = Teal80,
    onSecondary = DarkOnBackground,
    secondaryContainer = Teal60,
    onSecondaryContainer = Teal80,

    tertiary = RecoveryGreenLight,
    onTertiary = DarkOnBackground,
    tertiaryContainer = RecoveryGreenDark,
    onTertiaryContainer = RecoveryGreenLight,

    error = DarkError,
    onError = DarkOnError,
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkOnErrorContainer,

    background = DarkBackground,
    onBackground = DarkOnBackground,

    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,

    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,

    inverseSurface = LightSurface,
    inverseOnSurface = LightOnSurface,
    inversePrimary = Purple40,

    surfaceTint = Purple80,
)

// ============================================================================
// Theme Composable
// ============================================================================
@Composable
fun UltimateRecoveryProTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        // Dynamic color is available on Android 12+ (API 31+)
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) {
                dynamicDarkColorScheme(context)
            } else {
                dynamicLightColorScheme(context)
            }
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    // Set status bar and navigation bar colors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.surface.toArgb()
            window.navigationBarColor = colorScheme.surface.toArgb()

            // Adjust system bars contrast
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
