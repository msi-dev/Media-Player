package com.msi

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewmodel.compose.viewModel
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.msi.ui.screens.MainScreen
import com.msi.ui.theme.MediaPlayerTheme
import com.msi.ui.theme.ProvideResponsiveDimensions
import com.msi.ui.viewmodel.MediaViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            // Requesting files & playback permissions nicely at launch
            val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                listOf(
                    Manifest.permission.READ_MEDIA_AUDIO,
                    Manifest.permission.READ_MEDIA_VIDEO,
                    Manifest.permission.POST_NOTIFICATIONS
                )
            } else {
                listOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE
                )
            }

            val viewModel: MediaViewModel = viewModel()

            LaunchedEffect(Unit) {
                // Background scan files initially scanned or trigger dummy preloads
                viewModel.triggerScanner()
            }

            ProvideResponsiveDimensions {
                MediaPlayerTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = androidx.compose.material3.MaterialTheme.colorScheme.background
                    ) {
                        MainScreen(viewModel = viewModel)
                    }
                }
            }
        }
    }
}
