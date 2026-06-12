package com.example.mediaplayer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.mediaplayer.data.SongEntity

class AudioNotificationManager(private val context: Context) {

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val channelId = "acoustic_media_hub_playback"

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Acoustic Playback Controls",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Playback controls and information for playing audio"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun buildNotification(
        song: SongEntity,
        isPlaying: Boolean
    ): Notification {
        val playPauseAction = if (isPlaying) {
            val pauseIntent = Intent(context, MusicService::class.java).apply {
                action = MusicService.ACTION_PAUSE
            }
            val pendingPauseIntent = PendingIntent.getService(
                context, 1, pauseIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            NotificationCompat.Action.Builder(
                android.R.drawable.ic_media_pause,
                "Pause",
                pendingPauseIntent
            ).build()
        } else {
            val playIntent = Intent(context, MusicService::class.java).apply {
                action = MusicService.ACTION_PLAY
            }
            val pendingPlayIntent = PendingIntent.getService(
                context, 1, playIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            NotificationCompat.Action.Builder(
                android.R.drawable.ic_media_play,
                "Play",
                pendingPlayIntent
            ).build()
        }

        val prevIntent = Intent(context, MusicService::class.java).apply {
            action = MusicService.ACTION_PREVIOUS
        }
        val pendingPrevIntent = PendingIntent.getService(
            context, 2, prevIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nextIntent = Intent(context, MusicService::class.java).apply {
            action = MusicService.ACTION_NEXT
        }
        val pendingNextIntent = PendingIntent.getService(
            context, 3, nextIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val closeIntent = Intent(context, MusicService::class.java).apply {
            action = MusicService.ACTION_CLOSE
        }
        val pendingCloseIntent = PendingIntent.getService(
            context, 4, closeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Intent to launch the MainActivity
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingContentIntent = PendingIntent.getActivity(
            context, 0, contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(song.title)
            .setContentText(song.artist)
            .setSubText(song.album)
            .setOngoing(isPlaying)
            .setContentIntent(pendingContentIntent)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setShowWhen(false)
            .addAction(android.R.drawable.ic_media_previous, "Previous", pendingPrevIntent)
            .addAction(playPauseAction)
            .addAction(android.R.drawable.ic_media_next, "Next", pendingNextIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Close", pendingCloseIntent)

        // Use modern Media3 media style notification if session is available
        val session = MusicService.mediaSession
        if (session != null) {
            val style = androidx.media3.session.MediaStyleNotificationHelper.MediaStyle(session)
                .setShowActionsInCompactView(0, 1, 2)
            builder.setStyle(style)
        }

        return builder.build()
    }

    fun showNotification(notification: Notification, notificationId: Int = 101) {
        notificationManager.notify(notificationId, notification)
    }

    fun cancelNotification(notificationId: Int = 101) {
        notificationManager.cancel(notificationId)
    }
}
