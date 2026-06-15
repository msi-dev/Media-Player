package com.example.data.repository

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import android.util.Log
import com.example.data.db.MediaDao
import com.example.data.db.MediaEntity
import com.example.data.db.PlaylistEntity
import com.example.data.db.PlaylistSongJoin
import com.example.ndk.NativeMediaBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class MediaRepository(
    private val context: Context,
    private val mediaDao: MediaDao,
    private val hiddenFolderDao: com.example.data.db.HiddenFolderDao
) {
    private val TAG = "MediaRepository"

    val allMedia: Flow<List<MediaEntity>> = mediaDao.getAllMedia()
    val allAudio: Flow<List<MediaEntity>> = mediaDao.getAllAudio()
    val allVideos: Flow<List<MediaEntity>> = mediaDao.getAllVideos()
    val favorites: Flow<List<MediaEntity>> = mediaDao.getFavorites()
    val recentlyPlayed: Flow<List<MediaEntity>> = mediaDao.getRecentlyPlayed()
    val playlists: Flow<List<PlaylistEntity>> = mediaDao.getAllPlaylists()

    suspend fun toggleFavorite(path: String, isFavorite: Boolean) {
        mediaDao.updateFavorite(path, isFavorite)
    }

    suspend fun recordPlayback(path: String, position: Long = 0L) {
        mediaDao.recordPlayback(path, System.currentTimeMillis(), position)
    }

    suspend fun updateLastPlayedPosition(path: String, position: Long) {
        mediaDao.updateLastPlayedPosition(path, position)
    }

    suspend fun deleteMediaByPath(path: String) {
        mediaDao.deleteMediaByPath(path)
    }

    suspend fun deleteMediaByPaths(paths: List<String>) {
        mediaDao.deleteMediaByPaths(paths)
    }

    suspend fun renameMediaByPath(path: String, newTitle: String) {
        mediaDao.renameMediaByPath(path, newTitle)
    }

    // Playlists
    suspend fun createPlaylist(name: String): Long {
        return mediaDao.insertPlaylist(PlaylistEntity(name = name))
    }

    suspend fun deletePlaylist(id: Long) {
        mediaDao.deletePlaylist(id)
    }

    suspend fun addSongToPlaylist(playlistId: Long, songPath: String) {
        mediaDao.addSongToPlaylist(PlaylistSongJoin(playlistId, songPath))
    }

    suspend fun removeSongFromPlaylist(playlistId: Long, songPath: String) {
        mediaDao.removeSongFromPlaylist(playlistId, songPath)
    }

    fun getSongsInPlaylist(playlistId: Long): Flow<List<MediaEntity>> {
        return mediaDao.getSongsInPlaylist(playlistId)
    }

    suspend fun getMediaByPaths(paths: List<String>): List<MediaEntity> {
        return mediaDao.getMediaByPaths(paths)
    }

    /**
     * Performs scan of local system using MediaStore, with auto-injection of demo files if empty.
     */
    suspend fun scanLocalMedia(forceScan: Boolean = false) = withContext(Dispatchers.IO) {
        if (!forceScan) {
            val cachedCount = try {
                mediaDao.getMediaCount()
            } catch (e: Exception) {
                0
            }
            if (cachedCount > 0) {
                Log.i(TAG, "Instant load: Database cache holds $cachedCount items. Skipping redundant filesystem crawl.")
                return@withContext
            }
        }

        Log.i(TAG, "Starting storage media scan with hidden folders check...")
        val foundMedia = mutableListOf<MediaEntity>()

        val hiddenFolders = try {
            hiddenFolderDao.getAllHiddenFolders().map { it.folderPath }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load hidden folders from database: ${e.message}")
            emptyList<String>()
        }

        // 1. Scan Audio
        try {
            val audioProjection = arrayOf(
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.SIZE,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.ARTIST
            )
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                audioProjection,
                null,
                null,
                null
            )?.use { cursor ->
                val dataIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val titleIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val durationIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val albumIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val artistIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)

                while (cursor.moveToNext()) {
                    val path = cursor.getString(dataIndex)
                    
                    // Skip hidden folder media items
                    if (hiddenFolders.any { path.startsWith(it) }) {
                        continue
                    }

                    val format = NativeMediaBridge.detectFormat(path)
                    
                    // Verify if format is supported natively
                    if (NativeMediaBridge.isFormatSupported(format)) {
                        var title = cursor.getString(titleIndex) ?: java.io.File(path).nameWithoutExtension
                        var duration = cursor.getLong(durationIndex)
                        val size = cursor.getLong(sizeIndex)
                        var album = cursor.getString(albumIndex) ?: "Unknown Album"
                        var artist = cursor.getString(artistIndex) ?: "Unknown Artist"
                        var genre = "Unknown Genre"
                        var year = "Unknown Year"

                        try {
                            val meta = com.example.data.util.MetadataParser.extractMetadata(context, path)
                            meta.title?.let { if (it.isNotBlank()) title = it }
                            meta.artist?.let { if (it.isNotBlank()) artist = it }
                            meta.album?.let { if (it.isNotBlank()) album = it }
                            meta.genre?.let { if (it.isNotBlank()) genre = it }
                            meta.year?.let { if (it.isNotBlank()) year = it }
                            meta.duration?.let { if (it > 0) duration = it }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error extracting metadata for $path: ${e.message}")
                        }

                        foundMedia.add(
                            MediaEntity(
                                path = path,
                                title = title,
                                duration = duration,
                                size = size,
                                album = album,
                                artist = artist,
                                genre = genre,
                                year = year,
                                isVideo = false
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying MediaStore for audio: ${e.message}")
        }

        // 2. Scan Video
        try {
            val videoProjection = arrayOf(
                MediaStore.Video.Media.DATA,
                MediaStore.Video.Media.TITLE,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.SIZE
            )
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                videoProjection,
                null,
                null,
                null
            )?.use { cursor ->
                val dataIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                val titleIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE)
                val durationIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)

                while (cursor.moveToNext()) {
                    val path = cursor.getString(dataIndex)

                    // Skip hidden folder media items
                    if (hiddenFolders.any { path.startsWith(it) }) {
                        continue
                    }

                    val format = NativeMediaBridge.detectFormat(path)

                    if (NativeMediaBridge.isFormatSupported(format)) {
                        var title = cursor.getString(titleIndex) ?: java.io.File(path).nameWithoutExtension
                        var duration = cursor.getLong(durationIndex)
                        val size = cursor.getLong(sizeIndex)
                        var album = "Video Folder"
                        var artist = "Video Provider"
                        var genre = "Video Genre"
                        var year = "Unknown Year"

                        try {
                            val meta = com.example.data.util.MetadataParser.extractMetadata(context, path)
                            meta.title?.let { if (it.isNotBlank()) title = it }
                            meta.artist?.let { if (it.isNotBlank()) artist = it }
                            meta.album?.let { if (it.isNotBlank()) album = it }
                            meta.genre?.let { if (it.isNotBlank()) genre = it }
                            meta.year?.let { if (it.isNotBlank()) year = it }
                            meta.duration?.let { if (it > 0) duration = it }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error extracting video metadata for $path: ${e.message}")
                        }

                        foundMedia.add(
                            MediaEntity(
                                path = path,
                                title = title,
                                duration = duration,
                                size = size,
                                album = album,
                                artist = artist,
                                genre = genre,
                                year = year,
                                isVideo = true
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying MediaStore for video: ${e.message}")
        }

        // 3. Fallback High-Fidelity Demo Media Injection
        // If empty, generate amazing premium presets so the user receives a fully working sandbox.
        if (foundMedia.isEmpty()) {
            Log.i(TAG, "No elements found on emulator hardware scan. Injected professional offline media sets.")
            val demoMedia = listOf(
                // Premium Local Audio demo presets
                MediaEntity(
                    path = "asset:///songs/synthwave_horizon.mp3",
                    title = "Synthwave Horizon",
                    duration = 198000L, // 3:18
                    size = 4752000L,
                    album = "Retro Cosmic Drive",
                    artist = "Stellar Ranger",
                    genre = "Synthwave",
                    isVideo = false
                ),
                MediaEntity(
                    path = "asset:///songs/sunset_highway.flac",
                    title = "Sunset Highway (Hi-Res flac)",
                    duration = 245000L, // 4:05
                    size = 28500000L,
                    album = "California Chillout",
                    artist = "Pulse Vector",
                    genre = "Chillwave",
                    isVideo = false
                ),
                MediaEntity(
                    path = "asset:///songs/neon_dreams.aac",
                    title = "Neon Dreams",
                    duration = 180000L, // 3:00
                    size = 3600000L,
                    album = "Blade City",
                    artist = "Luna Helix",
                    genre = "Electro-Pop",
                    isVideo = false
                ),
                MediaEntity(
                    path = "asset:///songs/liquid_ambient.wav",
                    title = "Liquid Ambient (Studio wav)",
                    duration = 312000L, // 5:12
                    size = 53000000L,
                    album = "Ethereal Echoes",
                    artist = "Ascension Zero",
                    genre = "Ambient",
                    isVideo = false
                ),
                MediaEntity(
                    path = "asset:///songs/acoustic_breeze.opus",
                    title = "Acoustic Breeze",
                    duration = 142000L, // 2:22
                    size = 2100000L,
                    album = "Woodland Whispers",
                    artist = "Finn & The Whispers",
                    genre = "Folk",
                    isVideo = false
                ),
                MediaEntity(
                    path = "asset:///songs/cyberpunk_assault.avi", // Native format demonstration
                    title = "Cyberpunk Assault",
                    duration = 210000L,
                    size = 120000000L,
                    album = "Industrial Cyber",
                    artist = "Grid Terminal",
                    genre = "Industrial",
                    isVideo = false
                ),

                // Premium Immersive Video demo presets
                MediaEntity(
                    path = "https://storage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4",
                    title = "Sintel Cinematic Trailer",
                    duration = 520000L,
                    size = 85000000L,
                    album = "Blender Foundation",
                    artist = "Creative Video Group",
                    isVideo = true
                ),
                MediaEntity(
                    path = "https://storage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4",
                    title = "Tears of Steel (VFX Edition)",
                    duration = 734000L,
                    size = 140000000L,
                    album = "CGI Production",
                    artist = "VFX Collective",
                    isVideo = true
                ),
                MediaEntity(
                    path = "https://storage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                    title = "Big Buck Bunny (Animation)",
                    duration = 596000L,
                    size = 90000000L,
                    album = "Blender Animation",
                    artist = "Orangutan Studios",
                    isVideo = true
                )
            )
            mediaDao.insertMedia(demoMedia)
        } else {
            mediaDao.insertMedia(foundMedia)
        }
    }
}
