package com.example.mediaplayer

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.mediaplayer.data.SongEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MusicService : Service() {

    private val binder = MusicBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var notificationManager: AudioNotificationManager

    companion object {
        const val ACTION_PLAY = "com.example.mediaplayer.action.PLAY"
        const val ACTION_PAUSE = "com.example.mediaplayer.action.PAUSE"
        const val ACTION_TOGGLE = "com.example.mediaplayer.action.TOGGLE"
        const val ACTION_NEXT = "com.example.mediaplayer.action.NEXT"
        const val ACTION_PREVIOUS = "com.example.mediaplayer.action.PREVIOUS"
        const val ACTION_CLOSE = "com.example.mediaplayer.action.CLOSE"

        // Expose a globally accessible ExoPlayer instance
        var playerInstance: ExoPlayer? = null
        val currentPlayingSong = MutableStateFlow<SongEntity?>(null)
        val isPlaying = MutableStateFlow(false)
        var mediaSession: androidx.media3.session.MediaSession? = null
        
        var playlistQueue = listOf<SongEntity>()

        // Synchronized methods to trigger play/pause from UI or service
        fun playTrack(song: SongEntity, queue: List<SongEntity>, context: Context) {
            val player = playerInstance ?: return
            
            // Check if user clicked on the ALREADY PLAYING song!
            if (currentPlayingSong.value?.id == song.id) {
                // If it is already playing, or paused, just keep/resume playing! Do NOT restart from zero!
                if (!player.isPlaying) {
                    player.play()
                }
                return
            }

            playlistQueue = queue
            currentPlayingSong.value = song

            player.stop()
            player.clearMediaItems()

            val activeQueue = queue
            val idx = activeQueue.indexOfFirst { it.id == song.id }
            val orderedQueue = if (idx >= 0) {
                activeQueue.subList(idx, activeQueue.size) + activeQueue.subList(0, idx)
            } else {
                activeQueue
            }

            orderedQueue.forEach { item ->
                player.addMediaItem(
                    MediaItem.Builder()
                        .setMediaId(item.id)
                        .setUri(item.id)
                        .build()
                )
            }

            player.prepare()
            player.play()

            // Start foreground service via Intent
            val playServiceIntent = Intent(context, MusicService::class.java).apply {
                action = ACTION_PLAY
            }
            context.startService(playServiceIntent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        initPlayer()
        notificationManager = AudioNotificationManager(this)
    }

    private fun initPlayer() {
        if (playerInstance != null) return

        val context = applicationContext

        val player = ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_OFF
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_ENDED) {
                        MusicService.isPlaying.value = false
                        stopForegroundService(false)
                    }
                }

                override fun onIsPlayingChanged(playing: Boolean) {
                    MusicService.isPlaying.value = playing
                    if (playing) {
                        startForegroundService()
                    } else {
                        // Pause state: refresh notification but remove foreground priority to let it be swipable
                        stopForegroundService(false)
                    }
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    mediaItem?.let { item ->
                        val matchedSong = playlistQueue.find { it.id == item.mediaId }
                        if (matchedSong != null) {
                            MusicService.currentPlayingSong.value = matchedSong
                            updateNotification()
                        }
                    }
                }
            })
        }
        playerInstance = player
        mediaSession = androidx.media3.session.MediaSession.Builder(context, player).build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.action?.let { action ->
            when (action) {
                ACTION_PLAY -> resumePlayback()
                ACTION_PAUSE -> pausePlayback()
                ACTION_TOGGLE -> togglePlayPause()
                ACTION_NEXT -> playNext()
                ACTION_PREVIOUS -> playPrevious()
                ACTION_CLOSE -> stopForegroundService(true)
            }
        }
        return START_NOT_STICKY
    }

    private fun resumePlayback() {
        playerInstance?.play()
        startForegroundService()
    }

    private fun pausePlayback() {
        playerInstance?.pause()
        stopForegroundService(false)
    }

    private fun togglePlayPause() {
        playerInstance?.let {
            if (it.isPlaying) {
                pausePlayback()
            } else {
                resumePlayback()
            }
        }
    }

    private fun playNext() {
        playerInstance?.let { player ->
            if (player.hasNextMediaItem()) {
                player.seekToNextMediaItem()
            } else {
                player.seekToDefaultPosition(0)
            }
        }
        updateNotification()
    }

    private fun playPrevious() {
        playerInstance?.let { player ->
            if (player.hasPreviousMediaItem()) {
                player.seekToPreviousMediaItem()
            } else {
                player.seekToDefaultPosition(player.mediaItemCount - 1)
            }
        }
        updateNotification()
    }

    private fun startForegroundService() {
        val song = MusicService.currentPlayingSong.value ?: return
        val notification = notificationManager.buildNotification(song, true)
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            startForeground(101, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(101, notification)
        }
    }

    private fun updateNotification() {
        val song = MusicService.currentPlayingSong.value ?: return
        val activePlaying = MusicService.isPlaying.value
        val notification = notificationManager.buildNotification(song, activePlaying)
        notificationManager.showNotification(notification)
    }

    private fun stopForegroundService(killAll: Boolean) {
        if (killAll) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            notificationManager.cancelNotification()
            playerInstance?.pause()
            MusicService.isPlaying.value = false
            stopSelf()
        } else {
            // Keep notification but let user swipe it away
            stopForeground(STOP_FOREGROUND_DETACH)
            updateNotification()
        }
    }

    inner class MusicBinder : Binder() {
        fun getService(): MusicService = this@MusicService
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
