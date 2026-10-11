package com.jambo199.vividmusic.ui.online

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jambo199.vividmusic.online.Ncs
import com.jambo199.vividmusic.online.NcsArtistRef
import com.jambo199.vividmusic.online.NcsFilter
import com.jambo199.vividmusic.online.NcsTrack
import com.jambo199.vividmusic.ui.LocalNav
import com.jambo199.vividmusic.ui.MainViewModel
import com.jambo199.vividmusic.ui.components.LocalBottomPadding

/**
 * NoCopyrightSounds: every NCS release, newest or most played first. Play straight from NCS, or
 * save songs into Music › NCS so they join the library and play offline.
 */
@Composable
fun NcsScreen(viewModel: MainViewModel) {
    val ncs = ncsViewModel()
    val nav = LocalNav.current
    val listing by ncs.listing.collectAsState()
    val genres by ncs.genres.collectAsState()
    val moods by ncs.moods.collectAsState()
    val saveAll by ncs.saveAll.collectAsState()
    val showArtists by ncs.showArtists.collectAsState()
    val artistList by ncs.artistList.collectAsState()
    val state = rememberNcsListState(viewModel, ncs)
    val bottom = LocalBottomPadding.current
    var saveDialog by rememberSaveable { mutableStateOf(false) }
    var info by remember { mutableStateOf<NcsTrack?>(null) }

    LaunchedEffect(Unit) { ncs.start() }
    DisposableEffect(Unit) {
        ncs.watchDownloads()
        onDispose { ncs.stopWatchingDownloads() }
    }

    val songs = remember(listing.tracks, state.libraryPaths) { listing.tracks.map { state.song(it) } }
    val source = when {
        listing.query.isNotBlank() -> "NCS · \"${listing.query}\""
        listing.popular -> listOfNotNull("NCS · Most popular", listing.genre?.name, listing.mood?.name).joinToString(" · ")
        else -> listOfNotNull("NCS", listing.genre?.name, listing.mood?.name).joinToString(" · ")
    }
    // Searching for an artist shows them first, so all their songs are a tap away.
    val artists: List<NcsArtistRef> = remember(listing.tracks, listing.query) {
        val q = listing.query.trim().lowercase()
        if (q.length < 2) emptyList() else {
            listing.tracks.flatMap { it.artistLinks }.distinctBy { it.path }.filter { it.name.lowercase().contains(q) }
        }
    }

    val listState = rememberLazyListState()
    val nearEnd by remember {
        derivedStateOf {
            val layout = listState.layoutInfo
            val last = layout.visibleItemsInfo.lastOrNull()?.index ?: 0
            layout.totalItemsCount > 0 && last >= layout.totalItemsCount - 6
        }
    }
    LaunchedEffect(nearEnd, listing.tracks.size, listing.loading, showArtists) {
        if (!showArtists && nearEnd && listing.pages > 0 && listing.error == null) ncs.loadMore()
    }
    LaunchedEffect(nearEnd, artistList.artists.size, artistList.loading, showArtists) {
        if (showArtists && nearEnd && artistList.pages > 0 && artistList.error == null) ncs.loadMoreArtists()
    }

    LazyColumn(state = listState, contentPadding = PaddingValues(bottom = bottom + 16.dp)) {
        item(key = "search") {
            // A fresh field whenever the page switches between songs and artists.
            key(showArtists) {
                if (showArtists) {
                    OnlineSearchField("Search NCS artists", artistList.query) { ncs.searchArtists(it) }
                } else {
                    OnlineSearchField("Search NCS songs and artists", listing.query) { ncs.search(it) }
                }
            }
        }
        item(key = "sort") {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { SortChip("Newest", Icons.Rounded.NewReleases, !showArtists && !listing.popular) { ncs.selectSort(NcsSort.NEWEST) } }
                item { SortChip("Most popular", Icons.Rounded.Whatshot, !showArtists && listing.popular) { ncs.selectSort(NcsSort.POPULAR) } }
                item { SortChip("Artists", Icons.Rounded.Groups, showArtists) { ncs.showArtistList() } }
            }
        }
        if (showArtists) {
            artistItems(artistList, ncs, onOpen = { nav.ncsArtist(it.path) })
            return@LazyColumn
        }
        if (genres.isNotEmpty()) {
            item(key = "genres") { FilterRow("All genres", genres, listing.genre) { ncs.selectGenre(it) } }
        }
        if (moods.isNotEmpty()) {
            item(key = "moods") { FilterRow("Any mood", moods, listing.mood) { ncs.selectMood(it) } }
        }
        if (artists.isNotEmpty()) {
            item(key = "artists") {
                Column {
                    SectionTitle("Artists")
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(artists, key = { it.path }) { a ->
                            AssistChip(
                                onClick = { nav.ncsArtist(a.path) },
                                label = { Text(a.name) },
                                leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            )
                        }
                    }
                }
            }
        }
        saveAll?.let { progress ->
            item(key = "save-all") {
                SaveAllCard(progress, onStop = { ncs.stopSaveEverything() }, onDismiss = { ncs.dismissSaveEverything() })
            }
        }
        item(key = "title") {
            val narrowed = listOfNotNull(listing.genre?.name, listing.mood?.name).joinToString(" · ")
            SectionTitle(
                title = when {
                    listing.query.isNotBlank() -> "Songs matching \"${listing.query}\""
                    listing.popular -> if (narrowed.isEmpty()) "Most popular" else "Most popular · $narrowed"
                    listing.filtered -> narrowed
                    else -> "Latest releases"
                },
                subtitle = if (listing.popular) "NoCopyrightSounds · most played first" else "NoCopyrightSounds",
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
        ncsTrackItems(
            state = state,
            tracks = listing.tracks,
            songs = songs,
            source = source,
            rankOf = { _, t -> if (listing.popular) t.rank else 0 },
            onInfo = { info = it },
        )
        item(key = "footer") {
            when {
                listing.loading -> Column {
                    LoadingRow()
                    if (listing.popular) {
                        Credit("Finding the most played NCS songs…")
                    }
                }
                listing.error != null -> ErrorRow(listing.error.orEmpty()) { ncs.loadMore() }
                listing.pages > 0 && listing.tracks.isEmpty() -> {
                    val narrowed = listOfNotNull(listing.genre?.name, listing.mood?.name).joinToString(" · ")
                    Column {
                        Credit(
                            when {
                                narrowed.isNotEmpty() && listing.popular -> "None of the most played songs are $narrowed."
                                narrowed.isNotEmpty() -> "Nothing found in $narrowed."
                                else -> "Nothing found. Try an artist or song name."
                            },
                        )
                        if (narrowed.isNotEmpty()) {
                            FilledTonalButton(onClick = { ncs.clearFilters() }, modifier = Modifier.padding(horizontal = 20.dp)) {
                                Text("Show all genres and moods")
                            }
                        }
                    }
                }
                listing.pages > 0 && !listing.hasMore ->
                    Credit(if (listing.popular) "That's the top ${listing.tracks.size} most played." else "That's all ${listing.tracks.size} songs.")
            }
        }
        item(key = "credit") {
            Credit(
                "Music by NoCopyrightSounds (ncs.io), streamed and downloaded from NCS itself. " +
                    (if (listing.popular) "Most played is ranked by plays on Deezer. " else "") +
                    "Saved songs go to Music › NCS and join your library. Using NCS music in videos? " +
                    "Credit the artist as NCS asks (tap ⋮ on a song for the text).",
                link = Ncs.SITE + "/usage-policy",
            )
        }
    }

    info?.let { track -> NcsInfoSheet(track, viewModel, onDismiss = { info = null }) }

    if (saveDialog) {
        val narrowed = listOfNotNull(listing.genre?.name, listing.mood?.name).joinToString(" · ")
        SaveDialog(
            shown = listing.tracks.size,
            everythingLabel = when {
                listing.popular -> if (narrowed.isEmpty()) "The whole most-played chart" else "The whole most-played chart in $narrowed"
                listing.query.isNotBlank() -> "Every NCS song matching \"${listing.query}\""
                narrowed.isNotEmpty() -> "Every NCS song in $narrowed"
                else -> "Every NCS song"
            },
            everythingNote = when {
                listing.popular -> "The top songs ranked by plays (up to 300). Songs you already have are skipped."
                listing.filtered -> "Goes through every page, not just the songs loaded so far. Songs you already have are skipped."
                else -> "That's the whole NCS catalogue: thousands of songs and many gigabytes. Songs you already have are skipped."
            },
            onDismiss = { saveDialog = false },
            onSaveShown = {
                saveDialog = false
                state.save(listing.tracks)
            },
            onSaveEverything = { wifiOnly ->
                saveDialog = false
                state.whenSavingAllowed { ncs.saveEverything(state.libraryPaths, wifiOnly) }
            },
        )
    }
}

/** The artist list: how it's ordered, then one row per artist. */
private fun LazyListScope.artistItems(list: NcsArtistListing, ncs: NcsViewModel, onOpen: (NcsArtistEntry) -> Unit) {
    item(key = "artist-sort") {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(NcsArtistSort.entries, key = { it.name }) { sort ->
                FilterChip(
                    selected = list.query.isBlank() && list.sort == sort,
                    onClick = { ncs.sortArtists(sort) },
                    label = { Text(sort.label) },
                )
            }
        }
    }
    item(key = "artist-title") {
        SectionTitle(
            title = when {
                list.query.isNotBlank() -> "Artists matching \"${list.query}\""
                list.popular -> "Most played artists"
                else -> "All NCS artists"
            },
            subtitle = "NoCopyrightSounds",
        )
    }
    items(list.artists, key = { it.path }) { a -> NcsArtistRow(a) { onOpen(a) } }
    item(key = "artist-footer") {
        when {
            list.loading -> Column {
                LoadingRow()
                if (list.popular) Credit("Ranking artists by how much their songs are played…")
            }
            list.error != null -> ErrorRow(list.error) { ncs.loadMoreArtists() }
            list.pages > 0 && list.artists.isEmpty() -> Credit("No NCS artist goes by that name. Try part of it, like \"jim\".")
            list.pages > 0 && list.popular ->
                Credit("Artists with songs among the most played NCS songs (ranked by plays on Deezer). For everyone, choose A–Z.")
            list.pages > 0 && !list.hasMore -> Credit("That's all ${list.artists.size} artists.")
        }
    }
}

@Composable
private fun NcsArtistRow(artist: NcsArtistEntry, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = if (artist.rank > 0) 4.dp else 16.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (artist.rank > 0) {
            Text(
                artist.rank.toString(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = if (artist.rank <= 3) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.width(34.dp),
            )
            Spacer(Modifier.width(4.dp))
        }
        RemoteImage(
            artist.image,
            artist.name,
            Modifier
                .size(52.dp)
                .clip(CircleShape),
        ) {
            Icon(Icons.Rounded.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(artist.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (artist.subtitle.isNotBlank()) {
                Text(
                    artist.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SortChip(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
    )
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
    everythingLabel: String,
    everythingNote: String,
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
                ChoiceRow(everythingLabel, everything) { everything = true }
                if (everything) {
                    Text(
                        everythingNote,
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
