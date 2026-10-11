package com.jambo199.vividmusic.ui.screens

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.jambo199.vividmusic.AppGraph
import com.jambo199.vividmusic.data.FunCounters
import com.jambo199.vividmusic.playback.Spectrum
import com.jambo199.vividmusic.ui.LocalNav
import com.jambo199.vividmusic.ui.MainViewModel
import com.jambo199.vividmusic.ui.components.Artwork
import com.jambo199.vividmusic.ui.components.rememberSpectrum
import com.jambo199.vividmusic.ui.theme.rememberArtworkColor
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** The light show's own memory between frames: which colour it's on and how bright the flash is. */
private class PartyShow {
    var colorIndex = 0
    var flash = 0f
    var angle = 0f
    var lastBeat = 0f
    var lastFrame = 0L

    fun step(beat: Float, bass: Float, frameNanos: Long) {
        val dt = if (lastFrame == 0L) 0.016f else ((frameNanos - lastFrame) / 1e9f).coerceIn(0f, 0.1f)
        lastFrame = frameNanos
        // A new beat: next colour, and a flash.
        if (beat > 0.55f && lastBeat < 0.35f) {
            colorIndex++
            flash = 1f
        }
        lastBeat = beat
        flash *= 1f - (dt * 5f).coerceAtMost(1f)
        angle += dt * (0.25f + bass * 1.6f)
    }
}

/**
 * Party mode: the whole screen becomes a light show to the music – beams, a ring of bars and
 * colours that change on the beat, in the colours of the song's artwork. Tap to show or hide the
 * controls. Strobe flashes are off unless you turn them on.
 */
@Composable
fun PartyScreen(viewModel: MainViewModel) {
    val playback by viewModel.playback.collectAsState()
    val song = playback.song
    val nav = LocalNav.current
    val spectrum = rememberSpectrum(enabled = true, playing = playback.isPlaying, speed = playback.speed)
    val base = rememberArtworkColor(song) ?: Color(0xFFD500F9)
    val palette = remember(base) { partyColors(base) }
    val show = remember { PartyShow() }
    var strobe by rememberSaveable { mutableStateOf(false) }
    var controls by remember { mutableStateOf(true) }

    // Full screen, and the screen stays on for the whole party.
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window
        val bars = window?.let { WindowCompat.getInsetsController(it, view) }
        view.keepScreenOn = true
        bars?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        bars?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            view.keepScreenOn = false
            bars?.show(WindowInsetsCompat.Type.systemBars())
        }
    }
    LaunchedEffect(Unit) { AppGraph.counters.add(FunCounters.PARTY) }
    // The controls tuck themselves away after a few seconds.
    LaunchedEffect(controls) {
        if (controls) {
            delay(6_000)
            controls = false
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) { detectTapGestures { controls = !controls } },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val frame = spectrum.tick // redraw every frame
            val bars = spectrum.bars
            val bass = (bars[0] + bars[1] + bars[2] + bars[3]) / 4f
            val highs = (Spectrum.BANDS - 8 until Spectrum.BANDS).sumOf { bars[it].toDouble() }.toFloat() / 8f
            show.step(spectrum.beat, bass, frame)
            val now = palette[show.colorIndex % palette.size]
            val next = palette[(show.colorIndex + 1) % palette.size]
            val center = Offset(size.width / 2, size.height * 0.42f)
            val reach = max(size.width, size.height)

            // The room's light: a hard flash on the beat with strobe on, a soft glow without.
            if (strobe) {
                drawRect(now.copy(alpha = show.flash * 0.85f))
            } else {
                drawRect(
                    Brush.radialGradient(
                        listOf(lerp(now, next, 0.3f).copy(alpha = 0.25f + 0.45f * spectrum.beat), Color.Transparent),
                        center,
                        reach * (0.55f + 0.25f * bass),
                    ),
                )
            }

            // Beams sweeping round from the middle.
            val beams = 8
            for (i in 0 until beams) {
                val a = show.angle * (if (i % 2 == 0) 1f else -0.7f) + i * (2 * PI.toFloat() / beams)
                val end = center + Offset(cos(a), sin(a)) * reach
                val c = palette[(show.colorIndex + i) % palette.size]
                drawLine(
                    Brush.linearGradient(listOf(c.copy(alpha = 0.15f + 0.55f * highs), Color.Transparent), center, end),
                    center,
                    end,
                    strokeWidth = (10 + 26 * bass) * density,
                    cap = StrokeCap.Round,
                )
            }

            // A ring of bars round the artwork, mirrored so it's symmetrical.
            val r0 = size.minDimension * 0.24f
            val count = Spectrum.BANDS * 2
            for (i in 0 until count) {
                val band = if (i < Spectrum.BANDS) i else count - 1 - i
                val level = bars[band]
                val a = -PI.toFloat() / 2 + i * (2 * PI.toFloat() / count) + show.angle * 0.2f
                val dir = Offset(cos(a), sin(a))
                val length = 6 * density + level * size.minDimension * 0.2f
                drawLine(
                    lerp(now, next, band / Spectrum.BANDS.toFloat()),
                    center + dir * r0,
                    center + dir * (r0 + length),
                    strokeWidth = 5 * density,
                    cap = StrokeCap.Round,
                )
            }
        }

        // The artwork in the middle, bumping on the beat.
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.84f),
            contentAlignment = Alignment.Center,
        ) {
            Artwork(
                song,
                Modifier
                    .size(170.dp)
                    .graphicsLayer {
                        val s = 1f + spectrum.beat * 0.1f
                        scaleX = s
                        scaleY = s
                        rotationZ = show.angle * 12f
                    },
                shape = CircleShape,
                iconSize = 64.dp,
            )
        }

        AnimatedVisibility(
            visible = controls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                IconButton(onClick = { nav.back() }, modifier = Modifier.align(Alignment.TopStart).padding(8.dp)) {
                    Icon(Icons.Rounded.Close, contentDescription = "Leave party mode", tint = Color.White)
                }
                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))))
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        song?.title ?: "Play something to start the party",
                        color = Color.White,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    song?.let {
                        Text(it.displayArtist, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.titleMedium, maxLines = 1)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { viewModel.player.previous() }) {
                            Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous", tint = Color.White, modifier = Modifier.size(36.dp))
                        }
                        IconButton(onClick = { viewModel.player.togglePlayPause() }, modifier = Modifier.size(64.dp)) {
                            Icon(
                                if (playback.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (playback.isPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(52.dp),
                            )
                        }
                        IconButton(onClick = { viewModel.player.next() }) {
                            Icon(Icons.Rounded.SkipNext, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(36.dp))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    FilterChip(
                        selected = strobe,
                        onClick = { strobe = !strobe },
                        label = { Text(if (strobe) "Strobe on · flashing lights" else "Strobe (flashing lights)") },
                        colors = FilterChipDefaults.filterChipColors(labelColor = Color.White),
                    )
                }
            }
        }
    }
}

/** Five bright colours around the artwork's own, for the lights to step through. */
private fun partyColors(base: Color): List<Color> {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(base.toArgb(), hsv)
    return listOf(0f, 72f, 144f, 216f, 288f).map { shift ->
        Color.hsv((hsv[0] + shift) % 360f, hsv[1].coerceIn(0.65f, 1f), 1f)
    }
}
