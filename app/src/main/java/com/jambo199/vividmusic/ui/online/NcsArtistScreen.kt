package com.jambo199.vividmusic.ui.online

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jambo199.vividmusic.online.Load
import com.jambo199.vividmusic.online.Ncs
import com.jambo199.vividmusic.online.NcsArtist
import com.jambo199.vividmusic.online.NcsTrack
import com.jambo199.vividmusic.ui.LocalNav
import com.jambo199.vividmusic.ui.MainViewModel
import com.jambo199.vividmusic.ui.components.LocalBottomPadding

/** Everything an artist has released on NCS, most popular first. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NcsArtistScreen(viewModel: MainViewModel, path: String) {
    val ncs = ncsViewModel()
    val nav = LocalNav.current
    val flow = remember(path) { ncs.artist(path) }
    val load by flow.collectAsState()
    DisposableEffect(Unit) {
        ncs.watchDownloads()
        onDispose { ncs.stopWatchingDownloads() }
    }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text((load as? Load.Ready)?.data?.name.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = {
                IconButton(onClick = { nav.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
            },
        )
        when (val l = load) {
            Load.Loading -> LoadingRow()
            is Load.Failed -> ErrorRow(l.message.ifBlank { "Couldn't load this artist" }) { ncs.artist(path, retry = true) }
            is Load.Ready -> ArtistContent(viewModel, ncs, l.data)
        }
    }
}

@Composable
private fun ArtistContent(viewModel: MainViewModel, ncs: NcsViewModel, artist: NcsArtist) {
    val state = rememberNcsListState(viewModel, ncs)
    var info by remember { mutableStateOf<NcsTrack?>(null) }
    val songs = remember(artist, state.libraryPaths) { artist.tracks.map { state.song(it) } }
    val source = "NCS · ${artist.name}"

    LazyColumn(contentPadding = PaddingValues(bottom = LocalBottomPadding.current + 16.dp)) {
        item(key = "header") {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                RemoteImage(
                    artist.image,
                    artist.name,
                    Modifier
                        .size(112.dp)
                        .clip(CircleShape),
                ) {
                    Icon(Icons.Rounded.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(48.dp))
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(artist.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (artist.tags.isNotBlank()) {
                        Text(artist.tags, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, maxLines = 2)
                    }
                    Text(
                        if (artist.tracks.size == 1) "1 song on NCS" else "${artist.tracks.size} songs on NCS",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (songs.isNotEmpty()) {
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
                        Text("Play", maxLines = 1)
                    }
                    FilledTonalButton(onClick = { viewModel.shuffle(songs, source) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Shuffle", maxLines = 1)
                    }
                    OutlinedButton(onClick = { state.save(artist.tracks) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Save all", maxLines = 1)
                    }
                }
            }
            item(key = "title") { SectionTitle("Popular tracks", subtitle = artist.name) }
        } else {
            item(key = "empty") { Credit("NCS doesn't have any songs by ${artist.name} right now.") }
        }
        ncsTrackItems(
            state = state,
            tracks = artist.tracks,
            songs = songs,
            source = source,
            rankOf = { index, _ -> index + 1 },
            onInfo = { info = it },
        )
        item(key = "credit") {
            Credit("Releases by ${artist.name} on NoCopyrightSounds, most popular first.", link = Ncs.SITE + artist.path)
        }
    }

    info?.let { track -> NcsInfoSheet(track, viewModel, onDismiss = { info = null }) }
}
