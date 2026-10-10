package com.jambo199.vividmusic.playback

import android.content.ComponentName
import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.jambo199.vividmusic.AppGraph
import com.jambo199.vividmusic.data.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Immutable
data class PlaybackState(
    val connected: Boolean = false,
    val song: Song? = null,
    val index: Int = -1,
    val isPlaying: Boolean = false,
    val playWhenReady: Boolean = false,
    val isBuffering: Boolean = false,
    val durationMs: Long = 0,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val speed: Float = 1f,
    val pitch: Float = 1f,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    val queueSize: Int = 0,
)

@Immutable
data class QueueEntry(val index: Int, val song: Song)

/** The UI's handle on the playback service, exposed as observable state plus simple commands. */
class PlayerConnection(private val context: Context) {
    private var future: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private val pending = ArrayList<(MediaController) -> Unit>()

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val _queue = MutableStateFlow<List<QueueEntry>>(emptyList())

    /** The queue in play order (shuffled order while shuffle is on). */
    val queue: StateFlow<List<QueueEntry>> = _queue.asStateFlow()

    val source: StateFlow<String> get() = AppGraph.queueStore.source

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            publishState(player)
            if (events.containsAny(
                    Player.EVENT_TIMELINE_CHANGED,
                    Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                    Player.EVENT_MEDIA_ITEM_TRANSITION,
                )
            ) {
                publishQueue(player)
            }
        }
    }

    fun connect() {
        if (future != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val f = MediaController.Builder(context, token).buildAsync()
        future = f
        f.addListener(
            {
                if (future !== f) return@addListener
                val c = try {
                    f.get()
                } catch (e: Exception) {
                    future = null
                    return@addListener
                }
                controller = c
                c.addListener(listener)
                publishState(c)
                publishQueue(c)
                val actions = pending.toList()
                pending.clear()
                actions.forEach { it(c) }
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    fun disconnect() {
        controller?.removeListener(listener)
        controller = null
        future?.let { MediaController.releaseFuture(it) }
        future = null
        _state.value = _state.value.copy(connected = false)
    }

    private fun withController(action: (MediaController) -> Unit) {
        val c = controller
        if (c != null) action(c) else {
            pending += action
            connect()
        }
    }

    fun position(): Long = controller?.currentPosition ?: 0L
    fun bufferedPosition(): Long = controller?.bufferedPosition ?: 0L

    private fun publishState(p: Player) {
        val item = p.currentMediaItem
        val song = item?.let { AppGraph.library.findByKey(it.mediaId) ?: it.toSong() }
        val duration = p.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: song?.durationMs ?: 0L
        _state.value = PlaybackState(
            connected = true,
            song = song,
            index = p.currentMediaItemIndex,
            isPlaying = p.isPlaying,
            playWhenReady = p.playWhenReady,
            isBuffering = p.playbackState == Player.STATE_BUFFERING,
            durationMs = duration,
            shuffle = p.shuffleModeEnabled,
            repeatMode = p.repeatMode,
            speed = p.playbackParameters.speed,
            pitch = p.playbackParameters.pitch,
            hasNext = p.hasNextMediaItem(),
            hasPrevious = p.hasPreviousMediaItem(),
            queueSize = p.mediaItemCount,
        )
    }

    private fun publishQueue(p: Player) {
        val timeline = p.currentTimeline
        val count = p.mediaItemCount
        if (timeline.isEmpty || count == 0) {
            _queue.value = emptyList()
            return
        }
        val order = ArrayList<Int>(count)
        var i = timeline.getFirstWindowIndex(p.shuffleModeEnabled)
        while (i != C.INDEX_UNSET && order.size < count) {
            order += i
            i = timeline.getNextWindowIndex(i, Player.REPEAT_MODE_OFF, p.shuffleModeEnabled)
        }
        if (order.size < count) {
            // Shouldn't happen, but never lose items from the list.
            val seen = order.toHashSet()
            (0 until count).filterNot { it in seen }.forEach { order += it }
        }
        val library = AppGraph.library.library
        _queue.value = order.map { index ->
            val item = p.getMediaItemAt(index)
            QueueEntry(index, library.byKey[item.mediaId] ?: item.toSong())
        }
    }

    // region commands

    fun playSongs(songs: List<Song>, startIndex: Int = 0, shuffle: Boolean? = null, source: String = "") {
        if (songs.isEmpty()) return
        AppGraph.queueStore.setSource(source)
        withController { c ->
            val items = songs.map { it.toMediaItem() }
            when (shuffle) {
                true -> {
                    c.shuffleModeEnabled = true
                    c.setMediaItems(items, true)
                }
                false -> {
                    c.shuffleModeEnabled = false
                    c.setMediaItems(items, startIndex.coerceIn(0, items.lastIndex), 0L)
                }
                null -> c.setMediaItems(items, startIndex.coerceIn(0, items.lastIndex), 0L)
            }
            c.prepare()
            c.play()
        }
    }

    fun playNext(songs: List<Song>) {
        if (songs.isEmpty()) return
        withController { c ->
            if (c.mediaItemCount == 0) {
                playSongs(songs)
            } else {
                c.addMediaItems(c.currentMediaItemIndex + 1, songs.map { it.toMediaItem() })
            }
        }
    }

    fun addToQueue(songs: List<Song>) {
        if (songs.isEmpty()) return
        withController { c ->
            if (c.mediaItemCount == 0) playSongs(songs) else c.addMediaItems(songs.map { it.toMediaItem() })
        }
    }

    fun togglePlayPause() = withController { c ->
        if (c.isPlaying) {
            c.pause()
        } else {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            if (c.playbackState == Player.STATE_ENDED) c.seekToDefaultPosition()
            c.play()
        }
    }

    fun play() = withController { c ->
        if (c.playbackState == Player.STATE_IDLE) c.prepare()
        c.play()
    }

    fun pause() = withController { it.pause() }
    fun next() = withController { it.seekToNext() }
    fun previous() = withController { it.seekToPrevious() }
    fun seekTo(positionMs: Long) = withController { it.seekTo(positionMs.coerceAtLeast(0)) }
    fun seekBy(deltaMs: Long) = withController { it.seekTo((it.currentPosition + deltaMs).coerceAtLeast(0)) }

    fun toggleShuffle() = withController { it.shuffleModeEnabled = !it.shuffleModeEnabled }

    fun cycleRepeat() = withController { c ->
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun setSpeed(speed: Float, pitch: Float) = withController {
        it.playbackParameters = PlaybackParameters(speed.coerceIn(0.25f, 3f), pitch.coerceIn(0.5f, 2f))
    }

    fun playQueueIndex(index: Int) = withController { c ->
        if (index in 0 until c.mediaItemCount) {
            c.seekToDefaultPosition(index)
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            c.play()
        }
    }

    fun removeQueueIndex(index: Int) = withController { c ->
        if (index in 0 until c.mediaItemCount) c.removeMediaItem(index)
    }

    fun moveQueueItem(from: Int, to: Int) = withController { c ->
        if (from in 0 until c.mediaItemCount && to in 0 until c.mediaItemCount && from != to) c.moveMediaItem(from, to)
    }

    /** Removes everything except the playing song. */
    fun clearUpcoming() = withController { c ->
        val current = c.currentMediaItemIndex
        if (current == C.INDEX_UNSET) return@withController
        if (current + 1 < c.mediaItemCount) c.removeMediaItems(current + 1, c.mediaItemCount)
        if (current > 0) c.removeMediaItems(0, current)
    }

    fun stopAndClear() = withController { c ->
        c.stop()
        c.clearMediaItems()
    }

    /** Removes deleted files from the queue. */
    fun removeKeys(keys: Set<String>) = withController { c ->
        for (i in c.mediaItemCount - 1 downTo 0) {
            if (c.getMediaItemAt(i).mediaId in keys) c.removeMediaItem(i)
        }
    }

    // endregion
}
