package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HiddenFolderDao {
    @Query("SELECT * FROM hidden_folders ORDER BY folderPath ASC")
    fun getAllHiddenFoldersFlow(): Flow<List<HiddenFolderEntity>>

    @Query("SELECT * FROM hidden_folders")
    suspend fun getAllHiddenFolders(): List<HiddenFolderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHiddenFolder(hiddenFolder: HiddenFolderEntity)

    @Query("DELETE FROM hidden_folders WHERE folderPath = :path")
    suspend fun deleteHiddenFolder(path: String)
}
