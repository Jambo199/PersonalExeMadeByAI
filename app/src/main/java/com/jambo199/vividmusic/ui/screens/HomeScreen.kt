package com.jambo199.vividmusic.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FiberNew
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Quiz
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jambo199.vividmusic.data.Album
import com.jambo199.vividmusic.data.Artist
import com.jambo199.vividmusic.data.Genre
import com.jambo199.vividmusic.data.HomeFeed
import com.jambo199.vividmusic.data.MainSection
import com.jambo199.vividmusic.data.SmartPlaylists
import com.jambo199.vividmusic.data.Song
import com.jambo199.vividmusic.data.UserDataStore
import com.jambo199.vividmusic.data.formatTotalDuration
import com.jambo199.vividmusic.data.pluralize
import com.jambo199.vividmusic.playback.PlaybackState
import com.jambo199.vividmusic.ui.LocalNav
import com.jambo199.vividmusic.ui.LocalSongMenu
import com.jambo199.vividmusic.ui.MainViewModel
import com.jambo199.vividmusic.ui.components.Artwork
import com.jambo199.vividmusic.ui.components.EmptyState
import com.jambo199.vividmusic.ui.components.LocalBottomPadding
import com.jambo199.vividmusic.ui.components.SongRow
import com.jambo199.vividmusic.ui.components.placeholderBrush
import java.util.Calendar

/** Quick picks, recent albums and other ways back into your music. */
@Composable
fun HomeScreen(viewModel: MainViewModel) {
    val feed by viewModel.home.collectAsState()
    val library by viewModel.library.collectAsState()
    val libraryState by viewModel.libraryState.collectAsState()
    val playback by viewModel.playback.collectAsState()
    val nav = LocalNav.current
    val menu = LocalSongMenu.current
    val bottom = LocalBottomPadding.current
    val listState = rememberLazyListState()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) viewModel.addPickedFolder(uri)
    }

    LaunchedEffect(listState) {
        viewModel.reselect.collect { if (it == MainSection.HOME) listState.animateScrollToItem(0) }
    }

    if (feed.songCount == 0) {
        if (library.songs.isEmpty() && libraryState.hasLoadedOnce && !libraryState.isScanning) {
            EmptyState(
                icon = Icons.Rounded.LibraryMusic,
                title = "Your music will show up here",
                message = "Put music files on your phone or SD card, or choose a folder to play from.",
                action = { FilledTonalButton(onClick = { picker.launch(null) }) { Text("Choose a folder") } },
            )
        }
        return
    }

    val playAlbum: (Album) -> Unit = { viewModel.play(it.songs, 0, it.title) }

    LazyColumn(state = listState, contentPadding = PaddingValues(bottom = bottom + 16.dp)) {
        item(key = "greeting") {
            Greeting(feed) { viewModel.shuffle(library.songs, "All songs") }
        }
        item(key = "tiles") {
            ShortcutTiles { nav.playlist(it) }
        }

        if (feed.quickPicks.isNotEmpty()) {
            item(key = "quick-title") {
                HomeHeader("Quick picks", "A mix from your library") {
                    IconButton(onClick = { viewModel.refreshHome() }) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "New picks")
                    }
                    TextButton(onClick = { viewModel.play(feed.quickPicks, 0, "Quick picks") }) { Text("Play all") }
                }
            }
            item(key = "quick") {
                QuickPicks(
                    songs = feed.quickPicks,
                    playback = playback,
                    onPlay = { viewModel.play(feed.quickPicks, it, "Quick picks") },
                    onMore = { menu.open(it) },
                )
            }
        }

        if (feed.keepListening.isNotEmpty()) {
            item(key = "keep-title") { HomeHeader("Keep listening", "Played lately") }
            item(key = "keep") { AlbumCarousel(feed.keepListening, onOpen = { nav.album(it.id) }, onPlay = playAlbum) }
        }

        if (feed.forgottenFavorites.isNotEmpty()) {
            item(key = "forgotten-title") { HomeHeader("Forgotten favourites", "Songs you loved that haven't come up lately") }
            item(key = "forgotten") {
                SongCarousel(
                    songs = feed.forgottenFavorites,
                    onPlay = { viewModel.play(feed.forgottenFavorites, it, "Forgotten favourites") },
                    onMore = { menu.open(it) },
                )
            }
        }

        if (feed.recentlyAdded.isNotEmpty()) {
            item(key = "added-title") {
                HomeHeader("Recently added", "New on your phone") {
                    TextButton(onClick = { nav.playlist(SmartPlaylists.RECENTLY_ADDED) }) { Text("See all") }
                }
            }
            item(key = "added") { AlbumCarousel(feed.recentlyAdded, onOpen = { nav.album(it.id) }, onPlay = playAlbum) }
        }

        if (feed.artists.isNotEmpty()) {
            item(key = "artists-title") {
                if (feed.artistsByPlays) HomeHeader("Your top artists", "Who you play most")
                else HomeHeader("Artists", "Biggest in your library")
            }
            item(key = "artists") { ArtistCarousel(feed.artists) { nav.artist(it.name) } }
        }

        if (library.genres.size >= 2) {
            item(key = "genres-title") { HomeHeader("Genres", "Browse by style") }
            item(key = "genres") { GenreCarousel(library.genres.take(12)) { nav.genre(it.name) } }
        }

        if (feed.unplayedAlbums.isNotEmpty()) {
            item(key = "unplayed-title") { HomeHeader("Not played yet", "Albums waiting for a first listen") }
            item(key = "unplayed") { AlbumCarousel(feed.unplayedAlbums, onOpen = { nav.album(it.id) }, onPlay = playAlbum) }
        }

        item(key = "stats") { StatsCard(viewModel) { nav.stats() } }
        item(key = "wrapped") {
            GradientCard(
                "Your Wrapped",
                "Your top songs, artists and listening personality",
                Icons.Rounded.AutoAwesome,
                listOf(Color(0xFF12005E), Color(0xFFD500F9)),
            ) { nav.wrapped() }
        }
        item(key = "quiz") {
            GradientCard(
                "Guess the song",
                "Hear a few seconds, name the tune. How many can you get?",
                Icons.Rounded.Quiz,
                listOf(Color(0xFF7C4DFF), Color(0xFFE040FB)),
            ) { nav.quiz() }
        }
    }
}

private fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    in 17..21 -> "Good evening"
    else -> "Late night tunes"
}

@Composable
private fun Greeting(feed: HomeFeed, onShuffle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(greeting(), style = MaterialTheme.typography.headlineMedium)
            Text(
                "${pluralize(feed.songCount, "song")} • ${formatTotalDuration(feed.totalDurationMs)} of music",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FilledIconButton(onClick = onShuffle, modifier = Modifier.size(56.dp)) {
            Icon(Icons.Rounded.Shuffle, contentDescription = "Shuffle all")
        }
    }
}

private data class Tile(val playlist: Long, val icon: ImageVector, val colors: List<Color>)

private val Tiles = listOf(
    Tile(SmartPlaylists.FAVORITES, Icons.Rounded.Favorite, listOf(Color(0xFF7C4DFF), Color(0xFFE040FB))),
    Tile(SmartPlaylists.RECENTLY_PLAYED, Icons.Rounded.History, listOf(Color(0xFF1E88E5), Color(0xFF26C6DA))),
    Tile(SmartPlaylists.MOST_PLAYED, Icons.Rounded.Whatshot, listOf(Color(0xFFF4511E), Color(0xFFFFB300))),
    Tile(SmartPlaylists.RECENTLY_ADDED, Icons.Rounded.FiberNew, listOf(Color(0xFF00897B), Color(0xFF9CCC65))),
)

@Composable
private fun ShortcutTiles(onOpen: (Long) -> Unit) {
    Column(
        Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Tiles.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { tile ->
                    Surface(
                        onClick = { onOpen(tile.playlist) },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(56.dp)
                                    .background(Brush.linearGradient(tile.colors)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(tile.icon, contentDescription = null, tint = Color.White)
                            }
                            Text(
                                SmartPlaylists.name(tile.playlist),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 12.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeHeader(title: String, subtitle: String, actions: (@Composable () -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, top = 24.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        actions?.invoke()
    }
}

/** Columns of four songs that you swipe through sideways, like a shelf. */
@Composable
private fun QuickPicks(songs: List<Song>, playback: PlaybackState, onPlay: (Int) -> Unit, onMore: (Song) -> Unit) {
    val columns = remember(songs) { songs.withIndex().toList().chunked(4) }
    val state = rememberLazyListState()
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columnWidth = if (columns.size > 1) maxWidth * 0.88f else maxWidth
        LazyRow(
            state = state,
            flingBehavior = rememberSnapFlingBehavior(state, SnapPosition.Start),
        ) {
            items(columns, key = { it.first().value.key }) { column ->
                Column(Modifier.width(columnWidth)) {
                    column.forEach { (index, song) ->
                        SongRow(
                            song = song,
                            onClick = { onPlay(index) },
                            isCurrent = playback.song?.key == song.key,
                            isPlaying = playback.isPlaying,
                            subtitle = song.displayArtist,
                            onMore = { onMore(song) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayBadge(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.PlayArrow,
            contentDescription = "Play",
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun AlbumCarousel(albums: List<Album>, onOpen: (Album) -> Unit, onPlay: (Album) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(albums, key = { it.id }) { album ->
            Column(
                Modifier
                    .width(148.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onOpen(album) }
                    .padding(4.dp),
            ) {
                Box {
                    Artwork(
                        album.cover,
                        Modifier.size(140.dp),
                        RoundedCornerShape(12.dp),
                        placeholderIcon = Icons.Rounded.Album,
                        iconSize = 44.dp,
                    )
                    PlayBadge(
                        onClick = { onPlay(album) },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp),
                    )
                }
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
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SongCarousel(songs: List<Song>, onPlay: (Int) -> Unit, onMore: (Song) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        itemsIndexed(songs, key = { _, s -> s.key }) { index, song ->
            Column(
                Modifier
                    .width(148.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .combinedClickable(onClick = { onPlay(index) }, onLongClick = { onMore(song) })
                    .padding(4.dp),
            ) {
                Artwork(song, Modifier.size(140.dp), RoundedCornerShape(12.dp), iconSize = 44.dp)
                Spacer(Modifier.height(8.dp))
                Text(song.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    song.displayArtist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ArtistCarousel(artists: List<Artist>, onOpen: (Artist) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(artists, key = { it.name }) { artist ->
            Column(
                Modifier
                    .width(116.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onOpen(artist) }
                    .padding(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Artwork(artist.cover, Modifier.size(104.dp), CircleShape, placeholderIcon = Icons.Rounded.Person, iconSize = 40.dp)
                Spacer(Modifier.height(8.dp))
                Text(
                    artist.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
                Text(
                    pluralize(artist.songs.size, "song"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StatsCard(viewModel: MainViewModel, onOpen: () -> Unit) {
    val data by viewModel.userData.collectAsState()
    val week = remember(data.dailyMs) {
        val now = System.currentTimeMillis()
        (0 until 7).sumOf { data.dailyMs[UserDataStore.dayKey(now - it * 24L * 60 * 60 * 1000)] ?: 0L }
    }
    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 28.dp),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Insights, contentDescription = null, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("Your listening", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (week >= 60_000) "${formatTotalDuration(week)} this week • ${pluralize(data.playCounts.values.sum(), "play")} in total"
                    else "See what you play most",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
        }
    }
}

/** A colourful card leading to one of the fun pages (Wrapped, the quiz). */
@Composable
private fun GradientCard(title: String, subtitle: String, icon: ImageVector, colors: List<Color>, onOpen: () -> Unit) {
    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp),
    ) {
        Row(
            Modifier
                .background(Brush.linearGradient(colors))
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = Color.White)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Color.White)
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = Color.White)
        }
    }
}

@Composable
private fun GenreCarousel(genres: List<Genre>, onOpen: (Genre) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(genres, key = { it.name }) { genre ->
            Box(
                Modifier
                    .width(156.dp)
                    .height(92.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(placeholderBrush(genre.name))
                    .clickable { onOpen(genre) },
            ) {
                Artwork(
                    genre.cover,
                    Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 14.dp, y = 10.dp)
                        .size(64.dp)
                        .graphicsLayer { rotationZ = 22f },
                    RoundedCornerShape(8.dp),
                )
                Column(Modifier.padding(12.dp)) {
                    Text(
                        genre.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(96.dp),
                    )
                    Text(
                        pluralize(genre.songs.size, "song"),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.85f),
                    )
                }
            }
        }
    }
}
