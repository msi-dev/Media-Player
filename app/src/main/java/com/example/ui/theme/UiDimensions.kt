package com.example.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class UiDimensions(
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
    
    val textTitleLarge: TextUnit = 20.sp,
    val textBodyLarge: TextUnit = 16.sp,
    val textBodyMedium: TextUnit = 14.sp,
    val textLabelLarge: TextUnit = 12.sp,
    val textHeadlineMedium: TextUnit = 24.sp
)

val LocalUiDimensions = staticCompositionLocalOf { UiDimensions() }

object ResponsiveDimensions {
    val dimensions: UiDimensions
        @Composable
        @ReadOnlyComposable
        get() = LocalUiDimensions.current
}

@Composable
fun ProvideResponsiveDimensions(content: @Composable () -> Unit) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current.density
    
    val screenWidth = configuration.screenWidthDp
    
    // Core Adaptive UI System:
    // Determine screen sizing category (Compact, Medium, Large, Foldable, Tablet)
    val dimensions = when {
        screenWidth >= 900 -> {
            // Tablets / 4K / Landscape DeX (Expanded/Large devices)
            UiDimensions(
                paddingSmall = 10.dp,
                paddingMedium = 16.dp,
                paddingLarge = 24.dp,
                spacingSmall = 8.dp,
                spacingMedium = 12.dp,
                spacingLarge = 18.dp,
                iconSmall = 16.dp,
                iconMedium = 22.dp,
                iconLarge = 30.dp,
                iconHuge = 42.dp,
                cardHeightCompact = 80.dp,
                cardHeightMedium = 120.dp,
                cardHeightLarge = 160.dp,
                textTitleLarge = 22.sp,
                textBodyLarge = 16.sp,
                textBodyMedium = 14.sp,
                textLabelLarge = 12.sp,
                textHeadlineMedium = 26.sp
            )
        }
        screenWidth >= 600 -> {
            // Foldables / Large Phones / Medium Device class
            UiDimensions(
                paddingSmall = 8.dp,
                paddingMedium = 14.dp,
                paddingLarge = 22.dp,
                spacingSmall = 6.dp,
                spacingMedium = 10.dp,
                spacingLarge = 16.dp,
                iconSmall = 16.dp,
                iconMedium = 20.dp,
                iconLarge = 28.dp,
                iconHuge = 38.dp,
                cardHeightCompact = 75.dp,
                cardHeightMedium = 110.dp,
                cardHeightLarge = 140.dp,
                textTitleLarge = 18.sp,
                textBodyLarge = 15.sp,
                textBodyMedium = 13.sp,
                textLabelLarge = 11.sp,
                textHeadlineMedium = 22.sp
            )
        }
        else -> {
            // Compact / Modern Phones (FHD, QHD, etc.): keep standard precise Material 3 scale to prevent double-scaling and text crop
            val scaleFactor = 1.0f
            UiDimensions(
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
                cardHeightLarge = (145 * scaleFactor).dp,
                textTitleLarge = (20 * scaleFactor).sp,
                textBodyLarge = (16 * scaleFactor).sp,
                textBodyMedium = (14 * scaleFactor).sp,
                textLabelLarge = (12 * scaleFactor).sp,
                textHeadlineMedium = (24 * scaleFactor).sp
            )
        }
    }
    
    CompositionLocalProvider(LocalUiDimensions provides dimensions) {
        content()
    }
}
