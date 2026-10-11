package com.jambo199.vividmusic.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private class Piece(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var angle: Float,
    val spin: Float,
    val color: Color,
    val w: Float,
    val h: Float,
    /** Pieces flutter side to side as they fall. */
    val wobble: Float,
)

val ConfettiColors = listOf(
    Color(0xFFFF1744), Color(0xFFFFEA00), Color(0xFF00E676), Color(0xFF2979FF),
    Color(0xFFD500F9), Color(0xFFFF9100), Color(0xFF18FFFF),
)

/**
 * A burst of confetti from the top of the screen each time [burst] changes to a new value
 * (nothing while it's null). It falls through and is gone after a few seconds.
 */
@Composable
fun Confetti(burst: Any?, modifier: Modifier = Modifier, colors: List<Color> = ConfettiColors) {
    val pieces = remember { ArrayList<Piece>() }
    var frame by remember { mutableLongStateOf(0L) }
    var area by remember { mutableLongStateOf(0L) }
    // Sizes and speeds below are in dp, so it looks the same on every screen.
    val d = LocalDensity.current.density
    LaunchedEffect(burst) {
        if (burst == null) return@LaunchedEffect
        // Wait for the first layout so the pieces know how wide the screen is.
        while (area == 0L) withFrameNanos { }
        val width = (area shr 32).toFloat()
        val random = Random(System.nanoTime())
        pieces.clear()
        repeat(150) {
            val speed = (120f + random.nextFloat() * 360f) * d
            val dir = Math.toRadians(60.0 + random.nextDouble() * 60.0)
            pieces += Piece(
                x = width / 2 + (random.nextFloat() - 0.5f) * width * 0.3f,
                y = -8f * d,
                vx = (cos(dir) * speed).toFloat() * if (random.nextBoolean()) 1 else -1,
                vy = (sin(dir) * speed * 0.6f).toFloat(),
                angle = random.nextFloat() * 360f,
                spin = (random.nextFloat() - 0.5f) * 720f,
                color = colors[random.nextInt(colors.size)],
                w = (6f + random.nextFloat() * 5f) * d,
                h = (3f + random.nextFloat() * 3f) * d,
                wobble = random.nextFloat() * 6f,
            )
        }
        var last = withFrameNanos { it }
        while (pieces.isNotEmpty()) {
            withFrameNanos { now ->
                val dt = ((now - last) / 1e9f).coerceAtMost(0.05f)
                last = now
                val height = (area and 0xffffffffL).toFloat()
                val iterator = pieces.iterator()
                while (iterator.hasNext()) {
                    val p = iterator.next()
                    p.vy += 360f * d * dt
                    // Air slows them down, so they float rather than drop.
                    p.vx *= 1f - 1.6f * dt
                    p.vy = p.vy.coerceAtMost(210f * d)
                    p.x += (p.vx + sin(p.y / (16f * d) + p.wobble) * 24f * d) * dt
                    p.y += p.vy * dt
                    p.angle += p.spin * dt
                    if (p.y > height + 16f * d) iterator.remove()
                }
                frame = now
            }
        }
    }
    Canvas(modifier.fillMaxSize().onSizeChanged { area = (it.width.toLong() shl 32) or it.height.toLong() }) {
        frame // redraw every frame
        for (p in pieces) {
            rotate(p.angle, Offset(p.x, p.y)) {
                drawRect(p.color, Offset(p.x - p.w / 2, p.y - p.h / 2), Size(p.w, p.h))
            }
        }
    }
}
