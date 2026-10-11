package com.jambo199.vividmusic.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jambo199.vividmusic.AppGraph
import com.jambo199.vividmusic.ui.LocalNav
import com.jambo199.vividmusic.ui.MainViewModel
import com.jambo199.vividmusic.ui.components.Artwork
import com.jambo199.vividmusic.ui.theme.rememberArtworkColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Car mode: just the song and three huge buttons on a dark screen that stays on, so it's easy to
 * use at a glance. Swipe anywhere to skip.
 */
@Composable
fun CarScreen(viewModel: MainViewModel) {
    val playback by viewModel.playback.collectAsState()
    val userData by viewModel.userData.collectAsState()
    val song = playback.song
    val nav = LocalNav.current
    val accent = rememberArtworkColor(song) ?: Color(0xFF64B5F6)
    val scope = rememberCoroutineScope()
    val swipe = remember { Animatable(0f) }

    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    var clock by remember { mutableStateOf(LocalTime.now()) }
    var progress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(playback.isPlaying, song?.key) {
        while (true) {
            clock = LocalTime.now()
            val duration = playback.durationMs
            progress = if (duration > 0) (viewModel.player.position().toFloat() / duration).coerceIn(0f, 1f) else 0f
            delay(1_000)
        }
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        val v = swipe.value
                        when {
                            v < -size.width * 0.2f -> viewModel.player.next()
                            v > size.width * 0.2f -> viewModel.player.previous()
                        }
                        scope.launch { swipe.animateTo(0f, spring()) }
                    },
                ) { change, amount ->
                    change.consume()
                    scope.launch { swipe.snapTo(swipe.value + amount) }
                }
            }
            .safeDrawingPadding(),
    ) {
        val landscape = maxWidth > maxHeight
        val button = if (landscape) minOf(maxHeight * 0.32f, 120.dp) else minOf(maxWidth * 0.27f, 120.dp)
        Column(Modifier.fillMaxSize().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { nav.back() }) {
                    Icon(Icons.Rounded.Close, contentDescription = "Leave car mode", tint = Color.White, modifier = Modifier.size(32.dp))
                }
                Spacer(Modifier.weight(1f))
                Text(
                    clock.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)),
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            Spacer(Modifier.height(12.dp))
            val info: @Composable (Modifier) -> Unit = { mod ->
                Row(mod.graphicsLayer { translationX = swipe.value }, verticalAlignment = Alignment.CenterVertically) {
                    Artwork(song, Modifier.size(if (landscape) 120.dp else 96.dp), shape = RoundedCornerShape(16.dp), iconSize = 48.dp)
                    Spacer(Modifier.width(20.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            song?.title ?: "Nothing playing",
                            color = Color.White,
                            fontSize = 30.sp,
                            lineHeight = 36.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            song?.displayArtist.orEmpty(),
                            color = accent,
                            fontSize = 22.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            val controls: @Composable (Modifier) -> Unit = { mod ->
                Row(mod, horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    CarButton(Icons.Rounded.SkipPrevious, "Previous", button, Color.White.copy(alpha = 0.12f), Color.White) { viewModel.player.previous() }
                    CarButton(
                        if (playback.playWhenReady) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        if (playback.playWhenReady) "Pause" else "Play",
                        button * 1.15f,
                        accent,
                        Color.Black,
                    ) { viewModel.player.togglePlayPause() }
                    CarButton(Icons.Rounded.SkipNext, "Next", button, Color.White.copy(alpha = 0.12f), Color.White) { viewModel.player.next() }
                }
            }
            if (landscape) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    info(Modifier.weight(1f))
                    Spacer(Modifier.width(16.dp))
                    controls(Modifier.weight(1f))
                }
            } else {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { info(Modifier.fillMaxWidth()) }
                controls(Modifier.fillMaxWidth())
                Spacer(Modifier.height(28.dp))
            }
            LinearProgressIndicator(
                progress = { progress },
                color = accent,
                trackColor = Color.White.copy(alpha = 0.15f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
            )
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                IconButton(onClick = { viewModel.player.toggleShuffle() }, modifier = Modifier.size(64.dp)) {
                    Icon(
                        Icons.Rounded.Shuffle,
                        contentDescription = if (playback.shuffle) "Shuffle on" else "Shuffle off",
                        tint = if (playback.shuffle) accent else Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(36.dp),
                    )
                }
                if (song != null && !song.isOnline) {
                    val favorite = song.key in userData.favoriteSet
                    IconButton(onClick = { AppGraph.userData.toggleFavorite(song.key) }, modifier = Modifier.size(64.dp)) {
                        Icon(
                            if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = if (favorite) "Remove from favourites" else "Add to favourites",
                            tint = if (favorite) Color(0xFFFF4081) else Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(36.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CarButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, size: Dp, background: Color, tint: Color, onClick: () -> Unit) {
    FilledIconButton(
        onClick = onClick,
        shape = CircleShape,
        colors = IconButtonDefaults.filledIconButtonColors(containerColor = background, contentColor = tint),
        modifier = Modifier.size(size),
    ) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(size * 0.5f))
    }
}
