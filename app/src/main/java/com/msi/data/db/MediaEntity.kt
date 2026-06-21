package com.msi.data.db

data class MediaEntity(
    val path: String,
    val title: String,
    val duration: Long = 0L,
    val size: Long = 0L,
    val album: String = "Unknown Album",
    val artist: String = "Unknown Artist",
    val genre: String = "Unknown Genre",
    val year: String = "Unknown Year",
    val isVideo: Boolean = false,
    var isFavorite: Boolean = false,
    var recentlyPlayed: Long = 0L,
    var playCount: Int = 0,
    var lastPlayedPosition: Long = 0L
)

data class PlaylistEntity(
    val playlistId: Long = 0L,
    val name: String
)

data class PlaylistSongJoin(
    val playlistId: Long,
    val songPath: String
)
