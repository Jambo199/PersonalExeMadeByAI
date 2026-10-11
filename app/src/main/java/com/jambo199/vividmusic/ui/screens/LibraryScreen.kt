package com.jambo199.vividmusic.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Quiz
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jambo199.vividmusic.data.Album
import com.jambo199.vividmusic.data.AlbumSortField
import com.jambo199.vividmusic.data.Artist
import com.jambo199.vividmusic.data.ArtistSortField
import com.jambo199.vividmusic.data.LibraryTab
import com.jambo199.vividmusic.data.MainSection
import com.jambo199.vividmusic.data.SongSortField
import com.jambo199.vividmusic.data.pluralize
import com.jambo199.vividmusic.data.scrollLabel
import com.jambo199.vividmusic.data.sortKey
import com.jambo199.vividmusic.ui.LocalNav
import com.jambo199.vividmusic.ui.LocalSongMenu
import com.jambo199.vividmusic.ui.MainViewModel
import com.jambo199.vividmusic.ui.components.Artwork
import com.jambo199.vividmusic.ui.components.EmptyState
import com.jambo199.vividmusic.ui.components.FastScroller
import com.jambo199.vividmusic.ui.components.LocalBottomPadding
import com.jambo199.vividmusic.ui.components.LocalSelection
import com.jambo199.vividmusic.ui.components.SongRow
import com.jambo199.vividmusic.ui.components.SortMenu
import com.jambo199.vividmusic.ui.components.UpdateBanner
import com.jambo199.vividmusic.ui.online.NcsScreen
import kotlinx.coroutines.launch


/** The screen behind the bottom navigation bar: Home, Library or Folders. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel, onSleepTimer: () -> Unit) {
    val nav = LocalNav.current
    val section by viewModel.section.collectAsState()
    val libraryState by viewModel.libraryState.collectAsState()
    var menuOpen by remember { mutableStateOf(false) }
    // Keeps each page's scroll position while you switch between them.
    val pages = rememberSaveableStateHolder()

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    if (section == MainSection.HOME) "Vivid Music" else section.label,
                    fontWeight = FontWeight.Bold,
                )
            },
            actions = {
                IconButton(onClick = { nav.search() }) { Icon(Icons.Rounded.Search, contentDescription = "Search") }
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "More") }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Shuffle all") },
                        leadingIcon = { Icon(Icons.Rounded.Shuffle, null) },
                        onClick = {
                            menuOpen = false
                            viewModel.shuffle(viewModel.library.value.songs, "All songs")
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Sleep timer") },
                        leadingIcon = { Icon(Icons.Rounded.Bedtime, null) },
                        onClick = {
                            menuOpen = false
                            onSleepTimer()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Focus timer") },
                        leadingIcon = { Icon(Icons.Rounded.HourglassTop, null) },
                        onClick = {
                            menuOpen = false
                            nav.focus()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Car mode") },
                        leadingIcon = { Icon(Icons.Rounded.DirectionsCar, null) },
                        onClick = {
                            menuOpen = false
                            nav.car()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Music alarm") },
                        leadingIcon = { Icon(Icons.Rounded.Alarm, null) },
                        onClick = {
                            menuOpen = false
                            nav.alarm()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Equalizer") },
                        leadingIcon = { Icon(Icons.Rounded.Equalizer, null) },
                        onClick = {
                            menuOpen = false
                            nav.equalizer()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Guess the song") },
                        leadingIcon = { Icon(Icons.Rounded.Quiz, null) },
                        onClick = {
                            menuOpen = false
                            nav.quiz()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Listening stats") },
                        leadingIcon = { Icon(Icons.Rounded.Insights, null) },
                        onClick = {
                            menuOpen = false
                            nav.stats()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Rescan library") },
                        leadingIcon = { Icon(Icons.Rounded.Refresh, null) },
                        onClick = {
                            menuOpen = false
                            viewModel.refreshLibrary()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Settings") },
                        leadingIcon = { Icon(Icons.Rounded.Settings, null) },
                        onClick = {
                            menuOpen = false
                            nav.settings()
                        },
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        )
        val update by viewModel.update.collectAsState()
        val updateDismissed by viewModel.updateBannerDismissed.collectAsState()
        UpdateBanner(
            state = update,
            dismissed = updateDismissed,
            onUpdate = { viewModel.startUpdate() },
            onDismiss = { viewModel.dismissUpdateBanner() },
        )
        if (libraryState.isScanning || libraryState.scanMessage != null) {
            Column(Modifier.fillMaxWidth()) {
                LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
                libraryState.scanMessage?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                    )
                }
            }
        }
        AnimatedContent(
            targetState = section,
            transitionSpec = { fadeIn(tween(200, delayMillis = 60)) togetherWith fadeOut(tween(120)) },
            modifier = Modifier.fillMaxSize(),
            label = "section",
        ) { page ->
            pages.SaveableStateProvider(page.name) {
                when (page) {
                    MainSection.HOME -> HomeScreen(viewModel)
                    MainSection.LIBRARY -> LibraryPages(viewModel)
                    MainSection.FOLDERS -> FoldersTab(viewModel)
                    MainSection.NCS -> NcsScreen(viewModel)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun LibraryPages(viewModel: MainViewModel) {
    val scope = rememberCoroutineScope()
    val settings by viewModel.settings.collectAsState()
    val tabs = settings.libraryTabs.ifEmpty { listOf(LibraryTab.SONGS) }
    val pagerState = rememberPagerState(initialPage = settings.libraryTab.coerceIn(0, tabs.lastIndex)) { tabs.size }

    val tabRequest by viewModel.tabRequest.collectAsState()
    LaunchedEffect(tabRequest) {
        tabRequest?.let {
            pagerState.scrollToPage(tabs.indexOf(it).coerceAtLeast(0))
            viewModel.consumeTabRequest()
        }
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            if (page != viewModel.settings.value.libraryTab) viewModel.updateSettings { it.copy(libraryTab = page) }
        }
    }

    Column(Modifier.fillMaxSize()) {
        if (tabs.size > 1) {
            PrimaryTabRow(
                selectedTabIndex = pagerState.currentPage.coerceIn(0, tabs.lastIndex),
                containerColor = MaterialTheme.colorScheme.surface,
                divider = {},
            ) {
                tabs.forEachIndexed { index, tab ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = {
                            Text(
                                tab.label,
                                maxLines = 1,
                                fontWeight = if (pagerState.currentPage == index) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        },
                    )
                }
            }
        }
        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = 1,
            modifier = Modifier.fillMaxSize(),
            // Short pages (few playlists) start at the top instead of floating mid-screen.
            verticalAlignment = Alignment.Top,
            key = { tabs.getOrNull(it)?.name ?: it },
        ) { page ->
            when (tabs.getOrNull(page)) {
                LibraryTab.ALBUMS -> AlbumsTab(viewModel)
                LibraryTab.ARTISTS -> ArtistsTab(viewModel)
                LibraryTab.PLAYLISTS -> PlaylistsTab(viewModel)
                else -> SongsTab(viewModel)
            }
        }
    }
}

@Composable
private fun SongsTab(viewModel: MainViewModel) {
    val songs by viewModel.sortedSongs.collectAsState()
    val selection = LocalSelection.current
    val settings by viewModel.settings.collectAsState()
    val playback by viewModel.playback.collectAsState()
    val libraryState by viewModel.libraryState.collectAsState()
    val menu = LocalSongMenu.current
    val bottom = LocalBottomPadding.current
    val listState = rememberLazyListState()
    var sortOpen by remember { mutableStateOf(false) }

    if (songs.isEmpty()) {
        if (libraryState.hasLoadedOnce && !libraryState.isScanning) {
            EmptyState(
                icon = Icons.Rounded.LibraryMusic,
                title = "No songs found",
                message = "Put music files on your phone or SD card, or add a folder from the Folders page.",
            )
        }
        return
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(state = listState, contentPadding = PaddingValues(bottom = bottom + 8.dp)) {
            item(key = "header") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilledTonalButton(onClick = { viewModel.shuffle(songs, "All songs") }) {
                        Icon(Icons.Rounded.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Shuffle ${songs.size}")
                    }
                    Spacer(Modifier.weight(1f))
                    Box {
                        TextButton(onClick = { sortOpen = true }) {
                            Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(settings.songSort.label)
                        }
                        SortMenu(
                            expanded = sortOpen,
                            onDismiss = { sortOpen = false },
                            options = SongSortField.entries.map { it to it.label },
                            selected = settings.songSort,
                            ascending = settings.songSortAscending,
                            onSelect = { f -> viewModel.updateSettings { it.copy(songSort = f) } },
                            onToggleDirection = { viewModel.updateSettings { it.copy(songSortAscending = !it.songSortAscending) } },
                        )
                    }
                }
            }
            itemsIndexed(songs, key = { _, s -> s.key }) { index, song ->
                SongRow(
                    song = song,
                    isCurrent = playback.song?.key == song.key,
                    isPlaying = playback.isPlaying,
                    selected = selection.isSelected(song),
                    onClick = { if (selection.active) selection.toggle(song) else viewModel.play(songs, index, "All songs") },
                    onLongClick = { selection.start(song, songs) },
                    onMore = { menu.open(song) },
                )
            }
        }
        FastScroller(
            listState = listState,
            itemCount = songs.size,
            headerCount = 1,
            bottomPadding = bottom,
            modifier = Modifier.align(Alignment.CenterEnd),
            labelFor = { i -> songs.getOrNull(i)?.let { scrollLabel(it, settings.songSort) } },
        )
    }
}

@Composable
private fun AlbumsTab(viewModel: MainViewModel) {
    val albums by viewModel.sortedAlbums.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val nav = LocalNav.current
    val bottom = LocalBottomPadding.current
    var sortOpen by remember { mutableStateOf(false) }

    if (albums.isEmpty()) {
        EmptyState(Icons.Rounded.Album, "No albums", "Albums appear here once you have music on your phone.")
        return
    }

    val header: @Composable () -> Unit = {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(pluralize(albums.size, "album"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { viewModel.updateSettings { it.copy(albumGrid = !it.albumGrid) } }) {
                Icon(
                    if (settings.albumGrid) Icons.Rounded.ViewList else Icons.Rounded.GridView,
                    contentDescription = if (settings.albumGrid) "Show as list" else "Show as grid",
                )
            }
            Box {
                TextButton(onClick = { sortOpen = true }) {
                    Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(settings.albumSort.label)
                }
                SortMenu(
                    expanded = sortOpen,
                    onDismiss = { sortOpen = false },
                    options = AlbumSortField.entries.map { it to it.label },
                    selected = settings.albumSort,
                    ascending = settings.albumSortAscending,
                    onSelect = { f -> viewModel.updateSettings { it.copy(albumSort = f) } },
                    onToggleDirection = { viewModel.updateSettings { it.copy(albumSortAscending = !it.albumSortAscending) } },
                )
            }
        }
    }

    if (settings.albumGrid) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(152.dp),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = bottom + 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "header", span = { GridItemSpan(maxLineSpan) }) { header() }
            items(albums, key = { it.id }) { album -> AlbumCard(album) { nav.album(album.id) } }
        }
    } else {
        LazyColumn(contentPadding = PaddingValues(bottom = bottom + 8.dp)) {
            item(key = "header") { header() }
            items(albums, key = { it.id }) { album ->
                AlbumListRow(album) { nav.album(album.id) }
            }
        }
    }
}

@Composable
fun AlbumCard(album: Album, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(4.dp),
    ) {
        Artwork(
            album.cover,
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            RoundedCornerShape(12.dp),
            placeholderIcon = Icons.Rounded.Album,
            iconSize = 48.dp,
        )
        Spacer(Modifier.height(8.dp))
        Text(album.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            album.artist,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun AlbumListRow(album: Album, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(album.cover, Modifier.size(56.dp), placeholderIcon = Icons.Rounded.Album)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(album.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(album.artist, album.year.takeIf { it > 0 }?.toString(), pluralize(album.songs.size, "song")).joinToString(" • "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ArtistsTab(viewModel: MainViewModel) {
    val artists by viewModel.sortedArtists.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val nav = LocalNav.current
    val bottom = LocalBottomPadding.current
    val listState = rememberLazyListState()
    var sortOpen by remember { mutableStateOf(false) }

    if (artists.isEmpty()) {
        EmptyState(Icons.Rounded.Person, "No artists", "Artists appear here once you have music on your phone.")
        return
    }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(state = listState, contentPadding = PaddingValues(bottom = bottom + 8.dp)) {
            item(key = "header") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(pluralize(artists.size, "artist"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    Box {
                        TextButton(onClick = { sortOpen = true }) {
                            Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(settings.artistSort.label)
                        }
                        SortMenu(
                            expanded = sortOpen,
                            onDismiss = { sortOpen = false },
                            options = ArtistSortField.entries.map { it to it.label },
                            selected = settings.artistSort,
                            ascending = settings.artistSortAscending,
                            onSelect = { f -> viewModel.updateSettings { it.copy(artistSort = f) } },
                            onToggleDirection = { viewModel.updateSettings { it.copy(artistSortAscending = !it.artistSortAscending) } },
                        )
                    }
                }
            }
            items(artists, key = { it.name }) { artist -> ArtistRow(artist) { nav.artist(artist.name) } }
        }
        FastScroller(
            listState = listState,
            itemCount = artists.size,
            headerCount = 1,
            bottomPadding = bottom,
            modifier = Modifier.align(Alignment.CenterEnd),
            labelFor = { i -> artists.getOrNull(i)?.let { sortKey(it.name).firstOrNull()?.uppercase() } },
        )
    }
}

@Composable
fun ArtistRow(artist: Artist, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(artist.cover, Modifier.size(56.dp), CircleShape, placeholderIcon = Icons.Rounded.Person)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(artist.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${pluralize(artist.albums.size, "album")} • ${pluralize(artist.songs.size, "song")}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
