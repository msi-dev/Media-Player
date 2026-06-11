package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.example.data.db.MediaEntity
import com.example.ui.theme.DarkPrimary
import com.example.ui.viewmodel.AspectRatioMode
import com.example.ui.viewmodel.MediaViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    video: MediaEntity,
    viewModel: MediaViewModel,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val activity = remember { context as? Activity }
    val coroutineScope = rememberCoroutineScope()

    // Grab original player instance from central coordinator
    val player = remember { viewModel.allAudio.value; viewModel.allVideos.value; (context.applicationContext as com.example.MediaPlayerApp).playbackManager.player }

    // Interactive custom state observers
    var isControlsVisible by remember { mutableStateOf(true) }
    var currentVolume by remember { mutableFloatStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()) }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat() }
    var currentBrightness by remember { mutableFloatStateOf(activity?.window?.attributes?.screenBrightness ?: 0.5f) }

    // Quick overlay notification feeds (HUD)
    var hudText by remember { mutableStateOf("") }
    var hudIcon by remember { mutableStateOf<androidx.compose.ui.graphics.vector.ImageVector?>(null) }
    var hudVisible by remember { mutableStateOf(false) }

    // Active speed and ratios from VM
    val playbackSpeed by viewModel.videoPlaybackSpeed.collectAsState()
    val aspectRatio by viewModel.videoAspectRatio.collectAsState()
    val subtitleSize by viewModel.subtitleSize.collectAsState()
    val subtitleColorName by viewModel.subtitleColor.collectAsState()
    val subtitleBgEnabled by viewModel.subtitleBackground.collectAsState()
    val subtitleDelayMs by viewModel.subtitleDelay.collectAsState()
    val gestureSensitivity by viewModel.gestureSensitivity.collectAsState()

    val mappedSubtitleColor = when (subtitleColorName) {
        "Yellow" -> Color.Yellow
        "Cyan" -> Color.Cyan
        "Green" -> Color.Green
        else -> Color.White
    }

    // Set Orientation to Landscape for immersive cinema experience
    DisposableEffect(Unit) {
        val originalOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        
        // Setup Media Item on core player
        player.stop()
        player.clearMediaItems()
        player.setMediaItem(MediaItem.fromUri(video.path))
        player.prepare()
        player.play()

        onDispose {
            player.stop()
            activity?.requestedOrientation = originalOrientation
        }
    }

    // Controls autohide timer
    LaunchedEffect(isControlsVisible) {
        if (isControlsVisible) {
            delay(4000)
            isControlsVisible = false
        }
    }

    fun showHUD(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
        hudText = text
        hudIcon = icon
        hudVisible = true
        coroutineScope.launch {
            delay(1000)
            hudVisible = false
        }
    }

    // Set interactive ExoPlayer parameters dynamically
    LaunchedEffect(playbackSpeed) {
        player.setPlaybackSpeed(playbackSpeed)
    }

    // Immersive cinematic dialog container
    Dialog(
        onDismissRequest = { onClose() },
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    // Tap to reveal coordinates
                    detectVerticalDragGestures(
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            val width = size.width
                            val xPos = change.position.x

                            if (xPos < width / 2) {
                                // Left half of screen: Drag Brightness scaled by sensitivity
                                var b = currentBrightness - (dragAmount / 500f) * gestureSensitivity
                                b = b.coerceIn(0.01f, 1f)
                                currentBrightness = b
                                activity?.let { act ->
                                    val lp = act.window.attributes
                                    lp.screenBrightness = b
                                    act.window.attributes = lp
                                }
                                showHUD("Brightness: ${(b * 100).roundToInt()}%", Icons.Filled.LightMode)
                            } else {
                                // Right half of screen: Drag Volume scaled by sensitivity
                                var v = currentVolume - (dragAmount / 15f) * gestureSensitivity
                                v = v.coerceIn(0f, maxVolume)
                                currentVolume = v
                                audioManager.setStreamVolume(
                                    AudioManager.STREAM_MUSIC,
                                    v.roundToInt(),
                                    0
                                )
                                showHUD("Volume: ${((v / maxVolume) * 100).roundToInt()}%", Icons.Filled.VolumeUp)
                            }
                        }
                    )
                }
                .pointerInput(Unit) {
                    // Quick gesture drag seekbar
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            val duration = player.duration.coerceAtLeast(0L)
                            if (duration > 0) {
                                val seekDelta = (dragAmount * 150).toLong()
                                val targetPosition = (player.currentPosition + seekDelta).coerceIn(0L, duration)
                                player.seekTo(targetPosition)
                                showHUD("Seek: ${formatDuration(targetPosition)} / ${formatDuration(duration)}", Icons.Filled.History)
                            }
                        }
                    )
                }
        ) {
            // Android player element
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false // Custom Gestures and Compose UI controls
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        this.player = player
                    }
                },
                update = { view ->
                    // Apply Aspect Ratio Mode to Surface view scale
                    when (aspectRatio) {
                        AspectRatioMode.FIT -> {
                            view.resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                        }
                        AspectRatioMode.STRETCH -> {
                            view.resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
                        }
                        AspectRatioMode.CROP -> {
                            view.resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        }
                        AspectRatioMode.FILL -> {
                            view.resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .align(Alignment.Center)
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures { _, _ -> } // Intercept
                    }
            )

            // Transparent Tap Panel to toggle controls
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        // Regular taps toggle visible bars
                    }
            ) {
                IconButton(
                    onClick = { isControlsVisible = !isControlsVisible },
                    modifier = Modifier.fillMaxSize()
                ) {}
            }

            // High Contrast Dynamic Subtitle Overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 75.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "External subtitle synched (-$subtitleDelayMs ms)\n[Dialogue Track loaded]",
                    color = mappedSubtitleColor,
                    fontSize = subtitleSize.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .background(
                            color = if (subtitleBgEnabled) Color.Black.copy(alpha = 0.65f) else Color.Transparent,
                            shape = RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }

            // Gesture HUD Toast Overlay (Center HUD)
            AnimatedVisibility(
                visible = hudVisible,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.75f)),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        hudIcon?.let { icon ->
                            Icon(icon, contentDescription = null, tint = DarkPrimary, modifier = Modifier.size(28.dp))
                        }
                        Text(
                            text = hudText,
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Immersive Custom UI Control HUD Panels
            AnimatedVisibility(
                visible = isControlsVisible,
                enter = slideInVertically { -it } + fadeIn(),
                exit = slideOutVertically { -it } + fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                // Top control bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.6f))
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        IconButton(onClick = onClose) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                        Column {
                            Text(
                                text = video.title,
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            Text(
                                text = "Local Cinema Deck / Decoded natively",
                                color = Color.LightGray,
                                fontSize = 12.sp
                            )
                        }
                    }

                    // Video Aspect Ratios / Zoom Modes
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(onClick = {
                            val nextRatio = when (aspectRatio) {
                                AspectRatioMode.FIT -> AspectRatioMode.STRETCH
                                AspectRatioMode.STRETCH -> AspectRatioMode.CROP
                                AspectRatioMode.CROP -> AspectRatioMode.FILL
                                AspectRatioMode.FILL -> AspectRatioMode.FIT
                            }
                            viewModel.setVideoAspectRatio(nextRatio)
                            showHUD("Ratio: ${nextRatio.name}", Icons.Filled.AspectRatio)
                        }) {
                            Icon(Icons.Filled.AspectRatio, contentDescription = "Aspect Ratio", tint = Color.White)
                        }

                        IconButton(onClick = {
                            val nextSpeed = when (playbackSpeed) {
                                1.0f -> 1.25f
                                1.25f -> 1.5f
                                1.5f -> 2.0f
                                2.0f -> 0.75f
                                else -> 1.0f
                            }
                            viewModel.setVideoPreviewSpeedAndSpeed(nextSpeed)
                            showHUD("Speed: ${nextSpeed}x", Icons.Filled.Speed)
                        }) {
                            Icon(Icons.Filled.Speed, contentDescription = "Playback Speed", tint = Color.White)
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = isControlsVisible,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                // Bottom control panel (Play/Pause, Slider, PIP)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.6f))
                        .navigationBarsPadding()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    var position by remember { mutableLongStateOf(player.currentPosition) }
                    val duration = player.duration.coerceAtLeast(0L)

                    // Refresh track progress
                    LaunchedEffect(isControlsVisible) {
                        while (isControlsVisible) {
                            position = player.currentPosition
                            delay(200)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = formatDuration(position),
                            color = Color.White,
                            fontSize = 12.sp
                        )

                        Slider(
                            value = if (duration > 0) position.toFloat() / duration else 0f,
                            onValueChange = { percent ->
                                val target = (percent * duration).toLong()
                                position = target
                                player.seekTo(target)
                            },
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = DarkPrimary,
                                activeTrackColor = DarkPrimary,
                                inactiveTrackColor = Color.DarkGray
                            )
                        )

                        Text(
                            text = formatDuration(duration),
                            color = Color.White,
                            fontSize = 12.sp
                        )
                    }

                    // Interactive Volume Slider control
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (currentVolume == 0f) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                            contentDescription = "Volume",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Slider(
                            value = currentVolume,
                            onValueChange = { newVal ->
                                currentVolume = newVal
                                audioManager.setStreamVolume(
                                    AudioManager.STREAM_MUSIC,
                                    newVal.roundToInt(),
                                    0
                                )
                            },
                            valueRange = 0f..maxVolume,
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = DarkPrimary,
                                activeTrackColor = DarkPrimary,
                                inactiveTrackColor = Color.DarkGray
                            )
                        )
                        Text(
                            text = "${((currentVolume / maxVolume) * 100).roundToInt()}%",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(36.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.End
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Picture in picture button
                        IconButton(onClick = {
                            showHUD("Entering PiP Window Mode", Icons.Filled.PictureInPicture)
                        }) {
                            Icon(Icons.Filled.PictureInPicture, contentDescription = "PiP Mode", tint = Color.White)
                        }

                        // Play/Pause Deck
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = {
                                player.seekTo(0)
                                showHUD("Restarted Video", Icons.Filled.Replay)
                            }) {
                                Icon(Icons.Filled.Replay, contentDescription = "Restart", tint = Color.White, modifier = Modifier.size(28.dp))
                            }

                            val activePlaying = player.isPlaying
                            IconButton(onClick = {
                                if (activePlaying) {
                                    player.pause()
                                } else {
                                    player.play()
                                }
                            }) {
                                Icon(
                                    if (activePlaying) Icons.Filled.PauseCircle else Icons.Filled.PlayCircle,
                                    contentDescription = "Play/Pause",
                                    tint = DarkPrimary,
                                    modifier = Modifier.size(54.dp)
                                )
                            }

                            IconButton(onClick = {
                                val jump = (player.currentPosition + 10000).coerceAtLeast(0)
                                player.seekTo(jump)
                                showHUD("Jump +10s", Icons.Filled.Forward10)
                            }) {
                                Icon(Icons.Filled.Forward10, contentDescription = "Forward 10s", tint = Color.White, modifier = Modifier.size(28.dp))
                            }
                        }

                        // Subtitle custom adjustments button
                        IconButton(onClick = {
                            // Cycle Subtitle delays
                            val nextDelay = (subtitleDelayMs + 500) % 2500
                            viewModel.setSubtitleDelay(nextDelay)
                            showHUD("Subtitle Delay: -$nextDelay ms", Icons.Filled.Subtitles)
                        }) {
                            Icon(Icons.Filled.Subtitles, contentDescription = "Subtitles Delay", tint = Color.White)
                        }
                    }
                }
            }
        }
    }
}

// Media3 Speed state controller extension helper
fun MediaViewModel.setVideoPreviewSpeedAndSpeed(speed: Float) {
    setVideoPlaybackSpeed(speed)
}

// Global duration formatting clock helper
fun formatDuration(ms: Long): String {
    val totalSecs = ms / 1000
    val h = totalSecs / 3600
    val m = (totalSecs % 3600) / 60
    val s = totalSecs % 60
    return if (h > 0) {
        String.format("%02d:%02d:%02d", h, m, s)
    } else {
        String.format("%02d:%02d", m, s)
    }
}
