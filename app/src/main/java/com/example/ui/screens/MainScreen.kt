package com.example.ui.screens

import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
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
import com.example.ui.layout.*
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
    
    val selectAudioLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.importSelectedFile(it, isVideo = false) }
    }

    val selectVideoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.importSelectedFile(it, isVideo = true) }
    }

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

                    var showPickerMenu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(
                            onClick = { showPickerMenu = true },
                            modifier = Modifier.size(54.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.FolderOpen,
                                contentDescription = "Open Local File",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = showPickerMenu,
                            onDismissRequest = { showPickerMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Open Audio File") },
                                leadingIcon = { Icon(Icons.Filled.Audiotrack, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                onClick = {
                                    showPickerMenu = false
                                    selectAudioLauncher.launch(arrayOf("audio/*"))
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Open Video File") },
                                leadingIcon = { Icon(Icons.Filled.Videocam, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                onClick = {
                                    showPickerMenu = false
                                    selectVideoLauncher.launch(arrayOf("video/*"))
                                }
                            )
                        }
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
