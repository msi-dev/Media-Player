package com.example.ui.screens

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.VideoPlaybackActivity
import com.example.data.db.MediaEntity
import com.example.ui.adapters.BrowserItem
import com.example.ui.adapters.FolderBrowserAdapter
import com.example.ui.components.SkeletonListLoader
import com.example.ui.theme.DarkPrimary
import com.example.ui.viewmodel.FolderBrowserViewModel
import com.example.ui.viewmodel.FolderBrowserViewModelFactory
import com.example.ui.viewmodel.MediaViewModel

@Composable
fun FolderTab(
    viewModel: MediaViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val app = context.applicationContext as Application
    
    // Initialize our decoupled lightweight ViewModel per screen requirement
    val browserViewModel: FolderBrowserViewModel = viewModel(
        factory = FolderBrowserViewModelFactory(app)
    )

    val currentFolder by browserViewModel.currentFolder.collectAsState()
    val folders by browserViewModel.folders.collectAsState()
    val folderItems by browserViewModel.folderItems.collectAsState()
    val isLoadingFolders by browserViewModel.isLoadingFolders.collectAsState()
    val isLoadingItems by browserViewModel.isLoadingItems.collectAsState()
    val isAllItemsLoaded by browserViewModel.isAllItemsLoaded.collectAsState()

    // Back button pops folder view before exiting
    BackHandler(enabled = currentFolder != null) {
        browserViewModel.navigateBack()
    }

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
                
                if (pickedEntities.isNotEmpty()) {
                    viewModel.playSongAtIndex(pickedEntities, 0)
                }
            }
        }
    )

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Text(
            text = "Storage Directories",
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        Text(
            text = "Highly optimized folder explorer utilizing native scrolling.",
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
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
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

        // Animated breadcrumb header bar inside directory browser
        AnimatedVisibility(
            visible = currentFolder != null,
            enter = slideInVertically() + fadeIn(),
            exit = slideOutVertically() + fadeOut()
        ) {
            currentFolder?.let { folder ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .clickable {
                            browserViewModel.navigateBack()
                        },
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Filled.ChevronLeft,
                            contentDescription = "Back",
                            tint = DarkPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Icon(
                            Icons.Filled.FolderOpen,
                            contentDescription = null,
                            tint = DarkPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = folder.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "Back to list",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                }
            }
        }

        // Main List Content View Block
        Box(modifier = Modifier.weight(1f)) {
            val showFolders = currentFolder == null

            AnimatedContent(
                targetState = showFolders,
                transitionSpec = {
                    if (!targetState) {
                        // Forward transition: entering folder (folders -> items)
                        (slideInHorizontally(initialOffsetX = { it }) + fadeIn(animationSpec = androidx.compose.animation.core.tween(300)))
                            .togetherWith(slideOutHorizontally(targetOffsetX = { -it / 3 }) + fadeOut(animationSpec = androidx.compose.animation.core.tween(150)))
                    } else {
                        // Backward transition: returning to folders (items -> folders)
                        (slideInHorizontally(initialOffsetX = { -it / 3 }) + fadeIn(animationSpec = androidx.compose.animation.core.tween(300)))
                            .togetherWith(slideOutHorizontally(targetOffsetX = { it }) + fadeOut(animationSpec = androidx.compose.animation.core.tween(150)))
                    }
                },
                label = "FolderTransition",
                modifier = Modifier.fillMaxSize()
            ) { targetShowFolders ->
                if (targetShowFolders && isLoadingFolders && folders.isEmpty()) {
                    SkeletonListLoader()
                } else if (!targetShowFolders && isLoadingItems && folderItems.isEmpty()) {
                    SkeletonListLoader()
                } else if (targetShowFolders && folders.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No media folders detected.", color = Color.Gray)
                    }
                } else {
                    // High-performance programmatic RecyclerView bridging Compose ViewPort
                    AndroidView<RecyclerView>(
                        modifier = Modifier.fillMaxSize(),
                        factory = { viewContext ->
                            val recyclerView = RecyclerView(viewContext).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                layoutManager = LinearLayoutManager(viewContext)
                                setHasFixedSize(true)
                            }

                            val adapter = FolderBrowserAdapter(
                                context = viewContext,
                                onFolderClicked = { folder ->
                                    browserViewModel.enterFolder(folder)
                                },
                                onMediaClicked = { media, pList, index ->
                                    if (media.isVideo) {
                                        // Handle Video start safely pausing audio to avoid interlocks
                                        viewModel.pause()
                                        viewModel.setCurrentlyPlayingVideo(media)
                                        val intent = Intent(viewContext, VideoPlaybackActivity::class.java).apply {
                                            putExtra("extra_media_path", media.path)
                                            putExtra("extra_media_title", media.title)
                                        }
                                        viewContext.startActivity(intent)
                                    } else {
                                        // Handle background audio PlaybackService queue load
                                        viewModel.playSongAtIndex(pList, index)
                                    }
                                }
                            )
                            recyclerView.adapter = adapter

                            // Add infinite scroll pagination watcher
                            recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                                override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                                    super.onScrolled(rv, dx, dy)
                                    if (targetShowFolders) return
                                    val lm = rv.layoutManager as LinearLayoutManager
                                    val totalItems = lm?.itemCount ?: 0
                                    val lastItem = lm?.findLastVisibleItemPosition() ?: 0
                                    if (totalItems <= lastItem + 6) {
                                        browserViewModel.loadNextPageOfItems()
                                    }
                                }
                            })

                            recyclerView
                        },
                        update = { recyclerView ->
                            val adapter = recyclerView.adapter as? FolderBrowserAdapter ?: return@AndroidView
                            val items = if (targetShowFolders) {
                                folders.map { BrowserItem.FolderItem(it) }
                            } else {
                                folderItems.map { BrowserItem.MediaFileItem(it) }
                            }
                            adapter.submitList(items)
                        }
                    )
                }
            }
        }
    }
}
