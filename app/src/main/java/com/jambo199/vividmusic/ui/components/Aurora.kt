package com.jambo199.vividmusic.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Soft clouds of colour drifting slowly behind the player, in shades of the artwork's colour.
 * They drift while music plays and hold still when it's paused.
 */
@Composable
fun AuroraBackground(color: Color, playing: Boolean, modifier: Modifier = Modifier) {
    val colors = remember(color) { auroraColors(color) }
    var time by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(playing) {
        if (!playing) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                time += (now - last) / 1_000_000_000f
                last = now
            }
        }
    }
    Canvas(modifier.fillMaxSize()) {
        drawRect(Color(0xFF07070D))
        val radius = max(size.width, size.height) * 0.6f
        colors.forEachIndexed { i, c ->
            // Each cloud loops on its own slow figure-of-eight.
            val speed = 0.07f + i * 0.023f
            val phase = i * 2.1f
            val center = Offset(
                size.width * (0.5f + 0.42f * sin(time * speed + phase)),
                size.height * (0.38f + 0.3f * cos(time * speed * 1.3f + phase * 0.7f)),
            )
            drawCircle(
                Brush.radialGradient(listOf(c.copy(alpha = 0.62f), c.copy(alpha = 0.18f), Color.Transparent), center, radius),
                radius = radius,
                center = center,
            )
        }
        // Darker towards the bottom, where the controls are.
        drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.15f), Color.Black.copy(alpha = 0.6f))))
    }
}

/** The artwork's colour and two neighbours on the colour wheel, made bright enough to glow. */
internal fun auroraColors(base: Color): List<Color> {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(base.toArgb(), hsv)
    val s = hsv[1].coerceIn(0.45f, 0.9f)
    val v = hsv[2].coerceIn(0.7f, 1f)
    return listOf(0f, 40f, -55f).map { shift ->
        Color.hsv((hsv[0] + shift + 360f) % 360f, s, v)
    }
}
