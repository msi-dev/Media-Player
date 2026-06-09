package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "hidden_folders")
data class HiddenFolderEntity(
    @PrimaryKey val folderPath: String
)
