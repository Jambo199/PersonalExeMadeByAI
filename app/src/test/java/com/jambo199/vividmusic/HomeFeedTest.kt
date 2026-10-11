package com.jambo199.vividmusic

import com.jambo199.vividmusic.data.HomeFeedBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeFeedTest {
    @Test
    fun noiseIsStableForTheSameSeed() {
        assertEquals(HomeFeedBuilder.noise("song-a", 42), HomeFeedBuilder.noise("song-a", 42), 0.0)
        assertNotEquals(HomeFeedBuilder.noise("song-a", 42), HomeFeedBuilder.noise("song-a", 43), 0.0)
    }

    @Test
    fun noiseIsSpreadAcrossZeroToOne() {
        val values = (0 until 2000).map { HomeFeedBuilder.noise("content://media/external/audio/media/$it", 7) }
        assertTrue(values.all { it >= 0.0 && it < 1.0 })
        // Roughly uniform: each quarter gets a fair share.
        val quarters = values.groupingBy { (it * 4).toInt() }.eachCount()
        assertEquals(4, quarters.size)
        assertTrue(quarters.values.all { it in 400..600 })
    }
}
