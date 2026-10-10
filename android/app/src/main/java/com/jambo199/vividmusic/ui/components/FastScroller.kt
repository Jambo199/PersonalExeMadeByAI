package com.jambo199.vividmusic.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * A draggable scrollbar for long lists, with a letter bubble while dragging.
 * [headerCount] is the number of list items before the first data item.
 */
@Composable
fun FastScroller(
    listState: LazyListState,
    itemCount: Int,
    modifier: Modifier = Modifier,
    headerCount: Int = 0,
    bottomPadding: androidx.compose.ui.unit.Dp = 0.dp,
    labelFor: (Int) -> String? = { null },
) {
    if (itemCount < 30) return
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var visible by remember { mutableStateOf(false) }

    val scrolling = listState.isScrollInProgress
    LaunchedEffect(scrolling, dragging) {
        if (scrolling || dragging) {
            visible = true
        } else {
            delay(1500)
            visible = false
        }
    }

    val listFraction by remember(itemCount, headerCount) {
        derivedStateOf {
            val info = listState.layoutInfo
            val visibleCount = info.visibleItemsInfo.size.coerceAtLeast(1)
            val first = (listState.firstVisibleItemIndex - headerCount).coerceAtLeast(0)
            val maxFirst = (itemCount - visibleCount + 1).coerceAtLeast(1)
            (first.toFloat() / maxFirst).coerceIn(0f, 1f)
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxHeight()
            .padding(bottom = bottomPadding)
            .width(36.dp),
    ) {
        val trackHeightPx = with(density) { maxHeight.toPx() }
        val thumbHeight = 48.dp
        val thumbHeightPx = with(density) { thumbHeight.toPx() }
        val fraction = if (dragging) dragFraction else listFraction
        val thumbY = ((trackHeightPx - thumbHeightPx) * fraction).roundToInt()

        fun scrollToFraction(f: Float) {
            dragFraction = f.coerceIn(0f, 1f)
            val target = (dragFraction * (itemCount - 1)).roundToInt() + headerCount
            scope.launch { listState.scrollToItem(target) }
        }

        Box(
            Modifier
                .fillMaxHeight()
                .width(36.dp)
                .pointerInput(itemCount, headerCount) {
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            dragging = true
                            scrollToFraction(offset.y / size.height)
                        },
                        onDragEnd = { dragging = false },
                        onDragCancel = { dragging = false },
                        onVerticalDrag = { change, _ ->
                            change.consume()
                            scrollToFraction(change.position.y / size.height)
                        },
                    )
                },
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.offset { IntOffset(0, thumbY) }.align(Alignment.TopEnd),
            ) {
                Box(
                    Modifier
                        .padding(end = 4.dp)
                        .size(width = 6.dp, height = thumbHeight)
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            if (dragging) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        ),
                )
            }
        }

        if (dragging) {
            val index = (dragFraction * (itemCount - 1)).roundToInt()
            val label = labelFor(index)
            if (!label.isNullOrBlank()) {
                Box(
                    Modifier
                        .offset { IntOffset(-with(density) { 72.dp.roundToPx() }, (thumbY - with(density) { 8.dp.roundToPx() }).coerceAtLeast(0)) }
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label.take(2),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
    }
}
