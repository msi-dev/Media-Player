package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.example.ui.theme.DarkPrimary

@Composable
fun AospVerticalSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedRange<Float> = -15f..15f,
    modifier: Modifier = Modifier
) {
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val rangeStart = valueRange.start
    val rangeEnd = valueRange.endInclusive
    val rangeSpan = rangeEnd - rangeStart

    BoxWithConstraints(
        modifier = modifier
            .width(52.dp)
            .height(180.dp)
            .pointerInput(Unit) {
                fun updateValue(y: Float) {
                    val heightPx = size.height.toFloat()
                    val fraction = (1f - (y / heightPx)).coerceIn(0f, 1f)
                    val newValue = rangeStart + fraction * rangeSpan
                    currentOnValueChange(newValue)
                }

                detectTapGestures(
                    onPress = { offset ->
                        updateValue(offset.y)
                    }
                )
            }
            .pointerInput(Unit) {
                fun updateValue(y: Float) {
                    val heightPx = size.height.toFloat()
                    val fraction = (1f - (y / heightPx)).coerceIn(0f, 1f)
                    val newValue = rangeStart + fraction * rangeSpan
                    currentOnValueChange(newValue)
                }

                detectDragGestures(
                    onDragStart = { offset ->
                        updateValue(offset.y)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        updateValue(change.position.y)
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val trackWidth = 10.dp.toPx()
            
            // Draw background vertical track (thick pill)
            drawRoundRect(
                color = Color.White.copy(alpha = 0.15f),
                topLeft = Offset((canvasWidth - trackWidth) / 2, 0f),
                size = Size(trackWidth, canvasHeight),
                cornerRadius = CornerRadius(trackWidth / 2, trackWidth / 2)
            )

            // Draw active vertical fill from bottom (rangeStart) up to current value
            val fraction = (value - rangeStart) / rangeSpan
            val activeHeight = canvasHeight * fraction
            val activeTop = canvasHeight - activeHeight
            if (activeHeight > 0f) {
                drawRoundRect(
                    color = DarkPrimary,
                    topLeft = Offset((canvasWidth - trackWidth) / 2, activeTop),
                    size = Size(trackWidth, activeHeight),
                    cornerRadius = CornerRadius(trackWidth / 2, trackWidth / 2)
                )
            }

            // Draw sleek horizontal capsule thumb at the value position
            val thumbHeight = 6.dp.toPx()
            val thumbWidth = 24.dp.toPx()
            val thumbY = activeTop
            
            drawRoundRect(
                color = Color.White,
                topLeft = Offset((canvasWidth - thumbWidth) / 2, thumbY - thumbHeight / 2),
                size = Size(thumbWidth, thumbHeight),
                cornerRadius = CornerRadius(thumbHeight / 2, thumbHeight / 2)
            )
        }
    }
}
