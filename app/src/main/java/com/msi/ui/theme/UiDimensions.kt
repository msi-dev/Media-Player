package com.msi.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Dimension and scaling calculations to keep UI elements looking identical on
 * high-DPI (high density, large screens) and low-DPI (smaller width, low density) phones.
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

val LocalUiDimensions = staticCompositionLocalOf { UiDimensions() }

object ResponsiveDimensions {
    val dimensions: UiDimensions
        @Composable
        get() = LocalUiDimensions.current
}

@Composable
fun ProvideResponsiveDimensions(content: @Composable () -> Unit) {
    val density = LocalDensity.current.density
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp

    // Dynamic scale factor based on screen density and width
    val computedScaleFactor = remember(screenWidthDp, density) {
        // Baseline design width is around 390dp (iPhone 13, Galaxy S21)
        val widthScale = screenWidthDp.toFloat() / 390f
        val densityAdjustment = when {
            density < 1.5f -> 0.76f  // Scale down significantly on low DPI
            density < 2.5f -> 0.88f  // Scaled moderately
            density >= 3.5f -> 1.05f  // Expand slightly on premium ultra high density screens
            else -> 0.98f
        }
        (widthScale * densityAdjustment).coerceIn(0.68f, 1.25f)
    }

    val dimensions = remember(density, computedScaleFactor) {
        val s = computedScaleFactor
        UiDimensions(
            scaleFactor = s,
            paddingSmall = (8 * s).dp,
            paddingMedium = (16 * s).dp,
            paddingLarge = (24 * s).dp,
            spacingSmall = (8 * s).dp,
            spacingMedium = (12 * s).dp,
            spacingLarge = (18 * s).dp,
            iconSmall = (18 * s).dp,
            iconMedium = (24 * s).dp,
            iconLarge = (32 * s).dp,
            iconHuge = (48 * s).dp,
            cardHeightCompact = (72 * s).dp,
            cardHeightMedium = (100 * s).dp,
            cardHeightLarge = (130 * s).dp,
            albumArtSize = (160 * s).dp,
            fullscreenPlayerPadding = (20 * s).dp,
            textTitleLarge = (18 * s).sp,
            textBodyLarge = (14 * s).sp,
            textBodyMedium = (12 * s).sp,
            textLabelLarge = (10 * s).sp,
            textHeadlineMedium = (22 * s).sp
        )
    }

    CompositionLocalProvider(LocalUiDimensions provides dimensions) {
        content()
    }
}
