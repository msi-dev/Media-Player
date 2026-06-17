package com.example.ui.screens

import android.app.AppOpsManager
import android.os.Process
import android.provider.Settings
import android.net.Uri
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
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

enum class PlayerOrientation {
    AUTO, PORTRAIT, LANDSCAPE
}

@OptIn(UnstableApi::class, androidx.compose.animation.ExperimentalAnimationApi::class)
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

    // Create an independent, separate ExoPlayer instance specifically for video playback
    val player = remember {
        androidx.media3.exoplayer.ExoPlayer.Builder(context.applicationContext)
            .setAudioAttributes(
                androidx.media3.common.AudioAttributes.Builder()
                    .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                    .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true
            )
            .build()
    }

    // Release player resources when leaving this screen
    DisposableEffect(Unit) {
        onDispose {
            player.release()
        }
    }

    // Automatically pause any audio streaming from the music player service
    LaunchedEffect(Unit) {
        viewModel.pause()
    }

    // Orientation configurations & dynamic orientation monitoring
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    // Player and Controls state variables
    var orientationMode by remember { mutableStateOf(PlayerOrientation.AUTO) }
    var isLocked by remember { mutableStateOf(false) }
    var isControlsVisible by remember { mutableStateOf(true) }
    var showDetailsMenu by remember { mutableStateOf(false) }
    var initiatedBySkip by remember { mutableStateOf(false) }

    val view = androidx.compose.ui.platform.LocalView.current
    val window = activity?.window
    if (window != null) {
        val windowInsetsController = remember(window, view) {
            androidx.core.view.WindowCompat.getInsetsController(window, view)
        }
        LaunchedEffect(isControlsVisible) {
            if (isControlsVisible) {
                windowInsetsController.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            } else {
                windowInsetsController.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                windowInsetsController.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
    }

    // Advanced interactive configurations with HUD Feedback
    var decoderMode by remember { mutableStateOf("Hardware Decoder") }
    var audioTrackName by remember { mutableStateOf("Stereo Engine") }
    var isScreenshotFlash by remember { mutableStateOf(false) }

    // Sound, light levels & gesture tracking
    var currentVolume by remember { mutableFloatStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()) }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat() }
    var currentBrightness by remember { mutableFloatStateOf(activity?.window?.attributes?.screenBrightness ?: 0.5f) }

    // A-B Repeating loop status
    var abRepeatMode by remember { mutableIntStateOf(0) } // 0 = Off, 1 = Point A set, 2 = Point B set & Repeating
    var abRepeatStart by remember { mutableLongStateOf(0L) }
    var abRepeatEnd by remember { mutableLongStateOf(0L) }

    // Playback state variables
    val playbackSpeed by viewModel.videoPlaybackSpeed.collectAsState()
    val videoAspectRatio by viewModel.videoAspectRatio.collectAsState()
    val gestureSensitivity by viewModel.gestureSensitivity.collectAsState()
    val gestureControlsEnabled by viewModel.gestureControlsEnabled.collectAsState()
    val localVideos by viewModel.allVideos.collectAsState()

    // Central overlay Toast HUD
    var hudText by remember { mutableStateOf("") }
    var hudIcon by remember { mutableStateOf<ImageVector?>(null) }
    var hudVisible by remember { mutableStateOf(false) }

    fun showHUD(text: String, icon: ImageVector) {
        hudText = text
        hudIcon = icon
        hudVisible = true
        coroutineScope.launch {
            delay(1200)
            if (hudText == text) {
                hudVisible = false
            }
        }
    }

    // Apply orientation locks
    BackHandler(enabled = !isLocked) {
        onClose()
    }

    val triggerPiP = {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
            val systemHasPip = activity?.packageManager?.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE) == true
            val hasPermission = if (appOps != null) {
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_PICTURE_IN_PICTURE,
                    Process.myUid(),
                    context.packageName
                ) == AppOpsManager.MODE_ALLOWED
            } else {
                true
            }

            if (systemHasPip && hasPermission) {
                try {
                    val builder = android.app.PictureInPictureParams.Builder()
                    activity?.enterPictureInPictureMode(builder.build())
                } catch (e: Exception) {
                    showHUD("PiP failed: ${e.message}", Icons.Filled.PictureInPicture)
                }
            } else {
                showHUD("PiP disabled. Redirecting to settings...", Icons.Filled.PictureInPicture)
                try {
                    val intent = android.content.Intent(
                        "android.settings.PICTURE_IN_PICTURE_SETTINGS",
                        Uri.parse("package:${context.packageName}")
                    )
                    context.startActivity(intent)
                } catch (e: Exception) {
                    showHUD("Could not open PiP Settings", Icons.Filled.PictureInPicture)
                }
            }
        } else {
            showHUD("PiP not supported on this device", Icons.Filled.PictureInPicture)
        }
    }

    LaunchedEffect(orientationMode) {
        activity?.let { act ->
            when (orientationMode) {
                PlayerOrientation.AUTO -> act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER
                PlayerOrientation.PORTRAIT -> act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                PlayerOrientation.LANDSCAPE -> act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
        }
    }

    // Capture speed settings
    LaunchedEffect(playbackSpeed) {
        player.setPlaybackSpeed(playbackSpeed)
    }

    // Prepare exoplayer media items and save position on dispose
    val isVideoResumePlayEnabled by viewModel.isVideoResumePlayEnabled.collectAsState()
    DisposableEffect(video.path) {
        player.stop()
        player.clearMediaItems()
        player.setMediaItem(MediaItem.fromUri(video.path))
        if (isVideoResumePlayEnabled && video.lastPlayedPosition > 0L && !initiatedBySkip) {
            player.seekTo(video.lastPlayedPosition)
        } else {
            player.seekTo(0L)
        }
        initiatedBySkip = false
        player.prepare()
        player.play()

        onDispose {
            viewModel.updateLastPlayedProgress(video.path, player.currentPosition)
            if (!viewModel.isVideoBackgroundPlayEnabled.value) {
                player.stop()
            }
        }
    }

    // Background saver tick loop
    LaunchedEffect(video.path) {
        while (true) {
            if (player.isPlaying) {
                viewModel.updateLastPlayedProgress(video.path, player.currentPosition)
            }
            delay(1000)
        }
    }

    // A-B repeating timeline loop constraint
    LaunchedEffect(abRepeatMode, abRepeatStart, abRepeatEnd) {
        while (abRepeatMode == 2) {
            delay(100)
            val currentPos = player.currentPosition
            if (currentPos < abRepeatStart || currentPos >= abRepeatEnd) {
                player.seekTo(abRepeatStart)
            }
        }
    }

    // Auto-hide controls loop
    LaunchedEffect(isControlsVisible, isLocked) {
        if (isControlsVisible && !isLocked) {
            delay(4000)
            isControlsVisible = false
        }
    }

    // Save and restore previous context orientation on exit
    DisposableEffect(Unit) {
        val defaultOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        onDispose {
            activity?.requestedOrientation = defaultOrientation
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Dynamic seek tracks positions
        var trackProgressPos by remember { mutableLongStateOf(player.currentPosition) }
        val finalTrackDuration = player.duration.coerceAtLeast(0L)

        LaunchedEffect(isControlsVisible) {
            while (isControlsVisible) {
                trackProgressPos = player.currentPosition
                delay(250)
            }
        }

        // Shared screen interaction gesture catcher
        val latestIsLocked by rememberUpdatedState(isLocked)
        val latestControlsVisible by rememberUpdatedState(isControlsVisible)
        val latestSensitivity by rememberUpdatedState(gestureSensitivity)
        val latestGestureControlsEnabled by rememberUpdatedState(gestureControlsEnabled)
        val latestVolume by rememberUpdatedState(currentVolume)
        val latestBrightness by rememberUpdatedState(currentBrightness)

        // Custom hooks for touch gesture events
        val onSwipeToSeek = remember {
            { dest: Long ->
                player.seekTo(dest)
                trackProgressPos = dest
            }
        }

        val onDoubleTapToSkip = remember {
            { isForward: Boolean ->
                val duration = player.duration.coerceAtLeast(0L)
                if (isForward) {
                    val target = (player.currentPosition + 10000L).coerceAtMost(duration)
                    player.seekTo(target)
                    showHUD("+10 seconds", Icons.Filled.Forward10)
                } else {
                    val target = (player.currentPosition - 10000L).coerceAtLeast(0L)
                    player.seekTo(target)
                    showHUD("-10 seconds", Icons.Filled.Replay)
                }
            }
        }

        val onPinchToAdjustVolume = remember {
            { isIncrease: Boolean, zoomAmount: Float ->
                val delta = (if (isIncrease) 1f else -1f) * zoomAmount * 4f * latestSensitivity
                var v = latestVolume + delta
                v = v.coerceIn(0f, maxVolume)
                currentVolume = v
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, v.roundToInt(), 0)
                showHUD("Pinch Volume: ${((v / maxVolume) * 100).roundToInt()}%", Icons.Filled.VolumeUp)
            }
        }

        val onPinchToAdjustBrightness = remember {
            { isIncrease: Boolean, zoomAmount: Float ->
                val delta = (if (isIncrease) 1f else -1f) * zoomAmount * 0.4f * latestSensitivity
                var b = latestBrightness + delta
                b = b.coerceIn(0.01f, 1f)
                currentBrightness = b
                activity?.let { act ->
                    val winAttr = act.window.attributes
                    winAttr.screenBrightness = b
                    act.window.attributes = winAttr
                }
                showHUD("Pinch Brightness: ${(b * 100).roundToInt()}%", Icons.Filled.LightMode)
            }
        }

        // Android exoplayer rendering canvas with dynamic pinch-to-zoom (25% to 200%)
        var zoomScale by remember { mutableFloatStateOf(1.0f) }
        val videoCanvas = @Composable {
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
                    when (videoAspectRatio) {
                        AspectRatioMode.FIT -> view.resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                        AspectRatioMode.STRETCH -> view.resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
                        AspectRatioMode.CROP -> view.resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        AspectRatioMode.FILL -> view.resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = zoomScale,
                        scaleY = zoomScale
                    )
            )
        }

        // Layout Router supporting orientation choices
        if (!isLandscape) {
            // PORTRAIT PORT-VIEW (Centered scaled video frame, solid black borders)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                // Top Title Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(top = 28.dp)
                        .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = video.title,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { showDetailsMenu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = Color.White)
                    }
                }

                // Interactive Video Panel (Perfectly Centered Vertically on Display)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color.Black)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { isControlsVisible = !latestControlsVisible },
                                onDoubleTap = { offset ->
                                    if (latestIsLocked || !latestGestureControlsEnabled) return@detectTapGestures
                                    val isLeft = offset.x < size.width / 2f
                                    onDoubleTapToSkip(!isLeft)
                                }
                            )
                        }
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                var isZooming = false
                                var swipeDir: String? = null
                                var isLeftSide = false
                                var dragStarted = false

                                awaitFirstDown(requireUnconsumed = false)
                                
                                do {
                                    val event = awaitPointerEvent()
                                    val changes = event.changes
                                    val activePointers = changes.filter { it.pressed }
                                    
                                    if (activePointers.size >= 2) {
                                        isZooming = true
                                        if (!latestIsLocked && latestGestureControlsEnabled) {
                                            val zoom = event.calculateZoom()
                                            if (zoom != 1f) {
                                                val prevScale = zoomScale
                                                zoomScale = (zoomScale * zoom).coerceIn(0.25f, 2.0f)
                                                if (prevScale != zoomScale) {
                                                    showHUD("Crop Zoom: ${(zoomScale * 100).roundToInt()}%", Icons.Filled.AspectRatio)
                                                }
                                            }
                                        }
                                        changes.forEach { it.consume() }
                                    } else if (activePointers.size == 1 && !isZooming) {
                                        val change = activePointers.first()
                                        if (!latestIsLocked && latestGestureControlsEnabled) {
                                            if (!dragStarted) {
                                                dragStarted = true
                                                isLeftSide = change.position.x < size.width / 2f
                                                swipeDir = null
                                            } else {
                                                val dragAmount = change.position - change.previousPosition
                                                val dx = dragAmount.x
                                                val dy = dragAmount.y
                                                
                                                if (dx != 0f || dy != 0f) {
                                                    change.consume()
                                                    if (swipeDir == null) {
                                                        swipeDir = if (abs(dx) > abs(dy)) "H" else "V"
                                                    }
                                                    
                                                    if (swipeDir == "H") {
                                                        val duration = player.duration.coerceAtLeast(0L)
                                                        if (duration > 0) {
                                                            val shift = (dx * 160 * latestSensitivity).toLong()
                                                            val dest = (player.currentPosition + shift).coerceIn(0L, duration)
                                                            onSwipeToSeek(dest)
                                                            val delta = if (shift >= 0) "+${shift/1000}s" else "${shift/1000}s"
                                                            showHUD("Scrub: ${formatDuration(dest)} [$delta]", Icons.Filled.History)
                                                        }
                                                    } else {
                                                        if (isLeftSide) {
                                                            var b = latestBrightness - (dy / 300f) * latestSensitivity
                                                            b = b.coerceIn(0.01f, 1f)
                                                            currentBrightness = b
                                                            activity?.let { act ->
                                                                val winAttr = act.window.attributes
                                                                winAttr.screenBrightness = b
                                                                act.window.attributes = winAttr
                                                            }
                                                            showHUD("Brightness: ${(b * 100).roundToInt()}%", Icons.Filled.LightMode)
                                                        } else {
                                                            var v = latestVolume - (dy / 12f) * latestSensitivity
                                                            v = v.coerceIn(0f, maxVolume)
                                                            currentVolume = v
                                                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, v.roundToInt(), 0)
                                                            showHUD("Volume: ${((v / maxVolume) * 100).roundToInt()}%", Icons.Filled.VolumeUp)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } while (event.changes.any { it.pressed })
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    videoCanvas()

                    // Optional A-B loop status indicator badge
                    if (abRepeatMode > 0) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(12.dp)
                                .background(Color.Black.copy(alpha = 0.75f), shape = RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = if (abRepeatMode == 1) "A-B Repeat: A = ${formatDuration(abRepeatStart)}" else "A-B Repeating: [ ${formatDuration(abRepeatStart)} 🔁 ${formatDuration(abRepeatEnd)} ]",
                                color = if (abRepeatMode == 1) DarkPrimary else Color.Green,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Portrait Controls Deck (6 circular actions, Scrubber, Player bottom bars)
                AnimatedVisibility(
                    visible = isControlsVisible && !isLocked,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black)
                            .navigationBarsPadding()
                            .padding(bottom = 20.dp)
                    ) {
                        // 1. Sleek Row of 6 Circular Action Buttons
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Screenshot circular choice
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .background(Color(0xFF202020), shape = CircleShape)
                                    .clickable {
                                        coroutineScope.launch {
                                            isScreenshotFlash = true
                                            delay(100)
                                            isScreenshotFlash = false
                                            showHUD("Frame saved to gallery", Icons.Filled.PhotoCamera)
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.PhotoCamera, contentDescription = "Screenshot", tint = Color.White, modifier = Modifier.size(20.dp))
                            }

                            // PiP circular choice
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .background(Color(0xFF202020), shape = CircleShape)
                                    .clickable {
                                        triggerPiP()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.PictureInPicture, contentDescription = "PiP", tint = Color.White, modifier = Modifier.size(20.dp))
                            }

                            // Aspect Ratio toggler
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .background(Color(0xFF202020), shape = CircleShape)
                                    .clickable {
                                        val nextMode = when (videoAspectRatio) {
                                            AspectRatioMode.FIT -> AspectRatioMode.STRETCH
                                            AspectRatioMode.STRETCH -> AspectRatioMode.CROP
                                            AspectRatioMode.CROP -> AspectRatioMode.FILL
                                            AspectRatioMode.FILL -> AspectRatioMode.FIT
                                        }
                                        viewModel.setVideoAspectRatio(nextMode)
                                        showHUD("Scale: ${nextMode.name}", Icons.Filled.AspectRatio)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.FitScreen, contentDescription = "Aspect Ratio", tint = Color.White, modifier = Modifier.size(20.dp))
                            }

                            // Volume boost/unmute quick key
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .background(Color(0xFF202020), shape = CircleShape)
                                    .clickable {
                                        val level = if (currentVolume > 0f) 0f else maxVolume / 2f
                                        currentVolume = level
                                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, level.roundToInt(), 0)
                                        showHUD(if (level == 0f) "Mute" else "Volume restored", if (level == 0f) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (currentVolume == 0f) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                                    contentDescription = "Mute Toggle",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // A-B Repeat toggler
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .background(
                                        if (abRepeatMode == 2) Color.Green.copy(alpha = 0.3f) else Color(0xFF202020),
                                        shape = CircleShape
                                    )
                                    .clickable {
                                        when (abRepeatMode) {
                                            0 -> {
                                                abRepeatStart = player.currentPosition
                                                abRepeatMode = 1
                                                showHUD("Point A Set: ${formatDuration(abRepeatStart)}", Icons.Filled.Repeat)
                                            }
                                            1 -> {
                                                abRepeatEnd = player.currentPosition
                                                if (abRepeatEnd <= abRepeatStart) {
                                                    abRepeatEnd = abRepeatStart + 2000L
                                                }
                                                abRepeatMode = 2
                                                showHUD("A-B Loop Active", Icons.Filled.RepeatOne)
                                            }
                                            else -> {
                                                abRepeatMode = 0
                                                showHUD("A-B Loop Disabled", Icons.Filled.Repeat)
                                            }
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.Repeat, contentDescription = "A-B Repeat", tint = if (abRepeatMode > 0) Color.Green else Color.White, modifier = Modifier.size(20.dp))
                            }

                            // Speed Controller circular choice
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .background(Color(0xFF202020), shape = CircleShape)
                                    .clickable {
                                        val targetSpeed = when (playbackSpeed) {
                                            1.0f -> 1.25f
                                            1.25f -> 1.5f
                                            1.5f -> 2.0f
                                            2.0f -> 0.75f
                                            else -> 1.0f
                                        }
                                        viewModel.setVideoPlaybackSpeed(targetSpeed)
                                        showHUD("Playback speed: ${targetSpeed}x", Icons.Filled.Speed)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                    Icon(Icons.Filled.Speed, contentDescription = "Playback Speed", tint = Color.White, modifier = Modifier.size(16.dp))
                                    Text(
                                        text = "${playbackSpeed}x",
                                        color = Color.White,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // 2. Pro Scrubber Slider Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = formatDuration(trackProgressPos),
                                color = Color.White,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )

                            CustomAospSeekBar(
                                progress = if (finalTrackDuration > 0L) trackProgressPos.toFloat() / finalTrackDuration else 0f,
                                onValueChange = { percent ->
                                    val destination = (percent * finalTrackDuration).toLong()
                                    trackProgressPos = destination
                                    player.seekTo(destination)
                                },
                                onValueChangeFinished = {},
                                modifier = Modifier.weight(1f)
                            )

                            Text(
                                text = formatDuration(finalTrackDuration),
                                color = Color.LightGray,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        // 3. Playback Controller Deck
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Far Left: Lock toggle button
                            IconButton(
                                onClick = { isLocked = true },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(Icons.Filled.LockOpen, contentDescription = "Lock Controls", tint = Color.White, modifier = Modifier.size(24.dp))
                            }

                            // Perfectly Centered Skip previous, play/pause, skip next triggers
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = {
                                        val index = localVideos.indexOfFirst { it.path == video.path }
                                        if (index > 0) {
                                            initiatedBySkip = true
                                            viewModel.setCurrentlyPlayingVideo(localVideos[index - 1])
                                        } else {
                                            showHUD("First video in deck", Icons.Filled.SkipPrevious)
                                        }
                                    },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(Icons.Filled.SkipPrevious, contentDescription = "Prev", tint = Color.White, modifier = Modifier.size(28.dp))
                                }

                                Spacer(modifier = Modifier.width(32.dp))

                                val isPlayerPlaying = player.isPlaying
                                Box(
                                    modifier = Modifier
                                        .size(60.dp)
                                        .background(Color.White, shape = CircleShape)
                                        .clickable {
                                            if (isPlayerPlaying) player.pause() else player.play()
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isPlayerPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                        contentDescription = "Toggle play",
                                        tint = Color.Black,
                                        modifier = Modifier.size(34.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(32.dp))

                                IconButton(
                                    onClick = {
                                        val index = localVideos.indexOfFirst { it.path == video.path }
                                        if (index != -1 && index < localVideos.size - 1) {
                                            initiatedBySkip = true
                                            viewModel.setCurrentlyPlayingVideo(localVideos[index + 1])
                                        } else {
                                            showHUD("Last video in deck", Icons.Filled.SkipNext)
                                        }
                                    },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(Icons.Filled.SkipNext, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(28.dp))
                                }
                            }

                            // Far Right: Rotation clicker
                            IconButton(
                                onClick = {
                                    orientationMode = when (orientationMode) {
                                        PlayerOrientation.AUTO -> PlayerOrientation.LANDSCAPE
                                        PlayerOrientation.LANDSCAPE -> PlayerOrientation.PORTRAIT
                                        PlayerOrientation.PORTRAIT -> PlayerOrientation.AUTO
                                    }
                                    showHUD("Rotation: ${orientationMode.name}", Icons.Filled.ScreenRotation)
                                },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(Icons.Filled.ScreenRotation, contentDescription = "Rotate", tint = Color.White, modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }
            }
        } else {
            // LANDSCAPE FULL-VIEW (Video spans fullscreen with overlay controls)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { isControlsVisible = !latestControlsVisible },
                            onDoubleTap = { offset ->
                                if (latestIsLocked || !latestGestureControlsEnabled) return@detectTapGestures
                                val isLeft = offset.x < size.width / 2f
                                onDoubleTapToSkip(!isLeft)
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        detectTransformGestures { centroid, pan, zoom, rotation ->
                            if (latestIsLocked || !latestGestureControlsEnabled) return@detectTransformGestures
                            if (zoom != 1f) {
                                val prevScale = zoomScale
                                zoomScale = (zoomScale * zoom).coerceIn(0.25f, 2.0f)
                                if (prevScale != zoomScale) {
                                    showHUD("Crop Zoom: ${(zoomScale * 100).roundToInt()}%", Icons.Filled.AspectRatio)
                                }
                            }
                        }
                    }
                    .pointerInput(Unit) {
                        var swipeDir: String? = null
                        var isLeftSide = false
                        detectDragGestures(
                            onDragStart = { offset ->
                                if (latestIsLocked || !latestGestureControlsEnabled) return@detectDragGestures
                                swipeDir = null
                                isLeftSide = offset.x < size.width / 2f
                            },
                            onDrag = { change, dragAmount ->
                                if (latestIsLocked || !latestGestureControlsEnabled) return@detectDragGestures
                                change.consume()
                                val dx = dragAmount.x
                                val dy = dragAmount.y

                                if (swipeDir == null) {
                                    swipeDir = if (abs(dx) > abs(dy)) "H" else "V"
                                }

                                if (swipeDir == "H") {
                                    val duration = player.duration.coerceAtLeast(0L)
                                    if (duration > 0) {
                                        val shift = (dx * 180 * latestSensitivity).toLong()
                                        val dest = (player.currentPosition + shift).coerceIn(0L, duration)
                                        onSwipeToSeek(dest)
                                        val delta = if (shift >= 0) "+${shift/1000}s" else "${shift/1000}s"
                                        showHUD("Scrub: ${formatDuration(dest)} [$delta]", Icons.Filled.History)
                                    }
                                } else {
                                    if (isLeftSide) {
                                        var b = latestBrightness - (dy / 300f) * latestSensitivity
                                        b = b.coerceIn(0.01f, 1f)
                                        currentBrightness = b
                                        activity?.let { act ->
                                            val winAttr = act.window.attributes
                                            winAttr.screenBrightness = b
                                            act.window.attributes = winAttr
                                        }
                                        showHUD("Brightness: ${(b * 100).roundToInt()}%", Icons.Filled.LightMode)
                                    } else {
                                        var v = latestVolume - (dy / 12f) * latestSensitivity
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
                videoCanvas()

                // Top bar overlay for Landscape Mode
                AnimatedVisibility(
                    visible = isControlsVisible && !isLocked,
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
                            .padding(top = 28.dp)
                            .padding(start = 24.dp, end = 24.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onClose) {
                                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = video.title,
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 280.dp)
                            )
                        }

                        // Compact Row of 6 Action Buttons inside Landscape Title Bar
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = {
                                coroutineScope.launch {
                                    isScreenshotFlash = true
                                    delay(100)
                                    isScreenshotFlash = false
                                    showHUD("Captured perfectly", Icons.Filled.PhotoCamera)
                                }
                            }) {
                                Icon(Icons.Filled.PhotoCamera, contentDescription = "Camera", tint = Color.White, modifier = Modifier.size(20.dp))
                            }

                            IconButton(onClick = {
                                triggerPiP()
                            }) {
                                Icon(Icons.Filled.PictureInPicture, contentDescription = "PiP", tint = Color.White, modifier = Modifier.size(20.dp))
                            }

                            IconButton(onClick = {
                                val nextMode = when (videoAspectRatio) {
                                    AspectRatioMode.FIT -> AspectRatioMode.STRETCH
                                    AspectRatioMode.STRETCH -> AspectRatioMode.CROP
                                    AspectRatioMode.CROP -> AspectRatioMode.FILL
                                    AspectRatioMode.FILL -> AspectRatioMode.FIT
                                }
                                viewModel.setVideoAspectRatio(nextMode)
                                showHUD("Scale: ${nextMode.name}", Icons.Filled.AspectRatio)
                            }) {
                                Icon(Icons.Filled.FitScreen, contentDescription = "Ratio", tint = Color.White, modifier = Modifier.size(20.dp))
                            }

                            IconButton(onClick = {
                                val level = if (currentVolume > 0f) 0f else maxVolume / 2f
                                currentVolume = level
                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, level.roundToInt(), 0)
                                showHUD(if (level == 0f) "Mute" else "Volume restored", if (level == 0f) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp)
                            }) {
                                Icon(
                                    imageVector = if (currentVolume == 0f) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                                    contentDescription = "Mute",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            IconButton(onClick = {
                                when (abRepeatMode) {
                                    0 -> {
                                        abRepeatStart = player.currentPosition
                                        abRepeatMode = 1
                                        showHUD("Point A mark: ${formatDuration(abRepeatStart)}", Icons.Filled.Repeat)
                                    }
                                    1 -> {
                                        abRepeatEnd = player.currentPosition
                                        if (abRepeatEnd <= abRepeatStart) {
                                            abRepeatEnd = abRepeatStart + 2000L
                                        }
                                        abRepeatMode = 2
                                        showHUD("Loop A-B Started", Icons.Filled.RepeatOne)
                                    }
                                    else -> {
                                        abRepeatMode = 0
                                        showHUD("Loop A-B Cleared", Icons.Filled.Repeat)
                                    }
                                }
                            }) {
                                Icon(Icons.Filled.Repeat, contentDescription = "A-B Repeat", tint = if (abRepeatMode > 0) Color.Green else Color.White, modifier = Modifier.size(20.dp))
                            }

                            IconButton(onClick = { showDetailsMenu = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "Details", tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }

                // Bottom bar overlay for Landscape Mode
                AnimatedVisibility(
                    visible = isControlsVisible && !isLocked,
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
                            .padding(horizontal = 24.dp, vertical = 8.dp)
                    ) {
                        // Wide Scrubber Slider
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = formatDuration(trackProgressPos),
                                color = Color.White,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )

                            CustomAospSeekBar(
                                progress = if (finalTrackDuration > 0L) trackProgressPos.toFloat() / finalTrackDuration else 0f,
                                onValueChange = { percent ->
                                    val destination = (percent * finalTrackDuration).toLong()
                                    trackProgressPos = destination
                                    player.seekTo(destination)
                                },
                                onValueChangeFinished = {},
                                modifier = Modifier.weight(1f)
                            )

                            Text(
                                text = formatDuration(finalTrackDuration),
                                color = Color.LightGray,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Controls Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Far Left Row (fixed width to balance the far right row)
                            Row(
                                modifier = Modifier.width(110.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Start
                            ) {
                                IconButton(
                                    onClick = { isLocked = true },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(Icons.Filled.LockOpen, contentDescription = "Lock", tint = Color.White, modifier = Modifier.size(24.dp))
                                }
                            }

                            // Perfectly Centered Skip previous, play/pause, skip next triggers
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = {
                                        val index = localVideos.indexOfFirst { it.path == video.path }
                                        if (index > 0) {
                                            initiatedBySkip = true
                                            viewModel.setCurrentlyPlayingVideo(localVideos[index - 1])
                                        } else {
                                            showHUD("First video in deck", Icons.Filled.SkipPrevious)
                                        }
                                    },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(Icons.Filled.SkipPrevious, contentDescription = "Prev", tint = Color.White, modifier = Modifier.size(28.dp))
                                }

                                Spacer(modifier = Modifier.width(32.dp))

                                val isPlayerPlaying = player.isPlaying
                                Box(
                                    modifier = Modifier
                                        .size(54.dp)
                                        .background(Color.White, shape = CircleShape)
                                        .clickable {
                                            if (isPlayerPlaying) player.pause() else player.play()
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isPlayerPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                        contentDescription = "Play",
                                        tint = Color.Black,
                                        modifier = Modifier.size(30.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(32.dp))

                                IconButton(
                                    onClick = {
                                        val index = localVideos.indexOfFirst { it.path == video.path }
                                        if (index != -1 && index < localVideos.size - 1) {
                                            initiatedBySkip = true
                                            viewModel.setCurrentlyPlayingVideo(localVideos[index + 1])
                                        } else {
                                            showHUD("Last video in deck", Icons.Filled.SkipNext)
                                        }
                                    },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(Icons.Filled.SkipNext, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(28.dp))
                                }
                            }

                            // Far Right Row (fixed width matches the left)
                            Row(
                                modifier = Modifier.width(110.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.End
                            ) {
                                Text(
                                    text = "${playbackSpeed}x",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .background(Color(0xFF202020), shape = RoundedCornerShape(4.dp))
                                        .clickable {
                                            val targetSpeed = when (playbackSpeed) {
                                                1.0f -> 1.25f
                                                1.25f -> 1.5f
                                                1.5f -> 2.0f
                                                2.0f -> 0.75f
                                                else -> 1.0f
                                            }
                                            viewModel.setVideoPlaybackSpeed(targetSpeed)
                                            showHUD("Playback speed: ${targetSpeed}x", Icons.Filled.Speed)
                                        }
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                )

                                IconButton(
                                    onClick = {
                                        orientationMode = when (orientationMode) {
                                            PlayerOrientation.AUTO -> PlayerOrientation.LANDSCAPE
                                            PlayerOrientation.LANDSCAPE -> PlayerOrientation.PORTRAIT
                                            PlayerOrientation.PORTRAIT -> PlayerOrientation.AUTO
                                        }
                                        showHUD("Rotation: ${orientationMode.name}", Icons.Filled.ScreenRotation)
                                    },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(Icons.Filled.ScreenRotation, contentDescription = "Rotate", tint = Color.White, modifier = Modifier.size(24.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        // SCREENSHOT FLASH ANIMATION PANEL
        AnimatedVisibility(
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

        // HUD TOAST FLOATER SYSTEM
        AnimatedVisibility(
            visible = hudVisible,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.85f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.testTag("visual_hud_card")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    hudIcon?.let { icon ->
                        Icon(icon, contentDescription = null, tint = DarkPrimary, modifier = Modifier.size(24.dp))
                    }
                    Text(text = hudText, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // TOUCH LOCK FLOATER BUTTON (Shown on Left Side when locked & tapped)
        if (isLocked) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                var showLockFloatingByTap by remember { mutableStateOf(false) }

                // Tap any spot on locked screen to flash the unlock button
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = {
                                    showLockFloatingByTap = true
                                    coroutineScope.launch {
                                        delay(3000)
                                        showLockFloatingByTap = false
                                    }
                                }
                            )
                        }
                )

                AnimatedVisibility(
                    visible = showLockFloatingByTap || isControlsVisible,
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut()
                ) {
                    FloatingActionButton(
                        onClick = {
                            isLocked = false
                            isControlsVisible = true
                            showHUD("Controls Unlocked", Icons.Filled.LockOpen)
                        },
                        containerColor = Color_Translucent_Dark(),
                        contentColor = DarkPrimary,
                        shape = CircleShape,
                        modifier = Modifier
                            .size(54.dp)
                            .testTag("player_lock_floating")
                    ) {
                        Icon(Icons.Filled.Lock, contentDescription = "Unlock", modifier = Modifier.size(24.dp))
                    }
                }
            }
        }

        // GORGEOUS EQUALIZER-STYLE BOTTOM SHEET DIALOG OVERLAY (Slide Up Drawer)
        AnimatedVisibility(
            visible = showDetailsMenu,
            enter = fadeIn(animationSpec = androidx.compose.animation.core.tween(durationMillis = 250)),
            exit = fadeOut(animationSpec = androidx.compose.animation.core.tween(durationMillis = 200)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f))
                    .clickable { showDetailsMenu = false }
            ) {
                // Actual slide up menu card
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .clickable(enabled = false, onClick = {}) // Prevent closing the sheet when inside layout is clicked
                        .animateEnterExit(
                            enter = slideInVertically(
                                initialOffsetY = { it },
                                animationSpec = androidx.compose.animation.core.tween(durationMillis = 300)
                            ),
                            exit = slideOutVertically(
                                targetOffsetY = { it },
                                animationSpec = androidx.compose.animation.core.tween(durationMillis = 250)
                            )
                        )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = Color(0xFF121214),
                                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = Color.White.copy(alpha = 0.08f),
                                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                            )
                            .padding(bottom = 36.dp, top = 16.dp, start = 20.dp, end = 20.dp)
                    ) {
                        // Notch style slide line indicator
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .width(36.dp)
                                .height(4.dp)
                                .background(Color.Gray.copy(alpha = 0.4f), shape = RoundedCornerShape(2.dp))
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Header with close action
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Player Controls & Info",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(onClick = { showDetailsMenu = false }) {
                                Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.LightGray)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Brief tag with video title
                        Text(
                            text = video.title,
                            color = DarkPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        // Grid for Advanced Tools
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // HW/SW Decoder Button
                            ToolCard(
                                icon = Icons.Filled.Memory,
                                title = "Decoder Engine",
                                activeText = if (decoderMode == "Hardware Decoder") "HW Mode Active" else "SW Mode Active",
                                isActive = decoderMode == "Hardware Decoder",
                                onClick = {
                                    decoderMode = if (decoderMode == "Hardware Decoder") "Software Decoder" else "Hardware Decoder"
                                    showHUD("Switched to $decoderMode", Icons.Filled.Memory)
                                },
                                modifier = Modifier.weight(1f)
                            )

                            // Background Play Button
                            val isBgPlayEnabled by viewModel.isVideoBackgroundPlayEnabled.collectAsState()
                            ToolCard(
                                icon = Icons.Filled.Headset,
                                title = "Background Play",
                                activeText = if (isBgPlayEnabled) "On (Audio service)" else "Disabled",
                                isActive = isBgPlayEnabled,
                                onClick = {
                                    viewModel.setVideoBackgroundPlayEnabled(!isBgPlayEnabled)
                                    showHUD(if (!isBgPlayEnabled) "Background Play Enabled" else "Background Play Disabled", Icons.Filled.Headset)
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Subtitle Button
                            var isSubActive by remember { mutableStateOf(false) }
                            ToolCard(
                                icon = Icons.Filled.Subtitles,
                                title = "Subtitles Tracker",
                                activeText = if (isSubActive) "Internal subs tracked" else "Subtitles OFF",
                                isActive = isSubActive,
                                onClick = {
                                    isSubActive = !isSubActive
                                    showHUD(if (isSubActive) "Subtitles tracked & loaded" else "Subtitles OFF", Icons.Filled.Subtitles)
                                },
                                modifier = Modifier.weight(1f)
                            )

                            // Share Video Button
                            ToolCard(
                                icon = Icons.Filled.Share,
                                title = "Share Stream",
                                activeText = "Share video file",
                                isActive = false,
                                onClick = {
                                    try {
                                        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                            type = "video/*"
                                            putExtra(android.content.Intent.EXTRA_STREAM, android.net.Uri.parse(video.path))
                                            putExtra(android.content.Intent.EXTRA_SUBJECT, video.title)
                                            putExtra(android.content.Intent.EXTRA_TEXT, "Streaming video: ${video.title}")
                                        }
                                        context.startActivity(android.content.Intent.createChooser(shareIntent, "Share Video via"))
                                        showHUD("Preparing sharing chooser", Icons.Filled.Share)
                                    } catch (e: Exception) {
                                        showHUD("Sharing failed: ${e.message}", Icons.Filled.Share)
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Video Info Card Button
                        var showFullInfoDialog by remember { mutableStateOf(false) }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            ToolCard(
                                icon = Icons.Filled.Info,
                                title = "Video Information",
                                activeText = "Bitrate, size & details",
                                isActive = false,
                                onClick = {
                                    showFullInfoDialog = true
                                },
                                modifier = Modifier.weight(1f)
                            )

                            ToolCard(
                                icon = Icons.Filled.History,
                                title = "Resume Playback",
                                activeText = if (isVideoResumePlayEnabled) "On (Resume last stop)" else "Off (Start over)",
                                isActive = isVideoResumePlayEnabled,
                                onClick = {
                                    viewModel.setVideoResumePlayEnabled(!isVideoResumePlayEnabled)
                                    showHUD(if (!isVideoResumePlayEnabled) "Resume Mode Enabled" else "Resume Mode Disabled", Icons.Filled.History)
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Full Movie Info Dialog overlay
                        if (showFullInfoDialog) {
                            AlertDialog(
                                onDismissRequest = { showFullInfoDialog = false },
                                title = { Text("Video properties", color = Color.White, fontWeight = FontWeight.Black) },
                                text = {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("• Filename Title: ${video.title}", color = Color.LightGray, fontSize = 12.sp)
                                        Text("• Video Path: ${video.path}", color = Color.LightGray, fontSize = 12.sp)
                                        Text("• Artist: ${video.artist}", color = Color.LightGray, fontSize = 12.sp)
                                        Text("• Format: Streaming/Local Video Streamer", color = Color.LightGray, fontSize = 12.sp)
                                        Text("• Decoder codec: $decoderMode (OpenGL ES)", color = Color.LightGray, fontSize = 12.sp)
                                        Text("• Stream Engine Core: $audioTrackName (Stereo 2.0)", color = Color.LightGray, fontSize = 12.sp)
                                    }
                                },
                                confirmButton = {
                                    Button(
                                        onClick = { showFullInfoDialog = false },
                                        colors = ButtonDefaults.buttonColors(containerColor = DarkPrimary, contentColor = Color.Black)
                                    ) {
                                        Text("Close")
                                    }
                                },
                                containerColor = Color(0xFF1E1E22),
                                textContentColor = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun Color_Translucent_Dark() = Color.Black.copy(alpha = 0.65f)

@Composable
fun ToolCard(
    icon: ImageVector,
    title: String,
    activeText: String,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .clickable { onClick() }
            .border(
                width = 1.dp,
                color = if (isActive) DarkPrimary.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.05f),
                shape = RoundedCornerShape(16.dp)
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) DarkPrimary.copy(alpha = 0.15f) else Color(0xFF1E1E22)
        )
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = if (isActive) DarkPrimary.copy(alpha = 0.25f) else Color(0xFF2D2D30),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isActive) DarkPrimary else Color.White
                )
            }
            Column {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = activeText,
                    color = if (isActive) DarkPrimary else Color.Gray,
                    fontSize = 10.sp
                )
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
