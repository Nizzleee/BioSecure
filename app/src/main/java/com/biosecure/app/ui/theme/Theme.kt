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
    secondary = Teal,
    tertiary = TealLight,
    background = Background,
    surface = White,
    onPrimary = White,
    onSecondary = White,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    error = ErrorRed
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF00B4A6),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF0D3B35),
    onPrimaryContainer = Color.White,
    background = Color(0xFF121212),
    onBackground = Color.White,
    surface = Color(0xFF1E1E1E),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF2C2C2C),
    onSurfaceVariant = Color(0xFFCCCCCC),
    outline = Color(0xFF00B4A6),
    outlineVariant = Color(0xFF444444),
    inverseSurface = Color.White,
    inverseOnSurface = Color(0xFF121212)
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