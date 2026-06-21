package com.msi.ui.layout

import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import com.msi.ui.components.AlbumArtImage
import com.msi.ui.theme.ResponsiveDimensions
import com.msi.ui.viewmodel.MediaViewModel
import java.io.File

@Composable
fun MusicPlayerLayout(
    viewModel: MediaViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentTrack by viewModel.currentTrack.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val position by viewModel.playbackPosition.collectAsState()
    val duration by viewModel.trackDuration.collectAsState()
    val dims = ResponsiveDimensions.dimensions

    val track = currentTrack ?: return

    // Safe local seek tracking
    var isSeeking by remember { mutableStateOf(false) }
    var seekProgressValue by remember { mutableFloatStateOf(0f) }

    val activeDisplayPosition = if (isSeeking) {
        (seekProgressValue * duration).toLong()
    } else {
        position
    }

    // Surface volume handling
    var audioVolume by remember { mutableFloatStateOf(1.0f) }

    // Equalizer floating particle simulation
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val waveScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    // BLOCK CLICKS LEAKAGE: Consume click and intercept taps so they do not click lists underneath!
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.colorScheme.background
                    )
                )
            )
            .clickable(
                enabled = true,
                onClick = {},
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(dims.scaleDp(18.dp)),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = { viewModel.setPlayerExpanded(false) }) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = "Collapse Player",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(dims.scaleDp(28.dp))
                    )
                }

                Text(
                    text = "NOW PLAYING",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = dims.scaleSp(12.sp),
                    letterSpacing = 1.5.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )

                IconButton(onClick = { viewModel.toggleFavorite(track) }) {
                    Icon(
                        imageVector = if (track.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = "Favorite track",
                        tint = if (track.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(dims.scaleDp(22.dp))
                    )
                }
            }

            // Visualizer & Media area (Custom Video Overlay / Album Art)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = dims.scaleDp(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (!track.isAudio) {
                    // Video Content Area - Bound ExoPlayer directly to PlayerView!
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = viewModel.exoPlayer
                                useController = true
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16 / 9f)
                            .clip(RoundedCornerShape(dims.scaleDp(16.dp)))
                            .background(Color.Black)
                    )
                } else {
                    // Album Art container
                    Box(
                        modifier = Modifier
                            .size(dims.scaleDp(dims.albumArtSize))
                            .clip(RoundedCornerShape(dims.scaleDp(24.dp))),
                        contentAlignment = Alignment.Center
                    ) {
                        AlbumArtImage(
                            songTitle = track.title,
                            isAudio = track.isAudio,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            // Album details and titles (Centered)
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = track.title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = dims.scaleSp(18.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = dims.scaleDp(16.dp))
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = track.artist + " • " + track.album,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    fontSize = dims.scaleSp(12.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(dims.scaleDp(12.dp)))

            // Progress Slider seeking section
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val currentProgressFraction = if (duration > 0) activeDisplayPosition.toFloat() / duration else 0f

                Slider(
                    value = if (isSeeking) seekProgressValue else currentProgressFraction,
                    onValueChange = {
                        isSeeking = true
                        seekProgressValue = it
                    },
                    onValueChangeFinished = {
                        val pos = (seekProgressValue * duration).toLong()
                        viewModel.seekTo(pos)
                        isSeeking = false
                    },
                    colors = SliderDefaults.colors(
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                        thumbColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = dims.scaleDp(6.dp))
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = dims.scaleDp(8.dp)),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatDuration(activeDisplayPosition),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = dims.scaleSp(10.sp)
                    )
                    Text(
                        text = formatDuration(duration),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = dims.scaleSp(10.sp)
                    )
                }
            }

            // Central control transport controllers
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = dims.scaleDp(12.dp)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                IconButton(
                    onClick = { viewModel.playPrevious() },
                    modifier = Modifier.size(dims.scaleDp(44.dp))
                ) {
                    Icon(
                        imageVector = Icons.Filled.SkipPrevious,
                        contentDescription = "Previous Song",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(dims.scaleDp(36.dp))
                    )
                }

                IconButton(
                    onClick = { if (isPlaying) viewModel.pause() else viewModel.play() },
                    modifier = Modifier.size(dims.scaleDp(66.dp))
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.PauseCircle else Icons.Filled.PlayCircle,
                        contentDescription = "Play/Pause",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(dims.scaleDp(58.dp))
                    )
                }

                IconButton(
                    onClick = { viewModel.playNext() },
                    modifier = Modifier.size(dims.scaleDp(44.dp))
                ) {
                    Icon(
                        imageVector = Icons.Filled.SkipNext,
                        contentDescription = "Next Song",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(dims.scaleDp(36.dp))
                    )
                }
            }

            // Volume Controller Slider
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dims.scaleDp(12.dp), vertical = dims.scaleDp(6.dp)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.VolumeDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(dims.scaleDp(18.dp))
                )

                Slider(
                    value = audioVolume,
                    onValueChange = {
                        audioVolume = it
                        viewModel.exoPlayer.volume = it
                    },
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = dims.scaleDp(8.dp)),
                    colors = SliderDefaults.colors(
                        activeTrackColor = MaterialTheme.colorScheme.onSurface,
                        inactiveTrackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                        thumbColor = MaterialTheme.colorScheme.onSurface
                    )
                )

                Icon(
                    imageVector = Icons.Filled.VolumeUp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(dims.scaleDp(18.dp))
                )
            }
        }
    }
}

// Convert track positions helper
private fun formatDuration(ms: Long): String {
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
