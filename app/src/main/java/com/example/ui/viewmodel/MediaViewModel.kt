package com.example.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.MediaPlayerApp
import com.example.data.db.MediaEntity
import com.example.data.db.PlaylistEntity
import com.example.data.repository.MediaRepository
import com.example.data.repository.HiddenFolderRepository
import com.example.playback.PlaybackManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MediaViewModel(
    application: Application,
    private val repository: MediaRepository,
    private val playbackManager: PlaybackManager,
    private val hiddenFolderRepository: HiddenFolderRepository
) : AndroidViewModel(application) {

    private val TAG = "MediaViewModel"

    // Hidden Folders Flow
    val hiddenFolders = hiddenFolderRepository.allHiddenFolders.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    // Subscribed flows directly from the Database Repository (dynamically filtered by non-hidden directories)
    val allAudio = combine(repository.allAudio, hiddenFolders) { audio, hidden ->
        val hiddenPaths = hidden.map { it.folderPath }
        audio.filter { item ->
            hiddenPaths.none { hiddenPath -> item.path.startsWith(hiddenPath) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allVideos = combine(repository.allVideos, hiddenFolders) { videos, hidden ->
        val hiddenPaths = hidden.map { it.folderPath }
        videos.filter { item ->
            hiddenPaths.none { hiddenPath -> item.path.startsWith(hiddenPath) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favorites = repository.favorites.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val playlists = repository.playlists.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val recentlyPlayed = repository.recentlyPlayed.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Search query state
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    // Filtered lists combining Search Query + Data collections
    val filteredAudio = combine(allAudio, _searchQuery) { audio, query ->
        if (query.isEmpty()) audio
        else audio.filter { it.title.contains(query, ignoreCase = true) || it.artist.contains(query, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredVideos = combine(allVideos, _searchQuery) { videos, query ->
        if (query.isEmpty()) videos
        else videos.filter { it.title.contains(query, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Grouping calculations for library sections
    val albums = allAudio.combine(allAudio) { songs, _ ->
        songs.groupBy { it.album }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val artists = allAudio.combine(allAudio) { songs, _ ->
        songs.groupBy { it.artist }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val genres = allAudio.combine(allAudio) { songs, _ ->
        songs.groupBy { it.genre }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // Folder View calculated dynamically from full data set paths
    val folders = combine(allAudio, allVideos) { aud, vid ->
        val combinedPaths = (aud + vid)
        combinedPaths.groupBy { item ->
            val index = item.path.lastIndexOf('/')
            if (index != -1) {
                // If it represents assets, structure it as "Assets" folder
                if (item.path.startsWith("asset:///")) {
                    "Local Assets"
                } else {
                    item.path.substring(0, index).substringAfterLast('/')
                }
            } else {
                "Root Storage"
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // Aggregated real physical directory paths of all cataloged media files
    val allPhysicalFolders = combine(repository.allAudio, repository.allVideos) { aud, vid ->
        val combined = aud + vid
        combined.map { item ->
            val index = item.path.lastIndexOf('/')
            if (index != -1) {
                if (item.path.startsWith("asset:///")) {
                    "Local Assets"
                } else {
                    item.path.substring(0, index)
                }
            } else {
                "Root Storage"
            }
        }.distinct().filter { it != "Local Assets" && it != "Root Storage" }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Settings States (persisted via SharedPreferences, customizable in UI)
    private val prefs = application.getSharedPreferences("media_player_settings", android.content.Context.MODE_PRIVATE)

    private val preferenceChangeListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            "enabled_tabs_order" -> {
                _visibleTabs.value = loadSavedTabs()
            }
            "is_dark_theme" -> {
                _isDarkTheme.value = if (prefs.contains("is_dark_theme")) prefs.getBoolean("is_dark_theme", false) else null
            }
            "gesture_sensitivity" -> {
                _gestureSensitivity.value = prefs.getFloat("gesture_sensitivity", 1.0f)
            }
            "smart_resume_enabled" -> {
                _smartResumeEnabled.value = prefs.getBoolean("smart_resume_enabled", false)
            }
            "audio_focus_enabled" -> {
                _audioFocusEnabled.value = prefs.getBoolean("audio_focus_enabled", true)
            }
            "default_playback_speed" -> {
                _defaultPlaybackSpeed.value = prefs.getFloat("default_playback_speed", 1.0f)
            }
            "default_aspect_ratio" -> {
                _defaultAspectRatio.value = prefs.getString("default_aspect_ratio", "FIT") ?: "FIT"
            }
            "subtitle_enabled_default" -> {
                _subtitleEnabledDefault.value = prefs.getBoolean("subtitle_enabled_default", true)
            }
            "gesture_controls_enabled" -> {
                _gestureControlsEnabled.value = prefs.getBoolean("gesture_controls_enabled", true)
            }
        }
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(preferenceChangeListener)
    }

    // Configurable Sub-Tabs defaults ordered according to Tracks, Album, Favorite, Playlist, then others
    val defaultTabs = listOf("Tracks", "Album", "Favorite", "Playlist", "Artists", "Genres")
    
    private val _visibleTabs = MutableStateFlow<List<String>>(loadSavedTabs())
    val visibleTabs = _visibleTabs.asStateFlow()

    private fun loadSavedTabs(): List<String> {
        val tabString = prefs.getString("enabled_tabs_order", null)
        if (tabString != null) {
            val list = tabString.split(",").filter { it.isNotEmpty() }
            if (list.isNotEmpty()) {
                return list.filter { defaultTabs.contains(it) }
            }
        }
        return defaultTabs
    }

    fun saveVisibleTabs(tabs: List<String>) {
        _visibleTabs.value = tabs
        prefs.edit().putString("enabled_tabs_order", tabs.joinToString(",")).apply()
    }

    private val _isDarkTheme = MutableStateFlow<Boolean?>(
        if (prefs.contains("is_dark_theme")) prefs.getBoolean("is_dark_theme", false) else null
    )
    val isDarkTheme = _isDarkTheme.asStateFlow()

    private val _gestureSensitivity = MutableStateFlow(prefs.getFloat("gesture_sensitivity", 1.0f))
    val gestureSensitivity = _gestureSensitivity.asStateFlow()

    private val _smartResumeEnabled = MutableStateFlow(prefs.getBoolean("smart_resume_enabled", false))
    val smartResumeEnabled = _smartResumeEnabled.asStateFlow()

    private val _audioFocusEnabled = MutableStateFlow(prefs.getBoolean("audio_focus_enabled", true))
    val audioFocusEnabled = _audioFocusEnabled.asStateFlow()

    private val _defaultPlaybackSpeed = MutableStateFlow(prefs.getFloat("default_playback_speed", 1.0f))
    val defaultPlaybackSpeed = _defaultPlaybackSpeed.asStateFlow()

    private val _defaultAspectRatio = MutableStateFlow(prefs.getString("default_aspect_ratio", "FIT") ?: "FIT")
    val defaultAspectRatio = _defaultAspectRatio.asStateFlow()

    private val _subtitleEnabledDefault = MutableStateFlow(prefs.getBoolean("subtitle_enabled_default", true))
    val subtitleEnabledDefault = _subtitleEnabledDefault.asStateFlow()

    private val _gestureControlsEnabled = MutableStateFlow(prefs.getBoolean("gesture_controls_enabled", true))
    val gestureControlsEnabled = _gestureControlsEnabled.asStateFlow()

    private val _selectedPlaylistSongs = MutableStateFlow<List<MediaEntity>>(emptyList())
    val selectedPlaylistSongs = _selectedPlaylistSongs.asStateFlow()

    private val _activePlaylist = MutableStateFlow<PlaylistEntity?>(null)
    val activePlaylist = _activePlaylist.asStateFlow()

    // Video Playback configuration parameters
    private val _currentlyPlayingVideo = MutableStateFlow<MediaEntity?>(null)
    val currentlyPlayingVideo = _currentlyPlayingVideo.asStateFlow()

    fun setCurrentlyPlayingVideo(video: MediaEntity?) {
        _currentlyPlayingVideo.value = video
    }

    private val _videoPlaybackSpeed = MutableStateFlow(1.0f)
    val videoPlaybackSpeed = _videoPlaybackSpeed.asStateFlow()

    private val _videoAspectRatio = MutableStateFlow(AspectRatioMode.FIT)
    val videoAspectRatio = _videoAspectRatio.asStateFlow()

    // Subtitle configurations
    private val _subtitleSize = MutableStateFlow(18f) // sp
    val subtitleSize = _subtitleSize.asStateFlow()

    val subtitleColors = listOf("White", "Yellow", "Cyan", "Green")
    private val _subtitleColor = MutableStateFlow("White")
    val subtitleColor = _subtitleColor.asStateFlow()

    private val _subtitleBackground = MutableStateFlow(true)
    val subtitleBackground = _subtitleBackground.asStateFlow()

    private val _subtitleDelay = MutableStateFlow(0) // milliseconds offset
    val subtitleDelay = _subtitleDelay.asStateFlow()

    // Active Playback states linked directly to the Singleton playbackManager
    val currentSong = playbackManager.currentSong
    val isPlaying = playbackManager.isPlaying
    val spectrumData = playbackManager.visualizerManager.spectrumData
    val playbackQueue = playbackManager.playbackQueue
    val isShuffleEnabled = playbackManager.isShuffleEnabled
    val repeatMode = playbackManager.repeatMode
    val playbackSpeed = playbackManager.playbackSpeed
    val pitch = playbackManager.pitch
    val balance = playbackManager.balance
    val currentPosition = playbackManager.currentPosition
    val duration = playbackManager.duration
    val sleepTimerRemaining = playbackManager.sleepTimerRemaining

    val eqEnabled = playbackManager.eqEnabled
    val eqBands = playbackManager.eqBands
    val bassBoost = playbackManager.bassBoostLevel
    val virtualizer = playbackManager.virtualizerLevel
    val loudnessEnhancer = playbackManager.loudnessEnhancerGain

    init {
        // Automatically scan files on creation (smart cached startup loads Room instantly)
        viewModelScope.launch {
            repository.scanLocalMedia(forceScan = false)
        }

        // Smart Resume startup automation
        viewModelScope.launch {
            var hasResumed = false
            repository.allMedia.collect { allMediaList ->
                if (!hasResumed && allMediaList.isNotEmpty()) {
                    if (smartResumeEnabled.value) {
                        hasResumed = true
                        val lastItem = allMediaList.filter { it.recentlyPlayed > 0 }
                            .maxByOrNull { it.recentlyPlayed }
                        if (lastItem != null) {
                            val targetPosition = lastItem.lastPlayedPosition
                            Log.i(TAG, "Smart Resume: Restoring last active item ${lastItem.title} at $targetPosition ms.")
                            
                            val isVideo = lastItem.isVideo
                            val rawCategoryList = if (isVideo) {
                                allMediaList.filter { it.isVideo }
                            } else {
                                allMediaList.filter { !it.isVideo }
                            }
                            val listToLoad = if (rawCategoryList.any { it.path == lastItem.path }) rawCategoryList else listOf(lastItem)
                            val indexToResume = listToLoad.indexOfFirst { it.path == lastItem.path }.coerceAtLeast(0)
                            
                            playbackManager.setQueue(listToLoad, indexToResume)
                            if (targetPosition > 0L) {
                                playbackManager.seekTo(targetPosition)
                            }
                            playbackManager.play()
                        }
                    }
                }
            }
        }
    }

    fun search(query: String) {
        _searchQuery.value = query
    }

    fun forceScanMedia() {
        viewModelScope.launch {
            repository.scanLocalMedia(forceScan = true)
        }
    }

    // Playback control interfaces
    fun playSongAtIndex(songs: List<MediaEntity>, index: Int) {
        val selectedMedia = songs[index]
        if (selectedMedia.isVideo) {
            _currentlyPlayingVideo.value = selectedMedia
        } else {
            playbackManager.setQueue(songs, index)
            recordPlaybackEvent(selectedMedia.path)
        }
    }

    fun playMediaDirectly(media: MediaEntity) {
        if (media.isVideo) {
            _currentlyPlayingVideo.value = media
        } else {
            playbackManager.setQueue(listOf(media), 0)
            playbackManager.play()
        }
    }

    fun play() = playbackManager.play()
    fun pause() = playbackManager.pause()
    fun stop() = playbackManager.stop()
    fun playNext() = playbackManager.playNext()
    fun playPrevious() = playbackManager.playPrevious()
    fun toggleShuffle() = playbackManager.toggleShuffle()
    fun toggleRepeatMode() = playbackManager.toggleRepeatMode()
    fun seekTo(positionMs: Long) = playbackManager.seekTo(positionMs)

    fun setPlaybackSpeed(speed: Float) {
        playbackManager.setPlaybackSpeed(speed)
    }

    fun setPitch(pitch: Float) {
        playbackManager.setPitch(pitch)
    }

    fun setBalance(bal: Float) {
        playbackManager.setBalance(bal)
    }

    // Favorite mechanics
    fun toggleFavorite(item: MediaEntity) {
        viewModelScope.launch {
            repository.toggleFavorite(item.path, !item.isFavorite)
        }
    }

    private fun recordPlaybackEvent(path: String) {
        viewModelScope.launch {
            repository.recordPlayback(path)
        }
    }

    fun updateLastPlayedProgress(path: String, position: Long) {
        viewModelScope.launch {
            repository.updateLastPlayedPosition(path, position)
        }
    }

    // Playlist Interfaces
    fun createPlaylist(name: String) {
        viewModelScope.launch {
            repository.createPlaylist(name)
        }
    }

    fun deletePlaylist(id: Long) {
        viewModelScope.launch {
            repository.deletePlaylist(id)
        }
    }

    fun addSongToPlaylist(playlistId: Long, songPath: String) {
        viewModelScope.launch {
            repository.addSongToPlaylist(playlistId, songPath)
        }
    }

    fun removeSongFromPlaylist(playlistId: Long, songPath: String) {
        viewModelScope.launch {
            repository.removeSongFromPlaylist(playlistId, songPath)
        }
    }

    fun selectPlaylist(playlist: PlaylistEntity) {
        _activePlaylist.value = playlist
        viewModelScope.launch {
            repository.getSongsInPlaylist(playlist.playlistId).collect { songs ->
                _selectedPlaylistSongs.value = songs
            }
        }
    }

    // Queue actions
    fun playNext(song: MediaEntity) = playbackManager.playNext(song)
    fun addToQueue(song: MediaEntity) = playbackManager.addToQueue(song)
    fun removeFromQueue(songPath: String) = playbackManager.removeFromQueue(songPath)
    fun reorderQueue(from: Int, to: Int) = playbackManager.reorderQueue(from, to)
    fun clearQueue() = playbackManager.clearQueue()

    // Sleep Timer action
    fun setSleepTimer(minutes: Int) = playbackManager.setSleepTimer(minutes)

    // Equalizer controls
    fun toggleEqualizer(enabled: Boolean) = playbackManager.toggleEqualizer(enabled)
    fun setEqualizerBand(band: Int, level: Int) = playbackManager.setEqualizerBand(band, level)
    fun setBassBoost(level: Int) = playbackManager.setBassBoost(level)
    fun setVirtualizer(level: Int) = playbackManager.setVirtualizer(level)
    fun setLoudnessEnhancer(gain: Int) = playbackManager.setLoudnessEnhancer(gain)

    // Dark/Light toggle
    fun setThemePref(themeMode: Boolean?) {
        _isDarkTheme.value = themeMode
        if (themeMode != null) {
            prefs.edit().putBoolean("is_dark_theme", themeMode).apply()
        } else {
            prefs.edit().remove("is_dark_theme").apply()
        }
    }

    fun setGestureSensitivity(value: Float) {
        _gestureSensitivity.value = value
        prefs.edit().putFloat("gesture_sensitivity", value).apply()
    }

    fun setSmartResumeEnabled(enabled: Boolean) {
        _smartResumeEnabled.value = enabled
        prefs.edit().putBoolean("smart_resume_enabled", enabled).apply()
    }

    fun setAudioFocusEnabled(enabled: Boolean) {
        _audioFocusEnabled.value = enabled
        prefs.edit().putBoolean("audio_focus_enabled", enabled).apply()
    }

    fun setDefaultPlaybackSpeed(speed: Float) {
        _defaultPlaybackSpeed.value = speed
        prefs.edit().putFloat("default_playback_speed", speed).apply()
    }

    fun setDefaultAspectRatio(mode: String) {
        _defaultAspectRatio.value = mode
        prefs.edit().putString("default_aspect_ratio", mode).apply()
    }

    fun setSubtitleEnabledDefault(enabled: Boolean) {
        _subtitleEnabledDefault.value = enabled
        prefs.edit().putBoolean("subtitle_enabled_default", enabled).apply()
    }

    fun setGestureControlsEnabled(enabled: Boolean) {
        _gestureControlsEnabled.value = enabled
        prefs.edit().putBoolean("gesture_controls_enabled", enabled).apply()
    }

    // Subtitle configurations
    fun setSubtitleSize(size: Float) { _subtitleSize.value = size }
    fun setSubtitleColor(color: String) { _subtitleColor.value = color }
    fun toggleSubtitleBackground(show: Boolean) { _subtitleBackground.value = show }
    fun setSubtitleDelay(delayMs: Int) { _subtitleDelay.value = delayMs }

    // Video Player Custom Speeds & Actions
    fun setVideoPlaybackSpeed(speed: Float) { _videoPlaybackSpeed.value = speed }
    fun setVideoAspectRatio(mode: AspectRatioMode) { _videoAspectRatio.value = mode }

    // Deletion support
    fun deleteMediaByPath(path: String) {
        viewModelScope.launch {
            repository.deleteMediaByPath(path)
        }
    }

    fun deleteMediaByPaths(paths: List<String>) {
        viewModelScope.launch {
            repository.deleteMediaByPaths(paths)
        }
    }

    fun renameMediaByPath(path: String, newTitle: String) {
        viewModelScope.launch {
            repository.renameMediaByPath(path, newTitle)
        }
    }

    // Hidden Folders settings controllers
    fun addHiddenFolder(path: String) {
        viewModelScope.launch {
            hiddenFolderRepository.insertHiddenFolder(path)
            forceScanMedia()
        }
    }

    fun removeHiddenFolder(path: String) {
        viewModelScope.launch {
            hiddenFolderRepository.deleteHiddenFolder(path)
            forceScanMedia()
        }
    }

    // Cache and Database maintenance
    fun clearCache() {
        try {
            val app = getApplication<Application>()
            app.cacheDir.deleteRecursively()
            Log.i(TAG, "Cache directories deleted successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear application cache: ${e.message}")
        }
    }

    fun performDatabaseMaintenance() {
        viewModelScope.launch {
            forceScanMedia()
            Log.i(TAG, "Database maintenance completed successfully.")
        }
    }

    override fun onCleared() {
        super.onCleared()
        prefs.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener)
    }
}

enum class AspectRatioMode {
    FIT, STRETCH, CROP, FILL
}

// ViewModel Factory supporting manual Constructor Injection container
class MediaViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MediaViewModel::class.java)) {
            val app = application as MediaPlayerApp
            @Suppress("UNCHECKED_CAST")
            return MediaViewModel(
                application,
                app.mediaRepository,
                app.playbackManager,
                app.hiddenFolderRepository
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
