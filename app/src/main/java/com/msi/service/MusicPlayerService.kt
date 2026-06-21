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
class MusicPlayerService : MediaSessionService() {
    private val TAG = "MusicPlayerService"
    private var mediaSession: MediaSession? = null
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var isForeground = false
    private var pausedTimeoutRunnable: Runnable? = null

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Starting foreground MusicPlayerService lifecycle service...")
        
        try {
            // Register modern notification channels immediately in onCreate
            NotificationService.createNotificationChannel(this)

            val app = application as MediaPlayerApp
            val player = app.playbackManager.player

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

            // Dynamic session creation attached to active ExoPlayer and callback
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
            
            Log.i(TAG, "MediaSession built and tied to hardware ExoPlayer session.")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting MusicPlayerService: ${e.message}")
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
            val playbackManager = app.playbackManager
            when (action) {
                "ACTION_PLAY" -> playbackManager.play()
                "ACTION_PAUSE" -> playbackManager.pause()
                "ACTION_NEXT" -> playbackManager.playNext()
                "ACTION_PREVIOUS" -> playbackManager.playPrevious()
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
        val currentSong = playbackManager.currentSong.value
        val isPlaying = playbackManager.isPlaying.value

        val notification = if (currentSong != null) {
            NotificationService.buildMediaNotification(
                context = this,
                mediaSession = session,
                title = currentSong.title,
                artist = currentSong.artist,
                largeIcon = null,
                isPlaying = isPlaying
            )
        } else {
            androidx.core.app.NotificationCompat.Builder(this, NotificationService.CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle("Music Player")
                .setContentText("Initializing playback...")
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_LOW)
                .setOngoing(isPlaying)
                .build()
        }

        try {
            if (isPlaying) {
                cancelPausedTimeout()
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    startForeground(
                        NotificationService.MUSIC_NOTIFICATION_ID,
                        notification,
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                    )
                } else {
                    startForeground(NotificationService.MUSIC_NOTIFICATION_ID, notification)
                }
                isForeground = true
            } else {
                if (!isForeground) {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        startForeground(
                            NotificationService.MUSIC_NOTIFICATION_ID,
                            notification,
                            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                        )
                    } else {
                        startForeground(NotificationService.MUSIC_NOTIFICATION_ID, notification)
                    }
                    isForeground = true
                } else {
                    val androidNotificationManager = getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                    androidNotificationManager.notify(NotificationService.MUSIC_NOTIFICATION_ID, notification)
                }
                startPausedTimeout()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed startForeground or notify in MusicPlayerService: ${e.message}")
        }
    }

    private fun startPausedTimeout() {
        cancelPausedTimeout()
        pausedTimeoutRunnable = Runnable {
            Log.i(TAG, "Paused timeout reached. Removing foreground service state.")
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
        Log.i(TAG, "Disposing MusicPlayerService background service...")
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
