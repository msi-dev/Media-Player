package com.example.playback

import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.MediaPlayerApp

@OptIn(UnstableApi::class)
class MediaPlaybackService : MediaSessionService() {
    private val TAG = "MediaPlaybackService"
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Starting foreground MediaSession lifecycle service...")
        
        try {
            // Register modern notification channels
            NotificationManager.createNotificationChannel(this)

            val app = application as MediaPlayerApp
            val player = app.playbackManager.player

            // Dynamic session creation attached to active ExoPlayer
            mediaSession = MediaSession.Builder(this, player)
                .setSessionActivity(getBackIntent())
                .build()
            
            Log.i(TAG, "MediaSession built and tied to hardware ExoPlayer session.")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting MediaPlaybackService: ${e.message}")
        }
    }

    private fun getBackIntent(): PendingIntent {
        val intent = Intent(this, com.example.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        Log.i(TAG, "Disposing MediaSession background service...")
        mediaSession?.run {
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
