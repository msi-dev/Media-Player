package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ndk.NativeMediaBridge
import com.example.ui.theme.DarkPrimary
import com.example.ui.theme.DarkSecondary
import com.example.ui.theme.DarkTertiary
import com.example.ui.viewmodel.MediaViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsTab(
    viewModel: MediaViewModel,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    // Grab states from VM
    val isDarkPref by viewModel.isDarkTheme.collectAsState()
    val eqActive by viewModel.eqEnabled.collectAsState()
    val bands by viewModel.eqBands.collectAsState()
    val bassBoostVal by viewModel.bassBoost.collectAsState()
    val virtualizerVal by viewModel.virtualizer.collectAsState()
    val loudnessVal by viewModel.loudnessEnhancer.collectAsState()
    val balanceVal by viewModel.balance.collectAsState()
    val sleepTimerVal by viewModel.sleepTimerRemaining.collectAsState()
    val gestureSensitivity by viewModel.gestureSensitivity.collectAsState()
    val smartResumeEnabled by viewModel.smartResumeEnabled.collectAsState()

    // Dynamic performance hardware profiling info from native JNI C++
    val nativePerformance = remember { NativeMediaBridge.getNativeOptimizationProfile() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Aesthetic Top Header Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.horizontalGradient(listOf(DarkSecondary, DarkPrimary)))
                    .padding(18.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Sound Master Deck",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Manage 5-band active hardware Equalizer, sleep engines, and explore native optimization vectors.",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 12.sp
                    )
                }
            }
        }

        // Section: Active Hardware Equalizer
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Equalizer, contentDescription = null, tint = DarkPrimary)
                        Text("Active hardware Equalizer", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                    Switch(
                        checked = eqActive,
                        onCheckedChange = { viewModel.toggleEqualizer(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = DarkPrimary
                        )
                    )
                }

                if (eqActive) {
                    val frequencies = listOf("60Hz", "230Hz", "910Hz", "4kHz", "14Hz")
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        bands.forEachIndexed { idx, sliderVal ->
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(frequencies[idx], color = Color.LightGray, fontSize = 11.sp)
                                    Text("${if (sliderVal >= 0) "+" else ""}${sliderVal} dB", color = DarkPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                Slider(
                                    value = sliderVal.toFloat(),
                                    onValueChange = { newVal ->
                                        viewModel.setEqualizerBand(idx, newVal.roundToInt())
                                    },
                                    valueRange = -15f..15f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = DarkPrimary,
                                        activeTrackColor = DarkPrimary,
                                        inactiveTrackColor = Color.DarkGray
                                    )
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        "Turn on equalizer switch above to configure band sliders dynamically.",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }

        // Section: Enhancements & Balance Control
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Tune, contentDescription = null, tint = DarkPrimary)
                    Text("Sound Enhancements", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }

                // 1. Bass Boost Slider
                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Bass Booster", color = Color.LightGray, fontSize = 12.sp)
                        Text("$bassBoostVal / 1000", color = DarkPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = bassBoostVal.toFloat(),
                        onValueChange = { viewModel.setBassBoost(it.roundToInt()) },
                        valueRange = 0f..1000f,
                        colors = SliderDefaults.colors(thumbColor = DarkPrimary, activeTrackColor = DarkPrimary)
                    )
                }

                // 2. Virtualizer Sound Slider
                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Surround Virtualizer", color = Color.LightGray, fontSize = 12.sp)
                        Text("$virtualizerVal / 1000", color = DarkPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = virtualizerVal.toFloat(),
                        onValueChange = { viewModel.setVirtualizer(it.roundToInt()) },
                        valueRange = 0f..1000f,
                        colors = SliderDefaults.colors(thumbColor = DarkPrimary, activeTrackColor = DarkPrimary)
                    )
                }

                // 3. Loudness booster
                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Loudness Enhancer Gain", color = Color.LightGray, fontSize = 12.sp)
                        Text("$loudnessVal mB", color = DarkPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = loudnessVal.toFloat(),
                        onValueChange = { viewModel.setLoudnessEnhancer(it.roundToInt()) },
                        valueRange = 0f..1000f,
                        colors = SliderDefaults.colors(thumbColor = DarkPrimary, activeTrackColor = DarkPrimary)
                    )
                }

                // 4. Stereo balance control
                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Left-Right Speaker Balance", color = Color.LightGray, fontSize = 12.sp)
                        Text(
                            text = when {
                                balanceVal < -0.05f -> "L -${(balanceVal * -100).roundToInt()}%"
                                balanceVal > 0.05f -> "R +${(balanceVal * 100).roundToInt()}%"
                                else -> "Balanced Center"
                            },
                            color = DarkPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Slider(
                        value = balanceVal,
                        onValueChange = { viewModel.setBalance(it) },
                        valueRange = -1.0f..1.0f,
                        colors = SliderDefaults.colors(thumbColor = DarkPrimary, activeTrackColor = DarkPrimary)
                    )
                }
            }
        }

        // Section: Sleep timer and theme styling
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Bedtime, contentDescription = null, tint = DarkPrimary)
                    Text("Sleep Timer & Themes", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }

                // Sleep trigger dropdown simulation buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Suspension Sleep Timer", color = Color.LightGray, fontSize = 13.sp)
                        Text(
                            text = if (sleepTimerVal > 0) "Active: ${formatDuration(sleepTimerVal)}" else "Sleep clock stopped",
                            color = if (sleepTimerVal > 0) DarkTertiary else Color.Gray,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(0, 15, 30, 60).forEach { mins ->
                            Button(
                                onClick = { viewModel.setSleepTimer(mins) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (mins == 0 && sleepTimerVal == 0L) DarkPrimary
                                    else if (mins > 0 && sleepTimerVal > 0L) DarkSecondary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Text(
                                    text = if (mins == 0) "Off" else "${mins}m",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                // Light / Dark Theme selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Application Visage Mod", color = Color.LightGray, fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { viewModel.setThemePref(false) }
                        ) {
                            RadioButton(selected = isDarkPref == false, onClick = { viewModel.setThemePref(false) })
                            Text("Light", color = Color.White, fontSize = 12.sp)
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { viewModel.setThemePref(true) }
                        ) {
                            RadioButton(selected = isDarkPref == true, onClick = { viewModel.setThemePref(true) })
                            Text("Dark", color = Color.White, fontSize = 12.sp)
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { viewModel.setThemePref(null) }
                        ) {
                            RadioButton(selected = isDarkPref == null, onClick = { viewModel.setThemePref(null) })
                            Text("System", color = Color.White, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Section: NATIVE Layer profiling (NDK JNI values query)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.SettingsInputHdmi, contentDescription = null, tint = DarkPrimary)
                    Text("C++ Native Engine Profiler", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
                Text(
                    text = nativePerformance,
                    color = DarkTertiary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 16.sp
                )
                Text(
                    text = "Formats verified at native JNI layers: AVI, MIDI, DSD, FLAC, AMR, APE, MOV, WEBM. Background media caching scanner operational.",
                    color = Color.LightGray,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }
        }

        // Section: About Developer Screen Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("About Media Player", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("Version 1.0.0 (Enterprise Premium)", color = DarkPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("Local Cinema Decoders are optimized under Media3 ExoPlayer bindings for fast, low-battery local codecs.", color = Color.Gray, fontSize = 11.sp, lineHeight = 16.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Support Codecs", color = Color.Gray, fontSize = 11.sp)
                    Text("Avi, MP3, FLAC, MKV, WAV", color = Color.LightGray, fontSize = 11.sp)
                }
            }
        }

        // Section: Custom Gesture & Smart Playback Settings Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.SettingsSuggest, contentDescription = null, tint = DarkPrimary)
                    Text("Gesture & Playback Settings", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }

                // 1. Gesture Sensitivity Slider
                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Gesture Drag Sensitivity", color = Color.LightGray, fontSize = 13.sp)
                        Text("${(gestureSensitivity * 100).roundToInt()}%", color = DarkPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = gestureSensitivity,
                        onValueChange = { viewModel.setGestureSensitivity(it) },
                        valueRange = 0.2f..2.0f,
                        colors = SliderDefaults.colors(
                            thumbColor = DarkPrimary,
                            activeTrackColor = DarkPrimary,
                            inactiveTrackColor = Color.DarkGray
                        )
                    )
                    Text(
                        "Alters scaling metrics of Brightness (left half screen drag) and Volume (right half screen drag) in full-screen Video Player.",
                        color = Color.Gray,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }

                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                // 2. Smart Resume Switch Option
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text("Smart Playback Resume", color = Color.LightGray, fontSize = 13.sp)
                        Text(
                            "Automatically resumes media from the exact timestamp last paused upon application startup.",
                            color = Color.Gray,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                    Switch(
                        checked = smartResumeEnabled,
                        onCheckedChange = { viewModel.setSmartResumeEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = DarkPrimary
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(100.dp))
    }
}
