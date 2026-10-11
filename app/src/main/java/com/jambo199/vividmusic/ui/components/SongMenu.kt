package com.jambo199.vividmusic.ui.components

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.media.MediaMetadataRetriever
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jambo199.vividmusic.data.Playlist
import com.jambo199.vividmusic.data.Song
import com.jambo199.vividmusic.data.SongSource
import com.jambo199.vividmusic.data.TagEditor
import com.jambo199.vividmusic.data.TagEdits
import com.jambo199.vividmusic.data.formatDuration
import com.jambo199.vividmusic.data.formatSize
import com.jambo199.vividmusic.data.pluralize
import com.jambo199.vividmusic.online.NcsSeen
import com.jambo199.vividmusic.online.NcsTrack
import com.jambo199.vividmusic.ui.DeleteRequest
import com.jambo199.vividmusic.ui.LocalNav
import com.jambo199.vividmusic.ui.MainViewModel
import com.jambo199.vividmusic.ui.SongMenuRequest
import com.jambo199.vividmusic.ui.SongMenuState
import com.jambo199.vividmusic.ui.online.NcsInfoSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

/** Hosts the song options sheet and the dialogs it opens. Place once near the root. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongMenuHost(state: SongMenuState, viewModel: MainViewModel) {
    val nav = LocalNav.current
    val context = LocalContext.current
    val userData by viewModel.userData.collectAsState()
    val library by viewModel.library.collectAsState()

    var details by remember { mutableStateOf<Song?>(null) }
    var editing by remember { mutableStateOf<Song?>(null) }
    var ncsInfo by remember { mutableStateOf<NcsTrack?>(null) }
    // Android's "allow changing this file?" dialog for the tag editor.
    var askTagPermission: (IntentSender) -> Unit = {}
    val tagPermission = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        viewModel.onTagPermission(result.resultCode == Activity.RESULT_OK) { askTagPermission(it) }
    }
    askTagPermission = { sender -> tagPermission.launch(IntentSenderRequest.Builder(sender).build()) }
    var confirmDelete by remember { mutableStateOf<List<Song>?>(null) }
    // Declared before the launcher so the launcher's callback can report follow-up prompts.
    var launchDelete: (DeleteRequest) -> Unit = {}
    val deleteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        viewModel.onDeleteConfirmed(result.resultCode == Activity.RESULT_OK) { launchDelete(it) }
    }
    launchDelete = { result ->
        when (result) {
            is DeleteRequest.NeedsConfirmation ->
                deleteLauncher.launch(IntentSenderRequest.Builder(result.intentSender).build())
            is DeleteRequest.Done -> viewModel.toast(
                when {
                    result.failed == 0 -> if (result.deleted == 1) "Deleted" else "${result.deleted} songs deleted"
                    result.deleted == 0 -> "Couldn't delete"
                    else -> "Deleted ${result.deleted}, ${result.failed} failed"
                },
            )
        }
    }

    val request = state.request
    if (request != null) {
        val songs = request.songs
        val single = songs.singleOrNull()
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { state.close() }, sheetState = sheetState) {
            Column(Modifier.navigationBarsPadding().padding(bottom = 8.dp)) {
                SheetHeader(request)
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    fun act(block: () -> Unit) {
                        state.close()
                        block()
                    }
                    MenuRow(Icons.Rounded.SkipNext, "Play next") { act { viewModel.playNext(songs) } }
                    MenuRow(Icons.AutoMirrored.Rounded.QueueMusic, "Add to queue") { act { viewModel.addToQueue(songs) } }
                    // Playlists, sharing files, details and deleting only make sense for files on the phone.
                    val onPhone = songs.none { it.isOnline }
                    if (onPhone) {
                        MenuRow(Icons.AutoMirrored.Rounded.PlaylistAdd, "Add to playlist…") { act { state.addToPlaylist(songs) } }
                    }
                    if (single != null) {
                        val fav = single.key in userData.favoriteSet
                        if (!single.isOnline) {
                            MenuRow(
                                if (fav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                if (fav) "Remove from favourites" else "Add to favourites",
                            ) { act { viewModel.toggleFavorite(single) } }
                        }
                        if (library.albumsById.containsKey(single.albumId)) {
                            MenuRow(Icons.Rounded.Album, "Go to album") { act { nav.fromPlayer { album(single.albumId) } } }
                        }
                        val artists = library.artistNamesOf(single).filter { library.artistsByName.containsKey(it) }
                        artists.forEach { name ->
                            MenuRow(Icons.Rounded.Person, if (artists.size == 1) "Go to artist" else "Go to $name") {
                                act { nav.fromPlayer { artist(name) } }
                            }
                        }
                        if (library.folders.containsKey(single.folder)) {
                            MenuRow(Icons.Rounded.Folder, "Go to folder") { act { nav.fromPlayer { folder(single.folder) } } }
                        }
                    }
                    val pid = request.playlistId
                    if (pid != null && pid > 0 && request.playlistIndex >= 0) {
                        MenuRow(Icons.Rounded.RemoveCircleOutline, "Remove from this playlist") {
                            act { viewModel.removeFromPlaylist(pid, request.playlistIndex) }
                        }
                    }
                    if (onPhone) {
                        MenuRow(Icons.Rounded.Share, "Share") { act { shareSongs(context, songs) } }
                        if (single != null) {
                            MenuRow(Icons.Rounded.Info, "Details") { act { details = single } }
                            if (TagEditor.canEdit(single)) {
                                MenuRow(Icons.Rounded.Edit, "Edit tags") { act { editing = single } }
                            }
                        }
                    } else if (single != null) {
                        if (single.key.startsWith(Song.NCS_PREFIX)) {
                            MenuRow(Icons.Rounded.Info, "Song info") { act { ncsInfo = NcsSeen[single.key] ?: NcsTrack.fromSong(single) } }
                        }
                        MenuRow(Icons.Rounded.Share, "Share link") { act { shareLink(context, single) } }
                    }
                    val deletable = songs.filter { it.source == SongSource.MEDIA_STORE || it.source == SongSource.FOLDER }
                    if (deletable.isNotEmpty()) {
                        MenuRow(Icons.Rounded.Delete, "Delete from device", destructive = true) {
                            act { confirmDelete = deletable }
                        }
                    }
                }
            }
        }
    }

    state.playlistRequest?.let { songs ->
        AddToPlaylistDialog(
            playlists = userData.playlists,
            onDismiss = { state.playlistRequest = null },
            onPick = { p ->
                viewModel.addToPlaylist(p, songs)
                state.playlistRequest = null
            },
            onCreate = { name ->
                viewModel.createPlaylist(name, songs)
                state.playlistRequest = null
            },
        )
    }

    details?.let { song -> SongDetailsDialog(song) { details = null } }

    ncsInfo?.let { track -> NcsInfoSheet(track, viewModel) { ncsInfo = null } }

    editing?.let { song ->
        EditTagsDialog(
            song = song,
            onDismiss = { editing = null },
            onSave = { edits ->
                editing = null
                viewModel.editTags(song, edits) { askTagPermission(it) }
            },
        )
    }

    confirmDelete?.let { songs ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(if (songs.size == 1) "Delete this song?" else "Delete ${songs.size} songs?") },
            text = {
                Text(
                    if (songs.size == 1) "\"${songs[0].title}\" will be permanently deleted from your phone."
                    else "These files will be permanently deleted from your phone.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = null
                    viewModel.deleteSongs(songs) { launchDelete(it) }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SheetHeader(request: SongMenuRequest) {
    val songs = request.songs
    val first = songs.first()
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(first, Modifier.size(56.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (songs.size == 1) first.title else pluralize(songs.size, "song"),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (songs.size == 1) "${first.displayArtist} • ${first.displayAlbum}" else formatDuration(songs.sumOf { it.durationMs }),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun MenuRow(icon: ImageVector, label: String, destructive: Boolean = false, onClick: () -> Unit) {
    val color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    ListItem(
        headlineContent = { Text(label, color = color) },
        leadingContent = { Icon(icon, contentDescription = null, tint = if (destructive) color else MaterialTheme.colorScheme.onSurfaceVariant) },
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
    )
}

fun shareSongs(context: android.content.Context, songs: List<Song>) {
    if (songs.isEmpty()) return
    val intent = if (songs.size == 1) {
        Intent(Intent.ACTION_SEND)
            .setType(songs[0].mimeType.ifBlank { "audio/*" })
            .putExtra(Intent.EXTRA_STREAM, songs[0].uri)
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE)
            .setType("audio/*")
            .putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(songs.map { it.uri }))
    }
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        context.startActivity(Intent.createChooser(intent, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
    }
}

@Composable
fun AddToPlaylistDialog(
    playlists: List<Playlist>,
    onDismiss: () -> Unit,
    onPick: (Playlist) -> Unit,
    onCreate: (String) -> Unit,
) {
    var creating by rememberSaveable { mutableStateOf(playlists.isEmpty()) }
    var name by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (creating) "New playlist" else "Add to playlist") },
        text = {
            if (creating) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Playlist name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    item {
                        ListItem(
                            headlineContent = { Text("New playlist…", fontWeight = FontWeight.SemiBold) },
                            leadingContent = { Icon(Icons.Rounded.Add, contentDescription = null) },
                            colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                            modifier = Modifier.clickable { creating = true },
                        )
                    }
                    items(playlists, key = { it.id }) { p ->
                        ListItem(
                            headlineContent = { Text(p.name) },
                            supportingContent = { Text(pluralize(p.songKeys.size, "song")) },
                            colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                            modifier = Modifier.clickable { onPick(p) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (creating) {
                TextButton(onClick = { onCreate(name) }, enabled = name.isNotBlank()) { Text("Create") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun SongDetailsDialog(song: Song, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val tech by produceState<Map<String, String>>(emptyMap(), song.key) {
        value = withContext(Dispatchers.IO) { readTechnicalInfo(context, song) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Details") },
        text = {
            SelectionContainer {
                Column(
                    Modifier
                        .heightIn(max = 460.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    val rows = buildList {
                        add("Title" to song.title)
                        add("Artist" to song.displayArtist)
                        add("Album" to song.displayAlbum)
                        if (song.albumArtist.isNotBlank()) add("Album artist" to song.albumArtist)
                        if (song.track > 0) add("Track" to (if (song.disc > 0) "Disc ${song.disc}, track ${song.track}" else song.track.toString()))
                        if (song.year > 0) add("Year" to song.year.toString())
                        if (song.genre.isNotBlank()) add("Genre" to song.genre)
                        add("Length" to formatDuration(song.durationMs))
                        tech["format"]?.let { add("Format" to it) }
                        tech["bitrate"]?.let { add("Bitrate" to it) }
                        tech["sample"]?.let { add("Sample rate" to it) }
                        if (song.sizeBytes > 0) add("Size" to formatSize(song.sizeBytes))
                        add("File" to song.fileName)
                        add("Location" to song.path)
                        if (song.dateAddedSec > 0) {
                            add("Added" to DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(song.dateAddedSec * 1000)))
                        }
                    }
                    rows.forEach { (label, value) ->
                        Column {
                            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            Text(value, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

/** Reads codec details that the media index doesn't store. */
fun readTechnicalInfo(context: android.content.Context, song: Song): Map<String, String> {
    val out = HashMap<String, String>()
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(context, song.uri)
        val mime = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE) ?: song.mimeType
        val ext = song.extension
        out["format"] = listOfNotNull(ext.ifBlank { null }, mime.ifBlank { null }?.let { "($it)" }).joinToString(" ")
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull()?.let {
            out["bitrate"] = "${it / 1000} kbps"
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val rate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.toIntOrNull()
            val bits = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITS_PER_SAMPLE)?.toIntOrNull()
            if (rate != null && rate > 0) {
                out["sample"] = "%.1f kHz".format(rate / 1000f) + if (bits != null && bits > 0) " · $bits-bit" else ""
            }
        }
    } catch (_: Exception) {
    } finally {
        try {
            retriever.release()
        } catch (_: Exception) {
        }
    }
    return out
}

/** Short quality badge like "FLAC · 44.1 kHz" for the player screen. */
fun qualityBadge(info: Map<String, String>, song: Song): String? {
    val parts = ArrayList<String>()
    if (song.extension.isNotBlank()) parts += song.extension
    info["sample"]?.let { parts += it }
    if (info["sample"] == null) info["bitrate"]?.let { parts += it }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

/** Shares where an online song can be found. */
private fun shareLink(context: Context, song: Song) {
    val text = "${song.title} – ${song.displayArtist}\n${song.path.ifBlank { song.uri.toString() }}"
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, "Share"))
}

/** Title, artist, album and the rest, saved into the file itself. */
@Composable
private fun EditTagsDialog(song: Song, onDismiss: () -> Unit, onSave: (TagEdits) -> Unit) {
    val start = remember(song.key) { TagEdits.of(song) }
    var title by rememberSaveable { mutableStateOf(start.title) }
    var artist by rememberSaveable { mutableStateOf(start.artist) }
    var album by rememberSaveable { mutableStateOf(start.album) }
    var albumArtist by rememberSaveable { mutableStateOf(start.albumArtist) }
    var genre by rememberSaveable { mutableStateOf(start.genre) }
    var year by rememberSaveable { mutableStateOf(start.year) }
    var track by rememberSaveable { mutableStateOf(start.track) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit tags") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(song.fileName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                onClick = { onSave(TagEdits(title, artist, album, albumArtist, genre, year, track)) },
                enabled = title.isNotBlank(),
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
