package com.msi.data.repository

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import com.msi.data.db.MediaDao
import com.msi.data.db.MediaEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

class MediaRepository(
    private val context: Context,
    private val mediaDao: MediaDao
) {
    val allAudio: Flow<List<MediaEntity>> = mediaDao.getAllAudio()
    val allVideos: Flow<List<MediaEntity>> = mediaDao.getAllVideos()
    val favorites: Flow<List<MediaEntity>> = mediaDao.getFavorites()

    fun searchMedia(query: String): Flow<List<MediaEntity>> {
        return mediaDao.searchMedia("%$query%")
    }

    suspend fun toggleFavorite(mediaId: Long, isFav: Boolean) {
        mediaDao.updateFavorite(mediaId, isFav)
    }

    suspend fun deleteByPath(path: String) = withContext(Dispatchers.IO) {
        try {
            val file = File(path)
            if (file.exists()) {
                file.delete()
            }
        } catch (_: Exception) {}
        mediaDao.deleteByPath(path)
    }

    suspend fun addDummyPreloadIfEmpty() = withContext(Dispatchers.IO) {
        // Quick fallback mock data for testing so a user sees immediate assets if native files aren't scanned
        val dummyData = listOf(
            MediaEntity(
                title = "Acoustic Sunset Serenity",
                artist = "Nature Chillout",
                path = "mock://sunset_serenity.mp3",
                duration = 184000L,
                size = 5304020L,
                mimeType = "audio/mp3",
                isAudio = true,
                album = "Acoustic Waves",
                folderPath = "/sdcard/Music",
                isFavorite = false
            ),
            MediaEntity(
                title = "Lo-Fi Beats Journey",
                artist = "Midnight Coffee",
                path = "mock://lofi_beats.mp3",
                duration = 245000L,
                size = 6120300L,
                mimeType = "audio/mp3",
                isAudio = true,
                album = "Cosmic Coffee",
                folderPath = "/sdcard/Music/LoFi",
                isFavorite = true
            ),
            MediaEntity(
                title = "Synthwave Neon Driver",
                artist = "Retro Future",
                path = "mock://synthwave_driver.mp3",
                duration = 192000L,
                size = 4802090L,
                mimeType = "audio/mp3",
                isAudio = true,
                album = "Grid Rider",
                folderPath = "/sdcard/Music",
                isFavorite = false
            ),
            MediaEntity(
                title = "Ocean Wave Ambient",
                artist = "Zen Relaxation",
                path = "mock://ocean_wave.mp3",
                duration = 312000L,
                size = 9410290L,
                mimeType = "audio/mp3",
                isAudio = true,
                album = "Meditative Nature",
                folderPath = "/sdcard/Music/Nature",
                isFavorite = false
            ),
            MediaEntity(
                title = "Cyberpunk Cinematic trailer",
                artist = "Neon Matrix",
                path = "mock://cyber_trailer.mp4",
                duration = 92000L,
                size = 18409200L,
                mimeType = "video/mp4",
                isAudio = false,
                album = "Visuals Volume 1",
                folderPath = "/sdcard/Movies",
                isFavorite = false
            ),
            MediaEntity(
                title = "Deep Space Odyssey",
                artist = "Celestia Ambient",
                path = "mock://deep_space.mp4",
                duration = 150000L,
                size = 29402910L,
                mimeType = "video/mp4",
                isAudio = false,
                album = "Galaxy Reels",
                folderPath = "/sdcard/Movies/Space",
                isFavorite = true
            )
        )
        mediaDao.insertMedia(dummyData)
    }

    suspend fun scanDeviceMedia() = withContext(Dispatchers.IO) {
        val scannedFiles = mutableListOf<MediaEntity>()

        // 1. Scan Audio
        coroutineContext.run {
            val audioProjection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.SIZE,
                MediaStore.Audio.Media.MIME_TYPE,
                MediaStore.Audio.Media.ALBUM
            )

            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                audioProjection,
                null,
                null,
                null
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val pathCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)

                while (cursor.moveToNext()) {
                    val path = cursor.getString(pathCol) ?: continue
                    val pFile = File(path)
                    val parentFolder = pFile.parent ?: ""
                    scannedFiles.add(
                        MediaEntity(
                            title = cursor.getString(titleCol) ?: pFile.nameWithoutExtension,
                            artist = cursor.getString(artistCol) ?: "<Unknown Artist>",
                            path = path,
                            duration = cursor.getLong(durationCol),
                            size = cursor.getLong(sizeCol),
                            mimeType = cursor.getString(mimeCol) ?: "audio/mpeg",
                            isAudio = true,
                            album = cursor.getString(albumCol) ?: "Unknown Album",
                            folderPath = parentFolder
                        )
                    )
                }
            }
        }

        // 2. Scan Video
        coroutineContext.run {
            val videoProjection = arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.TITLE,
                MediaStore.Video.Media.DATA,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.MIME_TYPE,
                MediaStore.Video.Media.CATEGORY
            )

            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                videoProjection,
                null,
                null,
                null
            )?.use { cursor ->
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE)
                val pathCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)

                while (cursor.moveToNext()) {
                    val path = cursor.getString(pathCol) ?: continue
                    val pFile = File(path)
                    val parentFolder = pFile.parent ?: ""
                    scannedFiles.add(
                        MediaEntity(
                            title = cursor.getString(titleCol) ?: pFile.nameWithoutExtension,
                            artist = "Video Studio",
                            path = path,
                            duration = cursor.getLong(durationCol),
                            size = cursor.getLong(sizeCol),
                            mimeType = cursor.getString(mimeCol) ?: "video/mp4",
                            isAudio = false,
                            album = "Local Video Collection",
                            folderPath = parentFolder
                        )
                    )
                }
            }
        }

        if (scannedFiles.isNotEmpty()) {
            mediaDao.insertMedia(scannedFiles)
        } else {
            // Preload mockup tracks if nothing was scanned from physical storage
            addDummyPreloadIfEmpty()
        }
    }

    suspend fun insertManualUriMedia(title: String, path: String, duration: Long, isAudio: Boolean, mimeType: String, size: Long = 0) {
        val parentFolder = File(path).parent ?: "/sdcard"
        val manualEntity = MediaEntity(
            title = title,
            artist = if (isAudio) "Imported Audio" else "Imported Video",
            path = path,
            duration = duration,
            size = size,
            mimeType = mimeType,
            isAudio = isAudio,
            album = if (isAudio) "Local Core" else "Gallery Link",
            folderPath = parentFolder
        )
        mediaDao.insertMedia(listOf(manualEntity))
    }
}
