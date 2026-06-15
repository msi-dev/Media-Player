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
fun CustomAospSeekBar(
    progress: Float, // Needs to be 0.0f to 1.0f
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnValueChangeFinished by rememberUpdatedState(onValueChangeFinished)

    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }
    val displayProgress = if (isDragging) dragProgress else progress

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp) // Generous clickable & draggable area
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { offset ->
                        isDragging = true
                        val widthPx = size.width.toFloat()
                        val newProgress = (offset.x / widthPx).coerceIn(0f, 1f)
                        dragProgress = newProgress
                        currentOnValueChange(newProgress)
                        tryAwaitRelease()
                        isDragging = false
                        currentOnValueChangeFinished()
                    }
                )
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        val widthPx = size.width.toFloat()
                        dragProgress = (offset.x / widthPx).coerceIn(0f, 1f)
                        currentOnValueChange(dragProgress)
                    },
                    onDragEnd = {
                        isDragging = false
                        currentOnValueChangeFinished()
                    },
                    onDragCancel = {
                        isDragging = false
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val widthPx = size.width.toFloat()
                        dragProgress = (change.position.x / widthPx).coerceIn(0f, 1f)
                        currentOnValueChange(dragProgress)
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val trackHeight = 4.dp.toPx()
            val thumbRadius = if (isDragging) 8.dp.toPx() else 6.dp.toPx()
            val canvasWidth = size.width
            val canvasHeight = size.height

            // 1. Draw Background Track (sleek thin gray pill)
            drawRoundRect(
                color = Color.White.copy(alpha = 0.2f),
                topLeft = Offset(0f, (canvasHeight - trackHeight) / 2),
                size = Size(canvasWidth, trackHeight),
                cornerRadius = CornerRadius(trackHeight / 2, trackHeight / 2)
            )

            // 2. Draw Active Track (glowing primary colored pill)
            val activeWidth = canvasWidth * displayProgress
            if (activeWidth > 0) {
                drawRoundRect(
                    color = DarkPrimary,
                    topLeft = Offset(0f, (canvasHeight - trackHeight) / 2),
                    size = Size(activeWidth, trackHeight),
                    cornerRadius = CornerRadius(trackHeight / 2, trackHeight / 2)
                )
            }

            // 3. Draw AOSP Style Sleek Glow Ring around Thumb if active
            val thumbX = activeWidth
            val centerY = canvasHeight / 2

            if (isDragging) {
                drawCircle(
                    color = DarkPrimary.copy(alpha = 0.35f),
                    radius = thumbRadius + 8.dp.toPx(),
                    center = Offset(thumbX, centerY)
                )
            }

            // 4. Draw Inner White Core Thumb
            drawCircle(
                color = Color.White,
                radius = thumbRadius,
                center = Offset(thumbX, centerY)
            )
        }
    }
}
