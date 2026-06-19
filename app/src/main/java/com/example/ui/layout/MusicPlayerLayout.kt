package com.example.ui.layout

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.MediaEntity
import com.example.ui.components.AlbumArtImage
import com.example.ui.components.CdStyleAlbumArt
import com.example.ui.components.CustomAospSeekBar
import com.example.ui.viewmodel.MediaViewModel

// Theme Palette Helpers
private val DarkPrimary = Color(0xFFCCCCCC)
private val DarkTertiary = Color(0xFF757575)

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

    var showSpeedDialog by remember { mutableStateOf(false) }
    var showSleepTimerPicker by remember { mutableStateOf(false) }
    val queue by viewModel.playbackQueue.collectAsState()

    var showQueueSheet by remember { mutableStateOf(false) }
    var showEqualizerDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Blurred Abstract Dynamic cover background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        )

        val dims = com.example.ui.theme.ResponsiveDimensions.dimensions
        val isWideLandscape = windowSizeClass.widthSizeClass != WindowWidthSizeClass.Compact || windowSizeClass.heightSizeClass == WindowHeightSizeClass.Compact

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
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Collapse", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(32.dp))
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "DECODED IN STUDIO DECK",
                        color = DarkPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.3.sp
                    )
                    if (timerRemaining > 0) {
                        Text(
                            text = "Sleep Timer: ${formatDuration(timerRemaining)}",
                            color = DarkTertiary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                IconButton(onClick = { showQueueSheet = true }) {
                    Icon(
                        imageVector = Icons.Filled.PlaylistPlay,
                        contentDescription = "Queue Overlay Sheet",
                        tint = if (showQueueSheet) DarkPrimary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            if (isWideLandscape) {
                // Large screen / landscape side-by-side design!
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
                            modifier = Modifier
                                .size(200.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        AudioVisualizer(
                            viewModel = viewModel,
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
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = song.artist,
                                color = DarkPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                        }

                        // Timeline Seek Slider
                        Column(modifier = Modifier.fillMaxWidth()) {
                            var localSeekProgress by remember { mutableFloatStateOf(0f) }
                            var isSeeking by remember { mutableStateOf(false) }
                            val currentDisplayPosition = if (isSeeking) (localSeekProgress * totalTime).toLong() else progress

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(formatDuration(currentDisplayPosition), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text(formatDuration(totalTime), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                            CustomAospSeekBar(
                                progress = if (totalTime > 0) currentDisplayPosition.toFloat() / totalTime else 0f,
                                onValueChange = { percent ->
                                    isSeeking = true
                                    localSeekProgress = percent
                                },
                                onValueChangeFinished = {
                                    val target = (localSeekProgress * totalTime).toLong()
                                    viewModel.seekTo(target)
                                    isSeeking = false
                                },
                                modifier = Modifier.height(24.dp)
                            )
                        }

                        // Player Controllers Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { viewModel.toggleShuffle() }) {
                                Icon(Icons.Filled.Shuffle, null, tint = if (isShuffle) DarkPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f), modifier = Modifier.size(20.dp))
                            }
                            IconButton(onClick = { viewModel.playPrevious() }) {
                                Icon(Icons.Filled.SkipPrevious, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(32.dp))
                            }
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), shape = CircleShape)
                                    .padding(4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                IconButton(
                                    onClick = { if (isPlaying) viewModel.pause() else viewModel.play() },
                                    modifier = Modifier.size(64.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Filled.PauseCircle else Icons.Filled.PlayCircle,
                                        contentDescription = "Play/Pause",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(56.dp)
                                    )
                                }
                            }
                            IconButton(onClick = { viewModel.playNext() }) {
                                Icon(Icons.Filled.SkipNext, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(32.dp))
                            }
                            IconButton(onClick = { viewModel.toggleRepeatMode() }) {
                                Icon(if (repeatMode == 1) Icons.Filled.RepeatOne else Icons.Filled.Repeat, null, tint = if (repeatMode > 0) DarkPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f), modifier = Modifier.size(20.dp))
                            }
                        }

                        // Sound Speed/EQ/Sleep Bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), shape = RoundedCornerShape(10.dp))
                                .padding(vertical = 6.dp, horizontal = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(modifier = Modifier.clickable { showSpeedDialog = true }, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Filled.SlowMotionVideo, null, tint = DarkPrimary, modifier = Modifier.size(14.dp))
                                Text("Speed: ${playbackSpeed}x", color = MaterialTheme.colorScheme.onSurface, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                            Row(modifier = Modifier.clickable { showEqualizerDialog = true }, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Filled.Equalizer, null, tint = DarkPrimary, modifier = Modifier.size(14.dp))
                                Text("EQ", color = MaterialTheme.colorScheme.onSurface, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                            Row(modifier = Modifier.clickable { if (timerRemaining > 0) viewModel.setSleepTimer(0) else showSleepTimerPicker = true }, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Filled.Alarm, null, tint = if (timerRemaining > 0) DarkTertiary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                                Text(if (timerRemaining > 0) formatDuration(timerRemaining) else "Sleep off", color = MaterialTheme.colorScheme.onSurface, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else {
                // Classic Portrait / Phone Sized Layout
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = if (dims.albumArtSize < 150.dp) 4.dp else 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(if (dims.albumArtSize < 150.dp) 8.dp else 16.dp)
                ) {
                    CdStyleAlbumArt(
                        songPath = song.path,
                        songTitle = song.title,
                        isPlaying = isPlaying,
                        modifier = Modifier
                            .size(if (dims.albumArtSize > 180.dp) 280.dp else if (dims.albumArtSize < 150.dp) 160.dp else 210.dp)
                    )

                    // Dynamic wave spectrum visualization
                    AudioVisualizer(
                        viewModel = viewModel,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (dims.albumArtSize < 150.dp) 36.dp else 64.dp)
                            .padding(horizontal = 16.dp)
                    )
                }

                // Titles & Artist Card
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                ) {
                    Text(
                        text = song.title,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = if (dims.albumArtSize < 150.dp) 18.sp else 22.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = song.artist,
                        color = DarkPrimary,
                        fontSize = if (dims.albumArtSize < 150.dp) 13.sp else 15.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "${song.album} (Support APE/FLAC High Fidelity decode)",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = if (dims.albumArtSize < 150.dp) 9.sp else 11.sp,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(if (dims.albumArtSize < 150.dp) 6.dp else 16.dp))

                // Timeline Scrubbing Slider block
                Column(modifier = Modifier.fillMaxWidth()) {
                    var localSeekProgress by remember { mutableFloatStateOf(0f) }
                    var isSeeking by remember { mutableStateOf(false) }

                    val currentDisplayPosition = if (isSeeking) (localSeekProgress * totalTime).toLong() else progress

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(formatDuration(currentDisplayPosition), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(formatDuration(totalTime), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    CustomAospSeekBar(
                        progress = if (totalTime > 0) currentDisplayPosition.toFloat() / totalTime else 0f,
                        onValueChange = { percent ->
                            isSeeking = true
                            localSeekProgress = percent
                        },
                        onValueChangeFinished = {
                            val target = (localSeekProgress * totalTime).toLong()
                            viewModel.seekTo(target)
                            isSeeking = false
                        }
                    )
                }

                Spacer(modifier = Modifier.height(if (dims.albumArtSize < 150.dp) 4.dp else 12.dp))

                // Professional Audio Controller Deck
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Shuffle Icon
                    IconButton(onClick = { viewModel.toggleShuffle() }) {
                        Icon(
                            Icons.Filled.Shuffle,
                            contentDescription = "Shuffle",
                            tint = if (isShuffle) DarkPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Skip previous (enlarged)
                    IconButton(
                        onClick = { viewModel.playPrevious() },
                        modifier = Modifier.size(if (dims.albumArtSize < 150.dp) 48.dp else 64.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SkipPrevious,
                            contentDescription = "Previous",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(if (dims.albumArtSize < 150.dp) 32.dp else 44.dp)
                        )
                    }

                    // Core play pause trigger (enlarged with halo highlight)
                    Box(
                        modifier = Modifier
                            .size(if (dims.albumArtSize < 150.dp) 82.dp else 110.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), shape = CircleShape)
                            .padding(if (dims.albumArtSize < 150.dp) 2.dp else 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        IconButton(
                            onClick = { if (isPlaying) viewModel.pause() else viewModel.play() },
                            modifier = Modifier.size(if (dims.albumArtSize < 150.dp) 72.dp else 96.dp)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Filled.PauseCircle else Icons.Filled.PlayCircle,
                                contentDescription = "PlayPause",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(if (dims.albumArtSize < 150.dp) 64.dp else 90.dp)
                            )
                        }
                    }

                    // Skip next (enlarged)
                    IconButton(
                        onClick = { viewModel.playNext() },
                        modifier = Modifier.size(if (dims.albumArtSize < 150.dp) 48.dp else 64.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SkipNext,
                            contentDescription = "Next",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(if (dims.albumArtSize < 150.dp) 32.dp else 44.dp)
                        )
                    }

                    // Repeat Modes toggles
                    IconButton(onClick = { viewModel.toggleRepeatMode() }) {
                        Icon(
                            when (repeatMode) {
                                1 -> Icons.Filled.RepeatOne
                                2 -> Icons.Filled.Repeat
                                else -> Icons.Filled.Repeat
                            },
                            contentDescription = "Repeat",
                            tint = if (repeatMode > 0) DarkPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Timers, speed, & equalizer controllers footer
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), shape = RoundedCornerShape(12.dp))
                        .padding(vertical = 8.dp, horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.clickable {
                            showSpeedDialog = true
                        }
                    ) {
                        Icon(Icons.Filled.SlowMotionVideo, contentDescription = null, tint = DarkPrimary, modifier = Modifier.size(16.dp))
                        Text("Speed: ${playbackSpeed}x", color = MaterialTheme.colorScheme.onSurface, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.clickable {
                            showEqualizerDialog = true
                        }
                    ) {
                        Icon(Icons.Filled.Equalizer, contentDescription = null, tint = DarkPrimary, modifier = Modifier.size(16.dp))
                        Text("Equalizer", color = MaterialTheme.colorScheme.onSurface, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.clickable {
                            if (timerRemaining > 0) {
                                viewModel.setSleepTimer(0) // Cancel if already active
                            } else {
                                showSleepTimerPicker = true
                            }
                        }
                    ) {
                        Icon(Icons.Filled.Alarm, contentDescription = null, tint = if (timerRemaining > 0) DarkTertiary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                        Text(
                            text = if (timerRemaining > 0) formatDuration(timerRemaining) else "Sleep off",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    // Modern Slide Up Sheet for Speed Selection
    if (showSpeedDialog) {
        SpeedOverlayBottomSheet(
            playbackSpeed = playbackSpeed,
            viewModel = viewModel,
            onDismiss = { showSpeedDialog = false }
        )
    }

    // Modern Interactive Parametric Equalizer Bottom Sheet
    if (showEqualizerDialog) {
        EqualizerOverlayBottomSheet(
            viewModel = viewModel,
            onDismiss = { showEqualizerDialog = false }
        )
    }

    // Modern Material 3 Sleep Timer Slide-Up Bottom Sheet
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
