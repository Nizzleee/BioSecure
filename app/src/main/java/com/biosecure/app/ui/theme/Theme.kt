package com.biosecure.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val BioSecureLightColorScheme = lightColorScheme(
    primary = GreenDark,
    onPrimary = White,
    primaryContainer = GreenMedium,
    onPrimaryContainer = White,
    secondary = Teal,
    onSecondary = White,
    secondaryContainer = Color(0xFFB2EBE8),
    onSecondaryContainer = GreenDark,
    tertiary = TealLight,
    onTertiary = White,
    tertiaryContainer = Color(0xFFD0F5F3),
    onTertiaryContainer = GreenDark,
    background = Background,
    onBackground = TextPrimary,
    surface = White,
    onSurface = TextPrimary,
    surfaceVariant = White,
    onSurfaceVariant = TextSecondary,
    outline = Teal,
    outlineVariant = Color(0xFFE0E0E0),
    error = ErrorRed,
    onError = White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    inverseSurface = TextPrimary,
    inverseOnSurface = White,
    scrim = Color(0xFF000000),
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF1ED9C5),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF0D3B35),
    onPrimaryContainer = Color(0xFFB8FFF0),
    secondary = Color(0xFF1ED9C5),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF004F47),
    onSecondaryContainer = Color(0xFFB8FFF0),
    tertiary = Color(0xFF4ECDC4),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF005048),
    onTertiaryContainer = Color(0xFFB8FFF0),
    background = Color(0xFF101512),
    onBackground = Color.White,
    surface = Color(0xFF1A2420),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF2A3530),
    onSurfaceVariant = Color.White.copy(alpha = 0.75f),
    outline = Color(0xFF8A9590),
    outlineVariant = Color(0xFF3A4540),
    error = Color(0xFFCF6679),
    onError = Color(0xFF370B1E),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = Color(0xFFE3E3E1),
    inverseOnSurface = Color(0xFF101512),
    scrim = Color(0xFF000000),
)

@Composable
fun BioSecureTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else BioSecureLightColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = if (darkTheme) DarkBackground.toArgb() else GreenDark.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
