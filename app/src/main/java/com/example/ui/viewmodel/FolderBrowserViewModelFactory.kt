package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.MediaPlayerApp

class FolderBrowserViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FolderBrowserViewModel::class.java)) {
            val app = application as MediaPlayerApp
            @Suppress("UNCHECKED_CAST")
            return FolderBrowserViewModel(
                application,
                app.mediaRepository
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
