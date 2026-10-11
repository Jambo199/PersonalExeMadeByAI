package com.jambo199.vividmusic.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/**
 * A seek bar whose played part is a gentle wave that ripples while music plays and smooths out
 * when it's paused; the rest is a straight line. Tap or drag anywhere on it to seek.
 * [onSeeking] reports the place while dragging, [onSeek] the place let go at.
 */
@Composable
fun WavySeekBar(
    progress: Float,
    playing: Boolean,
    activeColor: Color,
    trackColor: Color,
    onSeeking: (Float) -> Unit,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 32.dp,
) {
    val seeking by rememberUpdatedState(onSeeking)
    val seek by rememberUpdatedState(onSeek)
    var dragging by remember { mutableFloatStateOf(-1f) }
    // The wave settles flat when paused (and while you drag, so it's easy to aim).
    val amplitude by animateFloatAsState(if (playing && dragging < 0f) 1f else 0f, tween(400), label = "waveHeight")
    var phase by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(playing) {
        if (!playing) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                phase += (now - last) / 1e9f * 2.4f
                last = now
            }
        }
    }
    val thumbScale by animateFloatAsState(if (dragging >= 0f) 1.4f else 1f, label = "thumb")
    val shown = if (dragging >= 0f) dragging else progress.coerceIn(0f, 1f)

    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(shown, 0f..1f)
                setProgress { value ->
                    seek(value.coerceIn(0f, 1f))
                    true
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { offset -> seek((offset.x / size.width).coerceIn(0f, 1f)) }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        dragging = (offset.x / size.width).coerceIn(0f, 1f)
                        seeking(dragging)
                    },
                    onDragEnd = {
                        val at = dragging
                        dragging = -1f
                        if (at >= 0f) seek(at)
                    },
                    onDragCancel = { dragging = -1f },
                ) { change, amount ->
                    change.consume()
                    dragging = (dragging + amount / size.width).coerceIn(0f, 1f)
                    seeking(dragging)
                }
            },
    ) {
        val stroke = 4.dp.toPx()
        val thumb = 7.dp.toPx() * thumbScale
        val left = thumb
        val right = size.width - thumb
        val y = size.height / 2
        val x = left + (right - left) * shown
        // What's still to come: a straight, faint line.
        if (x < right) {
            drawLine(trackColor, Offset(x + thumb + 3.dp.toPx(), y), Offset(right, y), stroke, StrokeCap.Round)
        }
        // What's been played: a wave (or a line, when it's flat).
        if (x > left) {
            val wave = 3.5.dp.toPx() * amplitude
            val length = 26.dp.toPx()
            val path = Path()
            path.moveTo(left, y)
            var px = left
            val step = 2f
            while (px < x - thumb * 0.5f) {
                px = minOf(px + step, x - thumb * 0.5f)
                val k = (px - left) / length * 2 * PI.toFloat() - phase
                path.lineTo(px, y + sin(k) * wave)
            }
            drawPath(path, activeColor, style = Stroke(width = stroke, cap = StrokeCap.Round))
        }
        drawCircle(activeColor, radius = thumb, center = Offset(x, y))
    }
}
