package com.example.mediaplayer

import android.app.Application
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import android.util.Log
import androidx.annotation.OptIn
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.example.mediaplayer.data.HistoryEntity
import com.example.mediaplayer.data.MediaDatabase
import com.example.mediaplayer.data.MediaRepository
import com.example.mediaplayer.data.PlaylistEntity
import com.example.mediaplayer.data.SongEntity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MediaViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: MediaRepository
    private var exoPlayer: ExoPlayer? = null

    // Room Database Flows
    val allSongs = MutableStateFlow<List<SongEntity>>(emptyList())
    val favoriteSongs = MutableStateFlow<List<SongEntity>>(emptyList())
    val allPlaylists = MutableStateFlow<List<PlaylistEntity>>(emptyList())
    val playbackHistory = MutableStateFlow<List<HistoryEntity>>(emptyList())

    // Currently selected playlist songs
    private val _songsInPlaylist = MutableStateFlow<List<SongEntity>>(emptyList())
    val songsInPlaylist: StateFlow<List<SongEntity>> = _songsInPlaylist.asStateFlow()

    // Player Playback States
    private val _currentPlayingSong = MutableStateFlow<SongEntity?>(null)
    val currentPlayingSong: StateFlow<SongEntity?> = _currentPlayingSong.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _playbackProgress = MutableStateFlow(0L)
    val playbackProgress: StateFlow<Long> = _playbackProgress.asStateFlow()

    private val _playbackDuration = MutableStateFlow(0L)
    val playbackDuration: StateFlow<Long> = _playbackDuration.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _isShuffleEnabled = MutableStateFlow(false)
    val isShuffleEnabled: StateFlow<Boolean> = _isShuffleEnabled.asStateFlow()

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    // Sound Processing States
    private val _isEqualizerEnabled = MutableStateFlow(false)
    val isEqualizerEnabled: StateFlow<Boolean> = _isEqualizerEnabled.asStateFlow()

    private val _eqBands = MutableStateFlow<List<EqBand>>(emptyList())
    val eqBands: StateFlow<List<EqBand>> = _eqBands.asStateFlow()

    private val _bassBoostLevel = MutableStateFlow(0) // 0 to 1000
    val bassBoostLevel: StateFlow<Int> = _bassBoostLevel.asStateFlow()

    private val _virtualizerLevel = MutableStateFlow(0) // 0 to 1000
    val virtualizerLevel: StateFlow<Int> = _virtualizerLevel.asStateFlow()

    private val _selectedEqPreset = MutableStateFlow("Custom")
    val selectedEqPreset: StateFlow<String> = _selectedEqPreset.asStateFlow()

    // Hardware Audio Effect Holders
    private var hardwareEqualizer: Equalizer? = null
    private var hardwareBassBoost: BassBoost? = null
    private var hardwareVirtualizer: Virtualizer? = null

    private var progressJob: Job? = null

    init {
        val database = MediaDatabase.getDatabase(application)
        repository = MediaRepository(application, database.mediaDao())

        // Collect database data
        viewModelScope.launch {
            repository.allSongs.collectLatest { allSongs.value = it }
        }
        viewModelScope.launch {
            repository.favoriteSongs.collectLatest { favoriteSongs.value = it }
        }
        viewModelScope.launch {
            repository.allPlaylists.collectLatest { allPlaylists.value = it }
        }
        viewModelScope.launch {
            repository.playbackHistory.collectLatest { playbackHistory.value = it }
        }

        // Initialize Player on main
        initPlayer(application)

        // Initial scan of media store
        viewModelScope.launch {
            repository.scanLocalMedia()
        }
    }

    private fun initPlayer(context: Application) {
        try {
            // Use attribution context in Android 11+ for correct AppOps tracking
            val attributionContext = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                context.createAttributionContext("media_playback")
            } else {
                context
            }
            exoPlayer = ExoPlayer.Builder(attributionContext).build().apply {
                repeatMode = Player.REPEAT_MODE_OFF
                
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_READY) {
                            _playbackDuration.value = duration
                            startProgressTicker()
                        } else if (state == Player.STATE_ENDED) {
                            stopProgressTicker()
                            _isPlaying.value = false
                        }
                    }

                    override fun onIsPlayingChanged(playing: Boolean) {
                        _isPlaying.value = playing
                        if (playing) {
                            startProgressTicker()
                            // Register to history
                            currentPlayingSong.value?.let { song ->
                                viewModelScope.launch {
                                    repository.addToHistory(
                                        song.id,
                                        song.title,
                                        song.artist,
                                        song.duration,
                                        song.mediaType
                                    )
                                }
                            }
                        } else {
                            stopProgressTicker()
                        }
                    }

                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        mediaItem?.let { item ->
                            val currentSongId = item.mediaId
                            val songObj = allSongs.value.find { it.id == currentSongId }
                            _currentPlayingSong.value = songObj
                        }
                    }
                })
            }
            
            // Link Equalizer once player instance is ready
            setupAudioEffects()
        } catch (e: Exception) {
            Log.e("MediaViewModel", "Error creating player: ${e.message}")
        }
    }

    private fun setupAudioEffects() {
        val player = exoPlayer ?: return
        val sessionId = player.audioSessionId
        if (sessionId == 0) {
            Log.d("MediaViewModel", "Audio session ID is 0, setup deferred.")
            return
        }
        try {
            // Equalizer
            hardwareEqualizer = Equalizer(0, sessionId).apply {
                enabled = _isEqualizerEnabled.value
            }

            // Read hardware EQ bands
            val bandsCount = hardwareEqualizer?.numberOfBands?.toInt() ?: 5
            val bandsList = mutableListOf<EqBand>()
            val range = hardwareEqualizer?.bandLevelRange ?: shortArrayOf(-1500, 1500)
            val minLevel = range[0].toInt()
            val maxLevel = range[1].toInt()

            for (i in 0 until bandsCount) {
                val centerFreq = hardwareEqualizer?.getCenterFreq(i.toShort()) ?: 0
                val label = formatFreq(centerFreq)
                bandsList.add(EqBand(id = i, label = label, minLevel = minLevel, maxLevel = maxLevel, level = 0))
            }
            _eqBands.value = bandsList

            // Bass Boost
            hardwareBassBoost = BassBoost(0, sessionId).apply {
                enabled = true
                if (strengthSupported) {
                    setStrength(_bassBoostLevel.value.toShort())
                }
            }

            // Virtualizer
            hardwareVirtualizer = Virtualizer(0, sessionId).apply {
                enabled = true
                if (strengthSupported) {
                    setStrength(_virtualizerLevel.value.toShort())
                }
            }
        } catch (e: Exception) {
            Log.e("MediaViewModel", "Error in AudioFX setup: ${e.message}")
        }
    }

    private fun formatFreq(milliHz: Int): String {
        val hz = milliHz / 1000
        return if (hz >= 1000) "${hz / 1000} kHz" else "$hz Hz"
    }

    // Playback functions
    fun playSong(song: SongEntity, playlist: List<SongEntity> = allSongs.value) {
        val player = exoPlayer ?: return
        player.stop()
        player.clearMediaItems()

        // Sync Audio Session on new play
        val sessionId = player.audioSessionId
        if (hardwareEqualizer == null && sessionId != 0) {
            setupAudioEffects()
        }

        _playbackDuration.value = song.duration
        _currentPlayingSong.value = song

        // Populate player playlist
        val activeQueue = if (_isShuffleEnabled.value) {
            // Keep current song at front in shuffle
            listOf(song) + (playlist.filter { it.id != song.id }.shuffled())
        } else {
            val idx = playlist.indexOfFirst { it.id == song.id }
            if (idx >= 0) {
                playlist.subList(idx, playlist.size) + playlist.subList(0, idx)
            } else {
                playlist
            }
        }

        activeQueue.forEach { item ->
            player.addMediaItem(
                MediaItem.Builder()
                    .setMediaId(item.id)
                    .setUri(item.id)
                    .build()
            )
        }

        player.prepare()
        player.play()
    }

    fun togglePlayPause() {
        val player = exoPlayer ?: return
        if (player.isPlaying) {
            player.pause()
        } else {
            if (player.mediaItemCount == 0 && allSongs.value.isNotEmpty()) {
                playSong(allSongs.value.first())
            } else {
                player.play()
            }
        }
    }

    fun playNext() {
        val player = exoPlayer ?: return
        if (player.hasNextMediaItem()) {
            player.seekToNextMediaItem()
        } else {
            // wrap around
            player.seekToDefaultPosition(0)
        }
    }

    fun playPrevious() {
        val player = exoPlayer ?: return
        if (player.hasPreviousMediaItem()) {
            player.seekToPreviousMediaItem()
        } else {
            // wrap around to the end of index
            player.seekToDefaultPosition(player.mediaItemCount - 1)
        }
    }

    fun seekTo(position: Long) {
        val player = exoPlayer ?: return
        player.seekTo(position)
        _playbackProgress.value = position
    }

    fun setShuffleEnabled(enabled: Boolean) {
        _isShuffleEnabled.value = enabled
        // Toggle shuffle mode on player
        exoPlayer?.shuffleModeEnabled = enabled
    }

    fun setRepeatMode(mode: Int) {
        _repeatMode.value = mode
        exoPlayer?.repeatMode = mode
    }

    fun setPlaybackSpeed(speed: Float) {
        _playbackSpeed.value = speed
        exoPlayer?.setPlaybackSpeed(speed)
    }

    // Audio Effects sliders handlers
    fun setEqualizerEnabled(enabled: Boolean) {
        _isEqualizerEnabled.value = enabled
        hardwareEqualizer?.enabled = enabled
    }

    fun updateEqBandLevel(bandId: Int, level: Int) {
        val updatedBands = _eqBands.value.map { band ->
            if (band.id == bandId) {
                hardwareEqualizer?.setBandLevel(bandId.toShort(), level.toShort())
                _selectedEqPreset.value = "Custom"
                band.copy(level = level)
            } else {
                band
            }
        }
        _eqBands.value = updatedBands
    }

    fun setBassBoostLevel(level: Int) {
        _bassBoostLevel.value = level
        try {
            if (hardwareBassBoost?.strengthSupported == true) {
                hardwareBassBoost?.setStrength(level.toShort())
            }
        } catch (e: Exception) {
            Log.e("MediaViewModel", "Error settings Bass Boost: ${e.message}")
        }
    }

    fun setVirtualizerLevel(level: Int) {
        _virtualizerLevel.value = level
        try {
            if (hardwareVirtualizer?.strengthSupported == true) {
                hardwareVirtualizer?.setStrength(level.toShort())
            }
        } catch (e: Exception) {
            Log.e("MediaViewModel", "Error settings Virtualizer: ${e.message}")
        }
    }

    fun applyPreset(presetName: String) {
        _selectedEqPreset.value = presetName
        val targetGains = when (presetName) {
            "Rock" -> listOf(300, 200, -100, 200, 400)
            "Pop" -> listOf(-100, 100, 300, 200, -100)
            "Jazz" -> listOf(300, 100, -200, 100, 300)
            "Classical" -> listOf(200, 100, 0, 100, 200)
            "Heavy Metal" -> listOf(400, 100, -200, 300, 100)
            "Normal" -> listOf(0, 0, 0, 0, 0)
            else -> return
        }

        val updatedBands = _eqBands.value.mapIndexed { index, band ->
            val gain = targetGains.getOrElse(index) { 0 }
            // Scale gain inside limits
            val finalGain = gain.coerceIn(band.minLevel, band.maxLevel)
            try {
                hardwareEqualizer?.setBandLevel(band.id.toShort(), finalGain.toShort())
            } catch (e: Exception) {
                Log.e("MediaViewModel", "Error setting preset levels: ${e.message}")
            }
            band.copy(level = finalGain)
        }
        _eqBands.value = updatedBands
    }

    // Playlist Database Operations
    fun toggleFavorite(songId: String, isFav: Boolean) {
        viewModelScope.launch {
            repository.setSongFavorite(songId, isFav)
        }
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            repository.insertPlaylist(name)
        }
    }

    fun deletePlaylist(id: Long) {
        viewModelScope.launch {
            repository.deletePlaylist(id)
        }
    }

    fun addSongToPlaylist(playlistId: Long, songId: String) {
        viewModelScope.launch {
            repository.addSongToPlaylist(playlistId, songId)
        }
    }

    fun removeSongFromPlaylist(playlistId: Long, songId: String) {
        viewModelScope.launch {
            repository.removeSongFromPlaylist(playlistId, songId)
        }
    }

    fun selectPlaylist(id: Long) {
        viewModelScope.launch {
            repository.getSongsInPlaylist(id).collect {
                _songsInPlaylist.value = it
            }
        }
    }

    fun deleteHistoryItem(songId: String) {
        viewModelScope.launch {
            repository.deleteHistoryItem(songId)
        }
    }

    fun clearPlaybackHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    // Background progress update loop
    private fun startProgressTicker() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (true) {
                exoPlayer?.let { player ->
                    _playbackProgress.value = player.currentPosition
                }
                delay(250)
            }
        }
    }

    private fun stopProgressTicker() {
        progressJob?.cancel()
        progressJob = null
    }

    override fun onCleared() {
        stopProgressTicker()
        exoPlayer?.release()
        hardwareEqualizer?.release()
        hardwareBassBoost?.release()
        hardwareVirtualizer?.release()
        super.onCleared()
    }
}

data class EqBand(
    val id: Int,
    val label: String,
    val minLevel: Int,
    val maxLevel: Int,
    val level: Int
)
