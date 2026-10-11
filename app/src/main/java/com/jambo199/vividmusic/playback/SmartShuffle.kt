package com.jambo199.vividmusic.playback

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder
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

/** Wraps ExoPlayer so shuffle always starts from the song that's playing. */
@OptIn(UnstableApi::class)
class ShuffleAwarePlayer(private val exo: ExoPlayer) : ForwardingPlayer(exo) {

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
