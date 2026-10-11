package com.jambo199.vividmusic.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jambo199.vividmusic.data.Wrapped
import com.jambo199.vividmusic.data.formatTotalDuration
import com.jambo199.vividmusic.ui.LocalNav
import com.jambo199.vividmusic.ui.MainViewModel
import com.jambo199.vividmusic.ui.components.Artwork
import com.jambo199.vividmusic.ui.components.Capturable
import com.jambo199.vividmusic.ui.components.shareLayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Gradients = listOf(
    listOf(Color(0xFF6A1B9A), Color(0xFFE91E63)),
    listOf(Color(0xFF0D47A1), Color(0xFF00BCD4)),
    listOf(Color(0xFFE65100), Color(0xFFFFC107)),
    listOf(Color(0xFF1B5E20), Color(0xFF8BC34A)),
    listOf(Color(0xFF311B92), Color(0xFF7C4DFF)),
    listOf(Color(0xFFB71C1C), Color(0xFFFF7043)),
    listOf(Color(0xFF004D40), Color(0xFF26A69A)),
    listOf(Color(0xFF1A237E), Color(0xFFE040FB)),
)

/** Your listening as a story you tap through, ending in a card you can share. */
@Composable
fun WrappedScreen(viewModel: MainViewModel) {
    val nav = LocalNav.current
    val library by viewModel.library.collectAsState()
    val data by viewModel.userData.collectAsState()
    val wrapped = remember(library, data) { Wrapped.from(library, data) }
    if (!wrapped.hasStory) {
        StoryPage(Gradients[0]) {
            Text("Your Wrapped is warming up", style = MaterialTheme.typography.headlineMedium, color = Color.White, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Text(
                "Play a few more songs and come back: your top songs, artists and listening personality will be here.",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            WhiteButton("OK") { nav.back() }
        }
        return
    }

    val pages = remember(wrapped) { pagesFor(wrapped) }
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    // Moves on by itself, like a story; tapping skips ahead or back.
    LaunchedEffect(pager.currentPage) {
        if (pager.currentPage < pages.lastIndex) {
            delay(7_000)
            pager.animateScrollToPage(pager.currentPage + 1)
        }
    }
    Box(Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pager,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(pages.size) {
                    detectTapGestures { offset ->
                        val forward = offset.x > size.width / 3
                        val target = (pager.currentPage + if (forward) 1 else -1).coerceIn(0, pages.lastIndex)
                        scope.launch { pager.animateScrollToPage(target) }
                    }
                },
        ) { page ->
            StoryPage(Gradients[page % Gradients.size]) { pages[page](wrapped) }
        }
        // Story progress at the top, and a way out.
        Column(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                pages.indices.forEach { i ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(alpha = if (i <= pager.currentPage) 0.95f else 0.35f)),
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Your Wrapped", style = MaterialTheme.typography.titleSmall, color = Color.White, modifier = Modifier.weight(1f))
                IconButton(onClick = { nav.back() }) { Icon(Icons.Rounded.Close, contentDescription = "Close", tint = Color.White) }
            }
        }
    }
}

private fun pagesFor(w: Wrapped): List<@Composable (Wrapped) -> Unit> = buildList<@Composable (Wrapped) -> Unit> {
    add { IntroPage(it) }
    add { MinutesPage(it) }
    add { TopSongPage(it) }
    if (w.topSongs.size > 1) add { TopFivePage(it) }
    if (w.topArtists.isNotEmpty()) add { TopArtistPage(it) }
    if (w.topGenre != null || w.busiestDay != null) add { GenreAndDayPage(it) }
    add { PersonalityPage(it) }
    add { SummaryPage(it) }
}

@Composable
private fun StoryPage(colors: List<Color>, content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(colors)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 28.dp, vertical = 72.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) { content() }
    }
}

@Composable
private fun Kicker(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.85f), letterSpacing = 2.sp)
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun Big(text: String) {
    Text(text, fontSize = 52.sp, fontWeight = FontWeight.Black, color = Color.White, textAlign = TextAlign.Center, lineHeight = 56.sp)
}

@Composable
private fun Line(text: String, strong: Boolean = false) {
    Text(
        text,
        style = if (strong) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
        fontWeight = if (strong) FontWeight.Bold else FontWeight.Normal,
        color = Color.White,
        textAlign = TextAlign.Center,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
    )
}

private fun sinceText(since: Long): String =
    if (since <= 0) "since you started" else "since " + SimpleDateFormat("d MMMM yyyy", Locale.getDefault()).format(Date(since))

@Composable
private fun IntroPage(w: Wrapped) {
    Kicker("Vivid Music")
    Big("Your Wrapped")
    Spacer(Modifier.height(16.dp))
    Line("Your listening ${sinceText(w.since)}, in a few taps.")
    Spacer(Modifier.height(32.dp))
    Text("Tap to continue", style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.7f))
}

@Composable
private fun MinutesPage(w: Wrapped) {
    Kicker("Time well spent")
    Big("%,d".format(w.minutes))
    Line("minutes of music", strong = true)
    Spacer(Modifier.height(16.dp))
    Line(w.comparison)
    Spacer(Modifier.height(8.dp))
    Line("${w.plays} plays of ${w.differentSongs} different songs over ${w.activeDays} days.")
}

@Composable
private fun TopSongPage(w: Wrapped) {
    val (song, plays) = w.topSongs.first()
    Kicker("Your number one song")
    Artwork(song, Modifier.size(220.dp), shape = RoundedCornerShape(24.dp), iconSize = 80.dp)
    Spacer(Modifier.height(20.dp))
    Line(song.title, strong = true)
    Line(song.displayArtist)
    Spacer(Modifier.height(8.dp))
    Line("Played $plays ${if (plays == 1) "time" else "times"}")
}

@Composable
private fun TopFivePage(w: Wrapped) {
    Kicker("Your top songs")
    w.topSongs.forEachIndexed { i, (song, plays) ->
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("${i + 1}", fontSize = 28.sp, fontWeight = FontWeight.Black, color = Color.White, modifier = Modifier.width(36.dp))
            Artwork(song, Modifier.size(52.dp), shape = RoundedCornerShape(10.dp), iconSize = 24.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(song.title, color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song.displayArtist, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text("$plays", color = Color.White, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun TopArtistPage(w: Wrapped) {
    val (artist, plays) = w.topArtists.first()
    Kicker("Your top artist")
    Artwork(artist.cover, Modifier.size(200.dp), shape = CircleShape, iconSize = 80.dp)
    Spacer(Modifier.height(20.dp))
    Big(artist.name)
    Spacer(Modifier.height(8.dp))
    Line("$plays plays")
    if (w.topArtists.size > 1) {
        Spacer(Modifier.height(16.dp))
        Line("Then " + w.topArtists.drop(1).joinToString(", ") { it.first.name })
    }
}

@Composable
private fun GenreAndDayPage(w: Wrapped) {
    w.topGenre?.let { (genre, plays) ->
        Kicker("Your sound")
        Big(genre)
        Line("$plays plays")
        Spacer(Modifier.height(36.dp))
    }
    w.busiestDay?.let { (day, ms) ->
        Kicker("Your biggest day")
        val date = runCatching { SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(day) }.getOrNull()
        Line(date?.let { SimpleDateFormat("EEEE d MMMM", Locale.getDefault()).format(it) } ?: day, strong = true)
        Line("${formatTotalDuration(ms)} of music in one day")
    }
}

@Composable
private fun PersonalityPage(w: Wrapped) {
    Kicker("Your listening personality")
    Big(w.personality.title)
    Spacer(Modifier.height(16.dp))
    Line(w.personality.description)
}

@Composable
private fun SummaryPage(w: Wrapped) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val layer = rememberGraphicsLayer()
    Capturable(layer) { SummaryCard(w) }
    Spacer(Modifier.height(24.dp))
    Button(
        onClick = { scope.launch { shareLayer(context, layer, "vivid-wrapped.png", "Share your Wrapped") } },
        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
    ) {
        Icon(Icons.Rounded.Share, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("Share")
    }
}

@Composable
private fun SummaryCard(w: Wrapped) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF12005E), Color(0xFFD500F9))))
            .padding(24.dp),
    ) {
        Text("MY VIVID WRAPPED", style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.8f), letterSpacing = 2.sp)
        Spacer(Modifier.height(12.dp))
        Text("%,d minutes".format(w.minutes), fontSize = 34.sp, fontWeight = FontWeight.Black, color = Color.White)
        Spacer(Modifier.height(16.dp))
        SummaryLine("Top song", w.topSongs.firstOrNull()?.first?.title)
        SummaryLine("Top artist", w.topArtists.firstOrNull()?.first?.name)
        SummaryLine("Top genre", w.topGenre?.first)
        SummaryLine("Plays", "${w.plays}")
        SummaryLine("I'm", w.personality.title)
    }
}

@Composable
private fun SummaryLine(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Row(Modifier.padding(vertical = 4.dp)) {
        Text(label, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.width(110.dp))
        Text(value, color = Color.White, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun WhiteButton(text: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick) { Text(text, color = Color.White) }
}
