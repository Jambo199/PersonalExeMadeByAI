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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.jambo199.vividmusic.data.Song
import com.jambo199.vividmusic.data.formatDuration
import com.jambo199.vividmusic.online.Downloads
import com.jambo199.vividmusic.online.NcsTrack
import com.jambo199.vividmusic.ui.MainViewModel
import com.jambo199.vividmusic.ui.components.PlayingIndicator
import java.io.File

/** What NCS song lists need from their screen: playback, saved songs and downloads. */
@Stable
class NcsListState(
    val viewModel: MainViewModel,
    val ncs: NcsViewModel,
    private val musicRoot: File,
    private val byPath: Map<String, Song>,
    val downloads: Map<String, Downloads.Info>,
    val playingKey: String?,
    val isPlaying: Boolean,
    /** Saves songs to the phone (asking for storage access first on old Android). */
    val save: (List<NcsTrack>) -> Unit,
    /** Runs [action] once saving is allowed. */
    val whenSavingAllowed: (() -> Unit) -> Unit,
) {
    val libraryPaths: Set<String> get() = byPath.keys

    private fun localPath(t: NcsTrack) = File(musicRoot, t.downloadPath).absolutePath

    /** The saved copy when there is one, so it plays from the phone, else the stream. */
    fun song(t: NcsTrack): Song = byPath[localPath(t)] ?: t.toSong()

    fun saved(t: NcsTrack): Boolean = localPath(t) in byPath || downloads[t.url]?.status == Downloads.Status.DONE
}

@Composable
fun rememberNcsListState(viewModel: MainViewModel, ncs: NcsViewModel): NcsListState {
    val downloads by ncs.downloadInfo.collectAsState()
    val library by viewModel.library.collectAsState()
    val playback by viewModel.playback.collectAsState()
    val gate = rememberStorageGate(viewModel)
    val musicRoot = remember { Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC) }
    val byPath = remember(library) { library.songs.associateBy { it.path } }
    return NcsListState(
        viewModel = viewModel,
        ncs = ncs,
        musicRoot = musicRoot,
        byPath = byPath,
        downloads = downloads,
        playingKey = playback.song?.key,
        isPlaying = playback.isPlaying,
        save = { tracks ->
            gate {
                val started = ncs.save(tracks, byPath.keys)
                viewModel.toast(
                    when (started) {
                        0 -> if (tracks.size == 1) "Already saved or saving" else "These are already saved or saving"
                        1 -> "Saving to Music › NCS"
                        else -> "Saving $started songs to Music › NCS"
                    },
                )
            }
        },
        whenSavingAllowed = gate,
    )
}

/**
 * One row per song. [rankOf] gives the number shown in front (0 for none); [onInfo] opens a
 * song's details.
 */
fun LazyListScope.ncsTrackItems(
    state: NcsListState,
    tracks: List<NcsTrack>,
    songs: List<Song>,
    source: String,
    rankOf: (index: Int, track: NcsTrack) -> Int = { _, _ -> 0 },
    onInfo: (NcsTrack) -> Unit,
) {
    itemsIndexed(tracks, key = { _, t -> t.id }) { index, t ->
        val song = songs.getOrNull(index) ?: return@itemsIndexed
        NcsRow(
            track = t,
            rank = rankOf(index, t),
            current = state.playingKey == song.key,
            playing = state.isPlaying,
            saved = state.saved(t),
            download = state.downloads[t.url],
            onClick = { state.viewModel.play(songs, index, source) },
            onInfo = { onInfo(t) },
            onSave = { state.save(listOf(t)) },
            onRetry = {
                state.ncs.forget(t)
                state.save(listOf(t))
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NcsRow(
    track: NcsTrack,
    rank: Int,
    current: Boolean,
    playing: Boolean,
    saved: Boolean,
    download: Downloads.Info?,
    onClick: () -> Unit,
    onInfo: () -> Unit,
    onSave: () -> Unit,
    onRetry: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onInfo)
            .padding(start = if (rank > 0) 4.dp else 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (rank > 0) {
            Text(
                rank.toString(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = if (rank <= 3) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.width(34.dp),
            )
            Spacer(Modifier.width(4.dp))
        }
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
            val info = listOfNotNull(
                track.genre.ifBlank { null },
                track.year.takeIf { it > 0 }?.toString(),
                track.durationMs.takeIf { it > 0 }?.let(::formatDuration),
            ).joinToString(" • ")
            if (info.isNotEmpty()) {
                Text(info, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
        DownloadState(download, saved, onDownload = onSave, onRetry = onRetry)
        IconButton(onClick = onInfo) {
            Icon(Icons.Rounded.MoreVert, contentDescription = "Song info", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Download button that shows progress, done, or failed (tap to retry). */
@Composable
private fun DownloadState(info: Downloads.Info?, done: Boolean, onDownload: () -> Unit, onRetry: () -> Unit) {
    val status = info?.status ?: Downloads.Status.NONE
    val progress = info?.progress ?: 0f
    Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
        when {
            done || status == Downloads.Status.DONE ->
                Icon(Icons.Rounded.DownloadDone, contentDescription = "Saved", tint = MaterialTheme.colorScheme.primary)
            status == Downloads.Status.RUNNING || status == Downloads.Status.QUEUED ->
                if (progress > 0f) {
                    CircularProgressIndicator(progress = { progress }, modifier = Modifier.size(22.dp), strokeWidth = 3.dp)
                } else {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 3.dp)
                }
            status == Downloads.Status.FAILED ->
                IconButton(onClick = onRetry) {
                    Icon(Icons.Rounded.ErrorOutline, contentDescription = "Saving failed, try again", tint = MaterialTheme.colorScheme.error)
                }
            else -> IconButton(onClick = onDownload) { Icon(Icons.Rounded.Download, contentDescription = "Save to phone") }
        }
    }
}

/**
 * Downloads to shared storage need the storage permission before Android 10; asks for it and
 * runs the action once allowed.
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
