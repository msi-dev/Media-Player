package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.MediaEntity
import com.example.ui.theme.DarkPrimary
import com.example.ui.theme.DarkSecondary
import com.example.ui.viewmodel.MediaViewModel

@Composable
fun VideoTab(
    viewModel: MediaViewModel,
    modifier: Modifier = Modifier
) {
    val videos by viewModel.filteredVideos.collectAsState()

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Text(
            text = "Video Cinema Library",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        Text(
            text = "Supports MKV, AVI, MP4, MOV. Hardware accelerated.",
            color = Color.Gray,
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        if (videos.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.VideoFile,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(65.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("No local video clips scanned.", color = Color.Gray, fontSize = 14.sp)
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(160.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 90.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(videos) { video ->
                    VideoGridCard(
                        video = video,
                        onClick = { viewModel.setCurrentlyPlayingVideo(video) }
                    )
                }
            }
        }
    }
}

@Composable
fun VideoGridCard(
    video: MediaEntity,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column {
            // Simulated High-Fi Video Thumbnail Cover
            val thumbnailBrush = Brush.verticalGradient(
                colors = listOf(DarkSecondary.copy(alpha = 0.5f), Color.Black)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(105.dp)
                    .background(thumbnailBrush),
                contentAlignment = Alignment.Center
            ) {
                // Play Icon overlay
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = "Play",
                    tint = DarkPrimary,
                    modifier = Modifier.size(36.dp)
                )

                // Render exact physical duration badge
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .background(Color.Black.copy(alpha = 0.75f), shape = RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = formatDuration(video.duration),
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = video.title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = video.album, // Classified structural path tag
                        color = Color.LightGray,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "Local HD",
                        color = DarkPrimary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

