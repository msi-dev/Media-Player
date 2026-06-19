package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// ──────────────────────────────────────────────
// 1.  Custom Monochrome Color Schemes
// ──────────────────────────────────────────────

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFCCCCCC),                // Light grey accent
    onPrimary = Color(0xFF000000),              // Black text on accent
    primaryContainer = Color(0xFF2D2D2D),       // Dark container for accent
    onPrimaryContainer = Color(0xFFE0E0E0),

    secondary = Color(0xFF9E9E9E),              // Medium grey
    onSecondary = Color(0xFF000000),
    secondaryContainer = Color(0xFF1E1E1E),
    onSecondaryContainer = Color(0xFFBDBDBD),

    tertiary = Color(0xFF757575),               // Darker grey
    onTertiary = Color(0xFF000000),
    tertiaryContainer = Color(0xFF2D2D2D),
    onTertiaryContainer = Color(0xFFBDBDBD),

    background = Color(0xFF000000),             // Pure black
    onBackground = Color(0xFFFFFFFF),           // Pure white text

    surface = Color(0xFF000000),                // Pure black
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF161616),         // Extremely dark grey
    onSurfaceVariant = Color(0xFF9E9E9E),

    outline = Color(0xFF303030),                // Dark outline
    outlineVariant = Color(0xFF202020),

    inverseSurface = Color(0xFFE0E0E0),
    inverseOnSurface = Color(0xFF000000),
    inversePrimary = Color(0xFF424242),

    surfaceTint = Color(0xFFFFFFFF),            // Tint overlay (used by some components)
    scrim = Color(0x99000000),                  // Semi‑transparent black for dialogs

    error = Color(0xFFFF6B6B),                  // Soft red that stands out against black
    onError = Color(0xFF000000),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF424242),                // Dark grey accent
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE0E0E0),
    onPrimaryContainer = Color(0xFF1A1A1A),

    secondary = Color(0xFF616161),              // Medium grey
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFBDBDBD),
    onSecondaryContainer = Color(0xFF1A1A1A),

    tertiary = Color(0xFF9E9E9E),               // Light grey
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF5F5F5),
    onTertiaryContainer = Color(0xFF1A1A1A),

    background = Color(0xFFFFFFFF),             // Pure white
    onBackground = Color(0xFF000000),           // Pure black text

    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF000000),
    surfaceVariant = Color(0xFFF5F5F5),         // Very light grey
    onSurfaceVariant = Color(0xFF424242),

    outline = Color(0xFFE0E0E0),                // Light grey border
    outlineVariant = Color(0xFFBDBDBD),

    inverseSurface = Color(0xFF303030),
    inverseOnSurface = Color(0xFFFFFFFF),
    inversePrimary = Color(0xFFCCCCCC),

    surfaceTint = Color(0xFF000000),
    scrim = Color(0x99000000),

    error = Color(0xFFBA1A1A),                  // Strong red
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

// List of player custom/ambient colors for dynamic elements (waveform, progress tracker, equalizers)
object PlayerColors {
    val seekBarTrack = Color(0xFF444444)
    val seekBarProgress = Color(0xFFFFFFFF)
    val waveForm = Color(0xFF888888)
    val buttonDisabled = Color(0xFF666666)
}

// ──────────────────────────────────────────────
// 2.  Theme Composable
// ──────────────────────────────────────────────

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,      // keep false for strict monochrome
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,        // custom typography from Type.kt
        content = content
    )
}
