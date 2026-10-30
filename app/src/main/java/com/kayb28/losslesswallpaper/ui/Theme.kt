package com.kayb28.losslesswallpaper.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// La app es siempre oscura, sin importar el tema del sistema.
private val DarkScheme = darkColorScheme(
    primary = Color(0xFF8FB8FF),
    onPrimary = Color(0xFF0B1426),
    primaryContainer = Color(0xFF28415F),
    onPrimaryContainer = Color(0xFFD6E3FF),
    secondary = Color(0xFFA6C4A0),
    onSecondary = Color(0xFF14200F),
    background = Color(0xFF0F1216),
    onBackground = Color(0xFFE2E6EC),
    surface = Color(0xFF0F1216),
    onSurface = Color(0xFFE2E6EC),
    surfaceVariant = Color(0xFF1A1F26),
    onSurfaceVariant = Color(0xFFB9C1CC),
    outline = Color(0xFF47505C),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF37090A),
)

@Composable
fun LosslessWallpaperTheme(content: @Composable () -> Unit) {
    // Modo oscuro forzado: siempre se usa DarkScheme.
    MaterialTheme(colorScheme = DarkScheme, content = content)
}
