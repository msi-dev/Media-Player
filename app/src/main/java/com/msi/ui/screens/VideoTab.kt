package com.msi.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.msi.data.db.MediaEntity
import com.msi.ui.components.AlbumArtImage
import com.msi.ui.theme.ResponsiveDimensions
import com.msi.ui.viewmodel.MediaViewModel

@Composable
fun VideoTab(
    viewModel: MediaViewModel,
    modifier: Modifier = Modifier
) {
    val items by viewModel.filteredVideos.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val currentTrack by viewModel.currentTrack.collectAsState()
    val dims = ResponsiveDimensions.dimensions

    if (isScanning && items.isEmpty()) {
        Column(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            Text(
                text = "Scanning device video library...",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = dims.scaleSp(12.sp),
                modifier = Modifier.padding(top = 16.dp)
            )
        }
        return
    }

    if (items.isEmpty()) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(dims.scaleDp(24.dp)),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "No Videos Found",
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                fontSize = dims.scaleSp(14.sp),
                textAlign = TextAlign.Center
            )
            Text(
                text = "Add some videos in MP4 format to your device directories.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = dims.scaleSp(11.sp),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(dims.scaleDp(6.dp))
    ) {
        items(
            items = items,
            key = { it.id }
        ) { video ->
            val isCurrent = currentTrack?.id == video.id
            VideoItemRow(
                video = video,
                isCurrent = isCurrent,
                onClick = { viewModel.playMediaItem(video, items) },
                onFavoriteToggle = { viewModel.toggleFavorite(video) }
            )
        }
    }
}

@Composable
fun VideoItemRow(
    video: MediaEntity,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onFavoriteToggle: () -> Unit
) {
    val dims = ResponsiveDimensions.dimensions

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(dims.scaleDp(8.dp)),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrent) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(dims.scaleDp(6.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Video scaled down thumbnail art (16:9 ratio style slot but compact!)
            AlbumArtImage(
                songTitle = video.title,
                isAudio = false,
                modifier = Modifier
                    .size(width = dims.scaleDp(58.dp), height = dims.scaleDp(36.dp)) // Compact 16:9 aspect art
                    .clip(RoundedCornerShape(dims.scaleDp(4.dp)))
            )

            // Scaled detail fonts
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = dims.scaleDp(8.dp)),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = video.title,
                    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = dims.scaleSp(11.sp), // scaled title
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${video.mimeType.substringAfter("/")} Collection • ${formatBytesToMb(video.size)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    fontSize = dims.scaleSp(9.sp), // compact details
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Duration and Fav Control
            Text(
                text = formatVideoItemDuration(video.duration),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = dims.scaleSp(10.sp),
                modifier = Modifier.padding(horizontal = dims.scaleDp(6.dp))
            )

            IconButton(
                onClick = onFavoriteToggle,
                modifier = Modifier.size(dims.scaleDp(30.dp))
            ) {
                Icon(
                    imageVector = if (video.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = "Favorite key",
                    tint = if (video.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(dims.scaleDp(16.dp))
                )
            }
        }
    }
}

private fun formatBytesToMb(bytes: Long): String {
    if (bytes <= 0L) return "N/A"
    return String.format("%.1f MB", bytes.toFloat() / (1024f * 1024f))
}

private fun formatVideoItemDuration(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val sec = (ms / 1000) % 60
    val min = (ms / (1000 * 60)) % 60
    val hrs = (ms / (1000 * 60 * 60))
    return if (hrs > 0) {
        String.format("%d:%02d:%02d", hrs, min, sec)
    } else {
        String.format("%d:%02d", min, sec)
    }
}
