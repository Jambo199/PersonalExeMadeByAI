package com.jambo199.vividmusic.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jambo199.vividmusic.AppGraph
import com.jambo199.vividmusic.data.LrcWriter
import com.jambo199.vividmusic.ui.LocalNav
import com.jambo199.vividmusic.ui.MainViewModel

/**
 * Timing plain lyrics by hand: play the song and tap each time a new line starts. The result is
 * saved as an .lrc file for the song, so its lyrics follow along from then on.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsSyncScreen(viewModel: MainViewModel) {
    val playback by viewModel.playback.collectAsState()
    val lyrics by viewModel.lyrics.collectAsState()
    val nav = LocalNav.current
    val song = playback.song
    // The song and its words when the page opened; a new song doesn't swap them underneath you.
    val key = remember { song?.key }
    val lines = remember { LrcWriter.linesOf(lyrics?.takeIf { it.songKey == key }?.plain.orEmpty()) }
    val times = remember { mutableStateListOf<Long>() }
    val listState = rememberLazyListState()
    LaunchedEffect(times.size) {
        if (lines.isNotEmpty()) listState.animateScrollToItem((times.size - 2).coerceAtLeast(0))
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text("Sync the lyrics")
                    song?.let { Text(it.title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            },
            navigationIcon = {
                IconButton(onClick = { nav.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
            },
        )
        if (lines.isEmpty() || song == null || song.key != key) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    if (song?.key != key) "The song changed. Go back and open this again from the song you want to sync."
                    else "There are no plain lyrics to sync for this song.",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            return@Column
        }
        Text(
            "Play the song from the start and tap the big button each time a new line begins. Made a slip? Undo goes back a line.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        LazyColumn(
            Modifier.weight(1f),
            state = listState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            itemsIndexed(lines) { i, line ->
                val next = i == times.size
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (next) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        times.getOrNull(i)?.let { LrcWriter.stamp(it).trim('[', ']') } ?: "--:--.--",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(72.dp),
                    )
                    Text(
                        line,
                        style = if (next) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                        fontWeight = if (next) FontWeight.Bold else FontWeight.Normal,
                        color = if (i < times.size) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = {
                    times.clear()
                    viewModel.player.seekTo(0)
                    viewModel.player.play()
                }) { Icon(Icons.Rounded.Replay, contentDescription = "Start again from the beginning") }
                IconButton(onClick = { viewModel.player.togglePlayPause() }) {
                    Icon(
                        if (playback.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (playback.isPlaying) "Pause" else "Play",
                    )
                }
                IconButton(
                    enabled = times.isNotEmpty(),
                    onClick = {
                        times.removeAt(times.lastIndex)
                        // Back a little before the line that's been undone, to tap it again.
                        viewModel.player.seekTo(((times.lastOrNull() ?: 0L) - 1_000).coerceAtLeast(0))
                    },
                ) { Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = "Undo the last line") }
                Text("${times.size} / ${lines.size}", style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(8.dp))
            if (times.size < lines.size) {
                Button(
                    onClick = {
                        if (!playback.isPlaying && times.isEmpty()) {
                            viewModel.player.seekTo(0)
                            viewModel.player.play()
                        } else {
                            val now = viewModel.player.position()
                            // Never earlier than the line before.
                            times += maxOf(now, times.lastOrNull() ?: 0L)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp),
                ) {
                    Text(
                        if (!playback.isPlaying && times.isEmpty()) "Play from the start" else "Tap: line ${times.size + 1} starts now",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            } else {
                FilledTonalButton(
                    onClick = {
                        AppGraph.lyrics.saveOwn(song.key, LrcWriter.write(song.title, song.displayArtist, lines, times.toList()))
                        viewModel.toast("Synced lyrics saved")
                        nav.back()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp),
                ) { Text("Save synced lyrics", style = MaterialTheme.typography.titleMedium) }
                TextButton(onClick = { times.clear() }) { Text("Start over") }
            }
            Spacer(Modifier.size(4.dp))
        }
    }
}
