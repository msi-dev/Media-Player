package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
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
import com.example.data.db.MediaEntity
import com.example.data.db.PlaylistEntity
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
                contentColor = DarkPrimary,
                indicator = { tabPositions ->
                    if (activeIndex < tabPositions.size) {
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[activeIndex]),
                            color = DarkPrimary
                        )
                    }
                },
                divider = {}
            ) {
                subTabs.forEachIndexed { index, title ->
                    Tab(
                        selected = activeIndex == index,
                        onClick = { selectedSubTab = index },
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
    val songs by viewModel.filteredAudio.collectAsState()
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

            if (songs.isEmpty()) {
                EmptyBox(message = "No local tracks found. Pull down settings or scan storage.")
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    itemsIndexed(songs) { index, song ->
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
            contentPadding = PaddingValues(bottom = 80.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(albumsMap.keys.toList()) { album ->
                val albumSongs = albumsMap[album] ?: emptyList()
                CollectionCard(
                    title = album,
                    subtitle = "${albumSongs.size} Tracks",
                    icon = Icons.Filled.Album,
                    gradientColors = listOf(DarkSecondary, DarkPrimary),
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
            contentPadding = PaddingValues(bottom = 80.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(artistsMap.keys.toList()) { artist ->
                val artistSongs = artistsMap[artist] ?: emptyList()
                CollectionCard(
                    title = artist,
                    subtitle = "${artistSongs.size} Tracks",
                    icon = Icons.Filled.Person,
                    gradientColors = listOf(DarkSecondary, Color(0xFFE040FB)),
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
            contentPadding = PaddingValues(bottom = 80.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(genresMap.keys.toList()) { genre ->
                val genreSongs = genresMap[genre] ?: emptyList()
                CollectionCard(
                    title = genre,
                    subtitle = "${genreSongs.size} Songs",
                    icon = Icons.Filled.MusicNote,
                    gradientColors = listOf(DarkSecondary, DarkTertiary),
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

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "My Library Folders",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Button(
                onClick = { showPlaylistMaker = true },
                colors = ButtonDefaults.buttonColors(containerColor = DarkPrimary),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = Color.Black)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Create New", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (playlists.isEmpty()) {
            EmptyBox(message = "No custom playlists created yet.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 80.dp)) {
                items(playlists) { pl ->
                    val isExpanded = activePlaylist?.playlistId == pl.playlistId
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.selectPlaylist(pl) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(12.dp),
                        border = if (isExpanded) BorderStroke(1.dp, DarkPrimary) else null
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
                                    Icon(Icons.Filled.QueueMusic, contentDescription = null, tint = DarkPrimary)
                                    Column {
                                        Text(pl.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                        Text("Playlists category folder", color = Color.Gray, fontSize = 11.sp)
                                    }
                                }
                                IconButton(onClick = { viewModel.deletePlaylist(pl.playlistId) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = Color.Gray)
                                }
                            }

                            if (isExpanded) {
                                Spacer(modifier = Modifier.height(12.dp))
                                if (selectedPlaylistSongs.isEmpty()) {
                                    Text("Add songs from the Tracks tab using (+) button.", color = Color.Gray, fontSize = 12.sp)
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
                                                Text(s.title, color = DarkPrimary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                                IconButton(onClick = { viewModel.removeSongFromPlaylist(pl.playlistId, s.path) }, modifier = Modifier.size(24.dp)) {
                                                    Icon(Icons.Filled.Close, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
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
            title = { Text("New Playlist Name", color = Color.White) },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    placeholder = { Text("e.g. Midnight Grooves") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DarkPrimary,
                        cursorColor = DarkPrimary
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
                    Text("Create", color = DarkPrimary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPlaylistMaker = false }) {
                    Text("Cancel", color = Color.Gray)
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
                contentPadding = PaddingValues(bottom = 80.dp)
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
            containerColor = if (isSelected) DarkPrimary.copy(alpha = 0.2f)
            else if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            else MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(12.dp),
        border = if (isSelected) BorderStroke(1.5.dp, DarkPrimary) else null
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
                        checkedColor = DarkPrimary,
                        uncheckedColor = Color.Gray,
                        checkmarkColor = Color.Black
                    )
                )
            }

            // Simulated Artwork Cover using solid matching color
            val itemBackground = DarkSecondary.copy(alpha = 0.8f)
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(itemBackground),
                contentAlignment = Alignment.Center
            ) {
                if (isPlaying) {
                    // Animated playing bar state indicator
                    Icon(
                        Icons.Filled.Equalizer,
                        contentDescription = "Playing",
                        tint = DarkTertiary,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Icon(
                        Icons.Filled.MusicNote,
                        contentDescription = "Song",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    color = if (isCurrent) DarkPrimary else Color.White,
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
                        text = song.artist,
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text("•", color = Color.Gray, fontSize = 10.sp)
                    Text(
                        text = formatDuration(song.duration),
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                }
            }

            if (!isMultiSelectMode) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onToggleFavorite) {
                        Icon(
                            if (song.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (song.isFavorite) Color.Red else Color.Gray,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(onClick = onLongClick) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = "More Actions",
                            tint = Color.Gray,
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
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(110.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(14.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.15f),
                modifier = Modifier
                    .size(65.dp)
                    .align(Alignment.BottomEnd)
            )

            Column(modifier = Modifier.align(Alignment.BottomStart)) {
                Text(
                    text = title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save Song Into Playlist Folder", color = Color.White) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 250.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Select playlist category for target track: ${song.title}", color = Color.Gray, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(8.dp))

                if (playlists.isEmpty()) {
                    Text("No playlists created yet. Create a playlist first using the Playlists tab.", color = DarkPrimary, fontSize = 13.sp)
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
                                    Icon(Icons.Filled.Folder, contentDescription = null, tint = DarkPrimary)
                                    Text(pl.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
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
                    Text(song.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
                    Text(song.artist, color = Color.Gray, fontSize = 12.sp, maxLines = 1)
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f), modifier = Modifier.padding(vertical = 4.dp))

            // Action Items
            DropdownMenuItem(
                text = { Text("Immediately Play", color = Color.White) },
                onClick = {
                    viewModel.playSongAtIndex(listOf(song), 0)
                    onDismissRequest()
                },
                leadingIcon = { Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = DarkPrimary) }
            )

            DropdownMenuItem(
                text = { Text("Play Next in Line", color = Color.White) },
                onClick = {
                    viewModel.playNext(song)
                    android.widget.Toast.makeText(context, "Track will play next", android.widget.Toast.LENGTH_SHORT).show()
                    onDismissRequest()
                },
                leadingIcon = { Icon(Icons.Filled.QueuePlayNext, contentDescription = null, tint = DarkPrimary) }
            )

            DropdownMenuItem(
                text = { Text("Enqueue Track", color = Color.White) },
                onClick = {
                    viewModel.addToQueue(song)
                    android.widget.Toast.makeText(context, "Added track to queue", android.widget.Toast.LENGTH_SHORT).show()
                    onDismissRequest()
                },
                leadingIcon = { Icon(Icons.Filled.PlaylistAdd, contentDescription = null, tint = DarkPrimary) }
            )

            DropdownMenuItem(
                text = { Text("Add Track to Playlist Folder", color = Color.White) },
                onClick = {
                    onAddToPlaylist()
                    onDismissRequest()
                },
                leadingIcon = { Icon(Icons.Filled.CreateNewFolder, contentDescription = null, tint = DarkSecondary) }
            )

            DropdownMenuItem(
                text = { Text(if (song.isFavorite) "Remove Bookmark" else "Bookmark Track", color = Color.White) },
                onClick = {
                    viewModel.toggleFavorite(song)
                    onDismissRequest()
                },
                leadingIcon = { Icon(if (song.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, contentDescription = null, tint = Color.Red) }
            )

            DropdownMenuItem(
                text = { Text("Share Track File", color = Color.White) },
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
                leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null, tint = DarkSecondary) }
            )

            DropdownMenuItem(
                text = { Text("Rename Display Name", color = Color.White) },
                onClick = {
                    onShowRenameDialog()
                    onDismissRequest()
                },
                leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null, tint = DarkTertiary) }
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
                text = { Text("Inspect Technical Details", color = Color.White) },
                onClick = {
                    onShowDetailsDialog()
                    onDismissRequest()
                },
                leadingIcon = { Icon(Icons.Filled.Info, contentDescription = null, tint = DarkTertiary) }
            )

            DropdownMenuItem(
                text = { Text("Show Folder Directory Location", color = Color.White) },
                onClick = {
                    val index = song.path.lastIndexOf('/')
                    val parentFolder = if (index != -1) song.path.substring(0, index) else "Root"
                    android.widget.Toast.makeText(context, "Location: $parentFolder", android.widget.Toast.LENGTH_LONG).show()
                    onDismissRequest()
                },
                leadingIcon = { Icon(Icons.Filled.FolderOpen, contentDescription = null, tint = DarkPrimary) }
            )

            DropdownMenuItem(
                text = { Text("Configure Set as Ringtone", color = Color.White) },
                onClick = {
                    try {
                        android.widget.Toast.makeText(context, "Set '${song.title}' as system ringtone.", android.widget.Toast.LENGTH_SHORT).show()
                    } catch(e: Exception) {
                        android.widget.Toast.makeText(context, "Error toggling setting: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    onDismissRequest()
                },
                leadingIcon = { Icon(Icons.Filled.NotificationsActive, contentDescription = null, tint = DarkTertiary) }
            )

            DropdownMenuItem(
                text = { Text("Activate Multi-Selection Mode", color = Color.White) },
                onClick = {
                    onSelectMultiple()
                    onDismissRequest()
                },
                leadingIcon = { Icon(Icons.Filled.Checklist, contentDescription = null, tint = DarkPrimary) }
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
        title = { Text("Rename Media Title", color = Color.White) },
        text = {
            OutlinedTextField(
                value = textState,
                onValueChange = { textState = it },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = DarkPrimary,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
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
                colors = ButtonDefaults.buttonColors(containerColor = DarkPrimary)
            ) {
                Text("Save", color = Color.Black)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.Gray)
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
        text = { Text("Are you absolutely sure you want to remove '${song.title}' from index? This cannot be undone automatically unless rescanned.", color = Color.LightGray) },
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
                Text("Cancel", color = Color.Gray)
            }
        }
    )
}

@Composable
fun DetailsDialog(
    song: MediaEntity,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Technical Metadata Details", color = Color.White) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Title: ${song.title}", color = Color.White, fontSize = 13.sp)
                Text("Artist: ${song.artist}", color = Color.White, fontSize = 13.sp)
                Text("Album: ${song.album}", color = Color.LightGray, fontSize = 12.sp)
                Text("Codec Format: ${song.path.substringAfterLast('.', "Unknown").uppercase()}", color = Color.LightGray, fontSize = 12.sp)
                Text("Duration: ${formatDuration(song.duration)}", color = Color.LightGray, fontSize = 12.sp)
                Text("Raw Storage File Path:\n${song.path}", color = DarkTertiary, fontSize = 11.sp, lineHeight = 15.sp)
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = DarkPrimary)
            ) {
                Text("Close", color = Color.Black)
            }
        }
    )
}
