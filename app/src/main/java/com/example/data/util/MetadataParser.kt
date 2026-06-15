package com.example.data.util

import android.content.Context
import android.media.MediaMetadataRetriever
import android.util.Log
import java.io.File

object MetadataParser {
    private const val TAG = "MetadataParser"

    data class ExtractedMetadata(
        val title: String?,
        val artist: String?,
        val album: String?,
        val genre: String?,
        val year: String?,
        val duration: Long?
    )

    fun extractMetadata(context: Context, path: String): ExtractedMetadata {
        val retriever = MediaMetadataRetriever()
        var title: String? = null
        var artist: String? = null
        var album: String? = null
        var genre: String? = null
        var year: String? = null
        var duration: Long? = null

        try {
            if (path.startsWith("asset:///")) {
                // Handling asset files
                val assetPath = if (path.startsWith("asset:///songs/")) {
                    "songs/" + path.substringAfter("asset:///songs/")
                } else if (path.startsWith("asset:///videos/")) {
                    "videos/" + path.substringAfter("asset:///videos/")
                } else {
                    path.substringAfter("asset:///")
                }
                context.assets.openFd(assetPath).use { fd ->
                    retriever.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
                }
            } else if (path.startsWith("http://") || path.startsWith("https://")) {
                retriever.setDataSource(path, HashMap())
            } else {
                val file = File(path)
                if (file.exists()) {
                    retriever.setDataSource(path)
                } else {
                    Log.w(TAG, "File not on disk to parse metadata: $path")
                }
            }

            title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
            album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            genre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)
            year = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)
                ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            duration = durationStr?.toLongOrNull()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to retrieve local file metadata from path $path: ${e.message}")
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                // Closed
            }
        }

        return ExtractedMetadata(title, artist, album, genre, year, duration)
    }
}
