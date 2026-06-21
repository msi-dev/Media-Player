package com.msi.scanner

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.msi.MediaPlayerApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Background Service that uses Kotlin Coroutines to index audio and video files asynchronously.
 * Saves results directly into Room database for persistence, ensuring the UI remains ultra responsive.
 */
class MediaScannerService : Service() {
    private val TAG = "MediaScannerService"
    
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val forceScan = intent?.getBooleanExtra(EXTRA_FORCE_SCAN, false) ?: false
        
        Log.i(TAG, "Service onStartCommand with Action: $action, forceScan: $forceScan")
        
        if (action == ACTION_START_SCAN) {
            startMediaScan(forceScan)
        }
        
        return START_NOT_STICKY
    }

    private fun startMediaScan(forceScan: Boolean) {
        // Mark as scanning globally
        MediaScannerController.setScanning(true)
        
        serviceScope.launch {
            try {
                Log.i(TAG, "Executing background coroutine indexing...")
                val app = application as MediaPlayerApp
                app.mediaRepository.scanLocalMedia(forceScan = forceScan)
                Log.i(TAG, "Background indexing completed successfully.")
            } catch (e: Exception) {
                Log.e(TAG, "Error during async media scanning: ${e.message}", e)
            } finally {
                MediaScannerController.setScanning(false)
                // Stop the service once scanning task is finished
                stopSelf()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "Destroying MediaScannerService and cancelling scopes.")
        serviceJob.cancel()
    }

    companion object {
        const val ACTION_START_SCAN = "com.msi.scanner.ACTION_START_SCAN"
        const val EXTRA_FORCE_SCAN = "com.msi.scanner.EXTRA_FORCE_SCAN"

        /**
         * Utility to start the scanner service from any context cleanly.
         */
        fun startScan(context: Context, forceScan: Boolean = false) {
            val intent = Intent(context, MediaScannerService::class.java).apply {
                action = ACTION_START_SCAN
                putExtra(EXTRA_FORCE_SCAN, forceScan)
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                Log.e("MediaScannerService", "Failed to start MediaScannerService: ${e.message}")
            }
        }
    }
}

/**
 * Globally accessible controller holding the scan state, so all viewmodels/screens can
 * reactively observe the indexing status in real time.
 */
object MediaScannerController {
    private val _isScanning = MutableStateFlow(false)
    val isScanning = _isScanning.asStateFlow()

    fun setScanning(scanning: Boolean) {
        _isScanning.value = scanning
    }
}
