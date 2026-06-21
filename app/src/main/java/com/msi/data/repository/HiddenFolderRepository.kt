package com.msi.data.repository

import com.msi.data.db.HiddenFolderDao
import com.msi.data.db.HiddenFolderEntity
import kotlinx.coroutines.flow.Flow

class HiddenFolderRepository(private val hiddenFolderDao: HiddenFolderDao) {

    val allHiddenFolders: Flow<List<HiddenFolderEntity>> = hiddenFolderDao.getAllHiddenFoldersFlow()

    suspend fun getHiddenFoldersList(): List<HiddenFolderEntity> {
        return hiddenFolderDao.getAllHiddenFolders()
    }

    suspend fun insertHiddenFolder(path: String) {
        hiddenFolderDao.insertHiddenFolder(HiddenFolderEntity(folderPath = path))
    }

    suspend fun deleteHiddenFolder(path: String) {
        hiddenFolderDao.deleteHiddenFolder(path)
    }
}
