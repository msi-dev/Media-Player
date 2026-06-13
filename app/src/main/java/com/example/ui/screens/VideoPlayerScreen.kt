package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.example.data.db.MediaEntity
import com.example.ui.components.CustomAospSeekBar
import com.example.ui.theme.DarkPrimary
import com.example.ui.theme.DarkSecondary
import com.example.ui.theme.DarkTertiary
import com.example.ui.viewmodel.AspectRatioMode
import com.example.ui.viewmodel.MediaViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

// Enum supporting manual orientation overrides
enum class PlayerOrientationMode {
    AUTO, PORTRAIT, LANDSCAPE
}

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

    // Grab standard player instance from central coordinator
    val player = remember { (context.applicationContext as com.example.MediaPlayerApp).playbackManager.player }

    // Orientation configurations & layout sizing detections
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    // Screen controllers
    var currentOrientationMode by remember { mutableStateOf(PlayerOrientationMode.AUTO) }
    var isLocked by remember { mutableStateOf(false) }
    var isControlsVisible by remember { mutableStateOf(true) }
    var showExtraSettingsDrawer by remember { mutableStateOf(false) }

    // Simulated features (Xiaomi Video experience)
    var isScreenshotFlash by remember { mutableStateOf(false) }
    var decoderMode by remember { mutableStateOf("Hardware Decoder") }
    var audioTrackName by remember { mutableStateOf("Stereo Engine (Multi-Chan)") }

    // Ambient levels
    var currentVolume by remember { mutableFloatStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()) }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat() }
    var currentBrightness by remember { mutableFloatStateOf(activity?.window?.attributes?.screenBrightness ?: 0.5f) }

    // HUD overlays
    var hudText by remember { mutableStateOf("") }
    var hudIcon by remember { mutableStateOf<androidx.compose.ui.graphics.vector.ImageVector?>(null) }
    var hudVisible by remember { mutableStateOf(false) }
    var dynamicSeekLabel by remember { mutableStateOf("") }

    // Active configurations from viewmodel
    val playbackSpeed by viewModel.videoPlaybackSpeed.collectAsState()
    val aspectRatio by viewModel.videoAspectRatio.collectAsState()
    val gestureSensitivity by viewModel.gestureSensitivity.collectAsState()

    val localVideos by viewModel.allVideos.collectAsState()

    // Setup orientation locks based on enum
    LaunchedEffect(currentOrientationMode) {
        activity?.let { act ->
            when (currentOrientationMode) {
                PlayerOrientationMode.AUTO -> act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER
                PlayerOrientationMode.PORTRAIT -> act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                PlayerOrientationMode.LANDSCAPE -> act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
        }
    }

    // Media setup on player
    DisposableEffect(video.path) {
        player.stop()
        player.clearMediaItems()
        player.setMediaItem(androidx.media3.common.MediaItem.fromUri(video.path))
        if (video.lastPlayedPosition > 0L) {
            player.seekTo(video.lastPlayedPosition)
        }
        player.prepare()
        player.play()

        onDispose {
            val lastPos = player.currentPosition
            viewModel.updateLastPlayedProgress(video.path, lastPos)
            player.stop()
        }
    }

    // Continuous position saving loop for video
    LaunchedEffect(video.path) {
        while (true) {
            if (player.isPlaying) {
                viewModel.updateLastPlayedProgress(video.path, player.currentPosition)
            }
            kotlinx.coroutines.delay(1000)
        }
    }

    // Restore original phone status when returning to launcher
    DisposableEffect(Unit) {
        val originalOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        onDispose {
            activity?.requestedOrientation = originalOrientation
        }
    }

    // Autohide controller timer
    LaunchedEffect(isControlsVisible, isLocked) {
        if (isControlsVisible && !isLocked) {
            delay(4500)
            isControlsVisible = false
        }
    }

    fun showHUD(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
        hudText = text
        hudIcon = icon
        hudVisible = true
        coroutineScope.launch {
            delay(1000)
            if (hudText == text) {
                hudVisible = false
            }
        }
    }

    LaunchedEffect(playbackSpeed) {
        player.setPlaybackSpeed(playbackSpeed)
    }

    // Full screen immersive video player dialog
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            val componentWidth = maxWidth
            val componentHeight = maxHeight

            // Core Player render cell
            val videoBoxModifier = if (isLandscape) {
                Modifier.fillMaxSize()
            } else {
                Modifier
                    .fillMaxWidth()
                    .height(240.dp)
            }

            Column(modifier = Modifier.fillMaxSize()) {
                // Video display viewport area
                Box(
                    modifier = videoBoxModifier
                        .background(Color.Black)
                        .pointerInput(gestureSensitivity, isLocked, isControlsVisible) {
                            if (isLocked) {
                                detectTapGestures(
                                    onTap = { isControlsVisible = !isControlsVisible }
                                )
                                return@pointerInput
                            }
                            detectTapGestures(
                                onTap = { isControlsVisible = !isControlsVisible },
                                onDoubleTap = {
                                    if (player.isPlaying) {
                                        player.pause()
                                        showHUD("Paused", Icons.Filled.PauseCircle)
                                    } else {
                                        player.play()
                                        showHUD("Play", Icons.Filled.PlayArrow)
                                    }
                                }
                            )
                        }
                        .pointerInput(gestureSensitivity, isLocked) {
                            if (isLocked) return@pointerInput

                            var dragDirection: String? = null // "H" or "V"
                            var isLeftSide = false
                            detectDragGestures(
                                onDragStart = { offset ->
                                    dragDirection = null
                                    isLeftSide = offset.x < size.width / 2f
                                    dynamicSeekLabel = ""
                                },
                                onDragEnd = {
                                    dynamicSeekLabel = ""
                                },
                                onDragCancel = {
                                    dynamicSeekLabel = ""
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    val dx = dragAmount.x
                                    val dy = dragAmount.y

                                    if (dragDirection == null) {
                                        dragDirection = if (abs(dx) > abs(dy)) "H" else "V"
                                    }

                                    if (dragDirection == "H") {
                                        // Horizontal dynamic timeline scrubbing
                                        val duration = player.duration.coerceAtLeast(0L)
                                        if (duration > 0) {
                                            val seekOffset = (dx * 125 * gestureSensitivity).toLong()
                                            val targetPosition = (player.currentPosition + seekOffset).coerceIn(0L, duration)
                                            player.seekTo(targetPosition)
                                            val deltaStr = if (seekOffset >= 0) "+${seekOffset / 1000}s" else "${seekOffset / 1000}s"
                                            dynamicSeekLabel = "${formatDuration(targetPosition)} [ $deltaStr ]"
                                            showHUD("Scrub: $dynamicSeekLabel", Icons.Filled.History)
                                        }
                                    } else if (dragDirection == "V") {
                                        if (isLeftSide) {
                                            // Slide Left segment: Brightness
                                            var b = currentBrightness - (dy / 400f) * gestureSensitivity
                                            b = b.coerceIn(0.01f, 1f)
                                            currentBrightness = b
                                            activity?.let { act ->
                                                val lp = act.window.attributes
                                                lp.screenBrightness = b
                                                act.window.attributes = lp
                                            }
                                            showHUD("Brightness: ${(b * 100).roundToInt()}%", Icons.Filled.LightMode)
                                        } else {
                                            // Slide Right segment: Volume
                                            var v = currentVolume - (dy / 10f) * gestureSensitivity
                                            v = v.coerceIn(0f, maxVolume)
                                            currentVolume = v
                                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, v.roundToInt(), 0)
                                            showHUD("Volume: ${((v / maxVolume) * 100).roundToInt()}%", Icons.Filled.VolumeUp)
                                        }
                                    }
                                }
                            )
                        }
                ) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                useController = false
                                layoutParams = FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                this.player = player
                            }
                        },
                        update = { view ->
                            when (aspectRatio) {
                                AspectRatioMode.FIT -> view.resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                                AspectRatioMode.STRETCH -> view.resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
                                AspectRatioMode.CROP -> view.resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                AspectRatioMode.FILL -> view.resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Flash feedback animation for screenshots (Xiaomi styled)
                    androidx.compose.animation.AnimatedVisibility(
                        visible = isScreenshotFlash,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.White)
                        )
                    }

                    // LOCK OVERLAY CONTROLLER
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (isControlsVisible) {
                            FloatingActionButton(
                                onClick = {
                                    isLocked = !isLocked
                                    showHUD(if (isLocked) "Touch Locked" else "Touch Unlocked", if (isLocked) Icons.Filled.Lock else Icons.Filled.LockOpen)
                                },
                                containerColor = Color.Black.copy(alpha = 0.6f),
                                contentColor = if (isLocked) DarkPrimary else Color.White,
                                shape = CircleShape,
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("player_lock_button")
                            ) {
                                Icon(
                                    imageVector = if (isLocked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                                    contentDescription = "Lock controls",
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }

                    // STANDARD NAVIGATION & OVERLAY SLIDERS
                    if (!isLocked) {
                        // TOP BAR CONTROL HUD
                        androidx.compose.animation.AnimatedVisibility(
                            visible = isControlsVisible,
                            enter = slideInVertically { -it } + fadeIn(),
                            exit = slideOutVertically { -it } + fadeOut(),
                            modifier = Modifier.align(Alignment.TopCenter)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent)
                                        )
                                    )
                                    .statusBarsPadding()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    IconButton(onClick = onClose, modifier = Modifier.testTag("player_back_button")) {
                                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                                    }
                                    Column {
                                        Text(
                                            text = video.title,
                                            color = Color.White,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.widthIn(max = 200.dp)
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .background(DarkPrimary, shape = RoundedCornerShape(3.dp))
                                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                                            ) {
                                                Text(
                                                    text = "4K HEVC",
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.Black,
                                                    color = Color.Black
                                                )
                                            }
                                            Text(
                                                text = decoderMode,
                                                color = Color.LightGray,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    // Screenshot capture trigger button
                                    IconButton(onClick = {
                                        coroutineScope.launch {
                                            isScreenshotFlash = true
                                            delay(100)
                                            isScreenshotFlash = false
                                            showHUD("Frame captured successfully", Icons.Filled.PhotoCamera)
                                        }
                                    }) {
                                        Icon(Icons.Filled.PhotoCamera, contentDescription = "Screenshot", tint = Color.White, modifier = Modifier.size(20.dp))
                                    }

                                    // Rotate control button
                                    IconButton(onClick = {
                                        currentOrientationMode = when (currentOrientationMode) {
                                            PlayerOrientationMode.AUTO -> PlayerOrientationMode.LANDSCAPE
                                            PlayerOrientationMode.LANDSCAPE -> PlayerOrientationMode.PORTRAIT
                                            PlayerOrientationMode.PORTRAIT -> PlayerOrientationMode.AUTO
                                        }
                                        showHUD("Rotation: ${currentOrientationMode.name}", Icons.Filled.ScreenRotation)
                                    }) {
                                        Icon(
                                            imageVector = when (currentOrientationMode) {
                                                PlayerOrientationMode.AUTO -> Icons.Filled.ScreenRotation
                                                PlayerOrientationMode.PORTRAIT -> Icons.Filled.ScreenLockPortrait
                                                PlayerOrientationMode.LANDSCAPE -> Icons.Filled.ScreenLockRotation
                                            },
                                            contentDescription = "Rotate",
                                            tint = DarkPrimary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    // Premium flyout panel trigger
                                    IconButton(onClick = { showExtraSettingsDrawer = true }) {
                                        Icon(Icons.Filled.Tune, contentDescription = "Settings menu", tint = Color.White, modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                        }

                        // BOTTOM BAR CONTROL PANEL
                        androidx.compose.animation.AnimatedVisibility(
                            visible = isControlsVisible,
                            enter = slideInVertically { it } + fadeIn(),
                            exit = slideOutVertically { it } + fadeOut(),
                            modifier = Modifier.align(Alignment.BottomCenter)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                                        )
                                    )
                                    .navigationBarsPadding()
                                    .padding(horizontal = 20.dp, vertical = 12.dp)
                            ) {
                                var currentTrackPos by remember { mutableLongStateOf(player.currentPosition) }
                                val totalTrackLen = player.duration.coerceAtLeast(0L)

                                LaunchedEffect(isControlsVisible) {
                                    while (isControlsVisible) {
                                        currentTrackPos = player.currentPosition
                                        delay(250)
                                    }
                                }

                                // Interactive seek timeline slider bar
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = formatDuration(currentTrackPos),
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace
                                    )

                                    CustomAospSeekBar(
                                        progress = if (totalTrackLen > 0L) currentTrackPos.toFloat() / totalTrackLen else 0f,
                                        onValueChange = { percent ->
                                            val seekPoint = (percent * totalTrackLen).toLong()
                                            currentTrackPos = seekPoint
                                            player.seekTo(seekPoint)
                                        },
                                        onValueChangeFinished = {},
                                        modifier = Modifier.weight(1f)
                                    )

                                    Text(
                                        text = formatDuration(totalTrackLen),
                                        color = Color.LightGray,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }

                                // Main playback actions row
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        // Quick play speed change selection
                                        TextButton(onClick = {
                                            val nextSpeed = when (playbackSpeed) {
                                                1.0f -> 1.25f
                                                1.25f -> 1.5f
                                                1.5f -> 2.0f
                                                2.0f -> 0.75f
                                                else -> 1.0f
                                            }
                                            viewModel.setVideoPlaybackSpeed(nextSpeed)
                                            showHUD("Speed: ${nextSpeed}x", Icons.Filled.Speed)
                                        }) {
                                            Icon(Icons.Filled.Speed, contentDescription = null, tint = DarkPrimary, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("${playbackSpeed}x", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }

                                        // Aspect ratio change selection
                                        TextButton(onClick = {
                                            val nextRatio = when (aspectRatio) {
                                                AspectRatioMode.FIT -> AspectRatioMode.STRETCH
                                                AspectRatioMode.STRETCH -> AspectRatioMode.CROP
                                                AspectRatioMode.CROP -> AspectRatioMode.FILL
                                                AspectRatioMode.FILL -> AspectRatioMode.FIT
                                            }
                                            viewModel.setVideoAspectRatio(nextRatio)
                                            showHUD("View: ${nextRatio.name}", Icons.Filled.AspectRatio)
                                        }) {
                                            Icon(Icons.Filled.AspectRatio, contentDescription = null, tint = DarkPrimary, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(aspectRatio.name, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    // Center Playback cluster Deck
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(20.dp)
                                    ) {
                                        IconButton(onClick = {
                                            player.seekTo(0)
                                            showHUD("Replayed", Icons.Filled.Replay)
                                        }) {
                                            Icon(Icons.Filled.Replay, contentDescription = "Replay", tint = Color.White, modifier = Modifier.size(24.dp))
                                        }

                                        val playState = player.isPlaying
                                        IconButton(onClick = {
                                            if (playState) player.pause() else player.play()
                                        }) {
                                            Icon(
                                                imageVector = if (playState) Icons.Filled.PauseCircle else Icons.Filled.PlayCircle,
                                                contentDescription = "Toggle playback",
                                                tint = DarkPrimary,
                                                modifier = Modifier.size(48.dp)
                                            )
                                        }

                                        IconButton(onClick = {
                                            val target = (player.currentPosition + 10000).coerceIn(0L, totalTrackLen)
                                            player.seekTo(target)
                                            showHUD("+10 seconds", Icons.Filled.Forward10)
                                        }) {
                                            Icon(Icons.Filled.Forward10, contentDescription = "Skip Forward", tint = Color.White, modifier = Modifier.size(24.dp))
                                        }
                                    }

                                    // Volume level HUD toggle
                                    IconButton(onClick = {
                                        val nextVol = if (currentVolume > 0f) 0f else maxVolume / 2f
                                        currentVolume = nextVol
                                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, nextVol.roundToInt(), 0)
                                        showHUD(if (nextVol == 0f) "Mute" else "Sound restored", if (nextVol == 0f) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp)
                                    }) {
                                        Icon(
                                            imageVector = if (currentVolume == 0f) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                                            contentDescription = "Mute toggler",
                                            tint = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // GESTURE LEVEL HUD FEEDBACK OVERLAYS
                    androidx.compose.animation.AnimatedVisibility(
                        visible = hudVisible,
                        enter = fadeIn() + scaleIn(),
                        exit = fadeOut() + scaleOut(),
                        modifier = Modifier.align(Alignment.Center)
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.75f)),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.testTag("visual_hud_card")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                hudIcon?.let { icon ->
                                    Icon(icon, contentDescription = null, tint = DarkPrimary, modifier = Modifier.size(26.dp))
                                }
                                Text(
                                    text = hudText,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // PORTRAIT CUSTOM XIAOMI-INSPIRED BOTTOM PANEL
                if (!isLandscape) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(Color(0xFF121212))
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Section 1: Decoders & Badges
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = video.title,
                                            color = Color.White,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Black,
                                            maxLines = 2
                                        )
                                        Text(
                                            text = "${video.artist} • Local Cinema Deck",
                                            color = Color.Gray,
                                            fontSize = 13.sp,
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Box(
                                            modifier = Modifier
                                                .background(Color(0xFF2E2E2E), shape = RoundedCornerShape(4.dp))
                                                .clickable {
                                                    decoderMode = if (decoderMode.startsWith("Hardware")) "Software Decoder" else "Hardware Decoder"
                                                    showHUD(decoderMode, Icons.Filled.Hd)
                                                }
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = if (decoderMode.startsWith("Hardware")) "HW" else "SW",
                                                color = if (decoderMode.startsWith("Hardware")) DarkPrimary else Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Box(
                                            modifier = Modifier
                                                .background(Color(0xFF2E2E2E), shape = RoundedCornerShape(4.dp))
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = "DOLBY",
                                                color = DarkTertiary,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }

                            // Section 2: Quick Adjustments Row
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Card(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                coroutineScope.launch {
                                                    isScreenshotFlash = true
                                                    delay(100)
                                                    isScreenshotFlash = false
                                                    showHUD("Saved to Pictures/MediaPlayer", Icons.Filled.PhotoCamera)
                                                }
                                            },
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(12.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Icon(Icons.Filled.PhotoCamera, contentDescription = null, tint = DarkPrimary, modifier = Modifier.size(24.dp))
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text("Screenshot", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    Card(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                audioTrackName = if (audioTrackName.contains("Stereo")) "Surround Audio (5.1 DTS)" else "Stereo Engine (Multi-Chan)"
                                                showHUD("Engine: $audioTrackName", Icons.Filled.Audiotrack)
                                            },
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(12.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Icon(Icons.Filled.Audiotrack, contentDescription = null, tint = DarkTertiary, modifier = Modifier.size(24.dp))
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text("Audio Track", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    Card(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                currentOrientationMode = when (currentOrientationMode) {
                                                    PlayerOrientationMode.AUTO -> PlayerOrientationMode.LANDSCAPE
                                                    PlayerOrientationMode.LANDSCAPE -> PlayerOrientationMode.PORTRAIT
                                                    PlayerOrientationMode.PORTRAIT -> PlayerOrientationMode.AUTO
                                                }
                                                showHUD("Orientation: ${currentOrientationMode.name}", Icons.Filled.ScreenRotation)
                                            },
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(12.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Icon(Icons.Filled.ScreenRotation, contentDescription = null, tint = Color.Green, modifier = Modifier.size(24.dp))
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text("Rotate Mode", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }

                            // Section 3: Up Next Video Playlist
                            item {
                                Text(
                                    text = "Up Next in Library",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                                )
                            }

                            val filteredQueue = localVideos.filter { it.path != video.path }
                            if (filteredQueue.isEmpty()) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(100.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("No subsequent videos available in library.", color = Color.Gray, fontSize = 13.sp)
                                    }
                                }
                            } else {
                                items(filteredQueue) { itemMedia ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(0xFF1E1E1E), shape = RoundedCornerShape(10.dp))
                                            .clickable {
                                                viewModel.setCurrentlyPlayingVideo(itemMedia)
                                            }
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(54.dp)
                                                .background(Color.Black, shape = RoundedCornerShape(6.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = DarkPrimary, modifier = Modifier.size(22.dp))
                                        }

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = itemMedia.title,
                                                color = Color.White,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = formatDuration(itemMedia.duration),
                                                color = Color.Gray,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // RIGHT-SIDE QUICK SETTINGS DRAWER MENU (Xiaomi style extra configurations)
            androidx.compose.animation.AnimatedVisibility(
                visible = showExtraSettingsDrawer,
                enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
                exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.95f),
                    contentColor = Color.White,
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(280.dp)
                        .blur(if (showExtraSettingsDrawer) 0.dp else 10.dp)
                        .pointerInput(Unit) {} // Consume clicks
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp)
                            .statusBarsPadding()
                            .navigationBarsPadding(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Decoders & Modes", fontSize = 16.sp, fontWeight = FontWeight.Black, color = DarkPrimary)
                                IconButton(onClick = { showExtraSettingsDrawer = false }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Close panel", tint = Color.White)
                                }
                            }

                            HorizontalDivider(color = Color.DarkGray)

                            // Decoder Toggle Option
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Video Decoder Mode", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = { decoderMode = "Hardware Decoder" },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (decoderMode.startsWith("Hardware")) DarkPrimary else Color.DarkGray,
                                            contentColor = if (decoderMode.startsWith("Hardware")) Color.Black else Color.White
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("HW", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Button(
                                        onClick = { decoderMode = "Software Decoder" },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (decoderMode.startsWith("Software")) DarkPrimary else Color.DarkGray,
                                            contentColor = if (decoderMode.startsWith("Software")) Color.Black else Color.White
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("SW", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            // Sound enhancement Toggle Option
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Aero Dolby Sound Track", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF1A1A1A), shape = RoundedCornerShape(8.dp))
                                        .clickable {
                                            audioTrackName = if (audioTrackName.contains("Stereo")) "Surround Audio (5.1 DTS)" else "Stereo Engine (Multi-Chan)"
                                            showHUD(audioTrackName, Icons.Filled.Speaker)
                                        }
                                        .padding(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(audioTrackName, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = DarkTertiary, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }

                            // Gesture sensitivity slider
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Gestures Touch Sensitivity", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                                    Text("${(gestureSensitivity * 100).roundToInt()}%", color = DarkPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                Slider(
                                    value = gestureSensitivity,
                                    onValueChange = { viewModel.setGestureSensitivity(it) },
                                    valueRange = 0.5f..2.0f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = DarkPrimary,
                                        activeTrackColor = DarkPrimary,
                                        inactiveTrackColor = Color.DarkGray
                                    )
                                )
                            }
                        }

                        Button(
                            onClick = { showExtraSettingsDrawer = false },
                            colors = ButtonDefaults.buttonColors(containerColor = DarkPrimary, contentColor = Color.Black),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Apply Settings", fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
        }
    }
}

fun formatDuration(ms: Long): String {
    val totalSecs = (ms / 1000).coerceAtLeast(0)
    val hours = totalSecs / 3600
    val minutes = (totalSecs % 3600) / 60
    val seconds = totalSecs % 60
    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
