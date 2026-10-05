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
    onPrimary = Color.Black,
    primaryContainer = RetroPrimaryContainer,
    onPrimaryContainer = Color.White,
    secondary = RetroSecondary,
    onSecondary = Color.Black,
    secondaryContainer = RetroSecondaryContainer,
    onSecondaryContainer = Color.White,
    tertiary = RetroTertiary,
    onTertiary = Color.Black,
    tertiaryContainer = RetroTertiaryContainer,
    onTertiaryContainer = Color.White,
    background = RetroDarkBackground,
    onBackground = Color.White,
    surface = RetroDarkSurface,
    onSurface = Color.White,
    surfaceVariant = RetroDarkSurfaceVariant,
    onSurfaceVariant = Color(0xFFA1A1AA),
    outline = RetroDarkOutline,
    outlineVariant = RetroDarkOutlineVariant,
    error = RetroError,
    onError = Color.Black,
    errorContainer = RetroErrorContainer,
    onErrorContainer = Color.White
)

private val RetroLightColorScheme = lightColorScheme(
    primary = Color.Black,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE4E4E7),
    onPrimaryContainer = Color.Black,
    secondary = Color(0xFF27272A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF4F4F5),
    onSecondaryContainer = Color.Black,
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF18181B),
    surfaceVariant = Color(0xFFF4F4F5),
    onSurfaceVariant = Color(0xFF71717A),
    outline = Color(0xFFE4E4E7),
    background = Color(0xFFFAFAFA),
    onBackground = Color(0xFF18181B)
)

@Composable
fun RetroPackTheme(
    darkTheme: Boolean = true, // Default to pure OLED Deep Black theme
    dynamicColor: Boolean = false, // Keep high-contrast monochromatic palette
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
