package com.msi.ui.viewmodel

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.msi.MediaPlayerApp
import com.msi.VideoPlaybackActivity
import com.msi.AudioPlaybackActivity
import com.msi.data.db.MediaEntity
import com.msi.data.db.PlaylistEntity
import android.net.Uri
import android.provider.OpenableColumns
import com.msi.data.repository.MediaRepository
import com.msi.data.repository.HiddenFolderRepository
import com.msi.playback.PlaybackManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import kotlinx.coroutines.flow.Flow

class MediaViewModel(
    application: Application,
    private val repository: MediaRepository,
    private val playbackManager: PlaybackManager,
    private val hiddenFolderRepository: HiddenFolderRepository
) : AndroidViewModel(application) {

    private val TAG = "MediaViewModel"

    private val _isScanning = MutableStateFlow(false)
    val isScanning = combine(
        _isScanning,
        com.msi.scanner.MediaScannerController.isScanning
    ) { local, service ->
        local || service
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

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

    val pagedAudio: Flow<PagingData<MediaEntity>> = Pager(
        config = PagingConfig(
            pageSize = 30,
            enablePlaceholders = false,
            initialLoadSize = 30
        )
    ) {
        repository.getAudioPagingSource()
    }.flow.cachedIn(viewModelScope)

    val pagedVideos: Flow<PagingData<MediaEntity>> = Pager(
        config = PagingConfig(
            pageSize = 30,
            enablePlaceholders = false,
            initialLoadSize = 30
        )
    ) {
        repository.getVideosPagingSource()
    }.flow.cachedIn(viewModelScope)

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

    private val _requestPlayerExpansion = MutableSharedFlow<Unit>(replay = 0)
    val requestPlayerExpansion = _requestPlayerExpansion.asSharedFlow()

    fun requestPlayerExpanded() {
        viewModelScope.launch {
            _requestPlayerExpansion.emit(Unit)
        }
    }

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

    // Configurable Sub-Tabs defaults ordered according to Tracks, Recent, Album, Favorite, Playlist, then others
    val defaultTabs = listOf("Tracks", "Recent", "Album", "Favorite", "Playlist", "Artists", "Genres")
    
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

    // Preferences DataStore Integration
    val settingsDataStore = (application as MediaPlayerApp).settingsDataStore

    val dynamicColorEnabled = settingsDataStore.dynamicColorEnabled.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), false
    )

    val isDarkThemePrefSetting = settingsDataStore.isDarkTheme.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), "SYSTEM"
    )

    val waveformStylePref = settingsDataStore.waveformStyle.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), "Wave"
    )

    val waveformColorPref = settingsDataStore.waveformColorType.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), "Accent"
    )

    val equalizerPresetPref = settingsDataStore.equalizerPreset.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), "Normal"
    )

    val scanAndroidFolderPref = settingsDataStore.scanAndroidFolder.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), false
    )

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

    private val _isVideoResumePlayEnabled = MutableStateFlow(prefs.getBoolean("video_resume_play_enabled", true))
    val isVideoResumePlayEnabled = _isVideoResumePlayEnabled.asStateFlow()

    fun setVideoResumePlayEnabled(enabled: Boolean) {
        _isVideoResumePlayEnabled.value = enabled
        prefs.edit().putBoolean("video_resume_play_enabled", enabled).apply()
    }

    private val _hasPlayedOnce = MutableStateFlow(prefs.getBoolean("has_played_once", false))
    val hasPlayedOnce = _hasPlayedOnce.asStateFlow()

    fun markHasPlayedOnce() {
        if (!prefs.getBoolean("has_played_once", false)) {
            prefs.edit().putBoolean("has_played_once", true).apply()
            _hasPlayedOnce.value = true
        }
    }

    private val _selectedPlaylistSongs = MutableStateFlow<List<MediaEntity>>(emptyList())
    val selectedPlaylistSongs = _selectedPlaylistSongs.asStateFlow()

    private val _activePlaylist = MutableStateFlow<PlaylistEntity?>(null)
    val activePlaylist = _activePlaylist.asStateFlow()

    // Video Playback configuration parameters
    val currentlyPlayingVideo = playbackManager.currentlyPlayingVideo

    fun setCurrentlyPlayingVideo(video: MediaEntity?) {
        playbackManager.setCurrentlyPlayingVideo(video)
    }

    val isVideoBackgroundPlayEnabled = playbackManager.isVideoBackgroundPlayEnabled

    fun setVideoBackgroundPlayEnabled(enabled: Boolean) {
        playbackManager.setVideoBackgroundPlayEnabled(enabled)
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

    private val _customPresets = MutableStateFlow<Map<String, List<Int>>>(emptyMap())
    val customPresets = _customPresets.asStateFlow()

    init {
        loadCustomPresets()
        // Automatically scan files once on creation via our async MediaScanner Service
        initScanMedia()

        // Persisted state and Smart Resume startup automation
        viewModelScope.launch {
            var hasResumed = false
            repository.allMedia.collect { allMediaList ->
                if (!hasResumed && allMediaList.isNotEmpty()) {
                    hasResumed = true
                    
                    val statePrefs = application.getSharedPreferences("playback_state_persistence", android.content.Context.MODE_PRIVATE)
                    val savedSongPath = statePrefs.getString("persisted_current_song_path", "") ?: ""
                    val savedPos = statePrefs.getLong("persisted_current_position", 0L)
                    val savedQueueStr = statePrefs.getString("persisted_playback_queue", "") ?: ""
                    
                    val dbShuffleVal = repository.getSettingValue("persisted_shuffle_enabled")
                    val dbRepeatVal = repository.getSettingValue("persisted_repeat_mode")
                    
                    val savedShuffle = dbShuffleVal?.toBoolean() ?: statePrefs.getBoolean("persisted_shuffle_enabled", false)
                    val savedRepeat = dbRepeatVal?.toIntOrNull() ?: statePrefs.getInt("persisted_repeat_mode", 0)

                    if (savedQueueStr.isNotEmpty()) {
                        val savedPaths = savedQueueStr.split(",").filter { it.isNotEmpty() }
                        if (savedPaths.isNotEmpty()) {
                            val dbItems = repository.getMediaByPaths(savedPaths)
                            if (dbItems.isNotEmpty()) {
                                val itemsMap = dbItems.associateBy { it.path }
                                val orderedList = savedPaths.mapNotNull { itemsMap[it] }

                                 if (orderedList.isNotEmpty()) {
                                    val activeIndex = orderedList.indexOfFirst { it.path == savedSongPath }.coerceAtLeast(0)
                                    playbackManager.setQueue(orderedList, activeIndex, playWhenReady = false)
                                    if (savedPos > 0L) {
                                        playbackManager.seekTo(savedPos)
                                    }
                                    
                                    // Apply saved shuffle
                                    if (savedShuffle != playbackManager.isShuffleEnabled.value) {
                                        playbackManager.toggleShuffle()
                                    }
                                    // Apply saved repeat mode
                                    var attempts = 0
                                    while (playbackManager.repeatMode.value != savedRepeat && attempts < 3) {
                                        playbackManager.toggleRepeatMode()
                                        attempts++
                                    }
                                    
                                    Log.i(TAG, "Successfully restored persisted queue of size ${orderedList.size}, track index $activeIndex at progress $savedPos ms.")
                                } else {
                                    fallbackSmartResume(allMediaList)
                                }
                            } else {
                                fallbackSmartResume(allMediaList)
                            }
                        } else {
                            fallbackSmartResume(allMediaList)
                        }
                    } else if (smartResumeEnabled.value) {
                        fallbackSmartResume(allMediaList)
                    }
                }
            }
        }
    }

    private fun fallbackSmartResume(allMediaList: List<MediaEntity>) {
        val lastItem = allMediaList.filter { it.recentlyPlayed > 0 }
            .maxByOrNull { it.recentlyPlayed }
        if (lastItem != null) {
            val targetPosition = lastItem.lastPlayedPosition
            Log.i(TAG, "Smart Resume Fallback: Restoring last active item ${lastItem.title} at $targetPosition ms.")
            
            val isVideo = lastItem.isVideo
            val rawCategoryList = if (isVideo) {
                allMediaList.filter { it.isVideo }
            } else {
                allMediaList.filter { !it.isVideo }
            }
            val listToLoad = if (rawCategoryList.any { it.path == lastItem.path }) rawCategoryList else listOf(lastItem)
            val indexToResume = listToLoad.indexOfFirst { it.path == lastItem.path }.coerceAtLeast(0)
            
            playbackManager.setQueue(listToLoad, indexToResume, playWhenReady = false)
            if (targetPosition > 0L) {
                playbackManager.seekTo(targetPosition)
            }
        }
    }

    fun search(query: String) {
        _searchQuery.value = query
    }

    fun forceScanMedia() {
        _isScanning.value = true
        com.msi.scanner.MediaScannerService.startScan(getApplication(), forceScan = true)
        _isScanning.value = false
    }

    private var hasScannedOnStartup = false

    fun initScanMedia() {
        if (!hasScannedOnStartup) {
            hasScannedOnStartup = true
            _isScanning.value = true
            com.msi.scanner.MediaScannerService.startScan(getApplication(), forceScan = false)
            _isScanning.value = false
        }
    }

    // Playback control interfaces
    fun playSongAtIndex(songs: List<MediaEntity>, index: Int) {
        markHasPlayedOnce()
        val selectedMedia = songs[index]
        if (selectedMedia.isVideo) {
            playbackManager.pause()
            setCurrentlyPlayingVideo(selectedMedia)
            
            val intent = Intent(getApplication(), VideoPlaybackActivity::class.java).apply {
                putExtra("extra_media_path", selectedMedia.path)
                putExtra("extra_media_title", selectedMedia.title)
                putExtra("extra_media_artist", selectedMedia.artist)
                putExtra("extra_media_album", selectedMedia.album)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            getApplication<android.app.Application>().startActivity(intent)
        } else {
            playbackManager.activeVideoPlayer?.stop()
            setCurrentlyPlayingVideo(null)
            
            if (playbackManager.currentSong.value?.path == selectedMedia.path) {
                if (!playbackManager.isPlaying.value) {
                    playbackManager.play()
                }
            } else {
                playbackManager.setQueue(songs, index, playWhenReady = true)
                recordPlaybackEvent(selectedMedia.path)
            }
            requestPlayerExpanded()
        }
    }

    fun playMediaDirectly(media: MediaEntity) {
        markHasPlayedOnce()
        if (media.isVideo) {
            playbackManager.pause()
            setCurrentlyPlayingVideo(media)
            
            val intent = Intent(getApplication(), VideoPlaybackActivity::class.java).apply {
                putExtra("extra_media_path", media.path)
                putExtra("extra_media_title", media.title)
                putExtra("extra_media_artist", media.artist)
                putExtra("extra_media_album", media.album)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            getApplication<android.app.Application>().startActivity(intent)
        } else {
            playbackManager.activeVideoPlayer?.stop()
            setCurrentlyPlayingVideo(null)
            
            if (playbackManager.currentSong.value?.path == media.path) {
                if (!playbackManager.isPlaying.value) {
                    playbackManager.play()
                }
            } else {
                playbackManager.setQueue(listOf(media), 0, playWhenReady = true)
            }
            requestPlayerExpanded()

            val intent = Intent(getApplication(), AudioPlaybackActivity::class.java).apply {
                putExtra("extra_media_path", media.path)
                putExtra("extra_media_title", media.title)
                putExtra("extra_media_artist", media.artist)
                putExtra("extra_media_album", media.album)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            getApplication<android.app.Application>().startActivity(intent)
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

    fun createPlaylistAndAddSong(name: String, songPath: String) {
        viewModelScope.launch {
            val playlistId = repository.createPlaylist(name)
            repository.addSongToPlaylist(playlistId, songPath)
        }
    }

    fun deletePlaylist(id: Long) {
        viewModelScope.launch {
            repository.deletePlaylist(id)
        }
    }

    fun renamePlaylist(id: Long, newName: String) {
        viewModelScope.launch {
            repository.renamePlaylist(id, newName)
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
    fun setEqualizerPreset(levels: List<Int>) = playbackManager.setEqualizerPreset(levels)
    fun setBassBoost(level: Int) = playbackManager.setBassBoost(level)
    fun setVirtualizer(level: Int) = playbackManager.setVirtualizer(level)
    fun setLoudnessEnhancer(gain: Int) = playbackManager.setLoudnessEnhancer(gain)

    fun loadCustomPresets() {
        val customPrefs = getApplication<Application>().getSharedPreferences("equalizer_custom_presets", android.content.Context.MODE_PRIVATE)
        val map = mutableMapOf<String, List<Int>>()
        customPrefs.all.forEach { (key, value) ->
            if (value is String) {
                try {
                    val list = value.split(",").map { it.toInt() }
                    if (list.size == 10) {
                        map[key] = list
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading custom preset $key: ${e.message}")
                }
            }
        }
        _customPresets.value = map
    }

    fun saveCustomPreset(name: String, bands: List<Int>) {
        if (bands.size != 10) return
        val customPrefs = getApplication<Application>().getSharedPreferences("equalizer_custom_presets", android.content.Context.MODE_PRIVATE)
        customPrefs.edit().putString(name, bands.joinToString(",")).apply()
        loadCustomPresets()
    }

    fun deleteCustomPreset(name: String) {
        val customPrefs = getApplication<Application>().getSharedPreferences("equalizer_custom_presets", android.content.Context.MODE_PRIVATE)
        customPrefs.edit().remove(name).apply()
        loadCustomPresets()
    }

    // Dark/Light toggle
    fun setThemePref(themeMode: Boolean?) {
        _isDarkTheme.value = themeMode
        if (themeMode != null) {
            prefs.edit().putBoolean("is_dark_theme", themeMode).apply()
        } else {
            prefs.edit().remove("is_dark_theme").apply()
        }
    }

    // New Preferences DataStore mutators
    fun setDynamicColorEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setDynamicColorEnabled(enabled)
        }
    }

    fun setIsDarkThemePrefSetting(theme: String) {
        viewModelScope.launch {
            settingsDataStore.setDarkTheme(theme)
            when (theme) {
                "DARK" -> setThemePref(true)
                "LIGHT" -> setThemePref(false)
                else -> setThemePref(null)
            }
        }
    }

    fun setWaveformStyle(style: String) {
        viewModelScope.launch {
            settingsDataStore.setWaveformStyle(style)
        }
    }

    fun setWaveformColorType(colorType: String) {
        viewModelScope.launch {
            settingsDataStore.setWaveformColorType(colorType)
        }
    }

    fun setEqualizerPresetSetting(presetName: String) {
        viewModelScope.launch {
            settingsDataStore.setEqualizerPreset(presetName)
            applyEqualizerPreset(presetName)
        }
    }

    fun setScanAndroidFolderSetting(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setScanAndroidFolder(enabled)
            forceScanMedia()
        }
    }

    fun applyEqualizerPreset(preset: String) {
        val bands = when (preset) {
            "Classic" -> listOf(4, 3, 2, 2, -1, -1, 0, 2, 3, 4)
            "Bass Boost" -> listOf(8, 6, 4, 2, 0, 0, 0, 0, 0, 0)
            "Vocal" -> listOf(-3, -2, -1, 1, 3, 4, 3, 2, 1, 0)
            "Acoustic" -> listOf(4, 3, 2, 0, 1, 1, 2, 3, 2, 1)
            else -> listOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0) // Flat
        }
        setEqualizerPreset(bands)
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

    fun updateMediaMetadata(path: String, title: String, artist: String, album: String, genre: String, year: String) {
        viewModelScope.launch {
            repository.updateMediaMetadata(path, title, artist, album, genre, year)
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

    fun importSelectedFile(uri: Uri, isVideo: Boolean) {
        viewModelScope.launch {
            try {
                getApplication<Application>().contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to take persistable URI permission: ${e.message}")
            }

            val contentResolver = getApplication<Application>().contentResolver
            var displayName = ""
            var size = 0L
            val projection = arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
            try {
                contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (nameIndex != -1) displayName = cursor.getString(nameIndex) ?: ""
                        if (sizeIndex != -1) size = cursor.getLong(sizeIndex)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error resolving selected document details: ${e.message}")
            }

            if (displayName.isEmpty()) {
                displayName = uri.lastPathSegment ?: "Imported File"
            }

            val cleanTitle = if (displayName.contains(".")) displayName.substringBeforeLast(".") else displayName

            val mediaEntity = MediaEntity(
                path = uri.toString(),
                title = cleanTitle,
                duration = 0L,
                size = size,
                album = if (isVideo) "Imported Video" else "Imported Audio",
                artist = "Local File",
                genre = "Imported",
                year = "",
                isVideo = isVideo
            )

            repository.insertMedia(mediaEntity)
            playMediaDirectly(mediaEntity)
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
