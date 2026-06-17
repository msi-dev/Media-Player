package com.example.playback

import android.content.Context
import android.content.Intent
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.example.MediaPlayerApp
import com.example.data.db.MediaEntity
import com.example.data.repository.MediaRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Collections
import java.util.Timer
import java.util.TimerTask

@OptIn(UnstableApi::class)
class PlaybackManager(private val context: Context) {
    private val TAG = "PlaybackManager"

    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private val repository: MediaRepository by lazy {
        (context.applicationContext as MediaPlayerApp).mediaRepository
    }

    val visualizerManager = AudioVisualizerManager()

    private val statePrefs by lazy {
        context.getSharedPreferences("playback_state_persistence", Context.MODE_PRIVATE)
    }

    fun saveState() {
        val currentSongPath = _currentSong.value?.path ?: ""
        val pos = if (Looper.myLooper() == Looper.getMainLooper()) player.currentPosition else 0L
        val queuePaths = _playbackQueue.value.map { it.path }.joinToString(",")
        
        statePrefs.edit()
            .putString("persisted_current_song_path", currentSongPath)
            .putLong("persisted_current_position", pos)
            .putString("persisted_playback_queue", queuePaths)
            .putBoolean("persisted_shuffle_enabled", _isShuffleEnabled.value)
            .putInt("persisted_repeat_mode", _repeatMode.value)
            .apply()
    }

    // Base Media3 ExoPlayer
    val player: ExoPlayer = run {
        val audioAttributes = androidx.media3.common.AudioAttributes.Builder()
            .setUsage(androidx.media3.common.C.USAGE_MEDIA)
            .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        ExoPlayer.Builder(context)
            .setAudioAttributes(audioAttributes, true) // Automatically handles audio focus (ducking, pause, resume)
            .build()
    }

    // Observables (StateFlows) conforming to MVVM guidelines
    private val _currentSong = MutableStateFlow<MediaEntity?>(null)
    val currentSong: StateFlow<MediaEntity?> = _currentSong.asStateFlow()

    private val _currentlyPlayingVideo = MutableStateFlow<MediaEntity?>(null)
    val currentlyPlayingVideo: StateFlow<MediaEntity?> = _currentlyPlayingVideo.asStateFlow()

    private val _isVideoBackgroundPlayEnabled = MutableStateFlow(false)
    val isVideoBackgroundPlayEnabled: StateFlow<Boolean> = _isVideoBackgroundPlayEnabled.asStateFlow()

    fun setCurrentlyPlayingVideo(video: MediaEntity?) {
        _currentlyPlayingVideo.value = video
    }

    fun setVideoBackgroundPlayEnabled(enabled: Boolean) {
        _isVideoBackgroundPlayEnabled.value = enabled
    }

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _playbackQueue = MutableStateFlow<List<MediaEntity>>(emptyList())
    val playbackQueue: StateFlow<List<MediaEntity>> = _playbackQueue.asStateFlow()

    private val _isShuffleEnabled = MutableStateFlow(false)
    val isShuffleEnabled: StateFlow<Boolean> = _isShuffleEnabled.asStateFlow()

    // 0: Repeat Off, 1: Repeat One, 2: Repeat All
    private val _repeatMode = MutableStateFlow(0)
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _pitch = MutableStateFlow(1.0f)
    val pitch: StateFlow<Float> = _pitch.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _sleepTimerRemaining = MutableStateFlow(0L) // Remaining time in Milliseconds
    val sleepTimerRemaining: StateFlow<Long> = _sleepTimerRemaining.asStateFlow()

    // Equalizer & Audio Enhancements State
    private val _eqEnabled = MutableStateFlow(false)
    val eqEnabled = _eqEnabled.asStateFlow()

    // Equalizer 5 band levels (-15dB to +15dB, stored as Int -15 to +15)
    private val _eqBands = MutableStateFlow(listOf(0, 0, 0, 0, 0))
    val eqBands = _eqBands.asStateFlow()

    private val _bassBoostLevel = MutableStateFlow(0) // 0 to 1000
    val bassBoostLevel = _bassBoostLevel.asStateFlow()

    private val _virtualizerLevel = MutableStateFlow(0) // 0 to 1000
    val virtualizerLevel = _virtualizerLevel.asStateFlow()

    private val _loudnessEnhancerGain = MutableStateFlow(0) // 0 to 1000
    val loudnessEnhancerGain = _loudnessEnhancerGain.asStateFlow()

    private val _balance = MutableStateFlow(0.0f) // -1f L to +1f R
    val balance = _balance.asStateFlow()

    // Native AudioFx engines mapped to ExoPlayer sessionId
    private var nativeEqualizer: Equalizer? = null
    private var nativeBassBoost: BassBoost? = null
    private var nativeVirtualizer: Virtualizer? = null
    private var nativeLoudness: LoudnessEnhancer? = null

    // Handler to poll seekbar / timelines
    private val updateHandler = Handler(Looper.getMainLooper())
    private var lastDbWriteTime = 0L
    private val updateRunnable = object : Runnable {
        override fun run() {
            if (player.isPlaying) {
                val pos = player.currentPosition
                _currentPosition.value = pos
                _duration.value = player.duration.coerceAtLeast(0L)
                val now = System.currentTimeMillis()
                if (now - lastDbWriteTime > 3000L) {
                    lastDbWriteTime = now
                    _currentSong.value?.let { song ->
                        scope.launch(Dispatchers.IO) {
                            repository.updateLastPlayedPosition(song.path, pos)
                        }
                    }
                    saveState()
                }
            }
            updateHandler.postDelayed(this, 250)
        }
    }

    // Sleep Timer task
    private var sleepTimer: Timer? = null

    init {
        setupPlayerListener()
        updateHandler.post(updateRunnable)
        initializeAudioEffects()
    }

    private fun setupPlayerListener() {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
                visualizerManager.setPlaying(isPlaying)
                if (isPlaying) {
                     _duration.value = player.duration.coerceAtLeast(0L)
                     visualizerManager.startVisualizer(player.audioSessionId)
                } else {
                     _currentSong.value?.let { song ->
                         scope.launch(Dispatchers.IO) {
                             val currentPos = if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) player.currentPosition else 0L
                             repository.recordPlayback(song.path, currentPos)
                         }
                     }
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_READY -> {
                        _duration.value = player.duration.coerceAtLeast(0L)
                        applyAudioProfile()
                        if (player.isPlaying) {
                            visualizerManager.startVisualizer(player.audioSessionId)
                            visualizerManager.setPlaying(true)
                        }
                    }
                    Player.STATE_ENDED -> {
                        playNext()
                    }
                    else -> {}
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val path = mediaItem?.mediaId
                if (path != null) {
                    val song = _playbackQueue.value.find { it.path == path }
                    _currentSong.value = song
                    _currentPosition.value = 0L
                    if (song != null) {
                        Log.i(TAG, "ExoPlayer Transition: Active Song -> ${song.title}")
                        scope.launch(Dispatchers.IO) {
                            repository.recordPlayback(song.path, 0L)
                        }
                        if (player.isPlaying) {
                            visualizerManager.startVisualizer(player.audioSessionId)
                            visualizerManager.setPlaying(true)
                        }
                    }
                    saveState()
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                Log.e(TAG, "ExoPlayer Error Listener event detected: ${error.message} (code: ${error.errorCode})", error)
                attemptRecovery(error)
            }
        })
    }

    private fun attemptRecovery(error: androidx.media3.common.PlaybackException) {
        Log.w(TAG, "Attempting player error state recovery...")
        try {
            player.prepare()
            player.play()
        } catch (e: Exception) {
            Log.e(TAG, "Critical: Error recovery plan aborted. Skipping track to avoid hard loop.", e)
            playNext()
        }
    }

    private fun initializeAudioEffects() {
        try {
            val sessionId = player.audioSessionId
            if (sessionId != 0) {
                nativeEqualizer = Equalizer(0, sessionId).apply { enabled = _eqEnabled.value }
                nativeBassBoost = BassBoost(0, sessionId).apply { enabled = _bassBoostLevel.value > 0 }
                nativeVirtualizer = Virtualizer(0, sessionId).apply { enabled = _virtualizerLevel.value > 0 }
                nativeLoudness = LoudnessEnhancer(sessionId).apply { enabled = _loudnessEnhancerGain.value > 0 }
                Log.i(TAG, "Audio Enhancement Hardware Effects initialized on Session: $sessionId")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing native AudioFx hardware elements: ${e.message}")
        }
    }

    private fun applyAudioProfile() {
        try {
            // Apply Speed & Pitch parameters to Media3 ExoPlayer Engine
            player.playbackParameters = PlaybackParameters(_playbackSpeed.value, _pitch.value)

            // Balance Adjustment via ExoPlayer volumes
            val bal = _balance.value
            val leftVol = if (bal <= 0) 1.0f else 1.0f - bal
            val rightVol = if (bal >= 0) 1.0f else 1.0f + bal
            player.volume = (leftVol + rightVol) / 2.0f // Scale volume comfortably
        } catch (e: Exception) {
            Log.e(TAG, "Error seeking parameters adjustment: ${e.message}")
        }
    }

    // Custom Queue Operations
    fun setQueue(songs: List<MediaEntity>, startPosition: Int = 0, playWhenReady: Boolean = true) {
        if (songs.isEmpty()) return
        
        _playbackQueue.value = songs
        player.clearMediaItems()

        val mediaItems = songs.map { song ->
            val builder = MediaItem.Builder()
                .setMediaId(song.path)
            
            if (song.path.startsWith("asset:///")) {
                // Asset parsing
                val assetFilename = song.path.substring("asset:///".length)
                builder.setUri(Uri.parse("file:///android_asset/$assetFilename"))
            } else {
                builder.setUri(Uri.parse(song.path))
            }
            
            builder.build()
        }

        val targetSong = songs[startPosition]
        val savedPosition = targetSong.lastPlayedPosition
        player.addMediaItems(mediaItems)
        player.seekTo(startPosition, savedPosition)
        
        if (playWhenReady) {
            player.prepare()
            play()
        } else {
            player.prepare()
            player.playWhenReady = false
        }
        
        _currentSong.value = songs[startPosition]
        _duration.value = player.duration.coerceAtLeast(0L)
        saveState()
    }

    fun play() {
        try {
            val serviceIntent = Intent(context, MediaPlaybackService::class.java)
            androidx.core.content.ContextCompat.startForegroundService(context, serviceIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed startService in PlaybackManager.play: ${e.message}")
        }
        player.play()
    }

    fun pause() {
        player.pause()
    }

    fun stop() {
        player.stop()
        _isPlaying.value = false
        _currentPosition.value = 0L
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
        _currentPosition.value = positionMs
        saveState()
    }

    fun playNext() {
        if (player.hasNextMediaItem()) {
            player.seekToNextMediaItem()
            player.play()
        } else if (_repeatMode.value == 2) { // Repeat All
            player.seekTo(0, 0L)
            player.play()
        }
    }

    fun playPrevious() {
        if (player.hasPreviousMediaItem()) {
            player.seekToPreviousMediaItem()
            player.play()
        } else {
            player.seekTo(0L)
        }
    }

    fun toggleShuffle() {
        val nextShuffle = !_isShuffleEnabled.value
        _isShuffleEnabled.value = nextShuffle
        player.shuffleModeEnabled = nextShuffle
        Log.i(TAG, "Shuffle Toggled to: $nextShuffle")
        saveState()
    }

    fun toggleRepeatMode() {
        // 0: Off -> 1: One -> 2: All -> 0
        val nextMode = (_repeatMode.value + 1) % 3
        _repeatMode.value = nextMode
        player.repeatMode = when (nextMode) {
            1 -> Player.REPEAT_MODE_ONE
            2 -> Player.REPEAT_MODE_ALL
            else -> Player.REPEAT_MODE_OFF
        }
        Log.i(TAG, "Repeat Mode updated: $nextMode")
        saveState()
    }

    fun setPlaybackSpeed(speed: Float) {
        _playbackSpeed.value = speed
        applyAudioProfile()
    }

    fun setPitch(pitch: Float) {
        _pitch.value = pitch
        applyAudioProfile()
    }

    fun setBalance(bal: Float) {
        _balance.value = bal
        applyAudioProfile()
    }

    // Audio Enhancements Logic
    fun toggleEqualizer(enabled: Boolean) {
        _eqEnabled.value = enabled
        try {
            nativeEqualizer?.enabled = enabled
        } catch (e: Exception) {
            Log.e(TAG, "Equalizer toggle fail: ${e.message}")
        }
    }

    fun setEqualizerBand(band: Int, value: Int) {
        if (band < 0 || band >= 5) return
        val current = _eqBands.value.toMutableList()
        current[band] = value
        _eqBands.value = current

        try {
            nativeEqualizer?.let { eq ->
                if (eq.enabled) {
                    val nativeBand = band.toShort()
                    // Convert -15..+15 slider range to millibels (e.g. -1500mB to +1500mB)
                    val valueMillibels = (value * 100).toShort()
                    eq.setBandLevel(nativeBand, valueMillibels)
                    Log.i(TAG, "Set Equalizer Band $band to $valueMillibels mB")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error adjusting Equalizer Hardware Band $band: ${e.message}")
        }
    }

    fun setEqualizerPreset(levels: List<Int>) {
        if (levels.size != 5) return
        _eqBands.value = levels
        try {
            nativeEqualizer?.let { eq ->
                if (eq.enabled) {
                    for (i in 0 until 5) {
                        val nativeBand = i.toShort()
                        val valueMillibels = (levels[i] * 100).toShort()
                        eq.setBandLevel(nativeBand, valueMillibels)
                    }
                    Log.i(TAG, "Equalizer preset applied: $levels")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting equalizer preset: ${e.message}")
        }
    }

    fun setBassBoost(level: Int) {
        _bassBoostLevel.value = level
        try {
            nativeBassBoost?.apply {
                enabled = level > 0
                if (level > 0) {
                    setStrength(level.toShort())
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Bass Boost Hardware update error: ${e.message}")
        }
    }

    fun setVirtualizer(level: Int) {
        _virtualizerLevel.value = level
        try {
            nativeVirtualizer?.apply {
                enabled = level > 0
                if (level > 0) {
                    setStrength(level.toShort())
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Virtualizer Hardware update error: ${e.message}")
        }
    }

    fun setLoudnessEnhancer(gain: Int) {
        _loudnessEnhancerGain.value = gain
        try {
            nativeLoudness?.apply {
                enabled = gain > 0
                if (gain > 0) {
                    setTargetGain(gain)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Loudness Enhancer Hardware update error: ${e.message}")
        }
    }

    // Sleep Timer implementation
    fun setSleepTimer(minutes: Int) {
        sleepTimer?.cancel()
        if (minutes <= 0) {
            _sleepTimerRemaining.value = 0L
            return
        }

        var remainingSeconds = minutes * 60
        _sleepTimerRemaining.value = remainingSeconds * 1000L

        sleepTimer = Timer()
        sleepTimer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                remainingSeconds--
                _sleepTimerRemaining.value = remainingSeconds * 1000L

                if (remainingSeconds <= 0) {
                    // Trigger sleep trigger on UI thread
                    updateHandler.post {
                        pause()
                        _sleepTimerRemaining.value = 0L
                        Log.i(TAG, "Sleep Timer triggered: Playback Suspended.")
                    }
                    cancel()
                }
            }
        }, 1000L, 1000L)
    }

    // Queue modification utilities
    fun playNext(song: MediaEntity) {
        val currentQueue = _playbackQueue.value.toMutableList()
        var currentIndex = player.currentMediaItemIndex
        if (currentIndex < 0) currentIndex = 0
        
        val insertIndex = (currentIndex + 1).coerceAtMost(currentQueue.size)
        currentQueue.add(insertIndex, song)
        _playbackQueue.value = currentQueue

        val mediaItem = MediaItem.Builder()
            .setMediaId(song.path)
            .setUri(if (song.path.startsWith("asset:///")) Uri.parse("file:///android_asset/${song.path.substring("asset:///".length)}") else Uri.parse(song.path))
            .build()
        player.addMediaItem(insertIndex, mediaItem)
    }

    fun addToQueue(song: MediaEntity) {
        val currentQueue = _playbackQueue.value.toMutableList()
        currentQueue.add(song)
        _playbackQueue.value = currentQueue

        val mediaItem = MediaItem.Builder()
            .setMediaId(song.path)
            .setUri(if (song.path.startsWith("asset:///")) Uri.parse("file:///android_asset/${song.path.substring(9)}") else Uri.parse(song.path))
            .build()
        player.addMediaItem(mediaItem)
    }

    fun removeFromQueue(songPath: String) {
        val queue = _playbackQueue.value
        val index = queue.indexOfFirst { it.path == songPath }
        if (index != -1) {
            val currentQueue = queue.toMutableList()
            currentQueue.removeAt(index)
            _playbackQueue.value = currentQueue
            player.removeMediaItem(index)
        }
    }

    fun reorderQueue(fromIndex: Int, toIndex: Int) {
        if (fromIndex < 0 || fromIndex >= _playbackQueue.value.size || toIndex < 0 || toIndex >= _playbackQueue.value.size) return
        val currentQueue = _playbackQueue.value.toMutableList()
        Collections.swap(currentQueue, fromIndex, toIndex)
        _playbackQueue.value = currentQueue
        player.moveMediaItem(fromIndex, toIndex)
    }

    fun clearQueue() {
        _playbackQueue.value = emptyList()
        player.clearMediaItems()
        _currentSong.value = null
    }

    fun release() {
        updateHandler.removeCallbacks(updateRunnable)
        visualizerManager.stop()
        player.release()
        sleepTimer?.cancel()
        nativeEqualizer?.release()
        nativeBassBoost?.release()
        nativeVirtualizer?.release()
        nativeLoudness?.release()
    }
}
