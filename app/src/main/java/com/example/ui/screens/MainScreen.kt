package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.MediaEntity
import com.example.ui.components.AlbumArtImage
import com.example.ui.theme.DarkPrimary
import com.example.ui.theme.DarkSecondary
import com.example.ui.theme.DarkTertiary
import com.example.ui.theme.TextSecondaryDark
import com.example.ui.viewmodel.MediaViewModel
import kotlinx.coroutines.launch
import android.content.Intent
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MediaViewModel
) {
    val context = LocalContext.current
    val tabs = remember { listOf("Audio", "Video", "Folders") }
    var activeTab by remember { mutableIntStateOf(0) }
    
    val safeActiveTab = remember(activeTab, tabs) {
        if (activeTab >= tabs.size && tabs.isNotEmpty()) {
            tabs.size - 1
        } else if (activeTab < 0) {
            0
        } else {
            activeTab
        }
    }

    val currentSong by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    var isPlayerExpanded by remember { mutableStateOf(false) }

    val searchQuery by viewModel.searchQuery.collectAsState()

    // Adaptive Sizing Layout detection (canonical Material layouts)
    val configuration = LocalConfiguration.current
    val isWideScreen = configuration.screenWidthDp >= 600

    val systemStatusBarPadding = WindowInsets.statusBars.asPaddingValues()

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // NavigationRail for tablets/wide layouts
        if (isWideScreen) {
            NavigationRail(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = DarkPrimary,
                modifier = Modifier.fillMaxHeight()
            ) {
                Spacer(modifier = Modifier.height(24.dp))
                Icon(Icons.Filled.PlayCircle, contentDescription = null, tint = DarkPrimary, modifier = Modifier.size(36.dp))
                Spacer(modifier = Modifier.height(48.dp))

                tabs.forEachIndexed { index, label ->
                    val icon = when (label) {
                        "Audio" -> Icons.Filled.Audiotrack
                        "Video" -> Icons.Filled.Videocam
                        else -> Icons.Filled.Folder
                    }
                    NavigationRailItem(
                        selected = safeActiveTab == index,
                        onClick = { activeTab = index },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp) },
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = Color.White,
                            selectedTextColor = DarkPrimary,
                            indicatorColor = DarkTertiary,
                            unselectedIconColor = TextSecondaryDark,
                            unselectedTextColor = TextSecondaryDark
                        )
                    )
                }
            }
        }

        // Standard Main content panel
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            // Minimal Full-Width Search Bar with Settings button instead of Title Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = systemStatusBarPadding.calculateTopPadding())
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.search(it) },
                    placeholder = { Text("Search tracks, videos, genres...", fontSize = 13.sp, color = Color.Gray) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search", modifier = Modifier.size(18.dp), tint = Color.Gray) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.search("") }) {
                                Icon(Icons.Filled.Close, contentDescription = "Clear", modifier = Modifier.size(18.dp), tint = Color.Gray)
                            }
                        }
                    },
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 13.sp,
                        color = Color.White
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp),
                    shape = RoundedCornerShape(27.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DarkPrimary,
                        unfocusedBorderColor = Color.DarkGray,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    singleLine = true
                )

                IconButton(
                    onClick = {
                        val intent = Intent(context, com.example.SettingsActivity::class.java)
                        context.startActivity(intent)
                    },
                    modifier = Modifier.size(54.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "Settings",
                        tint = DarkPrimary,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // active screens mapping
            Box(modifier = Modifier.weight(1f)) {
                val activeTabLabel = if (safeActiveTab < tabs.size) tabs[safeActiveTab] else "Audio"
                when (activeTabLabel) {
                    "Audio" -> AudioTab(viewModel)
                    "Video" -> VideoTab(viewModel)
                    else -> FolderTab(viewModel)
                }
            }

            // Pinned visual Mini-Player deck (positioned structurally above the bottom navigation bar)
            if (currentSong != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = if (isWideScreen) 16.dp else 12.dp)
                        .padding(bottom = 8.dp)
                ) {
                    MiniPlayerCard(
                        song = currentSong!!,
                        isPlaying = isPlaying,
                        viewModel = viewModel,
                        onClick = { isPlayerExpanded = true }
                    )
                }
            }

            // Bottom NavigationBar for standard phones
            if (!isWideScreen) {
                NavigationBar(
                    containerColor = Color.Transparent,
                    contentColor = DarkPrimary,
                    windowInsets = WindowInsets.navigationBars,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(76.dp)
                ) {
                    tabs.forEachIndexed { index, label ->
                        val icon = when (label) {
                            "Audio" -> Icons.Filled.MusicNote
                            "Video" -> Icons.Filled.Videocam
                            else -> Icons.Filled.Folder
                        }
                        NavigationBarItem(
                            selected = safeActiveTab == index,
                            onClick = { activeTab = index },
                            icon = { Icon(icon, contentDescription = label, modifier = Modifier.size(24.dp)) },
                            label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 0.3.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color.White,
                                selectedTextColor = DarkPrimary,
                                indicatorColor = DarkTertiary,
                                unselectedIconColor = TextSecondaryDark,
                                unselectedTextColor = TextSecondaryDark
                            )
                        )
                    }
                }
            }
        }
    }

    // Material motion slide expansion full music sheet
    AnimatedVisibility(
        visible = isPlayerExpanded,
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = spring(dampingRatio = 0.82f, stiffness = 400f)
        ) + fadeIn(),
        exit = slideOutVertically(
            targetOffsetY = { it },
            animationSpec = spring(dampingRatio = 0.85f, stiffness = 450f)
        ) + fadeOut()
    ) {
        currentSong?.let { song ->
            FullscreenPlayerSheet(
                song = song,
                isPlaying = isPlaying,
                viewModel = viewModel,
                onCollapse = { isPlayerExpanded = false }
            )
        }
    }
}

// Bottom floating bar
@Composable
fun MiniPlayerCard(
    song: MediaEntity,
    isPlaying: Boolean,
    viewModel: MediaViewModel,
    onClick: () -> Unit
) {
    val progress by viewModel.currentPosition.collectAsState()
    val totalTime by viewModel.duration.collectAsState()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    AlbumArtImage(
                        songPath = song.path,
                        songTitle = song.title,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(6.dp))
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            song.title,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            song.artist,
                            color = Color.LightGray,
                            fontSize = 11.sp,
                            maxLines = 1
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconButton(onClick = { viewModel.playPrevious() }) {
                        Icon(Icons.Filled.SkipPrevious, contentDescription = "Prev", tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                    IconButton(onClick = {
                        if (isPlaying) viewModel.pause() else viewModel.play()
                    }) {
                        Icon(
                            if (isPlaying) Icons.Filled.PauseCircle else Icons.Filled.PlayCircle,
                            contentDescription = "PlayPause",
                            tint = DarkPrimary,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    IconButton(onClick = { viewModel.playNext() }) {
                        Icon(Icons.Filled.SkipNext, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                }
            }

            // Quick micro progress seek tracker
            val percentage = if (totalTime > 0) progress.toFloat() / totalTime else 0f
            LinearProgressIndicator(
                progress = percentage,
                color = DarkPrimary,
                trackColor = Color.DarkGray,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.3.dp)
            )
        }
    }
}

// Immersive Glassmorphic Full Screen Media Overlay Sheet
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullscreenPlayerSheet(
    song: MediaEntity,
    isPlaying: Boolean,
    viewModel: MediaViewModel,
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
            .background(Color.Black)
    ) {
        // Blurred Abstract Dynamic cover background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        )

        val dims = com.example.ui.theme.ResponsiveDimensions.dimensions
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(dims.fullscreenPlayerPadding),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onCollapse) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Collapse", tint = Color.White, modifier = Modifier.size(32.dp))
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
                        tint = if (showQueueSheet) DarkPrimary else Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            // Large Glowing Album Art Card with AudioVisualizer
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                AlbumArtImage(
                    songPath = song.path,
                    songTitle = song.title,
                    modifier = Modifier
                        .size(dims.albumArtSize)
                        .clip(RoundedCornerShape(24.dp))
                )

                // Dynamic wave spectrum visualization
                AudioVisualizer(
                    viewModel = viewModel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
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
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = song.artist,
                    color = DarkPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "${song.album} (Support APE/FLAC High Fidelity decode)",
                    color = Color.Gray,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Timeline Scrubbing Slider block
            Column(modifier = Modifier.fillMaxWidth()) {
                var localSeekProgress by remember { mutableFloatStateOf(0f) }
                var isSeeking by remember { mutableStateOf(false) }

                val currentDisplayPosition = if (isSeeking) (localSeekProgress * totalTime).toLong() else progress

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(formatDuration(currentDisplayPosition), color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(formatDuration(totalTime), color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                Slider(
                    value = if (totalTime > 0) currentDisplayPosition.toFloat() / totalTime else 0f,
                    onValueChange = { percent ->
                        isSeeking = true
                        localSeekProgress = percent
                    },
                    onValueChangeFinished = {
                        val target = (localSeekProgress * totalTime).toLong()
                        viewModel.seekTo(target)
                        isSeeking = false
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = DarkPrimary,
                        activeTrackColor = DarkPrimary,
                        inactiveTrackColor = Color.DarkGray
                    )
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

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
                        tint = if (isShuffle) DarkPrimary else Color.White.copy(alpha = 0.55f),
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Skip previous (enlarged)
                IconButton(
                    onClick = { viewModel.playPrevious() },
                    modifier = Modifier.size(64.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.SkipPrevious,
                        contentDescription = "Previous",
                        tint = Color.White,
                        modifier = Modifier.size(44.dp)
                    )
                }

                // Core play pause trigger (enlarged)
                IconButton(
                    onClick = { if (isPlaying) viewModel.pause() else viewModel.play() },
                    modifier = Modifier.size(96.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.PauseCircle else Icons.Filled.PlayCircle,
                        contentDescription = "PlayPause",
                        tint = DarkPrimary,
                        modifier = Modifier.size(90.dp)
                    )
                }

                // Skip next (enlarged)
                IconButton(
                    onClick = { viewModel.playNext() },
                    modifier = Modifier.size(64.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.SkipNext,
                        contentDescription = "Next",
                        tint = Color.White,
                        modifier = Modifier.size(44.dp)
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
                        tint = if (repeatMode > 0) DarkPrimary else Color.White.copy(alpha = 0.55f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Timers, speed, & equalizer controllers footer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.05f), shape = RoundedCornerShape(12.dp))
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
                    Text("Speed: ${playbackSpeed}x", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.clickable {
                        showEqualizerDialog = true
                    }
                ) {
                    Icon(Icons.Filled.Equalizer, contentDescription = null, tint = DarkPrimary, modifier = Modifier.size(16.dp))
                    Text("Equalizer", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                    Icon(Icons.Filled.Alarm, contentDescription = null, tint = if (timerRemaining > 0) DarkTertiary else Color.Gray, modifier = Modifier.size(16.dp))
                    Text(
                        text = if (timerRemaining > 0) formatDuration(timerRemaining) else "Sleep off",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    // Modern Dialog for Speed Selection
    if (showSpeedDialog) {
        AlertDialog(
            onDismissRequest = { showSpeedDialog = false },
            title = { Text("Playback Speed", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
                    speeds.forEach { speed ->
                        val selected = playbackSpeed == speed
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setPlaybackSpeed(speed)
                                    showSpeedDialog = false
                                }
                                .padding(vertical = 10.dp, horizontal = 12.dp)
                                .background(if (selected) DarkPrimary.copy(alpha = 0.2f) else Color.Transparent, shape = RoundedCornerShape(8.dp)),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${speed}x", color = if (selected) DarkPrimary else Color.White, fontSize = 14.sp)
                            if (selected) {
                                Icon(Icons.Filled.Check, contentDescription = null, tint = DarkPrimary, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSpeedDialog = false }) {
                    Text("Close", color = DarkPrimary)
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }

    // Modern Interactive Parametric Equalizer Dialog
    if (showEqualizerDialog) {
        val eqActive by viewModel.eqEnabled.collectAsState()
        val bands by viewModel.eqBands.collectAsState()
        val frequencies = listOf("60Hz", "230Hz", "910Hz", "4kHz", "14kHz")
        
        AlertDialog(
            onDismissRequest = { showEqualizerDialog = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.Equalizer, contentDescription = null, tint = DarkPrimary)
                        Text("Equalizer", fontWeight = FontWeight.Black, fontSize = 18.sp, color = Color.White)
                    }
                    Switch(
                        checked = eqActive,
                        onCheckedChange = { viewModel.toggleEqualizer(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = DarkPrimary,
                            checkedTrackColor = DarkPrimary.copy(alpha = 0.4f)
                        )
                    )
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (eqActive) {
                        bands.forEachIndexed { idx, sliderVal ->
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = frequencies.getOrElse(idx) { "Band ${idx + 1}" },
                                        color = Color.LightGray,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "${if (sliderVal >= 0) "+" else ""}${sliderVal} dB",
                                        color = DarkPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                                Slider(
                                    value = sliderVal.toFloat(),
                                    onValueChange = { newVal ->
                                        viewModel.setEqualizerBand(idx, newVal.toInt())
                                    },
                                    valueRange = -15f..15f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = DarkPrimary,
                                        activeTrackColor = DarkPrimary,
                                        inactiveTrackColor = Color.DarkGray
                                    )
                                )
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Toggle active hardware frequencies on the top right to customize your ambient theater acoustics.",
                                color = Color.Gray,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showEqualizerDialog = false }) {
                    Text("Done", color = DarkPrimary, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = Color(0xFF1E1E1E),
            shape = RoundedCornerShape(20.dp)
        )
    }

    // Modern Material 3 Sleep Timer Clock-Face Picker Dialog
    if (showSleepTimerPicker) {
        val pickerState = rememberTimePickerState(
            is24Hour = true
        )

        AlertDialog(
            onDismissRequest = { showSleepTimerPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val now = java.util.Calendar.getInstance()
                        val currentHour = now.get(java.util.Calendar.HOUR_OF_DAY)
                        val currentMinute = now.get(java.util.Calendar.MINUTE)

                        var deltaMinutes = (pickerState.hour * 60 + pickerState.minute) - (currentHour * 60 + currentMinute)
                        if (deltaMinutes <= 0) {
                            deltaMinutes += 24 * 60 // Target is tomorrow
                        }
                        viewModel.setSleepTimer(deltaMinutes)
                        showSleepTimerPicker = false
                    }
                ) {
                    Text("Set", color = DarkPrimary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSleepTimerPicker = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            },
            title = {
                Text("Select Sleep Stop Time", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
            },
            text = {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    TimePicker(
                        state = pickerState,
                        colors = TimePickerDefaults.colors(
                            clockDialColor = Color.DarkGray,
                            selectorColor = DarkPrimary,
                            periodSelectorBorderColor = DarkPrimary,
                            clockDialSelectedContentColor = Color.Black,
                            clockDialUnselectedContentColor = Color.LightGray
                        )
                    )
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceVariant
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueOverlayBottomSheet(
    queue: List<MediaEntity>,
    currentSong: MediaEntity,
    viewModel: MediaViewModel,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
        containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(12.dp),
        dragHandle = { BottomSheetDefaults.DragHandle(color = DarkPrimary) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Playback Queue (${queue.size} Songs)",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 17.sp
                )
                
                if (queue.isNotEmpty()) {
                    TextButton(
                        onClick = { 
                            viewModel.clearQueue()
                            onDismiss()
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Filled.ClearAll, contentDescription = "Clear Queue", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Clear All", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (queue.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No tracks inside execution queue", color = Color.Gray, fontSize = 13.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 450.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(queue) { idx, item ->
                        val active = item.path == currentSong.path
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { 
                                    viewModel.playSongAtIndex(queue, idx)
                                }
                                .background(
                                    if (active) DarkPrimary.copy(alpha = 0.12f) else Color.Transparent,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .border(
                                    border = BorderStroke(
                                        width = 1.dp,
                                        color = if (active) DarkPrimary.copy(alpha = 0.3f) else Color.Transparent
                                    ),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                AlbumArtImage(
                                    songPath = item.path,
                                    songTitle = item.title,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                )
                                
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.title,
                                        color = if (active) DarkPrimary else Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = item.artist,
                                        color = if (active) DarkPrimary.copy(alpha = 0.7f) else Color.Gray,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (active) {
                                    Icon(
                                        imageVector = Icons.Filled.VolumeUp,
                                        contentDescription = "Active Playing",
                                        tint = DarkPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { viewModel.removeFromQueue(item.path) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Delete,
                                        contentDescription = "Remove From Queue",
                                        tint = Color.Gray,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun AudioVisualizer(
    viewModel: MediaViewModel,
    modifier: Modifier = Modifier
) {
    val spectrum by viewModel.spectrumData.collectAsState()

    Canvas(modifier = modifier) {
        val count = spectrum.size
        val barWidth = size.width / count
        val maxBarHeight = size.height

        spectrum.forEachIndexed { index, value ->
            val left = index * barWidth
            val animatedHeight = value * maxBarHeight
            val top = maxBarHeight - animatedHeight

            val brush = Brush.verticalGradient(
                colors = listOf(
                    DarkTertiary,
                    DarkPrimary
                )
            )

            drawRoundRect(
                brush = brush,
                topLeft = androidx.compose.ui.geometry.Offset(left + 2.dp.toPx(), top),
                size = androidx.compose.ui.geometry.Size((barWidth - 4.dp.toPx()).coerceAtLeast(1f), animatedHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())
            )
        }
    }
}
