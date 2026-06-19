package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {
    @Query("SELECT COUNT(*) FROM media_items")
    suspend fun getMediaCount(): Int

    @Query("SELECT * FROM media_items order by title ASC")
    fun getAllMedia(): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media_items WHERE isVideo = 0 ORDER BY title ASC")
    fun getAllAudio(): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media_items WHERE isVideo = 0 ORDER BY title ASC")
    fun getAllAudioPaging(): androidx.paging.PagingSource<Int, MediaEntity>

    @Query("SELECT * FROM media_items WHERE isVideo = 1 ORDER BY title ASC")
    fun getAllVideos(): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media_items WHERE isVideo = 1 ORDER BY title ASC")
    fun getAllVideosPaging(): androidx.paging.PagingSource<Int, MediaEntity>

    @Query("SELECT * FROM media_items WHERE isFavorite = 1 ORDER BY title ASC")
    fun getFavorites(): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media_items WHERE recentlyPlayed > 0 ORDER BY recentlyPlayed DESC LIMIT 50")
    fun getRecentlyPlayed(): Flow<List<MediaEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedia(media: List<MediaEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMediaSingle(media: MediaEntity)

    @Query("UPDATE media_items SET isFavorite = :isFav WHERE path = :path")
    suspend fun updateFavorite(path: String, isFav: Boolean)

    @Query("UPDATE media_items SET recentlyPlayed = :timestamp, playCount = playCount + 1, lastPlayedPosition = :position WHERE path = :path")
    suspend fun recordPlayback(path: String, timestamp: Long, position: Long)

    @Query("UPDATE media_items SET lastPlayedPosition = :position WHERE path = :path")
    suspend fun updateLastPlayedPosition(path: String, position: Long)

    @Query("UPDATE media_items SET title = :newTitle WHERE path = :path")
    suspend fun renameMediaByPath(path: String, newTitle: String)

    @Query("UPDATE media_items SET title = :title, artist = :artist, album = :album, genre = :genre, year = :year WHERE path = :path")
    suspend fun updateMediaMetadata(path: String, title: String, artist: String, album: String, genre: String, year: String)

    @Query("DELETE FROM media_items WHERE path = :path")
    suspend fun deleteMediaByPath(path: String)

    @Query("DELETE FROM media_items WHERE path IN (:paths)")
    suspend fun deleteMediaByPaths(paths: List<String>)

    // Playlists
    @Query("SELECT * FROM playlists ORDER BY name ASC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("DELETE FROM playlists WHERE playlistId = :id")
    suspend fun deletePlaylist(id: Long)

    @Query("UPDATE playlists SET name = :newName WHERE playlistId = :id")
    suspend fun renamePlaylist(id: Long, newName: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addSongToPlaylist(join: PlaylistSongJoin)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songPath = :songPath")
    suspend fun removeSongFromPlaylist(playlistId: Long, songPath: String)

    @Query("SELECT * FROM media_items WHERE path IN (:paths)")
    suspend fun getMediaByPaths(paths: List<String>): List<MediaEntity>

    @Transaction
    @Query("SELECT media_items.* FROM media_items INNER JOIN playlist_songs ON media_items.path = playlist_songs.songPath WHERE playlist_songs.playlistId = :playlistId ORDER BY media_items.title ASC")
    fun getSongsInPlaylist(playlistId: Long): Flow<List<MediaEntity>>

    // App Settings
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSetting(setting: AppSettingEntity)

    @Query("SELECT value FROM app_settings WHERE key = :key")
    suspend fun getSettingValue(key: String): String?
}
