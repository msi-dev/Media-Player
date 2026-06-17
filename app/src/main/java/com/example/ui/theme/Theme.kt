package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFCCCCCC), // Light grey accent
    secondary = Color(0xFF9E9E9E), // Medium grey accent
    tertiary = Color(0xFF757575), // Darker grey accent
    background = Color(0xFF000000), // Pure Black background
    surface = Color(0xFF000000), // Pure Black surface
    onPrimary = Color(0xFF000000), // Black text on primary
    onSecondary = Color(0xFF000000),
    onTertiary = Color(0xFF000000),
    onBackground = Color(0xFFFFFFFF), // Pure White text
    onSurface = Color(0xFFFFFFFF), // Pure White text on surface
    surfaceVariant = Color(0xFF161616), // Extremely dark grey
    onSurfaceVariant = Color(0xFF9E9E9E), // Soft grey text
    outline = Color(0xFF303030) // Dark outline
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF424242), // Dark grey accent
    secondary = Color(0xFF616161), // Medium grey accent
    tertiary = Color(0xFF9E9E9E), // Light grey accent
    background = Color(0xFFFFFFFF), // Pure White background
    surface = Color(0xFFFFFFFF), // Pure White surface
    onPrimary = Color(0xFFFFFFFF), // White text on primary
    onSecondary = Color(0xFFFFFFFF),
    onTertiary = Color(0xFFFFFFFF),
    onBackground = Color(0xFF000000), // Pure Black text
    onSurface = Color(0xFF000000), // Pure Black text on surface
    surfaceVariant = Color(0xFFF5F5F5), // Light grey variant
    onSurfaceVariant = Color(0xFF424242), // Strong grey text
    outline = Color(0xFFE0E0E0) // Light outline
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Set to false to strictly enforce our custom theme colors
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
