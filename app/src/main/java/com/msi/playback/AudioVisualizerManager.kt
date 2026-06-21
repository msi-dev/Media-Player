package com.msi.playback

import android.media.audiofx.Visualizer
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlin.random.Random

class AudioVisualizerManager {
    private val TAG = "AudioVisualizerManager"
    private var visualizer: Visualizer? = null

    // Normalized visualizer bytes (values from 0f to 1f representing heights of spectrum bars)
    private val _spectrumData = MutableStateFlow<List<Float>>(List(32) { 0f })
    val spectrumData = _spectrumData.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var animationJob: Job? = null
    private var isRealVisualizerActive = false

    fun startVisualizer(audioSessionId: Int) {
        stop()
        if (audioSessionId == 0) {
            Log.w(TAG, "Audio Session ID is zero, using procedural simulation.")
            startProceduralSimulation()
            return
        }

        try {
            val maxRate = Visualizer.getMaxCaptureRate()
            val captureRange = Visualizer.getCaptureSizeRange()
            val capSize = if (captureRange != null && captureRange.size >= 2) {
                // Safely clamp captures
                256.coerceIn(captureRange[0], captureRange[1])
            } else {
                128
            }

            visualizer = Visualizer(audioSessionId).apply {
                captureSize = capSize
                setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(vis: Visualizer?, waveform: ByteArray?, samplingRate: Int) {
                        if (waveform != null && !isRealVisualizerActive) {
                            isRealVisualizerActive = true
                            stopProceduralSimulation()
                        }
                        waveform?.let { processWaveform(it) }
                    }

                    override fun onFftDataCapture(vis: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                        if (fft != null && !isRealVisualizerActive) {
                            isRealVisualizerActive = true
                            stopProceduralSimulation()
                        }
                        fft?.let { processFft(it) }
                    }
                }, maxRate / 2, true, true)
                enabled = true
            }
            Log.i(TAG, "Android Hardware visualizer bound perfectly to Session ID: $audioSessionId code.")
        } catch (e: Exception) {
            Log.e(TAG, "Cannot construct visualizer on session $audioSessionId : ${e.message}. Launching custom procedural simulation.")
            isRealVisualizerActive = false
            startProceduralSimulation()
        }
    }

    private fun processWaveform(waveform: ByteArray) {
        val numBars = 32
        val step = (waveform.size / numBars).coerceAtLeast(1)
        val normalized = List(numBars) { i ->
            var sum = 0f
            val limit = (i * step + step).coerceAtMost(waveform.size)
            var count = 0
            for (j in (i * step) until limit) {
                val byteVal = waveform[j].toInt() and 0xFF
                // Waveform is signed or unsigned 8bit, average distance from center (128)
                val amp = kotlin.math.abs(byteVal - 128).toFloat() / 128f
                sum += amp
                count++
            }
            val valOut = if (count > 0) (sum / count) * 2.2f else 0f
            valOut.coerceIn(0f, 1f)
        }
        _spectrumData.value = normalized
    }

    private fun processFft(fft: ByteArray) {
        // FFT contains real and imaginary components [r[0], i[0], r[1], i[1] ...]
        val numBars = 32
        val halfSize = fft.size / 2
        val step = (halfSize / numBars).coerceAtLeast(1)

        val normalized = List(numBars) { i ->
            var maxMagnitude = 0f
            val startIdx = i * step
            val endIdx = (startIdx + step).coerceAtMost(halfSize)
            for (j in startIdx until endIdx) {
                val r = fft[2 * j].toFloat()
                val im = fft[2 * j + 1].toFloat()
                val mag = hypot(r, im)
                if (mag > maxMagnitude) {
                    maxMagnitude = mag
                }
            }
            // Normalize level comfortably (FFT range depends on system gain)
            val level = (maxMagnitude / 100f).coerceIn(0f, 1f)
            level
        }
        _spectrumData.value = normalized
    }

    fun setPlaying(playing: Boolean) {
        if (!playing) {
            if (isRealVisualizerActive) {
                // When paused, clear the heights
                _spectrumData.value = List(32) { 0f }
            } else {
                // Pause animation loop
                stopProceduralSimulation()
                _spectrumData.value = List(32) { 0.05f }
            }
        } else {
            if (!isRealVisualizerActive && animationJob == null) {
                startProceduralSimulation()
            }
        }
    }

    private fun startProceduralSimulation() {
        if (isRealVisualizerActive) return
        stopProceduralSimulation()
        animationJob = scope.launch {
            val random = Random(System.currentTimeMillis())
            val baseHeights = MutableList(32) { 0.1f }
            while (true) {
                // Procedural beautiful wavy equalizer simulation when device lacks hardware loopback
                for (i in 0 until 32) {
                    val target = 0.1f + (kotlin.math.sin(System.currentTimeMillis() * 0.005 + i * 0.3).toFloat() + 1f) * 0.4f * (0.5f + random.nextFloat() * 0.5f)
                    // Glide to target
                    baseHeights[i] = baseHeights[i] + (target - baseHeights[i]) * 0.35f
                }
                _spectrumData.value = baseHeights.toList()
                delay(40) // ~25fps responsive drawing
            }
        }
    }

    private fun stopProceduralSimulation() {
        animationJob?.cancel()
        animationJob = null
    }

    fun stop() {
        try {
            visualizer?.enabled = false
            visualizer?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping visualizer: ${e.message}")
        } finally {
            visualizer = null
            isRealVisualizerActive = false
            stopProceduralSimulation()
            _spectrumData.value = List(32) { 0f }
        }
    }
}
