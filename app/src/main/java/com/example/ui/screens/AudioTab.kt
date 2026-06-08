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
    var selectedSubTab by remember { mutableIntStateOf(0) }
    val subTabs = listOf("Tracks", "Albums", "Artists", "Genres", "Playlists", "Favorites")

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 8.dp)) {
        // Aesthetic Scrollable Sub-Tabs
        ScrollableTabRow(
            selectedTabIndex = selectedSubTab,
            edgePadding = 8.dp,
            containerColor = Color.Transparent,
            contentColor = DarkPrimary,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedSubTab]),
                    color = DarkPrimary
                )
            },
            divider = {}
        ) {
            subTabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedSubTab == index,
                    onClick = { selectedSubTab = index },
                    text = {
                        Text(
                            text = title,
                            fontWeight = if (selectedSubTab == index) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 14.sp
                        )
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Multi-view animation deck
        AnimatedContent(
            targetState = selectedSubTab,
            transitionSpec = {
                fadeIn() togetherWith fadeOut()
            },
            label = "SubTabTransition"
        ) { targetTab ->
            when (targetTab) {
                0 -> TracksView(viewModel)
                1 -> AlbumsView(viewModel)
                2 -> ArtistsView(viewModel)
                3 -> GenresView(viewModel)
                4 -> PlaylistsView(viewModel)
                5 -> FavoritesView(viewModel)
            }
        }
    }
}

@Composable
fun TracksView(viewModel: MediaViewModel) {
    val songs by viewModel.filteredAudio.collectAsState()
    val activeSong by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()

    var playlistDialogSong by remember { mutableStateOf<MediaEntity?>(null) }

    if (songs.isEmpty()) {
        EmptyBox(message = "No local tracks found. Pull down settings or scan storage.")
    } else {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                itemsIndexed(songs) { index, song ->
                    val isCurrent = song.path == activeSong?.path
                    SongListItem(
                        song = song,
                        isCurrent = isCurrent,
                        isPlaying = isCurrent && isPlaying,
                        onClick = { viewModel.playSongAtIndex(songs, index) },
                        onAddToPlaylist = { playlistDialogSong = song },
                        onToggleFavorite = { viewModel.toggleFavorite(song) }
                    )
                }
            }

            // Playlist addition dialog
            playlistDialogSong?.let { song ->
                AddToPlaylistDialog(
                    song = song,
                    viewModel = viewModel,
                    onDismiss = { playlistDialogSong = null }
                )
            }
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

    if (favorites.isEmpty()) {
        EmptyBox(message = "Tap ♥ on track items to bookmark songs.")
    } else {
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
                    onAddToPlaylist = {},
                    onToggleFavorite = { viewModel.toggleFavorite(song) }
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
    onAddToPlaylist: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onAddToPlaylist
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            else MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Simulated Artwork Cover using matching colors
            val itemBackground = Brush.linearGradient(
                colors = listOf(DarkSecondary.copy(alpha = 0.8f), DarkPrimary.copy(alpha = 0.8f))
            )
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

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        if (song.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (song.isFavorite) Color.Red else Color.Gray,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = onAddToPlaylist) {
                    Icon(
                        Icons.Filled.PlaylistAdd,
                        contentDescription = "Add to folder playlist",
                        tint = Color.Gray,
                        modifier = Modifier.size(22.dp)
                    )
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
                .background(Brush.radialGradient(gradientColors))
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
