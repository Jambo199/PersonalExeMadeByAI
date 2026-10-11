package com.jambo199.vividmusic

import androidx.media3.common.C
import androidx.media3.exoplayer.source.ShuffleOrder
import com.jambo199.vividmusic.playback.BalancedShuffle
import com.jambo199.vividmusic.playback.SmartShuffleOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SmartShuffleOrderTest {
    private fun ShuffleOrder.toList(): List<Int> {
        val out = ArrayList<Int>()
        var i = firstIndex
        while (i != C.INDEX_UNSET) {
            out += i
            i = getNextIndex(i)
        }
        return out
    }

    private fun assertConsistent(order: ShuffleOrder) {
        val list = order.toList()
        assertEquals((0 until order.length).toSet(), list.toSet())
        assertEquals(order.length, list.size)
        if (list.isNotEmpty()) assertEquals(list.last(), order.lastIndex)
        list.forEachIndexed { pos, index ->
            assertEquals(if (pos == 0) C.INDEX_UNSET else list[pos - 1], order.getPreviousIndex(index))
        }
    }

    @Test
    fun startsWithChosenSong() {
        repeat(20) { seed ->
            val order = SmartShuffleOrder.startingWith(7, 30, Random(seed))
            assertEquals(7, order.firstIndex)
            assertConsistent(order)
        }
    }

    @Test
    fun playNextInsertsRightAfterCurrent() {
        val order = SmartShuffleOrder.startingWith(3, 10, Random(1))
        val before = order.toList()
        // "Play next" while song 3 plays inserts two items at queue index 4.
        val after = order.cloneAndInsert(4, 2)
        assertConsistent(after)
        val list = after.toList()
        assertEquals(listOf(3, 4, 5), list.take(3))
        // The rest keep their relative order (shifted by two when >= 4).
        val shiftedBefore = before.drop(1).map { if (it >= 4) it + 2 else it }
        assertEquals(shiftedBefore, list.drop(3))
    }

    @Test
    fun addToQueueAppendsInOrder() {
        val order = SmartShuffleOrder.startingWith(0, 5, Random(2))
        val after = order.cloneAndInsert(5, 3)
        assertConsistent(after)
        assertEquals(listOf(5, 6, 7), after.toList().takeLast(3))
    }

    @Test
    fun removeKeepsOrder() {
        val order = SmartShuffleOrder.startingWith(2, 8, Random(3))
        val before = order.toList()
        val after = order.cloneAndRemove(4, 6)
        assertConsistent(after)
        val expected = before.filter { it < 4 || it >= 6 }.map { if (it >= 6) it - 2 else it }
        assertEquals(expected, after.toList())
    }

    @Test
    fun freshOrderAfterClear() {
        val cleared = SmartShuffleOrder.startingWith(1, 4).cloneAndClear()
        assertEquals(0, cleared.length)
        assertEquals(C.INDEX_UNSET, cleared.firstIndex)
        val refilled = cleared.cloneAndInsert(0, 12)
        assertConsistent(refilled)
    }

    @Test
    fun balancedShuffleKeepsArtistsApart() {
        // Half the songs by one artist, the rest by three others.
        val artists = List(20) { "A" } + List(7) { "B" } + List(7) { "C" } + List(6) { "D" }
        repeat(20) { seed ->
            val order = BalancedShuffle.order(first = 25, artists = artists, random = Random(seed))
            assertEquals(25, order.first())
            assertEquals((0 until 40).toSet(), order.toSet())
            assertEquals(40, order.size)
            // Spread evenly, "A" can't be avoided entirely, but long runs of it can.
            var longestRun = 0
            var run = 0
            var last = ""
            for (i in order) {
                run = if (artists[i] == last) run + 1 else 1
                last = artists[i]
                longestRun = maxOf(longestRun, run)
            }
            assertTrue("run of $longestRun with seed $seed", longestRun <= 4)
        }
    }

    @Test
    fun balancedShuffleWithoutArtistsIsStillAShuffle() {
        val order = BalancedShuffle.order(first = 0, artists = List(10) { "" }, random = Random(1))
        assertEquals((0 until 10).toList(), order.sorted())
        assertEquals(0, order.first())
    }
}
