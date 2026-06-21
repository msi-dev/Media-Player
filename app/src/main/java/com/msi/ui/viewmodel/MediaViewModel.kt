package com.msi.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.msi.data.db.MediaDatabase
import com.msi.data.db.MediaEntity
import com.msi.data.repository.MediaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MediaViewModel(application: Application) : AndroidViewModel(application) {
    private val db = MediaDatabase.getDatabase(application)
    private val repository = MediaRepository(application, db.mediaDao())

    // UI Navigation/View States
    private val _isScanning = MutableStateFlow(false)
    val isScanning = _isScanning.asStateFlow()

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab = _selectedTab.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _isPlayerExpanded = MutableStateFlow(false)
    val isPlayerExpanded = _isPlayerExpanded.asStateFlow()

    private val _isSidebarOpen = MutableStateFlow(false)
    val isSidebarOpen = _isSidebarOpen.asStateFlow()

    private val _currentFolder = MutableStateFlow<String?>(null)
    val currentFolder = _currentFolder.asStateFlow()

    // Library Data Flows
    val audioTracks: StateFlow<List<MediaEntity>> = repository.allAudio
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val videoTracks: StateFlow<List<MediaEntity>> = repository.allVideos
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteTracks: StateFlow<List<MediaEntity>> = repository.favorites
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Joined filtered list combining Search query & active lists
    val filteredAudio: StateFlow<List<MediaEntity>> = combine(audioTracks, _searchQuery) { list, query ->
        if (query.isBlank()) list else list.filter {
            it.title.contains(query, ignoreCase = true) || it.artist.contains(query, ignoreCase = true)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredVideos: StateFlow<List<MediaEntity>> = combine(videoTracks, _searchQuery) { list, query ->
        if (query.isBlank()) list else list.filter {
            it.title.contains(query, ignoreCase = true)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Folders parsing: group tracks by folder paths!
    val foldersWithCounts: StateFlow<List<Pair<String, Int>>> = combine(audioTracks, videoTracks) { audios, videos ->
        val map = mutableMapOf<String, Int>()
        (audios + videos).forEach { item ->
            val path = item.folderPath.ifBlank { "/sdcard" }
            map[path] = (map[path] ?: 0) + 1
        }
        map.toList().sortedBy { it.first }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Player State Variables
    val exoPlayer = ExoPlayer.Builder(application).build().apply {
        repeatMode = Player.REPEAT_MODE_ALL
    }

    private val _currentTrack = MutableStateFlow<MediaEntity?>(null)
    val currentTrack = _currentTrack.asStateFlow()

    private val _currentQueue = MutableStateFlow<List<MediaEntity>>(emptyList())
    val currentQueue = _currentQueue.asStateFlow()

    private val _currentTrackIndex = MutableStateFlow(-1)
    val currentTrackIndex = _currentTrackIndex.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _playbackPosition = MutableStateFlow(0L)
    val playbackPosition = _playbackPosition.asStateFlow()

    private val _trackDuration = MutableStateFlow(0L)
    val trackDuration = _trackDuration.asStateFlow()

    private var positionTrackerJob: Job? = null

    init {
        // Automatically start scanning device media or preloading mockup tracks
        triggerScanner()

        // Sync player status changes to local flows
        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                _isPlaying.value = playing
                if (playing) {
                    startTrackPositionTracker()
                } else {
                    stopTrackPositionTracker()
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                super.onMediaItemTransition(mediaItem, reason)
                val currentIndex = exoPlayer.currentMediaItemIndex
                if (currentIndex in _currentQueue.value.indices) {
                    _currentTrackIndex.value = currentIndex
                    _currentTrack.value = _currentQueue.value[currentIndex]
                }
                _trackDuration.value = exoPlayer.duration.coerceAtLeast(0L)
            }

            override fun onPlaybackStateChanged(state: Int) {
                super.onPlaybackStateChanged(state)
                if (state == Player.STATE_READY) {
                    _trackDuration.value = exoPlayer.duration.coerceAtLeast(0L)
                }
            }
        })
    }

    fun triggerScanner() {
        viewModelScope.launch {
            _isScanning.value = true
            try {
                repository.scanDeviceMedia()
            } catch (e: Exception) {
                repository.addDummyPreloadIfEmpty()
            } finally {
                delay(800) // Ensure smooth UI transition
                _isScanning.value = false
            }
        }
    }

    fun selectTab(tabIndex: Int) {
        _selectedTab.value = tabIndex
        if (tabIndex != 2) {
            _currentFolder.value = null
        }
    }

    fun enterFolder(folderPath: String) {
        _currentFolder.value = folderPath
    }

    fun exitFolder() {
        _currentFolder.value = null
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setPlayerExpanded(expanded: Boolean) {
        _isPlayerExpanded.value = expanded
    }

    fun setSidebarOpen(open: Boolean) {
        _isSidebarOpen.value = open
    }

    // Playback Controller Logic
    fun playMediaItem(media: MediaEntity, queue: List<MediaEntity>) {
        viewModelScope.launch {
            _currentQueue.value = queue
            val index = queue.indexOfFirst { it.path == media.path }.coerceAtLeast(0)
            _currentTrackIndex.value = index
            _currentTrack.value = media

            exoPlayer.stop()
            exoPlayer.clearMediaItems()

            queue.forEach { item ->
                exoPlayer.addMediaItem(MediaItem.fromUri(item.path))
            }

            exoPlayer.seekTo(index, 0L)
            exoPlayer.prepare()
            exoPlayer.play()

            // Automatically open visual media overlay or full player
            if (!media.isAudio) {
                // Video clicked: expand screen!
                _isPlayerExpanded.value = true
            }
        }
    }

    fun play() {
        exoPlayer.play()
    }

    fun pause() {
        exoPlayer.pause()
    }

    fun playPrevious() {
        if (exoPlayer.hasPreviousMediaItem()) {
            exoPlayer.seekToPrevious()
        }
    }

    fun playNext() {
        if (exoPlayer.hasNextMediaItem()) {
            exoPlayer.seekToNext()
        }
    }

    fun seekTo(positionMs: Long) {
        exoPlayer.seekTo(positionMs)
        _playbackPosition.value = positionMs
    }

    fun toggleFavorite(media: MediaEntity) {
        viewModelScope.launch {
            repository.toggleFavorite(media.id, !media.isFavorite)
            // Sync active track favourite status
            if (_currentTrack.value?.id == media.id) {
                _currentTrack.value = _currentTrack.value?.copy(isFavorite = !media.isFavorite)
            }
        }
    }

    fun importMediaFile(title: String, path: String, duration: Long, isAudio: Boolean, mimeType: String, size: Long = 0) {
        viewModelScope.launch {
            repository.insertManualUriMedia(title, path, duration, isAudio, mimeType, size)
            triggerScanner()
        }
    }

    private fun startTrackPositionTracker() {
        positionTrackerJob?.cancel()
        positionTrackerJob = viewModelScope.launch(Dispatchers.Main) {
            while (true) {
                _playbackPosition.value = exoPlayer.currentPosition
                delay(500)
            }
        }
    }

    private fun stopTrackPositionTracker() {
        positionTrackerJob?.cancel()
        positionTrackerJob = null
    }

    override fun onCleared() {
        exoPlayer.release()
        stopTrackPositionTracker()
        super.onCleared()
    }
}
