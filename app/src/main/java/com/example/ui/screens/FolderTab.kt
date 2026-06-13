package com.example.ui.screens

import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.MediaEntity
import com.example.ui.theme.DarkPrimary
import com.example.ui.viewmodel.MediaViewModel

@Composable
fun FolderTab(
    viewModel: MediaViewModel,
    modifier: Modifier = Modifier
) {
    val foldersMap by viewModel.folders.collectAsState()
    var expandedFolder by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    
    // Standard Android documents SAF selection launcher
    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
        onResult = { uris ->
            if (uris.isNotEmpty()) {
                val pickedEntities = uris.map { uri ->
                    var displayName = "Selected Device File"
                    try {
                        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                            val nameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (nameColumn != -1 && cursor.moveToFirst()) {
                                displayName = cursor.getString(nameColumn)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("FolderTab", "Failed query display metadata: ${e.message}")
                    }
                    
                    val pathString = uri.toString()
                    val lowerName = displayName.lowercase()
                    val isVideoFile = lowerName.endsWith(".mp4") || lowerName.endsWith(".mkv") || lowerName.endsWith(".avi") || lowerName.endsWith(".webm") || lowerName.endsWith(".mov") || lowerName.endsWith(".3gp")
                    
                    val parsedTitle = displayName.substringBeforeLast(".")
                    val formattedTitle = if (parsedTitle.isNotEmpty()) {
                        parsedTitle.take(1).uppercase() + parsedTitle.drop(1)
                    } else {
                        displayName
                    }
                    
                    MediaEntity(
                        path = pathString,
                        title = formattedTitle,
                        artist = "Local Device",
                        album = "External Imports",
                        duration = 0L,
                        isVideo = isVideoFile,
                        isFavorite = false,
                        lastPlayedPosition = 0L,
                        recentlyPlayed = System.currentTimeMillis()
                    )
                }
                
                // Load files as queue list and start/play
                if (pickedEntities.isNotEmpty()) {
                    viewModel.playSongAtIndex(pickedEntities, 0)
                }
            }
        }
    )

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Text(
            text = "Storage Directories",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        Text(
            text = "Browse physical files dynamically mapped by directory paths.",
            color = Color.Gray,
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // System Storage Explorer launcher action row
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 14.dp)
                .clickable {
                    try {
                        documentPickerLauncher.launch(arrayOf("audio/*", "video/*"))
                    } catch (e: Exception) {
                        Log.e("FolderTab", "Could not launch SAF: ${e.message}")
                    }
                },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, DarkPrimary.copy(alpha = 0.25f))
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(DarkPrimary, shape = RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.FileOpen, contentDescription = "File Picker", tint = Color.Black, modifier = Modifier.size(20.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "System Media Explorer",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "Select any audio or video file directly from storage",
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                }
                Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
            }
        }

        if (foldersMap.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Folder, contentDescription = null, modifier = Modifier.size(60.dp), tint = Color.Gray)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("No folders found with media content.", color = Color.Gray, fontSize = 14.sp)
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 90.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(foldersMap.keys.toList()) { folderName ->
                    val files = foldersMap[folderName] ?: emptyList()
                    val isExpanded = expandedFolder == folderName

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                expandedFolder = if (isExpanded) null else folderName
                            },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column {
                            // Folder Header Row
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        Icons.Filled.Folder,
                                        contentDescription = "Folder",
                                        tint = DarkPrimary,
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Column {
                                        Text(
                                            folderName,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            "${files.size} scanned elements",
                                            color = Color.Gray,
                                            fontSize = 12.sp
                                        )
                                    }
                                }

                                Icon(
                                    if (isExpanded) Icons.Filled.ExpandMore else Icons.Filled.ChevronRight,
                                    contentDescription = null,
                                    tint = Color.Gray
                                )
                            }

                            // Dynamic expanded file list
                            AnimatedVisibility(
                                visible = isExpanded,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut()
                            ) {
                                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                                Column(
                                    modifier = Modifier
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
                                        .padding(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    files.forEachIndexed { fIdx, file ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable {
                                                    viewModel.playSongAtIndex(files, fIdx)
                                                }
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(
                                                    if (file.isVideo) Icons.Filled.VideoFile else Icons.Filled.MusicNote,
                                                    contentDescription = null,
                                                    tint = if (file.isVideo) Color(0xFF00E5FF) else DarkPrimary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Text(
                                                    file.title,
                                                    color = Color.White,
                                                    fontSize = 13.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }

                                            Icon(
                                                Icons.Filled.PlayCircle,
                                                contentDescription = "Play",
                                                tint = DarkPrimary,
                                                modifier = Modifier.size(20.dp)
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
