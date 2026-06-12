package com.example.mediaplayer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Elegant Neon Dark Theme Colors
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF00F5D4),      // Neon Teal
    onPrimary = Color(0xFF021714),
    secondary = Color(0xFF00BBF9),    // Electric Cyan
    onSecondary = Color(0xFF001F2B),
    tertiary = Color(0xFFF15BB5),     // Vibrant Hot Magenta
    background = Color(0xFF080F14),   // Deep Charcoal Midnight Blue
    onBackground = Color(0xFFE2EBF5),
    surface = Color(0xFF111C24),      // Deep Glass Card Surface
    onSurface = Color(0xFFE2EBF5),
    surfaceVariant = Color(0xFF1B2B38),
    onSurfaceVariant = Color(0xFFBACACE),
    outline = Color(0xFF283E4C)
)

// Fallback Light Theme (Slightly relaxed warm styling)
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF00897B),
    onPrimary = Color.White,
    secondary = Color(0xFF0077C2),
    onSecondary = Color.White,
    tertiary = Color(0xFFC2185B),
    background = Color(0xFFF1F5F9),
    onBackground = Color(0xFF1E293B),
    surface = Color.White,
    onSurface = Color(0xFF1E293B)
)

@Composable
fun MediaPlayerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
