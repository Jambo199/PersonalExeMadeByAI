package com.jambo199.vividmusic.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.jambo199.vividmusic.data.pluralize
import com.jambo199.vividmusic.ui.LocalNav
import com.jambo199.vividmusic.ui.LocalSongMenu
import com.jambo199.vividmusic.ui.MainViewModel
import com.jambo199.vividmusic.ui.components.EmptyState
import com.jambo199.vividmusic.ui.components.LocalBottomPadding
import com.jambo199.vividmusic.ui.components.LocalSelection
import com.jambo199.vividmusic.ui.components.SectionHeader
import com.jambo199.vividmusic.ui.components.SongRow

@Composable
fun SearchScreen(viewModel: MainViewModel) {
    val query by viewModel.query.collectAsState()
    val results by viewModel.searchResults.collectAsState()
    val playback by viewModel.playback.collectAsState()
    val nav = LocalNav.current
    val menu = LocalSongMenu.current
    val selection = LocalSelection.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Column(Modifier.fillMaxSize()) {
        TextField(
            value = query,
            onValueChange = viewModel::setQuery,
            placeholder = { Text("Songs, albums, artists, folders") },
            leadingIcon = {
                IconButton(onClick = { nav.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { viewModel.setQuery("") }) { Icon(Icons.Rounded.Close, contentDescription = "Clear") }
                } else {
                    Icon(Icons.Rounded.Search, contentDescription = null)
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
            shape = RoundedCornerShape(28.dp),
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            modifier = Modifier
                .statusBarsPadding()
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .focusRequester(focus),
        )

        if (query.isBlank()) {
            EmptyState(Icons.Rounded.Search, "Search your music", "Type a song, artist, album or folder name.")
            return@Column
        }
        if (results.isEmpty && results.query == query) {
            EmptyState(Icons.Rounded.SearchOff, "No results", "Nothing matches \"$query\".")
            return@Column
        }

        LazyColumn(contentPadding = PaddingValues(bottom = LocalBottomPadding.current + 8.dp)) {
            if (results.artists.isNotEmpty()) {
                item { SectionHeader("Artists") }
                items(results.artists.take(8), key = { "ar:" + it.name }) { artist ->
                    ArtistRow(artist) {
                        keyboard?.hide()
                        nav.artist(artist.name)
                    }
                }
            }
            if (results.albums.isNotEmpty()) {
                item { SectionHeader("Albums") }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(results.albums, key = { it.id }) { album ->
                            AlbumCard(album, Modifier.width(140.dp)) {
                                keyboard?.hide()
                                nav.album(album.id)
                            }
                        }
                    }
                }
            }
            if (results.folders.isNotEmpty()) {
                item { SectionHeader("Folders") }
                items(results.folders.take(10), key = { "f:" + it.key }) { folder ->
                    FolderRow(folder, subtitle = folder.displayPath.substringBeforeLast('/')) {
                        keyboard?.hide()
                        nav.folder(folder.key)
                    }
                }
            }
            if (results.songs.isNotEmpty()) {
                item { SectionHeader(pluralize(results.songs.size, "song")) }
                itemsIndexed(results.songs, key = { _, s -> "s:" + s.key }) { index, song ->
                    SongRow(
                        song = song,
                        isCurrent = playback.song?.key == song.key,
                        isPlaying = playback.isPlaying,
                        selected = selection.isSelected(song),
                        onClick = {
                            keyboard?.hide()
                            if (selection.active) selection.toggle(song)
                            else viewModel.play(results.songs, index, "Search · $query")
                        },
                        onLongClick = { selection.start(song, results.songs) },
                        onMore = { menu.open(song) },
                    )
                }
            }
        }
    }
}
