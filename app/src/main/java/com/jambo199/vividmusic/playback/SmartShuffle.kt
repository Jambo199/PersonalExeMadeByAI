package com.jambo199.vividmusic.playback

import android.os.Handler
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder
import java.util.concurrent.CopyOnWriteArraySet
import kotlin.random.Random

/**
 * A shuffle order that behaves the way people expect:
 *  - turning shuffle on (or picking a song while shuffled) plays that song first, then the rest,
 *  - "Play next" really plays next, even while shuffled,
 *  - "Add to queue" goes to the end of the shuffled order.
 */
@OptIn(UnstableApi::class)
class SmartShuffleOrder private constructor(
    private val shuffled: IntArray,
    private val random: Random,
) : ShuffleOrder {

    private val positionOf = IntArray(shuffled.size).also { pos ->
        shuffled.forEachIndexed { i, index -> pos[index] = i }
    }

    constructor(length: Int, random: Random = Random(System.nanoTime())) :
        this(IntArray(length) { it }.also { it.shuffle(random) }, random)

    override fun getLength(): Int = shuffled.size

    override fun getNextIndex(index: Int): Int {
        val p = positionOf[index] + 1
        return if (p < shuffled.size) shuffled[p] else C.INDEX_UNSET
    }

    override fun getPreviousIndex(index: Int): Int {
        val p = positionOf[index] - 1
        return if (p >= 0) shuffled[p] else C.INDEX_UNSET
    }

    override fun getLastIndex(): Int = if (shuffled.isEmpty()) C.INDEX_UNSET else shuffled[shuffled.size - 1]

    override fun getFirstIndex(): Int = if (shuffled.isEmpty()) C.INDEX_UNSET else shuffled[0]

    override fun cloneAndInsert(insertionIndex: Int, insertionCount: Int): ShuffleOrder {
        val inserted = IntArray(insertionCount) { insertionIndex + it }
        if (shuffled.isEmpty()) {
            inserted.shuffle(random)
            return SmartShuffleOrder(inserted, random)
        }
        val shifted = IntArray(shuffled.size) { i ->
            val v = shuffled[i]
            if (v >= insertionIndex) v + insertionCount else v
        }
        val result = ArrayList<Int>(shuffled.size + insertionCount)
        when {
            insertionIndex >= shuffled.size -> {
                result.addAll(shifted.toList())
                result.addAll(inserted.toList())
            }
            insertionIndex == 0 -> {
                result.addAll(inserted.toList())
                result.addAll(shifted.toList())
            }
            else -> {
                // Put the new items right after the item that precedes them in the queue.
                val anchorPos = positionOf[insertionIndex - 1]
                for (i in 0..anchorPos) result.add(shifted[i])
                result.addAll(inserted.toList())
                for (i in anchorPos + 1 until shifted.size) result.add(shifted[i])
            }
        }
        return SmartShuffleOrder(result.toIntArray(), random)
    }

    override fun cloneAndRemove(indexFrom: Int, indexToExclusive: Int): ShuffleOrder {
        val count = indexToExclusive - indexFrom
        val result = ArrayList<Int>(shuffled.size)
        for (v in shuffled) {
            when {
                v < indexFrom -> result.add(v)
                v >= indexToExclusive -> result.add(v - count)
            }
        }
        return SmartShuffleOrder(result.toIntArray(), random)
    }

    override fun cloneAndClear(): ShuffleOrder = SmartShuffleOrder(IntArray(0), random)

    companion object {
        /** A fresh random order that starts with [first]. */
        fun startingWith(first: Int, length: Int, random: Random = Random(System.nanoTime())): SmartShuffleOrder {
            if (length <= 0) return SmartShuffleOrder(IntArray(0), random)
            val rest = (0 until length).filter { it != first }.toMutableList().apply { shuffle(random) }
            val order = if (first in 0 until length) listOf(first) + rest else rest
            return SmartShuffleOrder(order.toIntArray(), random)
        }
    }
}

/**
 * The player the media session sees: shuffle always starts from the song that's playing, plus a
 * short volume fade when you press play or pause (from the app, notification, headset or widget)
 * so music doesn't start or stop with a click. Pauses Android makes itself (headphones
 * unplugged, a call) stay instant. It can also show the current lyric line as the artist.
 */
@OptIn(UnstableApi::class)
class ShuffleAwarePlayer(
    private val exo: ExoPlayer,
    /** Fade length in ms, 0 for none. */
    private val fadeMs: () -> Long = { 0L },
    /** Volume to fade up to (below 1 when volume normalisation turns a song down). */
    private val targetVolume: () -> Float = { 1f },
) : ForwardingPlayer(exo) {
    private val handler = Handler(exo.applicationLooper)
    private var fade = 0

    /** True while a fade is moving the volume, so others don't set it underneath. */
    val isFading: Boolean get() = fadingNow
    private var fadingNow = false

    /**
     * Fading out before a pause: the session (and so the app, notification and widget) is told
     * "paused" straight away, so the play button doesn't flip back and forth.
     */
    private var pausing = false
    private val listeners = CopyOnWriteArraySet<Player.Listener>()

    override fun addListener(listener: Player.Listener) {
        super.addListener(listener)
        listeners += listener
    }

    override fun removeListener(listener: Player.Listener) {
        super.removeListener(listener)
        listeners -= listener
    }

    /** The lyric line being sung, and the song it belongs to. */
    private var lyricKey: String? = null
    private var lyricLine: String? = null

    /**
     * Shows [line] in place of the artist everywhere outside the app: lock screen, notification,
     * Dynamic Island-style pop-ups and car screens. Null puts the artist back.
     */
    fun showLyricLine(songKey: String?, line: String?) {
        if (songKey == lyricKey && line == lyricLine) return
        lyricKey = songKey
        lyricLine = line
        val metadata = mediaMetadata
        listeners.forEach { it.onMediaMetadataChanged(metadata) }
    }

    override fun getMediaMetadata(): MediaMetadata {
        val metadata = super.getMediaMetadata()
        val line = lyricLine ?: return metadata
        if (lyricKey != exo.currentMediaItem?.mediaId) return metadata
        return metadata.buildUpon().setArtist(line).build()
    }

    override fun getPlayWhenReady(): Boolean = !pausing && super.getPlayWhenReady()

    override fun isPlaying(): Boolean = !pausing && super.isPlaying()

    private fun setPausing(value: Boolean) {
        if (pausing == value) return
        pausing = value
        val playing = !value
        val audible = playing && exo.isPlaying
        listeners.forEach {
            it.onPlayWhenReadyChanged(playing, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            it.onIsPlayingChanged(audible)
        }
    }

    override fun play() {
        val ms = fadeMs()
        val id = ++fade
        setPausing(false)
        when {
            ms <= 0 -> {
                fadingNow = false
                exo.volume = targetVolume()
                super.play()
            }
            // Pressed play again while it was fading out: come back up from where it got to.
            exo.isPlaying -> ramp(id, exo.volume, targetVolume, ms) {}
            else -> {
                exo.volume = 0f
                super.play()
                ramp(id, 0f, targetVolume, ms) {}
            }
        }
    }

    override fun pause() {
        val ms = fadeMs()
        val id = ++fade
        if (ms <= 0 || !exo.isPlaying) {
            fadingNow = false
            pausing = false
            super.pause()
            exo.volume = targetVolume()
            return
        }
        setPausing(true)
        ramp(id, exo.volume, { 0f }, ms) {
            // Pause for real first, so lifting "pausing" doesn't report playing again.
            exo.pause()
            pausing = false
            exo.volume = targetVolume()
        }
    }

    /** Starts playing silently and slowly turns it up over [ms], to wake you gently. */
    fun fadeIn(ms: Long) {
        val id = ++fade
        setPausing(false)
        exo.volume = 0f
        super.play()
        // Ears hear loudness on a curve, so a straight line would seem to jump up at the start.
        ramp(id, 0f, targetVolume, ms, curve = { it * it }) {}
    }

    /**
     * Moves the volume from [from] to [to] in steps. [to] is read at every step because a new
     * song's ReplayGain tags can arrive during the fade.
     */
    private fun ramp(id: Int, from: Float, to: () -> Float, ms: Long, curve: (Float) -> Float = { it }, done: () -> Unit) {
        fadingNow = true
        val steps = (ms / 100).toInt().coerceIn(10, 600)
        for (i in 1..steps) {
            handler.postDelayed({
                if (id != fade) return@postDelayed
                exo.volume = from + (to() - from) * curve(i / steps.toFloat())
                if (i == steps) {
                    fadingNow = false
                    done()
                }
            }, ms * i / steps)
        }
    }

    override fun setShuffleModeEnabled(shuffleModeEnabled: Boolean) {
        if (shuffleModeEnabled && !exo.shuffleModeEnabled) reshuffleFromCurrent()
        super.setShuffleModeEnabled(shuffleModeEnabled)
    }

    override fun setMediaItems(mediaItems: MutableList<MediaItem>) {
        super.setMediaItems(mediaItems)
        if (exo.shuffleModeEnabled) reshuffleFromCurrent()
    }

    override fun setMediaItems(mediaItems: MutableList<MediaItem>, resetPosition: Boolean) {
        super.setMediaItems(mediaItems, resetPosition)
        if (exo.shuffleModeEnabled) reshuffleFromCurrent()
    }

    override fun setMediaItems(mediaItems: MutableList<MediaItem>, startIndex: Int, startPositionMs: Long) {
        super.setMediaItems(mediaItems, startIndex, startPositionMs)
        if (exo.shuffleModeEnabled) reshuffleFromCurrent()
    }

    private fun reshuffleFromCurrent() {
        val count = exo.mediaItemCount
        if (count == 0) return
        exo.setShuffleOrder(SmartShuffleOrder.startingWith(exo.currentMediaItemIndex, count))
    }
}
