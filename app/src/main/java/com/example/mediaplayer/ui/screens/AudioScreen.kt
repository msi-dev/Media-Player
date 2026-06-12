package com.example.mediaplayer.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
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
import androidx.media3.common.Player
import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import androidx.compose.ui.viewinterop.AndroidView
import androidx.activity.compose.BackHandler
import com.example.mediaplayer.MediaViewModel
import com.example.mediaplayer.data.SongEntity
import com.example.mediaplayer.ui.components.AsymmetricVisualizer

@Composable
fun AudioScreen(
    viewModel: MediaViewModel,
    modifier: Modifier = Modifier,
    onMenuClick: () -> Unit = {}
) {
    val allSongs by viewModel.allSongs.collectAsState()
    val audioSongs = remember(allSongs) { allSongs.filter { it.mediaType == "AUDIO" } }

    val currentSong by viewModel.currentPlayingSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    val filteredSongs = remember(audioSongs, searchQuery) {
        if (searchQuery.isBlank()) audioSongs
        else audioSongs.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
            it.artist.contains(searchQuery, ignoreCase = true)
        }
    }

    var showPlaylistSelectionDialog by remember { mutableStateOf<SongEntity?>(null) }
    var useWebAudioApi by remember { mutableStateOf(false) }
    var activeWebAudioSong by remember { mutableStateOf<SongEntity?>(null) }

    val tabs = listOf("Song", "Album", "Folder", "Favorite", "Playlist")
    var selectedTab by remember { mutableStateOf("Song") }

    Box(modifier = modifier.fillMaxSize()) {
        if (activeWebAudioSong != null) {
            Html5AudioPlayer(
                song = activeWebAudioSong!!,
                onBack = { activeWebAudioSong = null }
            )
        } else {
            Column(
                modifier = Modifier
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
                        placeholder = { Text("Search songs, artists...") },
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

                // 5 Tab Bar
                ScrollableTabRow(
                    selectedTabIndex = tabs.indexOf(selectedTab),
                    containerColor = Color.Transparent,
                    edgePadding = 0.dp,
                    divider = {},
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.tabIndicatorOffset(tabPositions[tabs.indexOf(selectedTab)])
                        )
                    },
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    tabs.forEach { tab ->
                        Tab(
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            text = { Text(text = tab, fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
                        )
                    }
                }

                if (filteredSongs.isEmpty() && selectedTab != "Playlist") {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No compatible songs found",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    when (selectedTab) {
                        "Song" -> {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(bottom = 80.dp)
                            ) {
                                items(filteredSongs) { song ->
                                    SongRowItem(
                                        song = song,
                                        currentSong = currentSong,
                                        isPlaying = isPlaying,
                                        onSongClick = {
                                            if (useWebAudioApi) {
                                                activeWebAudioSong = song
                                            } else {
                                                viewModel.playSong(song, filteredSongs)
                                            }
                                        },
                                        onFavoriteClick = { viewModel.toggleFavorite(song.id, !song.isFavorite) },
                                        onPlaylistAddClick = { showPlaylistSelectionDialog = song }
                                    )
                                }
                            }
                        }

                        "Album" -> {
                            val albums = remember(filteredSongs) { filteredSongs.groupBy { it.album } }
                            var expandedAlbum by remember { mutableStateOf<String?>(null) }

                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(bottom = 80.dp)
                            ) {
                                items(albums.keys.toList()) { albumName ->
                                    val albumSongs = albums[albumName] ?: emptyList()
                                    val isExpanded = expandedAlbum == albumName
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { expandedAlbum = if (isExpanded) null else albumName },
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
                                                        imageVector = Icons.Default.Album,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(40.dp)
                                                    )
                                                    Column {
                                                        Text(text = albumName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                        Text(text = "${albumSongs.size} tracks", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                                albumSongs.forEach { song ->
                                                    SongRowItem(
                                                        song = song,
                                                        currentSong = currentSong,
                                                        isPlaying = isPlaying,
                                                        onSongClick = {
                                                            if (useWebAudioApi) {
                                                                activeWebAudioSong = song
                                                            } else {
                                                                viewModel.playSong(song, albumSongs)
                                                            }
                                                        },
                                                        onFavoriteClick = { viewModel.toggleFavorite(song.id, !song.isFavorite) },
                                                        onPlaylistAddClick = { showPlaylistSelectionDialog = song }
                                                    )
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        "Folder" -> {
                            val folders = remember(filteredSongs) {
                                filteredSongs.groupBy { song ->
                                    if (song.id.startsWith("http")) {
                                        "Online Streams"
                                    } else {
                                        val parts = song.id.split("/")
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
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(bottom = 80.dp)
                            ) {
                                items(folders.keys.toList()) { folderName ->
                                    val folderSongs = folders[folderName] ?: emptyList()
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
                                                        Text(text = "${folderSongs.size} tracks", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                                folderSongs.forEach { song ->
                                                    SongRowItem(
                                                        song = song,
                                                        currentSong = currentSong,
                                                        isPlaying = isPlaying,
                                                        onSongClick = {
                                                            if (useWebAudioApi) {
                                                                activeWebAudioSong = song
                                                            } else {
                                                                viewModel.playSong(song, folderSongs)
                                                            }
                                                        },
                                                        onFavoriteClick = { viewModel.toggleFavorite(song.id, !song.isFavorite) },
                                                        onPlaylistAddClick = { showPlaylistSelectionDialog = song }
                                                    )
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        "Favorite" -> {
                            val favoriteSongs = remember(filteredSongs) { filteredSongs.filter { it.isFavorite } }
                            if (favoriteSongs.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.FavoriteBorder,
                                            contentDescription = null,
                                            modifier = Modifier.size(48.dp),
                                            tint = MaterialTheme.colorScheme.outline
                                        )
                                        Text(
                                            text = "No favorites added yet.",
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    contentPadding = PaddingValues(bottom = 80.dp)
                                ) {
                                    items(favoriteSongs) { song ->
                                        SongRowItem(
                                            song = song,
                                            currentSong = currentSong,
                                            isPlaying = isPlaying,
                                            onSongClick = {
                                                if (useWebAudioApi) {
                                                    activeWebAudioSong = song
                                                } else {
                                                    viewModel.playSong(song, favoriteSongs)
                                                }
                                            },
                                            onFavoriteClick = { viewModel.toggleFavorite(song.id, !song.isFavorite) },
                                            onPlaylistAddClick = { showPlaylistSelectionDialog = song }
                                        )
                                    }
                                }
                            }
                        }

                        "Playlist" -> {
                            val playlists by viewModel.allPlaylists.collectAsState()
                            var newPlaylistName by remember { mutableStateOf("") }
                            var expandedPlaylistId by remember { mutableStateOf<Long?>(null) }
                            val songsInPlaylist by viewModel.songsInPlaylist.collectAsState()

                            LaunchedEffect(expandedPlaylistId) {
                                expandedPlaylistId?.let { id ->
                                    viewModel.selectPlaylist(id)
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = newPlaylistName,
                                        onValueChange = { newPlaylistName = it },
                                        placeholder = { Text("New Playlist Name...") },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp),
                                        textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                                            unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                                        ),
                                        singleLine = true
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Button(
                                        onClick = {
                                            if (newPlaylistName.isNotBlank()) {
                                                viewModel.createPlaylist(newPlaylistName)
                                                newPlaylistName = ""
                                            }
                                        },
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.Add, contentDescription = "Create", modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Create", fontSize = 12.sp)
                                    }
                                }

                                if (playlists.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxWidth(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "No playlists created yet.",
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                        contentPadding = PaddingValues(bottom = 80.dp)
                                    ) {
                                        items(playlists) { playlist ->
                                            val isExpanded = expandedPlaylistId == playlist.id
                                            Card(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { expandedPlaylistId = if (isExpanded) null else playlist.id },
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
                                                                imageVector = Icons.Default.QueueMusic,
                                                                contentDescription = null,
                                                                tint = MaterialTheme.colorScheme.tertiary,
                                                                modifier = Modifier.size(40.dp)
                                                            )
                                                            Column {
                                                                Text(text = playlist.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                                Text(text = "Tap to view tracks", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                            }
                                                        }
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            IconButton(onClick = { viewModel.deletePlaylist(playlist.id) }) {
                                                                Icon(
                                                                    imageVector = Icons.Default.Delete,
                                                                    contentDescription = "Delete Playlist",
                                                                    tint = MaterialTheme.colorScheme.error,
                                                                    modifier = Modifier.size(20.dp)
                                                                )
                                                            }
                                                            Icon(
                                                                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                                                contentDescription = "Expand"
                                                            )
                                                        }
                                                    }

                                                    if (isExpanded) {
                                                        Spacer(modifier = Modifier.height(12.dp))
                                                        Divider(color = MaterialTheme.colorScheme.outlineVariant)
                                                        Spacer(modifier = Modifier.height(8.dp))
                                                        if (songsInPlaylist.isEmpty()) {
                                                            Text(
                                                                text = "This playlist is empty. Add songs using '+' from other tabs!",
                                                                fontSize = 12.sp,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                modifier = Modifier.padding(vertical = 8.dp)
                                                            )
                                                        } else {
                                                            songsInPlaylist.forEach { song ->
                                                                Card(
                                                                    modifier = Modifier.fillMaxWidth(),
                                                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                                                                    shape = RoundedCornerShape(10.dp)
                                                                ) {
                                                                    Row(
                                                                        modifier = Modifier
                                                                            .fillMaxWidth()
                                                                            .padding(8.dp)
                                                                            .clickable {
                                                                                if (useWebAudioApi) {
                                                                                    activeWebAudioSong = song
                                                                                } else {
                                                                                    viewModel.playSong(song, songsInPlaylist)
                                                                                }
                                                                            },
                                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                                        verticalAlignment = Alignment.CenterVertically
                                                                    ) {
                                                                        Column(modifier = Modifier.weight(1f)) {
                                                                            Text(text = song.title, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                                            Text(text = song.artist, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                                        }
                                                                        IconButton(onClick = { viewModel.removeSongFromPlaylist(playlist.id, song.id) }) {
                                                                            Icon(
                                                                                imageVector = Icons.Default.RemoveCircleOutline,
                                                                                contentDescription = "Remove",
                                                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                                modifier = Modifier.size(18.dp)
                                                                            )
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
                    }
                }
            }
        }

        // FLOATING PERSISTENT BOTTOM CAPSULE
        AnimatedVisibility(
            visible = currentSong != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp)
        ) {
            currentSong?.let { song ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .padding(horizontal = 12.dp)
                        .clickable { viewModel.expandPlayer(true) },
                    shape = RoundedCornerShape(32.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            SongThumbnail(
                                song = song,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                            Column {
                                Text(
                                    text = song.title,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = song.artist,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            IconButton(onClick = { viewModel.playPrevious() }) {
                                Icon(imageVector = Icons.Default.SkipPrevious, contentDescription = "Prev", modifier = Modifier.size(24.dp))
                            }
                            IconButton(onClick = { viewModel.togglePlayPause() }) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                                    contentDescription = "PlayPause",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                            IconButton(onClick = { viewModel.playNext() }) {
                                Icon(imageVector = Icons.Default.SkipNext, contentDescription = "Next", modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }
            }
        }

        // ADD TO PLAYLIST QUICK SELECTOR SHEET DIALOG
        if (showPlaylistSelectionDialog != null) {
            val targetSong = showPlaylistSelectionDialog!!
            val playlists by viewModel.allPlaylists.collectAsState()

            AlertDialog(
                onDismissRequest = { showPlaylistSelectionDialog = null },
                title = { Text("Add to Playlist") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Select a compilation playlist to add \"${targetSong.title}\" into.", fontSize = 13.sp)
                        if (playlists.isEmpty()) {
                            Text("No playlists created yet. Set one up in Libraries!", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                        } else {
                            LazyColumn(
                                modifier = Modifier.height(200.dp)
                            ) {
                                items(playlists) { playlist ->
                                    ListItem(
                                        modifier = Modifier.clickable {
                                            viewModel.addSongToPlaylist(playlist.id, targetSong.id)
                                            showPlaylistSelectionDialog = null
                                        },
                                        headlineContent = { Text(playlist.name) },
                                        leadingContent = { Icon(imageVector = Icons.Default.PlaylistPlay, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showPlaylistSelectionDialog = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun FullAudioDetailPlayer(
    song: SongEntity,
    viewModel: MediaViewModel,
    onBack: () -> Unit
) {
    val isPlaying by viewModel.isPlaying.collectAsState()
    val progress by viewModel.playbackProgress.collectAsState()
    val duration by viewModel.playbackDuration.collectAsState()
    val playSpeed by viewModel.playbackSpeed.collectAsState()
    val shuffleOn by viewModel.isShuffleEnabled.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()

    var speedMenuOpen by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // TOP RETRACT ACTION BAR
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = "Collapse",
                    modifier = Modifier.size(32.dp)
                )
            }
            Text(
                text = "NOW PLAYING",
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.primary
            )
            IconButton(
                onClick = { viewModel.toggleFavorite(song.id, !song.isFavorite) }
            ) {
                Icon(
                    imageVector = if (song.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    tint = if (song.isFavorite) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                    contentDescription = "Favorite status"
                )
            }
        }

        // HERO COVER ART (REPLACES DISK HOOP WITH MODERN RETRIVAL ARTWORK)
        SongThumbnail(
            song = song,
            modifier = Modifier
                .size(240.dp)
                .clip(RoundedCornerShape(24.dp))
        )

        // TEXT HEADLINE DETAILS
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = song.title,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = song.artist,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // REAL-TIME SOUND GRAPHICS
        AsymmetricVisualizer(
            isPlaying = isPlaying,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        )

        // SLIDER TIMERS SCENE
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = formatTrackTime(progress), fontSize = 11.sp)
                Text(text = formatTrackTime(duration), fontSize = 11.sp)
            }
            Slider(
                value = progress.toFloat(),
                onValueChange = { viewModel.seekTo(it.toLong()) },
                valueRange = 0f..(duration.toFloat().coerceAtLeast(1f)),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth().height(16.dp)
            )
        }

        // MUSIC PLAYBACK BUTTONS ROW
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Shuffle
            IconButton(onClick = { viewModel.setShuffleEnabled(!shuffleOn) }) {
                Icon(
                    imageVector = Icons.Default.Shuffle,
                    contentDescription = "Shuffle",
                    tint = if (shuffleOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Skip backward
            IconButton(onClick = { viewModel.playPrevious() }) {
                Icon(
                    imageVector = Icons.Default.SkipPrevious,
                    contentDescription = "Skip prev",
                    modifier = Modifier.size(48.dp)
                )
            }

            // Big Play/Pause
            IconButton(onClick = { viewModel.togglePlayPause() }) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.PauseCircleFilled else Icons.Default.PlayCircleFilled,
                    contentDescription = "Playpause toggle",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(80.dp)
                )
            }

            // Skip forward
            IconButton(onClick = { viewModel.playNext() }) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = "Skip next",
                    modifier = Modifier.size(48.dp)
                )
            }

            // Repeat toggle
            val isRepeating = repeatMode != Player.REPEAT_MODE_OFF
            IconButton(
                onClick = {
                    val nextMode = if (repeatMode == Player.REPEAT_MODE_OFF) Player.REPEAT_MODE_ONE
                                   else if (repeatMode == Player.REPEAT_MODE_ONE) Player.REPEAT_MODE_ALL
                                   else Player.REPEAT_MODE_OFF
                    viewModel.setRepeatMode(nextMode)
                }
            ) {
                Icon(
                    imageVector = if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                    contentDescription = "Repeat",
                    tint = if (isRepeating) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // FOOTER UTILITIES (SPEED, EQUALIZER, SLEEP TIMER ROW)
        var showEqualizerDialog by remember { mutableStateOf(false) }
        var showSleepTimerDialog by remember { mutableStateOf(false) }
        val sleepRemainingSeconds by viewModel.sleepTimeRemaining.collectAsState()
        val timerActive = sleepRemainingSeconds > 0

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Speed trigger
            Box {
                TextButton(onClick = { speedMenuOpen = true }) {
                    Icon(imageVector = Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "${playSpeed}x", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                DropdownMenu(
                    expanded = speedMenuOpen,
                    onDismissRequest = { speedMenuOpen = false }
                ) {
                    listOf(0.5f, 0.8f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                        DropdownMenuItem(
                            text = { Text("${speed}x") },
                            onClick = {
                                viewModel.setPlaybackSpeed(speed)
                                speedMenuOpen = false
                            }
                        )
                    }
                }
            }

            // Equalizer trigger
            TextButton(onClick = { showEqualizerDialog = true }) {
                Icon(imageVector = Icons.Default.GraphicEq, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "Equalizer", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            // Sleep Timer trigger
            TextButton(onClick = { showSleepTimerDialog = true }) {
                Icon(
                    imageVector = if (timerActive) Icons.Default.Timer else Icons.Default.TimerOff,
                    contentDescription = null,
                    tint = if (timerActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (timerActive) formatTimerMinutes(sleepRemainingSeconds) else "Timer",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (timerActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // EqualizerDialog popup
        if (showEqualizerDialog) {
            AlertDialog(
                onDismissRequest = { showEqualizerDialog = false },
                confirmButton = {
                    TextButton(onClick = { showEqualizerDialog = false }) {
                        Text("Done")
                    }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.GraphicEq, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Acoustic Equalizer")
                    }
                },
                text = {
                    Box(modifier = Modifier.fillMaxWidth().height(360.dp)) {
                        EqualizerScreen(viewModel = viewModel)
                    }
                }
            )
        }

        // SleepTimerDialog popup
        if (showSleepTimerDialog) {
            var sliderMinutes by remember { mutableFloatStateOf(15f) }
            
            AlertDialog(
                onDismissRequest = { showSleepTimerDialog = false },
                confirmButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (timerActive) {
                            TextButton(
                                onClick = {
                                    viewModel.cancelSleepTimer()
                                    showSleepTimerDialog = false
                                }
                            ) {
                                Text("Stop Timer", color = MaterialTheme.colorScheme.error)
                            }
                        }
                        Button(
                            onClick = {
                                viewModel.startSleepTimer(sliderMinutes.toInt())
                                showSleepTimerDialog = false
                            }
                        ) {
                            Text("Start")
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showSleepTimerDialog = false }) {
                        Text("Cancel")
                    }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sleep Timer")
                    }
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Stop playback automatically inside selected time interval.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${sliderMinutes.toInt()} minutes",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Slider(
                            value = sliderMinutes,
                            onValueChange = { sliderMinutes = it },
                            valueRange = 1f..120f,
                            steps = 119,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary
                            )
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            listOf(10, 20, 30, 60).forEach { mins ->
                                SuggestionChip(
                                    onClick = { sliderMinutes = mins.toFloat() },
                                    label = { Text("${mins}m") }
                                )
                            }
                        }
                    }
                }
            )
        }
    }
}

private fun formatTrackTime(millis: Long): String {
    val seconds = (millis / 1000) % 60
    val minutes = (millis / 60000) % 60
    return String.format("%02d:%02d", minutes, seconds)
}

@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Html5AudioPlayer(
    song: SongEntity,
    onBack: () -> Unit
) {
    val htmlContent = remember(song.id) {
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
    color: #ffffff;
    display: flex;
    flex-direction: column;
    justify-content: space-between;
    align-items: center;
    box-sizing: border-box;
    overflow: hidden;
  }
  
  .header {
    width: 100%;
    padding: 20px;
    text-align: center;
    box-sizing: border-box;
  }
  
  .title {
    font-size: 18px;
    font-weight: bold;
    color: #00ffe6;
    text-shadow: 0 0 10px rgba(0, 255, 230, 0.4);
    margin-bottom: 4px;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
  
  .artist {
    font-size: 13px;
    color: #a0a0a0;
  }

  .canvas-container {
    flex-grow: 1;
    width: 100%;
    display: flex;
    justify-content: center;
    align-items: center;
    position: relative;
  }

  canvas {
    width: 100%;
    height: 180px;
    display: block;
  }

  .disc-container {
    position: absolute;
    width: 130px;
    height: 130px;
    border-radius: 50%;
    border: 3px solid rgba(0, 255, 230, 0.2);
    background: radial-gradient(circle, #1a1a1a 30%, #000000 80%);
    box-shadow: 0 0 20px rgba(0, 255, 230, 0.15);
    display: flex;
    justify-content: center;
    align-items: center;
    animation: rotateDisc 8s linear infinite;
    animation-play-state: paused;
  }
  
  .disc-center {
    width: 35px;
    height: 35px;
    border-radius: 50%;
    background-color: #00ffe6;
    box-shadow: 0 0 10px #00ffe6;
  }

  @keyframes rotateDisc {
    0% { transform: rotate(0deg); }
    100% { transform: rotate(360deg); }
  }

  .spinner-overlay {
    position: absolute;
    width: 100%; height: 100%;
    background: rgba(13, 13, 13, 0.9);
    display: flex;
    flex-direction: column;
    justify-content: center;
    align-items: center;
    z-index: 10;
    transition: opacity 0.4s ease;
  }

  .spinner {
    width: 50px;
    height: 50px;
    border: 4px solid rgba(0, 255, 230, 0.1);
    border-radius: 50%;
    border-top-color: #00ffe6;
    animation: spin 1s linear infinite;
    filter: drop-shadow(0 0 10px #00ffe6);
  }

  .loading-text {
    margin-top: 16px;
    font-size: 13px;
    letter-spacing: 2px;
    color: #00ffe6;
    font-weight: bold;
    text-shadow: 0 0 8px rgba(0, 255, 230, 0.3);
  }

  @keyframes spin {
    0% { transform: rotate(0deg); }
    100% { transform: rotate(360deg); }
  }

  .controls-deck {
    width: 100%;
    background: #141414;
    border-top: 1px solid rgba(0, 255, 230, 0.15);
    padding: 20px;
    box-sizing: border-box;
    display: flex;
    flex-direction: column;
    gap: 15px;
  }

  .seeker-row {
    display: flex;
    align-items: center;
    width: 100%;
    gap: 10px;
  }

  .time-lbl {
    font-size: 11px;
    font-family: monospace;
    color: #a0a0a0;
  }

  .seek-slider {
    flex-grow: 1;
    -webkit-appearance: none;
    background: rgba(255, 255, 255, 0.1);
    height: 4px;
    border-radius: 2px;
    outline: none;
  }

  .seek-slider::-webkit-slider-thumb {
    -webkit-appearance: none;
    width: 12px;
    height: 12px;
    border-radius: 50%;
    background: #00ffe6;
    box-shadow: 0 0 8px #00ffe6;
    cursor: pointer;
  }

  .buttons-row {
    display: flex;
    justify-content: center;
    align-items: center;
    width: 100%;
    gap: 25px;
  }

  button {
    background: none;
    border: none;
    color: #ffffff;
    cursor: pointer;
    outline: none;
    transition: all 0.2s ease;
  }

  button:active {
    transform: scale(0.9);
  }

  .btn-play {
    width: 54px;
    height: 54px;
    border-radius: 50%;
    background: #00ffe6;
    color: #000;
    box-shadow: 0 0 15px rgba(0, 255, 230, 0.4);
    display: flex;
    justify-content: center;
    align-items: center;
  }

  .btn-play svg {
    width: 24px;
    height: 24px;
    fill: currentColor;
  }

  .btn-prev-next {
    color: #a0a0a0;
  }

  .btn-prev-next svg {
    width: 20px;
    height: 20px;
    fill: currentColor;
  }
</style>
</head>
<body>

  <div class="header">
    <div class="title" id="track-title">Loading...</div>
    <div class="artist" id="track-artist">Please wait</div>
  </div>

  <div class="canvas-container">
    <div class="spinner-overlay" id="loading-spinner">
      <div class="spinner"></div>
      <div class="loading-text" id="buff-text">Connecting Audio</div>
    </div>
    
    <div class="disc-container" id="disc">
      <div class="disc-center"></div>
    </div>
    
    <canvas id="visualizer-canvas"></canvas>
  </div>

  <div class="controls-deck">
    <div class="seeker-row">
      <span class="time-lbl" id="elapsed">00:00</span>
      <input type="range" class="seek-slider" id="seek-bar" value="0" min="0" max="100">
      <span class="time-lbl" id="duration">00:00</span>
    </div>

    <div class="buttons-row">
      <button class="btn-prev-next" onclick="seekOffset(-10)">
        <svg viewBox="0 0 24 24"><path d="M11 18V6l-8.5 6 8.5 6zm.5-6l8.5 6V6l-8.5 6z"/></svg>
      </button>
      
      <button class="btn-play" onclick="togglePlayback()">
        <svg id="play-icon" viewBox="0 0 24 24"><path d="M8 5v14l11-7z"/></svg>
        <svg id="pause-icon" viewBox="0 0 24 24" style="display:none;"><path d="M6 19h4V5H6v14zm8-14v14h4V5h-4z"/></svg>
      </button>
      
      <button class="btn-prev-next" onclick="seekOffset(10)">
        <svg viewBox="0 0 24 24"><path d="M4 18l8.5-6L4 6v12zm9-12v12l8.5-6L13 6z"/></svg>
      </button>
    </div>
  </div>

<script>
  let audioCtx = null;
  let audioSource = null;
  let analyser = null;
  let gainNode = null;
  let mediaElement = null;
  
  const audioUrl = "##AUDIO_URL##";
  const songTitle = "##TITLE##";
  const songArtist = "##ARTIST##";

  document.getElementById('track-title').innerText = songTitle;
  document.getElementById('track-artist').innerText = songArtist;

  const visualizerCanvas = document.getElementById('visualizer-canvas');
  const canvasCtx = visualizerCanvas.getContext('2d');
  const playIcon = document.getElementById('play-icon');
  const pauseIcon = document.getElementById('pause-icon');
  const spinner = document.getElementById('loading-spinner');
  const buffText = document.getElementById('buff-text');
  const disc = document.getElementById('disc');
  const seekBar = document.getElementById('seek-bar');
  const elapsedLbl = document.getElementById('elapsed');
  const durationLbl = document.getElementById('duration');

  function resizeCanvas() {
    visualizerCanvas.width = visualizerCanvas.parentElement.clientWidth;
    visualizerCanvas.height = 180;
  }
  window.addEventListener('resize', resizeCanvas);
  resizeCanvas();

  mediaElement = new Audio();
  mediaElement.crossOrigin = "anonymous";
  mediaElement.src = audioUrl;

  mediaElement.addEventListener('canplay', () => {
    hideSpinner();
    updateDuration();
  });

  mediaElement.addEventListener('waiting', () => {
    showSpinner("Buffering...");
  });

  mediaElement.addEventListener('stalled', () => {
    showSpinner("Loading Stream...");
  });

  mediaElement.addEventListener('play', () => {
    initWebAudio();
    playIcon.style.display = 'none';
    pauseIcon.style.display = 'block';
    disc.style.animationPlayState = 'running';
    if (audioCtx && audioCtx.state === 'suspended') {
      audioCtx.resume();
    }
  });

  mediaElement.addEventListener('pause', () => {
    playIcon.style.display = 'block';
    pauseIcon.style.display = 'none';
    disc.style.animationPlayState = 'paused';
  });

  mediaElement.addEventListener('timeupdate', () => {
    updateProgress();
  });

  let isSeeking = false;
  seekBar.addEventListener('input', () => {
    isSeeking = true;
    const time = mediaElement.duration * (seekBar.value / 100);
    elapsedLbl.innerText = formatTime(time);
  });

  seekBar.addEventListener('change', () => {
    const time = mediaElement.duration * (seekBar.value / 100);
    mediaElement.currentTime = time;
    isSeeking = false;
  });

  function showSpinner(text) {
    if (text) buffText.innerText = text;
    spinner.style.opacity = '1';
    spinner.style.pointerEvents = 'auto';
  }

  function hideSpinner() {
    spinner.style.opacity = '0';
    spinner.style.pointerEvents = 'none';
  }

  function togglePlayback() {
    if (mediaElement.paused || mediaElement.ended) {
      mediaElement.play().catch(err => {
        console.error("Start play failed", err);
        initWebAudio();
      });
    } else {
      mediaElement.pause();
    }
  }

  function seekOffset(amount) {
    if (!mediaElement.duration) return;
    mediaElement.currentTime = Math.max(0, Math.min(mediaElement.duration, mediaElement.currentTime + amount));
  }

  function formatTime(seconds) {
    if (isNaN(seconds)) return '00:00';
    const m = Math.floor(seconds / 60);
    const s = Math.floor(seconds % 60);
    return m.toString().padStart(2, '0') + ':' + s.toString().padStart(2, '0');
  }

  function updateDuration() {
    durationLbl.innerText = formatTime(mediaElement.duration);
  }

  function updateProgress() {
    if (isSeeking) return;
    if (mediaElement.duration) {
      seekBar.value = (mediaElement.currentTime / mediaElement.duration) * 100;
    }
    elapsedLbl.innerText = formatTime(mediaElement.currentTime);
  }

  function initWebAudio() {
    if (audioCtx) return;
    
    try {
      const AudioContext = window.AudioContext || window.webkitAudioContext;
      audioCtx = new AudioContext();
      
      audioSource = audioCtx.createMediaElementSource(mediaElement);
      analyser = audioCtx.createAnalyser();
      gainNode = audioCtx.createGain();
      
      analyser.fftSize = 128;
      
      audioSource.connect(analyser);
      analyser.connect(gainNode);
      gainNode.connect(audioCtx.destination);
      
      drawFrequencyBars();
    } catch(e) {
      console.error("Web Audio API error", e);
    }
  }

  function drawFrequencyBars() {
    requestAnimationFrame(drawFrequencyBars);
    
    if (!analyser) return;
    
    const bufferLength = analyser.frequencyBinCount;
    const dataArray = new Uint8Array(bufferLength);
    analyser.getByteFrequencyData(dataArray);
    
    const width = visualizerCanvas.width;
    const height = visualizerCanvas.height;
    
    canvasCtx.clearRect(0, 0, width, height);
    
    const barWidth = (width / bufferLength) * 1.3;
    let x = 0;
    
    for (let i = 0; i < bufferLength; i++) {
      const barHeightValue = dataArray[i];
      const barHeight = (barHeightValue / 255) * height * 0.95;
      
      const gradient = canvasCtx.createLinearGradient(0, height, 0, height - barHeight);
      gradient.addColorStop(0, '#121212');
      gradient.addColorStop(0.3, '#005f52');
      gradient.addColorStop(1, '#00ffe6');
      
      canvasCtx.fillStyle = gradient;
      canvasCtx.fillRect(x, height - barHeight, barWidth - 3, barHeight);
      
      x += barWidth;
    }
  }

  mediaElement.load();
</script>
</body>
</html>
        """.trimIndent()
        rawHtml
            .replace("##AUDIO_URL##", song.id)
            .replace("##TITLE##", song.title.replace("'", "\\'"))
            .replace("##ARTIST##", song.artist.replace("'", "\\'"))
    }

    BackHandler {
        onBack()
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0D0D0D))
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Web Audio Developer Engine",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Powered by Web Audio API",
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
                    settings.allowFileAccess = true
                    
                    webChromeClient = object : WebChromeClient() {}
                    webViewClient = object : WebViewClient() {}
                    
                    loadDataWithBaseURL("https://localhost", htmlContent, "text/html", "UTF-8", null)
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFF0D0D0D))
        )
    }
}

@Composable
fun SongRowItem(
    song: com.example.mediaplayer.data.SongEntity,
    currentSong: com.example.mediaplayer.data.SongEntity?,
    isPlaying: Boolean,
    onSongClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onPlaylistAddClick: () -> Unit
) {
    val isCurrent = currentSong?.id == song.id
    val cardBg = if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSongClick() },
        colors = CardDefaults.cardColors(containerColor = cardBg),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                SongThumbnail(
                    song = song,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                )

                Column {
                    Text(
                        text = song.title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = song.artist,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onFavoriteClick) {
                    Icon(
                        imageVector = if (song.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        tint = if (song.isFavorite) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                        contentDescription = "Favorite"
                    )
                }
                IconButton(onClick = onPlaylistAddClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                        contentDescription = "Playlist Add"
                    )
                }
            }
        }
    }
}

@Composable
fun SongThumbnail(
    song: SongEntity,
    modifier: Modifier = Modifier
) {
    var errorOccurred by remember { mutableStateOf(false) }
    
    Box(
        modifier = modifier
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.secondaryContainer
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        if (!errorOccurred) {
            androidx.compose.foundation.Image(
                painter = coil.compose.rememberAsyncImagePainter(
                    model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                        .data(android.net.Uri.parse(song.id))
                        .crossfade(true)
                        .build(),
                    onError = { errorOccurred = true }
                ),
                contentDescription = "Album art for ${song.title}",
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
        }
        
        if (errorOccurred) {
            val firstChar = song.title.firstOrNull()?.uppercase() ?: "?"
            Text(
                text = firstChar,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }
    }
}

private fun formatTimerMinutes(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
