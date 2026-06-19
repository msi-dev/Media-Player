package com.example.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "media_player_settings")

class SettingsDataStore(private val context: Context) {

    companion object {
        val DYNAMIC_COLOR_ENABLED = booleanPreferencesKey("dynamic_color_enabled")
        val IS_DARK_THEME = stringPreferencesKey("is_dark_theme") // "LIGHT", "DARK", "SYSTEM"
        val WAVEFORM_STYLE = stringPreferencesKey("waveform_style") // "Wave", "Bars", "Spectrum", "Symmetrical"
        val WAVEFORM_COLOR_TYPE = stringPreferencesKey("waveform_color_type") // "Monochromatic", "Accent"
        val SMART_RESUME_ENABLED = booleanPreferencesKey("smart_resume_enabled")
        val AUDIO_FOCUS_ENABLED = booleanPreferencesKey("audio_focus_enabled")
        val DEFAULT_PLAYBACK_SPEED = floatPreferencesKey("default_playback_speed")
        val EQUALIZER_PRESET = stringPreferencesKey("equalizer_preset") // e.g. "Normal", "Pop", "Rock", "Jazz", "Custom"
        val SCAN_ANDROID_FOLDER = booleanPreferencesKey("scan_android_folder")
    }

    // Dynamic Color Theme
    val dynamicColorEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[DYNAMIC_COLOR_ENABLED] ?: false
        }

    suspend fun setDynamicColorEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[DYNAMIC_COLOR_ENABLED] = enabled
        }
    }

    // Is Dark Theme
    val isDarkTheme: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[IS_DARK_THEME] ?: "SYSTEM"
        }

    suspend fun setDarkTheme(theme: String) {
        context.dataStore.edit { preferences ->
            preferences[IS_DARK_THEME] = theme
        }
    }

    // Waveform Style
    val waveformStyle: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[WAVEFORM_STYLE] ?: "Wave"
        }

    suspend fun setWaveformStyle(style: String) {
        context.dataStore.edit { preferences ->
            preferences[WAVEFORM_STYLE] = style
        }
    }

    // Waveform Color Type
    val waveformColorType: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[WAVEFORM_COLOR_TYPE] ?: "Monochromatic"
        }

    suspend fun setWaveformColorType(colorType: String) {
        context.dataStore.edit { preferences ->
            preferences[WAVEFORM_COLOR_TYPE] = colorType
        }
    }

    // Smart Resume
    val smartResumeEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[SMART_RESUME_ENABLED] ?: false
        }

    suspend fun setSmartResumeEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SMART_RESUME_ENABLED] = enabled
        }
    }

    // Audio Focus
    val audioFocusEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[AUDIO_FOCUS_ENABLED] ?: true
        }

    suspend fun setAudioFocusEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[AUDIO_FOCUS_ENABLED] = enabled
        }
    }

    // Default Playback Speed
    val defaultPlaybackSpeed: Flow<Float> = context.dataStore.data
        .map { preferences ->
            preferences[DEFAULT_PLAYBACK_SPEED] ?: 1.0f
        }

    suspend fun setDefaultPlaybackSpeed(speed: Float) {
        context.dataStore.edit { preferences ->
            preferences[DEFAULT_PLAYBACK_SPEED] = speed
        }
    }

    // Equalizer Preset
    val equalizerPreset: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[EQUALIZER_PRESET] ?: "Normal"
        }

    suspend fun setEqualizerPreset(preset: String) {
        context.dataStore.edit { preferences ->
            preferences[EQUALIZER_PRESET] = preset
        }
    }

    // Scan Android Folder
    val scanAndroidFolder: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[SCAN_ANDROID_FOLDER] ?: false
        }

    suspend fun setScanAndroidFolder(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SCAN_ANDROID_FOLDER] = enabled
        }
    }
}
