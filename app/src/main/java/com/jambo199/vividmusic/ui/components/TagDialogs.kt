package com.jambo199.vividmusic.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.jambo199.vividmusic.AppGraph
import com.jambo199.vividmusic.data.LocalTags
import com.jambo199.vividmusic.data.Settings
import com.jambo199.vividmusic.data.Song
import com.jambo199.vividmusic.data.TagEdits
import com.jambo199.vividmusic.data.TagLookup
import com.jambo199.vividmusic.data.TagMatch
import com.jambo199.vividmusic.data.TagQuery
import com.jambo199.vividmusic.ui.MainViewModel
import kotlinx.coroutines.launch

/**
 * Title, artist, album and the rest, saved into the file itself. The tags can be filled in from
 * the file and folder names, or from an online match (with its cover), then checked and saved.
 */
@Composable
fun EditTagsDialog(song: Song, onDismiss: () -> Unit, onSave: (TagEdits, TagMatch?) -> Unit) {
    val settings by AppGraph.settings.state.collectAsState()
    val start = remember(song.key) { TagEdits.of(song) }
    val fileBase = song.fileName.substringBeforeLast('.')
    var title by rememberSaveable { mutableStateOf(start.title) }
    var artist by rememberSaveable { mutableStateOf(start.artist) }
    var album by rememberSaveable { mutableStateOf(start.album) }
    var albumArtist by rememberSaveable { mutableStateOf(start.albumArtist) }
    var genre by rememberSaveable { mutableStateOf(start.genre) }
    var year by rememberSaveable { mutableStateOf(start.year) }
    var track by rememberSaveable { mutableStateOf(start.track) }
    var results by remember { mutableStateOf<List<TagMatch>?>(null) }
    var searching by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    var cover by remember { mutableStateOf<TagMatch?>(null) }
    var useCover by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    fun current() = TagEdits(title, artist, album, albumArtist, genre, year, track)
    fun fill(m: TagMatch) {
        val e = current().with(m, fillOnly = false, fileBase = fileBase)
        title = e.title
        artist = e.artist
        album = e.album
        albumArtist = e.albumArtist
        genre = e.genre
        year = e.year.filter(Char::isDigit).take(4)
        track = e.track.filter(Char::isDigit).take(3)
    }
    fun pick(m: TagMatch) {
        scope.launch {
            val full = AppGraph.tags.complete(m)
            fill(full)
            cover = full.takeIf { it.artworkUrl != null }
            useCover = true
            note = "Filled in from ${full.source}. Check the tags, then Save."
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit tags") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(song.fileName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                    OutlinedButton(
                        onClick = {
                            val guess = LocalTags.guess(song.fileName, LocalTags.folders(song.path))
                            if (guess == null) {
                                note = "The file and folder names don't say more than this."
                            } else {
                                fill(guess)
                                note = "Filled in from the file and folder names."
                            }
                        },
                        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Rounded.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("From file name", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    OutlinedButton(
                        onClick = {
                            searching = true
                            note = null
                            scope.launch {
                                // What's in the boxes now, so a fixed title or artist finds better matches.
                                val q = if (current() == start) TagQuery.of(song) else TagQuery(title.ifBlank { fileBase }, artist, album, song.durationMs)
                                val found = AppGraph.tags.search(q)
                                results = found.matches
                                note = when {
                                    found.matches.isNotEmpty() -> null
                                    found.unreachable.size >= 3 -> "Couldn't reach the music catalogues. Check your internet connection."
                                    else -> "Nothing found. Fix the title or artist above, then search again."
                                }
                                searching = false
                            }
                        },
                        enabled = !searching,
                        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (searching) "Searching…" else "Search online", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                results?.takeIf { it.isNotEmpty() }?.let { list ->
                    val best = TagLookup.best(list, settings.tagMinMatch)
                    if (best != null) {
                        FilledTonalButton(onClick = { pick(best) }, modifier = Modifier.fillMaxWidth()) {
                            Text("Use the best match (${best.score}% sure)")
                        }
                    } else {
                        Text(
                            "None is a sure match (${settings.tagMinMatch}% or more). Tap the right one, if it's there.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    list.forEach { m -> MatchRow(m, best = m == best) { pick(m) } }
                }
                cover?.let { m ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(12.dp)).clickable { useCover = !useCover },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AsyncImage(
                            model = m.thumbUrl ?: m.artworkUrl,
                            contentDescription = "New cover",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Use this cover", style = MaterialTheme.typography.bodyMedium)
                            Text("From ${m.source}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Checkbox(useCover, { useCover = it })
                    }
                }
                OutlinedTextField(title, { title = it }, label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(artist, { artist = it }, label = { Text("Artist") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(album, { album = it }, label = { Text("Album") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(albumArtist, { albumArtist = it }, label = { Text("Album artist") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(genre, { genre = it }, label = { Text("Genre") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        year,
                        { v -> year = v.filter(Char::isDigit).take(4) },
                        label = { Text("Year") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        track,
                        { v -> track = v.filter(Char::isDigit).take(3) },
                        label = { Text("Track") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(current(), cover?.takeIf { useCover }) },
                enabled = title.isNotBlank(),
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** One online match: its cover, what it says, where it's from and how sure it is. */
@Composable
private fun MatchRow(m: TagMatch, best: Boolean, onPick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onPick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
            if (m.thumbUrl != null) {
                AsyncImage(model = m.thumbUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(m.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (m.summary.isNotBlank()) {
                Text(m.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text(
                m.source + if (best) " · best match" else "",
                style = MaterialTheme.typography.labelSmall,
                color = if (best) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            "${m.score}%",
            style = MaterialTheme.typography.labelLarge,
            color = if (best) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** How auto-tag should go (kept for next time), before it starts. */
@Composable
fun AutoTagDialog(count: Int, onDismiss: () -> Unit, onStart: () -> Unit) {
    val settings by AppGraph.settings.state.collectAsState()
    fun change(transform: (Settings) -> Settings) = AppGraph.settings.update(transform)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (count == 1) "Auto-tag this song" else "Auto-tag $count songs") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Where the tags come from", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                ChoiceRow(
                    selected = settings.tagOnline,
                    title = "Look them up online",
                    note = "iTunes, Deezer and MusicBrainz. Each song gets the match that fits it best, with its cover.",
                ) { change { it.copy(tagOnline = true) } }
                ChoiceRow(
                    selected = !settings.tagOnline,
                    title = "From file and folder names",
                    note = "Works offline: \"03 - Artist - Title.mp3\" in a folder like \"Artist/Album (2001)\".",
                ) { change { it.copy(tagOnline = false) } }
                if (settings.tagOnline) {
                    Text(
                        "Use a match when it's at least",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(70, 85, 95).forEach { v ->
                            FilterChip(selected = settings.tagMinMatch == v, onClick = { change { it.copy(tagMinMatch = v) } }, label = { Text("$v% sure") })
                        }
                    }
                    Text(
                        "Songs without a match that sure are left as they are, and listed at the end.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                SwitchRow(
                    title = "Only fill in missing tags",
                    note = if (settings.tagFillOnly) "Tags the songs already have stay as they are" else "Replaces the songs' tags with the match's",
                    checked = settings.tagFillOnly,
                ) { v -> change { it.copy(tagFillOnly = v) } }
                if (settings.tagOnline) {
                    SwitchRow(
                        title = "Add album covers",
                        note = if (settings.tagFillOnly) "To songs that don't have one" else "Replacing the covers they have",
                        checked = settings.tagCovers,
                    ) { v -> change { it.copy(tagCovers = v) } }
                }
            }
        },
        confirmButton = { TextButton(onClick = onStart) { Text("Start") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ChoiceRow(selected: Boolean, title: String, note: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SwitchRow(title: String, note: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onChange(!checked) }.padding(top = 10.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** An auto-tag run: how far it's got, then what happened to each song. */
@Composable
fun AutoTagProgressDialog(run: MainViewModel.AutoTagRun, onStop: () -> Unit, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = { if (run.finished) onClose() },
        properties = DialogProperties(dismissOnClickOutside = false),
        title = {
            Text(
                when {
                    !run.finished -> "Auto-tagging…"
                    run.cancelled -> "Auto-tag stopped"
                    else -> "Auto-tag finished"
                },
            )
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LinearProgressIndicator(
                    progress = { if (run.total > 0) run.done.toFloat() / run.total else 0f },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("${run.done} of ${run.total}", style = MaterialTheme.typography.bodyMedium)
                run.current?.let {
                    Text("Now: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(
                    listOfNotNull(
                        "${run.tagged} tagged",
                        run.alreadyRight.takeIf { it > 0 }?.let { "$it already right" },
                        run.notSure.size.takeIf { it > 0 }?.let { "$it not sure" },
                        run.failed.size.takeIf { it > 0 }?.let { "$it couldn't be changed" },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (run.finished && run.notSure.isNotEmpty()) {
                    Text("No match sure enough for", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
                    run.notSure.take(10).forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    if (run.notSure.size > 10) Text("and ${run.notSure.size - 10} more", style = MaterialTheme.typography.bodySmall)
                    Text(
                        "Open Edit tags on one of these and search online to pick a match yourself.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (run.failed.isNotEmpty()) {
                    Text("Couldn't change", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
                    run.failed.take(6).forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                }
            }
        },
        confirmButton = {
            if (run.finished) TextButton(onClick = onClose) { Text("Done") } else TextButton(onClick = onStop) { Text("Stop") }
        },
    )
}
