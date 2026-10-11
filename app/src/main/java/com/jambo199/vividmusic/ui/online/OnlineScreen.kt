package com.jambo199.vividmusic.ui.online

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jambo199.vividmusic.online.ArchiveItem
import com.jambo199.vividmusic.online.InternetArchive
import com.jambo199.vividmusic.online.Load
import com.jambo199.vividmusic.online.Podcast
import com.jambo199.vividmusic.ui.LocalNav
import com.jambo199.vividmusic.ui.MainViewModel
import com.jambo199.vividmusic.ui.components.LocalBottomPadding

private val TABS = listOf("Free music", "Podcasts")

/** Music from the internet: free albums and concerts, and podcasts. No ads, no account. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnlineScreen(viewModel: MainViewModel) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.surface, divider = {}) {
            TABS.forEachIndexed { i, title ->
                Tab(
                    selected = tab == i,
                    onClick = { tab = i },
                    text = { Text(title, maxLines = 1, fontWeight = if (tab == i) FontWeight.SemiBold else FontWeight.Normal) },
                )
            }
        }
        when (tab) {
            0 -> FreeMusicPage()
            else -> PodcastsPage()
        }
    }
}

@Composable
private fun FreeMusicPage() {
    val online = onlineViewModel()
    val nav = LocalNav.current
    val shelves by online.shelves.collectAsState()
    val search by online.archiveSearch.collectAsState()
    val bottom = LocalBottomPadding.current
    LaunchedEffect(Unit) { online.startArchive() }

    LazyColumn(contentPadding = PaddingValues(bottom = bottom + 16.dp)) {
        item(key = "search") {
            OnlineSearchField("Search free albums and concerts", search?.first.orEmpty()) { online.searchArchive(it) }
        }
        val s = search
        if (s != null) {
            item(key = "results-title") { SectionTitle("Results for \"${s.first}\"") }
            when (val load = s.second) {
                Load.Loading -> item(key = "loading") { LoadingRow() }
                is Load.Failed -> item(key = "error") { ErrorRow(load.message) { online.searchArchive(s.first) } }
                is Load.Ready -> {
                    if (load.data.isEmpty()) {
                        item(key = "none") { Credit("Nothing found. Try a band name, a city or a style.") }
                    }
                    items(load.data, key = { "res" + it.id }) { item ->
                        ArchiveRow(item) { nav.archive(item.id) }
                    }
                }
            }
        } else {
            InternetArchive.Shelf.entries.forEach { shelf ->
                item(key = "t" + shelf.name) { SectionTitle(shelf.title, shelf.subtitle) }
                item(key = "s" + shelf.name) {
                    when (val load = shelves[shelf] ?: Load.Loading) {
                        Load.Loading -> LoadingRow()
                        is Load.Failed -> ErrorRow(load.message) { online.startArchive() }
                        is Load.Ready -> CoverShelf(load.data, key = { it.id }) { item ->
                            CoverCard(item.cover, item.title, listOf(item.creator, item.year).filter { it.isNotBlank() }.joinToString(" · ")) {
                                nav.archive(item.id)
                            }
                        }
                    }
                }
            }
        }
        item(key = "credit") {
            Credit(
                "Free and legal: concerts from bands that allow sharing them (the Live Music Archive) and " +
                    "Creative Commons netlabel releases, from the Internet Archive. Downloads go to Music › Vivid Music.",
            )
        }
    }
}

@Composable
fun ArchiveRow(item: ArchiveItem, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RemoteImage(item.cover, item.title, Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                listOf(item.creator, item.year).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PodcastsPage() {
    val online = onlineViewModel()
    val nav = LocalNav.current
    val data by online.data.collectAsState()
    val top by online.topPodcasts.collectAsState()
    val search by online.podcastSearch.collectAsState()
    val bottom = LocalBottomPadding.current
    LaunchedEffect(Unit) { online.startPodcasts() }

    LazyColumn(contentPadding = PaddingValues(bottom = bottom + 16.dp)) {
        item(key = "search") {
            OnlineSearchField("Search podcasts", search?.first.orEmpty()) { online.searchPodcasts(it) }
        }
        val s = search
        if (s != null) {
            item(key = "results-title") { SectionTitle("Results for \"${s.first}\"") }
            when (val load = s.second) {
                Load.Loading -> item(key = "loading") { LoadingRow() }
                is Load.Failed -> item(key = "error") { ErrorRow(load.message) { online.searchPodcasts(s.first) } }
                is Load.Ready -> {
                    if (load.data.isEmpty()) item(key = "none") { Credit("No podcasts found.") }
                    items(load.data, key = { "res" + it.feedUrl }) { p -> PodcastRow(p) { nav.podcast(p.feedUrl) } }
                }
            }
        } else {
            if (data.podcasts.isNotEmpty()) {
                item(key = "subs-title") { SectionTitle("Your podcasts") }
                item(key = "subs") {
                    CoverShelf(data.podcasts, key = { it.feedUrl }) { p ->
                        CoverCard(p.artwork, p.title, p.author, width = 132.dp) { nav.podcast(p.feedUrl) }
                    }
                }
            }
            item(key = "top-title") { SectionTitle("Top podcasts", "Popular right now") }
            when (val load = top) {
                Load.Loading -> item(key = "loading") { LoadingRow() }
                is Load.Failed -> item(key = "error") { ErrorRow(load.message) { online.startPodcasts() } }
                is Load.Ready -> items(load.data, key = { "top" + it.feedUrl }) { p -> PodcastRow(p) { nav.podcast(p.feedUrl) } }
            }
        }
        item(key = "credit") { Credit("Podcast search by Apple Podcasts' public directory; episodes come straight from each show.") }
    }
}

@Composable
fun PodcastRow(podcast: Podcast, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RemoteImage(podcast.artwork, podcast.title, Modifier.size(56.dp).clip(RoundedCornerShape(10.dp))) {
            Icon(Icons.Rounded.Podcasts, contentDescription = null, tint = Color.White)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(podcast.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                podcast.author,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun Credit(text: String) {
    Row(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.Top) {
        Icon(
            Icons.Rounded.Public,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(16.dp)
                .padding(top = 2.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
