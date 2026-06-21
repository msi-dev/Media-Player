package com.msi

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import com.msi.data.db.MediaEntity
import com.msi.playback.IntentManager
import com.msi.ui.layout.FullscreenPlayerSheet
import com.msi.ui.theme.MyApplicationTheme
import com.msi.ui.theme.ProvideResponsiveDimensions
import com.msi.ui.viewmodel.MediaViewModel
import com.msi.ui.viewmodel.MediaViewModelFactory

class AudioPlaybackActivity : ComponentActivity() {
    private val TAG = "AudioPlaybackActivity"
    private lateinit var viewModel: MediaViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val factory = MediaViewModelFactory(application)
        viewModel = ViewModelProvider(this, factory)[MediaViewModel::class.java]

        handleIntent(intent)

        setContent {
            val isDarkThemePref by viewModel.isDarkTheme.collectAsState()
            val forceDark = when (isDarkThemePref) {
                true -> true
                false -> false
                null -> isSystemInDarkTheme()
            }
            val dynamicColorEnabled by viewModel.dynamicColorEnabled.collectAsState()

            @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
            val windowSizeClass = calculateWindowSizeClass(this)

            MyApplicationTheme(
                darkTheme = forceDark,
                dynamicColor = dynamicColorEnabled
            ) {
                ProvideResponsiveDimensions {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        val currentSong by viewModel.currentSong.collectAsState()
                        val isPlaying by viewModel.isPlaying.collectAsState()

                        currentSong?.let { song ->
                            FullscreenPlayerSheet(
                                song = song,
                                isPlaying = isPlaying,
                                viewModel = viewModel,
                                windowSizeClass = windowSizeClass,
                                onCollapse = { finish() }
                            )
                        } ?: run {
                            val path = intent.getStringExtra("extra_media_path")
                            val title = intent.getStringExtra("extra_media_title") ?: "External Audio"
                            val artist = intent.getStringExtra("extra_media_artist") ?: "External Artist"
                            val album = intent.getStringExtra("extra_media_album") ?: "External Album"
                            
                            if (path != null) {
                                val fallbackSong = MediaEntity(
                                    path = path,
                                    title = title,
                                    artist = artist,
                                    album = album,
                                    duration = 0L,
                                    size = 0L,
                                    genre = "External",
                                    isVideo = false,
                                    isFavorite = false,
                                    lastPlayedPosition = 0L
                                )
                                FullscreenPlayerSheet(
                                    song = fallbackSong,
                                    isPlaying = isPlaying,
                                    viewModel = viewModel,
                                    windowSizeClass = windowSizeClass,
                                    onCollapse = { finish() }
                                )
                            } else {
                                finish()
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val path = intent.getStringExtra("extra_media_path") ?: intent.data?.toString()
        if (path != null) {
            val title = intent.getStringExtra("extra_media_title") ?: intent.data?.lastPathSegment ?: "External Audio"
            val artist = intent.getStringExtra("extra_media_artist") ?: "External"
            val album = intent.getStringExtra("extra_media_album") ?: "Streaming"

            val media = MediaEntity(
                path = path,
                title = title,
                duration = 0L,
                size = 0L,
                album = album,
                artist = artist,
                genre = "Unknown Genre",
                isVideo = false,
                isFavorite = false,
                lastPlayedPosition = 0L
            )
            Log.i(TAG, "AudioPlaybackActivity playing track: ${media.title}")
            viewModel.playMediaDirectly(media)
        }
    }

    override fun getAttributionTag(): String? {
        return "default"
    }
}
