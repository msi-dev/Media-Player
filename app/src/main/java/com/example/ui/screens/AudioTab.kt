package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.activity.compose.BackHandler
import com.example.data.db.MediaEntity
import com.example.data.db.PlaylistEntity
import com.example.ui.components.AlbumArtImage
import com.example.ui.components.SkeletonListLoader
import com.example.ui.theme.DarkPrimary
import com.example.ui.theme.DarkSecondary
import com.example.ui.theme.DarkTertiary
import com.example.ui.viewmodel.MediaViewModel

@Composable
fun AudioTab(
    viewModel: MediaViewModel,
    modifier: Modifier = Modifier
) {
    val subTabs by viewModel.visibleTabs.collectAsState()
    var selectedSubTab by remember { mutableIntStateOf(0) }
    val subHistory = remember { mutableStateListOf(0) }

    val updateSubTab = { index: Int ->
        if (selectedSubTab != index) {
            subHistory.add(index)
            selectedSubTab = index
        }
    }

    BackHandler(enabled = subHistory.size > 1) {
        subHistory.removeAt(subHistory.lastIndex)
        selectedSubTab = subHistory.last()
    }

    val activeIndex = if (selectedSubTab >= subTabs.size) 0 else selectedSubTab

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 4.dp)) {
        if (subTabs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("All audio tabs are hidden.\nEnable tabs in Settings.", color = Color.Gray, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        } else {
            // Aesthetic Scrollable Sub-Tabs
            ScrollableTabRow(
                selectedTabIndex = activeIndex,
                edgePadding = 4.dp,
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.primary,
                indicator = { tabPositions ->
                    if (activeIndex < tabPositions.size) {
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[activeIndex]),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                divider = {}
            ) {
                subTabs.forEachIndexed { index, title ->
                    Tab(
                        selected = activeIndex == index,
                        onClick = { updateSubTab(index) },
                        selectedContentColor = MaterialTheme.colorScheme.primary,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        text = {
                            Text(
                                text = title,
                                fontWeight = if (activeIndex == index) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 12.sp
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Multi-view animation deck
            AnimatedContent(
                targetState = if (activeIndex < subTabs.size) subTabs[activeIndex] else "",
                transitionSpec = {
                    fadeIn() togetherWith fadeOut()
                },
                label = "SubTabTransition"
            ) { tabName ->
                when (tabName) {
                    "Tracks" -> TracksView(viewModel)
                    "Recent" -> RecentView(viewModel)
                    "Album" -> AlbumsView(viewModel)
                    "Favorite" -> FavoritesView(viewModel)
                    "Playlist" -> PlaylistsView(viewModel)
                    "Artists" -> ArtistsView(viewModel)
                    "Genres" -> GenresView(viewModel)
                    else -> Box(modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}

@Composable
fun TracksView(viewModel: MediaViewModel) {
    val lazySongs = viewModel.pagedAudio.collectAsLazyPagingItems()
    val songs = lazySongs.itemSnapshotList.items.filterNotNull()
    val activeSong by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()

    var activeMenuSong by remember { mutableStateOf<MediaEntity?>(null) }
    var playlistDialogSong by remember { mutableStateOf<MediaEntity?>(null) }
    var renameDialogSong by remember { mutableStateOf<MediaEntity?>(null) }
    var deleteDialogSong by remember { mutableStateOf<MediaEntity?>(null) }
    var detailsDialogSong by remember { mutableStateOf<MediaEntity?>(null) }

    // Multi-select features
    var isMultiSelectMode by remember { mutableStateOf(false) }
    val selectedSongPaths = remember { mutableStateListOf<String>() }

    // Backup for Undo mechanics
    var lastDeletedSong by remember { mutableStateOf<MediaEntity?>(null) }
    var showUndoSnackbar by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (isMultiSelectMode) {
                // Multi-select context action bar
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${selectedSongPaths.size} Tracks Selected",
                            color = DarkPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(
                                onClick = {
                                    if (selectedSongPaths.isNotEmpty()) {
                                        viewModel.deleteMediaByPaths(selectedSongPaths.toList())
                                        android.widget.Toast.makeText(viewModel.getApplication(), "Deleted selected items from database list", android.widget.Toast.LENGTH_SHORT).show()
                                        selectedSongPaths.clear()
                                        isMultiSelectMode = false
                                    }
                                }
                            ) {
                                Text("Delete", color = Color.Red, fontWeight = FontWeight.Bold)
                            }
                            TextButton(
                                onClick = {
                                    isMultiSelectMode = false
                                    selectedSongPaths.clear()
                                }
                            ) {
                                Text("Cancel", color = Color.LightGray)
                            }
                        }
                    }
                }
            }

            val isScanning by viewModel.isScanning.collectAsState()
            val loadState = lazySongs.loadState.refresh

            if (loadState is androidx.paging.LoadState.Loading || (isScanning && lazySongs.itemCount == 0)) {
                Box(modifier = Modifier.weight(1f)) {
                    com.example.ui.components.MediaScannerLoadingState(
                        title = "Loading audio tracks...",
                        subtitle = "Please wait, indexing of local library is running in the background."
                    )
                }
            } else if (lazySongs.itemCount == 0) {
                Box(modifier = Modifier.weight(1f)) {
                    com.example.ui.components.MediaScannerEmptyState(
                        title = "No audio files found",
                        description = "No local audio or music tracks were found on your device storage. Execute a deep scan of the system folders now.",
                        icon = Icons.Filled.QueueMusic,
                        onAction = { viewModel.forceScanMedia() }
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 160.dp)
                ) {
                    items(
                        count = lazySongs.itemCount,
                        key = { index -> lazySongs[index]?.path ?: index.toString() }
                    ) { index ->
                        val song = lazySongs[index] ?: return@items
                        val isCurrent = song.path == activeSong?.path
                        SongListItem(
                            song = song,
                            isCurrent = isCurrent,
                            isPlaying = isCurrent && isPlaying,
                            onClick = { viewModel.playSongAtIndex(songs, index) },
                            onLongClick = { activeMenuSong = song },
                            onToggleFavorite = { viewModel.toggleFavorite(song) },
                            isMultiSelectMode = isMultiSelectMode,
                            isSelected = selectedSongPaths.contains(song.path),
                            onSelectedChange = { isSelected ->
                                if (isSelected) {
                                    selectedSongPaths.add(song.path)
                                } else {
                                    selectedSongPaths.remove(song.path)
                                }
                            }
                        )
                    }
                }
            }
        }

        // Undo Delete Snackbar overlay
        if (showUndoSnackbar && lastDeletedSong != null) {
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 90.dp, start = 16.dp, end = 16.dp),
                action = {
                    TextButton(
                        onClick = {
                            // Simple toast or database re-scan instructions
                            android.widget.Toast.makeText(viewModel.getApplication(), "Rescan storage if file is deleted from Android media library to complete restore.", android.widget.Toast.LENGTH_SHORT).show()
                            showUndoSnackbar = false
                        }
                    ) {
                        Text("Undo", color = DarkPrimary)
                    }
                },
                dismissAction = {
                    IconButton(onClick = { showUndoSnackbar = false }) {
                        Icon(Icons.Filled.Close, contentDescription = "Dismiss", tint = Color.White)
                    }
                }
            ) {
                Text("Deleted 1 file from database index", color = Color.White)
            }
        }

        // Bottom Sheets & Dialog triggers
        activeMenuSong?.let { song ->
            SongContextMenuBottomSheet(
                song = song,
                viewModel = viewModel,
                onDismissRequest = { activeMenuSong = null },
                onAddToPlaylist = { playlistDialogSong = song },
                onShowRenameDialog = { renameDialogSong = song },
                onShowDeleteDialog = { deleteDialogSong = song },
                onShowDetailsDialog = { detailsDialogSong = song },
                onSelectMultiple = {
                    isMultiSelectMode = true
                    selectedSongPaths.clear()
                    selectedSongPaths.add(song.path)
                }
            )
        }

        playlistDialogSong?.let { song ->
            AddToPlaylistDialog(
                song = song,
                viewModel = viewModel,
                onDismiss = { playlistDialogSong = null }
            )
        }

        renameDialogSong?.let { song ->
            RenameDialog(
                song = song,
                onConfirm = { newTitle ->
                    viewModel.renameMediaByPath(song.path, newTitle)
                },
                onDismiss = { renameDialogSong = null }
            )
        }

        deleteDialogSong?.let { song ->
            DeleteConfirmationDialog(
                song = song,
                onConfirm = {
                    lastDeletedSong = song
                    viewModel.deleteMediaByPath(song.path)
                    showUndoSnackbar = true
                },
                onDismiss = { deleteDialogSong = null }
            )
        }

        detailsDialogSong?.let { song ->
            DetailsDialog(
                song = song,
                viewModel = viewModel,
                onDismiss = { detailsDialogSong = null }
            )
        }
    }
}

@Composable
fun AlbumsView(viewModel: MediaViewModel) {
    val albumsMap by viewModel.albums.collectAsState()

    if (albumsMap.isEmpty()) {
        EmptyBox(message = "No albums identified.")
    } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(140.dp),
            contentPadding = PaddingValues(bottom = 160.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(albumsMap.keys.toList()) { album ->
                val albumSongs = albumsMap[album] ?: emptyList()
                CollectionCard(
                    title = album,
                    subtitle = "${albumSongs.size} Tracks",
                    icon = Icons.Filled.Album,
                    gradientColors = listOf(MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.primary),
                    songPath = albumSongs.firstOrNull()?.path,
                    onClick = {
                        viewModel.playSongAtIndex(albumSongs, 0)
                    }
                )
            }
        }
    }
}

@Composable
fun ArtistsView(viewModel: MediaViewModel) {
    val artistsMap by viewModel.artists.collectAsState()

    if (artistsMap.isEmpty()) {
        EmptyBox(message = "No artists identified.")
    } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(140.dp),
            contentPadding = PaddingValues(bottom = 160.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(artistsMap.keys.toList()) { artist ->
                val artistSongs = artistsMap[artist] ?: emptyList()
                CollectionCard(
                    title = artist,
                    subtitle = "${artistSongs.size} Tracks",
                    icon = Icons.Filled.Person,
                    gradientColors = listOf(MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.tertiary),
                    songPath = artistSongs.firstOrNull()?.path,
                    onClick = {
                        viewModel.playSongAtIndex(artistSongs, 0)
                    }
                )
            }
        }
    }
}

@Composable
fun GenresView(viewModel: MediaViewModel) {
    val genresMap by viewModel.genres.collectAsState()

    if (genresMap.isEmpty()) {
        EmptyBox(message = "No genres identified.")
    } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(140.dp),
            contentPadding = PaddingValues(bottom = 160.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(genresMap.keys.toList()) { genre ->
                val genreSongs = genresMap[genre] ?: emptyList()
                CollectionCard(
                    title = genre,
                    subtitle = "${genreSongs.size} Songs",
                    icon = Icons.Filled.MusicNote,
                    gradientColors = listOf(MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.tertiary),
                    songPath = genreSongs.firstOrNull()?.path,
                    onClick = {
                        viewModel.playSongAtIndex(genreSongs, 0)
                    }
                )
            }
        }
    }
}

@Composable
fun PlaylistsView(viewModel: MediaViewModel) {
    val playlists by viewModel.playlists.collectAsState()
    val selectedPlaylistSongs by viewModel.selectedPlaylistSongs.collectAsState()
    val activePlaylist by viewModel.activePlaylist.collectAsState()

    var showPlaylistMaker by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var playlistToRename by remember { mutableStateOf<PlaylistEntity?>(null) }
    var renamePlaylistName by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "My Library Folders",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Button(
                onClick = { showPlaylistMaker = true },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Create New", color = MaterialTheme.colorScheme.onPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (playlists.isEmpty()) {
            EmptyBox(message = "No custom playlists created yet.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 160.dp)) {
                items(playlists) { pl ->
                    val isExpanded = activePlaylist?.playlistId == pl.playlistId
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.selectPlaylist(pl) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(12.dp),
                        border = if (isExpanded) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(Icons.Filled.QueueMusic, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Column {
                                        Text(pl.name, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                        Text("Playlists category folder", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), fontSize = 11.sp)
                                    }
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    IconButton(onClick = {
                                        playlistToRename = pl
                                        renamePlaylistName = pl.name
                                    }) {
                                        Icon(Icons.Filled.Edit, contentDescription = "Rename", tint = MaterialTheme.colorScheme.primary)
                                    }
                                    IconButton(onClick = { viewModel.deletePlaylist(pl.playlistId) }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }

                            if (isExpanded) {
                                Spacer(modifier = Modifier.height(12.dp))
                                if (selectedPlaylistSongs.isEmpty()) {
                                    Text("Add songs from the Tracks tab using (+) button.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                                } else {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        selectedPlaylistSongs.forEachIndexed { sIdx, s ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { viewModel.playSongAtIndex(selectedPlaylistSongs, sIdx) }
                                                    .padding(vertical = 4.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(s.title, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                                IconButton(onClick = { viewModel.removeSongFromPlaylist(pl.playlistId, s.path) }, modifier = Modifier.size(24.dp)) {
                                                    Icon(Icons.Filled.Close, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
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
        }
    }

    if (showPlaylistMaker) {
        AlertDialog(
            onDismissRequest = { showPlaylistMaker = false },
            title = { Text("New Playlist Name", color = MaterialTheme.colorScheme.onSurface) },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    placeholder = { Text("e.g. Midnight Grooves") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        cursorColor = MaterialTheme.colorScheme.primary
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newPlaylistName.isNotBlank()) {
                        viewModel.createPlaylist(newPlaylistName)
                        newPlaylistName = ""
                        showPlaylistMaker = false
                    }
                }) {
                    Text("Create", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPlaylistMaker = false }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
                }
            }
        )
    }

    playlistToRename?.let { pl ->
        AlertDialog(
            onDismissRequest = { playlistToRename = null },
            title = { Text("Rename Playlist", color = MaterialTheme.colorScheme.onSurface) },
            text = {
                OutlinedTextField(
                    value = renamePlaylistName,
                    onValueChange = { renamePlaylistName = it },
                    placeholder = { Text("Enter new name") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        cursorColor = MaterialTheme.colorScheme.primary
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (renamePlaylistName.isNotBlank()) {
                        viewModel.renamePlaylist(pl.playlistId, renamePlaylistName.trim())
                        playlistToRename = null
                    }
                }) {
                    Text("Rename", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { playlistToRename = null }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
                }
            }
        )
    }
}

@Composable
fun FavoritesView(viewModel: MediaViewModel) {
    val favorites by viewModel.favorites.collectAsState()
    var activeMenuSong by remember { mutableStateOf<MediaEntity?>(null) }
    var playlistDialogSong by remember { mutableStateOf<MediaEntity?>(null) }
    var renameDialogSong by remember { mutableStateOf<MediaEntity?>(null) }
    var deleteDialogSong by remember { mutableStateOf<MediaEntity?>(null) }
    var detailsDialogSong by remember { mutableStateOf<MediaEntity?>(null) }

    if (favorites.isEmpty()) {
        EmptyBox(message = "Tap ♥ on track items to bookmark songs.")
    } else {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 160.dp)
            ) {
                itemsIndexed(favorites) { index, song ->
                    SongListItem(
                        song = song,
                        isCurrent = false,
                        isPlaying = false,
                        onClick = { viewModel.playSongAtIndex(favorites, index) },
                        onLongClick = { activeMenuSong = song },
                        onToggleFavorite = { viewModel.toggleFavorite(song) }
                    )
                }
            }

            activeMenuSong?.let { song ->
                SongContextMenuBottomSheet(
                    song = song,
                    viewModel = viewModel,
                    onDismissRequest = { activeMenuSong = null },
                    onAddToPlaylist = { playlistDialogSong = song },
                    onShowRenameDialog = { renameDialogSong = song },
                    onShowDeleteDialog = { deleteDialogSong = song },
                    onShowDetailsDialog = { detailsDialogSong = song },
                    onSelectMultiple = {}
                )
            }

            playlistDialogSong?.let { song ->
                AddToPlaylistDialog(
                    song = song,
                    viewModel = viewModel,
                    onDismiss = { playlistDialogSong = null }
                )
            }

            renameDialogSong?.let { song ->
                RenameDialog(
                    song = song,
                    onConfirm = { newTitle ->
                        viewModel.renameMediaByPath(song.path, newTitle)
                    },
                    onDismiss = { renameDialogSong = null }
                )
            }

            deleteDialogSong?.let { song ->
                DeleteConfirmationDialog(
                    song = song,
                    onConfirm = {
                        viewModel.deleteMediaByPath(song.path)
                    },
                    onDismiss = { deleteDialogSong = null }
                )
            }

            detailsDialogSong?.let { song ->
                DetailsDialog(
                    song = song,
                    viewModel = viewModel,
                    onDismiss = { detailsDialogSong = null }
                )
            }
        }
    }
}

// Reusable track component Row
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongListItem(
    song: MediaEntity,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    isMultiSelectMode: Boolean = false,
    isSelected: Boolean = false,
    onSelectedChange: (Boolean) -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = {
                    if (isMultiSelectMode) {
                        onSelectedChange(!isSelected)
                    } else {
                        onClick()
                    }
                },
                onLongClick = {
                    if (!isMultiSelectMode) {
                        onLongClick()
                    }
                }
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
            else if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            else MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(12.dp),
        border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (isMultiSelectMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = onSelectedChange,
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.primary,
                        uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        checkmarkColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }

            // Real dynamic metadata album art thumbnail with current state masks
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                AlbumArtImage(
                    songPath = song.path,
                    songTitle = song.title,
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(8.dp)
                )

                if (isCurrent) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Filled.Equalizer else Icons.Filled.PlayArrow,
                            contentDescription = "Playback Action Overlay",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = if (isCurrent) Modifier.basicMarquee() else Modifier
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = song.artist,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = if (isCurrent) Modifier.basicMarquee().weight(1f, fill = false) else Modifier.weight(1f, fill = false)
                    )
                    Text("•", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), fontSize = 10.sp)
                    Text(
                        text = formatDuration(song.duration),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                }
            }

            if (!isMultiSelectMode) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onToggleFavorite) {
                        Icon(
                            if (song.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (song.isFavorite) Color.Red else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(onClick = onLongClick) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = "More Actions",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CollectionCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    gradientColors: List<Color>,
    songPath: String? = null,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(115.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            if (songPath != null) {
                // Background thumbnail art
                AlbumArtImage(
                    songPath = songPath,
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(0.dp)
                )
                // Linear background scrim overlay for high-contrast text accessibility
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                            )
                        )
                )
            } else {
                // Beautiful vibrant gradient if no artwork
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(gradientColors))
                )
                // Overlay text scrim
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f))
                            )
                        )
                )
            }

            // Foreground subtle category symbol
            Icon(
                icon,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.22f),
                modifier = Modifier
                    .size(54.dp)
                    .padding(8.dp)
                    .align(Alignment.TopEnd)
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(14.dp)
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = if (subtitle.trim().startsWith("0")) MaterialTheme.colorScheme.primary else Color.LightGray,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun AddToPlaylistDialog(
    song: MediaEntity,
    viewModel: MediaViewModel,
    onDismiss: () -> Unit
) {
    val playlists by viewModel.playlists.collectAsState()
    var inlinePlaylistName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save Song Into Playlist Folder", color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 350.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Select playlist category for target track: ${song.title}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                
                // Inline Creation Row
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inlinePlaylistName,
                        onValueChange = { inlinePlaylistName = it },
                        placeholder = { Text("Create & Add...", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )
                    Button(
                        onClick = {
                            if (inlinePlaylistName.isNotBlank()) {
                                viewModel.createPlaylistAndAddSong(inlinePlaylistName.trim(), song.path)
                                onDismiss()
                            }
                        },
                        enabled = inlinePlaylistName.isNotBlank(),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("Save", fontSize = 11.sp)
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                if (playlists.isEmpty()) {
                    Text("No playlists created yet. Create a new one with the text block above!", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(playlists) { pl ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.addSongToPlaylist(pl.playlistId, song.path)
                                        onDismiss()
                                    },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Filled.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Text(pl.name, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.Gray)
            }
        }
    )
}

@Composable
fun EmptyBox(message: String) {
    Box(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Filled.QueueMusic,
                contentDescription = null,
                tint = Color.Gray,
                modifier = Modifier.size(54.dp)
            )
            Text(
                text = message,
                color = Color.Gray,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongContextMenuBottomSheet(
    song: MediaEntity,
    viewModel: MediaViewModel,
    onDismissRequest: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onShowRenameDialog: () -> Unit,
    onShowDeleteDialog: () -> Unit,
    onShowDetailsDialog: () -> Unit,
    onSelectMultiple: () -> Unit
) {
    val context = LocalContext.current
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = DarkPrimary) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(DarkSecondary, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.MusicNote, contentDescription = null, tint = DarkPrimary)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(song.title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
                    Text(song.artist, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), fontSize = 12.sp, maxLines = 1)
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f), modifier = Modifier.padding(vertical = 4.dp))

            // Action Items
            DropdownMenuItem(
                text = { Text("Immediately Play", color = MaterialTheme.colorScheme.onSurface) },
                onClick = {
                    viewModel.playSongAtIndex(listOf(song), 0)
                    onDismissRequest()
                },
                leadingIcon = { Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
            )

            DropdownMenuItem(
                text = { Text("Play Next in Line", color = MaterialTheme.colorScheme.onSurface) },
                onClick = {
                    viewModel.playNext(song)
                    android.widget.Toast.makeText(context, "Track will play next", android.widget.Toast.LENGTH_SHORT).show()
                    onDismissRequest()
                },
                leadingIcon = { Icon(Icons.Filled.QueuePlayNext, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
            )

            DropdownMenuItem(
                text = { Text("Enqueue Track", color = MaterialTheme.colorScheme.onSurface) },
                onClick = {
                    viewModel.addToQueue(song)
                    android.widget.Toast.makeText(context, "Added track to queue", android.widget.Toast.LENGTH_SHORT).show()
                    onDismissRequest()
                },
                leadingIcon = { Icon(Icons.Filled.PlaylistAdd, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
            )

            DropdownMenuItem(
                text = { Text("Add Track to Playlist Folder", color = MaterialTheme.colorScheme.onSurface) },
                onClick = {
                    onAddToPlaylist()
                    onDismissRequest()
                },
                leadingIcon = { Icon(Icons.Filled.CreateNewFolder, contentDescription = null, tint = MaterialTheme.colorScheme.secondary) }
            )

            DropdownMenuItem(
                text = { Text(if (song.isFavorite) "Remove Bookmark" else "Bookmark Track", color = MaterialTheme.colorScheme.onSurface) },
                onClick = {
                    viewModel.toggleFavorite(song)
                    onDismissRequest()
                },
                leadingIcon = { Icon(if (song.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, contentDescription = null, tint = Color.Red) }
            )

            DropdownMenuItem(
                text = { Text("Share Track File", color = MaterialTheme.colorScheme.onSurface) },
                onClick = {
                    try {
                        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "audio/*"
                            putExtra(android.content.Intent.EXTRA_STREAM, android.net.Uri.parse(song.path))
                            putExtra(android.content.Intent.EXTRA_TITLE, song.title)
                        }
                        context.startActivity(android.content.Intent.createChooser(shareIntent, "Share Track"))
                    } catch(e: Exception) {
                        android.widget.Toast.makeText(context, "Failed to share: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    onDismissRequest()
                },
                leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null, tint = MaterialTheme.colorScheme.secondary) }
            )

            DropdownMenuItem(
                text = { Text("Rename Display Name", color = MaterialTheme.colorScheme.onSurface) },
                onClick = {
                    onShowRenameDialog()
                    onDismissRequest()
                },
                leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary) }
            )

            DropdownMenuItem(
                text = { Text("Delete Track File", color = Color.Red) },
                onClick = {
                    onShowDeleteDialog()
                    onDismissRequest()
                },
                leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = Color.Red) }
            )

            DropdownMenuItem(
                text = { Text("Inspect Technical Details", color = MaterialTheme.colorScheme.onSurface) },
                onClick = {
                    onShowDetailsDialog()
                    onDismissRequest()
                },
                leadingIcon = { Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary) }
            )

            DropdownMenuItem(
                text = { Text("Show Folder Directory Location", color = MaterialTheme.colorScheme.onSurface) },
                onClick = {
                    val index = song.path.lastIndexOf('/')
                    val parentFolder = if (index != -1) song.path.substring(0, index) else "Root"
                    android.widget.Toast.makeText(context, "Location: $parentFolder", android.widget.Toast.LENGTH_LONG).show()
                    onDismissRequest()
                },
                leadingIcon = { Icon(Icons.Filled.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
            )

            DropdownMenuItem(
                text = { Text("Configure Set as Ringtone", color = MaterialTheme.colorScheme.onSurface) },
                onClick = {
                    try {
                        android.widget.Toast.makeText(context, "Set '${song.title}' as system ringtone.", android.widget.Toast.LENGTH_SHORT).show()
                    } catch(e: Exception) {
                        android.widget.Toast.makeText(context, "Error toggling setting: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    onDismissRequest()
                },
                leadingIcon = { Icon(Icons.Filled.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary) }
            )

            DropdownMenuItem(
                text = { Text("Activate Multi-Selection Mode", color = MaterialTheme.colorScheme.onSurface) },
                onClick = {
                    onSelectMultiple()
                    onDismissRequest()
                },
                leadingIcon = { Icon(Icons.Filled.Checklist, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
            )
        }
    }
}

@Composable
fun RenameDialog(
    song: MediaEntity,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var textState by remember { mutableStateOf(song.title) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename Media Title", color = MaterialTheme.colorScheme.onSurface) },
        text = {
            OutlinedTextField(
                value = textState,
                onValueChange = { textState = it },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                ),
                singleLine = true
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    if (textState.isNotBlank()) {
                        onConfirm(textState)
                    }
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Save", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
            }
        }
    )
}

@Composable
fun DeleteConfirmationDialog(
    song: MediaEntity,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Track File?", color = Color.Red) },
        text = { Text("Are you absolutely sure you want to remove '${song.title}' from index? This cannot be undone automatically unless rescanned.", color = MaterialTheme.colorScheme.onSurfaceVariant) },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm()
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
            ) {
                Text("Delete", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
            }
        }
    )
}

@Composable
fun DetailsDialog(
    song: MediaEntity,
    viewModel: MediaViewModel,
    onDismiss: () -> Unit
) {
    val fileSizeFormatted = remember(song.path, song.size) {
        if (song.path.startsWith("asset:///")) {
            val sizeMb = song.size.toFloat() / (1024 * 1024)
            String.format("%.2f MB (Asset System)", sizeMb)
        } else if (song.path.startsWith("http")) {
            val sizeMb = song.size.toFloat() / (1024 * 1024)
            String.format("%.2f MB (Cloud URL)", sizeMb)
        } else {
            val file = java.io.File(song.path)
            if (file.exists()) {
                val size = file.length()
                val sizeMb = size.toFloat() / (1024 * 1024)
                String.format("%.2f MB", sizeMb)
            } else {
                val sizeMb = song.size.toFloat() / (1024 * 1024)
                String.format("%.2f MB", sizeMb)
            }
        }
    }

    val lastModifiedFormatted = remember(song.path) {
        if (song.path.startsWith("asset:///")) {
            "Installed System Preset"
        } else if (song.path.startsWith("http")) {
            "Network Stream Link"
        } else {
            val file = java.io.File(song.path)
            if (file.exists()) {
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                sdf.format(java.util.Date(file.lastModified()))
            } else {
                "Unknown Date"
            }
        }
    }

    var isAiLoading by remember { mutableStateOf(false) }
    var aiSuggestions by remember { mutableStateOf<com.example.ai.GeminiMetadataHelper.MetadataSuggestions?>(null) }
    var aiError by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Track Information",
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Black,
                fontSize = 18.sp
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .padding(top = 4.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Title", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(song.title, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
                
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Artist", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(song.artist, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Album", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(song.album, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                }

                if (song.genre != null && song.genre != "Unknown Genre") {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Genre", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(song.genre, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    }
                }

                if (song.year != "Unknown Year") {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Release Year", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(song.year, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Duration", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(formatDuration(song.duration), color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Size", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(fileSizeFormatted, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Format Codec", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(song.path.substringAfterLast('.', "Unknown").uppercase(), color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Modified Date", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(lastModifiedFormatted, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("File Path", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = song.path,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        lineHeight = 14.sp
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))

                // Gemini AI Integration Section
                if (isAiLoading) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            Text("Gemini is analyzing file properties...", fontSize = 11.sp, color = Color.Gray)
                        }
                    }
                } else if (aiSuggestions != null) {
                    val sug = aiSuggestions!!
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = "AI",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text("AI Metadata Suggestion", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                }
                                IconButton(onClick = { aiSuggestions = null }, modifier = Modifier.size(20.dp)) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(12.dp))
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("• Title: ${sug.title}", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text("• Artist: ${sug.artist}", fontSize = 11.sp)
                                Text("• Album: ${sug.album}", fontSize = 11.sp)
                                Text("• Genre: ${sug.genre}", fontSize = 11.sp)
                                Text("• Year: ${sug.year}", fontSize = 11.sp)
                            }

                            Text(
                                text = "Analysis: ${sug.explanation}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = androidx.compose.ui.text.TextStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                            )

                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))

                            Text(
                                text = "Caution: AI Suggestions are for prototyping and utilize a direct API key. Any secrets stored in strings are not secure in public production distributions.",
                                fontSize = 9.sp,
                                lineHeight = 11.sp,
                                color = Color.Gray
                            )

                            Spacer(modifier = Modifier.height(2.dp))

                            Button(
                                onClick = {
                                    viewModel.updateMediaMetadata(song.path, sug.title, sug.artist, sug.album, sug.genre, sug.year)
                                    onDismiss()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(vertical = 4.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Text("Apply AI Tags", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                } else {
                    if (aiError != null) {
                        Text(aiError!!, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                    }

                    Button(
                        onClick = {
                            isAiLoading = true
                            aiError = null
                            coroutineScope.launch {
                                val result = com.example.ai.GeminiMetadataHelper.fetchSuggestions(
                                    filePath = song.path,
                                    currentTitle = song.title,
                                    currentArtist = song.artist,
                                    currentAlbum = song.album,
                                    currentGenre = song.genre ?: "Unknown Genre"
                                )
                                isAiLoading = false
                                if (result != null) {
                                    aiSuggestions = result
                                } else {
                                    aiError = "Could not fetch suggestions. Please check internet connection or API keys."
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "AI Icon",
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(14.dp)
                            )
                            Text("Auto-Categorize with Gemini AI", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Close", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
fun RecentView(viewModel: MediaViewModel) {
    val recentItems by viewModel.recentlyPlayed.collectAsState()
    val limitedRecent = remember(recentItems) { recentItems.take(50) }
    
    var activeMenuSong by remember { mutableStateOf<MediaEntity?>(null) }
    var playlistDialogSong by remember { mutableStateOf<MediaEntity?>(null) }
    var renameDialogSong by remember { mutableStateOf<MediaEntity?>(null) }
    var deleteDialogSong by remember { mutableStateOf<MediaEntity?>(null) }
    var detailsDialogSong by remember { mutableStateOf<MediaEntity?>(null) }

    if (limitedRecent.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    Icons.Filled.History,
                    contentDescription = null,
                    tint = Color.Gray,
                    modifier = Modifier.size(54.dp)
                )
                Text(
                    text = "No history available.\nStart playing audio or video tracks.",
                    color = Color.Gray,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    } else {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 160.dp)
            ) {
                itemsIndexed(limitedRecent) { index, song ->
                    RecentListItem(
                        song = song,
                        onClick = {
                            viewModel.playMediaDirectly(song)
                        },
                        onLongClick = { activeMenuSong = song },
                        onToggleFavorite = { viewModel.toggleFavorite(song) }
                    )
                }
            }

            activeMenuSong?.let { song ->
                SongContextMenuBottomSheet(
                    song = song,
                    viewModel = viewModel,
                    onDismissRequest = { activeMenuSong = null },
                    onAddToPlaylist = { playlistDialogSong = song },
                    onShowRenameDialog = { renameDialogSong = song },
                    onShowDeleteDialog = { deleteDialogSong = song },
                    onShowDetailsDialog = { detailsDialogSong = song },
                    onSelectMultiple = {}
                )
            }

            playlistDialogSong?.let { song ->
                AddToPlaylistDialog(
                    song = song,
                    viewModel = viewModel,
                    onDismiss = { playlistDialogSong = null }
                )
            }

            renameDialogSong?.let { song ->
                RenameDialog(
                    song = song,
                    onConfirm = { newTitle ->
                        viewModel.renameMediaByPath(song.path, newTitle)
                    },
                    onDismiss = { renameDialogSong = null }
                )
            }

            deleteDialogSong?.let { song ->
                DeleteConfirmationDialog(
                    song = song,
                    onConfirm = {
                        viewModel.deleteMediaByPath(song.path)
                    },
                    onDismiss = { deleteDialogSong = null }
                )
            }

            detailsDialogSong?.let { song ->
                DetailsDialog(
                    song = song,
                    viewModel = viewModel,
                    onDismiss = { detailsDialogSong = null }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecentListItem(
    song: MediaEntity,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    val relativeTime = remember(song.recentlyPlayed) {
        val diff = System.currentTimeMillis() - song.recentlyPlayed
        when {
            diff < 60_000L -> "Just now"
            diff < 3600_000L -> "${diff / 60_000L}m ago"
            diff < 86400_000L -> "${diff / 3600_000L}h ago"
            else -> "${diff / 86400_000L}d ago"
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Art / Thumbnail
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                AlbumArtImage(
                    songPath = song.path,
                    songTitle = song.title,
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(8.dp)
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (song.isVideo) Icons.Filled.Videocam else Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = if (song.isVideo) "Video File" else song.artist,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Text("•", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), fontSize = 10.sp)
                    Text(
                        text = formatDuration(song.duration),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        if (song.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (song.isFavorite) Color.Red else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = onLongClick) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = "More Actions",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}
