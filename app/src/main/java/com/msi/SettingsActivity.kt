package com.msi

import android.os.Bundle
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
import com.msi.ui.layout.SettingsLayout
import com.msi.ui.theme.MyApplicationTheme
import com.msi.ui.theme.ProvideResponsiveDimensions
import com.msi.ui.viewmodel.MediaViewModel
import com.msi.ui.viewmodel.MediaViewModelFactory

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val factory = MediaViewModelFactory(application)
        val viewModel = ViewModelProvider(this, factory)[MediaViewModel::class.java]

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
                        SettingsLayout(
                            viewModel = viewModel,
                            onBack = { finish() }
                        )
                    }
                }
            }
        }
    }

    override fun getAttributionTag(): String? {
        return "default"
    }
}
