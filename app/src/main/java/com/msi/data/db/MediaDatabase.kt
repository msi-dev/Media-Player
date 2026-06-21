package com.msi.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class MediaDatabase private constructor(context: Context) {
    private val dbHelper = DatabaseHelper(context.applicationContext)
    val databaseTrigger = DatabaseTrigger()

    private val mediaDaoInstance = MediaDao(this)
    private val hiddenFolderDaoInstance = HiddenFolderDao(this)

    fun mediaDao(): MediaDao = mediaDaoInstance
    fun hiddenFolderDao(): HiddenFolderDao = hiddenFolderDaoInstance

    val writableDatabase: SQLiteDatabase
        get() = dbHelper.writableDatabase

    val readableDatabase: SQLiteDatabase
        get() = dbHelper.readableDatabase

    private class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, "media_database", null, 4) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS media_items (
                    path TEXT PRIMARY KEY,
                    title TEXT NOT NULL,
                    duration INTEGER NOT NULL DEFAULT 0,
                    size INTEGER NOT NULL DEFAULT 0,
                    album TEXT NOT NULL DEFAULT 'Unknown Album',
                    artist TEXT NOT NULL DEFAULT 'Unknown Artist',
                    genre TEXT NOT NULL DEFAULT 'Unknown Genre',
                    year TEXT NOT NULL DEFAULT 'Unknown Year',
                    isVideo INTEGER NOT NULL DEFAULT 0,
                    isFavorite INTEGER NOT NULL DEFAULT 0,
                    recentlyPlayed INTEGER NOT NULL DEFAULT 0,
                    playCount INTEGER NOT NULL DEFAULT 0,
                    lastPlayedPosition INTEGER NOT NULL DEFAULT 0
                )
            """.trimIndent())

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS playlists (
                    playlistId INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL
                )
            """.trimIndent())

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS playlist_songs (
                    playlistId INTEGER NOT NULL,
                    songPath TEXT NOT NULL,
                    PRIMARY KEY(playlistId, songPath)
                )
            """.trimIndent())

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS hidden_folders (
                    folderPath TEXT PRIMARY KEY
                )
            """.trimIndent())

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS app_settings (
                    key TEXT PRIMARY KEY,
                    value TEXT NOT NULL
                )
            """.trimIndent())
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            db.execSQL("DROP TABLE IF EXISTS media_items")
            db.execSQL("DROP TABLE IF EXISTS playlists")
            db.execSQL("DROP TABLE IF EXISTS playlist_songs")
            db.execSQL("DROP TABLE IF EXISTS hidden_folders")
            db.execSQL("DROP TABLE IF EXISTS app_settings")
            onCreate(db)
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: MediaDatabase? = null

        fun getDatabase(context: Context): MediaDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = MediaDatabase(context)
                INSTANCE = instance
                instance
            }
        }
    }
}
