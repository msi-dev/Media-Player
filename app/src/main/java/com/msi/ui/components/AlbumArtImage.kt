package com.msi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

@Composable
fun AlbumArtImage(
    songTitle: String,
    isAudio: Boolean = true,
    modifier: Modifier = Modifier
) {
    // Generate beautiful procedural background gradient derived from the media's name signature
    val (startColor, endColor) = remember(songTitle) {
        val hash = songTitle.hashCode()
        val c1 = Color(
            red = ((hash and 0xFF0000) shr 16).coerceIn(40, 220),
            green = ((hash and 0x00FF00) shr 8).coerceIn(30, 160),
            blue = (hash and 0x0000FF).coerceIn(80, 240)
        )
        val c2 = Color(
            red = ((hash shr 2) and 0xFF).coerceIn(20, 100),
            green = ((hash shr 4) and 0xFF).coerceIn(10, 80),
            blue = ((hash shr 6) and 0xFF).coerceIn(100, 200)
        )
        c1 to c2
    }

    Box(
        modifier = modifier
            .background(Brush.linearGradient(listOf(startColor, endColor))),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isAudio) Icons.Filled.Audiotrack else Icons.Filled.Videocam,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.75f),
            modifier = Modifier.fillMaxSize(0.45f)
        )
    }
}
