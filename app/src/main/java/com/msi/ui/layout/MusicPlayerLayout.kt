package com.msi.ui.layout

import android.content.res.Configuration
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.msi.data.db.MediaEntity
import com.msi.ui.components.CdStyleAlbumArt
import com.msi.ui.components.CustomAospSeekBar
import com.msi.ui.viewmodel.MediaViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullscreenPlayerSheet(
    song: MediaEntity,
    isPlaying: Boolean,
    viewModel: MediaViewModel,
    windowSizeClass: WindowSizeClass,
    onCollapse: () -> Unit
) {
    val progress by viewModel.currentPosition.collectAsState()
    val totalTime by viewModel.duration.collectAsState()
    val isShuffle by viewModel.isShuffleEnabled.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()
    val playbackSpeed by viewModel.playbackSpeed.collectAsState()
    val timerRemaining by viewModel.sleepTimerRemaining.collectAsState()
    val waveformStyle by viewModel.waveformStylePref.collectAsState()
    val waveformColorType by viewModel.waveformColorPref.collectAsState()

    var showSpeedDialog by remember { mutableStateOf(false) }
    var showSleepTimerPicker by remember { mutableStateOf(false) }
    val queue by viewModel.playbackQueue.collectAsState()

    var showQueueSheet by remember { mutableStateOf(false) }
    var showEqualizerDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .clickable(enabled = true, onClick = { /* Consume clicks to prevent background leakage */ })
    ) {
        val dims = com.msi.ui.theme.ResponsiveDimensions.dimensions
        val configuration = LocalConfiguration.current
        val isWideLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        if (isWideLandscape) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(dims.fullscreenPlayerPadding),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onCollapse) {
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowDown,
                            contentDescription = "Collapse full screen player",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "NOW PLAYING",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.3.sp
                        )
                        if (timerRemaining > 0) {
                            Text(
                                text = "Sleep Timer: ${formatDuration(timerRemaining)}",
                                color = MaterialTheme.colorScheme.secondary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    IconButton(onClick = { showQueueSheet = true }) {
                        Icon(
                            imageVector = Icons.Filled.PlaylistPlay,
                            contentDescription = "Queue Overlay Sheet",
                            tint = if (showQueueSheet) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left Side: Album Art & Visualizer
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CdStyleAlbumArt(
                            songPath = song.path,
                            songTitle = song.title,
                            isPlaying = isPlaying,
                            modifier = Modifier.size(dims.albumArtSize)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        WaveformVisualizer(
                            isPlaying = isPlaying,
                            style = waveformStyle,
                            colorType = waveformColorType,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .padding(horizontal = 8.dp)
                        )
                    }

                    // Right Side: Titles, Slider, Actions, and Dynamic Config Buttons
                    Column(
                        modifier = Modifier
                            .weight(1.2f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.SpaceAround,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = song.title,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                maxLines = 1,
                                modifier = Modifier.basicMarquee(),
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = song.artist,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                modifier = Modifier.basicMarquee(),
                                textAlign = TextAlign.Center
                            )
                        }

                        // Reusable Timeline Seek Slider
                        PlayerSeekBar(
                            progress = progress,
                            totalTime = totalTime,
                            onSeekFinished = { targetPosition ->
                                viewModel.seekTo(targetPosition)
                            }
                        )

                        // Player Controllers Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { viewModel.toggleShuffle() }) {
                                Icon(
                                    imageVector = Icons.Filled.Shuffle,
                                    contentDescription = "Toggle Shuffle",
                                    tint = if (isShuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            IconButton(onClick = { viewModel.playPrevious() }) {
                                Icon(
                                    imageVector = Icons.Filled.SkipPrevious,
                                    contentDescription = "Play Previous Song",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .background(MaterialTheme.colorScheme.primary, shape = CircleShape)
                                    .clip(CircleShape)
                                    .clickable { if (isPlaying) viewModel.pause() else viewModel.play() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = "Play or Pause",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            IconButton(onClick = { viewModel.playNext() }) {
                                Icon(
                                    imageVector = Icons.Filled.SkipNext,
                                    contentDescription = "Play Next Song",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            IconButton(onClick = { viewModel.toggleRepeatMode() }) {
                                Icon(
                                    imageVector = if (repeatMode == 1) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                                    contentDescription = "Toggle Repeat Mode",
                                    tint = if (repeatMode > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // SOUND SPEED/EQ/SLEEP REUSABLE FOOTER
                        PlayerFooter(
                            playbackSpeed = playbackSpeed,
                            timerRemaining = timerRemaining,
                            onSpeedClick = { showSpeedDialog = true },
                            onEqualizerClick = { showEqualizerDialog = true },
                            onTimerClick = {
                                if (timerRemaining > 0) viewModel.setSleepTimer(0) else showSleepTimerPicker = true
                            },
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            }
        } else {
            // Classic Portrait / Phone Sized Layout
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Upper Content (Everything except bottom bar) with horizontal padding
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = dims.fullscreenPlayerPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Top Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onCollapse) {
                            Icon(
                                imageVector = Icons.Filled.KeyboardArrowDown,
                                contentDescription = "Collapse full screen player",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "NOW PLAYING",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.3.sp
                            )
                            if (timerRemaining > 0) {
                                Text(
                                    text = "Sleep Timer: ${formatDuration(timerRemaining)}",
                                    color = MaterialTheme.colorScheme.secondary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        IconButton(onClick = { showQueueSheet = true }) {
                            Icon(
                                imageVector = Icons.Filled.PlaylistPlay,
                                contentDescription = "Queue Overlay Sheet",
                                tint = if (showQueueSheet) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }

                    // CD Art thumbnail at top
                    Box(
                        modifier = Modifier
                            .weight(1.2f)
                            .fillMaxWidth()
                            .offset(y = (-16).dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val artSize = (dims.albumArtSize * 1.35f).coerceIn(160.dp, 280.dp)
                        CdStyleAlbumArt(
                            songPath = song.path,
                            songTitle = song.title,
                            isPlaying = isPlaying,
                            modifier = Modifier.size(artSize)
                        )
                    }

                    // Waveform visualization (below CD thumbnail)
                    WaveformVisualizer(
                        isPlaying = isPlaying,
                        style = waveformStyle,
                        colorType = waveformColorType,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp)
                            .padding(vertical = 4.dp)
                    )

                    // Music Title & Subtitle with sliding and marquee single line animations
                    AnimatedContent(
                        targetState = song,
                        transitionSpec = {
                            slideInHorizontally { width -> width / 2 } + fadeIn() togetherWith
                                    slideOutHorizontally { width -> -width / 2 } + fadeOut()
                        },
                        label = "song_title_subtext_slide",
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) { currentSong ->
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = currentSong.title,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = if (dims.albumArtSize < 150.dp) 18.sp else 21.sp,
                                fontWeight = FontWeight.Black,
                                maxLines = 1,
                                modifier = Modifier.basicMarquee(),
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "${currentSong.artist} • ${currentSong.album}",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = if (dims.albumArtSize < 150.dp) 12.sp else 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                modifier = Modifier.basicMarquee(),
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Seek bar
                    PlayerSeekBar(
                        progress = progress,
                        totalTime = totalTime,
                        onSeekFinished = { targetPosition ->
                            viewModel.seekTo(targetPosition)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Controller Deck (decreased icon size, for best UI on small/low-DPI devices)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { viewModel.toggleShuffle() }) {
                            Icon(
                                imageVector = Icons.Filled.Shuffle,
                                contentDescription = "Shuffle Playlist",
                                tint = if (isShuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(
                            onClick = { viewModel.playPrevious() },
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.SkipPrevious,
                                contentDescription = "Previous Track",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .background(MaterialTheme.colorScheme.primary, shape = CircleShape)
                                .clip(CircleShape)
                                .clickable { if (isPlaying) viewModel.pause() else viewModel.play() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = "Play or Pause",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        IconButton(
                            onClick = { viewModel.playNext() },
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.SkipNext,
                                contentDescription = "Next Track",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        IconButton(onClick = { viewModel.toggleRepeatMode() }) {
                            Icon(
                                imageVector = when (repeatMode) {
                                    1 -> Icons.Filled.RepeatOne
                                    else -> Icons.Filled.Repeat
                                },
                                contentDescription = "Toggle Repeat Options",
                                tint = if (repeatMode > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // Full-width bottom docked footer bar bleeding backgrounds perfectly underneath gesture bars
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    PlayerFooter(
                        playbackSpeed = playbackSpeed,
                        timerRemaining = timerRemaining,
                        onSpeedClick = { showSpeedDialog = true },
                        onEqualizerClick = { showEqualizerDialog = true },
                        onTimerClick = {
                            if (timerRemaining > 0) viewModel.setSleepTimer(0) else showSleepTimerPicker = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(vertical = 10.dp, horizontal = 12.dp)
                    )
                }
            }
        }
    }

    // Modal dialogues
    if (showSpeedDialog) {
        SpeedOverlayBottomSheet(
            playbackSpeed = playbackSpeed,
            viewModel = viewModel,
            onDismiss = { showSpeedDialog = false }
        )
    }

    if (showEqualizerDialog) {
        EqualizerOverlayBottomSheet(
            viewModel = viewModel,
            onDismiss = { showEqualizerDialog = false }
        )
    }

    if (showSleepTimerPicker) {
        SleepTimerOverlayBottomSheet(
            viewModel = viewModel,
            onDismiss = { showSleepTimerPicker = false }
        )
    }

    if (showQueueSheet) {
        QueueOverlayBottomSheet(
            queue = queue,
            currentSong = song,
            viewModel = viewModel,
            onDismiss = { showQueueSheet = false }
        )
    }
}

/**
 * Reusable seek bar component with self-contained seeking state for performance
 * and identical behaviour across layout configurations.
 */
@Composable
fun PlayerSeekBar(
    progress: Long,
    totalTime: Long,
    onSeekFinished: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var localSeekProgress by remember { mutableFloatStateOf(0f) }
    var isSeeking by remember { mutableStateOf(false) }
    val currentDisplayPosition = if (isSeeking) (localSeekProgress * totalTime).toLong() else progress

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatDuration(currentDisplayPosition),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = formatDuration(totalTime),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        CustomAospSeekBar(
            progress = if (totalTime > 0) currentDisplayPosition.toFloat() / totalTime else 0f,
            onValueChange = { percent ->
                isSeeking = true
                localSeekProgress = percent
            },
            onValueChangeFinished = {
                onSeekFinished((localSeekProgress * totalTime).toLong())
                isSeeking = false
            },
            modifier = Modifier.height(24.dp)
        )
    }
}

/**
 * Clean reusable M3 footer with equally balanced items that scale or wrap
 * safely on ultra small layouts.
 */
@Composable
fun PlayerFooter(
    playbackSpeed: Float,
    timerRemaining: Long,
    onSpeedClick: () -> Unit,
    onEqualizerClick: () -> Unit,
    onTimerClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Speed item
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .weight(1f)
                .clickable { onSpeedClick() }
                .padding(vertical = 4.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.SlowMotionVideo,
                contentDescription = "Adjust Playback Speed",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Speed: ${playbackSpeed}x",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Equalizer item
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .weight(1f)
                .clickable { onEqualizerClick() }
                .padding(vertical = 4.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Equalizer,
                contentDescription = "Open Audio Equalizer",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Equalizer",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Sleep Timer item
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .weight(1f)
                .clickable { onTimerClick() }
                .padding(vertical = 4.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Alarm,
                contentDescription = "Configure Sleep Timer",
                tint = if (timerRemaining > 0) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = if (timerRemaining > 0) formatDuration(timerRemaining) else "Sleep off",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun formatDuration(millis: Long): String {
    val sec = (millis / 1000) % 60
    val min = (millis / 1000 / 60) % 60
    val hr = (millis / 1000 / 3600)
    return if (hr > 0) {
        String.format("%02d:%02d:%02d", hr, min, sec)
    } else {
        String.format("%02d:%02d", min, sec)
    }
}
