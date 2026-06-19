package com.example.ui.layout

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.MediaEntity
import com.example.ui.viewmodel.MediaViewModel
import com.example.ui.components.AlbumArtImage
import com.example.ui.components.CustomAospSeekBar

@Composable
fun MiniPlayerCard(
    song: MediaEntity,
    isPlaying: Boolean,
    viewModel: MediaViewModel,
    onClick: () -> Unit
) {
    val progress by viewModel.currentPosition.collectAsState()
    val totalTime by viewModel.duration.collectAsState()
    val playbackSpeed by viewModel.playbackSpeed.collectAsState()
    val timerRemaining by viewModel.sleepTimerRemaining.collectAsState()
    val waveformStyle by viewModel.waveformStylePref.collectAsState()
    val waveformColorType by viewModel.waveformColorPref.collectAsState()

    var showMiniSpeedDialog by remember { mutableStateOf(false) }
    var showMiniSleepTimerDialog by remember { mutableStateOf(false) }

    var isSeeking by remember { mutableStateOf(false) }
    var localSeekProgress by remember { mutableFloatStateOf(0f) }
    val currentDisplayPosition = if (isSeeking) (localSeekProgress * totalTime).toLong() else progress

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(116.dp)
            .clickable(onClick = onClick)
            .border(
                BorderStroke(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)),
                shape = RoundedCornerShape(24.dp)
            ),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 6.dp, bottom = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Drag handle hook
            Box(
                modifier = Modifier
                    .width(36.dp)
                    .height(4.dp)
                    .background(
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        shape = CircleShape
                    )
                    .align(Alignment.CenterHorizontally)
            )

            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    AlbumArtImage(
                        songPath = song.path,
                        songTitle = song.title,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            song.title,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                song.artist,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            WaveformVisualizer(
                                isPlaying = isPlaying,
                                style = waveformStyle,
                                colorType = waveformColorType,
                                modifier = Modifier.width(28.dp).height(12.dp)
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Playback Speed Toggle
                    IconButton(onClick = { showMiniSpeedDialog = true }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Filled.Speed,
                            contentDescription = "Speed",
                            tint = if (playbackSpeed != 1.0f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(onClick = { viewModel.playPrevious() }, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Filled.SkipPrevious,
                            contentDescription = "Prev",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    IconButton(
                        onClick = { if (isPlaying) viewModel.pause() else viewModel.play() },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Filled.PauseCircle else Icons.Filled.PlayCircle,
                            contentDescription = "PlayPause",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(38.dp)
                        )
                    }

                    IconButton(onClick = { viewModel.playNext() }, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Filled.SkipNext,
                            contentDescription = "Next",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Sleep Stop Timer
                    IconButton(onClick = { showMiniSleepTimerDialog = true }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Filled.Timer,
                            contentDescription = "Sleep Timer",
                            tint = if (timerRemaining > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Interactive Progress Transport Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatDuration(currentDisplayPosition),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                ) {
                    CustomAospSeekBar(
                        progress = if (totalTime > 0) currentDisplayPosition.toFloat() / totalTime else 0f,
                        onValueChange = { percent ->
                            isSeeking = true
                            localSeekProgress = percent
                        },
                        onValueChangeFinished = {
                            val target = (localSeekProgress * totalTime).toLong()
                            viewModel.seekTo(target)
                            isSeeking = false
                        }
                    )
                }

                Text(
                    text = formatDuration(totalTime),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    if (showMiniSpeedDialog) {
        AlertDialog(
            onDismissRequest = { showMiniSpeedDialog = false },
            title = { Text("Playback Speed", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
                    speeds.forEach { speed ->
                        val selected = playbackSpeed == speed
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setPlaybackSpeed(speed)
                                    showMiniSpeedDialog = false
                                }
                                .padding(vertical = 10.dp, horizontal = 12.dp)
                                .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent, shape = RoundedCornerShape(8.dp)),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${speed}x", color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                            if (selected) {
                                Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMiniSpeedDialog = false }) {
                    Text("Close", color = MaterialTheme.colorScheme.primary)
                }
            }
        )
    }

    if (showMiniSleepTimerDialog) {
        val activeTimerRemaining by viewModel.sleepTimerRemaining.collectAsState()
        AlertDialog(
            onDismissRequest = { showMiniSleepTimerDialog = false },
            title = { Text("Sleep Timer", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (activeTimerRemaining > 0) {
                        val minutesLeft = (activeTimerRemaining / 1000L) / 60
                        val secondsLeft = (activeTimerRemaining / 1000L) % 60
                        Text(
                            text = String.format("Active Timer: %5d:%02d remaining", minutesLeft, secondsLeft),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    
                    val timerOptions = listOf(
                        "Stop Sleep Timer" to 0,
                        "15 minutes" to 15,
                        "30 minutes" to 30,
                        "60 minutes" to 60
                    )
                    
                    timerOptions.forEach { (label, mins) ->
                        if (mins > 0 || activeTimerRemaining > 0) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.setSleepTimer(mins)
                                        showMiniSleepTimerDialog = false
                                    }
                                    .padding(vertical = 10.dp, horizontal = 12.dp)
                                    .background(
                                        color = if (mins == 0 && activeTimerRemaining == 0L) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
                                        shape = RoundedCornerShape(8.dp)
                                    ),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(label, color = if (mins == 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                                if (mins > 0 && activeTimerRemaining > 0 && (activeTimerRemaining / 1000L / 60).toLong() == mins.toLong()) {
                                    Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMiniSleepTimerDialog = false }) {
                    Text("Close", color = MaterialTheme.colorScheme.primary)
                }
            }
        )
    }
}

private fun formatDuration(millis: Long): String {
    val sec = (millis / 1000) % 60
    val min = (millis / 1000 / 60) % 60
    val hr = (millis / 1000 / 3600)
    return if (hr > 0) {
        String.format("%02d:%02d:%02d", hr, min, sec)
    } else {
        String.format("%02d:%02d", min, sec)
    }
}
