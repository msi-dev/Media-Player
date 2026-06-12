package com.example.mediaplayer

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import kotlin.OptIn
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import com.example.mediaplayer.ui.theme.MediaPlayerTheme
import com.example.mediaplayer.ui.screens.*

class MainActivity : ComponentActivity() {

    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MediaPlayerTheme {
                val mediaViewModel: MediaViewModel = viewModel()

                // Trigger runtime permissions on launch
                val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    arrayOf(
                        Manifest.permission.READ_MEDIA_AUDIO,
                        Manifest.permission.READ_MEDIA_VIDEO
                    )
                } else {
                    arrayOf(
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    )
                }

                val launcher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { result ->
                    // Done - repository automatically syncs / scans and handles falls back details
                }

                LaunchedEffect(Unit) {
                    launcher.launch(permissionsToRequest)
                }

                MainLayout(viewModel = mediaViewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainLayout(viewModel: MediaViewModel) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var selectedScreenIndex by remember { mutableIntStateOf(0) }

    var showEqualizer by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }

    val navigationItems = listOf(
        NavigationTabItem("Audio", Icons.Default.MusicNote),
        NavigationTabItem("Video", Icons.Default.Videocam)
    )

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(300.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(24.dp)
                ) {
                    Column {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Acoustic Media Hub",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Precision Tuning & Playback",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.MusicNote, contentDescription = null) },
                    label = { Text("Audio Space") },
                    selected = selectedScreenIndex == 0,
                    onClick = {
                        selectedScreenIndex = 0
                        scope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )

                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Videocam, contentDescription = null) },
                    label = { Text("Video Space") },
                    selected = selectedScreenIndex == 1,
                    onClick = {
                        selectedScreenIndex = 1
                        scope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )

                Divider(modifier = Modifier.padding(vertical = 8.dp, horizontal = 12.dp))

                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.GraphicEq, contentDescription = null) },
                    label = { Text("Acoustic Tuning / EQ") },
                    selected = false,
                    onClick = {
                        showEqualizer = true
                        scope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )

                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.History, contentDescription = null) },
                    label = { Text("Playback History") },
                    selected = false,
                    onClick = {
                        showHistory = true
                        scope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
        }
    ) {
        Scaffold(
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                ) {
                    navigationItems.forEachIndexed { index, item ->
                        NavigationBarItem(
                            selected = selectedScreenIndex == index,
                            onClick = { selectedScreenIndex = index },
                            icon = { Icon(imageVector = item.icon, contentDescription = item.label) },
                            label = { Text(text = item.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                    }
                }
            }
        ) { innerPadding ->
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .statusBarsPadding(),
                color = MaterialTheme.colorScheme.background
            ) {
                when (selectedScreenIndex) {
                    0 -> AudioScreen(
                        viewModel = viewModel,
                        onMenuClick = {
                            scope.launch { drawerState.open() }
                        }
                    )
                    1 -> VideoScreen(
                        viewModel = viewModel,
                        onMenuClick = {
                            scope.launch { drawerState.open() }
                        }
                    )
                }
            }
        }
    }

    if (showEqualizer) {
        AlertDialog(
            onDismissRequest = { showEqualizer = false },
            confirmButton = {
                TextButton(onClick = { showEqualizer = false }) {
                    Text("Done")
                }
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.GraphicEq, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Acoustic Tuning")
                }
            },
            text = {
                Box(modifier = Modifier.height(420.dp)) {
                    EqualizerScreen(viewModel = viewModel)
                }
            }
        )
    }

    if (showHistory) {
        AlertDialog(
            onDismissRequest = { showHistory = false },
            confirmButton = {
                TextButton(onClick = { showHistory = false }) {
                    Text("Done")
                }
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Playback History")
                }
            },
            text = {
                Box(modifier = Modifier.height(420.dp)) {
                    HistoryScreen(
                        viewModel = viewModel,
                        onSongClick = { song ->
                            viewModel.playSong(song, listOf(song))
                        }
                    )
                }
            }
        )
    }
}

data class NavigationTabItem(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)
