package com.example.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.MediaEntity
import com.example.data.repository.MediaFolder
import com.example.data.repository.MediaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FolderBrowserViewModel(
    application: Application,
    private val repository: MediaRepository
) : AndroidViewModel(application) {

    private val TAG = "FolderBrowserViewModel"
    private val PAGE_SIZE = 50

    private val _folders = MutableStateFlow<List<MediaFolder>>(emptyList())
    val folders = _folders.asStateFlow()

    private val _currentFolder = MutableStateFlow<MediaFolder?>(null)
    val currentFolder = _currentFolder.asStateFlow()

    private val _folderItems = MutableStateFlow<List<MediaEntity>>(emptyList())
    val folderItems = _folderItems.asStateFlow()

    private val _isLoadingFolders = MutableStateFlow(false)
    val isLoadingFolders = _isLoadingFolders.asStateFlow()

    private val _isLoadingItems = MutableStateFlow(false)
    val isLoadingItems = _isLoadingItems.asStateFlow()

    private val _isAllItemsLoaded = MutableStateFlow(false)
    val isAllItemsLoaded = _isAllItemsLoaded.asStateFlow()

    private var currentOffset = 0

    init {
        loadFolders()
    }

    /**
     * Scan and load root storage media directories
     */
    fun loadFolders() {
        viewModelScope.launch {
            _isLoadingFolders.value = true
            try {
                val foldersList = repository.queryMediaFolders()
                _folders.value = foldersList
                Log.d(TAG, "Successfully loaded ${foldersList.size} media folders.")
            } catch (e: Exception) {
                Log.e(TAG, "Error querying media folders: ${e.message}")
            } finally {
                _isLoadingFolders.value = false
            }
        }
    }

    /**
     * Enter a specific folder and trigger lazy-load paging
     */
    fun enterFolder(folder: MediaFolder) {
        viewModelScope.launch {
            _currentFolder.value = folder
            _folderItems.value = emptyList()
            _isAllItemsLoaded.value = false
            currentOffset = 0
            loadNextPageOfItems()
        }
    }

    /**
     * Load next chunk page of mixed audio / video items
     */
    fun loadNextPageOfItems() {
        val folder = _currentFolder.value ?: return
        if (_isAllItemsLoaded.value || _isLoadingItems.value) return

        viewModelScope.launch {
            _isLoadingItems.value = true
            try {
                val newPage = repository.getPagedMediaItemsInFolder(
                    folderPath = folder.path,
                    limit = PAGE_SIZE,
                    offset = currentOffset
                )
                if (newPage.isEmpty()) {
                    _isAllItemsLoaded.value = true
                    Log.d(TAG, "All items loaded for folder: ${folder.path}")
                } else {
                    val currentList = _folderItems.value.toMutableList()
                    currentList.addAll(newPage)
                    _folderItems.value = currentList
                    currentOffset += newPage.size
                    Log.d(TAG, "Loaded page of ${newPage.size} items. New total: ${currentList.size}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading media page for folder ${folder.path}: ${e.message}")
            } finally {
                _isLoadingItems.value = false
            }
        }
    }

    /**
     * Exit child directory and return to home folders gallery listing
     */
    fun navigateBack(): Boolean {
        if (_currentFolder.value != null) {
            _currentFolder.value = null
            _folderItems.value = emptyList()
            _isAllItemsLoaded.value = false
            currentOffset = 0
            loadFolders()
            return true
        }
        return false
    }
}
