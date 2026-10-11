package com.jambo199.vividmusic

import com.jambo199.vividmusic.data.Wrapped
import com.jambo199.vividmusic.playback.ShakeDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FunFeaturesTest {
    @Test
    fun twoJoltsMakeAShake() {
        val g = ShakeDetector.Gesture()
        assertFalse(g.onForce(1f, 0))
        assertFalse(g.onForce(3f, 100)) // first jolt
        assertFalse(g.onForce(3.2f, 120)) // same jolt, still going
        assertFalse(g.onForce(1f, 200))
        assertTrue(g.onForce(3f, 400)) // second jolt soon after
    }

    @Test
    fun walkingAndSlowJoltsDontCount() {
        val g = ShakeDetector.Gesture()
        // Footsteps never get near the threshold.
        for (t in 0L until 5000L step 50) assertFalse(g.onForce(1.4f, t))
        // Two jolts too far apart.
        assertFalse(g.onForce(3f, 6000))
        assertFalse(g.onForce(1f, 6100))
        assertFalse(g.onForce(3f, 7000))
    }

    @Test
    fun oneShakeAtATime() {
        val g = ShakeDetector.Gesture()
        g.onForce(3f, 0); g.onForce(1f, 50)
        assertTrue(g.onForce(3f, 100))
        g.onForce(1f, 150)
        g.onForce(3f, 200); g.onForce(1f, 250)
        assertFalse(g.onForce(3f, 300)) // still cooling down
    }

    @Test
    fun personalities() {
        assertEquals("The Newcomer", Wrapped.personality(0, 0, 0, 0).title)
        assertEquals("The Repeat Champion", Wrapped.personality(plays = 30, differentSongs = 8, topSongPlays = 12, topArtistPlays = 14).title)
        assertEquals("The Superfan", Wrapped.personality(plays = 30, differentSongs = 20, topSongPlays = 3, topArtistPlays = 16).title)
        assertEquals("The Explorer", Wrapped.personality(plays = 30, differentSongs = 28, topSongPlays = 2, topArtistPlays = 4).title)
        assertEquals("The Mix Master", Wrapped.personality(plays = 30, differentSongs = 15, topSongPlays = 3, topArtistPlays = 5).title)
        assertEquals("The Loyal Listener", Wrapped.personality(plays = 30, differentSongs = 6, topSongPlays = 6, topArtistPlays = 8).title)
    }
}
