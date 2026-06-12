package com.example.mediaplayer.ui.screens

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.mediaplayer.MediaViewModel
import com.example.mediaplayer.data.SongEntity
import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun VideoScreen(
    viewModel: MediaViewModel,
    modifier: Modifier = Modifier,
    onMenuClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val allSongs by viewModel.allSongs.collectAsState()
    val videos = remember(allSongs) { allSongs.filter { it.mediaType == "VIDEO" } }

    var activeVideoUrl by remember { mutableStateOf<SongEntity?>(null) }
    var useHtml5Engine by remember { mutableStateOf(false) }

    var searchQuery by remember { mutableStateOf("") }
    val filteredVideos = remember(videos, searchQuery) {
        if (searchQuery.isBlank()) videos
        else videos.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
            it.artist.contains(searchQuery, ignoreCase = true)
        }
    }

    val tabs = listOf("Videos", "Folder")
    var selectedTab by remember { mutableStateOf("Videos") }

    if (activeVideoUrl == null) {
        // VIDEO LIST
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            // Top Search & Menu row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onMenuClick,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Menu",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search videos...") },
                    leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = "Search") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                        focusedContainerColor = MaterialTheme.colorScheme.surface
                    ),
                    singleLine = true
                )
            }

            // 2 Tab Bar
            TabRow(
                selectedTabIndex = tabs.indexOf(selectedTab),
                containerColor = Color.Transparent,
                divider = {},
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.tabIndicatorOffset(tabPositions[tabs.indexOf(selectedTab)])
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                tabs.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = { Text(text = tab, fontWeight = FontWeight.SemiBold, fontSize = 14.sp) }
                    )
                }
            }

            // Control engine bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("HTML5 Core Engine", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.width(6.dp))
                Switch(
                    checked = useHtml5Engine,
                    onCheckedChange = { useHtml5Engine = it },
                    thumbContent = { Icon(Icons.Default.Code, null, modifier = Modifier.size(12.dp)) }
                )
            }

            if (filteredVideos.isEmpty()) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No compatible videos found",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                when (selectedTab) {
                    "Videos" -> {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            items(filteredVideos) { video ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { activeVideoUrl = video },
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Video representation thumbnail box
                                        Box(
                                            modifier = Modifier
                                                .size(80.dp, 56.dp)
                                                .background(MaterialTheme.colorScheme.surfaceVariant),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PlayCircleFilled,
                                                contentDescription = "Play Icon",
                                                tint = MaterialTheme.colorScheme.secondary,
                                                modifier = Modifier.size(28.dp)
                                            )
                                        }

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = video.title,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1
                                            )
                                            Text(
                                                text = video.artist,
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    "Folder" -> {
                        val folders = remember(filteredVideos) {
                            filteredVideos.groupBy { video ->
                                if (video.id.startsWith("http")) {
                                    "Online Streams"
                                } else {
                                    val parts = video.id.split("/")
                                    if (parts.size > 2) {
                                        parts[parts.size - 2]
                                    } else {
                                        "Internal Storage"
                                    }
                                }
                            }
                        }
                        var expandedFolder by remember { mutableStateOf<String?>(null) }

                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            items(folders.keys.toList()) { folderName ->
                                val folderVideos = folders[folderName] ?: emptyList()
                                val isExpanded = expandedFolder == folderName
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { expandedFolder = if (isExpanded) null else folderName },
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Folder,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.secondary,
                                                    modifier = Modifier.size(40.dp)
                                                )
                                                Column {
                                                    Text(text = folderName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                    Text(text = "${folderVideos.size} videos", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                            Icon(
                                                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                                contentDescription = "Expand"
                                            )
                                        }

                                        if (isExpanded) {
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Divider(color = MaterialTheme.colorScheme.outlineVariant)
                                            Spacer(modifier = Modifier.height(8.dp))
                                            folderVideos.forEach { video ->
                                                Card(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 4.dp)
                                                        .clickable { activeVideoUrl = video },
                                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                                                    shape = RoundedCornerShape(10.dp)
                                                ) {
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(8.dp),
                                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(60.dp, 40.dp)
                                                                .background(MaterialTheme.colorScheme.surfaceVariant),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Icon(imageVector = Icons.Default.PlayCircleOutline, contentDescription = null, modifier = Modifier.size(20.dp))
                                                        }
                                                        Column {
                                                            Text(text = video.title, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                            Text(text = video.artist, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                        }
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(6.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    } else {
        if (useHtml5Engine) {
            Html5VideoPlayer(
                video = activeVideoUrl!!,
                onBack = { activeVideoUrl = null }
            )
        } else {
            // FULL GESTURE-POWERED IMMERSIVE PLAYER
            ImmersiveVideoPlayer(
                video = activeVideoUrl!!,
                onBack = { activeVideoUrl = null }
            )
        }
    }
}

@Composable
fun ImmersiveVideoPlayer(
    video: SongEntity,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context as? Activity }
    val scope = rememberCoroutineScope()

    // Dedicated local video player instance
    val videoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(video.id))
            prepare()
            playWhenReady = true
        }
    }

    var isPlaying by remember { mutableStateOf(true) }
    var progress by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var isLandscape by remember { mutableStateOf(false) }

    // Gesture control overlay variables
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }
    var currentVolume by remember { mutableStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)) }

    var currentBrightness by remember {
        mutableStateOf(
            activity?.window?.attributes?.screenBrightness?.takeIf { it >= 0 } ?: 0.5f
        )
    }

    // Overlay feedback UI states
    var showVolumeOverlay by remember { mutableStateOf(false) }
    var showBrightnessOverlay by remember { mutableStateOf(false) }
    var overlayTimeoutJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    // Auto-update timelines
    LaunchedEffect(videoPlayer) {
        videoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    duration = videoPlayer.duration
                }
            }
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
        })
        
        while (true) {
            progress = videoPlayer.currentPosition
            delay(500)
        }
    }

    // Cleanup video player
    DisposableEffect(Unit) {
        onDispose {
            videoPlayer.release()
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    // Back handler redirection
    BackHandler {
        if (isLandscape) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            isLandscape = false
        } else {
            onBack()
        }
    }

    fun startOverlayTimeout() {
        overlayTimeoutJob?.cancel()
        overlayTimeoutJob = scope.launch {
            delay(1500)
            showVolumeOverlay = false
            showBrightnessOverlay = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            // Gesture inputs
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { offset ->
                        // Seek back on left, forward on right
                        if (offset.x < size.width / 2) {
                            videoPlayer.seekTo((videoPlayer.currentPosition - 10000).coerceAtLeast(0))
                        } else {
                            videoPlayer.seekTo((videoPlayer.currentPosition + 10000).coerceAtMost(duration))
                        }
                    },
                    onTap = {
                        // Regular pause trigger
                        if (videoPlayer.isPlaying) videoPlayer.pause() else videoPlayer.play()
                    }
                )
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { /* ... */ },
                    onDrag = { change, dragAmount ->
                        val screenWidth = size.width
                        val axisY = dragAmount.y

                        if (change.position.x < screenWidth / 2) {
                            // Brightness Adjustment (Left third of display screen)
                            showBrightnessOverlay = true
                            val delta = -axisY / 400f
                            currentBrightness = (currentBrightness + delta).coerceIn(0.01f, 1f)
                            activity?.window?.let { window ->
                                val lp = window.attributes
                                lp.screenBrightness = currentBrightness
                                window.attributes = lp
                            }
                            startOverlayTimeout()
                        } else {
                            // Volume Adjustment (Right third of display screen)
                            showVolumeOverlay = true
                            val deltaIdx = if (axisY < 0) 1 else -1
                            currentVolume = (currentVolume + deltaIdx).coerceIn(0, maxVolume)
                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, currentVolume, 0)
                            startOverlayTimeout()
                        }
                    }
                )
            }
    ) {
        // Media3 Player Frame
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = videoPlayer
                    useController = false
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // IMMERSIVE TOP NAVIGATION BAR OVERLAY
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(Color.Black.copy(alpha = 0.4f))
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = {
                    if (isLandscape) {
                        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        isLandscape = false
                    } else {
                        onBack()
                    }
                }) {
                    Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Column {
                    Text(text = video.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(text = video.artist, color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                }
            }

            // Lock screen / flip button
            IconButton(onClick = {
                if (isLandscape) {
                    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                } else {
                    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                }
                isLandscape = !isLandscape
            }) {
                Icon(
                    imageVector = if (isLandscape) Icons.Default.ScreenLockLandscape else Icons.Default.ScreenRotation,
                    contentDescription = "Rotate",
                    tint = Color.White
                )
            }
        }

        // BRIGHTNESS / VOLUME RADIAL SLIDERS FEEDBACK DISPLAY
        if (showBrightnessOverlay) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 24.dp)
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(imageVector = Icons.Default.BrightnessMedium, contentDescription = "Brightness", tint = Color.Yellow)
                    Text(text = "${(currentBrightness * 100).toInt()}%", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (showVolumeOverlay) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 24.dp)
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(imageVector = Icons.Default.VolumeUp, contentDescription = "Volume", tint = Color.Cyan)
                    Text(text = "$currentVolume/$maxVolume", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }

        // BOTTOM PLAYBACK SCRUBBER CARD OVERLAY
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Color.Black.copy(alpha = 0.4f))
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val formattedProgress = formatTime(progress)
                val formattedDuration = formatTime(duration)

                Text(text = formattedProgress, color = Color.White, fontSize = 11.sp)
                Slider(
                    value = progress.toFloat(),
                    onValueChange = { videoPlayer.seekTo(it.toLong()) },
                    valueRange = 0f..(duration.toFloat().coerceAtLeast(1f)),
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.Cyan,
                        activeTrackColor = Color.Cyan,
                        inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                    )
                )
                Text(text = formattedDuration, color = Color.White, fontSize = 11.sp)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Controls Play / Pause Center Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    if (isPlaying) videoPlayer.pause() else videoPlayer.play()
                }) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.PauseCircleFilled else Icons.Default.PlayCircleFilled,
                        contentDescription = "Toggle play",
                        tint = Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
        }
    }
}

private fun formatTime(millis: Long): String {
    val sec = (millis / 1000) % 60
    val min = (millis / 60000) % 60
    val hr = (millis / 3600000)
    return if (hr > 0) {
        String.format("%d:%02d:%02d", hr, min, sec)
    } else {
        String.format("%02d:%02d", min, sec)
    }
}

@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Html5VideoPlayer(
    video: SongEntity,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context as? Activity }
    
    var isLandscape by remember { mutableStateOf(false) }

    val htmlContent = remember(video.id) {
        val rawHtml = """
<!DOCTYPE html>
<html>
<head>
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
<style>
  body, html {
    margin: 0; padding: 0;
    width: 100%; height: 100%;
    background-color: #0d0d0d;
    font-family: 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
    overflow: hidden;
    color: #ffffff;
    display: flex;
    flex-direction: column;
    justify-content: center;
    align-items: center;
  }
  
  .player-container {
    position: relative;
    width: 100%;
    height: 100%;
    display: flex;
    flex-direction: column;
    justify-content: center;
    align-items: center;
    background-color: #000;
  }

  video {
    width: 100%;
    height: 100%;
    background-color: #000;
    object-fit: contain;
    transition: object-fit 0.3s ease;
  }

  .spinner-overlay {
    position: absolute;
    top: 0; left: 0; right: 0; bottom: 0;
    background: rgba(13, 13, 13, 0.85);
    display: flex;
    flex-direction: column;
    justify-content: center;
    align-items: center;
    z-index: 10;
    transition: opacity 0.4s ease;
  }

  .spinner {
    width: 60px;
    height: 60px;
    border: 4px solid rgba(0, 255, 230, 0.1);
    border-radius: 50%;
    border-top-color: #00ffe6;
    animation: spin 1s linear infinite;
    filter: drop-shadow(0 0 10px #00ffe6);
  }

  .loading-text {
    margin-top: 16px;
    font-size: 14px;
    letter-spacing: 2px;
    font-weight: 600;
    text-transform: uppercase;
    color: #00ffe6;
    text-shadow: 0 0 8px rgba(0, 255, 230, 0.5);
  }

  @keyframes spin {
    0% { transform: rotate(0deg); }
    100% { transform: rotate(360deg); }
  }

  .controls-bar {
    position: absolute;
    bottom: 0; left: 0; right: 0;
    background: linear-gradient(to top, rgba(13,13,13,0.95), rgba(13,13,13,0.7), rgba(0,0,0,0));
    padding: 16px;
    box-sizing: border-box;
    display: flex;
    flex-direction: column;
    gap: 12px;
    z-index: 5;
    transition: opacity 0.3s ease;
    opacity: 1;
  }

  .controls-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    width: 100%;
    gap: 12px;
  }

  .controls-left, .controls-right {
    display: flex;
    align-items: center;
    gap: 14px;
  }

  button {
    background: none;
    border: none;
    color: #ffffff;
    cursor: pointer;
    padding: 6px;
    outline: none;
    display: flex;
    align-items: center;
    justify-content: center;
    transition: all 0.2s ease;
  }

  button:active {
    transform: scale(0.9);
  }

  button svg {
    width: 24px;
    height: 24px;
    fill: currentColor;
    filter: drop-shadow(0 0 2px rgba(255,255,255,0.3));
  }

  button.active svg {
    color: #00ffe6;
    filter: drop-shadow(0 0 6px rgba(0,255,230,0.8));
  }

  .time-display {
    font-size: 12px;
    font-family: monospace;
    opacity: 0.85;
    letter-spacing: 1px;
  }

  .seek-slider {
    flex-grow: 1;
    -webkit-appearance: none;
    background: rgba(255, 255, 255, 0.2);
    height: 4px;
    border-radius: 2px;
    outline: none;
    cursor: pointer;
    transition: height 0.1s;
  }

  .seek-slider:hover {
    height: 6px;
  }

  .seek-slider::-webkit-slider-thumb {
    -webkit-appearance: none;
    appearance: none;
    width: 14px;
    height: 14px;
    border-radius: 50%;
    background: #00ffe6;
    box-shadow: 0 0 8px #00ffe6;
    cursor: pointer;
    transition: transform 0.1s;
  }

  .seek-slider::-webkit-slider-thumb:hover {
    transform: scale(1.25);
  }

  .toast {
    position: absolute;
    top: 70px;
    background: rgba(0, 255, 230, 0.15);
    border: 1px solid #00ffe6;
    padding: 8px 16px;
    border-radius: 20px;
    font-size: 12px;
    font-weight: bold;
    color: #00ffe6;
    text-shadow: 0 0 5px rgba(0, 255, 230, 0.5);
    opacity: 0;
    transition: opacity 0.3s ease;
    pointer-events: none;
    z-index: 10;
  }
</style>
</head>
<body>

<div class="player-container" id="player-container" onclick="toggleOverlay()">
  <video id="html5-video" playsinline webkit-playsinline>
    <source src="##VIDEO_URL##" type="video/mp4">
  </video>

  <div class="spinner-overlay" id="loading-spinner">
    <div class="spinner"></div>
    <div class="loading-text">Buffering Media</div>
  </div>

  <div class="toast" id="aspect-toast">FIT: CONTAIN</div>

  <div class="controls-bar" id="controls" onclick="event.stopPropagation()">
    <div class="controls-row">
      <span class="time-display" id="time-elapsed">00:00</span>
      <input type="range" class="seek-slider" id="seek-bar" value="0" min="0" max="100">
      <span class="time-display" id="time-duration">00:00</span>
    </div>

    <div class="controls-row">
      <div class="controls-left">
        <button id="play-pause-btn" onclick="togglePlay()">
          <svg id="play-icon" viewBox="0 0 24 24"><path d="M8 5v14l11-7z"/></svg>
          <svg id="pause-icon" viewBox="0 0 24 24" style="display:none;"><path d="M6 19h4V5H6v14zm8-14v14h4V5h-4z"/></svg>
        </button>
      </div>

      <div class="controls-right">
        <button id="aspect-btn" onclick="toggleAspectRatio()" title="Aspect Ratio">
          <svg viewBox="0 0 24 24"><path d="M19 12h-2v3h-3v2h5v-5zM7 9h3V7H5v5h2V9zm14-6H3c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h18c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm0 16.01H3V4.99h18v14.02z"/></svg>
        </button>

        <button id="fullscreen-btn" onclick="toggleFullscreen()" title="Fullscreen">
          <svg viewBox="0 0 24 24"><path d="M7 14H5v5h5v-2H7v-3zm-2-4h2V7h3V5H5v5zm12 7h-3v2h5v-5h-2v3zM14 5v2h3v3h2V5h-5z"/></svg>
        </button>
      </div>
    </div>
  </div>
</div>

<script>
  const video = document.getElementById('html5-video');
  const playPauseBtn = document.getElementById('play-pause-btn');
  const playIcon = document.getElementById('play-icon');
  const pauseIcon = document.getElementById('pause-icon');
  const seekBar = document.getElementById('seek-bar');
  const timeElapsed = document.getElementById('time-elapsed');
  const timeDuration = document.getElementById('time-duration');
  const spinner = document.getElementById('loading-spinner');
  const toast = document.getElementById('aspect-toast');
  const controls = document.getElementById('controls');

  let overlayVisible = true;
  let overlayTimeout;

  const fitModes = ['contain', 'cover', 'fill'];
  let currentFitIdx = 0;

  video.addEventListener('canplay', () => {
    hideSpinner();
    updateDuration();
  });

  video.addEventListener('waiting', () => {
    showSpinner();
  });

  video.addEventListener('playing', () => {
    hideSpinner();
    playIcon.style.display = 'none';
    pauseIcon.style.display = 'block';
    resetOverlayTimeout();
  });

  video.addEventListener('pause', () => {
    playIcon.style.display = 'block';
    pauseIcon.style.display = 'none';
    showOverlay();
  });

  video.addEventListener('timeupdate', () => {
    updateProgress();
  });

  let isSeeking = false;
  seekBar.addEventListener('input', () => {
    isSeeking = true;
    const time = video.duration * (seekBar.value / 100);
    timeElapsed.innerText = formatTime(time);
  });

  seekBar.addEventListener('change', () => {
    const time = video.duration * (seekBar.value / 100);
    video.currentTime = time;
    isSeeking = false;
    resetOverlayTimeout();
  });

  function showSpinner() {
    spinner.style.opacity = '1';
    spinner.style.pointerEvents = 'auto';
  }

  function hideSpinner() {
    spinner.style.opacity = '0';
    spinner.style.pointerEvents = 'none';
  }

  function togglePlay() {
    if (video.paused || video.ended) {
      video.play();
    } else {
      video.pause();
    }
  }

  function formatTime(seconds) {
    if (isNaN(seconds)) return '00:00';
    const m = Math.floor(seconds / 60);
    const s = Math.floor(seconds % 60);
    return m.toString().padStart(2, '0') + ':' + s.toString().padStart(2, '0');
  }

  function updateDuration() {
    timeDuration.innerText = formatTime(video.duration);
  }

  function updateProgress() {
    if (isSeeking) return;
    if (video.duration) {
      seekBar.value = (video.currentTime / video.duration) * 100;
    }
    timeElapsed.innerText = formatTime(video.currentTime);
  }

  function toggleAspectRatio() {
    currentFitIdx = (currentFitIdx + 1) % fitModes.length;
    const activeFit = fitModes[currentFitIdx];
    video.style.objectFit = activeFit;
    
    toast.innerText = 'FIT: ' + activeFit.toUpperCase();
    toast.style.opacity = '1';
    setTimeout(() => {
      toast.style.opacity = '0';
    }, 1200);
  }

  function toggleFullscreen() {
    if (window.AndroidBridge && window.AndroidBridge.toggleFullscreen) {
      window.AndroidBridge.toggleFullscreen();
    } else {
      const elem = document.getElementById('player-container');
      if (!document.fullscreenElement) {
        elem.requestFullscreen().catch(err => {
          console.log(err);
        });
      } else {
        document.exitFullscreen();
      }
    }
  }

  function toggleOverlay() {
    if (overlayVisible) {
      hideOverlay();
    } else {
      showOverlay();
      resetOverlayTimeout();
    }
  }

  function showOverlay() {
    controls.style.opacity = '1';
    controls.style.pointerEvents = 'auto';
    overlayVisible = true;
  }

  function hideOverlay() {
    if (video.paused) return;
    controls.style.opacity = '0';
    controls.style.pointerEvents = 'none';
    overlayVisible = false;
  }

  function resetOverlayTimeout() {
    clearTimeout(overlayTimeout);
    showOverlay();
    if (!video.paused) {
      overlayTimeout = setTimeout(hideOverlay, 3000);
    }
  }

  video.autoplay = true;
  video.load();
</script>
</body>
</html>
        """.trimIndent()
        rawHtml.replace("##VIDEO_URL##", video.id)
    }

    DisposableEffect(Unit) {
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        }
    }

    BackHandler {
        if (isLandscape) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            isLandscape = false
        } else {
            onBack()
        }
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black)
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    if (isLandscape) {
                        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
                        isLandscape = false
                    } else {
                        onBack()
                    }
                }) {
                    Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = video.title,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        text = "HTML5 Core Engine",
                        color = Color.Cyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    ) { innerPadding ->
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.domStorageEnabled = true
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    settings.allowFileAccess = true
                    
                    webChromeClient = object : WebChromeClient() {}
                    webViewClient = object : WebViewClient() {}
                    
                    addJavascriptInterface(object : Any() {
                        @JavascriptInterface
                        fun toggleFullscreen() {
                            activity?.runOnUiThread {
                                if (isLandscape) {
                                    activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                    activity.window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
                                } else {
                                    activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                    activity.window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
                                }
                                isLandscape = !isLandscape
                            }
                        }
                    }, "AndroidBridge")
                    
                    loadDataWithBaseURL("https://localhost", htmlContent, "text/html", "UTF-8", null)
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color.Black)
        )
    }
}
