package com.msi.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.msi.ui.theme.ResponsiveDimensions
import com.msi.ui.viewmodel.MediaViewModel

@Composable
fun PlaylistSidebar(
    viewModel: MediaViewModel,
    isOpen: Boolean,
    onClose: () -> Unit
) {
    val queue by viewModel.currentQueue.collectAsState()
    val currentIndex by viewModel.currentTrackIndex.collectAsState()
    val dims = ResponsiveDimensions.dimensions

    AnimatedVisibility(
        visible = isOpen,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        // Overlay mask
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(
                    onClick = onClose,
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                )
        ) {
            // Sliding Sidebar Pane
            Row(modifier = Modifier.fillMaxSize()) {
                Spacer_Weight(1f) // Push sidebar drawer to align to the right!

                Column(
                    modifier = Modifier
                        .width(dims.scaleDp(260.dp)) // Scaled compact drawer width
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable(enabled = true, onClick = { /* Consuming taps */ })
                        .padding(dims.scaleDp(12.dp))
                ) {
                    // Header title
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.QueueMusic,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(dims.scaleDp(22.dp))
                        )
                        Text(
                            text = "Deck Queue list",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = dims.scaleSp(13.sp),
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = dims.scaleDp(8.dp))
                        )
                        IconButton(onClick = onClose, modifier = Modifier.size(dims.scaleDp(32.dp))) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Close Playlist drawer",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(dims.scaleDp(18.dp))
                            )
                        }
                    }

                    Divider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                        modifier = Modifier.padding(vertical = dims.scaleDp(8.dp))
                    )

                    if (queue.isEmpty()) {
                        Box(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No active tracks in deck play Queue.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = dims.scaleSp(11.sp),
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    } else {
                        LazyColumn(modifier = Modifier.weight(1f)) {
                            itemsIndexed(queue) { idx, item ->
                                val active = idx == currentIndex
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(
                                            if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                            else Color.Transparent
                                        )
                                        .clickable {
                                            viewModel.playMediaItem(item, queue)
                                            onClose()
                                        }
                                        .padding(dims.scaleDp(8.dp)),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${idx + 1}.",
                                        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = dims.scaleSp(10.sp),
                                        modifier = Modifier.padding(end = dims.scaleDp(6.dp))
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.title,
                                            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = dims.scaleSp(11.sp)
                                        )
                                        Text(
                                            text = item.artist,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                            fontSize = dims.scaleSp(9.sp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Spacer_Weight(weight: Float) {
    Box(modifier = Modifier.width(0.dp).fillMaxHeight().run { weight(weight) })
}
