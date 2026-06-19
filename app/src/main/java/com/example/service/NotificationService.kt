package com.example.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager as AndroidNotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaStyleNotificationHelper
import com.example.MainActivity

class NotificationService {
    companion object {
        const val CHANNEL_ID = "media_playback_channel"
        const val CHANNEL_NAME = "Media Playback Control"
        const val CHANNEL_DESC = "Interactive playback controls for audio and video streams"
        const val NOTIFICATION_ID = 8001

        fun checkNotificationPermission(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        }

        fun createNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as AndroidNotificationManager
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    AndroidNotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = CHANNEL_DESC
                    setShowBadge(false)
                }
                manager.createNotificationChannel(channel)
            }
        }

        @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
        private fun getServicePendingIntent(context: Context, action: String, serviceClass: Class<*>): PendingIntent {
            val intent = Intent(context, serviceClass).apply {
                this.action = action
            }
            return PendingIntent.getService(
                context,
                (action + serviceClass.simpleName).hashCode(),
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
        fun buildMediaNotification(
            context: Context,
            mediaSession: MediaSession,
            title: String,
            artist: String,
            largeIcon: Bitmap?,
            isPlaying: Boolean
        ): Notification {
            createNotificationChannel(context)

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val prevAction = NotificationCompat.Action(
                android.R.drawable.ic_media_previous, "Previous",
                getServicePendingIntent(context, "ACTION_PREVIOUS", MusicPlayerService::class.java)
            )

            val playPauseAction = if (isPlaying) {
                NotificationCompat.Action(
                    android.R.drawable.ic_media_pause, "Pause",
                    getServicePendingIntent(context, "ACTION_PAUSE", MusicPlayerService::class.java)
                )
            } else {
                NotificationCompat.Action(
                    android.R.drawable.ic_media_play, "Play",
                    getServicePendingIntent(context, "ACTION_PLAY", MusicPlayerService::class.java)
                )
            }

            val nextAction = NotificationCompat.Action(
                android.R.drawable.ic_media_next, "Next",
                getServicePendingIntent(context, "ACTION_NEXT", MusicPlayerService::class.java)
            )

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(title)
                .setContentText(artist)
                .setLargeIcon(largeIcon)
                .setContentIntent(pendingIntent)
                .setOngoing(isPlaying)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .addAction(prevAction)
                .addAction(playPauseAction)
                .addAction(nextAction)
                .setStyle(
                    MediaStyleNotificationHelper.MediaStyle(mediaSession)
                        .setShowActionsInCompactView(0, 1, 2)
                )

            return builder.build()
        }

        fun applyColorGrading(bitmap: Bitmap): Bitmap {
            val width = bitmap.width
            val height = bitmap.height
            val config = bitmap.config ?: Bitmap.Config.ARGB_8888
            val graded = Bitmap.createBitmap(width, height, config)
            val canvas = android.graphics.Canvas(graded)
            
            val paint = android.graphics.Paint()
            canvas.drawBitmap(bitmap, 0f, 0f, paint)
            
            // Rich color-grading shader (Deep cinematic Orange & Teal)
            val shader = android.graphics.RadialGradient(
                width / 2f, height / 2f, Math.min(width, height).toFloat() * 1.5f,
                intArrayOf(android.graphics.Color.argb(45, 255, 140, 0), android.graphics.Color.argb(135, 0, 18, 50)),
                floatArrayOf(0f, 1f),
                android.graphics.Shader.TileMode.CLAMP
            )
            val gradientPaint = android.graphics.Paint().apply {
                this.shader = shader
                this.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_OVER)
            }
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), gradientPaint)
            
            return graded
        }

        @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
        fun buildVideoMediaNotification(
            context: Context,
            mediaSession: MediaSession,
            title: String,
            artist: String,
            videoPath: String,
            isPlaying: Boolean
        ): Notification {
            createVideoNotificationChannel(context)

            // Dynamic video frame extraction & cinematic color-grading
            var gradedBitmap: Bitmap? = null
            try {
                val file = java.io.File(videoPath)
                if (file.exists()) {
                    val retriever = MediaMetadataRetriever()
                    retriever.setDataSource(videoPath)
                    val rawBitmap = retriever.getFrameAtTime(1000000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    retriever.release()
                    if (rawBitmap != null) {
                        gradedBitmap = applyColorGrading(rawBitmap)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val prevAction = NotificationCompat.Action(
                android.R.drawable.ic_media_previous, "Previous",
                getServicePendingIntent(context, "ACTION_PREVIOUS", VideoPlayerService::class.java)
            )

            val playPauseAction = if (isPlaying) {
                NotificationCompat.Action(
                    android.R.drawable.ic_media_pause, "Pause",
                    getServicePendingIntent(context, "ACTION_PAUSE", VideoPlayerService::class.java)
                )
            } else {
                NotificationCompat.Action(
                    android.R.drawable.ic_media_play, "Play",
                    getServicePendingIntent(context, "ACTION_PLAY", VideoPlayerService::class.java)
                )
            }

            val nextAction = NotificationCompat.Action(
                android.R.drawable.ic_media_next, "Next",
                getServicePendingIntent(context, "ACTION_NEXT", VideoPlayerService::class.java)
            )

            val builder = NotificationCompat.Builder(context, "video_playback_channel")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(title)
                .setContentText(artist)
                .setLargeIcon(gradedBitmap)
                .setContentIntent(pendingIntent)
                .setOngoing(isPlaying)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .addAction(prevAction)
                .addAction(playPauseAction)
                .addAction(nextAction)
                .setStyle(
                    MediaStyleNotificationHelper.MediaStyle(mediaSession)
                        .setShowActionsInCompactView(0, 1, 2)
                )

            return builder.build()
        }

        fun createVideoNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as AndroidNotificationManager
                val channel = NotificationChannel(
                    "video_playback_channel",
                    "Video Background Playback",
                    AndroidNotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Playback controls for background videocasting"
                    setShowBadge(false)
                }
                manager.createNotificationChannel(channel)
            }
        }

        fun deleteVideoNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as AndroidNotificationManager
                manager.deleteNotificationChannel("video_playback_channel")
            }
        }
    }
}
