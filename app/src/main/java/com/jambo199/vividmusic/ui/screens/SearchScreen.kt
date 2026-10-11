package com.jambo199.vividmusic.ui.screens

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
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
    val settings by viewModel.settings.collectAsState()
    val results by viewModel.searchResults.collectAsState()
    val playback by viewModel.playback.collectAsState()
    val nav = LocalNav.current
    val menu = LocalSongMenu.current
    val selection = LocalSelection.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    // "Play Alan Walker", "shuffle my favourites", or just a name to search for.
    val listen = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (result.resultCode == Activity.RESULT_OK && !spoken.isNullOrBlank()) viewModel.voice(spoken)
    }
    val startListening = {
        keyboard?.hide()
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Say a song, artist or album – or \"play…\"")
        try {
            listen.launch(intent)
        } catch (_: ActivityNotFoundException) {
            viewModel.toast("Voice search isn't available on this phone")
        }
    }

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
                    IconButton(onClick = startListening) { Icon(Icons.Rounded.Mic, contentDescription = "Voice search") }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                keyboard?.hide()
                viewModel.rememberSearch(query)
            }),
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
            val history = settings.searchHistory
            if (history.isEmpty()) {
                EmptyState(Icons.Rounded.Search, "Search your music", "Type a song, artist, album or folder name.")
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = LocalBottomPadding.current + 8.dp)) {
                    item {
                        SectionHeader("Recent searches") {
                            TextButton(onClick = { viewModel.clearSearchHistory() }) { Text("Clear") }
                        }
                    }
                    items(history, key = { "h:$it" }) { past ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.setQuery(past) }
                                .padding(start = 16.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.History, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(16.dp))
                            Text(past, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1)
                            IconButton(onClick = { viewModel.forgetSearch(past) }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Remove $past", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
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
                        viewModel.rememberSearch(query)
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
                                viewModel.rememberSearch(query)
                                nav.album(album.id)
                            }
                        }
                    }
                }
            }
            if (results.genres.isNotEmpty()) {
                item { SectionHeader("Genres") }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(results.genres, key = { "g:" + it.name }) { genre ->
                            AssistChip(
                                onClick = {
                                    keyboard?.hide()
                                    viewModel.rememberSearch(query)
                                    nav.genre(genre.name)
                                },
                                label = { Text("${genre.name} · ${genre.songs.size}") },
                                leadingIcon = { Icon(Icons.Rounded.Category, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            )
                        }
                    }
                }
            }
            if (results.folders.isNotEmpty()) {
                item { SectionHeader("Folders") }
                items(results.folders.take(10), key = { "f:" + it.key }) { folder ->
                    FolderRow(folder, subtitle = folder.displayPath.substringBeforeLast('/')) {
                        keyboard?.hide()
                        viewModel.rememberSearch(query)
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
                            viewModel.rememberSearch(query)
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
