package com.msi.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.msi.VideoPlaybackActivity
import com.msi.data.db.MediaEntity
import com.msi.ui.components.AlbumArtImage
import com.msi.ui.components.rememberVideoThumbnail
import com.msi.ui.theme.DarkPrimary
import com.msi.ui.viewmodel.MediaViewModel

@Composable
fun PlaylistSidebar(
    viewModel: MediaViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var sidebarTab by remember { mutableIntStateOf(0) } // 0 is Audio, 1 is Video

    val currentSong by viewModel.currentSong.collectAsState()
    val currentlyPlayingVideo by viewModel.currentlyPlayingVideo.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val audioList by viewModel.filteredAudio.collectAsState()
    val videoList by viewModel.filteredVideos.collectAsState()

    // Keep manual ordered tracklists for custom reordering
    var localOrderedAudio by remember { mutableStateOf(emptyList<MediaEntity>()) }
    var localOrderedVideos by remember { mutableStateOf(emptyList<MediaEntity>()) }

    val prefs = remember { context.getSharedPreferences("playlist_preferences", android.content.Context.MODE_PRIVATE) }

    // Sync and load custom list orders from SharedPreferences
    LaunchedEffect(audioList) {
        val savedPathsStr = prefs.getString("custom_audio_order", "") ?: ""
        val savedPaths = savedPathsStr.split(",").filter { it.isNotEmpty() }
        
        val sortedAudio = if (savedPaths.isNotEmpty()) {
            val audioMap = audioList.associateBy { it.path }
            val ordered = savedPaths.mapNotNull { audioMap[it] }
            val remaining = audioList.filterNot { it.path in savedPaths }
            ordered + remaining
        } else {
            audioList
        }
        localOrderedAudio = sortedAudio
    }

    LaunchedEffect(videoList) {
        val savedPathsStr = prefs.getString("custom_video_order", "") ?: ""
        val savedPaths = savedPathsStr.split(",").filter { it.isNotEmpty() }
        
        val sortedVideos = if (savedPaths.isNotEmpty()) {
            val videoMap = videoList.associateBy { it.path }
            val ordered = savedPaths.mapNotNull { videoMap[it] }
            val remaining = videoList.filterNot { it.path in savedPaths }
            ordered + remaining
        } else {
            videoList
        }
        localOrderedVideos = sortedVideos
    }

    // Local Search Query
    var searchQuery by remember { mutableStateOf("") }

    // Local search & type filtering of custom reordered collections
    val displayAudio = remember(localOrderedAudio, searchQuery) {
        if (searchQuery.isEmpty()) {
            localOrderedAudio
        } else {
            localOrderedAudio.filter { song ->
                song.title.contains(searchQuery, ignoreCase = true) ||
                song.artist.contains(searchQuery, ignoreCase = true) ||
                song.album.contains(searchQuery, ignoreCase = true) ||
                song.path.substringAfterLast("/").contains(searchQuery, ignoreCase = true) || // File name
                "audio".contains(searchQuery, ignoreCase = true) || // Media type "audio" query
                song.path.substringAfterLast(".").contains(searchQuery, ignoreCase = true) // Media extension
            }
        }
    }

    val displayVideos = remember(localOrderedVideos, searchQuery) {
        if (searchQuery.isEmpty()) {
            localOrderedVideos
        } else {
            localOrderedVideos.filter { video ->
                video.title.contains(searchQuery, ignoreCase = true) ||
                video.artist.contains(searchQuery, ignoreCase = true) ||
                video.album.contains(searchQuery, ignoreCase = true) ||
                video.path.substringAfterLast("/").contains(searchQuery, ignoreCase = true) || // File name
                "video".contains(searchQuery, ignoreCase = true) || // Media type "video" query
                video.path.substringAfterLast(".").contains(searchQuery, ignoreCase = true) // Media extension
            }
        }
    }

    // Lazy list states to control automatic scrolling
    val lazyListState = rememberLazyListState()

    // Auto scroll when active media changes or when tab changes
    LaunchedEffect(sidebarTab, currentSong, currentlyPlayingVideo, displayAudio, displayVideos) {
        if (sidebarTab == 0) {
            val idx = displayAudio.indexOfFirst { it.path == currentSong?.path }
            if (idx >= 0) {
                lazyListState.animateScrollToItem(idx)
            }
        } else {
            val idx = displayVideos.indexOfFirst { it.path == currentlyPlayingVideo?.path }
            if (idx >= 0) {
                lazyListState.animateScrollToItem(idx)
            }
        }
    }

    // Drag indicators tracker
    var draggedActiveIndex by remember { mutableStateOf<Int?>(null) }
    var dragAccumY by remember { mutableFloatStateOf(0f) }

    // Swap helper implementation in-place
    fun swapBackingItems(
        backingList: List<MediaEntity>,
        indexA: Int,
        indexB: Int
    ): List<MediaEntity> {
        if (indexA !in backingList.indices || indexB !in backingList.indices) return backingList
        val listRef = backingList.toMutableList()
        val temp = listRef[indexA]
        listRef[indexA] = listRef[indexB]
        listRef[indexB] = temp
        return listRef
    }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(320.dp)
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp)
            )
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                shape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp)
            )
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 16.dp, start = 16.dp, end = 16.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), shape = CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.QueueMusic,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Deck Playlist",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Studio streaming deck",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                }

                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(48.dp) // Minimum 48dp touch target
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close Playlist",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Search Bar Component
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                placeholder = {
                    Text(
                        text = "Search file name or media type...",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        fontSize = 11.sp
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { searchQuery = "" },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Clear,
                                contentDescription = "Clear search",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                },
                textStyle = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )

            // Custom Segmented Tab Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(24.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Audio Tab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .background(
                            color = if (sidebarTab == 0) MaterialTheme.colorScheme.primary else Color.Transparent,
                            shape = RoundedCornerShape(20.dp)
                        )
                        .clickable { sidebarTab = 0 },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Audiotrack,
                            contentDescription = null,
                            tint = if (sidebarTab == 0) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "Audio",
                            color = if (sidebarTab == 0) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Video Tab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .background(
                            color = if (sidebarTab == 1) MaterialTheme.colorScheme.primary else Color.Transparent,
                            shape = RoundedCornerShape(20.dp)
                        )
                        .clickable { sidebarTab = 1 },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Videocam,
                            contentDescription = null,
                            tint = if (sidebarTab == 1) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "Video",
                            color = if (sidebarTab == 1) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Playlist Queue Scrollable Lists
            val activeDisplayList = if (sidebarTab == 0) displayAudio else displayVideos
            val isEmpty = activeDisplayList.isEmpty()

            if (isEmpty) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(horizontal = 24.dp)
                    ) {
                        Icon(
                            imageVector = if (sidebarTab == 0) Icons.Filled.MusicNote else Icons.Filled.Videocam,
                            contentDescription = null,
                            tint = Color.Gray.copy(alpha = 0.4f),
                            modifier = Modifier.size(44.dp)
                        )
                        Text(
                            text = if (searchQuery.isNotEmpty()) "No match found" else if (sidebarTab == 0) "No audio files available" else "No video files scanned",
                            color = Color.LightGray,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = if (searchQuery.isNotEmpty()) "Try refining your search keyword" else "Scanned tracks from storage dynamically appear in this list for high-fidelity deck streaming.",
                            color = Color.Gray,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (sidebarTab == 0) {
                        itemsIndexed(displayAudio, key = { _, song -> "audio_${song.path}" }) { index, song ->
                            val isSelected = currentSong?.path == song.path
                            val isItemDragged = draggedActiveIndex == index

                            AudioPlaylistItem(
                                song = song,
                                isSelected = isSelected,
                                isPlaying = isPlaying,
                                index = index,
                                isDragged = isItemDragged,
                                onClick = {
                                    viewModel.playSongAtIndex(displayAudio, index)
                                },
                                onTogglePlay = {
                                    if (isPlaying) {
                                        viewModel.pause()
                                    } else {
                                        viewModel.play()
                                    }
                                },
                                onDragTouch = { dragAmountY ->
                                    dragAccumY += dragAmountY
                                    val threshold = 120f // pixels to swap
                                    val activeIdx = draggedActiveIndex
                                    if (activeIdx != null) {
                                        if (dragAccumY > threshold && activeIdx < displayAudio.size - 1) {
                                            val itemA = displayAudio[activeIdx]
                                            val itemB = displayAudio[activeIdx + 1]
                                            val backingIdxA = localOrderedAudio.indexOf(itemA)
                                            val backingIdxB = localOrderedAudio.indexOf(itemB)
                                            if (backingIdxA != -1 && backingIdxB != -1) {
                                                localOrderedAudio = swapBackingItems(localOrderedAudio, backingIdxA, backingIdxB)
                                            }
                                            draggedActiveIndex = activeIdx + 1
                                            dragAccumY = 0f
                                        } else if (dragAccumY < -threshold && activeIdx > 0) {
                                            val itemA = displayAudio[activeIdx]
                                            val itemB = displayAudio[activeIdx - 1]
                                            val backingIdxA = localOrderedAudio.indexOf(itemA)
                                            val backingIdxB = localOrderedAudio.indexOf(itemB)
                                            if (backingIdxA != -1 && backingIdxB != -1) {
                                                localOrderedAudio = swapBackingItems(localOrderedAudio, backingIdxA, backingIdxB)
                                            }
                                            draggedActiveIndex = activeIdx - 1
                                            dragAccumY = 0f
                                        }
                                    }
                                },
                                onDragStart = {
                                    draggedActiveIndex = index
                                    dragAccumY = 0f
                                },
                                onDragEnd = {
                                    draggedActiveIndex = null
                                    dragAccumY = 0f
                                    prefs.edit().putString("custom_audio_order", localOrderedAudio.joinToString(",") { it.path }).apply()
                                }
                            )
                        }
                    } else {
                        itemsIndexed(displayVideos, key = { _, video -> "video_${video.path}" }) { index, video ->
                            val isSelected = currentlyPlayingVideo?.path == video.path
                            val isItemDragged = draggedActiveIndex == index

                            VideoPlaylistItem(
                                video = video,
                                isSelected = isSelected,
                                index = index,
                                isDragged = isItemDragged,
                                onClick = {
                                    viewModel.setCurrentlyPlayingVideo(video)
                                    val intent = Intent(context, VideoPlaybackActivity::class.java).apply {
                                        putExtra("extra_media_path", video.path)
                                        putExtra("extra_media_title", video.title)
                                        putExtra("extra_media_artist", video.artist)
                                        putExtra("extra_media_album", video.album)
                                        putExtra("extra_media_duration", video.duration)
                                    }
                                    context.startActivity(intent)
                                },
                                onDragTouch = { dragAmountY ->
                                    dragAccumY += dragAmountY
                                    val threshold = 120f
                                    val activeIdx = draggedActiveIndex
                                    if (activeIdx != null) {
                                        if (dragAccumY > threshold && activeIdx < displayVideos.size - 1) {
                                            val itemA = displayVideos[activeIdx]
                                            val itemB = displayVideos[activeIdx + 1]
                                            val backingIdxA = localOrderedVideos.indexOf(itemA)
                                            val backingIdxB = localOrderedVideos.indexOf(itemB)
                                            if (backingIdxA != -1 && backingIdxB != -1) {
                                                localOrderedVideos = swapBackingItems(localOrderedVideos, backingIdxA, backingIdxB)
                                            }
                                            draggedActiveIndex = activeIdx + 1
                                            dragAccumY = 0f
                                        } else if (dragAccumY < -threshold && activeIdx > 0) {
                                            val itemA = displayVideos[activeIdx]
                                            val itemB = displayVideos[activeIdx - 1]
                                            val backingIdxA = localOrderedVideos.indexOf(itemA)
                                            val backingIdxB = localOrderedVideos.indexOf(itemB)
                                            if (backingIdxA != -1 && backingIdxB != -1) {
                                                localOrderedVideos = swapBackingItems(localOrderedVideos, backingIdxA, backingIdxB)
                                            }
                                            draggedActiveIndex = activeIdx - 1
                                            dragAccumY = 0f
                                        }
                                    }
                                },
                                onDragStart = {
                                    draggedActiveIndex = index
                                    dragAccumY = 0f
                                },
                                onDragEnd = {
                                    draggedActiveIndex = null
                                    dragAccumY = 0f
                                    prefs.edit().putString("custom_video_order", localOrderedVideos.joinToString(",") { it.path }).apply()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EqualizerWaveform(
    isPlaying: Boolean,
    color: Color = DarkPrimary,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "equalizer")

    val scale1 by if (isPlaying) {
        infiniteTransition.animateFloat(
            initialValue = 0.2f,
            targetValue = 0.9f,
            animationSpec = infiniteRepeatable(
                animation = tween(420, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bar1"
        )
    } else {
        remember { mutableStateOf(0.3f) }
    }

    val scale2 by if (isPlaying) {
        infiniteTransition.animateFloat(
            initialValue = 0.3f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(280, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bar2"
        )
    } else {
        remember { mutableStateOf(0.4f) }
    }

    val scale3 by if (isPlaying) {
        infiniteTransition.animateFloat(
            initialValue = 0.1f,
            targetValue = 0.8f,
            animationSpec = infiniteRepeatable(
                animation = tween(510, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bar3"
        )
    } else {
        remember { mutableStateOf(0.2f) }
    }

    Row(
        modifier = modifier
            .height(14.dp)
            .width(16.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(scale1)
                .background(color, shape = RoundedCornerShape(1.dp))
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(scale2)
                .background(color, shape = RoundedCornerShape(1.dp))
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(scale3)
                .background(color, shape = RoundedCornerShape(1.dp))
        )
    }
}

@Composable
fun AudioPlaylistItem(
    song: MediaEntity,
    isSelected: Boolean,
    isPlaying: Boolean,
    index: Int,
    isDragged: Boolean,
    onClick: () -> Unit,
    onTogglePlay: () -> Unit,
    onDragTouch: (Float) -> Unit,
    onDragStart: () -> Unit,
    onDragEnd: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (isDragged) MaterialTheme.colorScheme.primary else if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f) else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDragged) MaterialTheme.colorScheme.surfaceVariant else if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Drag handle on left
            Icon(
                imageVector = Icons.Filled.DragHandle,
                contentDescription = "Tap status, Drag to reorder",
                tint = if (isDragged) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier
                    .size(36.dp)
                    .padding(6.dp)
                    .pointerInput(index) {
                        detectDragGestures(
                            onDragStart = { onDragStart() },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                onDragTouch(dragAmount.y)
                            },
                            onDragEnd = { onDragEnd() },
                            onDragCancel = { onDragEnd() }
                        )
                    }
            )

            // Clickable content box
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onClick),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Album Art with small equalizer overlay if active
                Box(contentAlignment = Alignment.Center) {
                    AlbumArtImage(
                        songPath = song.path,
                        songTitle = song.title,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(Color.Black.copy(alpha = 0.4f)),
                            contentAlignment = Alignment.Center
                        ) {
                            EqualizerWaveform(isPlaying = isPlaying, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                // Titles Column
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = song.title,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .basicMarquee()
                        )
                        Text(
                            text = "(${formatDuration(song.duration)})",
                            color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                        if (isSelected) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Filled.VolumeUp else Icons.Filled.VolumeMute,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                    Text(
                        text = song.artist,
                        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp,
                        maxLines = 1,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .basicMarquee()
                    )
                }
            }

            // Right utility: Play/pause action trigger or duration label
            if (isSelected) {
                IconButton(
                    onClick = onTogglePlay,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.PauseCircle else Icons.Filled.PlayCircle,
                        contentDescription = "Play/Pause toggle",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            } else {
                Text(
                    text = formatDuration(song.duration),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(end = 4.dp),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun VideoPlaylistItem(
    video: MediaEntity,
    isSelected: Boolean,
    index: Int,
    isDragged: Boolean,
    onClick: () -> Unit,
    onDragTouch: (Float) -> Unit,
    onDragStart: () -> Unit,
    onDragEnd: () -> Unit
) {
    val bitmap = rememberVideoThumbnail(video.path)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (isDragged) MaterialTheme.colorScheme.primary else if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f) else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDragged) MaterialTheme.colorScheme.surfaceVariant else if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Drag handle on left
            Icon(
                imageVector = Icons.Filled.DragHandle,
                contentDescription = "Drag to reorder",
                tint = if (isDragged) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier
                    .size(36.dp)
                    .padding(6.dp)
                    .pointerInput(index) {
                        detectDragGestures(
                            onDragStart = { onDragStart() },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                onDragTouch(dragAmount.y)
                            },
                            onDragEnd = { onDragEnd() },
                            onDragCancel = { onDragEnd() }
                        )
                    }
            )

            // Clickable content box
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onClick),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Video Preview Box
                Box(
                    modifier = Modifier
                        .size(width = 62.dp, height = 46.dp)
                        .background(Color.Black, shape = RoundedCornerShape(8.dp))
                        .clip(RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Video Thumbnail",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF2D2D30))
                        )
                        Icon(
                            imageVector = Icons.Filled.Movie,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Floating small play icon overlay or active equalizer wave overlay
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .background(
                                color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.9f) else Color.Black.copy(alpha = 0.5f),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            EqualizerWaveform(isPlaying = true, color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary)
                        } else {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }

                // Details Column
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = video.title,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .basicMarquee()
                        )
                        Text(
                            text = "(${formatDuration(video.duration)})",
                            color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Filled.VolumeUp,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                    Text(
                        text = formatDuration(video.duration),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
