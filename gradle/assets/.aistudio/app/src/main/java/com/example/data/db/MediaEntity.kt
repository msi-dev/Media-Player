package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "media_items")
data class MediaEntity(
    @PrimaryKey val path: String,
    val title: String,
    val duration: Long = 0L,
    val size: Long = 0L,
    val album: String = "Unknown Album",
    val artist: String = "Unknown Artist",
    val genre: String = "Unknown Genre",
    val isVideo: Boolean = false,
    var isFavorite: Boolean = false,
    var recentlyPlayed: Long = 0L,
    var playCount: Int = 0,
    var lastPlayedPosition: Long = 0L
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val playlistId: Long = 0L,
    val name: String
)

@Entity(tableName = "playlist_songs", primaryKeys = ["playlistId", "songPath"])
data class PlaylistSongJoin(
    val playlistId: Long,
    val songPath: String
)
