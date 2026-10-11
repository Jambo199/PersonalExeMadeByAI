package com.jambo199.vividmusic.ui.online

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.jambo199.vividmusic.data.Song
import com.jambo199.vividmusic.online.Downloads
import com.jambo199.vividmusic.online.Ncs
import com.jambo199.vividmusic.online.NcsFilter
import com.jambo199.vividmusic.online.NcsTrack
import com.jambo199.vividmusic.ui.LocalSongMenu
import com.jambo199.vividmusic.ui.MainViewModel
import com.jambo199.vividmusic.ui.components.LocalBottomPadding
import com.jambo199.vividmusic.ui.components.PlayingIndicator
import java.io.File

/**
 * NoCopyrightSounds: every NCS release, newest first. Play straight from NCS, or save songs
 * into Music › NCS so they join the library and play offline.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NcsScreen(viewModel: MainViewModel) {
    val ncs = ncsViewModel()
    val menu = LocalSongMenu.current
    val listing by ncs.listing.collectAsState()
    val genres by ncs.genres.collectAsState()
    val moods by ncs.moods.collectAsState()
    val downloads by ncs.downloadInfo.collectAsState()
    val saveAll by ncs.saveAll.collectAsState()
    val library by viewModel.library.collectAsState()
    val playback by viewModel.playback.collectAsState()
    val bottom = LocalBottomPadding.current
    val gate = rememberStorageGate(viewModel)
    var saveDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) { ncs.start() }
    DisposableEffect(Unit) {
        ncs.watchDownloads()
        onDispose { ncs.stopWatchingDownloads() }
    }

    // Songs already saved play from the phone instead of the internet.
    val musicRoot = remember { Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC) }
    val byPath = remember(library) { library.songs.associateBy { it.path } }
    val libraryPaths = byPath.keys
    val songs: List<Song> = remember(listing.tracks, byPath) {
        listing.tracks.map { t -> byPath[File(musicRoot, t.downloadPath).absolutePath] ?: t.toSong() }
    }
    fun saved(t: NcsTrack) = File(musicRoot, t.downloadPath).absolutePath in byPath ||
        downloads[t.url]?.status == Downloads.Status.DONE
    val source = when {
        listing.query.isNotBlank() -> "NCS · \"${listing.query}\""
        else -> listOfNotNull("NCS", listing.genre?.name, listing.mood?.name).joinToString(" · ")
    }

    fun save(tracks: List<NcsTrack>) = gate {
        val started = ncs.save(tracks, libraryPaths)
        viewModel.toast(
            when (started) {
                0 -> if (tracks.size == 1) "Already saved or saving" else "These are already saved or saving"
                1 -> "Saving to Music › NCS"
                else -> "Saving $started songs to Music › NCS"
            },
        )
    }

    val listState = rememberLazyListState()
    val nearEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 6
        }
    }
    LaunchedEffect(nearEnd, listing.tracks.size, listing.loading) {
        if (nearEnd && listing.pages > 0 && listing.error == null) ncs.loadMore()
    }

    LazyColumn(state = listState, contentPadding = PaddingValues(bottom = bottom + 16.dp)) {
        item(key = "search") {
            OnlineSearchField("Search NCS songs and artists", listing.query) { ncs.search(it) }
        }
        if (genres.isNotEmpty()) {
            item(key = "genres") { FilterRow("All genres", genres, listing.genre) { ncs.selectGenre(it) } }
        }
        if (moods.isNotEmpty()) {
            item(key = "moods") { FilterRow("Any mood", moods, listing.mood) { ncs.selectMood(it) } }
        }
        saveAll?.let { progress ->
            item(key = "save-all") {
                SaveAllCard(progress, onStop = { ncs.stopSaveEverything() }, onDismiss = { ncs.dismissSaveEverything() })
            }
        }
        item(key = "title") {
            SectionTitle(
                title = when {
                    listing.query.isNotBlank() -> "Results for \"${listing.query}\""
                    listing.filtered -> listOfNotNull(listing.genre?.name, listing.mood?.name).joinToString(" · ")
                    else -> "Latest releases"
                },
                subtitle = "NoCopyrightSounds",
            )
        }
        if (songs.isNotEmpty()) {
            item(key = "buttons") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(onClick = { viewModel.playInOrder(songs, source) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Play", maxLines = 1)
                    }
                    FilledTonalButton(onClick = { viewModel.shuffle(songs, source) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Shuffle", maxLines = 1)
                    }
                    OutlinedButton(onClick = { saveDialog = true }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Save", maxLines = 1)
                    }
                }
            }
        }
        itemsIndexed(listing.tracks, key = { _, t -> t.id }) { index, t ->
            val song = songs.getOrNull(index) ?: return@itemsIndexed
            NcsRow(
                track = t,
                current = playback.song?.key == song.key,
                playing = playback.isPlaying,
                saved = saved(t),
                download = downloads[t.url],
                onClick = { viewModel.play(songs, index, source) },
                onLongClick = { menu.open(song) },
                onSave = { save(listOf(t)) },
                onRetry = {
                    ncs.forget(t)
                    save(listOf(t))
                },
            )
        }
        item(key = "footer") {
            when {
                listing.loading -> LoadingRow()
                listing.error != null -> ErrorRow(listing.error.orEmpty()) { ncs.loadMore() }
                listing.pages > 0 && listing.tracks.isEmpty() ->
                    Credit("Nothing found. Try an artist or song name, or another genre.")
                listing.pages > 0 && !listing.hasMore ->
                    Credit("That's all ${listing.tracks.size} songs.")
            }
        }
        item(key = "credit") {
            Credit(
                "Music by NoCopyrightSounds (ncs.io), streamed and downloaded from NCS itself. " +
                    "Saved songs go to Music › NCS and join your library. Using NCS music in videos? " +
                    "Credit the artist as NCS asks.",
                link = Ncs.SITE + "/usage-policy",
            )
        }
    }

    if (saveDialog) {
        SaveDialog(
            shown = listing.tracks.size,
            filterName = when {
                listing.query.isNotBlank() -> "matching \"${listing.query}\""
                listing.filtered -> "in " + listOfNotNull(listing.genre?.name, listing.mood?.name).joinToString(" · ")
                else -> null
            },
            onDismiss = { saveDialog = false },
            onSaveShown = {
                saveDialog = false
                save(listing.tracks)
            },
            onSaveEverything = { wifiOnly ->
                saveDialog = false
                gate { ncs.saveEverything(libraryPaths, wifiOnly) }
            },
        )
    }
}

@Composable
private fun FilterRow(allLabel: String, filters: List<NcsFilter>, selected: NcsFilter?, onSelect: (NcsFilter?) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "all") {
            FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text(allLabel) })
        }
        items(filters, key = { it.id }) { f ->
            FilterChip(
                selected = selected?.id == f.id,
                onClick = { onSelect(if (selected?.id == f.id) null else f) },
                label = { Text(f.name) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NcsRow(
    track: NcsTrack,
    current: Boolean,
    playing: Boolean,
    saved: Boolean,
    download: Downloads.Info?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onSave: () -> Unit,
    onRetry: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(contentAlignment = Alignment.Center) {
            RemoteImage(
                track.thumbnail,
                track.title,
                Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(10.dp)),
            ) {
                Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
            }
            if (current) PlayingIndicator(playing, color = MaterialTheme.colorScheme.onPrimary)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                track.artists,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val info = listOf(track.genre, track.released).filter { it.isNotBlank() }.joinToString(" • ")
            if (info.isNotEmpty()) {
                Text(info, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
        DownloadState(download, saved, onDownload = onSave, onRetry = onRetry)
    }
}

/** Download button that shows progress, done, or failed (tap to retry). */
@Composable
private fun DownloadState(info: Downloads.Info?, done: Boolean, onDownload: () -> Unit, onRetry: () -> Unit) {
    val status = info?.status ?: Downloads.Status.NONE
    val progress = info?.progress ?: 0f
    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
        when {
            done || status == Downloads.Status.DONE ->
                Icon(Icons.Rounded.DownloadDone, contentDescription = "Saved", tint = MaterialTheme.colorScheme.primary)
            status == Downloads.Status.RUNNING || status == Downloads.Status.QUEUED ->
                if (progress > 0f) {
                    CircularProgressIndicator(progress = { progress }, modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
                } else {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
                }
            status == Downloads.Status.FAILED ->
                IconButton(onClick = onRetry) {
                    Icon(Icons.Rounded.ErrorOutline, contentDescription = "Saving failed, try again", tint = MaterialTheme.colorScheme.error)
                }
            else -> IconButton(onClick = onDownload) { Icon(Icons.Rounded.Download, contentDescription = "Save to phone") }
        }
    }
}

@Composable
private fun SaveAllCard(progress: NcsSaveAll, onStop: () -> Unit, onDismiss: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                when {
                    progress.running -> "Saving every song…"
                    progress.error != null -> "Stopped: ${progress.error}"
                    else -> "Queued ${progress.queued} songs"
                },
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (progress.running) {
                    "${progress.queued} songs queued so far (page ${progress.pagesRead}). They download in the background; see the notification shade."
                } else {
                    "Songs download in the background into Music › NCS. Run it again any time to pick up new releases; songs you have are skipped."
                },
                style = MaterialTheme.typography.bodySmall,
            )
            if (progress.running) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (progress.running) {
                    TextButton(onClick = onStop) { Text("Stop") }
                } else {
                    TextButton(onClick = onDismiss) { Text("OK") }
                }
            }
        }
    }
}

@Composable
private fun SaveDialog(
    shown: Int,
    filterName: String?,
    onDismiss: () -> Unit,
    onSaveShown: () -> Unit,
    onSaveEverything: (wifiOnly: Boolean) -> Unit,
) {
    var everything by rememberSaveable { mutableStateOf(false) }
    var wifiOnly by rememberSaveable { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Headphones, contentDescription = null) },
        title = { Text("Save to your phone") },
        text = {
            Column {
                Text(
                    "Saved songs go to Music › NCS, join your library and play without internet.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                ChoiceRow("The $shown songs shown", !everything) { everything = false }
                ChoiceRow(
                    if (filterName == null) "Every NCS song" else "Every NCS song $filterName",
                    everything,
                ) { everything = true }
                if (everything) {
                    Text(
                        if (filterName == null) {
                            "That's the whole NCS catalogue: thousands of songs and many gigabytes. Songs you already have are skipped."
                        } else {
                            "Goes through every page, not just the songs loaded so far. Songs you already have are skipped."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 48.dp, bottom = 4.dp),
                    )
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = wifiOnly, role = Role.Checkbox) { wifiOnly = !wifiOnly },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = wifiOnly, onCheckedChange = null, modifier = Modifier.padding(12.dp))
                        Text("Only download on Wi-Fi", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (everything) onSaveEverything(wifiOnly) else onSaveShown() }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

/**
 * Downloads to shared storage need the storage permission before Android 10; asks for it and
 * runs [action] once allowed.
 */
@Composable
private fun rememberStorageGate(viewModel: MainViewModel): (() -> Unit) -> Unit {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) pending?.invoke() else viewModel.toast("Saving songs needs access to storage")
        pending = null
    }
    return { action ->
        val needs = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        if (needs) {
            pending = action
            launcher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            action()
        }
    }
}

/** Small print with an optional link. */
@Composable
fun Credit(text: String, link: String? = null) {
    val context = LocalContext.current
    Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (link != null) {
            TextButton(
                onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link))) } },
                contentPadding = PaddingValues(0.dp),
            ) { Text(link.removePrefix("https://")) }
        }
    }
}
