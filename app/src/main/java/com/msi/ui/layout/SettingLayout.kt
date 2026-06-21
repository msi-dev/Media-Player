package com.msi.ui.layout

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.msi.ui.viewmodel.MediaViewModel

@Composable
fun SettingsLayout(
    viewModel: MediaViewModel,
    onBack: () -> Unit
) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    // Observe DataStore through VM (if defined, else default settings placeholders)
    val dynamicThemeEnabled by viewModel.dynamicColorEnabled.collectAsState()
    val isDarkByPref by viewModel.isDarkThemePrefSetting.collectAsState()
    val waveformStyle by viewModel.waveformStylePref.collectAsState()
    val waveformColorType by viewModel.waveformColorPref.collectAsState()
    val eqPreset by viewModel.equalizerPresetPref.collectAsState()

    // Other generic state variables from VM SharedPreferences/StateFlows
    val isDarkPref by viewModel.isDarkTheme.collectAsState()
    val sleepTimerVal by viewModel.sleepTimerRemaining.collectAsState()
    val hiddenFoldersRaw by viewModel.hiddenFolders.collectAsState()

    var showHideFolderDialog by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Top Toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            Text(
                text = "Settings",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 12.dp)
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // SECTION 1: THEMES AND DYNAMIC COLORS
            Text(
                text = "Visual & Colors",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp)
            )

            // Theme Preferences Card
            SettingRowCard(
                icon = Icons.Default.DarkMode,
                title = "Theme Mode",
                subtitle = "Choose between Light, Dark, or System mode configurations."
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("LIGHT", "DARK", "SYSTEM").forEach { themeMode ->
                        val isSelected = isDarkByPref == themeMode
                        OutlinedButton(
                            onClick = { viewModel.setIsDarkThemePrefSetting(themeMode) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent,
                                contentColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            ),
                            border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                        ) {
                            Text(themeMode, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Toggle Dynamic Color Theme Switch
            SettingRowCard(
                icon = Icons.Default.Palette,
                title = "Dynamic Colors",
                subtitle = "Toggle Material 3 dynamic color accents matching wallpaper hues (Android 12+)."
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (dynamicThemeEnabled) "Enabled" else "Disabled (Default Monochrome)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Switch(
                        checked = dynamicThemeEnabled,
                        onCheckedChange = { checked -> viewModel.setDynamicColorEnabled(checked) }
                    )
                }
            }

            // SECTION 2: WAVEFORM VISUALIZATION CONFIGURATION
            Text(
                text = "Waveform Visualizer",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp)
            )

            SettingRowCard(
                icon = Icons.Default.Waves,
                title = "Visualizer Settings",
                subtitle = "Configure animation style and colors for the mini/full audio visualizers."
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Live Preview box!
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        WaveformVisualizer(
                            isPlaying = true,
                            style = waveformStyle,
                            colorType = waveformColorType,
                            modifier = Modifier.fillMaxWidth().height(40.dp)
                        )
                    }

                     // Toggles for Animation Styles
                     Column {
                         Text("Animation Style", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                         Spacer(modifier = Modifier.height(4.dp))
                         Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                             Row(
                                 modifier = Modifier.fillMaxWidth(),
                                 horizontalArrangement = Arrangement.spacedBy(6.dp)
                             ) {
                                 listOf("None", "Wave", "Bars").forEach { itemStyle ->
                                     val selected = waveformStyle == itemStyle
                                     Box(
                                         modifier = Modifier
                                             .weight(1f)
                                             .background(
                                                 color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                                 shape = RoundedCornerShape(8.dp)
                                             )
                                             .clickable { viewModel.setWaveformStyle(itemStyle) }
                                             .padding(vertical = 10.dp),
                                         contentAlignment = Alignment.Center
                                     ) {
                                         Text(
                                             text = itemStyle,
                                             fontSize = 10.sp,
                                             fontWeight = FontWeight.Bold,
                                             color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                         )
                                     }
                                 }
                             }
                             Row(
                                 modifier = Modifier.fillMaxWidth(0.67f), // Keep same item width as the 3-item row
                                 horizontalArrangement = Arrangement.spacedBy(6.dp)
                             ) {
                                 listOf("Spectrum", "Symmetrical").forEach { itemStyle ->
                                     val selected = waveformStyle == itemStyle
                                     Box(
                                         modifier = Modifier
                                             .weight(1f)
                                             .background(
                                                 color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                                 shape = RoundedCornerShape(8.dp)
                                             )
                                             .clickable { viewModel.setWaveformStyle(itemStyle) }
                                             .padding(vertical = 10.dp),
                                         contentAlignment = Alignment.Center
                                     ) {
                                         Text(
                                             text = itemStyle,
                                             fontSize = 10.sp,
                                             fontWeight = FontWeight.Bold,
                                             color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                         )
                                     }
                                 }
                             }
                         }
                     }

                    // Choice between Monochromatic or Accent Colors
                    Column {
                        Text("Color Palette", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Monochromatic", "Accent").forEach { itemColor ->
                                val selected = waveformColorType == itemColor
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(
                                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable { viewModel.setWaveformColorType(itemColor) }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = itemColor,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // SECTION 3: EQUALIZER PRESETS
            Text(
                text = "Audio & Hardware",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp)
            )

            SettingRowCard(
                icon = Icons.Default.Equalizer,
                title = "Equalizer Presets",
                subtitle = "Manage active hardware presets directly matching your current acoustic preference."
            ) {
                val eqActive by viewModel.eqEnabled.collectAsState()
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (eqActive) "Equalizer Enabled" else "Equalizer Disabled",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (eqActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                        Switch(
                            checked = eqActive,
                            onCheckedChange = { checked -> viewModel.toggleEqualizer(checked) }
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(4.dp))

                    val presets = listOf("Flat", "Bass Boost", "Vocal", "Acoustic", "Classic")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        presets.take(3).forEach { presetName ->
                            val isSelected = eqPreset == presetName
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable(enabled = eqActive) { viewModel.setEqualizerPresetSetting(presetName) }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = presetName,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else if (eqActive) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        presets.drop(3).forEach { presetName ->
                            val isSelected = eqPreset == presetName
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable(enabled = eqActive) { viewModel.setEqualizerPresetSetting(presetName) }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = presetName,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else if (eqActive) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                )
                            }
                        }
                    }
                }
            }

            // Sleep Timer Clock Picker
            SettingRowCard(
                icon = Icons.Default.AccessTime,
                title = "Sleep Timer",
                subtitle = "Schedule a count-down using custom clock values to suspend ongoing music."
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (sleepTimerVal > 0) "Time left: ${formatDuration(sleepTimerVal)}" else "Timer inactive",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (sleepTimerVal > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (sleepTimerVal > 0) {
                            Button(
                                onClick = { viewModel.setSleepTimer(0) },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Stop Timer", fontSize = 11.sp)
                            }
                        }
                        Button(
                            onClick = { showSleepTimerDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Open Clock", fontSize = 11.sp)
                        }
                    }
                }
            }

            // Exclude Folders Checklist
            SettingRowCard(
                icon = Icons.Default.FolderOff,
                title = "Hide Folder",
                subtitle = "Exclude directories from media crawls (currently hidden: ${hiddenFoldersRaw.size})."
            ) {
                Button(
                    onClick = { showHideFolderDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(imageVector = Icons.Default.VisibilityOff, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Configure Hidden Folders Checklist", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Scan Android Folder Select Switch
            val scanAndroidFolderEnabled by viewModel.scanAndroidFolderPref.collectAsState()
            SettingRowCard(
                icon = Icons.Default.Folder,
                title = "Scan Android Folder",
                subtitle = "Toggle scanning of Android-specific system folders containing application assets and cache."
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (scanAndroidFolderEnabled) "Scan System Directories" else "Skip System Directories (Recommended)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (scanAndroidFolderEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    Switch(
                        checked = scanAndroidFolderEnabled,
                        onCheckedChange = { checked -> viewModel.setScanAndroidFolderSetting(checked) }
                    )
                }
            }

            // SECTION 4: PLAYBACK PREFERENCES
            Text(
                text = "Preferences",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp)
            )

            // Smart Resume Preference
            val smartResumeEnabled by viewModel.smartResumeEnabled.collectAsState()
            SettingRowCard(
                icon = Icons.Default.Headset,
                title = "Smart Headphone Resume",
                subtitle = "Resume playing audio instantly upon headset/earbud connection."
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (smartResumeEnabled) "Enabled" else "Disabled",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Switch(
                        checked = smartResumeEnabled,
                        onCheckedChange = { checked -> viewModel.setSmartResumeEnabled(checked) }
                    )
                }
            }

            // Audio Focus Preference
            val audioFocusEnabled by viewModel.audioFocusEnabled.collectAsState()
            SettingRowCard(
                icon = Icons.Default.VolumeUp,
                title = "Manage Audio Focus",
                subtitle = "Automatically pause or lower audio volume when other apps play sound/telephony calls."
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (audioFocusEnabled) "Automatic Pause" else "Ignore Interruptions",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Switch(
                        checked = audioFocusEnabled,
                        onCheckedChange = { checked -> viewModel.setAudioFocusEnabled(checked) }
                    )
                }
            }

            // Audio Tab Layout Manager
            SettingRowCard(
                icon = Icons.Default.List,
                title = "Audio Tab Manager",
                subtitle = "Rearrange and toggle sections shown under the audio library panels."
            ) {
                val defaultTabs = viewModel.defaultTabs
                val visibleTabs by viewModel.visibleTabs.collectAsState()

                var tabsState by remember(visibleTabs) {
                    val hidden = defaultTabs.filter { !visibleTabs.contains(it) }
                    mutableStateOf(visibleTabs + hidden)
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    tabsState.forEachIndexed { index, tabName ->
                        val isVisible = visibleTabs.contains(tabName)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.05f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val onToggle = {
                                val currentVisible = visibleTabs.toMutableList()
                                if (isVisible) {
                                    currentVisible.remove(tabName)
                                } else {
                                    currentVisible.add(tabName)
                                }
                                viewModel.saveVisibleTabs(currentVisible)
                            }

                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onToggle() },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isVisible,
                                    onCheckedChange = { onToggle() }
                                )
                                Text(
                                    text = tabName,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isVisible) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                IconButton(
                                    onClick = {
                                        if (index > 0) {
                                            val newList = tabsState.toMutableList()
                                            val temp = newList[index]
                                            newList[index] = newList[index - 1]
                                            newList[index - 1] = temp
                                            tabsState = newList
                                            viewModel.saveVisibleTabs(newList.filter { visibleTabs.contains(it) })
                                        }
                                    },
                                    enabled = index > 0,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowUpward,
                                        contentDescription = "Move Up",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        if (index < tabsState.size - 1) {
                                            val newList = tabsState.toMutableList()
                                            val temp = newList[index]
                                            newList[index] = newList[index + 1]
                                            newList[index + 1] = temp
                                            tabsState = newList
                                            viewModel.saveVisibleTabs(newList.filter { visibleTabs.contains(it) })
                                        }
                                    },
                                    enabled = index < tabsState.size - 1,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowDownward,
                                        contentDescription = "Move Down",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // About Application Card
            SettingRowCard(
                icon = Icons.Default.Info,
                title = "About",
                subtitle = "App version details, updates, licenses, and core attributions."
            ) {
                Button(
                    onClick = { showAboutDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Attribution & System Info", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Modal dialogs
    if (showHideFolderDialog) {
        val physicalFolders by viewModel.allPhysicalFolders.collectAsState()
        val foldersRaw by viewModel.hiddenFolders.collectAsState()
        val pathList = foldersRaw.map { it.folderPath }
        
        AlertDialog(
            onDismissRequest = { showHideFolderDialog = false },
            title = { Text("Hidden Scanner Directories", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (physicalFolders.isEmpty()) {
                        Text("No scanning paths identified.", fontSize = 13.sp)
                    } else {
                        physicalFolders.forEach { folderPath ->
                            val isHidden = pathList.contains(folderPath)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (isHidden) {
                                            viewModel.removeHiddenFolder(folderPath)
                                        } else {
                                            viewModel.addHiddenFolder(folderPath)
                                        }
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isHidden,
                                    onCheckedChange = { checked ->
                                        if (isHidden) {
                                            viewModel.removeHiddenFolder(folderPath)
                                        } else {
                                            viewModel.addHiddenFolder(folderPath)
                                        }
                                    }
                                )
                                Text(
                                    text = folderPath.substringAfterLast("/"),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showHideFolderDialog = false }) {
                    Text("OK")
                }
            }
        )
    }

    if (showSleepTimerDialog) {
        // Simple TimePicker placeholder calling viewmodel.setSleepTimer
        // For clean compatibility, we can trigger dialog
        SleepTimerDialogWrapper(
            onDismissRequest = { showSleepTimerDialog = false },
            onConfirm = { mins ->
                viewModel.setSleepTimer(mins)
                showSleepTimerDialog = false
            }
        )
    }

    if (showAboutDialog) {
        AboutDialogWrapper(
            onDismissRequest = { showAboutDialog = false }
        )
    }
}

@Composable
fun SettingRowCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = subtitle,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
            content()
        }
    }
}

// Dialog wrappers leveraging basic native Dialog constructors
@Composable
fun SleepTimerDialogWrapper(
    onDismissRequest: () -> Unit,
    onConfirm: (minutes: Int) -> Unit
) {
    var valInput by remember { mutableStateOf("15") }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Sleep Timer Clock", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Enter minutes after which ongoing song pauses automatically:", fontSize = 13.sp)
                OutlinedTextField(
                    value = valInput,
                    onValueChange = { valInput = it.filter { c -> c.isDigit() } },
                    label = { Text("Minutes") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(valInput.toIntOrNull() ?: 15) }) {
                Text("Start Timer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun AboutDialogWrapper(
    onDismissRequest: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Attribution & System Info", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Media Player Pro", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("Version: 6.5 (Enterprise Edition)", fontSize = 13.sp)
                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                Text("Developed with absolute craft centering high-performance ExoPlayer playback engines, full Material 3 dynamic layouts, unified service controls, and responsive visual waveform tracking graphs.", fontSize = 12.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Done")
            }
        }
    )
}

// Utility extension
private fun formatDuration(millis: Long): String {
    val sec = (millis / 1000L) % 60
    val min = (millis / 1000L / 60) % 60
    val hr = (millis / 1000L / 3600)
    return if (hr > 0) {
        String.format("%02d:%02d:%02d", hr, min, sec)
    } else {
        String.format("%02d:%02d", min, sec)
    }
}
