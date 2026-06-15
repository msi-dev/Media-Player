package com.example

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import com.example.playback.MediaPlaybackService
import com.example.ui.screens.MainScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.ProvideResponsiveDimensions
import com.example.ui.viewmodel.MediaViewModel
import com.example.ui.viewmodel.MediaViewModelFactory

class MainActivity : ComponentActivity() {
    private val TAG = "MainActivity"
    private lateinit var viewModel: MediaViewModel

    // Adaptive Modern Runtime Permissions Checker
    private val mediaPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val audioGranted = permissions[Manifest.permission.READ_EXTERNAL_STORAGE] == true ||
                permissions[Manifest.permission.READ_MEDIA_AUDIO] == true
        val videoGranted = permissions[Manifest.permission.READ_MEDIA_VIDEO] == true
        val recordAudioGranted = permissions[Manifest.permission.RECORD_AUDIO] == true
        val notifGranted = permissions[Manifest.permission.POST_NOTIFICATIONS] == true

        Log.i(TAG, "Storage privileges updated: Audio Granted = $audioGranted, Video Granted = $videoGranted, Mic/Record = $recordAudioGranted, Notification Granted = $notifGranted")
        // Force scan database files following privileges updates
        viewModel.forceScanMedia()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        Log.i(TAG, "Bootstrapping components...")

        // 1. Loading MVVM Repository-injected ViewModel Factory
        val factory = MediaViewModelFactory(application)
        viewModel = ViewModelProvider(this, factory)[MediaViewModel::class.java]

        // 2. Trigger active Media3 Background Service intents
        try {
            val serviceIntent = Intent(this, MediaPlaybackService::class.java)
            startService(serviceIntent)
            Log.i(TAG, "ExoPlayer MediaPlaybackService command queued.")
        } catch (e: Exception) {
            Log.e(TAG, "Non-critical error starting background service: ${e.message}")
        }

        // 3. Request Storage Media files rights
        launchPermissionRequest()

        // 4. Handle incoming intent (from other apps)
        processIncomingIntent(intent)

        setContent {
            // Collect theme configurations dynamically from state configuration
            val isDarkThemePref by viewModel.isDarkTheme.collectAsState()
            val forceDark = when (isDarkThemePref) {
                true -> true
                false -> false
                null -> isSystemInDarkTheme() // Sync standard system settings
            }

            @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
            val windowSizeClass = calculateWindowSizeClass(this)

            MyApplicationTheme(darkTheme = forceDark) {
                ProvideResponsiveDimensions {
                    MainScreen(viewModel = viewModel, windowSizeClass = windowSizeClass)
                }
            }
        }
    }

    private fun launchPermissionRequest() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            mediaPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_MEDIA_AUDIO,
                    Manifest.permission.READ_MEDIA_VIDEO,
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.POST_NOTIFICATIONS
                )
            )
        } else {
            mediaPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.RECORD_AUDIO
                )
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        processIncomingIntent(intent)
    }

    private fun processIncomingIntent(intent: Intent?) {
        if (intent == null) return
        try {
            val intentManager = com.example.playback.IntentManager(this)
            val media = intentManager.handleIncomingIntent(intent)
            if (media != null) {
                Log.i(TAG, "Processed external intent for playback: ${media.title}")
                viewModel.playMediaDirectly(media)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling incoming intent: ${e.message}")
        }
    }

    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        when (keyCode) {
            android.view.KeyEvent.KEYCODE_SPACE -> {
                if (viewModel.isPlaying.value) {
                    viewModel.pause()
                } else {
                    viewModel.play()
                }
                return true
            }
            android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                val target = (viewModel.currentPosition.value - 5000L).coerceAtLeast(0L)
                viewModel.seekTo(target)
                return true
            }
            android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                val durationVal = viewModel.duration.value
                val target = (viewModel.currentPosition.value + 5000L).coerceAtMost(if (durationVal > 0) durationVal else 1000000L)
                viewModel.seekTo(target)
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun getAttributionTag(): String? {
        return "default"
    }
}
