package com.msi.data.db

import android.content.ContentValues
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

class HiddenFolderDao(private val db: MediaDatabase) {

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

    fun getAllHiddenFoldersFlow(): Flow<List<HiddenFolderEntity>> = observeQuery {
        val list = mutableListOf<HiddenFolderEntity>()
        val cursor = db.readableDatabase.rawQuery("SELECT * FROM hidden_folders ORDER BY folderPath ASC", null)
        while (cursor.moveToNext()) {
            val path = cursor.getString(cursor.getColumnIndexOrThrow("folderPath"))
            list.add(HiddenFolderEntity(path))
        }
        cursor.close()
        list
    }

    suspend fun getAllHiddenFolders(): List<HiddenFolderEntity> = withContext(Dispatchers.IO) {
        val list = mutableListOf<HiddenFolderEntity>()
        val cursor = db.readableDatabase.rawQuery("SELECT * FROM hidden_folders", null)
        while (cursor.moveToNext()) {
            val path = cursor.getString(cursor.getColumnIndexOrThrow("folderPath"))
            list.add(HiddenFolderEntity(path))
        }
        cursor.close()
        list
    }

    suspend fun insertHiddenFolder(hiddenFolder: HiddenFolderEntity) = withContext(Dispatchers.IO) {
        val cv = ContentValues().apply {
            put("folderPath", hiddenFolder.folderPath)
        }
        db.writableDatabase.insertWithOnConflict("hidden_folders", null, cv, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE)
        notifyChanged()
    }

    suspend fun deleteHiddenFolder(path: String) = withContext(Dispatchers.IO) {
        db.writableDatabase.delete("hidden_folders", "folderPath = ?", arrayOf(path))
        notifyChanged()
    }
}
