package com.msi.data.db

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.paging.PagingSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

class MediaDao(private val db: MediaDatabase) {

    private fun notifyChanged() {
        db.databaseTrigger.notifyChanged()
    }

    private fun <T> observeQuery(queryBlock: () -> T): Flow<T> {
        return flow {
            emit(queryBlock())
            db.databaseTrigger.triggers.collect {
                emit(queryBlock())
            }
        }.flowOn(Dispatchers.IO)
    }

    private fun cursorToMedia(c: Cursor): MediaEntity {
        return MediaEntity(
            path = c.getString(c.getColumnIndexOrThrow("path")),
            title = c.getString(c.getColumnIndexOrThrow("title")),
            duration = c.getLong(c.getColumnIndexOrThrow("duration")),
            size = c.getLong(c.getColumnIndexOrThrow("size")),
            album = c.getString(c.getColumnIndexOrThrow("album")),
            artist = c.getString(c.getColumnIndexOrThrow("artist")),
            genre = c.getString(c.getColumnIndexOrThrow("genre")),
            year = c.getString(c.getColumnIndexOrThrow("year")),
            isVideo = c.getInt(c.getColumnIndexOrThrow("isVideo")) == 1,
            isFavorite = c.getInt(c.getColumnIndexOrThrow("isFavorite")) == 1,
            recentlyPlayed = c.getLong(c.getColumnIndexOrThrow("recentlyPlayed")),
            playCount = c.getInt(c.getColumnIndexOrThrow("playCount")),
            lastPlayedPosition = c.getLong(c.getColumnIndexOrThrow("lastPlayedPosition"))
        )
    }

    suspend fun getMediaCount(): Int = withContext(Dispatchers.IO) {
        val cursor = db.readableDatabase.rawQuery("SELECT COUNT(*) FROM media_items", null)
        var count = 0
        if (cursor.moveToFirst()) {
            count = cursor.getInt(0)
        }
        cursor.close()
        count
    }

    fun getAllMedia(): Flow<List<MediaEntity>> = observeQuery {
        val list = mutableListOf<MediaEntity>()
        val cursor = db.readableDatabase.rawQuery("SELECT * FROM media_items ORDER BY title ASC", null)
        while (cursor.moveToNext()) {
            list.add(cursorToMedia(cursor))
        }
        cursor.close()
        list
    }

    fun getAllAudio(): Flow<List<MediaEntity>> = observeQuery {
        val list = mutableListOf<MediaEntity>()
        val cursor = db.readableDatabase.rawQuery("SELECT * FROM media_items WHERE isVideo = 0 ORDER BY title ASC", null)
        while (cursor.moveToNext()) {
            list.add(cursorToMedia(cursor))
        }
        cursor.close()
        list
    }

    fun getAudioListSync(limit: Int, offset: Int): List<MediaEntity> {
        val list = mutableListOf<MediaEntity>()
        val cursor = db.readableDatabase.rawQuery(
            "SELECT * FROM media_items WHERE isVideo = 0 ORDER BY title ASC LIMIT ? OFFSET ?",
            arrayOf(limit.toString(), offset.toString())
        )
        while (cursor.moveToNext()) {
            list.add(cursorToMedia(cursor))
        }
        cursor.close()
        return list
    }

    fun getAllAudioPaging(): PagingSource<Int, MediaEntity> {
        return SQLitePagingSource { limit, offset ->
            getAudioListSync(limit, offset)
        }
    }

    fun getAllVideos(): Flow<List<MediaEntity>> = observeQuery {
        val list = mutableListOf<MediaEntity>()
        val cursor = db.readableDatabase.rawQuery("SELECT * FROM media_items WHERE isVideo = 1 ORDER BY title ASC", null)
        while (cursor.moveToNext()) {
            list.add(cursorToMedia(cursor))
        }
        cursor.close()
        list
    }

    fun getVideoListSync(limit: Int, offset: Int): List<MediaEntity> {
        val list = mutableListOf<MediaEntity>()
        val cursor = db.readableDatabase.rawQuery(
            "SELECT * FROM media_items WHERE isVideo = 1 ORDER BY title ASC LIMIT ? OFFSET ?",
            arrayOf(limit.toString(), offset.toString())
        )
        while (cursor.moveToNext()) {
            list.add(cursorToMedia(cursor))
        }
        cursor.close()
        return list
    }

    fun getAllVideosPaging(): PagingSource<Int, MediaEntity> {
        return SQLitePagingSource { limit, offset ->
            getVideoListSync(limit, offset)
        }
    }

    fun getFavorites(): Flow<List<MediaEntity>> = observeQuery {
        val list = mutableListOf<MediaEntity>()
        val cursor = db.readableDatabase.rawQuery("SELECT * FROM media_items WHERE isFavorite = 1 ORDER BY title ASC", null)
        while (cursor.moveToNext()) {
            list.add(cursorToMedia(cursor))
        }
        cursor.close()
        list
    }

    fun getRecentlyPlayed(): Flow<List<MediaEntity>> = observeQuery {
        val list = mutableListOf<MediaEntity>()
        val cursor = db.readableDatabase.rawQuery("SELECT * FROM media_items WHERE recentlyPlayed > 0 ORDER BY recentlyPlayed DESC LIMIT 50", null)
        while (cursor.moveToNext()) {
            list.add(cursorToMedia(cursor))
        }
        cursor.close()
        list
    }

    suspend fun insertMedia(media: List<MediaEntity>) = withContext(Dispatchers.IO) {
        val database = db.writableDatabase
        database.beginTransaction()
        try {
            for (item in media) {
                val cv = ContentValues().apply {
                    put("path", item.path)
                    put("title", item.title)
                    put("duration", item.duration)
                    put("size", item.size)
                    put("album", item.album)
                    put("artist", item.artist)
                    put("genre", item.genre)
                    put("year", item.year)
                    put("isVideo", if (item.isVideo) 1 else 0)
                    put("isFavorite", if (item.isFavorite) 1 else 0)
                    put("recentlyPlayed", item.recentlyPlayed)
                    put("playCount", item.playCount)
                    put("lastPlayedPosition", item.lastPlayedPosition)
                }
                database.insertWithOnConflict("media_items", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            }
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
        notifyChanged()
    }

    suspend fun insertMediaSingle(media: MediaEntity) = withContext(Dispatchers.IO) {
        val database = db.writableDatabase
        val cv = ContentValues().apply {
            put("path", media.path)
            put("title", media.title)
            put("duration", media.duration)
            put("size", media.size)
            put("album", media.album)
            put("artist", media.artist)
            put("genre", media.genre)
            put("year", media.year)
            put("isVideo", if (media.isVideo) 1 else 0)
            put("isFavorite", if (media.isFavorite) 1 else 0)
            put("recentlyPlayed", media.recentlyPlayed)
            put("playCount", media.playCount)
            put("lastPlayedPosition", media.lastPlayedPosition)
        }
        database.insertWithOnConflict("media_items", null, cv, SQLiteDatabase.CONFLICT_IGNORE)
        notifyChanged()
    }

    suspend fun updateFavorite(path: String, isFav: Boolean) = withContext(Dispatchers.IO) {
        val cv = ContentValues().apply {
            put("isFavorite", if (isFav) 1 else 0)
        }
        db.writableDatabase.update("media_items", cv, "path = ?", arrayOf(path))
        notifyChanged()
    }

    suspend fun recordPlayback(path: String, timestamp: Long, position: Long) = withContext(Dispatchers.IO) {
        db.writableDatabase.execSQL(
            "UPDATE media_items SET recentlyPlayed = ?, playCount = playCount + 1, lastPlayedPosition = ? WHERE path = ?",
            arrayOf(timestamp, position, path)
        )
        notifyChanged()
    }

    suspend fun updateLastPlayedPosition(path: String, position: Long) = withContext(Dispatchers.IO) {
        val cv = ContentValues().apply {
            put("lastPlayedPosition", position)
        }
        db.writableDatabase.update("media_items", cv, "path = ?", arrayOf(path))
        notifyChanged()
    }

    suspend fun renameMediaByPath(path: String, newTitle: String) = withContext(Dispatchers.IO) {
        val cv = ContentValues().apply {
            put("title", newTitle)
        }
        db.writableDatabase.update("media_items", cv, "path = ?", arrayOf(path))
        notifyChanged()
    }

    suspend fun updateMediaMetadata(path: String, title: String, artist: String, album: String, genre: String, year: String) = withContext(Dispatchers.IO) {
        val cv = ContentValues().apply {
            put("title", title)
            put("artist", artist)
            put("album", album)
            put("genre", genre)
            put("year", year)
        }
        db.writableDatabase.update("media_items", cv, "path = ?", arrayOf(path))
        notifyChanged()
    }

    suspend fun deleteMediaByPath(path: String) = withContext(Dispatchers.IO) {
        db.writableDatabase.delete("media_items", "path = ?", arrayOf(path))
        notifyChanged()
    }

    suspend fun deleteMediaByPaths(paths: List<String>) = withContext(Dispatchers.IO) {
        if (paths.isEmpty()) return@withContext
        val database = db.writableDatabase
        database.beginTransaction()
        try {
            for (p in paths) {
                database.delete("media_items", "path = ?", arrayOf(p))
            }
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
        notifyChanged()
    }

    fun getAllPlaylists(): Flow<List<PlaylistEntity>> = observeQuery {
        val list = mutableListOf<PlaylistEntity>()
        val cursor = db.readableDatabase.rawQuery("SELECT * FROM playlists ORDER BY name ASC", null)
        while (cursor.moveToNext()) {
            list.add(PlaylistEntity(
                playlistId = cursor.getLong(cursor.getColumnIndexOrThrow("playlistId")),
                name = cursor.getString(cursor.getColumnIndexOrThrow("name"))
            ))
        }
        cursor.close()
        list
    }

    suspend fun insertPlaylist(playlist: PlaylistEntity): Long = withContext(Dispatchers.IO) {
        val cv = ContentValues().apply {
            put("name", playlist.name)
        }
        val id = db.writableDatabase.insert("playlists", null, cv)
        notifyChanged()
        id
    }

    suspend fun deletePlaylist(id: Long) = withContext(Dispatchers.IO) {
        db.writableDatabase.delete("playlists", "playlistId = ?", arrayOf(id.toString()))
        db.writableDatabase.delete("playlist_songs", "playlistId = ?", arrayOf(id.toString()))
        notifyChanged()
    }

    suspend fun renamePlaylist(id: Long, newName: String) = withContext(Dispatchers.IO) {
        val cv = ContentValues().apply {
            put("name", newName)
        }
        db.writableDatabase.update("playlists", cv, "playlistId = ?", arrayOf(id.toString()))
        notifyChanged()
    }

    suspend fun addSongToPlaylist(join: PlaylistSongJoin) = withContext(Dispatchers.IO) {
        val cv = ContentValues().apply {
            put("playlistId", join.playlistId)
            put("songPath", join.songPath)
        }
        db.writableDatabase.insertWithOnConflict("playlist_songs", null, cv, SQLiteDatabase.CONFLICT_IGNORE)
        notifyChanged()
    }

    suspend fun removeSongFromPlaylist(playlistId: Long, songPath: String) = withContext(Dispatchers.IO) {
        db.writableDatabase.delete("playlist_songs", "playlistId = ? AND songPath = ?", arrayOf(playlistId.toString(), songPath))
        notifyChanged()
    }

    suspend fun getMediaByPaths(paths: List<String>): List<MediaEntity> = withContext(Dispatchers.IO) {
        if (paths.isEmpty()) return@withContext emptyList<MediaEntity>()
        val list = mutableListOf<MediaEntity>()
        val placeholders = paths.joinToString(",") { "?" }
        val cursor = db.readableDatabase.rawQuery(
            "SELECT * FROM media_items WHERE path IN ($placeholders)",
            paths.toTypedArray()
        )
        while (cursor.moveToNext()) {
            list.add(cursorToMedia(cursor))
        }
        cursor.close()
        list
    }

    fun getSongsInPlaylist(playlistId: Long): Flow<List<MediaEntity>> = observeQuery {
        val list = mutableListOf<MediaEntity>()
        val cursor = db.readableDatabase.rawQuery(
            """
            SELECT media_items.* FROM media_items 
            INNER JOIN playlist_songs ON media_items.path = playlist_songs.songPath 
            WHERE playlist_songs.playlistId = ? 
            ORDER BY media_items.title ASC
            """.trimIndent(),
            arrayOf(playlistId.toString())
        )
        while (cursor.moveToNext()) {
            list.add(cursorToMedia(cursor))
        }
        cursor.close()
        list
    }

    suspend fun saveSetting(setting: AppSettingEntity) = withContext(Dispatchers.IO) {
        val cv = ContentValues().apply {
            put("key", setting.key)
            put("value", setting.value)
        }
        db.writableDatabase.insertWithOnConflict("app_settings", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        notifyChanged()
    }

    suspend fun getSettingValue(key: String): String? = withContext(Dispatchers.IO) {
        val cursor = db.readableDatabase.rawQuery("SELECT value FROM app_settings WHERE key = ?", arrayOf(key))
        var value: String? = null
        if (cursor.moveToFirst()) {
            value = cursor.getString(0)
        }
        cursor.close()
        value
    }
}
