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
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.example.ui.theme.DarkPrimary

@Composable
fun CustomAospSeekBar(
    progress: Float, // Needs to be 0.0f to 1.0f
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnValueChangeFinished by rememberUpdatedState(onValueChangeFinished)

    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }
    val displayProgress = if (isDragging) dragProgress else progress
    val primaryColor = MaterialTheme.colorScheme.primary
    val primaryCopy25 = primaryColor.copy(alpha = 0.25f)
    val primaryCopy35 = primaryColor.copy(alpha = 0.35f)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp) // Generous clickable & draggable area
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { offset ->
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        isDragging = true
                        val widthPx = size.width.toFloat()
                        val newProgress = (offset.x / widthPx).coerceIn(0f, 1f)
                        dragProgress = newProgress
                        currentOnValueChange(newProgress)
                        tryAwaitRelease()
                        isDragging = false
                        currentOnValueChangeFinished()
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                )
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        isDragging = true
                        val widthPx = size.width.toFloat()
                        dragProgress = (offset.x / widthPx).coerceIn(0f, 1f)
                        currentOnValueChange(dragProgress)
                    },
                    onDragEnd = {
                        isDragging = false
                        currentOnValueChangeFinished()
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onDragCancel = {
                        isDragging = false
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val widthPx = size.width.toFloat()
                        val nextProgress = (change.position.x / widthPx).coerceIn(0f, 1f)
                        
                        // Subtle tick feedback every ~2% change
                        val oldStep = (dragProgress * 50).toInt()
                        val newStep = (nextProgress * 50).toInt()
                        if (oldStep != newStep) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                        
                        dragProgress = nextProgress
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

            // 1. Draw Background Track (sleek thin gray pill highlighted with primary tint)
            drawRoundRect(
                color = primaryCopy25,
                topLeft = Offset(0f, (canvasHeight - trackHeight) / 2),
                size = Size(canvasWidth, trackHeight),
                cornerRadius = CornerRadius(trackHeight / 2, trackHeight / 2)
            )

            // 2. Draw Active Track (glowing primary colored pill)
            val activeWidth = canvasWidth * displayProgress
            if (activeWidth > 0) {
                drawRoundRect(
                    color = primaryColor,
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
                    color = primaryCopy35,
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
