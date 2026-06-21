package com.msi.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.msi.ui.theme.ResponsiveDimensions
import com.msi.ui.viewmodel.MediaViewModel
import java.io.File

@Composable
fun FolderTab(
    viewModel: MediaViewModel,
    modifier: Modifier = Modifier
) {
    val currentFolder by viewModel.currentFolder.collectAsState()
    val foldersWithCounts by viewModel.foldersWithCounts.collectAsState()
    val audioTracks by viewModel.audioTracks.collectAsState()
    val videoTracks by viewModel.videoTracks.collectAsState()
    val currentTrack by viewModel.currentTrack.collectAsState()

    val dims = ResponsiveDimensions.dimensions

    if (currentFolder == null) {
        // Main Folders list view
        if (foldersWithCounts.isEmpty()) {
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .padding(dims.scaleDp(24.dp)),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "No Folders Registered",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = dims.scaleSp(14.sp)
                )
                Text(
                    text = "Add audio assets or video records on your micro-SD or internal downloads.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = dims.scaleSp(11.sp),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            return
        }

        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(dims.scaleDp(6.dp))
        ) {
            items(foldersWithCounts) { (folderPath, count) ->
                FolderCardItem(
                    folderPath = folderPath,
                    count = count,
                    onClick = { viewModel.enterFolder(folderPath) }
                )
            }
        }
    } else {
        // Internal drilling view inside folder
        val folderPath = currentFolder!!
        val folderFiles = remember(folderPath, audioTracks, videoTracks) {
            (audioTracks + videoTracks).filter { it.folderPath == folderPath }
        }

        Column(modifier = modifier.fillMaxSize()) {
            // Navigator header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = dims.scaleDp(8.dp)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.exitFolder() }) {
                    Icon(
                        imageVector = Icons.Filled.ArrowBack,
                        contentDescription = "Go back",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(dims.scaleDp(24.dp))
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = File(folderPath).name.ifBlank { "Root Directory" },
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = dims.scaleSp(13.sp)
                    )
                    Text(
                        text = folderPath,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = dims.scaleSp(10.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(dims.scaleDp(6.dp))
            ) {
                items(folderFiles) { track ->
                    val isCurrent = currentTrack?.id == track.id
                    if (track.isAudio) {
                        AudioItemRow(
                            track = track,
                            isCurrent = isCurrent,
                            onClick = { viewModel.playMediaItem(track, folderFiles) },
                            onFavoriteToggle = { viewModel.toggleFavorite(track) }
                        )
                    } else {
                        VideoItemRow(
                            video = track,
                            isCurrent = isCurrent,
                            onClick = { viewModel.playMediaItem(track, folderFiles) },
                            onFavoriteToggle = { viewModel.toggleFavorite(track) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FolderCardItem(
    folderPath: String,
    count: Int,
    onClick: () -> Unit
) {
    val dims = ResponsiveDimensions.dimensions
    val folderName = remember(folderPath) { File(folderPath).name.ifBlank { "System Memory" } }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(dims.scaleDp(8.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(dims.scaleDp(8.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Folder,
                contentDescription = "Folder logo",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(dims.scaleDp(26.dp))
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = dims.scaleDp(10.dp))
            ) {
                Text(
                    text = folderName,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = dims.scaleSp(12.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$count files • $folderPath",
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    fontSize = dims.scaleSp(9.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
