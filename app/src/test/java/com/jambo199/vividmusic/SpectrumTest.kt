package com.jambo199.vividmusic

import com.jambo199.vividmusic.playback.Spectrum
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class SpectrumTest {
    private fun tone(hz: Double, rate: Int = 44_100, n: Int = 2048) =
        FloatArray(n) { (0.5 * sin(2 * PI * hz * it / rate)).toFloat() }

    private fun loudestBand(levels: FloatArray) = levels.indices.maxBy { levels[it] }

    @Test
    fun bassLightsTheLeftBars() {
        val levels = Spectrum.analyse(tone(100.0), 44_100)
        val band = loudestBand(levels)
        assertTrue("band $band", band < Spectrum.BANDS / 4)
        assertTrue(levels[band] > 0.8f)
        assertTrue(levels[Spectrum.BANDS - 1] < 0.3f)
    }

    @Test
    fun trebleLightsTheRightBars() {
        val band = loudestBand(Spectrum.analyse(tone(5_000.0), 44_100))
        assertTrue("band $band", band > Spectrum.BANDS / 2)
    }

    @Test
    fun silenceStaysDown() {
        val levels = Spectrum.analyse(FloatArray(2048), 44_100)
        assertTrue(levels.all { it < 0.05f })
    }
}
