package com.jambo199.vividmusic.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import com.jambo199.vividmusic.data.VisualizerStyle
import com.jambo199.vividmusic.playback.Spectrum
import kotlinx.coroutines.isActive

/** A visualizer's moving parts: band heights and the beat, eased frame by frame. */
@Stable
class SpectrumState {
    val bars = FloatArray(Spectrum.BANDS)
    internal val targets = FloatArray(Spectrum.BANDS)
    internal var beatTarget = 0f

    /** How strong the beat is right now, 0..1. */
    var beat by mutableFloatStateOf(0f)
        internal set

    /** Changes every frame, so drawing that reads it is redone. */
    var tick by mutableLongStateOf(0L)
        internal set
}

/**
 * Follows the music while [playing] (and settles to rest when paused). [speed] keeps it in time
 * when the playback speed is changed. Does nothing when not [enabled].
 */
@Composable
fun rememberSpectrum(enabled: Boolean, playing: Boolean, speed: Float): SpectrumState {
    val state = remember { SpectrumState() }
    if (!enabled) return state
    DisposableEffect(Unit) {
        Spectrum.watchers++
        onDispose { Spectrum.watchers-- }
    }
    LaunchedEffect(playing, speed) {
        while (isActive) {
            var resting = true
            withFrameNanos { frameTime ->
                val frame = if (playing) Spectrum.frameAt(System.nanoTime(), speed) else null
                if (frame != null) {
                    frame.levels.copyInto(state.targets)
                    state.beatTarget = frame.beat
                } else if (!playing) {
                    state.targets.fill(0f)
                    state.beatTarget = 0f
                }
                for (i in state.bars.indices) {
                    val t = state.targets[i]
                    val v = state.bars[i]
                    // Quick to rise, slow to fall, like a real level meter.
                    state.bars[i] = if (t > v) v + (t - v) * 0.55f else v - (v - t) * 0.14f
                    if (state.bars[i] > 0.004f) resting = false
                }
                val b = state.beat
                state.beat = if (state.beatTarget > b) state.beatTarget else b - (b - state.beatTarget) * 0.12f
                if (state.beat > 0.004f) resting = false
                state.tick = frameTime
            }
            if (!playing && resting) break
        }
    }
    return state
}

/** Draws [state] in [style] using [color]. */
@Composable
fun Visualizer(state: SpectrumState, style: VisualizerStyle, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        state.tick // redraw every frame
        val n = Spectrum.BANDS
        val slot = size.width / n
        val gap = slot * 0.3f
        val barWidth = slot - gap
        val minHeight = 2.dp.toPx()
        when (style) {
            VisualizerStyle.OFF -> Unit
            VisualizerStyle.BARS -> for (i in 0 until n) {
                val h = (state.bars[i] * size.height).coerceAtLeast(minHeight)
                drawRoundRect(
                    color = color,
                    topLeft = Offset(i * slot + gap / 2, size.height - h),
                    size = Size(barWidth, h),
                    cornerRadius = CornerRadius(barWidth / 2),
                )
            }
            VisualizerStyle.MIRROR -> for (i in 0 until n) {
                val h = (state.bars[i] * size.height).coerceAtLeast(minHeight)
                drawRoundRect(
                    color = color,
                    topLeft = Offset(i * slot + gap / 2, (size.height - h) / 2),
                    size = Size(barWidth, h),
                    cornerRadius = CornerRadius(barWidth / 2),
                )
            }
            VisualizerStyle.WAVE -> {
                val path = Path()
                path.moveTo(0f, size.height)
                var px = 0f
                var py = size.height - state.bars[0] * size.height
                path.lineTo(px, py)
                for (i in 1 until n) {
                    val x = i * size.width / (n - 1)
                    val y = size.height - state.bars[i] * size.height
                    // Smooth curve through the band tops.
                    path.quadraticTo(px, py, (px + x) / 2, (py + y) / 2)
                    px = x
                    py = y
                }
                path.lineTo(size.width, py)
                path.lineTo(size.width, size.height)
                path.close()
                drawPath(path, Brush.verticalGradient(listOf(color, color.copy(alpha = color.alpha * 0.25f))))
            }
        }
    }
}
