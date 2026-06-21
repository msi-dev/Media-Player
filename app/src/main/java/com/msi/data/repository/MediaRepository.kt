package com.msi.data.repository

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import android.util.Log
import com.msi.data.db.MediaDao
import com.msi.data.db.MediaEntity
import com.msi.data.db.PlaylistEntity
import com.msi.data.db.PlaylistSongJoin
import com.msi.ndk.NativeMediaBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

class MediaRepository(
    private val context: Context,
    private val mediaDao: MediaDao,
    private val hiddenFolderDao: com.msi.data.db.HiddenFolderDao
) {
    private val TAG = "MediaRepository"

    private var cachedFolders: List<MediaFolder>? = null

    fun clearFolderCache() {
        cachedFolders = null
    }

    val allMedia: Flow<List<MediaEntity>> = mediaDao.getAllMedia()
    val allAudio: Flow<List<MediaEntity>> = mediaDao.getAllAudio()
    val allVideos: Flow<List<MediaEntity>> = mediaDao.getAllVideos()
    
    fun getAudioPagingSource(): androidx.paging.PagingSource<Int, MediaEntity> = mediaDao.getAllAudioPaging()
    fun getVideosPagingSource(): androidx.paging.PagingSource<Int, MediaEntity> = mediaDao.getAllVideosPaging()

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

    suspend fun updateMediaMetadata(path: String, title: String, artist: String, album: String, genre: String, year: String) {
        mediaDao.updateMediaMetadata(path, title, artist, album, genre, year)
    }

    // Playlists
    suspend fun createPlaylist(name: String): Long {
        return mediaDao.insertPlaylist(PlaylistEntity(name = name))
    }

    suspend fun deletePlaylist(id: Long) {
        mediaDao.deletePlaylist(id)
    }

    suspend fun renamePlaylist(id: Long, newName: String) {
        mediaDao.renamePlaylist(id, newName)
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

    // App settings persistence
    suspend fun saveSetting(key: String, value: String) {
        mediaDao.saveSetting(com.msi.data.db.AppSettingEntity(key, value))
    }

    suspend fun getSettingValue(key: String): String? {
        return mediaDao.getSettingValue(key)
    }

    suspend fun insertMedia(media: MediaEntity) {
        mediaDao.insertMediaSingle(media)
    }

    /**
     * Performs scan of local system using MediaStore, with auto-injection of demo files if empty.
     */
    suspend fun scanLocalMedia(forceScan: Boolean = false) = withContext(Dispatchers.IO) {
        if (forceScan) {
            clearFolderCache()
        } else {
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

        val settingsDataStore = com.msi.data.settings.SettingsDataStore(context)
        val scanAndroidFolder = try {
            settingsDataStore.scanAndroidFolder.first()
        } catch (e: Exception) {
            false
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
                    if (!scanAndroidFolder && (path.contains("/Android/", ignoreCase = true) || path.startsWith("/Android/"))) {
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
                    if (!scanAndroidFolder && (path.contains("/Android/", ignoreCase = true) || path.startsWith("/Android/"))) {
                        continue
                    }

                    val format = NativeMediaBridge.detectFormat(path)

                    if (NativeMediaBridge.isFormatSupported(format)) {
                        val title = cursor.getString(titleIndex) ?: java.io.File(path).nameWithoutExtension
                        val duration = cursor.getLong(durationIndex)
                        val size = cursor.getLong(sizeIndex)
                        val album = "Video Folder"
                        val artist = "Video Provider"
                        val genre = "Video Genre"
                        val year = "Unknown Year"

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

    /**
     * Unified folder scanning: Queries local MediaStore for folders with audio/video media.
     * PERFORMANCE OPTIMIZATION: Runs Audio and Video sweeps in parallel using coroutine async deferrals
     * and caches the result to prevent storage queries from lagging/re-triggering on cheap hardware.
     */
    suspend fun queryMediaFolders(): List<MediaFolder> = withContext(Dispatchers.IO) {
        cachedFolders?.let {
            Log.i(TAG, "Instant load: Returning cached folders list of size ${it.size}")
            return@withContext it
        }

        // Run scans concurrently
        val resultList = coroutineScope {
            val audioDeferred = async {
                val audioMap = mutableMapOf<String, Pair<String, Int>>()
                val audioProjection = arrayOf(MediaStore.Audio.Media.DATA)
                try {
                    context.contentResolver.query(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        audioProjection,
                        null,
                        null,
                        null
                    )?.use { cursor ->
                        val dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                        if (dataCol != -1) {
                            while (cursor.moveToNext()) {
                                val filePath = cursor.getString(dataCol) ?: continue
                                val idx = filePath.lastIndexOf('/')
                                if (idx <= 0) continue
                                val parentPath = filePath.substring(0, idx)
                                val current = audioMap[parentPath]
                                if (current != null) {
                                    audioMap[parentPath] = Pair(current.first, current.second + 1)
                                } else {
                                    val parentName = parentPath.substringAfterLast('/')
                                    if (parentName.isNotEmpty()) {
                                        audioMap[parentPath] = Pair(parentName, 1)
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error querying MediaStore folders for audio: ${e.message}")
                }
                audioMap
            }

            val videoDeferred = async {
                val videoMap = mutableMapOf<String, Pair<String, Int>>()
                val videoProjection = arrayOf(MediaStore.Video.Media.DATA)
                try {
                    context.contentResolver.query(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        videoProjection,
                        null,
                        null,
                        null
                    )?.use { cursor ->
                        val dataCol = cursor.getColumnIndex(MediaStore.Video.Media.DATA)
                        if (dataCol != -1) {
                            while (cursor.moveToNext()) {
                                val filePath = cursor.getString(dataCol) ?: continue
                                val idx = filePath.lastIndexOf('/')
                                if (idx <= 0) continue
                                val parentPath = filePath.substring(0, idx)
                                val current = videoMap[parentPath]
                                if (current != null) {
                                    videoMap[parentPath] = Pair(current.first, current.second + 1)
                                } else {
                                    val parentName = parentPath.substringAfterLast('/')
                                    if (parentName.isNotEmpty()) {
                                        videoMap[parentPath] = Pair(parentName, 1)
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error querying MediaStore folders for video: ${e.message}")
                }
                videoMap
            }

            val audioResults = audioDeferred.await()
            val videoResults = videoDeferred.await()

            val foldersMap = mutableMapOf<String, Triple<String, Int, Pair<Boolean, Boolean>>>()
            for ((path, pair) in audioResults) {
                foldersMap[path] = Triple(pair.first, pair.second, Pair(true, false))
            }
            for ((path, pair) in videoResults) {
                val existing = foldersMap[path]
                if (existing != null) {
                    foldersMap[path] = Triple(existing.first, existing.second + pair.second, Pair(existing.third.first, true))
                } else {
                    foldersMap[path] = Triple(pair.first, pair.second, Pair(false, true))
                }
            }

            // Include any Demo preset folder if physical list is empty
            if (foldersMap.isEmpty()) {
                foldersMap["/demo_vault"] = Triple("Demo Vault", 9, Pair(true, true))
            }

            foldersMap.map { (path, info) ->
                MediaFolder(
                    path = path,
                    name = info.first,
                    totalItems = info.second,
                    hasAudio = info.third.first,
                    hasVideo = info.third.second
                )
            }.sortedBy { it.name.lowercase() }
        }

        cachedFolders = resultList
        resultList
    }

    /**
     * Unified fast item fetcher: Lazily loads and filters files by folder path with limits.
     */
    suspend fun getPagedMediaItemsInFolder(
        folderPath: String,
        limit: Int,
        offset: Int
    ): List<MediaEntity> = withContext(Dispatchers.IO) {
        val allItems = mutableListOf<MediaEntity>()

        // Fallback for demo folder (to keep system functional even on blank emulator/sandbox)
        if (folderPath.contains("demo_vault")) {
            val demoMedia = listOf(
                MediaEntity(
                    path = "asset:///songs/synthwave_horizon.mp3",
                    title = "Synthwave Horizon",
                    duration = 198000L,
                    size = 4752000L,
                    album = "Retro Cosmic Drive",
                    artist = "Stellar Ranger",
                    genre = "Synthwave",
                    isVideo = false
                ),
                MediaEntity(
                    path = "asset:///songs/sunset_highway.flac",
                    title = "Sunset Highway (Hi-Res flac)",
                    duration = 245000L,
                    size = 28500000L,
                    album = "California Chillout",
                    artist = "Pulse Vector",
                    genre = "Chillwave",
                    isVideo = false
                ),
                MediaEntity(
                    path = "asset:///songs/neon_dreams.aac",
                    title = "Neon Dreams",
                    duration = 180000L,
                    size = 3600000L,
                    album = "Blade City",
                    artist = "Luna Helix",
                    genre = "Electro-Pop",
                    isVideo = false
                ),
                MediaEntity(
                    path = "asset:///songs/liquid_ambient.wav",
                    title = "Liquid Ambient (Studio wav)",
                    duration = 312000L,
                    size = 53000000L,
                    album = "Ethereal Echoes",
                    artist = "Ascension Zero",
                    genre = "Ambient",
                    isVideo = false
                ),
                MediaEntity(
                    path = "asset:///songs/acoustic_breeze.opus",
                    title = "Acoustic Breeze",
                    duration = 142000L,
                    size = 2100000L,
                    album = "Woodland Whispers",
                    artist = "Finn & The Whispers",
                    genre = "Folk",
                    isVideo = false
                ),
                MediaEntity(
                    path = "asset:///songs/cyberpunk_assault.avi",
                    title = "Cyberpunk Assault",
                    duration = 210000L,
                    size = 120000000L,
                    album = "Industrial Cyber",
                    artist = "Grid Terminal",
                    genre = "Industrial",
                    isVideo = false
                ),
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
            return@withContext if (offset >= demoMedia.size) {
                emptyList()
            } else {
                val toIndex = (offset + limit).coerceAtMost(demoMedia.size)
                demoMedia.subList(offset, toIndex)
            }
        }

        // Query Audio from target folder
        val audioProjection = arrayOf(
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ARTIST
        )
        try {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                audioProjection,
                "_data LIKE ? AND _data NOT LIKE ?",
                arrayOf("$folderPath/%", "$folderPath/%/%"),
                null
            )?.use { cursor ->
                val dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                val titleCol = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)
                val durationCol = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)
                val sizeCol = cursor.getColumnIndex(MediaStore.Audio.Media.SIZE)
                val albumCol = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM)
                val artistCol = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)

                if (dataCol != -1 && titleCol != -1) {
                    while (cursor.moveToNext()) {
                        val path = cursor.getString(dataCol) ?: continue
                        val idx = path.lastIndexOf('/')
                        val parentPath = if (idx > 0) path.substring(0, idx) else ""
                        if (parentPath == folderPath) {
                            val parentName = if (idx > 0) path.substring(path.lastIndexOf('/', idx - 1) + 1, idx) else ""
                            val title = cursor.getString(titleCol) ?: if (parentName.isNotEmpty()) parentName else "Track"
                            val duration = if (durationCol != -1) cursor.getLong(durationCol) else 0L
                            val size = if (sizeCol != -1) cursor.getLong(sizeCol) else 0L
                            val album = if (albumCol != -1) cursor.getString(albumCol) ?: "Unknown Album" else "Unknown Album"
                            val artist = if (artistCol != -1) cursor.getString(artistCol) ?: "Unknown Artist" else "Unknown Artist"

                            allItems.add(
                                MediaEntity(
                                    path = path,
                                    title = title,
                                    duration = duration,
                                    size = size,
                                    album = album,
                                    artist = artist,
                                    isVideo = false
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching audios in folder: ${e.message}")
        }

        // Query Video from target folder
        val videoProjection = arrayOf(
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.TITLE,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE
        )
        try {
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                videoProjection,
                "_data LIKE ? AND _data NOT LIKE ?",
                arrayOf("$folderPath/%", "$folderPath/%/%"),
                null
            )?.use { cursor ->
                val dataCol = cursor.getColumnIndex(MediaStore.Video.Media.DATA)
                val titleCol = cursor.getColumnIndex(MediaStore.Video.Media.TITLE)
                val durationCol = cursor.getColumnIndex(MediaStore.Video.Media.DURATION)
                val sizeCol = cursor.getColumnIndex(MediaStore.Video.Media.SIZE)

                if (dataCol != -1 && titleCol != -1) {
                    while (cursor.moveToNext()) {
                        val path = cursor.getString(dataCol) ?: continue
                        val idx = path.lastIndexOf('/')
                        val parentPath = if (idx > 0) path.substring(0, idx) else ""
                        if (parentPath == folderPath) {
                            val parentName = if (idx > 0) path.substring(path.lastIndexOf('/', idx - 1) + 1, idx) else ""
                            val title = cursor.getString(titleCol) ?: if (parentName.isNotEmpty()) parentName else "Video"
                            val duration = if (durationCol != -1) cursor.getLong(durationCol) else 0L
                            val size = if (sizeCol != -1) cursor.getLong(sizeCol) else 0L

                            allItems.add(
                                MediaEntity(
                                    path = path,
                                    title = title,
                                    duration = duration,
                                    size = size,
                                    album = "Video Folder",
                                    artist = "Video Provider",
                                    isVideo = true
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching videos in folder: ${e.message}")
        }

        // Sort naturally
        allItems.sortBy { it.title.lowercase() }

        // Chunked offset slice slicing
        if (offset >= allItems.size) {
            emptyList()
        } else {
            val toIndex = (offset + limit).coerceAtMost(allItems.size)
            allItems.subList(offset, toIndex)
        }
    }
}

private fun java.io.File.nameIndex(): String {
    return nameWithoutExtension
}

data class MediaFolder(
    val path: String,
    val name: String,
    val totalItems: Int,
    val hasAudio: Boolean,
    val hasVideo: Boolean
)

