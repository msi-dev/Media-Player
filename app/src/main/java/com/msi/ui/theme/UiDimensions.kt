package com.msi.ui.theme

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Data class for full app spacing, icon, card, and text sizes.
 * Each property's default value is optimized for a standard phone.
 */
data class UiDimensions(
    val scaleFactor: Float = 1.0f,
    val paddingSmall: Dp = 8.dp,
    val paddingMedium: Dp = 16.dp,
    val paddingLarge: Dp = 24.dp,

    val spacingSmall: Dp = 8.dp,
    val spacingMedium: Dp = 12.dp,
    val spacingLarge: Dp = 18.dp,

    val iconSmall: Dp = 18.dp,
    val iconMedium: Dp = 24.dp,
    val iconLarge: Dp = 32.dp,
    val iconHuge: Dp = 48.dp,

    val cardHeightCompact: Dp = 80.dp,
    val cardHeightMedium: Dp = 110.dp,
    val cardHeightLarge: Dp = 140.dp,

    val albumArtSize: Dp = 180.dp,
    val fullscreenPlayerPadding: Dp = 24.dp,

    val textTitleLarge: TextUnit = 20.sp,
    val textBodyLarge: TextUnit = 16.sp,
    val textBodyMedium: TextUnit = 14.sp,
    val textLabelLarge: TextUnit = 12.sp,
    val textHeadlineMedium: TextUnit = 24.sp
) {
    fun scaleDp(value: Dp): Dp = (value.value * scaleFactor).dp
    fun scaleSp(value: Int): TextUnit = (value * scaleFactor).sp
    fun scaleSp(value: Float): TextUnit = (value * scaleFactor).sp
    fun scaleSp(value: TextUnit): TextUnit = (value.value * scaleFactor).sp
}

/**
 * CompositionLocal so any child can easily access these dimensions.
 */
val LocalUiDimensions = staticCompositionLocalOf { UiDimensions() }

/**
 * Helper object for easy access to UiDimensions.
 */
object ResponsiveDimensions {
    val dimensions: UiDimensions
        @Composable
        @ReadOnlyComposable
        get() = LocalUiDimensions.current
}

/**
 * Call this at the app root level. It automatically
 * determines and provides the appropriate UiDimensions based on
 * WindowSizeClass, density, and screen measurement.
 */
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun ProvideResponsiveDimensions(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp

    val activity = remember(context) {
        var ctx = context
        while (ctx is android.content.ContextWrapper) {
            if (ctx is Activity) break
            ctx = ctx.baseContext
        }
        ctx as? Activity
    }

    val windowSizeClass = if (activity != null) {
        calculateWindowSizeClass(activity)
    } else {
        null
    }

    // Dynamic scale factor based on screen density and width
    val computedScaleFactor = remember(screenWidthDp, density) {
        // Design baseline is 392dp
        val widthScale = screenWidthDp.toFloat() / 392f
        val densityAdjustment = when {
            density < 1.5f -> 0.82f
            density < 2.5f -> 0.90f
            density > 3.5f -> 1.05f
            else -> 1.00f
        }
        (widthScale * densityAdjustment).coerceIn(0.70f, 1.25f)
    }

    val dimensions = remember(windowSizeClass, density, computedScaleFactor) {
        if (windowSizeClass != null) {
            adaptiveDimensions(windowSizeClass, density, computedScaleFactor)
        } else {
            val textScale = computedScaleFactor
            UiDimensions(
                scaleFactor = computedScaleFactor,
                paddingSmall = (8 * computedScaleFactor).dp,
                paddingMedium = (16 * computedScaleFactor).dp,
                paddingLarge = (24 * computedScaleFactor).dp,
                spacingSmall = (8 * computedScaleFactor).dp,
                spacingMedium = (12 * computedScaleFactor).dp,
                spacingLarge = (18 * computedScaleFactor).dp,
                iconSmall = (18 * computedScaleFactor).dp,
                iconMedium = (24 * computedScaleFactor).dp,
                iconLarge = (32 * computedScaleFactor).dp,
                iconHuge = (48 * computedScaleFactor).dp,
                cardHeightCompact = (80 * computedScaleFactor).dp,
                cardHeightMedium = (110 * computedScaleFactor).dp,
                cardHeightLarge = (140 * computedScaleFactor).dp,
                albumArtSize = (180 * computedScaleFactor).dp,
                fullscreenPlayerPadding = (24 * computedScaleFactor).dp,
                textTitleLarge = (20 * textScale).sp,
                textBodyLarge = (16 * textScale).sp,
                textBodyMedium = (14 * textScale).sp,
                textLabelLarge = (12 * textScale).sp,
                textHeadlineMedium = (24 * textScale).sp
            )
        }
    }

    CompositionLocalProvider(LocalUiDimensions provides dimensions) {
        content()
    }
}

/**
 * Returns optimal dimensions based on WindowSizeClass and density.
 * A modest density factor (0.9 - 1.1) is applied to text size for readability.
 */
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
private fun adaptiveDimensions(
    windowSizeClass: WindowSizeClass,
    density: Float,
    scaleFactor: Float
): UiDimensions {
    val textScale = scaleFactor

    return when (windowSizeClass.widthSizeClass) {
        WindowWidthSizeClass.Compact -> {
            // === Phone (Compact width) ===
            // Height adjusted spacing
            if (windowSizeClass.heightSizeClass == WindowHeightSizeClass.Compact) {
                // Small phone (landscape or very small screen)
                UiDimensions(
                    scaleFactor = scaleFactor,
                    paddingSmall = (6 * scaleFactor).dp,
                    paddingMedium = (12 * scaleFactor).dp,
                    paddingLarge = (18 * scaleFactor).dp,
                    spacingSmall = (4 * scaleFactor).dp,
                    spacingMedium = (8 * scaleFactor).dp,
                    spacingLarge = (12 * scaleFactor).dp,
                    iconSmall = (16 * scaleFactor).dp,
                    iconMedium = (22 * scaleFactor).dp,
                    iconLarge = (28 * scaleFactor).dp,
                    iconHuge = (40 * scaleFactor).dp,
                    cardHeightCompact = (64 * scaleFactor).dp,
                    cardHeightMedium = (90 * scaleFactor).dp,
                    cardHeightLarge = (120 * scaleFactor).dp,
                    albumArtSize = (140 * scaleFactor).dp,
                    fullscreenPlayerPadding = (16 * scaleFactor).dp,
                    textTitleLarge = (20 * textScale).sp,
                    textBodyLarge = (16 * textScale).sp,
                    textBodyMedium = (14 * textScale).sp,
                    textLabelLarge = (12 * textScale).sp,
                    textHeadlineMedium = (24 * textScale).sp
                )
            } else {
                // Standard tall phone
                UiDimensions(
                    scaleFactor = scaleFactor,
                    paddingSmall = (8 * scaleFactor).dp,
                    paddingMedium = (16 * scaleFactor).dp,
                    paddingLarge = (24 * scaleFactor).dp,
                    spacingSmall = (8 * scaleFactor).dp,
                    spacingMedium = (12 * scaleFactor).dp,
                    spacingLarge = (18 * scaleFactor).dp,
                    iconSmall = (18 * scaleFactor).dp,
                    iconMedium = (24 * scaleFactor).dp,
                    iconLarge = (32 * scaleFactor).dp,
                    iconHuge = (48 * scaleFactor).dp,
                    cardHeightCompact = (80 * scaleFactor).dp,
                    cardHeightMedium = (110 * scaleFactor).dp,
                    cardHeightLarge = (140 * scaleFactor).dp,
                    albumArtSize = (180 * scaleFactor).dp,
                    fullscreenPlayerPadding = (24 * scaleFactor).dp,
                    textTitleLarge = (20 * textScale).sp,
                    textBodyLarge = (16 * textScale).sp,
                    textBodyMedium = (14 * textScale).sp,
                    textLabelLarge = (12 * textScale).sp,
                    textHeadlineMedium = (24 * textScale).sp
                )
            }
        }

        WindowWidthSizeClass.Medium -> {
            // === Foldable / Large Phone / Small Tablet ===
            UiDimensions(
                scaleFactor = scaleFactor,
                paddingSmall = 10.dp,
                paddingMedium = 16.dp,
                paddingLarge = 24.dp,
                spacingSmall = 8.dp,
                spacingMedium = 12.dp,
                spacingLarge = 18.dp,
                iconSmall = 20.dp,
                iconMedium = 26.dp,
                iconLarge = 36.dp,
                iconHuge = 52.dp,
                cardHeightCompact = 88.dp,
                cardHeightMedium = 120.dp,
                cardHeightLarge = 160.dp,
                albumArtSize = 200.dp,
                fullscreenPlayerPadding = 28.dp,
                textTitleLarge = (22 * textScale).sp,
                textBodyLarge = (16 * textScale).sp,
                textBodyMedium = (14 * textScale).sp,
                textLabelLarge = (12 * textScale).sp,
                textHeadlineMedium = (26 * textScale).sp
            )
        }

        WindowWidthSizeClass.Expanded -> {
            // === Tablet / Desktop / Landscape Large Screen ===
            UiDimensions(
                scaleFactor = scaleFactor,
                paddingSmall = 12.dp,
                paddingMedium = 20.dp,
                paddingLarge = 32.dp,
                spacingSmall = 10.dp,
                spacingMedium = 16.dp,
                spacingLarge = 24.dp,
                iconSmall = 22.dp,
                iconMedium = 28.dp,
                iconLarge = 38.dp,
                iconHuge = 56.dp,
                cardHeightCompact = 100.dp,
                cardHeightMedium = 140.dp,
                cardHeightLarge = 190.dp,
                albumArtSize = 260.dp,
                fullscreenPlayerPadding = 40.dp,
                textTitleLarge = (26 * textScale).sp,
                textBodyLarge = (18 * textScale).sp,
                textBodyMedium = (15 * textScale).sp,
                textLabelLarge = (13 * textScale).sp,
                textHeadlineMedium = (30 * textScale).sp
            )
        }
        else -> {
            // Fallback for any other custom width sizes
            UiDimensions(
                scaleFactor = scaleFactor,
                paddingSmall = (8 * scaleFactor).dp,
                paddingMedium = (16 * scaleFactor).dp,
                paddingLarge = (24 * scaleFactor).dp,
                spacingSmall = (8 * scaleFactor).dp,
                spacingMedium = (12 * scaleFactor).dp,
                spacingLarge = (18 * scaleFactor).dp,
                iconSmall = (18 * scaleFactor).dp,
                iconMedium = (24 * scaleFactor).dp,
                iconLarge = (32 * scaleFactor).dp,
                iconHuge = (48 * scaleFactor).dp,
                cardHeightCompact = (80 * scaleFactor).dp,
                cardHeightMedium = (110 * scaleFactor).dp,
                cardHeightLarge = (140 * scaleFactor).dp,
                albumArtSize = (180 * scaleFactor).dp,
                fullscreenPlayerPadding = (24 * scaleFactor).dp,
                textTitleLarge = (20 * textScale).sp,
                textBodyLarge = (16 * textScale).sp,
                textBodyMedium = (14 * textScale).sp,
                textLabelLarge = (12 * textScale).sp,
                textHeadlineMedium = (24 * textScale).sp
            )
        }
    }
}
