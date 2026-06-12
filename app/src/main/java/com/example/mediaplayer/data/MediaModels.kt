package com.example.mediaplayer.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "songs")
data class SongEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val isFavorite: Boolean = false,
    val mediaType: String = "AUDIO" // "AUDIO" or "VIDEO"
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String
)

@Entity(tableName = "playlist_songs", primaryKeys = ["playlistId", "songId"])
data class PlaylistSongCrossRef(
    val playlistId: Long,
    val songId: String
)

@Entity(tableName = "playback_history")
data class HistoryEntity(
    @PrimaryKey val songId: String,
    val title: String,
    val artist: String,
    val duration: Long,
    val playedAt: Long = System.currentTimeMillis(),
    val mediaType: String = "AUDIO"
)
