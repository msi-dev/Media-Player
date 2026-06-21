package com.msi.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.msi.ui.layout.MiniPlayerLayout
import com.msi.ui.layout.MusicPlayerLayout
import com.msi.ui.theme.ResponsiveDimensions
import com.msi.ui.viewmodel.MediaViewModel

@Composable
fun MainScreen(
    viewModel: MediaViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val dims = ResponsiveDimensions.dimensions

    val selectedTab by viewModel.selectedTab.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isPlayerExpanded by viewModel.isPlayerExpanded.collectAsState()
    val isSidebarOpen by viewModel.isSidebarOpen.collectAsState()
    val currentTrack by viewModel.currentTrack.collectAsState()

    var isSearchFocused by remember { mutableStateOf(false) }

    // Media select pickers to manually add files
    val selectAudioLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val title = queryMediaNameAndImport(context, it)
            // Import and reload audio index
            viewModel.importMediaFile(
                title = title,
                path = it.toString(),
                duration = 210000L,
                isAudio = true,
                mimeType = "audio/mpeg"
            )
        }
    }

    val selectVideoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val title = queryMediaNameAndImport(context, it)
            // Import and reload video index
            viewModel.importMediaFile(
                title = title,
                path = it.toString(),
                duration = 145000L,
                isAudio = false,
                mimeType = "video/mp4"
            )
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            // Under navigation bars
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                modifier = Modifier.height(dims.scaleDp(60.dp)) // Slim navigation
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { viewModel.selectTab(0) },
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.MusicNote,
                            contentDescription = "Audio track lists",
                            modifier = Modifier.size(dims.scaleDp(18.dp))
                        )
                    },
                    label = { Text("Audio", fontSize = dims.scaleSp(10.sp)) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    )
                )

                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { viewModel.selectTab(1) },
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.Videocam,
                            contentDescription = "Video playlist tab",
                            modifier = Modifier.size(dims.scaleDp(18.dp))
                        )
                    },
                    label = { Text("Video", fontSize = dims.scaleSp(10.sp)) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    )
                )

                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { viewModel.selectTab(2) },
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.FolderOpen,
                            contentDescription = "Stored folder groups",
                            modifier = Modifier.size(dims.scaleDp(18.dp))
                        )
                    },
                    label = { Text("Folders", fontSize = dims.scaleSp(10.sp)) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    )
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = dims.scaleDp(12.dp))
            ) {
                // Header Search and Utilities Row - Highly polished, tight, responsive gaps
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = dims.scaleDp(12.dp), bottom = dims.scaleDp(12.dp)),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(dims.scaleDp(6.dp)) // reduced gap
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        placeholder = {
                            Text(
                                "Search track, video, folder...",
                                fontSize = dims.scaleSp(11.sp),
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Filled.Search,
                                contentDescription = "Lookup",
                                modifier = Modifier.size(dims.scaleDp(16.dp))
                            )
                        },
                        modifier = Modifier
                            .weight(1.3f) // High expansive width!
                            .height(dims.scaleDp(40.dp))
                            .onFocusChanged { isSearchFocused = it.isFocused },
                        shape = RoundedCornerShape(dims.scaleDp(20.dp)),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                            focusedLeadingIconColor = MaterialTheme.colorScheme.primary,
                            unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = dims.scaleSp(12.sp))
                    )

                    // Compact utility controllers packed tightly on the right side
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(dims.scaleDp(2.dp)), // minimized gap!
                        modifier = Modifier.padding(start = dims.scaleDp(2.dp))
                    ) {
                        // 1. Deck Queue list side-drawer Launcher
                        IconButton(
                            onClick = { viewModel.setSidebarOpen(true) },
                            modifier = Modifier.size(dims.scaleDp(36.dp))
                        ) {
                            Icon(
                                imageVector = Icons.Filled.QueueMusic,
                                contentDescription = "Active playlist queue",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(dims.scaleDp(20.dp))
                            )
                        }

                        // 2. Open Local picker dialog
                        var showImportMenu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(
                                onClick = { showImportMenu = true },
                                modifier = Modifier.size(dims.scaleDp(36.dp))
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.FolderOpen,
                                    contentDescription = "Manual Import",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(dims.scaleDp(20.dp))
                                )
                            }
                            DropdownMenu(
                                expanded = showImportMenu,
                                onDismissRequest = { showImportMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Import Audio File", fontSize = dims.scaleSp(11.sp)) },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Filled.Audiotrack,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    onClick = {
                                        showImportMenu = false
                                        selectAudioLauncher.launch("audio/*")
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Import Video File", fontSize = dims.scaleSp(11.sp)) },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Filled.Videocam,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    onClick = {
                                        showImportMenu = false
                                        selectVideoLauncher.launch("video/*")
                                    }
                                )
                            }
                        }

                        // 3. Force MediaStore Scanner
                        IconButton(
                            onClick = { viewModel.triggerScanner() },
                            modifier = Modifier.size(dims.scaleDp(36.dp))
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "Scanner sync files",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(dims.scaleDp(20.dp))
                            )
                        }
                    }
                }

                // Dynamic tabs viewer
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    when (selectedTab) {
                        0 -> AudioTab(viewModel = viewModel)
                        1 -> VideoTab(viewModel = viewModel)
                        2 -> FolderTab(viewModel = viewModel)
                    }
                }
            }

            // Floating mini player card above the navigation taskbar
            if (currentTrack != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = dims.scaleDp(8.dp), vertical = dims.scaleDp(8.dp))
                ) {
                    MiniPlayerLayout(
                        viewModel = viewModel,
                        onClick = { viewModel.setPlayerExpanded(true) }
                    )
                }
            }

            // High performance fullscreen controller screen
            AnimatedVisibility(
                visible = isPlayerExpanded,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier.fillMaxSize()
            ) {
                MusicPlayerLayout(viewModel = viewModel)
            }

            // Compact deck active playlist sidebar
            PlaylistSidebar(
                viewModel = viewModel,
                isOpen = isSidebarOpen,
                onClose = { viewModel.setSidebarOpen(false) }
            )
        }
    }
}

// Extract real title names from import URIs safely
private fun queryMediaNameAndImport(context: android.content.Context, uri: Uri): String {
    var result = "Local Core Media"
    if (uri.scheme == "content") {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) {
                    result = cursor.getString(nameIndex) ?: "Imported File"
                }
            }
        }
    }
    if (result.endsWith(".mp3") || result.endsWith(".mp4") || result.endsWith(".m4a")) {
        result = result.substringBeforeLast(".")
    }
    return result
}
