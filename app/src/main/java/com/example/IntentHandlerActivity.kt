package com.example

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import com.example.playback.IntentManager

class IntentHandlerActivity : ComponentActivity() {
    private val TAG = "IntentHandlerActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "Parsing incoming external intent...")

        try {
            val intentManager = IntentManager(this)
            val media = intentManager.handleIncomingIntent(intent)
            if (media != null) {
                if (media.isVideo) {
                    val forwardIntent = Intent(this, VideoPlaybackActivity::class.java).apply {
                        action = intent?.action
                        data = intent?.data
                        type = intent?.type
                        putExtra("extra_media_path", media.path)
                        putExtra("extra_media_title", media.title)
                        putExtra("extra_media_artist", media.artist)
                        putExtra("extra_media_album", media.album)
                        flags = Intent.FLAG_ACTIVITY_FORWARD_RESULT
                    }
                    startActivity(forwardIntent)
                } else {
                    val forwardIntent = Intent(this, AudioPlaybackActivity::class.java).apply {
                        action = intent?.action
                        data = intent?.data
                        type = intent?.type
                        putExtra("extra_media_path", media.path)
                        putExtra("extra_media_title", media.title)
                        putExtra("extra_media_artist", media.artist)
                        putExtra("extra_media_album", media.album)
                        flags = Intent.FLAG_ACTIVITY_FORWARD_RESULT
                    }
                    startActivity(forwardIntent)
                }
            } else {
                val mainIntent = Intent(this, MainActivity::class.java)
                startActivity(mainIntent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error routing intent: ${e.message}", e)
            val mainIntent = Intent(this, MainActivity::class.java)
            startActivity(mainIntent)
        }
        finish()
    }
}
