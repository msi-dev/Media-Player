package com.example.service

import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.MediaPlayerApp

@OptIn(UnstableApi::class)
class VideoPlayerService : MediaSessionService() {
    private val TAG = "VideoPlayerService"
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Starting foreground VideoPlayerService lifecycle service...")
        
        try {
            // Register modern notification channels
            NotificationService.createVideoNotificationChannel(this)

            val app = application as MediaPlayerApp
            val player = app.playbackManager.activeVideoPlayer ?: app.playbackManager.player

            // Dynamic session creation attached to active video ExoPlayer
            mediaSession = MediaSession.Builder(this, player)
                .setSessionActivity(getBackIntent())
                .build()
            
            val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main)
            
            player.addListener(object : androidx.media3.common.Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    updateNotification()
                }

                override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                    updateNotification()
                }
            })

            // Initial notification update
            updateNotification()
            
            Log.i(TAG, "MediaSession built and tied to video ExoPlayer session.")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting VideoPlayerService: ${e.message}")
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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action != null) {
            val app = application as MediaPlayerApp
            val activeVideoPlayer = app.playbackManager.activeVideoPlayer
            if (activeVideoPlayer != null) {
                when (action) {
                    "ACTION_PLAY" -> activeVideoPlayer.play()
                    "ACTION_PAUSE" -> activeVideoPlayer.pause()
                    "ACTION_NEXT" -> { /* No-op or handle video skip */ }
                    "ACTION_PREVIOUS" -> { /* No-op or handle video skip */ }
                }
            }
        }
        updateNotification()
        return super.onStartCommand(intent, flags, startId)
    }

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var updatePending = false
    private var lastUpdateTime = 0L
    private var isForeground = false

    private val updateRunnable = Runnable {
        updatePending = false
        performUpdateNotification()
    }

    private fun updateNotification() {
        val now = System.currentTimeMillis()
        val timeSinceLastUpdate = now - lastUpdateTime
        
        if (!isForeground || timeSinceLastUpdate >= 500L) {
            handler.removeCallbacks(updateRunnable)
            performUpdateNotification()
        } else {
            if (!updatePending) {
                updatePending = true
                handler.removeCallbacks(updateRunnable)
                handler.postDelayed(updateRunnable, 500L - timeSinceLastUpdate)
            }
        }
    }

    private fun performUpdateNotification() {
        lastUpdateTime = System.currentTimeMillis()
        val app = application as MediaPlayerApp
        val playbackManager = app.playbackManager
        val currentlyPlayingVideo = playbackManager.currentlyPlayingVideo.value
        val isVideoBgPlay = playbackManager.isVideoBackgroundPlayEnabled.value
        val isPlaying = playbackManager.activeVideoPlayer?.isPlaying ?: false
        val session = mediaSession

        val notification = if (currentlyPlayingVideo != null && isVideoBgPlay && session != null) {
            NotificationService.buildVideoMediaNotification(
                context = this,
                mediaSession = session,
                title = currentlyPlayingVideo.title,
                artist = currentlyPlayingVideo.artist,
                videoPath = currentlyPlayingVideo.path,
                isPlaying = isPlaying
            )
        } else {
            androidx.core.app.NotificationCompat.Builder(this, "video_playback_channel")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(currentlyPlayingVideo?.title ?: "Video Player")
                .setContentText(currentlyPlayingVideo?.artist ?: "Initializing background video...")
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_LOW)
                .setOngoing(isPlaying)
                .build()
        }

        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                startForeground(
                    NotificationService.NOTIFICATION_ID,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(NotificationService.NOTIFICATION_ID, notification)
            }
            isForeground = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed startForeground in VideoPlayerService: ${e.message}")
        }

        if (!isPlaying && !isVideoBgPlay) {
            val androidNotificationManager = getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            androidNotificationManager.notify(NotificationService.NOTIFICATION_ID, notification)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_DETACH)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(false)
            }
            isForeground = false
        }
    }

    override fun onDestroy() {
        Log.i(TAG, "Disposing VideoPlayerService background service...")
        handler.removeCallbacks(updateRunnable)
        mediaSession?.run {
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    override fun getAttributionTag(): String? {
        return "default"
    }
}
