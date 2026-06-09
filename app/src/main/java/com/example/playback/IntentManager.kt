package com.example.playback

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.example.data.db.MediaEntity

class IntentManager(private val context: Context) {
    private val TAG = "IntentManager"

    fun handleIncomingIntent(intent: Intent?): MediaEntity? {
        if (intent == null) return null
        val action = intent.action
        val dataUri: Uri? = intent.data
        val mimeType = intent.type ?: ""
        Log.i(TAG, "Incoming intent action: $action, uri: $dataUri, mimeType: $mimeType")

        if ((Intent.ACTION_VIEW == action || Intent.ACTION_SEND == action) && dataUri != null) {
            val path = dataUri.toString()
            var title = dataUri.lastPathSegment ?: "External Media"
            
            if (dataUri.scheme == "content") {
                try {
                    context.contentResolver.query(dataUri, null, null, null, null)?.use { cursor ->
                        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1 && cursor.moveToFirst()) {
                            val resolvedName = cursor.getString(nameIndex)
                            if (!resolvedName.isNullOrEmpty()) {
                                title = resolvedName
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error resolving dynamic content display name: ${e.message}")
                }
            }

            val isVideo = mimeType.startsWith("video") || 
                          path.contains("video", ignoreCase = true) ||
                          path.endsWith(".mp4") || 
                          path.endsWith(".mkv") || 
                          path.endsWith(".webm") || 
                          path.endsWith(".3gp")

            return MediaEntity(
                path = path,
                title = title,
                duration = 0L,
                size = 0L,
                album = "Streaming",
                artist = "External Source",
                genre = "Unknown Genre",
                isVideo = isVideo,
                isFavorite = false,
                lastPlayedPosition = 0L
            )
        }
        return null
    }
}
