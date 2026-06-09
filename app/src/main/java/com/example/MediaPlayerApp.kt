package com.example

import android.app.Application
import android.util.Log
import com.example.data.db.MediaDatabase
import com.example.data.repository.MediaRepository
import com.example.data.repository.HiddenFolderRepository
import com.example.playback.PlaybackManager

class MediaPlayerApp : Application() {
    private val TAG = "MediaPlayerApp"

    // Core singletons for Clean Architecture MVVM DI container
    lateinit var database: MediaDatabase
        private set

    lateinit var mediaRepository: MediaRepository
        private set

    lateinit var hiddenFolderRepository: HiddenFolderRepository
        private set

    lateinit var playbackManager: PlaybackManager
        private set

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Initializing Media Player Application container...")
        
        // 1. Initializing Room database instance
        database = MediaDatabase.getDatabase(this)
        
        // 2b. Initializing Hidden Folders repository
        hiddenFolderRepository = HiddenFolderRepository(database.hiddenFolderDao())

        // 2. Initializing Media content and database scanner repository
        mediaRepository = MediaRepository(this, database.mediaDao(), database.hiddenFolderDao())
        
        // 3. Initializing central Media3 ExoPlayer state manager
        playbackManager = PlaybackManager(this)
        
        Log.i(TAG, "All enterprise applet systems ready.")
    }

    override fun onTerminate() {
        super.onTerminate()
        playbackManager.release()
    }
}
