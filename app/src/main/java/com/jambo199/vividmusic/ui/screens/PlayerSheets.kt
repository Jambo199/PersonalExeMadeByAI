package com.jambo199.vividmusic.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jambo199.vividmusic.data.LyricLine
import com.jambo199.vividmusic.data.Lyrics
import com.jambo199.vividmusic.data.OnlineLookup
import com.jambo199.vividmusic.data.OnlineLyrics
import com.jambo199.vividmusic.data.formatDuration
import com.jambo199.vividmusic.data.formatTotalDuration
import com.jambo199.vividmusic.data.pluralize
import com.jambo199.vividmusic.playback.SleepTimer
import com.jambo199.vividmusic.ui.MainViewModel
import com.jambo199.vividmusic.ui.components.DragHandle
import com.jambo199.vividmusic.ui.components.SongRow
import com.jambo199.vividmusic.ui.components.rememberReorderState
import com.jambo199.vividmusic.ui.components.reorderItem
import kotlinx.coroutines.delay

// region queue

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun QueueSheet(viewModel: MainViewModel, onDismiss: () -> Unit) {
    val queue by viewModel.queue.collectAsState()
    val playback by viewModel.playback.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val listState = rememberLazyListState()
    var saving by remember { mutableStateOf(false) }
    val currentPos = queue.indexOfFirst { it.index == playback.index }

    LaunchedEffect(Unit) {
        if (currentPos > 0) listState.scrollToItem((currentPos - 1).coerceAtLeast(0))
    }

    val reorder = rememberReorderState(
        source = queue,
        listState = listState,
        keyOf = { "q${it.index}:${it.song.key}" },
        onMove = { from, to ->
            val f = queue.getOrNull(from)?.index
            val t = queue.getOrNull(to)?.index
            if (f != null && t != null) viewModel.player.moveQueueItem(f, t)
        },
    )

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxHeight(0.92f)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Queue", style = MaterialTheme.typography.titleLarge)
                    val remaining = if (currentPos >= 0) queue.drop(currentPos + 1) else queue
                    Text(
                        "${pluralize(remaining.size, "song")} up next • ${formatTotalDuration(remaining.sumOf { it.song.durationMs })}" +
                            if (playback.shuffle) " • shuffled" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { saving = true }, enabled = queue.isNotEmpty()) { Text("Save") }
                TextButton(onClick = { viewModel.player.clearUpcoming() }, enabled = queue.size > 1) { Text("Clear") }
            }
            if (playback.shuffle) {
                Text(
                    "Turn shuffle off to drag songs into a new order.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
                )
            }
            HorizontalDivider(Modifier.padding(top = 8.dp))
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(bottom = 24.dp),
                modifier = Modifier.navigationBarsPadding(),
            ) {
                itemsIndexed(reorder.items, key = { _, e -> "q${e.index}:${e.song.key}" }) { pos, entry ->
                    val key = "q${entry.index}:${entry.song.key}"
                    val isCurrent = entry.index == playback.index
                    val past = currentPos >= 0 && pos < currentPos && !reorder.isDragging
                    SongRow(
                        song = entry.song,
                        modifier = Modifier
                            .reorderItem(reorder, key)
                            .then(if (reorder.draggingKey == key) Modifier else Modifier.animateItem())
                            .background(
                                if (isCurrent) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f) else Color.Transparent,
                            )
                            .graphicsLayer { alpha = if (past) 0.5f else 1f },
                        isCurrent = isCurrent,
                        isPlaying = playback.isPlaying,
                        subtitle = entry.song.displayArtist,
                        onClick = { viewModel.player.playQueueIndex(entry.index) },
                        onLongClick = null,
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (!isCurrent) {
                                    IconButton(onClick = { viewModel.player.removeQueueIndex(entry.index) }) {
                                        Icon(Icons.Rounded.Close, contentDescription = "Remove from queue")
                                    }
                                }
                                DragHandle(reorder, key, enabled = !playback.shuffle)
                            }
                        },
                    )
                }
            }
        }
    }

    if (saving) {
        NameDialog("Save queue as playlist", "", "Save", onDismiss = { saving = false }) {
            viewModel.saveQueueAsPlaylist(it)
            saving = false
        }
    }
}

// endregion

// region lyrics

@Composable
fun LyricsView(
    songKey: String,
    lyrics: Lyrics?,
    lookup: OnlineLookup,
    autoSearch: Boolean,
    onSearchOnline: (force: Boolean) -> Unit,
    position: () -> Long,
    isPlaying: Boolean,
    onSeek: (Long) -> Unit,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    if (lyrics == null) {
        if (autoSearch) {
            LaunchedEffect(songKey) {
                // Give lyrics stored in the file a moment to turn up before going online.
                delay(1200)
                onSearchOnline(false)
            }
        }
        NoLyrics(lookup, contentColor, onSearchOnline, modifier)
        return
    }
    val credit: @Composable () -> Unit = {
        if (lyrics.source == OnlineLyrics.SOURCE) {
            Text(
                "Lyrics from LRCLIB",
                style = MaterialTheme.typography.labelMedium,
                color = contentColor.copy(alpha = 0.55f),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp),
            )
        }
    }
    if (!lyrics.isSynced) {
        LazyColumn(modifier, contentPadding = PaddingValues(vertical = 24.dp, horizontal = 8.dp)) {
            item {
                Text(
                    lyrics.plain,
                    style = MaterialTheme.typography.titleMedium.copy(lineHeight = MaterialTheme.typography.titleMedium.lineHeight * 1.3f),
                    color = contentColor,
                )
            }
            item { credit() }
        }
        return
    }

    val lines = lyrics.lines
    val listState = rememberLazyListState()
    var positionMs by remember { mutableLongStateOf(position()) }
    LaunchedEffect(isPlaying, lyrics) {
        while (true) {
            positionMs = position()
            delay(if (isPlaying) 150 else 600)
        }
    }
    val current = currentLine(lines, positionMs)
    LaunchedEffect(current) {
        if (current >= 0) listState.animateScrollToItem((current - 2).coerceAtLeast(0))
    }
    LazyColumn(modifier, state = listState, contentPadding = PaddingValues(top = 16.dp, bottom = 160.dp)) {
        itemsIndexed(lines) { i, line ->
            LyricRow(line, active = i == current, passed = i < current, contentColor = contentColor) { onSeek(line.timeMs) }
        }
        item { credit() }
    }
}

@Composable
private fun NoLyrics(
    lookup: OnlineLookup,
    contentColor: Color,
    onSearchOnline: (force: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val buttonColors = ButtonDefaults.outlinedButtonColors(contentColor = contentColor)
    val border = BorderStroke(1.dp, contentColor.copy(alpha = 0.5f))
    Column(
        modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        val message = when (lookup.state) {
            OnlineLookup.State.SEARCHING -> "Looking for lyrics online…"
            OnlineLookup.State.NOT_FOUND -> "No lyrics found, here or on LRCLIB.\n\nPut a .lrc file with the same name next to the song, or use ⋮ → Link lyrics file."
            OnlineLookup.State.FAILED -> lookup.message ?: "Couldn't look up lyrics."
            OnlineLookup.State.IDLE -> "No lyrics on your phone for this song.\n\nPut a .lrc file with the same name next to the song, use ⋮ → Link lyrics file, or search online."
        }
        if (lookup.state == OnlineLookup.State.SEARCHING) {
            CircularProgressIndicator(color = contentColor, strokeWidth = 3.dp)
        }
        Text(
            message,
            style = MaterialTheme.typography.bodyLarge,
            color = contentColor.copy(alpha = 0.8f),
            textAlign = TextAlign.Center,
        )
        when (lookup.state) {
            OnlineLookup.State.IDLE -> OutlinedButton(onClick = { onSearchOnline(true) }, colors = buttonColors, border = border) {
                Icon(Icons.Rounded.TravelExplore, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Search online")
            }
            OnlineLookup.State.FAILED -> OutlinedButton(onClick = { onSearchOnline(true) }, colors = buttonColors, border = border) {
                Text("Try again")
            }
            else -> Unit
        }
    }
}

@Composable
private fun LyricRow(line: LyricLine, active: Boolean, passed: Boolean, contentColor: Color, onClick: () -> Unit) {
    val alpha by animateFloatAsState(if (active) 1f else if (passed) 0.45f else 0.6f, label = "lyricAlpha")
    val scale by animateFloatAsState(if (active) 1.04f else 1f, label = "lyricScale")
    val color by animateColorAsState(contentColor.copy(alpha = alpha), label = "lyricColor")
    Text(
        line.text.ifBlank { "♪" },
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold,
        color = color,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
            }
            .padding(horizontal = 12.dp, vertical = 10.dp),
    )
}

private fun currentLine(lines: List<LyricLine>, positionMs: Long): Int {
    var lo = 0
    var hi = lines.lastIndex
    var ans = -1
    while (lo <= hi) {
        val mid = (lo + hi) / 2
        if (lines[mid].timeMs <= positionMs + 200) {
            ans = mid
            lo = mid + 1
        } else {
            hi = mid - 1
        }
    }
    return ans
}

// endregion

// region sleep timer & speed

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SleepTimerDialog(viewModel: MainViewModel, onDismiss: () -> Unit) {
    val mode by viewModel.sleepTimer.collectAsState()
    var custom by rememberSaveable { mutableFloatStateOf(30f) }
    var remaining by remember { mutableLongStateOf(SleepTimer.remainingMs()) }
    LaunchedEffect(mode) {
        while (mode is SleepTimer.Mode.At) {
            remaining = SleepTimer.remainingMs()
            delay(1000)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sleep timer") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (val m = mode) {
                    is SleepTimer.Mode.At -> Text(
                        "Music fades out and stops in ${formatDuration(remaining)}.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    SleepTimer.Mode.EndOfSong -> Text(
                        "Music stops when this song ends.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    SleepTimer.Mode.Off -> Text("Stop the music after:", style = MaterialTheme.typography.bodyLarge)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(5, 10, 15, 30, 45, 60, 90).forEach { min ->
                        AssistChip(onClick = {
                            SleepTimer.startMinutes(min)
                            onDismiss()
                        }, label = { Text("$min min") })
                    }
                    FilterChip(
                        selected = mode == SleepTimer.Mode.EndOfSong,
                        onClick = {
                            SleepTimer.startEndOfSong()
                            onDismiss()
                        },
                        label = { Text("End of song") },
                    )
                }
                Text("Custom: ${custom.toInt()} min", style = MaterialTheme.typography.labelLarge)
                Slider(value = custom, onValueChange = { custom = it }, valueRange = 1f..180f)
                if (mode is SleepTimer.Mode.At) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(onClick = { SleepTimer.extend(5) }, label = { Text("+5 min") })
                        AssistChip(onClick = { SleepTimer.extend(15) }, label = { Text("+15 min") })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                SleepTimer.startMinutes(custom.toInt())
                onDismiss()
            }) { Text("Start ${custom.toInt()} min") }
        },
        dismissButton = {
            if (mode != SleepTimer.Mode.Off) {
                TextButton(onClick = {
                    SleepTimer.cancel()
                    onDismiss()
                }) { Text("Turn off") }
            } else {
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpeedDialog(viewModel: MainViewModel, onDismiss: () -> Unit) {
    val playback by viewModel.playback.collectAsState()
    var speed by remember { mutableFloatStateOf(playback.speed) }
    var pitch by remember { mutableFloatStateOf(playback.pitch) }
    fun applySpeed() = viewModel.player.setSpeed(speed, pitch)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Playback speed") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Speed: ${"%.2f".format(speed)}×", style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = speed,
                    onValueChange = { speed = (it * 20).toInt() / 20f },
                    onValueChangeFinished = { applySpeed() },
                    valueRange = 0.5f..2f,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0.75f, 1f, 1.25f, 1.5f, 2f).forEach { s ->
                        FilterChip(
                            selected = speed == s,
                            onClick = {
                                speed = s
                                applySpeed()
                            },
                            label = { Text("${s}×") },
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text("Pitch: ${"%.2f".format(pitch)}", style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = pitch,
                    onValueChange = { pitch = (it * 20).toInt() / 20f },
                    onValueChangeFinished = { applySpeed() },
                    valueRange = 0.5f..2f,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        dismissButton = {
            TextButton(onClick = {
                speed = 1f
                pitch = 1f
                applySpeed()
            }) { Text("Reset") }
        },
    )
}

// endregion
