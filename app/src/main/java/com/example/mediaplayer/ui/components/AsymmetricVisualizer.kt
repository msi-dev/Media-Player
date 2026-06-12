package com.example.mediaplayer.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import kotlin.random.Random

@Composable
fun AsymmetricVisualizer(
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary

    // Infinite animators to simulate real music frequency bounces
    val transition = rememberInfiniteTransition(label = "Visualizer")
    
    // Create random target heights for multiple columns
    val barCount = 18
    val scales = List(barCount) { index ->
        if (isPlaying) {
            val duration = 400 + (index * 45) % 350
            transition.animateFloat(
                initialValue = 0.15f,
                targetValue = 0.95f,
                animationSpec = infiniteRepeatable(
                    animation = tween(duration, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bar_$index"
            )
        } else {
            remember { mutableStateOf(0.08f) }
        }
    }

    Canvas(modifier = modifier.fillMaxWidth().height(120.dp)) {
        val width = size.width
        val height = size.height
        val barGap = 8.dp.toPx()
        val totalGapsWidth = barGap * (barCount - 1)
        val barWidth = (width - totalGapsWidth) / barCount

        val gradient = Brush.verticalGradient(
            colors = listOf(primaryColor, secondaryColor),
            startY = height,
            endY = 0f
        )

        for (i in 0 until barCount) {
            val scale = scales[i].value
            val barHeight = height * scale
            val left = i * (barWidth + barGap)
            val top = height - barHeight

            drawRoundRect(
                brush = gradient,
                topLeft = Offset(left, top),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2, barWidth / 2)
            )
        }
    }
}
