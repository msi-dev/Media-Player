package com.example.ui.screens

import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.basicMarquee
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
import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.isImeVisible
import com.example.ui.components.CdStyleAlbumArt
import com.example.ui.components.CustomAospSeekBar
import com.example.ui.components.AospVerticalSlider
import com.example.ui.theme.DarkPrimary
import com.example.ui.theme.DarkSecondary
import com.example.ui.theme.DarkTertiary
import com.example.ui.theme.TextSecondaryDark
import com.example.ui.viewmodel.MediaViewModel
import kotlinx.coroutines.launch
import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.ui.viewinterop.AndroidView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MediaViewModel,
    windowSizeClass: WindowSizeClass
) {
    val context = LocalContext.current
    val tabs = remember { listOf("Audio", "Video") }
    var activeTab by remember { mutableIntStateOf(0) }
    var isPlayerExpanded by remember { mutableStateOf(false) }
    var isSidebarOpen by remember { mutableStateOf(false) }
    val tabHistory = remember { mutableStateListOf<Int>(0) }

    val updateActiveTab = { idx: Int ->
        if (activeTab != idx) {
            tabHistory.add(idx)
            activeTab = idx
        }
    }

    val focusManager = LocalFocusManager.current
    var isSearchFocused by remember { mutableStateOf(false) }

    @OptIn(ExperimentalLayoutApi::class)
    val isKeyboardVisible = WindowInsets.isImeVisible

    LaunchedEffect(isKeyboardVisible) {
        if (!isKeyboardVisible) {
            focusManager.clearFocus()
        }
    }

    BackHandler(enabled = isSearchFocused && !isPlayerExpanded && !isSidebarOpen) {
        focusManager.clearFocus()
    }

    BackHandler(enabled = isPlayerExpanded) {
        isPlayerExpanded = false
    }

    BackHandler(enabled = isSidebarOpen && !isPlayerExpanded) {
        isSidebarOpen = false
    }

    BackHandler(enabled = tabHistory.size > 1 && !isPlayerExpanded && !isSidebarOpen) {
        tabHistory.removeAt(tabHistory.lastIndex)
        activeTab = tabHistory.last()
    }

    LaunchedEffect(viewModel) {
        viewModel.requestPlayerExpansion.collect {
            isPlayerExpanded = true
        }
    }
    
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

    val searchQuery by viewModel.searchQuery.collectAsState()

    val isDarkThemePref by viewModel.isDarkTheme.collectAsState()
    val isDark = when (isDarkThemePref) {
        true -> true
        false -> false
        null -> isSystemInDarkTheme()
    }

    // Adaptive Sizing Layout detection (canonical Material layouts)
    val isWideScreen = windowSizeClass.widthSizeClass != WindowWidthSizeClass.Compact

    val systemStatusBarPadding = WindowInsets.statusBars.asPaddingValues()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier.fillMaxSize()
        ) {
            // NavigationRail for tablets/wide layouts
            if (isWideScreen) {
                NavigationRail(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxHeight()
                ) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Icon(Icons.Filled.PlayCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
                    Spacer(modifier = Modifier.height(48.dp))

                    tabs.forEachIndexed { index, label ->
                        val icon = when (label) {
                            "Audio" -> Icons.Filled.Audiotrack
                            else -> Icons.Filled.Videocam
                        }
                        NavigationRailItem(
                            selected = safeActiveTab == index,
                            onClick = { updateActiveTab(index) },
                            icon = { Icon(icon, contentDescription = label, modifier = Modifier.size(28.dp)) },
                            label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp) },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
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
                        placeholder = { Text("Search tracks, videos, genres...", fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search", modifier = Modifier.size(18.dp)) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.search("") }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                                }
                            }
                        },
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp)
                            .onFocusChanged { isSearchFocused = it.isFocused },
                        shape = RoundedCornerShape(27.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            focusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            focusedTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unfocusedTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        singleLine = true
                    )

                    IconButton(
                        onClick = { isSidebarOpen = true },
                        modifier = Modifier.size(54.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.QueueMusic,
                            contentDescription = "Deck Playlist",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp)
                        )
                    }

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
                            tint = MaterialTheme.colorScheme.primary,
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
                        else -> VideoTab(viewModel)
                    }
                }
            }
        }

        // Modern Overlay Bottom Control Panel (Floating & Z-ordered always on TOP / Front of layers)
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = if (isDark) {
                            listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.85f),
                                Color.Black
                            )
                        } else {
                            listOf(
                                Color.Transparent,
                                Color.White.copy(alpha = 0.85f),
                                Color.White
                            )
                        }
                    )
                )
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Pinned visual Mini-Player deck (animated slide bottom-to-top)
            AnimatedVisibility(
                visible = currentSong != null,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (isWideScreen) 16.dp else 12.dp)
                    .padding(bottom = 6.dp)
            ) {
                currentSong?.let { song ->
                    MiniPlayerCard(
                        song = song,
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(68.dp)
                ) {
                    tabs.forEachIndexed { index, label ->
                        val icon = when (label) {
                            "Audio" -> Icons.Filled.MusicNote
                            else -> Icons.Filled.Videocam
                        }
                        NavigationBarItem(
                            selected = safeActiveTab == index,
                            onClick = { updateActiveTab(index) },
                            icon = { Icon(icon, contentDescription = label, modifier = Modifier.size(26.dp)) },
                            label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 0.3.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        )
                    }
                }
            }
        }

        // Sliding Sidebar Scrim (covers background layers)
        AnimatedVisibility(
            visible = isSidebarOpen,
            enter = fadeIn(animationSpec = androidx.compose.animation.core.tween(durationMillis = 300)),
            exit = fadeOut(animationSpec = androidx.compose.animation.core.tween(durationMillis = 250)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable { isSidebarOpen = false }
            )
        }

        // Sliding Sidebar Playlist Container (slides from right edge)
        AnimatedVisibility(
            visible = isSidebarOpen,
            enter = slideInHorizontally(
                initialOffsetX = { it },
                animationSpec = spring(dampingRatio = 0.85f, stiffness = 400f)
            ) + fadeIn(),
            exit = slideOutHorizontally(
                targetOffsetX = { it },
                animationSpec = spring(dampingRatio = 0.85f, stiffness = 450f)
            ) + fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            PlaylistSidebar(
                viewModel = viewModel,
                onClose = { isSidebarOpen = false }
            )
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
                windowSizeClass = windowSizeClass,
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
    val isShuffle by viewModel.isShuffleEnabled.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()
    val playbackSpeed by viewModel.playbackSpeed.collectAsState()
    var showMiniSpeedDialog by remember { mutableStateOf(false) }

    var isSeeking by remember { mutableStateOf(false) }
    var localSeekProgress by remember { mutableFloatStateOf(0f) }
    val currentDisplayPosition = if (isSeeking) (localSeekProgress * totalTime).toLong() else progress

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(112.dp)
            .clickable(onClick = onClick)
            .border(
                BorderStroke(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)),
                shape = RoundedCornerShape(24.dp)
            ),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 10.dp, bottom = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
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
                            .size(42.dp)
                            .clip(CircleShape)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            song.title,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            song.artist,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconButton(onClick = { viewModel.playPrevious() }, modifier = Modifier.size(44.dp)) {
                        Icon(
                            imageVector = Icons.Filled.SkipPrevious,
                            contentDescription = "Prev",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    IconButton(
                        onClick = { if (isPlaying) viewModel.pause() else viewModel.play() },
                        modifier = Modifier.size(52.dp)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Filled.PauseCircle else Icons.Filled.PlayCircle,
                            contentDescription = "PlayPause",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(44.dp)
                        )
                    }
                    IconButton(onClick = { viewModel.playNext() }, modifier = Modifier.size(44.dp)) {
                        Icon(
                            imageVector = Icons.Filled.SkipNext,
                            contentDescription = "Next",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }

            // Interactive Progress Transport Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatDuration(currentDisplayPosition),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                ) {
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

                Text(
                    text = formatDuration(totalTime),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    if (showMiniSpeedDialog) {
        AlertDialog(
            onDismissRequest = { showMiniSpeedDialog = false },
            title = { Text("Playback Speed", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface) },
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
                                    showMiniSpeedDialog = false
                                }
                                .padding(vertical = 10.dp, horizontal = 12.dp)
                                .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent, shape = RoundedCornerShape(8.dp)),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${speed}x", color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                            if (selected) {
                                Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMiniSpeedDialog = false }) {
                    Text("Close", color = MaterialTheme.colorScheme.primary)
                }
            }
        )
    }
}

// Immersive Glassmorphic Full Screen Media Overlay Sheet
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
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.primary) }
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
                    color = MaterialTheme.colorScheme.onSurface,
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
                    Text("No tracks inside execution queue", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
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
                                    if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .border(
                                    border = BorderStroke(
                                        width = 1.dp,
                                        color = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) else Color.Transparent
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
                                        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                        fontSize = 13.sp,
                                        fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = item.artist,
                                        color = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant,
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
                                        tint = MaterialTheme.colorScheme.primary,
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
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerOverlayBottomSheet(
    viewModel: MediaViewModel,
    onDismiss: () -> Unit
) {
    val eqActive by viewModel.eqEnabled.collectAsState()
    val bands by viewModel.eqBands.collectAsState()
    val frequencies = listOf("60Hz", "230Hz", "910Hz", "4kHz", "14kHz")
    var useWebEqualizer by remember { mutableStateOf(false) }
    
    val presets = listOf(
        "Flat" to listOf(0, 0, 0, 0, 0),
        "Bass Boost" to listOf(8, 5, 2, 0, -2),
        "Vocal" to listOf(-2, 1, 4, 5, 2),
        "Classic" to listOf(4, 2, -1, 2, 3),
        "Electronic" to listOf(5, 4, 0, 3, 5),
        "Jazz" to listOf(3, 1, 2, 2, -1),
        "Pop" to listOf(-1, 2, 5, 1, -2),
        "Rock" to listOf(5, 3, -1, 3, 4)
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.primary) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.75f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Equalizer,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = "Professional Equalizer", 
                        color = MaterialTheme.colorScheme.onSurface, 
                        fontWeight = FontWeight.Black, 
                        fontSize = 18.sp
                    )
                }
                
                Switch(
                    checked = eqActive,
                    onCheckedChange = { viewModel.toggleEqualizer(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                        uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            }

            if (eqActive) {
                // Mode switcher row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = { useWebEqualizer = false },
                        colors = ButtonDefaults.textButtonColors(
                            containerColor = if (!useWebEqualizer) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent,
                            contentColor = if (!useWebEqualizer) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Hardware EQ", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    TextButton(
                        onClick = { useWebEqualizer = true },
                        colors = ButtonDefaults.textButtonColors(
                            containerColor = if (useWebEqualizer) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent,
                            contentColor = if (useWebEqualizer) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Web Audio API EQ", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                // Preset horizontal scrolling chips (Visible & usable in both modes!)
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Acoustic Presets",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    
                    androidx.compose.foundation.lazy.LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(presets.size) { index ->
                            val preset = presets[index]
                            val isSelected = bands == preset.second
                            Box(
                                modifier = Modifier
                                    .background(
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                    .clickable {
                                        viewModel.setEqualizerPreset(preset.second)
                                    }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = preset.first,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                if (useWebEqualizer) {
                    WebAudioEqualizerView(viewModel = viewModel)
                } else {
                    // Vertical Sliders Deck!
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        bands.forEachIndexed { idx, value ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "${if (value >= 0) "+" else ""}${value} dB",
                                    color = if (value != 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black
                                )
                                
                                AospVerticalSlider(
                                    value = value.toFloat(),
                                    onValueChange = { newVal ->
                                        viewModel.setEqualizerBand(idx, newVal.toInt())
                                    },
                                    valueRange = -15f..15f
                                )
                                
                                Text(
                                    text = frequencies.getOrElse(idx) { "" },
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(horizontal = 24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Equalizer, 
                            contentDescription = null, 
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), 
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "Equalizer is Disabled",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Toggle active hardware frequencies on the top right to customize your ambient theater acoustics.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
fun WebAudioEqualizerView(viewModel: MediaViewModel, modifier: Modifier = Modifier) {
    val bands by viewModel.eqBands.collectAsState()
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    val darkTheme = androidx.compose.foundation.isSystemInDarkTheme()

    // Sync from native to Web View faders
    LaunchedEffect(bands) {
        webViewRef?.let { webView ->
            bands.forEachIndexed { band, value ->
                webView.post {
                     webView.evaluateJavascript("javascript:setWebEqualizerBand($band, $value)", null)
                }
            }
        }
    }

    val containerBg = if (darkTheme) Color(0xFF111111) else Color(0xFFF8F9FA)
    val borderCol = if (darkTheme) Color(0xFF222222) else Color(0xFFCFD8DC)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(380.dp)
            .background(containerBg, shape = RoundedCornerShape(12.dp))
            .border(1.dp, borderCol, shape = RoundedCornerShape(12.dp))
            .padding(4.dp)
    ) {
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        mediaPlaybackRequiresUserGesture = false
                    }
                    webViewClient = WebViewClient()
                    
                    // Prevent parent scroll/sheet drag from intercepting drag events on WebView sliders
                    setOnTouchListener { v, event ->
                        v.parent?.requestDisallowInterceptTouchEvent(true)
                        false
                    }
                    
                    // Add JavaScript Bridge for Web->Native sync
                    addJavascriptInterface(object {
                        @JavascriptInterface
                        fun setNativeEqualizerBand(bandIndex: Int, value: Float) {
                            // Sync Web slider value to central native viewModel instance
                            viewModel.setEqualizerBand(bandIndex, value.toInt())
                        }
                    }, "AndroidBridge")
                    
                    loadUrl("file:///android_asset/web_audio_equalizer.html?darkTheme=$darkTheme")
                    webViewRef = this
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { webView ->
                webViewRef = webView
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeedOverlayBottomSheet(
    playbackSpeed: Float,
    viewModel: MediaViewModel,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.primary) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Playback Speed",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Speed preset grid
            val speeds = listOf(0.25f, 0.50f, 0.75f, 1.0f, 1.5f, 2.0f, 3.0f, 4.0f)
            
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                val chunks = speeds.chunked(4)
                chunks.forEach { chunk ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        chunk.forEach { speed ->
                            val isSelected = playbackSpeed == speed
                            Button(
                                onClick = {
                                    viewModel.setPlaybackSpeed(speed)
                                    onDismiss()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = if (speed == 1.0f) "1x (Def)" else "${speed}x",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepTimerOverlayBottomSheet(
    viewModel: MediaViewModel,
    onDismiss: () -> Unit
) {
    val pickerState = rememberTimePickerState(is24Hour = true)
    var customInputMinutes by remember { mutableStateOf("") }
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.primary) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Sleep Stop Timer",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Preset Quick Buttons Row
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Preset Timings",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                
                val presets = listOf(
                    "5m" to 5,
                    "15m" to 15,
                    "30m" to 30,
                    "45m" to 45,
                    "1 Hour" to 60,
                    "2 Hours" to 120
                )
                
                androidx.compose.foundation.lazy.LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(presets.size) { idx ->
                        val (label, mins) = presets[idx]
                        Button(
                            onClick = {
                                viewModel.setSleepTimer(mins)
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                contentColor = MaterialTheme.colorScheme.primary
                            ),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

            // Time Picker Header & Clock picker view
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Select Specific Clock Stop Time",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                // M3 TimePicker with responsive thematic coloring
                TimePicker(
                    state = pickerState,
                    colors = TimePickerDefaults.colors(
                        clockDialColor = MaterialTheme.colorScheme.surfaceVariant,
                        selectorColor = MaterialTheme.colorScheme.primary,
                        periodSelectorBorderColor = MaterialTheme.colorScheme.primary,
                        clockDialSelectedContentColor = MaterialTheme.colorScheme.onPrimary,
                        clockDialUnselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        timeSelectorSelectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        timeSelectorSelectedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        timeSelectorUnselectedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        timeSelectorUnselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
                
                Button(
                    onClick = {
                        val now = java.util.Calendar.getInstance()
                        val currentHour = now.get(java.util.Calendar.HOUR_OF_DAY)
                        val currentMinute = now.get(java.util.Calendar.MINUTE)

                        var deltaMinutes = (pickerState.hour * 60 + pickerState.minute) - (currentHour * 60 + currentMinute)
                        if (deltaMinutes <= 0) {
                            deltaMinutes += 24 * 60 // Target is tomorrow
                        }
                        viewModel.setSleepTimer(deltaMinutes)
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Set Clock Stop Time", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

            // Custom Input Minutes Text Field Form
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Custom Stop Interval",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = customInputMinutes,
                        onValueChange = { input ->
                            if (input.all { it.isDigit() }) {
                                customInputMinutes = input
                            }
                        },
                        placeholder = { Text("Minutes", fontSize = 12.sp) },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                        ),
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )
                    
                    Button(
                        onClick = {
                            val mins = customInputMinutes.toIntOrNull()
                            if (mins != null && mins > 0) {
                                viewModel.setSleepTimer(mins)
                                onDismiss()
                            }
                        },
                        enabled = customInputMinutes.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary,
                            contentColor = MaterialTheme.colorScheme.onSecondary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Text("Apply", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
