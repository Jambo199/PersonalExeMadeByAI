package com.jambo199.vividmusic.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jambo199.vividmusic.playback.PlaybackState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Compact player pinned above the bottom of every library screen. Swipe sideways to skip, up to open. */
@Composable
fun MiniPlayer(
    state: PlaybackState,
    position: () -> Long,
    onOpen: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val song = state.song ?: return
    val scope = rememberCoroutineScope()
    val open by rememberUpdatedState(onOpen)
    val offsetX = remember { Animatable(0f) }
    LaunchedEffect(song.key) { offsetX.snapTo(0f) }

    val progress by produceState(0f, state.isPlaying, state.durationMs, song.key) {
        while (true) {
            value = if (state.durationMs > 0) (position().toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f
            delay(if (state.isPlaying) 500 else 2000)
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
    ) {
        Column {
            Row(
                modifier = Modifier
                    .clickable(onClick = onOpen)
                    .pointerInput(Unit) {
                        val threshold = 36.dp.toPx()
                        var dragged = 0f
                        detectVerticalDragGestures(
                            onDragStart = { dragged = 0f },
                            onDragEnd = { if (dragged < -threshold) open() },
                            onVerticalDrag = { change, amount ->
                                change.consume()
                                dragged += amount
                            },
                        )
                    }
                    .pointerInput(song.key) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                val x = offsetX.value
                                scope.launch {
                                    when {
                                        x < -140f -> onNext()
                                        x > 140f -> onPrevious()
                                    }
                                    offsetX.animateTo(0f)
                                }
                            },
                            onHorizontalDrag = { change, amount ->
                                change.consume()
                                scope.launch { offsetX.snapTo(offsetX.value + amount) }
                            },
                        )
                    }
                    .padding(start = 8.dp, end = 4.dp, top = 8.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    Modifier
                        .weight(1f)
                        .graphicsLayer {
                            translationX = offsetX.value
                            alpha = 1f - (abs(offsetX.value) / 600f).coerceIn(0f, 0.7f)
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Artwork(song, Modifier.size(46.dp), RoundedCornerShape(10.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            song.title,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            song.displayArtist,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                IconButton(onClick = onPlayPause) {
                    AnimatedContent(
                        targetState = state.playWhenReady,
                        transitionSpec = { (scaleIn() + fadeIn()) togetherWith fadeOut() },
                        label = "playPause",
                    ) { playing ->
                        Icon(
                            if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (playing) "Pause" else "Play",
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }
                IconButton(onClick = onNext, enabled = state.hasNext) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = "Next", modifier = Modifier.size(28.dp))
                }
            }
            Box(Modifier.padding(horizontal = 14.dp).padding(bottom = 6.dp)) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    strokeCap = StrokeCap.Round,
                    drawStopIndicator = {},
                )
            }
        }
    }
}
