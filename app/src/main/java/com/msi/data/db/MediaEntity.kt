package com.msi.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "media_files")
data class MediaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val artist: String,
    val path: String,
    val duration: Long,
    val size: Long,
    val mimeType: String,
    val isFavorite: Boolean = false,
    val isAudio: Boolean,
    val album: String = "Unknown Album",
    val folderPath: String = "",
    val addedDate: Long = System.currentTimeMillis()
)
