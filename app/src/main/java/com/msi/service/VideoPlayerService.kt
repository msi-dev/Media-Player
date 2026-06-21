package com.msi.service

import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.msi.MediaPlayerApp

@OptIn(UnstableApi::class)
class VideoPlayerService : MediaSessionService() {
    private val TAG = "VideoPlayerService"
    private var mediaSession: MediaSession? = null
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var isForeground = false
    private var pausedTimeoutRunnable: Runnable? = null

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Starting foreground VideoPlayerService lifecycle service...")
        
        try {
            // Register modern notification channels
            NotificationService.createVideoNotificationChannel(this)

            val app = application as MediaPlayerApp
            val player = app.playbackManager.activeVideoPlayer ?: app.playbackManager.player

            val sessionCallback = object : MediaSession.Callback {
                override fun onConnect(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo
                ): MediaSession.ConnectionResult {
                    return MediaSession.ConnectionResult.accept(
                        MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS,
                        MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS
                    )
                }
            }

            // Dynamic session creation attached to active video ExoPlayer
            mediaSession = MediaSession.Builder(this, player)
                .setSessionActivity(getBackIntent())
                .setCallback(sessionCallback)
                .build()
            
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
        val intent = Intent(this, com.msi.MainActivity::class.java).apply {
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
                    "ACTION_NEXT" -> Log.i(TAG, "ACTION_NEXT triggered for video player (supported via UI skipping)")
                    "ACTION_PREVIOUS" -> Log.i(TAG, "ACTION_PREVIOUS triggered for video player (supported via UI skipping)")
                }
            } else {
                Log.w(TAG, "onStartCommand action received but activeVideoPlayer is null.")
            }
        }
        updateNotification()
        return super.onStartCommand(intent, flags, startId)
    }

    private fun updateNotification() {
        mediaSession?.let { session ->
            onUpdateNotification(session, startForegroundRequired = true)
        }
    }

    override fun onUpdateNotification(session: MediaSession, startForegroundRequired: Boolean) {
        val app = application as MediaPlayerApp
        val playbackManager = app.playbackManager
        val currentlyPlayingVideo = playbackManager.currentlyPlayingVideo.value
        val isVideoBgPlay = playbackManager.isVideoBackgroundPlayEnabled.value
        val isPlaying = playbackManager.activeVideoPlayer?.isPlaying ?: false

        val notification = if (currentlyPlayingVideo != null && isVideoBgPlay) {
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
            if (isPlaying && isVideoBgPlay) {
                cancelPausedTimeout()
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    startForeground(
                        NotificationService.VIDEO_NOTIFICATION_ID,
                        notification,
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                    )
                } else {
                    startForeground(NotificationService.VIDEO_NOTIFICATION_ID, notification)
                }
                isForeground = true
            } else {
                if (!isForeground) {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        startForeground(
                            NotificationService.VIDEO_NOTIFICATION_ID,
                            notification,
                            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                        )
                    } else {
                        startForeground(NotificationService.VIDEO_NOTIFICATION_ID, notification)
                    }
                    isForeground = true
                } else {
                    val androidNotificationManager = getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                    androidNotificationManager.notify(NotificationService.VIDEO_NOTIFICATION_ID, notification)
                }
                startPausedTimeout()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed startForeground or notify in VideoPlayerService: ${e.message}")
        }
    }

    private fun startPausedTimeout() {
        cancelPausedTimeout()
        pausedTimeoutRunnable = Runnable {
            Log.i(TAG, "Paused timeout reached. Removing foreground service state for video.")
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_DETACH)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(false)
            }
            isForeground = false
        }
        handler.postDelayed(pausedTimeoutRunnable!!, 300000L) // 5 minutes grace period
    }

    private fun cancelPausedTimeout() {
        pausedTimeoutRunnable?.let {
            handler.removeCallbacks(it)
            pausedTimeoutRunnable = null
        }
    }

    override fun onDestroy() {
        Log.i(TAG, "Disposing VideoPlayerService background service...")
        cancelPausedTimeout()
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
