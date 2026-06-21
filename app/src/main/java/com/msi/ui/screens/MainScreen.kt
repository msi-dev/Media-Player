package com.msi.ui.screens

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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.rememberCoroutineScope
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
import com.msi.data.db.MediaEntity
import com.msi.ui.components.AlbumArtImage
import com.msi.ui.layout.*
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.isImeVisible
import com.msi.ui.components.CdStyleAlbumArt
import com.msi.ui.components.CustomAospSeekBar
import com.msi.ui.components.AospVerticalSlider
import com.msi.ui.theme.DarkPrimary
import com.msi.ui.theme.DarkSecondary
import com.msi.ui.theme.DarkTertiary
import com.msi.ui.theme.TextSecondaryDark
import com.msi.ui.viewmodel.MediaViewModel
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

    val tabs = remember { listOf("Music", "Video", "Recent", "Album", "Favorite", "Playlist", "Artists", "Genres") }
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val coroutineScope = rememberCoroutineScope()
    
    var isPlayerExpanded by remember { mutableStateOf(false) }
    var isSidebarOpen by remember { mutableStateOf(false) }

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

    BackHandler(enabled = pagerState.currentPage > 0 && !isPlayerExpanded && !isSidebarOpen) {
        coroutineScope.launch {
            pagerState.animateScrollToPage(0)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.requestPlayerExpansion.collect {
            isPlayerExpanded = true
        }
    }
    
    val safeActiveTab = pagerState.currentPage

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
                            "Music" -> Icons.Filled.Audiotrack
                            "Video" -> Icons.Filled.Videocam
                            "Recent" -> Icons.Filled.History
                            "Album" -> Icons.Filled.Album
                            "Favorite" -> Icons.Filled.Favorite
                            "Playlist" -> Icons.Filled.QueueMusic
                            "Artists" -> Icons.Filled.Person
                            "Genres" -> Icons.Filled.Style
                            else -> Icons.Filled.MusicNote
                        }
                        NavigationRailItem(
                            selected = safeActiveTab == index,
                            onClick = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(index)
                                }
                            },
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
                            .height(48.dp)
                            .onFocusChanged { isSearchFocused = it.isFocused },
                        shape = RoundedCornerShape(24.dp),
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

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        var showPickerMenu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(
                                onClick = { showPickerMenu = true },
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.FolderOpen,
                                    contentDescription = "Open Local File",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
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
                                val intent = Intent(context, com.msi.SettingsActivity::class.java)
                                context.startActivity(intent)
                            },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = "Settings",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Modern scrollable pregnancy-shaped pill tabs row is drawn right under the search bar!
                ScrollableTabRow(
                    selectedTabIndex = safeActiveTab,
                    edgePadding = 8.dp,
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.primary,
                    indicator = {}, // No standard line, we are using capsule pills!
                    divider = {},
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    tabs.forEachIndexed { index, title ->
                        val isSelected = safeActiveTab == index
                        Tab(
                            selected = isSelected,
                            onClick = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(index)
                                }
                            },
                            modifier = Modifier
                                .padding(horizontal = 4.dp, vertical = 4.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                ),
                            text = {
                                Text(
                                    text = title,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Modern Slidable ViewPager using HorizontalPager
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f)
                ) { page ->
                    when (tabs[page]) {
                        "Music" -> TracksView(viewModel)
                        "Video" -> VideoTab(viewModel)
                        "Recent" -> RecentView(viewModel)
                        "Album" -> AlbumsView(viewModel)
                        "Favorite" -> FavoritesView(viewModel)
                        "Playlist" -> PlaylistsView(viewModel)
                        "Artists" -> ArtistsView(viewModel)
                        "Genres" -> GenresView(viewModel)
                    }
                }
            }
        }

        val hasPlayedOnce by viewModel.hasPlayedOnce.collectAsState()
        val showMiniPlayer = hasPlayedOnce && currentSong != null

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
                                Color.Black.copy(alpha = 0.82f),
                                Color.Black
                            )
                        } else {
                            listOf(
                                Color.Transparent,
                                Color.White.copy(alpha = 0.82f),
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
                visible = showMiniPlayer,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (isWideScreen) 16.dp else 12.dp)
                    .padding(bottom = 12.dp)
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
