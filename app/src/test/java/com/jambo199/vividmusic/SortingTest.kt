package com.jambo199.vividmusic

import com.jambo199.vividmusic.data.NaturalOrderComparator
import com.jambo199.vividmusic.data.formatDuration
import com.jambo199.vividmusic.data.searchKey
import com.jambo199.vividmusic.data.sortKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SortingTest {
    @Test
    fun naturalOrderPutsNumbersInNumericOrder() {
        val sorted = listOf("Track 10.mp3", "Track 2.mp3", "track 1.mp3", "Track 02b.mp3", "Intro.mp3")
            .sortedWith(NaturalOrderComparator)
        assertEquals(listOf("Intro.mp3", "track 1.mp3", "Track 2.mp3", "Track 02b.mp3", "Track 10.mp3"), sorted)
    }

    @Test
    fun naturalOrderIgnoresCaseAndHandlesPrefixes() {
        assertTrue(NaturalOrderComparator.compare("abc", "ABD") < 0)
        assertTrue(NaturalOrderComparator.compare("abc", "abc1") < 0)
        assertEquals(0, NaturalOrderComparator.compare("Song", "song"))
    }

    @Test
    fun sortKeyDropsLeadingArticles() {
        assertEquals("beatles", sortKey("The Beatles"))
        assertEquals("tribe called quest", sortKey("A Tribe Called Quest"))
        assertEquals("theatre", sortKey("Theatre"))
    }

    @Test
    fun searchKeyRemovesAccents() {
        assertEquals("beyonce sinead", searchKey("Beyoncé Sinéad"))
    }

    @Test
    fun durationFormatting() {
        assertEquals("0:00", formatDuration(0))
        assertEquals("3:05", formatDuration(185_000))
        assertEquals("1:01:01", formatDuration(3_661_000))
    }
}
