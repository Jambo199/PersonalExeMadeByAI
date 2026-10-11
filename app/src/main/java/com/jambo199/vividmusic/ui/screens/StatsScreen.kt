package com.jambo199.vividmusic.ui.screens

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jambo199.vividmusic.data.Achievements
import com.jambo199.vividmusic.data.ListeningClock
import com.jambo199.vividmusic.data.ListeningStats
import com.jambo199.vividmusic.data.UserDataStore
import com.jambo199.vividmusic.data.formatSize
import com.jambo199.vividmusic.data.formatTotalDuration
import com.jambo199.vividmusic.data.pluralize
import com.jambo199.vividmusic.ui.LocalNav
import com.jambo199.vividmusic.ui.LocalSongMenu
import com.jambo199.vividmusic.ui.MainViewModel
import com.jambo199.vividmusic.ui.components.Artwork
import com.jambo199.vividmusic.ui.components.BadgeGrid
import com.jambo199.vividmusic.ui.components.LocalBottomPadding
import com.jambo199.vividmusic.ui.components.SectionHeader
import com.jambo199.vividmusic.ui.components.SongRow
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val DAY_MS = 24L * 60 * 60 * 1000
private val BAR_HEIGHT = 96.dp

/** Your listening: time, plays, favourites, a week chart and what you play most. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StatsScreen(viewModel: MainViewModel) {
    val library by viewModel.library.collectAsState()
    val data by viewModel.userData.collectAsState()
    val playback by viewModel.playback.collectAsState()
    val nav = LocalNav.current
    val menu = LocalSongMenu.current
    val bottom = LocalBottomPadding.current
    var confirmClear by remember { mutableStateOf(false) }
    val stats = remember(library, data) { ListeningStats.from(library, data) }
    val context = LocalContext.current
    val achievements = remember(library, data) {
        val quiz = context.getSharedPreferences("quiz", Context.MODE_PRIVATE)
        val quizBest = quiz.all.values.filterIsInstance<Int>().maxOrNull() ?: 0
        Achievements.from(library, data, quizBest)
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Your listening") },
            navigationIcon = {
                IconButton(onClick = { nav.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
            },
        )
        LazyColumn(contentPadding = PaddingValues(bottom = bottom + 16.dp)) {
            item(key = "summary") {
                Column(
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Button(onClick = { nav.wrapped() }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("See your Wrapped")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatCard(formatTotalDuration(stats.listenedMs), "listened", Modifier.weight(1f))
                        StatCard(stats.plays.toString(), if (stats.plays == 1) "play" else "plays", Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatCard(stats.songsPlayed.toString(), "different songs", Modifier.weight(1f))
                        StatCard(stats.favorites.toString(), if (stats.favorites == 1) "favourite" else "favourites", Modifier.weight(1f))
                    }
                    if (data.bestStreak > 0) {
                        val now = System.currentTimeMillis()
                        val streak = data.currentStreak(UserDataStore.dayKey(now), UserDataStore.dayKey(now - DAY_MS))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            StatCard(streak.toString(), if (streak == 1) "day in a row" else "days in a row", Modifier.weight(1f))
                            StatCard(data.bestStreak.toString(), "best streak (days)", Modifier.weight(1f))
                        }
                    }
                    if (stats.since > 0) {
                        Text(
                            "Since ${DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(stats.since))}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item(key = "week") { WeekChart(data.dailyMs) }
            if (ListeningClock.peakHour(data.hourMs) != null) {
                item(key = "clock") { ListeningClockCard(data.hourMs) }
            }
            item(key = "badges-title") {
                val unlocked = achievements.count { it.unlocked }
                SectionHeader("Badges · $unlocked of ${achievements.size}")
            }
            item(key = "badges") { BadgeGrid(achievements, Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }

            if (stats.topSongs.isNotEmpty()) {
                item(key = "songs-title") { SectionHeader("Most played songs") }
                val songs = stats.topSongs.map { it.first }
                itemsIndexed(stats.topSongs, key = { _, e -> "s" + e.first.key }) { i, (song, plays) ->
                    SongRow(
                        song = song,
                        onClick = { viewModel.play(songs, i, "Most played") },
                        isCurrent = playback.song?.key == song.key,
                        isPlaying = playback.isPlaying,
                        leadingNumber = i + 1,
                        subtitle = "${song.displayArtist} • ${pluralize(plays, "play")}",
                        onMore = { menu.open(song) },
                    )
                }
            }
            if (stats.topArtists.isNotEmpty()) {
                item(key = "artists-title") { SectionHeader("Top artists") }
                items(stats.topArtists, key = { "a" + it.first.name }) { (artist, plays) ->
                    RankRow(
                        title = artist.name,
                        subtitle = pluralize(plays, "play"),
                        onClick = { nav.artist(artist.name) },
                    ) {
                        Artwork(artist.cover, Modifier.size(48.dp), CircleShape, placeholderIcon = Icons.Rounded.Person)
                    }
                }
            }
            if (stats.topAlbums.isNotEmpty()) {
                item(key = "albums-title") { SectionHeader("Top albums") }
                items(stats.topAlbums, key = { "al" + it.first.id }) { (album, plays) ->
                    RankRow(
                        title = album.title,
                        subtitle = "${album.artist} • ${pluralize(plays, "play")}",
                        onClick = { nav.album(album.id) },
                    ) {
                        Artwork(album.cover, Modifier.size(48.dp), RoundedCornerShape(8.dp), placeholderIcon = Icons.Rounded.Album)
                    }
                }
            }
            if (stats.topSongs.isEmpty()) {
                item(key = "nothing") {
                    Text(
                        "Play some music and your favourite songs, artists and albums show up here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }

            item(key = "library-title") { SectionHeader("Your library") }
            item(key = "library") {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LibraryLine("Songs", stats.songCount.toString())
                    LibraryLine("Albums", stats.albumCount.toString())
                    LibraryLine("Artists", stats.artistCount.toString())
                    LibraryLine("Folders with music", stats.folderCount.toString())
                    LibraryLine("Total length", formatTotalDuration(stats.libraryDurationMs))
                    LibraryLine("Space used", formatSize(stats.librarySizeBytes))
                    if (stats.formats.isNotEmpty()) {
                        FlowRow(
                            Modifier.padding(top = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            stats.formats.forEach { (format, count) ->
                                Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                                    Text(
                                        "$format · $count",
                                        style = MaterialTheme.typography.labelLarge,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (stats.plays > 0 || stats.listenedMs > 0) {
                item(key = "clear") {
                    OutlinedButton(onClick = { confirmClear = true }, modifier = Modifier.padding(16.dp)) {
                        Text("Clear listening history")
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear listening history?") },
            text = { Text("Play counts, recently played and listening time are reset. Playlists and favourites stay.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    viewModel.clearHistory()
                }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun StatCard(value: String, label: String, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** When in the day you listen, as a 24-hour dial, and what that makes you. */
@Composable
private fun ListeningClockCard(hourMs: List<Long>) {
    val peak = ListeningClock.peakHour(hourMs) ?: return
    val type = ListeningClock.timeOfDay(peak)
    val primary = MaterialTheme.colorScheme.primary
    val faint = MaterialTheme.colorScheme.onSurfaceVariant
    val labels = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = faint)
    Surface(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            // A 24-hour dial: midnight at the top, each bar one hour.
            Canvas(Modifier.size(132.dp)) {
                val max = hourMs.maxOrNull()?.coerceAtLeast(1L) ?: 1L
                val c = center
                val inner = size.minDimension * 0.2f
                val outer = size.minDimension / 2 - 12.dp.toPx()
                drawCircle(faint.copy(alpha = 0.25f), radius = inner, center = c, style = Stroke(1.dp.toPx()))
                for (h in 0 until 24) {
                    val a = (h / 24f) * 2 * PI.toFloat() - PI.toFloat() / 2
                    val dir = Offset(cos(a), sin(a))
                    val level = hourMs.getOrElse(h) { 0L }.toFloat() / max
                    drawLine(
                        if (h == peak) primary else primary.copy(alpha = 0.35f + 0.4f * level),
                        c + dir * inner,
                        c + dir * (inner + 3.dp.toPx() + (outer - inner) * level),
                        strokeWidth = 5.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
                listOf(0 to "0", 6 to "6", 12 to "12", 18 to "18").forEach { (h, text) ->
                    val a = (h / 24f) * 2 * PI.toFloat() - PI.toFloat() / 2
                    val layout = labels.measure(text, labelStyle)
                    val at = c + Offset(cos(a), sin(a)) * (outer + 6.dp.toPx())
                    drawText(layout, topLeft = at - Offset(layout.size.width / 2f, layout.size.height / 2f))
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("Your listening clock", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(type.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = primary)
                Text(type.description, style = MaterialTheme.typography.bodyMedium, color = faint)
                Spacer(Modifier.height(4.dp))
                Text("Most often around ${ListeningClock.hourLabel(peak)}", style = MaterialTheme.typography.bodySmall, color = faint)
            }
        }
    }
}

/** Bars for each of the last seven days, today on the right. */
@Composable
private fun WeekChart(dailyMs: Map<String, Long>) {
    val now = remember { System.currentTimeMillis() }
    val dayName = remember { SimpleDateFormat("EEE", Locale.getDefault()) }
    val days = remember(dailyMs) {
        (6 downTo 0).map { back ->
            val t = now - back * DAY_MS
            dayName.format(Date(t)) to (dailyMs[UserDataStore.dayKey(t)] ?: 0L)
        }
    }
    val max = days.maxOf { it.second }.coerceAtLeast(1L)
    val week = days.sumOf { it.second }
    Surface(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Last 7 days", style = MaterialTheme.typography.titleMedium)
            Text(
                formatTotalDuration(week),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                days.forEachIndexed { i, (label, ms) ->
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                    ) {
                        val today = i == days.lastIndex
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(BAR_HEIGHT * (ms.toFloat() / max).coerceAtLeast(0.03f))
                                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                .background(
                                    if (today) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                                ),
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (today) FontWeight.Bold else FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RankRow(title: String, subtitle: String, onClick: () -> Unit, image: @Composable () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        image()
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun LibraryLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
    }
}
