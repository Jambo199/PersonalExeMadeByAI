package com.jambo199.vividmusic.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jambo199.vividmusic.data.FolderSongOrder
import com.jambo199.vividmusic.data.NaturalOrderComparator
import com.jambo199.vividmusic.data.SmartPlaylists
import com.jambo199.vividmusic.data.Song
import com.jambo199.vividmusic.data.formatTotalDuration
import com.jambo199.vividmusic.data.pluralize
import com.jambo199.vividmusic.ui.LocalNav
import com.jambo199.vividmusic.ui.LocalSongMenu
import com.jambo199.vividmusic.ui.MainViewModel
import com.jambo199.vividmusic.ui.components.Artwork
import com.jambo199.vividmusic.ui.components.DragHandle
import com.jambo199.vividmusic.ui.components.EmptyState
import com.jambo199.vividmusic.ui.components.LocalBottomPadding
import com.jambo199.vividmusic.ui.components.LocalSelection
import com.jambo199.vividmusic.ui.components.PlayShuffleRow
import com.jambo199.vividmusic.ui.components.SectionHeader
import com.jambo199.vividmusic.ui.components.SongRow
import com.jambo199.vividmusic.ui.components.SortMenu
import com.jambo199.vividmusic.ui.components.rememberReorderState
import com.jambo199.vividmusic.ui.components.reorderItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailTopBar(title: String, actions: @Composable () -> Unit = {}) {
    val nav = LocalNav.current
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            IconButton(onClick = { nav.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
        },
        actions = { actions() },
    )
}

@Composable
private fun DetailHeader(
    cover: Song?,
    title: String,
    lines: List<String>,
    shape: Shape = RoundedCornerShape(16.dp),
    placeholder: ImageVector,
    onSubtitleClick: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(cover, Modifier.size(132.dp), shape, placeholderIcon = placeholder, iconSize = 48.dp)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
            lines.forEachIndexed { i, line ->
                Text(
                    line,
                    style = if (i == 0) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
                    color = if (i == 0 && onSubtitleClick != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = if (i == 0 && onSubtitleClick != null) Modifier.clickable(onClick = onSubtitleClick) else Modifier,
                )
            }
        }
    }
}

@Composable
private fun NotFound(what: String) {
    Column(Modifier.fillMaxSize()) {
        DetailTopBar("")
        EmptyState(Icons.Rounded.FolderOff, "$what not found", "It may have been moved or deleted.")
    }
}

// region album

@Composable
fun AlbumScreen(viewModel: MainViewModel, albumId: Long) {
    val library by viewModel.library.collectAsState()
    val playback by viewModel.playback.collectAsState()
    val nav = LocalNav.current
    val menu = LocalSongMenu.current
    val selection = LocalSelection.current
    val album = library.albumsById[albumId] ?: return NotFound("Album")
    val songs = album.songs
    val multiDisc = songs.map { it.disc }.distinct().size > 1
    val source = "Album · ${album.title}"

    Column(Modifier.fillMaxSize()) {
        DetailTopBar(album.title)
        LazyColumn(contentPadding = PaddingValues(bottom = LocalBottomPadding.current + 8.dp)) {
            item {
                DetailHeader(
                    cover = album.cover,
                    title = album.title,
                    lines = listOf(
                        album.artist,
                        listOfNotNull(
                            album.year.takeIf { it > 0 }?.toString(),
                            pluralize(songs.size, "song"),
                            formatTotalDuration(album.durationMs),
                        ).joinToString(" • "),
                    ),
                    placeholder = Icons.Rounded.Album,
                    onSubtitleClick = if (library.artistsByName.containsKey(album.artist)) ({ nav.artist(album.artist) }) else null,
                )
                PlayShuffleRow(
                    onPlay = { viewModel.playInOrder(songs, source) },
                    onShuffle = { viewModel.shuffle(songs, source) },
                )
            }
            var lastDisc = -1
            songs.forEachIndexed { index, song ->
                if (multiDisc && song.disc != lastDisc) {
                    lastDisc = song.disc
                    item(key = "disc${song.disc}") { SectionHeader("Disc ${song.disc.coerceAtLeast(1)}") }
                }
                item(key = song.key) {
                    SongRow(
                        song = song,
                        leadingNumber = song.track,
                        subtitle = song.displayArtist,
                        isCurrent = playback.song?.key == song.key,
                        isPlaying = playback.isPlaying,
                        selected = selection.isSelected(song),
                        onClick = { if (selection.active) selection.toggle(song) else viewModel.play(songs, index, source) },
                        onLongClick = { selection.start(song, songs) },
                        onMore = { menu.open(song) },
                    )
                }
            }
            val moreAlbums = if (album.artist == Song.UNKNOWN_ARTIST) emptyList()
            else library.artistsByName[album.artist]?.albums.orEmpty().filter { it.id != album.id }
            if (moreAlbums.isNotEmpty()) {
                item(key = "more") {
                    SectionHeader("More by ${album.artist}")
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(moreAlbums, key = { it.id }) { other ->
                            AlbumCard(other, Modifier.width(148.dp)) { nav.album(other.id) }
                        }
                    }
                }
            }
        }
    }
}

// endregion

// region artist

@Composable
fun ArtistScreen(viewModel: MainViewModel, name: String) {
    val library by viewModel.library.collectAsState()
    val playback by viewModel.playback.collectAsState()
    val nav = LocalNav.current
    val menu = LocalSongMenu.current
    val selection = LocalSelection.current
    val artist = library.artistsByName[name] ?: return NotFound("Artist")
    val songs = artist.songs
    val source = "Artist · ${artist.name}"

    Column(Modifier.fillMaxSize()) {
        DetailTopBar(artist.name)
        LazyColumn(contentPadding = PaddingValues(bottom = LocalBottomPadding.current + 8.dp)) {
            item {
                DetailHeader(
                    cover = artist.cover,
                    title = artist.name,
                    lines = listOf(
                        "${pluralize(artist.albums.size, "album")} • ${pluralize(songs.size, "song")}",
                        formatTotalDuration(songs.sumOf { it.durationMs }),
                    ),
                    shape = CircleShape,
                    placeholder = Icons.Rounded.Person,
                )
                PlayShuffleRow(
                    onPlay = { viewModel.playInOrder(songs, source) },
                    onShuffle = { viewModel.shuffle(songs, source) },
                )
            }
            if (artist.albums.isNotEmpty()) {
                item { SectionHeader("Albums") }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(artist.albums, key = { it.id }) { album ->
                            AlbumCard(album, Modifier.width(150.dp)) { nav.album(album.id) }
                        }
                    }
                }
            }
            item { SectionHeader("Songs") }
            itemsIndexed(songs, key = { _, s -> s.key }) { index, song ->
                SongRow(
                    song = song,
                    subtitle = song.displayAlbum,
                    isCurrent = playback.song?.key == song.key,
                    isPlaying = playback.isPlaying,
                    selected = selection.isSelected(song),
                    onClick = { if (selection.active) selection.toggle(song) else viewModel.play(songs, index, source) },
                    onLongClick = { selection.start(song, songs) },
                    onMore = { menu.open(song) },
                )
            }
        }
    }
}

// endregion

// region folder

@Composable
fun FolderScreen(viewModel: MainViewModel, key: String) {
    val library by viewModel.library.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val playback by viewModel.playback.collectAsState()
    val nav = LocalNav.current
    val menu = LocalSongMenu.current
    val selection = LocalSelection.current
    val node = library.folders[key] ?: return NotFound("Folder")
    val songs = remember(node, settings.folderSongOrder) { viewModel.folderSongs(node) }
    val subfolders = remember(node, library) {
        node.childKeys.mapNotNull { library.folders[it] }.sortedWith(compareBy(NaturalOrderComparator) { it.name })
    }
    val source = "Folder · ${node.name}"
    var menuOpen by remember { mutableStateOf(false) }
    var sortOpen by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    val pickedRootUri = remember(node, settings.pickedFolders) {
        if (node.isPickedFolder && node.parentKey == null) {
            settings.pickedFolders.firstOrNull { "picked:" + Integer.toHexString(it.hashCode()) == node.key }
        } else {
            null
        }
    }

    Column(Modifier.fillMaxSize()) {
        DetailTopBar(node.name) {
            Box {
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "More") }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Play all, including subfolders") },
                        leadingIcon = { Icon(Icons.Rounded.PlayArrow, null) },
                        onClick = {
                            menuOpen = false
                            viewModel.playInOrder(viewModel.folderTreeSongs(node), source)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Shuffle all, including subfolders") },
                        leadingIcon = { Icon(Icons.Rounded.Shuffle, null) },
                        onClick = {
                            menuOpen = false
                            viewModel.shuffle(viewModel.folderTreeSongs(node), source)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Play next") },
                        leadingIcon = { Icon(Icons.Rounded.SkipNext, null) },
                        onClick = {
                            menuOpen = false
                            viewModel.playNext(viewModel.folderTreeSongs(node))
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Add to queue") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Rounded.QueueMusic, null) },
                        onClick = {
                            menuOpen = false
                            viewModel.addToQueue(viewModel.folderTreeSongs(node))
                        },
                    )
                    if (pickedRootUri != null) {
                        DropdownMenuItem(
                            text = { Text("Remove this folder from the app") },
                            leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                            onClick = {
                                menuOpen = false
                                confirmRemove = true
                            },
                        )
                    } else if (!node.isPickedFolder) {
                        DropdownMenuItem(
                            text = { Text("Hide this folder") },
                            leadingIcon = { Icon(Icons.Rounded.FolderOff, null) },
                            onClick = {
                                menuOpen = false
                                viewModel.excludeFolder(node.key)
                                nav.back()
                            },
                        )
                    }
                }
            }
        }
        LazyColumn(contentPadding = PaddingValues(bottom = LocalBottomPadding.current + 8.dp)) {
            item {
                Text(
                    node.displayPath,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Text(
                    listOfNotNull(
                        subfolders.size.takeIf { it > 0 }?.let { pluralize(it, "folder") },
                        pluralize(node.totalSongs, "song") + if (subfolders.isNotEmpty()) " in total" else "",
                    ).joinToString(" • "),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                PlayShuffleRow(
                    onPlay = {
                        viewModel.playInOrder(if (songs.isNotEmpty()) songs else viewModel.folderTreeSongs(node), source)
                    },
                    onShuffle = {
                        viewModel.shuffle(if (songs.isNotEmpty()) songs else viewModel.folderTreeSongs(node), source)
                    },
                )
            }
            if (subfolders.isNotEmpty()) {
                item { SectionHeader("Folders") }
                items(subfolders, key = { "f:" + it.key }) { child ->
                    FolderRow(child) { nav.folder(child.key) }
                }
            }
            if (songs.isNotEmpty()) {
                item {
                    SectionHeader(pluralize(songs.size, "song")) {
                        Box {
                            TextButton(onClick = { sortOpen = true }) {
                                Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(settings.folderSongOrder.label)
                            }
                            SortMenu(
                                expanded = sortOpen,
                                onDismiss = { sortOpen = false },
                                options = FolderSongOrder.entries.map { it to it.label },
                                selected = settings.folderSongOrder,
                                ascending = true,
                                onSelect = { o -> viewModel.updateSettings { it.copy(folderSongOrder = o) } },
                                onToggleDirection = {},
                            )
                        }
                    }
                }
                itemsIndexed(songs, key = { _, s -> s.key }) { index, song ->
                    SongRow(
                        song = song,
                        isCurrent = playback.song?.key == song.key,
                        isPlaying = playback.isPlaying,
                        subtitle = "${song.displayArtist} • ${song.fileName}",
                        selected = selection.isSelected(song),
                        onClick = { if (selection.active) selection.toggle(song) else viewModel.play(songs, index, source) },
                        onLongClick = { selection.start(song, songs) },
                        onMore = { menu.open(song) },
                    )
                }
            } else if (subfolders.isEmpty()) {
                item { EmptyState(Icons.Rounded.Folder, "Empty folder", "There are no songs in this folder.") }
            }
        }
    }

    if (confirmRemove && pickedRootUri != null) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Remove folder?") },
            text = { Text("\"${node.name}\" will no longer be scanned. Your files are not deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = false
                    viewModel.removePickedFolder(pickedRootUri)
                    nav.back()
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Cancel") } },
        )
    }
}

// endregion

// region playlist

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlaylistScreen(viewModel: MainViewModel, id: Long) {
    val library by viewModel.library.collectAsState()
    val userData by viewModel.userData.collectAsState()
    val playback by viewModel.playback.collectAsState()
    val nav = LocalNav.current
    val menu = LocalSongMenu.current
    val selection = LocalSelection.current
    val playlist = userData.playlists.firstOrNull { it.id == id }
    if (id > 0 && playlist == null) return NotFound("Playlist")
    val title = playlist?.name ?: SmartPlaylists.name(id)
    val songs = remember(id, library, userData) { viewModel.playlistSongs(id, library, userData) }
    val source = "Playlist · $title"
    val editable = playlist != null
    var menuOpen by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/x-mpegurl")) { uri ->
        if (uri != null) viewModel.exportPlaylist(title, songs, uri)
    }

    // Playlist entries keep their own index so duplicates and missing files are handled.
    data class Entry(val slot: Int, val song: Song)
    val entries = remember(playlist, library, songs) {
        if (playlist != null) {
            playlist.songKeys.mapIndexedNotNull { i, k -> library.byKey[k]?.let { Entry(i, it) } }
        } else {
            songs.mapIndexed { i, s -> Entry(i, s) }
        }
    }
    val reorder = rememberReorderState(
        source = entries,
        listState = listState,
        keyOf = { "e${it.slot}" },
        headerCount = 1,
        onMove = { from, to ->
            val f = entries.getOrNull(from)?.slot
            val t = entries.getOrNull(to)?.slot
            if (f != null && t != null) viewModel.movePlaylistItem(id, f, t)
        },
    )

    Column(Modifier.fillMaxSize()) {
        DetailTopBar(title) {
            Box {
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "More") }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Add all to queue") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Rounded.QueueMusic, null) },
                        onClick = {
                            menuOpen = false
                            viewModel.addToQueue(songs)
                        },
                    )
                    if (songs.isNotEmpty()) {
                        DropdownMenuItem(
                            text = { Text("Export as M3U file…") },
                            leadingIcon = { Icon(Icons.Rounded.Save, null) },
                            onClick = {
                                menuOpen = false
                                exporter.launch("$title.m3u8")
                            },
                        )
                    }
                    if (editable) {
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                            onClick = {
                                menuOpen = false
                                renaming = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete playlist") },
                            leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                            onClick = {
                                menuOpen = false
                                confirmDelete = true
                            },
                        )
                    }
                    if (id == SmartPlaylists.RECENTLY_PLAYED || id == SmartPlaylists.MOST_PLAYED) {
                        DropdownMenuItem(
                            text = { Text("Clear listening history") },
                            leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                            onClick = {
                                menuOpen = false
                                viewModel.clearHistory()
                            },
                        )
                    }
                }
            }
        }
        LazyColumn(state = listState, contentPadding = PaddingValues(bottom = LocalBottomPadding.current + 8.dp)) {
            item(key = "header") {
                DetailHeader(
                    cover = songs.firstOrNull(),
                    title = title,
                    lines = listOf(pluralize(songs.size, "song"), formatTotalDuration(songs.sumOf { it.durationMs })),
                    placeholder = Icons.AutoMirrored.Rounded.PlaylistPlay,
                )
                if (songs.isNotEmpty()) {
                    PlayShuffleRow(
                        onPlay = { viewModel.playInOrder(songs, source) },
                        onShuffle = { viewModel.shuffle(songs, source) },
                    )
                } else {
                    Text(
                        if (editable) "This playlist is empty. Add songs from any song's ⋮ menu." else "Nothing here yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
            itemsIndexed(reorder.items, key = { _, e -> "e${e.slot}" }) { index, entry ->
                val key = "e${entry.slot}"
                SongRow(
                    song = entry.song,
                    modifier = Modifier
                        .reorderItem(reorder, key)
                        .then(if (reorder.draggingKey == key) Modifier else Modifier.animateItem()),
                    isCurrent = playback.song?.key == entry.song.key,
                    isPlaying = playback.isPlaying,
                    selected = selection.isSelected(entry.song),
                    onClick = {
                        if (selection.active) selection.toggle(entry.song)
                        else viewModel.play(reorder.items.map { it.song }, index, source)
                    },
                    onLongClick = { selection.start(entry.song, songs) },
                    onMore = { menu.open(entry.song, if (editable) id else null, entry.slot) },
                    trailing = if (editable) ({ DragHandle(reorder, key) }) else null,
                )
            }
        }
    }

    if (renaming && playlist != null) {
        NameDialog("Rename playlist", playlist.name, "Rename", onDismiss = { renaming = false }) {
            viewModel.renamePlaylist(id, it)
            renaming = false
        }
    }
    if (confirmDelete && playlist != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete playlist?") },
            text = { Text("\"${playlist.name}\" will be deleted. The songs stay on your phone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    nav.back()
                    viewModel.deletePlaylist(id)
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

// endregion
