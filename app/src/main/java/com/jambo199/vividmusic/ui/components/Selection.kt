package com.jambo199.vividmusic.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jambo199.vividmusic.data.Song
import com.jambo199.vividmusic.data.pluralize

/** Multi-select across a song list: long-press a song to start, tap to add or remove. */
@Stable
class SelectionState {
    private val keys = mutableStateListOf<String>()
    private var songsByKey: Map<String, Song> = emptyMap()
    private var scope: List<Song> by mutableStateOf(emptyList())

    val active: Boolean by derivedStateOf { keys.isNotEmpty() }
    val count: Int get() = keys.size

    /** Selected songs in the order they appear in the list they came from. */
    val selectedSongs: List<Song>
        get() {
            val set = keys.toHashSet()
            val inScope = scope.filter { it.key in set }
            return if (inScope.size == set.size) inScope else keys.mapNotNull { songsByKey[it] }
        }

    fun isSelected(song: Song): Boolean = song.key in keys

    fun start(song: Song, list: List<Song>) {
        if (scope !== list) {
            keys.clear()
            scope = list
            songsByKey = list.associateBy { it.key }
        }
        if (song.key !in keys) keys += song.key
    }

    fun toggle(song: Song) {
        if (song.key in keys) keys -= song.key else keys += song.key
    }

    fun selectAll() {
        keys.clear()
        keys.addAll(scope.map { it.key }.distinct())
    }

    fun clear() {
        keys.clear()
    }
}

val LocalSelection = staticCompositionLocalOf<SelectionState> { error("SelectionState not provided") }

/** Bottom action bar shown while songs are selected. */
@Composable
fun SelectionBar(
    selection: SelectionState,
    onPlay: () -> Unit,
    onQueue: () -> Unit,
    onPlaylist: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Keep showing the last count while the bar animates away.
    val lastCount = remember { IntArray(1) }
    if (selection.count > 0) lastCount[0] = selection.count
    val shownCount = lastCount[0]
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
    ) {
        Row(
            Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            IconButton(onClick = { selection.clear() }) { Icon(Icons.Rounded.Close, contentDescription = "Cancel selection") }
            Column(Modifier.weight(1f)) {
                Text(pluralize(shownCount, "song"), style = MaterialTheme.typography.titleSmall)
                Text("selected", style = MaterialTheme.typography.labelSmall)
            }
            IconButton(onClick = { selection.selectAll() }) { Icon(Icons.Rounded.SelectAll, contentDescription = "Select all") }
            IconButton(onClick = onPlay) { Icon(Icons.Rounded.PlayArrow, contentDescription = "Play selected") }
            IconButton(onClick = onQueue) { Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = "Add selected to queue") }
            IconButton(onClick = onPlaylist) { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = "Add selected to playlist") }
            IconButton(onClick = onMore) { Icon(Icons.Rounded.MoreVert, contentDescription = "More actions") }
            Spacer(Modifier.width(2.dp))
        }
    }
}
