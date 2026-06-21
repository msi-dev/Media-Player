package com.msi.ui.layout

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.msi.ui.components.AlbumArtImage
import com.msi.ui.theme.ResponsiveDimensions
import com.msi.ui.viewmodel.MediaViewModel

@Composable
fun MiniPlayerLayout(
    viewModel: MediaViewModel,
    onClick: () -> Unit
) {
    val currentTrack by viewModel.currentTrack.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val dims = ResponsiveDimensions.dimensions

    val track = currentTrack ?: return

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(dims.scaleDp(58.dp)) // Slim, reduced height
            .clickable(
                onClick = onClick,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ),
        shape = RoundedCornerShape(dims.scaleDp(12.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = dims.scaleDp(11.dp)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Track Info Container
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dims.scaleDp(8.dp)),
                modifier = Modifier.weight(1f)
            ) {
                AlbumArtImage(
                    songTitle = track.title,
                    isAudio = track.isAudio,
                    modifier = Modifier
                        .size(dims.scaleDp(36.dp)) // Scaled down art
                        .clip(CircleShape)
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = track.title,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = dims.scaleSp(12.sp), // scaled font
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = track.artist,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        fontSize = dims.scaleSp(10.sp), // scaled down description
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Compact control layout aligned right
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dims.scaleDp(2.dp))
            ) {
                IconButton(
                    onClick = { viewModel.playPrevious() },
                    modifier = Modifier.size(dims.scaleDp(32.dp))
                ) {
                    Icon(
                        imageVector = Icons.Filled.SkipPrevious,
                        contentDescription = "Previous Song",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(dims.scaleDp(20.dp))
                    )
                }

                IconButton(
                    onClick = { if (isPlaying) viewModel.pause() else viewModel.play() },
                    modifier = Modifier
                        .size(dims.scaleDp(38.dp))
                        .testTag("mini_play_button")
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.PauseCircle else Icons.Filled.PlayCircle,
                        contentDescription = "play or pause",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(dims.scaleDp(32.dp))
                    )
                }

                IconButton(
                    onClick = { viewModel.playNext() },
                    modifier = Modifier.size(dims.scaleDp(32.dp))
                ) {
                    Icon(
                        imageVector = Icons.Filled.SkipNext,
                        contentDescription = "Skip Song",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(dims.scaleDp(20.dp))
                    )
                }
            }
        }
    }
}
