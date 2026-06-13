package com.example.ui.components

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import androidx.compose.runtime.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

// Efficient, thread-safe, local JVM cache-friendly asynchronous video thumbnail loader
@Composable
fun rememberVideoThumbnail(videoPath: String): Bitmap? {
    var thumbnail by remember(videoPath) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(videoPath) {
        thumbnail = withContext(Dispatchers.IO) {
            try {
                val file = File(videoPath)
                if (file.exists()) {
                    val retriever = MediaMetadataRetriever()
                    retriever.setDataSource(videoPath)
                    // Retrieve high-fidelity video frame at 1s mark (1,000,000 microseconds)
                    val bitmap = retriever.getFrameAtTime(1000000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    retriever.release()
                    bitmap
                } else {
                    null
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
    return thumbnail
}
