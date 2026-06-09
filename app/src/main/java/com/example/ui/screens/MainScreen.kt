package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
    var activeTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Audio", "Video", "Folders")

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
                    val icon = when (index) {
                        0 -> Icons.Filled.Audiotrack
                        1 -> Icons.Filled.Videocam
                        else -> Icons.Filled.Folder
                    }
                    NavigationRailItem(
                        selected = activeTab == index,
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
                    placeholder = { Text("Search tracks, videos, genres...", fontSize = 12.sp, color = Color.Gray) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search", modifier = Modifier.size(16.dp), tint = Color.Gray) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.search("") }) {
                                Icon(Icons.Filled.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp), tint = Color.Gray)
                            }
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp),
                    shape = RoundedCornerShape(24.dp),
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
                    modifier = Modifier.size(46.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "Settings",
                        tint = DarkPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // active screens mapping
            Box(modifier = Modifier.weight(1f)) {
                when (activeTab) {
                    0 -> AudioTab(viewModel)
                    1 -> VideoTab(viewModel)
                    else -> FolderTab(viewModel)
                }

                // Pinned visual Mini-Player deck
                if (currentSong != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = if (isWideScreen) 12.dp else 0.dp)
                            .padding(horizontal = if (isWideScreen) 16.dp else 0.dp)
                    ) {
                        MiniPlayerCard(
                            song = currentSong!!,
                            isPlaying = isPlaying,
                            viewModel = viewModel,
                            onClick = { isPlayerExpanded = true }
                        )
                    }
                }
            }

            // Bottom NavigationBar for standard phones
            if (!isWideScreen) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = DarkPrimary,
                    windowInsets = WindowInsets.navigationBars,
                    modifier = Modifier.height(58.dp)
                ) {
                    tabs.forEachIndexed { index, label ->
                        val icon = when (index) {
                            0 -> Icons.Filled.MusicNote
                            1 -> Icons.Filled.Videocam
                            else -> Icons.Filled.Folder
                        }
                        NavigationBarItem(
                            selected = activeTab == index,
                            onClick = { activeTab = index },
                            icon = { Icon(icon, contentDescription = label, modifier = Modifier.size(20.dp)) },
                            label = { Text(label, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 0.3.sp) },
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
                    val coverBg = Brush.linearGradient(listOf(DarkSecondary, DarkPrimary))
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(coverBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.MusicNote,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
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

    var showQueueDrawer by remember { mutableStateOf(false) }

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

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(24.dp),
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

                IconButton(onClick = { showQueueDrawer = !showQueueDrawer }) {
                    Icon(Icons.Filled.QueuePlayNext, contentDescription = "Queue Drawer", tint = if (showQueueDrawer) DarkPrimary else Color.White)
                }
            }

            if (showQueueDrawer) {
                // Queue drag reordering manager view
                Card(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.65f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Playback Queue (${queue.size} Tracks)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Spacer(modifier = Modifier.height(10.dp))
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            itemsIndexed(queue) { idx, item ->
                                val active = item.path == song.path
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.playSongAtIndex(queue, idx) }
                                        .background(
                                            if (active) DarkPrimary.copy(alpha = 0.15f) else Color.Transparent,
                                            shape = RoundedCornerShape(6.dp)
                                        )
                                        .padding(8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Filled.MusicNote, contentDescription = null, tint = if (active) DarkPrimary else Color.Gray, modifier = Modifier.size(16.dp))
                                        Text(item.title, color = if (active) DarkPrimary else Color.White, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    IconButton(onClick = { viewModel.removeFromQueue(item.path) }, modifier = Modifier.size(24.dp)) {
                                        Icon(Icons.Filled.Delete, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Large Glowing Album Art Card with AudioVisualizer
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    val coverBg = MaterialTheme.colorScheme.surfaceVariant
                    Box(
                        modifier = Modifier
                            .size(180.dp)
                            .clip(CircleShape)
                            .background(coverBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Album,
                            contentDescription = "Vinyl",
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.fillMaxSize(0.6f)
                        )
                    }

                    // Dynamic wave spectrum visualization
                    AudioVisualizer(
                        viewModel = viewModel,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .padding(horizontal = 16.dp)
                    )
                }
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
                IconButton(onClick = { viewModel.playPrevious() }) {
                    Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous", tint = Color.White, modifier = Modifier.size(48.dp))
                }

                // Core play pause trigger (enlarged)
                IconButton(
                    onClick = { if (isPlaying) viewModel.pause() else viewModel.play() }
                ) {
                    Icon(
                        if (isPlaying) Icons.Filled.PauseCircle else Icons.Filled.PlayCircle,
                        contentDescription = "PlayPause",
                        tint = DarkPrimary,
                        modifier = Modifier.size(82.dp)
                    )
                }

                // Skip next (enlarged)
                IconButton(onClick = { viewModel.playNext() }) {
                    Icon(Icons.Filled.SkipNext, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(48.dp))
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

            // Timers & speed controllers footer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.05f), shape = RoundedCornerShape(12.dp))
                    .padding(vertical = 8.dp, horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.clickable {
                        showSpeedDialog = true
                    }
                ) {
                    Icon(Icons.Filled.SlowMotionVideo, contentDescription = null, tint = DarkPrimary, modifier = Modifier.size(16.dp))
                    Text("Speed: ${playbackSpeed}x", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
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
                        text = if (timerRemaining > 0) "Sleep: ${formatDuration(timerRemaining)}" else "Sleep off",
                        color = Color.White,
                        fontSize = 12.sp,
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
