package com.example.ui.screens

import androidx.activity.compose.BackHandler
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.VideoPlaybackActivity
import com.example.data.db.MediaEntity
import com.example.ui.components.rememberVideoThumbnail
import com.example.ui.components.SkeletonListLoader
import com.example.ui.theme.DarkPrimary
import com.example.ui.theme.DarkSecondary
import com.example.ui.viewmodel.MediaViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

data class VideoDetails(
    val resolution: String = "Loading...",
    val aspectRatio: String = "Loading..."
)

// Global cache to avoid heavy re-reading during list scroll
private val videoMetadataCache = java.util.concurrent.ConcurrentHashMap<String, VideoDetails>()

@Composable
fun rememberVideoMetadata(videoPath: String): VideoDetails {
    var details by remember(videoPath) {
        mutableStateOf(videoMetadataCache[videoPath] ?: VideoDetails("Loading...", "Loading..."))
    }

    if (details.resolution == "Loading...") {
        LaunchedEffect(videoPath) {
            val result = withContext(Dispatchers.IO) {
                try {
                    val file = File(videoPath)
                    if (file.exists()) {
                        val retriever = MediaMetadataRetriever()
                        retriever.setDataSource(videoPath)
                        val widthStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                        val heightStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                        retriever.release()

                        val width = widthStr?.toIntOrNull() ?: 0
                        val height = heightStr?.toIntOrNull() ?: 0
                        if (width > 0 && height > 0) {
                            val res = "${width}x${height}"
                            val ratio = getAspectRatioString(width, height)
                            VideoDetails(res, ratio)
                        } else {
                            VideoDetails("Unknown", "Unknown")
                        }
                    } else {
                        VideoDetails("1280x720", "16:9")
                    }
                } catch (e: Exception) {
                    VideoDetails("Unknown", "Unknown")
                }
            }
            videoMetadataCache[videoPath] = result
            details = result
        }
    }

    return details
}

private fun getAspectRatioString(width: Int, height: Int): String {
    fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)
    val divisor = gcd(width, height)
    if (divisor > 0) {
        val w = width / divisor
        val h = height / divisor
        if (w == 16 && h == 9) return "16:9"
        if (w == 4 && h == 3) return "4:3"
        if (w == 21 && h == 9) return "21:9"
        if (w == 3 && h == 2) return "3:2"
        if (w == 1 && h == 1) return "1:1"
        if (w == 16 && h == 10) return "16:10"
        return "$w:$h"
    }
    return "Unknown"
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoTab(
    viewModel: MediaViewModel,
    modifier: Modifier = Modifier
) {
    val videos by viewModel.filteredVideos.collectAsState()
    val context = LocalContext.current

    // Sub-tab selection (0: Videos, 1: Folders)
    var subTabSelection by remember { mutableIntStateOf(0) }

    // Active bottom-sheet / dialogue entities
    var activeMenuVideo by remember { mutableStateOf<MediaEntity?>(null) }
    var renameDialogVideo by remember { mutableStateOf<MediaEntity?>(null) }
    var deleteDialogVideo by remember { mutableStateOf<MediaEntity?>(null) }
    var detailsDialogVideo by remember { mutableStateOf<MediaEntity?>(null) }
    var playlistDialogVideo by remember { mutableStateOf<MediaEntity?>(null) }

    var expandedFolder by remember { mutableStateOf<String?>(null) }
    var activeMenuFolder by remember { mutableStateOf<String?>(null) }
    var detailsDialogFolder by remember { mutableStateOf<String?>(null) }
    var deleteDialogFolder by remember { mutableStateOf<String?>(null) }

    BackHandler(enabled = expandedFolder != null) {
        expandedFolder = null
    }

    BackHandler(enabled = subTabSelection == 1 && expandedFolder == null) {
        subTabSelection = 0
    }

    // Grouping videos dynamically by folder path
    val videosByFolder = remember(videos) {
        videos.groupBy { video ->
            val lastSlashIdx = video.path.lastIndexOf('/')
            if (lastSlashIdx != -1) {
                video.path.substring(0, lastSlashIdx)
            } else {
                "Root Memory"
            }
        }
    }

    fun playVideo(videoItem: MediaEntity) {
        viewModel.pause()
        viewModel.setCurrentlyPlayingVideo(videoItem)
        val intent = Intent(context, VideoPlaybackActivity::class.java).apply {
            putExtra("extra_media_path", videoItem.path)
            putExtra("extra_media_title", videoItem.title)
            putExtra("extra_media_artist", videoItem.artist)
            putExtra("extra_media_album", videoItem.album)
            putExtra("extra_media_duration", videoItem.duration)
        }
        context.startActivity(intent)
    }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        // Sub tabs below search bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("Videos", "Folders").forEachIndexed { index, label ->
                val isSelected = subTabSelection == index
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                        .clickable { subTabSelection = index }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        val isScanning by viewModel.isScanning.collectAsState()

        if (isScanning && videos.isEmpty()) {
            SkeletonListLoader()
        } else if (videos.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.VideoFile,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                        modifier = Modifier.size(65.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        "No local video clips found.",
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            when (subTabSelection) {
                0 -> {
                    // Videos list tab
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 160.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(videos) { _, video ->
                            VideoListItem(
                                video = video,
                                onClick = { playVideo(video) },
                                onLongClick = { activeMenuVideo = video }
                            )
                        }
                    }
                }
                1 -> {
                    // Folders tab
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 160.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(videosByFolder.keys.toList()) { folderPath ->
                            val folderVideos = videosByFolder[folderPath] ?: emptyList()
                            val isExpanded = expandedFolder == folderPath
                            val folderName = folderPath.substringAfterLast('/')

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = {
                                            expandedFolder = if (isExpanded) null else folderPath
                                        },
                                        onLongClick = {
                                            activeMenuFolder = folderPath
                                        }
                                    ),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(
                                                Icons.Filled.Folder,
                                                contentDescription = "Folder",
                                                tint = DarkPrimary,
                                                modifier = Modifier.size(34.dp)
                                            )
                                            Column {
                                                Text(
                                                    text = folderName,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = folderPath,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                    fontSize = 11.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "${folderVideos.size} video files",
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(onClick = { activeMenuFolder = folderPath }) {
                                                Icon(
                                                    Icons.Filled.MoreVert,
                                                    contentDescription = "Folder Actions",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Icon(
                                                if (isExpanded) Icons.Filled.ExpandMore else Icons.Filled.ChevronRight,
                                                contentDescription = null,
                                                tint = Color.Gray,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }

                                    AnimatedVisibility(
                                        visible = isExpanded,
                                        enter = expandVertically() + fadeIn(),
                                        exit = shrinkVertically() + fadeOut()
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                                                .padding(bottom = 8.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                                            folderVideos.forEach { vid ->
                                                VideoListItem(
                                                    video = vid,
                                                    onClick = { playVideo(vid) },
                                                    onLongClick = { activeMenuVideo = vid },
                                                    modifier = Modifier.padding(horizontal = 8.dp)
                                                )
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

    // Context dialogs and sheets triggers

    // Video 3-dot Bottom Sheet Context Menu
    activeMenuVideo?.let { video ->
        VideoContextMenuBottomSheet(
            video = video,
            viewModel = viewModel,
            onDismissRequest = { activeMenuVideo = null },
            onAddToPlaylist = { playlistDialogVideo = video },
            onShowRenameDialog = { renameDialogVideo = video },
            onShowDeleteDialog = { deleteDialogVideo = video },
            onShowDetailsDialog = { detailsDialogVideo = video }
        )
    }

    // Video Add to Playlist dialog
    playlistDialogVideo?.let { video ->
        AddToPlaylistDialog(
            song = video,
            viewModel = viewModel,
            onDismiss = { playlistDialogVideo = null }
        )
    }

    // Video Rename Dialog
    renameDialogVideo?.let { video ->
        RenameDialog(
            song = video,
            onConfirm = { newTitle ->
                viewModel.renameMediaByPath(video.path, newTitle)
                Toast.makeText(context, "Video title saved", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { renameDialogVideo = null }
        )
    }

    // Video Delete confirmation
    deleteDialogVideo?.let { video ->
        DeleteConfirmationDialog(
            song = video,
            onConfirm = {
                viewModel.deleteMediaByPath(video.path)
                Toast.makeText(context, "Deleted video from database list", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { deleteDialogVideo = null }
        )
    }

    // Video Details Dialog
    detailsDialogVideo?.let { video ->
        VideoDetailsDialog(
            video = video,
            onDismiss = { detailsDialogVideo = null }
        )
    }

    // Folder 3-dot bottom sheet menu
    activeMenuFolder?.let { folderPath ->
        val folderVideos = videosByFolder[folderPath] ?: emptyList()
        FolderContextMenuBottomSheet(
            folderPath = folderPath,
            filesCount = folderVideos.size,
            onDismissRequest = { activeMenuFolder = null },
            onPlayAll = {
                if (folderVideos.isNotEmpty()) {
                    playVideo(folderVideos[0])
                }
                activeMenuFolder = null
            },
            onEnqueueAll = {
                folderVideos.forEach { viewModel.addToQueue(it) }
                Toast.makeText(context, "Added ${folderVideos.size} videos to library queue", Toast.LENGTH_SHORT).show()
                activeMenuFolder = null
            },
            onShowDetails = { detailsDialogFolder = folderPath },
            onShowDelete = { deleteDialogFolder = folderPath }
        )
    }

    // Folder details dialog
    detailsDialogFolder?.let { folderPath ->
        val folderVideos = videosByFolder[folderPath] ?: emptyList()
        FolderDetailsDialog(
            folderPath = folderPath,
            folderVideos = folderVideos,
            onDismiss = { detailsDialogFolder = null }
        )
    }

    // Folder delete index confirmation dialog
    deleteDialogFolder?.let { folderPath ->
        val folderVideos = videosByFolder[folderPath] ?: emptyList()
        AlertDialog(
            onDismissRequest = { deleteDialogFolder = null },
            title = { Text("Delete folder videos?", color = Color.Red) },
            text = { Text("Hide or remove all ${folderVideos.size} videos in folder '$folderPath' from library catalogs?", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = {
                Button(
                    onClick = {
                        val paths = folderVideos.map { it.path }
                        viewModel.deleteMediaByPaths(paths)
                        Toast.makeText(context, "Removed folder items from list", Toast.LENGTH_SHORT).show()
                        deleteDialogFolder = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("Delete catalog", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteDialogFolder = null }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoListItem(
    video: MediaEntity,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bitmap = rememberVideoThumbnail(video.path)
    val meta = rememberVideoMetadata(video.path)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Thumbnail container
            Box(
                modifier = Modifier
                    .size(width = 110.dp, height = 70.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black),
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
                    val thumbnailBrush = Brush.verticalGradient(
                        colors = listOf(DarkSecondary.copy(alpha = 0.5f), Color.Black)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(thumbnailBrush)
                    )
                    Icon(
                        Icons.Filled.VideoFile,
                        contentDescription = null,
                        tint = DarkPrimary.copy(alpha = 0.6f),
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Play Overlay Circular button
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(Color.Black.copy(alpha = 0.5f), shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = "Play",
                        tint = DarkPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Mini duration badge
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .background(Color.Black.copy(alpha = 0.75f), shape = RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = formatDuration(video.duration),
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Central details Column
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Metadata details: resolution and dimension ratios
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Resolution tag
                    Box(
                        modifier = Modifier
                            .border(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = meta.resolution,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Aspect ratio tag
                    Box(
                        modifier = Modifier
                            .border(0.5.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = meta.aspectRatio,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // 3-dot Actions trigger
            IconButton(onClick = onLongClick) {
                Icon(
                    Icons.Filled.MoreVert,
                    contentDescription = "More",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoContextMenuBottomSheet(
    video: MediaEntity,
    viewModel: MediaViewModel,
    onDismissRequest: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onShowRenameDialog: () -> Unit,
    onShowDeleteDialog: () -> Unit,
    onShowDetailsDialog: () -> Unit
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
                    Icon(Icons.Filled.Videocam, contentDescription = null, tint = DarkPrimary)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(video.title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
                    Text("Duration: ${formatDuration(video.duration)}", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), fontSize = 12.sp, maxLines = 1)
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f), modifier = Modifier.padding(vertical = 4.dp))

            DropdownMenuItem(
                text = { Text("Immediately Play", color = MaterialTheme.colorScheme.onSurface) },
                onClick = {
                    viewModel.pause()
                    viewModel.setCurrentlyPlayingVideo(video)
                    val intent = Intent(context, VideoPlaybackActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        putExtra("extra_media_path", video.path)
                        putExtra("extra_media_title", video.title)
                        putExtra("extra_media_artist", video.artist)
                        putExtra("extra_media_album", video.album)
                        putExtra("extra_media_duration", video.duration)
                    }
                    context.startActivity(intent)
                    onDismissRequest()
                },
                leadingIcon = { Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
            )

            DropdownMenuItem(
                text = { Text("Enqueue Video", color = MaterialTheme.colorScheme.onSurface) },
                onClick = {
                    viewModel.addToQueue(video)
                    onDismissRequest()
                },
                leadingIcon = { Icon(Icons.Filled.QueueMusic, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
            )

            DropdownMenuItem(
                text = { Text("Save Video Into Playlist", color = MaterialTheme.colorScheme.onSurface) },
                onClick = {
                    onAddToPlaylist()
                    onDismissRequest()
                },
                leadingIcon = { Icon(Icons.Filled.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
            )

            DropdownMenuItem(
                text = { Text("Rename Title", color = MaterialTheme.colorScheme.onSurface) },
                onClick = {
                    onShowRenameDialog()
                    onDismissRequest()
                },
                leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
            )

            DropdownMenuItem(
                text = { Text("Delete Track Index", color = MaterialTheme.colorScheme.onSurface) },
                onClick = {
                    onShowDeleteDialog()
                    onDismissRequest()
                },
                leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
            )

            DropdownMenuItem(
                text = { Text("View File Details", color = MaterialTheme.colorScheme.onSurface) },
                onClick = {
                    onShowDetailsDialog()
                    onDismissRequest()
                },
                leadingIcon = { Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderContextMenuBottomSheet(
    folderPath: String,
    filesCount: Int,
    onDismissRequest: () -> Unit,
    onPlayAll: () -> Unit,
    onEnqueueAll: () -> Unit,
    onShowDetails: () -> Unit,
    onShowDelete: () -> Unit
) {
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
                    Icon(Icons.Filled.Folder, contentDescription = null, tint = DarkPrimary)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(folderPath.substringAfterLast('/'), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
                    Text("$filesCount video elements", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), fontSize = 12.sp, maxLines = 1)
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f), modifier = Modifier.padding(vertical = 4.dp))

            DropdownMenuItem(
                text = { Text("Play All Videos", color = MaterialTheme.colorScheme.onSurface) },
                onClick = {
                    onPlayAll()
                    onDismissRequest()
                },
                leadingIcon = { Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
            )

            DropdownMenuItem(
                text = { Text("Enqueue All Videos", color = MaterialTheme.colorScheme.onSurface) },
                onClick = {
                    onEnqueueAll()
                    onDismissRequest()
                },
                leadingIcon = { Icon(Icons.Filled.QueueMusic, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
            )

            DropdownMenuItem(
                text = { Text("Folder Properties", color = MaterialTheme.colorScheme.onSurface) },
                onClick = {
                    onShowDetails()
                    onDismissRequest()
                },
                leadingIcon = { Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
            )

            DropdownMenuItem(
                text = { Text("Hide Folder Catalog", color = MaterialTheme.colorScheme.onSurface) },
                onClick = {
                    onShowDelete()
                    onDismissRequest()
                },
                leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
            )
        }
    }
}

@Composable
fun VideoDetailsDialog(
    video: MediaEntity,
    onDismiss: () -> Unit
) {
    val meta = rememberVideoMetadata(video.path)

    val fileSizeFormatted = remember(video.path, video.size) {
        val file = File(video.path)
        if (file.exists()) {
            val size = file.length()
            val sizeMb = size.toFloat() / (1024 * 1024)
            String.format("%.2f MB", sizeMb)
        } else {
            val sizeMb = video.size.toFloat() / (1024 * 1024)
            String.format("%.2f MB", sizeMb)
        }
    }

    val lastModifiedFormatted = remember(video.path) {
        val file = File(video.path)
        if (file.exists()) {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            sdf.format(Date(file.lastModified()))
        } else {
            "Unknown Date"
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Video Properties",
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Black,
                fontSize = 18.sp
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Title", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(video.title, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Duration", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(formatDuration(video.duration), color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
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
                        Text("Resolution", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(meta.resolution, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Aspect Ratio", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(meta.aspectRatio, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Format Codec", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(video.path.substringAfterLast('.', "Unknown").uppercase(), color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Modified Date", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(lastModifiedFormatted, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("File Path", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = video.path,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        lineHeight = 14.sp
                    )
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
fun FolderDetailsDialog(
    folderPath: String,
    folderVideos: List<MediaEntity>,
    onDismiss: () -> Unit
) {
    val totalSize = remember(folderVideos) {
        var sum = 0L
        folderVideos.forEach {
            val file = File(it.path)
            if (file.exists()) {
                sum += file.length()
            }
        }
        val sizeMb = sum.toFloat() / (1024 * 1024)
        String.format("%.2f MB", sizeMb)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Folder Properties",
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Black,
                fontSize = 18.sp
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Folder Name", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(folderPath.substringAfterLast('/'), color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Physical Location", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(folderPath, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Video Files", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text("${folderVideos.size} items", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Aggregated Size", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(totalSize, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
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
