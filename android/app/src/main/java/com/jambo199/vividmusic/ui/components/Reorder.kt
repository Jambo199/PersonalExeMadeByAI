package com.jambo199.vividmusic.ui.components

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Drag-to-reorder for a LazyColumn. Items are moved live in [items] while dragging and the
 * final move is reported once through onMove(from, to) when the finger lifts.
 */
@Stable
class ReorderState<T>(
    val items: SnapshotStateList<T>,
    private val listState: LazyListState,
    private val scope: CoroutineScope,
    private val keyOf: (T) -> Any,
    private val headerCount: Int,
    private val onMove: (from: Int, to: Int) -> Unit,
) {
    var draggingKey by mutableStateOf<Any?>(null)
        private set
    var dragOffset by mutableFloatStateOf(0f)
        private set
    private var startIndex = -1

    val isDragging: Boolean get() = draggingKey != null

    fun start(key: Any) {
        val index = items.indexOfFirst { keyOf(it) == key }
        if (index < 0) return
        draggingKey = key
        startIndex = index
        dragOffset = 0f
    }

    private fun current(): LazyListItemInfo? =
        listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == draggingKey }

    fun drag(dy: Float) {
        val key = draggingKey ?: return
        dragOffset += dy
        val info = current() ?: return
        val center = info.offset + dragOffset + info.size / 2f
        val target = listState.layoutInfo.visibleItemsInfo.firstOrNull { other ->
            other.key != key && other.index >= headerCount && other.index < headerCount + items.size &&
                center > other.offset && center < other.offset + other.size
        }
        if (target != null) {
            val from = info.index - headerCount
            val to = target.index - headerCount
            if (from in items.indices && to in items.indices) {
                items.add(to, items.removeAt(from))
                dragOffset += (info.offset - target.offset)
            }
        }
        // Auto-scroll when dragging near the top or bottom edge.
        val layout = listState.layoutInfo
        val top = info.offset + dragOffset
        val bottom = top + info.size
        val edge = 60f
        val delta = when {
            top < layout.viewportStartOffset + edge -> -24f
            bottom > layout.viewportEndOffset - edge -> 24f
            else -> 0f
        }
        if (delta != 0f) {
            scope.launch {
                val consumed = listState.scrollBy(delta)
                dragOffset += consumed
            }
        }
    }

    fun end() {
        val key = draggingKey ?: return
        val to = items.indexOfFirst { keyOf(it) == key }
        val from = startIndex
        draggingKey = null
        dragOffset = 0f
        startIndex = -1
        if (from >= 0 && to >= 0 && from != to) onMove(from, to)
    }
}

@Composable
fun <T> rememberReorderState(
    source: List<T>,
    listState: LazyListState,
    keyOf: (T) -> Any,
    headerCount: Int = 0,
    onMove: (from: Int, to: Int) -> Unit,
): ReorderState<T> {
    val scope = rememberCoroutineScope()
    val latestOnMove by rememberUpdatedState(onMove)
    val items = remember { mutableStateListOf<T>().apply { addAll(source) } }
    val state = remember(listState, headerCount) {
        ReorderState(items, listState, scope, keyOf, headerCount) { from, to -> latestOnMove(from, to) }
    }
    LaunchedEffect(source) {
        if (!state.isDragging) {
            items.clear()
            items.addAll(source)
        }
    }
    return state
}

/** Modifier for the row being dragged: lifts it and follows the finger. */
fun <T> Modifier.reorderItem(state: ReorderState<T>, key: Any): Modifier =
    if (state.draggingKey == key) {
        this
            .zIndex(1f)
            .graphicsLayer {
                translationY = state.dragOffset
                shadowElevation = 12f
            }
    } else {
        this
    }

/** The grip icon that starts a drag. */
@Composable
fun <T> DragHandle(state: ReorderState<T>, key: Any, enabled: Boolean = true) {
    Icon(
        Icons.Rounded.DragHandle,
        contentDescription = "Drag to reorder",
        tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
        modifier = if (!enabled) {
            Modifier.padding12()
        } else {
            Modifier
                .padding12()
                .pointerInput(key) {
                    detectDragGestures(
                        onDragStart = { state.start(key) },
                        onDragEnd = { state.end() },
                        onDragCancel = { state.end() },
                        onDrag = { change, amount ->
                            change.consume()
                            state.drag(amount.y)
                        },
                    )
                }
        },
    )
}

private fun Modifier.padding12(): Modifier = this.padding(12.dp)
