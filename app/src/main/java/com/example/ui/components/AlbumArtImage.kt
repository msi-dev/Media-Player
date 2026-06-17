package com.example.ui.components

import android.content.Context
import android.media.MediaMetadataRetriever
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.rotate
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

object AlbumArtCache {
    val EMPTY_ART = ByteArray(0)
    val cache = ConcurrentHashMap<String, ByteArray>()
}

fun loadAlbumArtBytes(context: Context, path: String): ByteArray? {
    val cached = AlbumArtCache.cache[path]
    if (cached != null) {
        return if (cached === AlbumArtCache.EMPTY_ART) null else cached
    }
    
    val retriever = MediaMetadataRetriever()
    return try {
        if (path.startsWith("asset:///")) {
            val assetPath = path.removePrefix("asset:///")
            val afd = context.assets.openFd(assetPath)
            retriever.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
        } else if (path.startsWith("http://") || path.startsWith("https://")) {
            return null
        } else {
            retriever.setDataSource(path)
        }
        val bytes = retriever.embeddedPicture
        if (bytes != null) {
            AlbumArtCache.cache[path] = bytes
            bytes
        } else {
            AlbumArtCache.cache[path] = AlbumArtCache.EMPTY_ART
            null
        }
    } catch (e: Exception) {
        AlbumArtCache.cache[path] = AlbumArtCache.EMPTY_ART
        null
    } finally {
        try {
            retriever.release()
        } catch (e: Exception) {}
    }
}

@Composable
fun AlbumArtImage(
    songPath: String,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(8.dp),
    fallbackIcon: ImageVector = Icons.Filled.Album,
    songTitle: String = ""
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var artBytes by remember(songPath) {
        val cached = AlbumArtCache.cache[songPath]
        mutableStateOf<ByteArray?>(if (cached != null && cached !== AlbumArtCache.EMPTY_ART) cached else null)
    }

    LaunchedEffect(songPath) {
        val cached = AlbumArtCache.cache[songPath]
        if (cached == null) {
            if (!songPath.startsWith("http://") && !songPath.startsWith("https://")) {
                withContext(Dispatchers.IO) {
                    val bytes = loadAlbumArtBytes(context, songPath)
                    withContext(Dispatchers.Main) {
                        artBytes = bytes
                    }
                }
            } else {
                AlbumArtCache.cache[songPath] = AlbumArtCache.EMPTY_ART
            }
        } else {
            artBytes = if (cached === AlbumArtCache.EMPTY_ART) null else cached
        }
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (artBytes != null) {
            AsyncImage(
                model = artBytes,
                contentDescription = "Album Art",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else if (songPath.startsWith("http://") || songPath.startsWith("https://")) {
            AsyncImage(
                model = songPath,
                contentDescription = "Web Album Art",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            val colors = listOf(
                Color(0xFFE91E63), Color(0xFF9C27B0), Color(0xFF673AB7),
                Color(0xFF3F51B5), Color(0xFF2196F3), Color(0xFF00BCD4),
                Color(0xFF009688), Color(0xFF4CAF50), Color(0xFFFF9800),
                Color(0xFFFF5722)
            )
            val identifier = if (songTitle.isNotEmpty()) songTitle else songPath
            val hash = identifier.hashCode()
            val color1 = colors[kotlin.math.abs(hash) % colors.size]
            val color2 = colors[kotlin.math.abs(hash * 31) % colors.size]
            
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(listOf(color1, color2))),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = fallbackIcon,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxSize(0.45f)
                )
            }
        }
    }
}

@Composable
fun CdStyleAlbumArt(
    songPath: String,
    songTitle: String,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    var rotationAngle by remember { mutableFloatStateOf(0f) }

    // Smoothly accumulate rotation when playing
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            var lastTime = System.currentTimeMillis()
            while (true) {
                val now = System.currentTimeMillis()
                val delta = now - lastTime
                lastTime = now
                // Smooth rotation update based on frame delta
                rotationAngle = (rotationAngle + (0.045f * delta)) % 360f
                delay(16)
            }
        }
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(CircleShape)
            .background(Color(0xFF0F1012))
            .border(4.dp, Color.Black.copy(alpha = 0.8f), CircleShape)
            .border(5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        // Rotatable Vinyl/CD Content Group
        Box(
            modifier = Modifier
                .fillMaxSize()
                .rotate(rotationAngle),
            contentAlignment = Alignment.Center
        ) {
            // Main Album Image cropped as circle
            AlbumArtImage(
                songPath = songPath,
                songTitle = songTitle,
                shape = CircleShape,
                modifier = Modifier.fillMaxSize()
            )

            // Glossy Vinyl Ring Grooves (Styling to make it look like a real Vinyl / CD!)
            Box(
                modifier = Modifier
                    .fillMaxSize(0.9f)
                    .border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape)
            )
            Box(
                modifier = Modifier
                    .fillMaxSize(0.75f)
                    .border(1.5.dp, Color.Black.copy(alpha = 0.15f), CircleShape)
                    .border(2.dp, Color.White.copy(alpha = 0.05f), CircleShape)
            )
            Box(
                modifier = Modifier
                    .fillMaxSize(0.6f)
                    .border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape)
            )
        }

        // Center Cap (Authentic physical vinyl spindle hole!)
        Box(
            modifier = Modifier
                .fillMaxSize(0.28f)
                .background(Color.Black, CircleShape)
                .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize(0.35f)
                    .background(Color(0xFF141416), CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
            )
        }
    }
}

