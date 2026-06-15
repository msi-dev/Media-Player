package com.example

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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import com.example.data.db.MediaEntity
import com.example.ui.screens.VideoPlayerScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.ProvideResponsiveDimensions
import com.example.ui.viewmodel.MediaViewModel
import com.example.ui.viewmodel.MediaViewModelFactory

class VideoPlaybackActivity : ComponentActivity() {
    private val TAG = "VideoPlaybackActivity"
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

            MyApplicationTheme(darkTheme = forceDark) {
                ProvideResponsiveDimensions {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        val currentlyPlayingVideo by viewModel.currentlyPlayingVideo.collectAsState()
                        
                        currentlyPlayingVideo?.let { video ->
                            VideoPlayerScreen(
                                video = video,
                                viewModel = viewModel,
                                onClose = {
                                    viewModel.setCurrentlyPlayingVideo(null)
                                    finish()
                                }
                            )
                        } ?: run {
                            val path = intent.getStringExtra("extra_media_path")
                            if (path != null) {
                                val title = intent.getStringExtra("extra_media_title") ?: "External Video"
                                val artist = intent.getStringExtra("extra_media_artist") ?: "External"
                                val album = intent.getStringExtra("extra_media_album") ?: "Streaming"
                                val fallbackVideo = MediaEntity(
                                    path = path,
                                    title = title,
                                    artist = artist,
                                    album = album,
                                    duration = 0L,
                                    size = 0L,
                                    genre = "External",
                                    isVideo = true,
                                    isFavorite = false,
                                    lastPlayedPosition = 0L
                                )
                                VideoPlayerScreen(
                                    video = fallbackVideo,
                                    viewModel = viewModel,
                                    onClose = { finish() }
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

    override fun onDestroy() {
        super.onDestroy()
        viewModel.setCurrentlyPlayingVideo(null)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val path = intent.getStringExtra("extra_media_path") ?: intent.data?.toString()
        if (path != null) {
            val title = intent.getStringExtra("extra_media_title") ?: intent.data?.lastPathSegment ?: "External Video"
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
                isVideo = true,
                isFavorite = false,
                lastPlayedPosition = 0L
            )
            Log.i(TAG, "VideoPlaybackActivity playing track: ${media.title}")
            viewModel.playMediaDirectly(media)
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
