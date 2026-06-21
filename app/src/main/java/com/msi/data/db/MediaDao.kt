package com.msi.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {
    @Query("SELECT * FROM media_files WHERE isAudio = 1 ORDER BY title ASC")
    fun getAllAudio(): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media_files WHERE isAudio = 0 ORDER BY title ASC")
    fun getAllVideos(): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media_files WHERE isFavorite = 1 ORDER BY title ASC")
    fun getFavorites(): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media_files WHERE title LIKE :query OR artist LIKE :query OR album LIKE :query ORDER BY title ASC")
    fun searchMedia(query: String): Flow<List<MediaEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMedia(media: List<MediaEntity>)

    @Query("UPDATE media_files SET isFavorite = :isFav WHERE id = :id")
    suspend fun updateFavorite(id: Long, isFav: Boolean)

    @Query("DELETE FROM media_files WHERE path = :path")
    suspend fun deleteByPath(path: String)

    @Query("DELETE FROM media_files")
    suspend fun clearAll()
}
