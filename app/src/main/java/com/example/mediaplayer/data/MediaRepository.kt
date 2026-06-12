package com.example.mediaplayer.data

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class MediaRepository(private val context: Context, private val mediaDao: MediaDao) {

    val allSongs: Flow<List<SongEntity>> = mediaDao.getAllSongsFlow()
    val favoriteSongs: Flow<List<SongEntity>> = mediaDao.getFavoriteSongsFlow()
    val allPlaylists: Flow<List<PlaylistEntity>> = mediaDao.getAllPlaylistsFlow()
    val playbackHistory: Flow<List<HistoryEntity>> = mediaDao.getHistoryFlow()

    fun getSongsInPlaylist(playlistId: Long): Flow<List<SongEntity>> =
        mediaDao.getSongsInPlaylistFlow(playlistId)

    suspend fun setSongFavorite(songId: String, isFav: Boolean) =
        mediaDao.setSongFavorite(songId, isFav)

    suspend fun insertPlaylist(name: String): Long =
        mediaDao.insertPlaylist(PlaylistEntity(name = name))

    suspend fun deletePlaylist(id: Long) =
        mediaDao.deletePlaylist(id)

    suspend fun addSongToPlaylist(playlistId: Long, songId: String) =
        mediaDao.addSongToPlaylist(PlaylistSongCrossRef(playlistId, songId))

    suspend fun removeSongFromPlaylist(playlistId: Long, songId: String) =
        mediaDao.removeSongFromPlaylist(playlistId, songId)

    suspend fun addToHistory(songId: String, title: String, artist: String, duration: Long, mediaType: String) =
        mediaDao.insertHistory(
            HistoryEntity(
                songId = songId,
                title = title,
                artist = artist,
                duration = duration,
                mediaType = mediaType,
                playedAt = System.currentTimeMillis()
            )
        )

    suspend fun deleteHistoryItem(songId: String) =
        mediaDao.deleteHistoryItem(songId)

    suspend fun clearHistory() =
        mediaDao.clearHistory()

    // Scans local media store for audio & video on device thread-safely
    suspend fun scanLocalMedia() = withContext(Dispatchers.IO) {
        val songsList = mutableListOf<SongEntity>()

        // Scan Audios
        val audioProjection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION
        )
        val audioCollection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        context.contentResolver.query(
            audioCollection,
            audioProjection,
            null,
            null,
            null
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

            while (cursor.moveToNext()) {
                val idLong = cursor.getLong(idColumn)
                val uri = ContentUris.withAppendedId(audioCollection, idLong).toString()
                val title = cursor.getString(titleColumn) ?: "Unknown Track"
                val artist = cursor.getString(artistColumn) ?: "Unknown Artist"
                val album = cursor.getString(albumColumn) ?: "Unknown Album"
                val duration = cursor.getLong(durationColumn)

                songsList.add(
                    SongEntity(
                        id = uri,
                        title = title,
                        artist = artist,
                        album = album,
                        duration = duration,
                        isFavorite = false,
                        mediaType = "AUDIO"
                    )
                )
            }
        }

        // Scan Videos
        val videoProjection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION
        )
        val videoCollection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        context.contentResolver.query(
            videoCollection,
            videoProjection,
            null,
            null,
            null
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)

            while (cursor.moveToNext()) {
                val idLong = cursor.getLong(idColumn)
                val uri = ContentUris.withAppendedId(videoCollection, idLong).toString()
                val title = cursor.getString(nameColumn) ?: "Unknown Video"
                val duration = cursor.getLong(durationColumn)

                songsList.add(
                    SongEntity(
                        id = uri,
                        title = title,
                        artist = "Video Files",
                        album = "Movies",
                        duration = duration,
                        isFavorite = false,
                        mediaType = "VIDEO"
                    )
                )
            }
        }

        // If no items were found on device, insert robust beautiful default samples so the player is fully functional out-of-the-box!
        if (songsList.isEmpty()) {
            val sampleTracks = listOf(
                SongEntity(
                    id = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3",
                    title = "Echoes of Silence (Sample)",
                    artist = "SoundHelix",
                    album = "Chill Out Volume 1",
                    duration = 372000L,
                    mediaType = "AUDIO"
                ),
                SongEntity(
                    id = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3",
                    title = "Midnight Breeze (Sample)",
                    artist = "SoundHelix",
                    album = "Chill Out Volume 2",
                    duration = 423000L,
                    mediaType = "AUDIO"
                ),
                SongEntity(
                    id = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3",
                    title = "Cyber Ambient Wave (Sample)",
                    artist = "SoundHelix",
                    album = "Matrix Waves",
                    duration = 344000L,
                    mediaType = "AUDIO"
                ),
                // Premium Video Samples
                SongEntity(
                    id = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                    title = "Big Buck Bunny (Nature video)",
                    artist = "Blender Foundation",
                    album = "CGI Shorts",
                    duration = 596000L,
                    mediaType = "VIDEO"
                ),
                SongEntity(
                    id = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
                    title = "Elephant\'s Dream (Sci-Fi shorts)",
                    artist = "Blender Project",
                    album = "CGI Shorts",
                    duration = 653000L,
                    mediaType = "VIDEO"
                )
            )
            mediaDao.insertSongs(sampleTracks)
        } else {
            mediaDao.insertSongs(songsList)
        }
    }
}
