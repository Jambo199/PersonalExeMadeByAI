package com.jambo199.vividmusic.ui.online

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.LibraryAddCheck
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.jambo199.vividmusic.data.Song
import com.jambo199.vividmusic.data.formatDuration
import com.jambo199.vividmusic.data.formatTotalDuration
import com.jambo199.vividmusic.online.ArchiveAlbum
import com.jambo199.vividmusic.online.ArchiveTrack
import com.jambo199.vividmusic.online.Downloads
import com.jambo199.vividmusic.online.Episode
import com.jambo199.vividmusic.online.InternetArchive
import com.jambo199.vividmusic.online.Load
import com.jambo199.vividmusic.online.PodcastFeed
import com.jambo199.vividmusic.ui.LocalNav
import com.jambo199.vividmusic.ui.MainViewModel
import com.jambo199.vividmusic.ui.components.LocalBottomPadding
import com.jambo199.vividmusic.ui.components.PlayingIndicator
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OnlineTopBar(title: String, actions: @Composable () -> Unit = {}) {
    val nav = LocalNav.current
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            IconButton(onClick = { nav.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
        },
        actions = { actions() },
    )
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
        if (granted) pending?.invoke() else viewModel.toast("Downloading needs access to storage")
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

/** Download button that shows progress, done, or failed (tap to retry). */
@Composable
private fun DownloadState(info: Downloads.Info?, done: Boolean, onDownload: () -> Unit, onRetry: () -> Unit) {
    val status = info?.status ?: Downloads.Status.NONE
    val progress = info?.progress ?: 0f
    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
        when {
            done || status == Downloads.Status.DONE ->
                Icon(Icons.Rounded.DownloadDone, contentDescription = "Downloaded", tint = MaterialTheme.colorScheme.primary)
            status == Downloads.Status.RUNNING || status == Downloads.Status.QUEUED ->
                if (progress > 0f) {
                    CircularProgressIndicator(progress = { progress }, modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
                } else {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
                }
            status == Downloads.Status.FAILED ->
                IconButton(onClick = onRetry) {
                    Icon(Icons.Rounded.ErrorOutline, contentDescription = "Download failed, try again", tint = MaterialTheme.colorScheme.error)
                }
            else -> IconButton(onClick = onDownload) { Icon(Icons.Rounded.Download, contentDescription = "Download") }
        }
    }
}

// region free music album

@Composable
fun ArchiveAlbumScreen(viewModel: MainViewModel, id: String) {
    val online = onlineViewModel()
    val albumFlow = remember(id) { online.album(id) }
    val load by albumFlow.collectAsState()
    DisposableEffect(Unit) {
        online.watchDownloads()
        onDispose { online.stopWatchingDownloads() }
    }
    Column(Modifier.fillMaxSize()) {
        OnlineTopBar((load as? Load.Ready)?.data?.item?.title ?: "")
        when (val l = load) {
            Load.Loading -> LoadingRow()
            is Load.Failed -> ErrorRow(l.message) { online.album(id) }
            is Load.Ready -> AlbumContent(viewModel, online, l.data)
        }
    }
}

@Composable
private fun AlbumContent(viewModel: MainViewModel, online: OnlineViewModel, album: ArchiveAlbum) {
    val context = LocalContext.current
    val library by viewModel.library.collectAsState()
    val playback by viewModel.playback.collectAsState()
    val data by online.data.collectAsState()
    val downloads by online.downloadInfo.collectAsState()
    val bottom = LocalBottomPadding.current
    val gate = rememberStorageGate(viewModel)
    var expanded by rememberSaveable { mutableStateOf(false) }

    // Tracks already downloaded play from the phone instead of the internet.
    val musicRoot = remember { Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).absolutePath }
    val local: Map<String, Song> = remember(library, album) {
        val byPath = library.songs.associateBy { it.path }
        album.tracks.mapNotNull { t -> byPath["$musicRoot/${album.downloadPath(t)}"]?.let { t.url to it } }.toMap()
    }
    val songs = remember(album, local) { album.tracks.map { t -> local[t.url] ?: album.toSong(t) } }
    val source = "Free music · ${album.item.title}"
    val missing = album.tracks.filter { it.url !in local && downloads[it.url]?.status != Downloads.Status.DONE }

    fun download(tracks: List<ArchiveTrack>) = gate {
        val started = online.downloadTracks(album, tracks)
        viewModel.toast(
            when (started) {
                0 -> "Already downloading"
                1 -> "Downloading 1 song to Music › Vivid Music"
                else -> "Downloading $started songs to Music › Vivid Music"
            },
        )
    }

    LazyColumn(contentPadding = PaddingValues(bottom = bottom + 16.dp)) {
        item(key = "header") {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                RemoteImage(
                    album.item.cover,
                    album.item.title,
                    Modifier
                        .size(120.dp)
                        .clip(RoundedCornerShape(16.dp)),
                )
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(album.item.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    if (album.item.creator.isNotBlank()) {
                        Text(album.item.creator, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(
                        listOf(album.item.date.take(10), "${album.tracks.size} tracks", formatTotalDuration(album.tracks.sumOf { it.durationMs }))
                            .filter { it.isNotBlank() }.joinToString(" • "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    InternetArchive.licenseName(album.license)?.let { name ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier
                                .padding(top = 6.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(album.license))) }
                                },
                        ) {
                            Text(name, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                        }
                    }
                }
            }
        }
        if (album.tracks.isEmpty()) {
            item(key = "empty") { Credit("This item has no playable audio files.") }
        } else {
            item(key = "buttons") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(onClick = { viewModel.playInOrder(songs, source) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Play")
                    }
                    FilledTonalButton(onClick = { viewModel.shuffle(songs, source) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Shuffle")
                    }
                    OutlinedButton(onClick = { download(missing) }, enabled = missing.isNotEmpty(), modifier = Modifier.weight(1f)) {
                        Icon(
                            if (missing.isEmpty()) Icons.Rounded.LibraryAddCheck else Icons.Rounded.Download,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(if (missing.isEmpty()) "Saved" else "Save all", maxLines = 1)
                    }
                }
            }
        }
        itemsIndexed(album.tracks, key = { _, t -> t.url }) { index, t ->
            val song = songs[index]
            val current = playback.song?.key == song.key
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.play(songs, index, source) }
                    .padding(start = 8.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(40.dp), contentAlignment = Alignment.Center) {
                    if (current) {
                        PlayingIndicator(playback.isPlaying)
                    } else {
                        Text(
                            if (t.track > 0) t.track.toString() else (index + 1).toString(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        t.title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val artist = t.artist.takeIf { it.isNotBlank() && it != album.item.creator }
                    val line = listOfNotNull(artist, t.durationMs.takeIf { it > 0 }?.let(::formatDuration)).joinToString(" • ")
                    if (line.isNotBlank()) {
                        Text(line, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
                DownloadState(
                    info = downloads[t.url],
                    done = t.url in local,
                    onDownload = { download(listOf(t)) },
                    onRetry = {
                        online.removeDownload(t.url)
                        download(listOf(t))
                    },
                )
            }
        }
        if (album.description.isNotBlank()) {
            item(key = "about") {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Text("About", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        album.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (expanded) Int.MAX_VALUE else 5,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable { expanded = !expanded },
                    )
                }
            }
        }
        item(key = "credit") { Credit("From the Internet Archive (archive.org/details/${album.item.id}).") }
    }
}

// endregion

// region podcast

@Composable
fun PodcastScreen(viewModel: MainViewModel, feedUrl: String) {
    val online = onlineViewModel()
    val feedFlow = remember(feedUrl) { online.feed(feedUrl) }
    val load by feedFlow.collectAsState()
    DisposableEffect(Unit) {
        online.watchDownloads()
        onDispose { online.stopWatchingDownloads() }
    }
    Column(Modifier.fillMaxSize()) {
        OnlineTopBar((load as? Load.Ready)?.data?.podcast?.title ?: "") {
            IconButton(onClick = { online.feed(feedUrl, refresh = true) }) { Icon(Icons.Rounded.Refresh, contentDescription = "Check for new episodes") }
        }
        when (val l = load) {
            Load.Loading -> LoadingRow()
            is Load.Failed -> ErrorRow(l.message) { online.feed(feedUrl, refresh = true) }
            is Load.Ready -> PodcastContent(viewModel, online, l.data)
        }
    }
}

@Composable
private fun PodcastContent(viewModel: MainViewModel, online: OnlineViewModel, feed: PodcastFeed) {
    val data by online.data.collectAsState()
    val downloads by online.downloadInfo.collectAsState()
    val positions by viewModel.positions.collectAsState()
    val playback by viewModel.playback.collectAsState()
    val bottom = LocalBottomPadding.current
    val gate = rememberStorageGate(viewModel)
    val podcast = feed.podcast
    val subscribed = podcast.feedUrl in data.podcastFeeds
    var expanded by rememberSaveable { mutableStateOf(false) }
    val dates = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }

    fun songFor(e: Episode): Song {
        val info = downloads[e.url]
        return e.toSong(podcast, info?.localUri?.takeIf { info.status == Downloads.Status.DONE })
    }

    LazyColumn(contentPadding = PaddingValues(bottom = bottom + 16.dp)) {
        item(key = "header") {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RemoteImage(
                        podcast.artwork,
                        podcast.title,
                        Modifier
                            .size(120.dp)
                            .clip(RoundedCornerShape(16.dp)),
                    ) {
                        Icon(Icons.Rounded.Podcasts, contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp))
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(podcast.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        if (podcast.author.isNotBlank()) {
                            Text(podcast.author, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text("${feed.episodes.size} episodes", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(6.dp))
                        if (subscribed) {
                            OutlinedButton(onClick = { online.toggleSubscription(podcast) }) { Text("Subscribed") }
                        } else {
                            Button(onClick = { online.toggleSubscription(podcast) }) { Text("Subscribe") }
                        }
                    }
                }
                if (feed.description.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        feed.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (expanded) Int.MAX_VALUE else 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable { expanded = !expanded },
                    )
                }
            }
        }
        itemsIndexed(feed.episodes, key = { _, e -> e.url }) { _, e ->
            val song = songFor(e)
            val current = playback.song?.key == e.key
            val position = positions[e.key] ?: 0L
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.play(listOf(song), 0, "Podcast · ${podcast.title}") }
                    .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    val meta = listOfNotNull(
                        e.published.takeIf { it > 0 }?.let { dates.format(Date(it)) },
                        e.durationMs.takeIf { it > 0 }?.let(::formatTotalDuration),
                        when {
                            position > 0 && e.durationMs > 0 -> "${formatTotalDuration(e.durationMs - position)} left"
                            position > 0 -> "Started"
                            else -> null
                        },
                    ).joinToString(" • ")
                    Text(meta, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        e.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (e.description.isNotBlank()) {
                        Text(
                            e.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (position > 0 && e.durationMs > 0) {
                        LinearProgressIndicator(
                            progress = { (position.toFloat() / e.durationMs).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .padding(top = 6.dp)
                                .fillMaxWidth(0.6f),
                            drawStopIndicator = {},
                        )
                    }
                }
                if (current) {
                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { PlayingIndicator(playback.isPlaying) }
                } else {
                    DownloadState(
                        info = downloads[e.url],
                        done = false,
                        onDownload = {
                            gate {
                                if (online.downloadEpisode(podcast, e)) viewModel.toast("Downloading to Podcasts › Vivid Music")
                                else viewModel.toast("Couldn't start the download")
                            }
                        },
                        onRetry = {
                            online.removeDownload(e.url)
                            gate { online.downloadEpisode(podcast, e) }
                        },
                    )
                }
            }
        }
        if (feed.episodes.isEmpty()) {
            item(key = "none") { Credit("This feed has no audio episodes.") }
        }
    }
}

// endregion
