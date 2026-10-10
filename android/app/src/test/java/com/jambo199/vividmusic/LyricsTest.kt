package com.jambo199.vividmusic

import com.jambo199.vividmusic.data.EmbeddedLyrics
import com.jambo199.vividmusic.data.LrcParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsTest {
    @Test
    fun parsesTimestampsInOrder() {
        val lines = LrcParser.parse(
            """
            [ti:Test]
            [ar:Someone]
            [00:12.50]Second
            [00:01.00]First
            [01:02.123]Third
            """.trimIndent(),
        )
        assertEquals(listOf(1_000L, 12_500L, 62_123L), lines.map { it.timeMs })
        assertEquals(listOf("First", "Second", "Third"), lines.map { it.text })
    }

    @Test
    fun repeatedTimestampsAndOffset() {
        val lines = LrcParser.parse("[offset:+500]\n[00:10.00][00:20.00]Chorus\n[00:15]Verse")
        assertEquals(listOf(9_500L, 14_500L, 19_500L), lines.map { it.timeMs })
        assertEquals(listOf("Chorus", "Verse", "Chorus"), lines.map { it.text })
    }

    @Test
    fun stripsWordTimingTags() {
        val lines = LrcParser.parse("[00:01.00]<00:01.00>Hello <00:01.50>world")
        assertEquals("Hello world", lines.single().text)
    }

    @Test
    fun plainTextHasNoSyncedLines() {
        assertTrue(LrcParser.parse("Just some words\nand more").isEmpty())
        assertEquals("Line", LrcParser.stripTags("[ar:Me]\n[00:01.00]Line"))
    }

    @Test
    fun decodesUsltFrames() {
        val latin = byteArrayOf(0) + "eng".toByteArray() + byteArrayOf(0) + "Hello lyrics".toByteArray(Charsets.ISO_8859_1)
        assertEquals("Hello lyrics", EmbeddedLyrics.parseUslt(latin))

        val utf8 = byteArrayOf(3) + "eng".toByteArray() + "desc".toByteArray() + byteArrayOf(0) + "Grüße".toByteArray(Charsets.UTF_8)
        assertEquals("Grüße", EmbeddedLyrics.parseUslt(utf8))

        val utf16 = byteArrayOf(1) + "eng".toByteArray() + byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0, 0) +
            "Hi".toByteArray(Charsets.UTF_16)
        assertEquals("Hi", EmbeddedLyrics.parseUslt(utf16))

        assertNull(EmbeddedLyrics.parseUslt(byteArrayOf(0, 1)))
    }
}
