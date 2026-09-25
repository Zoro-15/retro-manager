package com.retropack.manager.ui.theme

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val RetroDarkColorScheme = darkColorScheme(
    primary = RetroPrimary,
    onPrimary = Color.White,
    primaryContainer = RetroPrimaryContainer,
    onPrimaryContainer = RetroOnPrimaryContainer,
    secondary = RetroSecondary,
    onSecondary = Color.Black,
    secondaryContainer = RetroSecondaryContainer,
    onSecondaryContainer = RetroOnSecondaryContainer,
    tertiary = RetroTertiary,
    onTertiary = Color.White,
    tertiaryContainer = RetroTertiaryContainer,
    onTertiaryContainer = Color.White,
    background = RetroDarkBackground,
    onBackground = Color.White,
    surface = RetroDarkSurface,
    onSurface = Color.White,
    surfaceVariant = RetroDarkSurfaceVariant,
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = RetroDarkOutline,
    outlineVariant = RetroDarkOutlineVariant,
    error = RetroError,
    onError = Color.White,
    errorContainer = RetroErrorContainer,
    onErrorContainer = Color(0xFFFCA5A5)
)

private val RetroLightColorScheme = lightColorScheme(
    primary = RetroPrimaryDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E7FF),
    onPrimaryContainer = Color(0xFF1E1F3B),
    secondary = Color(0xFF0891B2),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCFFAFE),
    onSecondaryContainer = Color(0xFF083344),
    surface = Color(0xFFF8FAFC),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFE2E8F0),
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFFCBD5E1),
    background = Color(0xFFF1F5F9),
    onBackground = Color(0xFF0F172A)
)

@Composable
fun RetroPackTheme(
    darkTheme: Boolean = true, // Default to rich OLED Dark theme
    dynamicColor: Boolean = false, // Keep branded retro cyber palette
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> RetroDarkColorScheme
        else -> RetroLightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !darkTheme
                controller.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}
