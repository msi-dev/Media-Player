package com.example.ui.layout

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.sin

@Composable
fun WaveformVisualizer(
    isPlaying: Boolean,
    style: String = "Bars", // "Wave", "Bars", "Spectrum", "Symmetrical"
    colorType: String = "Accent", // "Monochromatic", "Accent"
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveform_anim")
    
    // Choose base color based on user configuration
    val baseColor = if (colorType == "Monochromatic") {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.primary
    }

    val secondaryColor = if (colorType == "Monochromatic") {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    } else {
        MaterialTheme.colorScheme.secondary
    }

    // Horizontal offset animation for sine wave
    val waveOffset by if (isPlaying) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = (2 * Math.PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "wave_offset"
        )
    } else {
        remember { mutableStateOf(0f) }
    }

    // Pulse factor for scaling sizing
    val pulseFactor by if (isPlaying) {
        infiniteTransition.animateFloat(
            initialValue = 0.6f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(600, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulse_factor"
        )
    } else {
        remember { mutableStateOf(0.4f) }
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        when (style) {
            "None" -> {
                // Do not render anything for visualizer
            }
            "Wave" -> {
                // Drawing smooth sine waves
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val width = size.width
                    val height = size.height
                    val centerY = height / 2f
                    val amplitude = (height / 2.5f) * (if (isPlaying) pulseFactor else 0.2f)
                    
                    val path = Path()
                    path.moveTo(0f, centerY)
                    
                    for (x in 0..width.toInt()) {
                        val angle = (x.toFloat() / width) * 2f * Math.PI.toFloat() + waveOffset
                        val y = centerY + sin(angle) * amplitude
                        path.lineTo(x.toFloat(), y)
                    }
                    
                    drawPath(
                        path = path,
                        color = baseColor,
                        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // Secondary offset wave to give cinematic depth
                    val secPath = Path()
                    secPath.moveTo(0f, centerY)
                    for (x in 0..width.toInt()) {
                        val angle = (x.toFloat() / width) * 2f * Math.PI.toFloat() - waveOffset + 1.0f
                        val y = centerY + sin(angle) * (amplitude * 0.7f)
                        secPath.lineTo(x.toFloat(), y)
                    }
                    drawPath(
                        path = secPath,
                        color = secondaryColor.copy(alpha = 0.7f),
                        style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }
            "Bars" -> {
                // bouncing bar column animations
                val barCount = 6
                val heights = (0 until barCount).map { index ->
                    val duration = remember { (400..800).random() }
                    infiniteTransition.animateFloat(
                        initialValue = 0.15f,
                        targetValue = 1.0f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(duration, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "bars_val_$index"
                    )
                }
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    heights.forEachIndexed { idx, anim ->
                        val fraction = if (isPlaying) anim.value else 0.15f
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(fraction)
                                .background(
                                    color = if (idx % 2 == 0) baseColor else secondaryColor,
                                    shape = RoundedCornerShape(1.dp)
                                )
                        )
                    }
                }
            }
            "Spectrum" -> {
                // Expanding symmetric central rays
                val rayCount = 8
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val width = size.width
                    val height = size.height
                    val centerY = height / 2f
                    val maxRayLength = height / 2.2f
                    val raySpacing = width / (rayCount + 1)
                    
                    for (i in 0 until rayCount) {
                        val fraction = if (isPlaying) {
                            val wave = sin(waveOffset + i * 0.6f) * 0.5f + 0.5f
                            wave * pulseFactor
                        } else {
                            0.2f
                        }
                        
                        val x = raySpacing * (i + 1)
                        val rayLength = maxRayLength * fraction
                        
                        // Upper half
                        drawLine(
                            color = baseColor,
                            start = Offset(x, centerY),
                            end = Offset(x, centerY - rayLength),
                            strokeWidth = 2.5.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                        // Lower half
                        drawLine(
                            color = secondaryColor,
                            start = Offset(x, centerY),
                            end = Offset(x, centerY + rayLength),
                            strokeWidth = 2.5.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                }
            }
            "Symmetrical" -> {
                // Symmetric horizontal lines radiating outward
                val count = 5
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (i in 0 until count) {
                        val duration = remember { (500..900).random() }
                        val anim = infiniteTransition.animateFloat(
                            initialValue = 0.2f,
                            targetValue = 1.0f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(duration, easing = FastOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "sym_val_$i"
                        )
                        val fraction = if (isPlaying) anim.value else 0.2f
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            val strokeColor = if (i % 2 == 0) baseColor else secondaryColor
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 1.dp)
                                    .weight(1f)
                                    .fillMaxHeight(fraction)
                                    .background(strokeColor, RoundedCornerShape(1.dp))
                            )
                        }
                    }
                }
            }
        }
    }
}
