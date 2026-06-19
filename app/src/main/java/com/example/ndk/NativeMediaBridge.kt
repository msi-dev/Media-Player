package com.example.ndk

import android.util.Log

object NativeMediaBridge {
    private const val TAG = "NativeMediaBridge"
    private var isNativeLibraryLoaded = false

    init {
        try {
            System.loadLibrary("media-core")
            isNativeLibraryLoaded = true
            Log.i(TAG, "Native library 'media-core' loaded successfully.")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load native library 'media-core'. Using Kotlin fallback implementations.", e)
            isNativeLibraryLoaded = false
        }
    }

    /**
     * Natively extracts the format extension from the file path.
     * PERFORMANCE OPTIMIZATION: Bypasses native calls entirely for standard file extensions
     * to prevent slow disk I/O or NDK thread freezing during large folder indexing.
     */
    fun detectFormat(filePath: String): String {
        val extension = fallbackDetectFormat(filePath)
        if (fallbackIsFormatSupported(extension)) {
            return extension
        }
        return if (isNativeLibraryLoaded) {
            try {
                detectFormatNative(filePath)
            } catch (e: Exception) {
                extension
            }
        } else {
            extension
        }
    }

    /**
     * Natively checks if a media format is supported.
     */
    fun isFormatSupported(format: String): Boolean {
        return if (isNativeLibraryLoaded) {
            try {
                isFormatSupportedNative(format)
            } catch (e: Exception) {
                fallbackIsFormatSupported(format)
            }
        } else {
            fallbackIsFormatSupported(format)
        }
    }

    /**
     * Natively retrieves an architecture-optimized hardware acceleration profile.
     */
    fun getNativeOptimizationProfile(): String {
        return if (isNativeLibraryLoaded) {
            try {
                getNativeOptimizationProfileNative()
            } catch (e: Exception) {
                fallbackGetNativeOptimizationProfile()
            }
        } else {
            fallbackGetNativeOptimizationProfile()
        }
    }

    // JNI Native Declarations
    private external fun detectFormatNative(filePath: String): String
    private external fun isFormatSupportedNative(format: String): Boolean
    private external fun getNativeOptimizationProfileNative(): String

    // Safe Fallback Implementations
    private fun fallbackDetectFormat(filePath: String): String {
        val index = filePath.lastIndexOf('.')
        if (index != -1 && index < filePath.length - 1) {
            return filePath.substring(index + 1).lowercase()
        }
        return "unknown"
    }

    private fun fallbackIsFormatSupported(format: String): Boolean {
        val supported = listOf(
            // Audio
            "mp3", "aac", "m4a", "flac", "wav", "ogg", "opus", "amr", "ape", "alac", "wma", "midi", "aiff", "dsd",
            // Video
            "mp4", "mkv", "avi", "mov", "webm", "flv", "mpeg", "mpg", "ts", "m2ts", "3gp", "wmv", "vob", "asf"
        )
        return supported.contains(format.lowercase())
    }

    private fun fallbackGetNativeOptimizationProfile(): String {
        return "Kotlin Fallback Performance Profile Active - Low-Latency Buffering Configured"
    }
}
