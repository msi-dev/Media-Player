package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.ProvideResponsiveDimensions
import com.example.ui.viewmodel.MediaViewModel
import com.example.ui.viewmodel.MediaViewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val factory = MediaViewModelFactory(application)
        val viewModel = ViewModelProvider(this, factory)[MediaViewModel::class.java]

        setContent {
            val isDarkThemePref by viewModel.isDarkTheme.collectAsState()
            val forceDark = when (isDarkThemePref) {
                true -> true
                false -> false
                null -> isSystemInDarkTheme()
            }

            MyApplicationTheme(darkTheme = forceDark) {
                ProvideResponsiveDimensions {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        SettingsContent(
                            viewModel = viewModel,
                            onBack = { finish() }
                        )
                    }
                }
            }
        }
    }

    override fun getAttributionTag(): String? {
        return "default"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContent(
    viewModel: MediaViewModel,
    onBack: () -> Unit
) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Observe settings data
    val isDarkPref by viewModel.isDarkTheme.collectAsState()
    val sleepTimerVal by viewModel.sleepTimerRemaining.collectAsState()

    // Folder states
    val physicalFolders by viewModel.allPhysicalFolders.collectAsState()
    val hiddenFoldersRaw by viewModel.hiddenFolders.collectAsState()
    val hiddenFolderPaths = remember(hiddenFoldersRaw) { hiddenFoldersRaw.map { it.folderPath } }

    var showHideFolderDialog by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Redesigned modern top level Title bar Settings with navigation back action
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
            // 1. THEMES DROPDOWN ROW
            SettingRowCard(
                icon = Icons.Default.Palette,
                title = "Themes",
                subtitle = "Toggle standard light mode, dark mode, or follow system default."
            ) {
                ThemesDropdown(
                    currentTheme = isDarkPref,
                    onThemeSelect = { theme ->
                        viewModel.setThemePref(theme)
                    }
                )
            }

            // 2. SLEEP TIMER TIME PICKER ROW
            SettingRowCard(
                icon = Icons.Default.AccessTime,
                title = "Sleep Timer",
                subtitle = "Set a count-down using custom clock values to suspend ongoing music."
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (sleepTimerVal > 0) "Seconds left: ${formatDuration(sleepTimerVal)}" else "Timer inactive",
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

            // 3. HIDE FOLDERS CHECKLIST ROW
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

            // 4. AUDIO TAB LAYOUT MANAGER ROW
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
                                    if (!currentVisible.contains(tabName)) {
                                        currentVisible.add(tabName)
                                    }
                                }
                                viewModel.saveVisibleTabs(currentVisible)
                            }

                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onToggle() }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Checkbox(
                                    checked = isVisible,
                                    onCheckedChange = { onToggle() }
                                )
                                Text(
                                    text = tabName,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                IconButton(
                                    onClick = {
                                        if (index > 0) {
                                            val newList = tabsState.toMutableList()
                                            val temp = newList[index]
                                            newList[index] = newList[index - 1]
                                            newList[index - 1] = temp
                                            tabsState = newList

                                            val newVisible = newList.filter { visibleTabs.contains(it) }
                                            viewModel.saveVisibleTabs(newVisible)
                                        }
                                    },
                                    enabled = index > 0,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowUpward,
                                        contentDescription = "Up",
                                        tint = if (index > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
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

                                            val newVisible = newList.filter { visibleTabs.contains(it) }
                                            viewModel.saveVisibleTabs(newVisible)
                                        }
                                    },
                                    enabled = index < tabsState.size - 1,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowDownward,
                                        contentDescription = "Down",
                                        tint = if (index < tabsState.size - 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 5. VERSION INFO ROW
            SettingRowCard(
                icon = Icons.Default.Info,
                title = "Version Info",
                subtitle = "Query real-time package compilation information from local device storage."
            ) {
                val versionName = remember {
                    try {
                        val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                        pInfo.versionName ?: "1.0.0"
                    } catch (e: Exception) {
                        "1.0.0"
                    }
                }

                Text(
                    text = "Release Variant: $versionName",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }

            // 6. ABOUT MODAL WINDOW ROW
            SettingRowCard(
                icon = Icons.Default.QuestionMark,
                title = "About",
                subtitle = "View architect credits, GitHub repositories, and dynamic download updates."
            ) {
                Button(
                    onClick = { showAboutDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(imageVector = Icons.Default.HelpOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("View About & Check Updates", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }

    // MODAL DIALOGS
    // A. SLEEP TIMER DIALOG
    if (showSleepTimerDialog) {
        SleepTimerPickerDialog(
            onDismissRequest = { showSleepTimerDialog = false },
            onConfirm = { minutes ->
                if (minutes > 0) {
                    viewModel.setSleepTimer(minutes)
                    Toast.makeText(context, "Timer scheduled for $minutes minute(s).", Toast.LENGTH_SHORT).show()
                } else {
                    viewModel.setSleepTimer(0)
                    Toast.makeText(context, "Timer switched off.", Toast.LENGTH_SHORT).show()
                }
                showSleepTimerDialog = false
            }
        )
    }

    // B. EXEMPTED DIRECTORIES / HIDE FOLDER CHECKLIST DIALOG
    if (showHideFolderDialog) {
        AlertDialog(
            onDismissRequest = { showHideFolderDialog = false },
            title = {
                Text(
                    "Exempt Directories",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 350.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Check folders to exempt them from library catalogs. Uncheck to recover them.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 6.dp)
                    )

                    if (physicalFolders.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No scan directories found.",
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    } else {
                        physicalFolders.forEach { folderPath ->
                            val isHidden = hiddenFolderPaths.contains(folderPath)
                            val onFolderToggle = {
                                if (isHidden) {
                                    viewModel.removeHiddenFolder(folderPath)
                                } else {
                                    viewModel.addHiddenFolder(folderPath)
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onFolderToggle() }
                                    .background(
                                        if (isHidden) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                    )
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isHidden,
                                    onCheckedChange = { onFolderToggle() },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = MaterialTheme.colorScheme.error,
                                        uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                    )
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    val dirName = folderPath.substringAfterLast('/')
                                    Text(
                                        text = if (dirName.isBlank()) "Root Storage" else dirName,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = folderPath,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showHideFolderDialog = false }
                ) {
                    Text(
                        "Done",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            shape = RoundedCornerShape(16.dp),
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    // C. ABOUT MODAL DIALOG WITH REAL GITHUB UPDATE CHECKER
    if (showAboutDialog) {
        AboutDialog(
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

@Composable
fun ThemesDropdown(
    currentTheme: Boolean?,
    onThemeSelect: (Boolean?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val displayText = when (currentTheme) {
        true -> "Dark Mode"
        false -> "Light Mode"
        null -> "System Default"
    }

    Box {
        OutlinedCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true },
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(displayText, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = "Expand dropdown"
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth(0.9f)
        ) {
            DropdownMenuItem(
                text = { Text("Light Mode") },
                onClick = {
                    onThemeSelect(false)
                    expanded = false
                },
                leadingIcon = { Icon(Icons.Default.LightMode, contentDescription = null) }
            )
            DropdownMenuItem(
                text = { Text("Dark Mode") },
                onClick = {
                    onThemeSelect(true)
                    expanded = false
                },
                leadingIcon = { Icon(Icons.Default.DarkMode, contentDescription = null) }
            )
            DropdownMenuItem(
                text = { Text("System Default") },
                onClick = {
                    onThemeSelect(null)
                    expanded = false
                },
                leadingIcon = { Icon(Icons.Default.Android, contentDescription = null) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepTimerPickerDialog(
    onDismissRequest: () -> Unit,
    onConfirm: (minutes: Int) -> Unit
) {
    val state = rememberTimePickerState(initialHour = 0, initialMinute = 0, is24Hour = true)
    var customInput by remember { mutableStateOf("") }
    var useTextField by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                onClick = {
                    if (useTextField) {
                        val parsed = customInput.toIntOrNull() ?: 0
                        onConfirm(parsed)
                    } else {
                        val hours = state.hour
                        val mins = state.minute
                        onConfirm(hours * 60 + mins)
                    }
                }
            ) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Cancel")
            }
        },
        title = {
            Text("Set Sleep Timer", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Enter Minutes Manually", modifier = Modifier.weight(1f), fontSize = 14.sp)
                    Switch(checked = useTextField, onCheckedChange = { useTextField = it })
                }

                if (useTextField) {
                    OutlinedTextField(
                        value = customInput,
                        onValueChange = { customInput = it.filter { char -> char.isDigit() } },
                        label = { Text("Duration in Minutes") },
                        placeholder = { Text("e.g. 45") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    TimePicker(state = state)
                }
            }
        }
    )
}

@Composable
fun AboutDialog(
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    var checkingUpdates by remember { mutableStateOf(false) }
    var updateStatus by remember { mutableStateOf("Check for updates from GitHub release.") }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Close")
            }
        },
        title = {
            Text("About", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Media Player Pro",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Developer: KSA Sirajul\nContact: ksa.sirajul.2026@gmail.com",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "An offline media playback powerhouse featuring parametric equalizer boost, directory filters, active sleep suspensions, and zero telemetry analytics.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(6.dp))

                Button(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/ksa-sirajul-2026/media-player"))
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(imageVector = Icons.Default.Code, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("GitHub Project Source", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = updateStatus,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 15.sp,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = {
                            checkingUpdates = true
                            updateStatus = "Contacting GitHub..."
                            scope.launch(Dispatchers.IO) {
                                try {
                                    val url = URL("https://api.github.com/repos/ksa-sirajul-2026/media-player/releases/latest")
                                    val connection = url.openConnection() as java.net.HttpURLConnection
                                    connection.requestMethod = "GET"
                                    connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
                                    connection.setRequestProperty("User-Agent", "Mozilla/5.0")
                                    connection.connectTimeout = 5000
                                    connection.readTimeout = 5000

                                    val responseCode = connection.responseCode
                                    if (responseCode == 200) {
                                        val stream = connection.inputStream
                                        val responseText = stream.bufferedReader().use { it.readText() }
                                        val json = JSONObject(responseText)
                                        val tagName = json.optString("tag_name", "1.0.0")
                                        val body = json.optString("body", "No changes documented.")
                                        withContext(Dispatchers.Main) {
                                            updateStatus = "Latest version on GitHub: $tagName\nChanges: $body"
                                            checkingUpdates = false
                                        }
                                    } else {
                                        withContext(Dispatchers.Main) {
                                            updateStatus = "No releases found on GitHub repository (Code: $responseCode)"
                                            checkingUpdates = false
                                        }
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        updateStatus = "Could not check for updates:\n${e.localizedMessage}"
                                        checkingUpdates = false
                                    }
                                }
                            }
                        },
                        enabled = !checkingUpdates,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        if (checkingUpdates) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                        } else {
                            Icon(imageVector = Icons.Default.Update, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Check Latest Release", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        shape = RoundedCornerShape(16.dp),
        containerColor = MaterialTheme.colorScheme.surface
    )
}

private fun formatDuration(ms: Long): String {
    val sec = (ms / 1000) % 60
    val min = (ms / (1000 * 60)) % 60
    val hr = (ms / (1000 * 60 * 60))
    return if (hr > 0) {
        String.format("%02d:%02d:%02d", hr, min, sec)
    } else {
        String.format("%02d:%02d", min, sec)
    }
}
