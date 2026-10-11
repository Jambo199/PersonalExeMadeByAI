package com.jambo199.vividmusic

import com.jambo199.vividmusic.data.Normalization
import com.jambo199.vividmusic.data.ReplayGain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReplayGainTest {
    @Test
    fun parsesTagValues() {
        assertEquals(-7.32f, ReplayGain.parseDb("-7.32 dB")!!, 0.001f)
        assertEquals(1.5f, ReplayGain.parseDb("+1.5dB")!!, 0.001f)
        assertEquals(-3f, ReplayGain.parseDb(" -3,0 dB ")!!, 0.001f)
        assertNull(ReplayGain.parseDb("loud"))
        assertNull(ReplayGain.parseDb(null))
    }

    @Test
    fun turnsLoudSongsDownOnly() {
        val gain = ReplayGain(trackDb = -6.02f, albumDb = -3f)
        assertEquals(0.5f, gain.volume(Normalization.TRACK), 0.01f)
        assertEquals(0.708f, gain.volume(Normalization.ALBUM), 0.01f)
        assertEquals(1f, gain.volume(Normalization.OFF), 0f)
        assertEquals(1f, ReplayGain(trackDb = 4f).volume(Normalization.TRACK), 0f)
        assertEquals(1f, ReplayGain().volume(Normalization.TRACK), 0f)
    }

    @Test
    fun adjustmentsForTaggedAndUntaggedSongs() {
        // -12 dB tag + 6 dB pre-amp = -6 dB.
        assertEquals(0.5f, ReplayGain(trackDb = -12f).volume(Normalization.TRACK, preampDb = 6.02f), 0.01f)
        // Untagged songs use their own adjustment, and only when normalisation is on.
        assertEquals(0.5f, ReplayGain().volume(Normalization.TRACK, untaggedDb = -6.02f), 0.01f)
        assertEquals(1f, ReplayGain().volume(Normalization.OFF, untaggedDb = -6.02f), 0f)
    }

    @Test
    fun fallsBackToTheOtherGain() {
        assertEquals(0.5f, ReplayGain(albumDb = -6.02f).volume(Normalization.TRACK), 0.01f)
        assertEquals(0.5f, ReplayGain(trackDb = -6.02f).volume(Normalization.ALBUM), 0.01f)
    }
}
