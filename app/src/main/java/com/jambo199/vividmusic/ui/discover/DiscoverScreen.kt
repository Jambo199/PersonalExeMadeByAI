package com.jambo199.vividmusic.ui.discover

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jambo199.vividmusic.data.Song
import com.jambo199.vividmusic.ui.LocalNav
import com.jambo199.vividmusic.ui.MainViewModel
import com.jambo199.vividmusic.ui.components.Artwork
import com.jambo199.vividmusic.ui.theme.rememberArtworkColor
import kotlinx.coroutines.launch
import kotlin.math.abs

/** "Swipe to discover": right to keep a song, left to pass. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(main: MainViewModel) {
    val discover: DiscoverViewModel = viewModel()
    val state by discover.state.collectAsState()
    val nav = LocalNav.current

    // The preview stops while the app is in the background.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> discover.onStop()
                Lifecycle.Event.ON_START -> discover.onStart()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Swipe to discover") },
            navigationIcon = {
                IconButton(onClick = { nav.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
            },
            actions = {
                IconButton(onClick = { discover.toggleSound() }) {
                    Icon(
                        if (state.sound) Icons.AutoMirrored.Rounded.VolumeUp else Icons.AutoMirrored.Rounded.VolumeOff,
                        contentDescription = if (state.sound) "Mute previews" else "Play previews",
                    )
                }
            },
        )
        Row(
            Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DiscoverSource.entries.forEach { source ->
                FilterChip(
                    selected = state.source == source,
                    onClick = { if (state.source != source) discover.deal(source) },
                    label = { Text(source.label) },
                )
            }
        }
        Box(Modifier.weight(1f)) {
            when {
                state.loading -> Centered { Text("Shuffling the deck…", style = MaterialTheme.typography.titleMedium) }
                state.finished -> Finished(state, main, onAgain = { discover.deal() })
                else -> Deck(state, onKeep = discover::keep, onPass = discover::pass)
            }
            state.message?.takeIf { !state.finished }?.let { message ->
                Snackbar(
                    action = { TextButton(onClick = discover::dismissMessage) { Text("OK") } },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                ) { Text(message) }
            }
        }
    }
}

@Composable
private fun Deck(state: DiscoverState, onKeep: () -> Unit, onPass: () -> Unit) {
    val song = state.current ?: return
    val scope = rememberCoroutineScope()
    val online = song.isOnline
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width = with(LocalDensity.current) { maxWidth.toPx() }
        // One set of offsets per card, so a new card starts in the middle.
        key(song.key) {
            val offsetX = remember { Animatable(0f) }
            val offsetY = remember { Animatable(0f) }
            var flinging by remember { mutableStateOf(false) }
            fun fling(keep: Boolean) {
                if (flinging) return
                flinging = true
                scope.launch {
                    launch { offsetY.animateTo(offsetY.value + 80f, tween(220)) }
                    offsetX.animateTo(if (keep) width * 1.5f else -width * 1.5f, tween(220))
                    if (keep) onKeep() else onPass()
                }
            }
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    // As big as fits: the artwork's square plus room for the title underneath.
                    val art = minOf(maxWidth - 32.dp, maxHeight - 130.dp).coerceAtLeast(96.dp)
                    // The next card waits underneath, a little smaller.
                    state.upNext?.let { next ->
                        SongCard(
                            next,
                            art,
                            Modifier.graphicsLayer {
                                val progress = (abs(offsetX.value) / (width * 0.5f)).coerceIn(0f, 1f)
                                val scale = 0.9f + 0.1f * progress
                                scaleX = scale
                                scaleY = scale
                                alpha = 0.6f + 0.4f * progress
                            },
                        )
                    }
                    SongCard(
                        song,
                        art,
                        Modifier
                            .graphicsLayer {
                                translationX = offsetX.value
                                translationY = offsetY.value
                                rotationZ = offsetX.value / 40f
                            }
                            .pointerInput(song.key) {
                                detectDragGestures(
                                    onDragEnd = {
                                        when {
                                            offsetX.value > width * 0.28f -> fling(keep = true)
                                            offsetX.value < -width * 0.28f -> fling(keep = false)
                                            else -> scope.launch {
                                                launch { offsetY.animateTo(0f, spring()) }
                                                offsetX.animateTo(0f, spring())
                                            }
                                        }
                                    },
                                ) { change, drag ->
                                    change.consume()
                                    scope.launch {
                                        offsetX.snapTo(offsetX.value + drag.x)
                                        offsetY.snapTo(offsetY.value + drag.y)
                                    }
                                }
                            },
                        stamp = (offsetX.value / (width * 0.28f)).coerceIn(-1f, 1f),
                        keepLabel = if (online) "SAVE" else "LOVE",
                    )
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(40.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilledIconButton(
                        onClick = { fling(keep = false) },
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
                        modifier = Modifier.size(68.dp),
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = "Pass", tint = Color(0xFFE53935), modifier = Modifier.size(34.dp))
                    }
                    Text(
                        "${state.index + 1} / ${state.cards.size}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FilledIconButton(
                        onClick = { fling(keep = true) },
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFFE91E63)),
                        modifier = Modifier.size(68.dp),
                    ) {
                        Icon(
                            Icons.Rounded.Favorite,
                            contentDescription = if (online) "Save to phone" else "Love it",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    if (online) "Swipe right to save it to your phone, left to pass" else "Swipe right to add it to your favourites, left to pass",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** A song as a big card in its artwork's colours; [stamp] shows LOVE (1) or NOPE (-1) while dragging. */
@Composable
private fun SongCard(song: Song, art: Dp, modifier: Modifier = Modifier, stamp: Float = 0f, keepLabel: String = "LOVE") {
    val color = rememberArtworkColor(song) ?: MaterialTheme.colorScheme.primary
    Box(
        modifier
            .width(art + 32.dp)
            .shadow(12.dp, RoundedCornerShape(28.dp))
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.verticalGradient(listOf(color, lerp(color, Color.Black, 0.8f)))),
    ) {
        Column(Modifier.padding(16.dp)) {
            Artwork(
                song,
                Modifier.size(art),
                shape = RoundedCornerShape(20.dp),
                iconSize = 72.dp,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                song.title,
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOf(song.displayArtist, song.year.takeIf { it > 0 }?.toString()).filterNotNull().joinToString(" · "),
                color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (song.genre.isNotBlank()) {
                Text(song.genre, color = Color.White.copy(alpha = 0.65f), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            }
        }
        if (stamp > 0.05f) Stamp(keepLabel, Color(0xFF00E676), stamp, Modifier.align(Alignment.TopStart), -14f)
        if (stamp < -0.05f) Stamp("NOPE", Color(0xFFFF1744), -stamp, Modifier.align(Alignment.TopEnd), 14f)
    }
}

@Composable
private fun Stamp(text: String, color: Color, strength: Float, modifier: Modifier, angle: Float) {
    Text(
        text,
        color = color,
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Black,
        modifier = modifier
            .padding(28.dp)
            .graphicsLayer {
                rotationZ = angle
                alpha = strength
            }
            .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

@Composable
private fun Finished(state: DiscoverState, main: MainViewModel, onAgain: () -> Unit) {
    if (state.cards.isEmpty()) {
        Centered {
            Icon(Icons.Rounded.Style, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            Text(state.message ?: "Nothing to discover right now.", textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
        }
        return
    }
    val online = state.source == DiscoverSource.NCS
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(110.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Color(0xFFE91E63), Color(0xFFFF9800)))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Favorite, contentDescription = null, tint = Color.White, modifier = Modifier.size(52.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(
            when (state.loved.size) {
                0 -> "Nothing grabbed you this time"
                1 -> if (online) "1 song saved" else "1 new favourite"
                else -> if (online) "${state.loved.size} songs saved" else "${state.loved.size} new favourites"
            },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(
            if (online) "Saved songs join your library in Music › NCS once they've downloaded."
            else "Passed on ${state.passed}. They'll stay out of the deck for two months.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        state.loved.take(8).forEach { song ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Artwork(song, Modifier.size(44.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                    Text(song.displayArtist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        if (state.loved.isNotEmpty()) {
            Button(onClick = { main.play(state.loved, 0, if (online) "Discovered on NCS" else "New favourites") }, modifier = Modifier.fillMaxWidth()) {
                Text(if (state.loved.size == 1) "Play it" else "Play them")
            }
            Spacer(Modifier.height(8.dp))
        }
        OutlinedButton(onClick = onAgain, modifier = Modifier.fillMaxWidth()) { Text("Deal more cards") }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) { content() }
}
