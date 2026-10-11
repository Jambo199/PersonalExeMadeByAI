package com.jambo199.vividmusic

import com.jambo199.vividmusic.data.ArtistSeparators
import org.junit.Assert.assertEquals
import org.junit.Test

class ArtistSeparatorsTest {
    private val defaults = ArtistSeparators.DEFAULT

    @Test
    fun splitsOnSemicolonsAndFeaturing() {
        assertEquals(listOf("A", "B"), ArtistSeparators.split("A; B", defaults))
        assertEquals(listOf("Artist", "Guest"), ArtistSeparators.split("Artist feat. Guest", defaults))
        assertEquals(listOf("Artist", "Guest"), ArtistSeparators.split("Artist ft Guest", defaults))
        assertEquals(listOf("Artist", "Guest One"), ArtistSeparators.split("Artist (feat. Guest One)", defaults))
        assertEquals(listOf("Artist", "Guest"), ArtistSeparators.split("Artist FEATURING Guest", defaults))
    }

    @Test
    fun leavesNamesAloneUnlessAsked() {
        assertEquals(listOf("Simon & Garfunkel"), ArtistSeparators.split("Simon & Garfunkel", defaults))
        assertEquals(listOf("AC/DC"), ArtistSeparators.split("AC/DC", defaults))
        // "ft" inside a word is not a separator.
        assertEquals(listOf("Daft Punk"), ArtistSeparators.split("Daft Punk", defaults))
        assertEquals(listOf("Simon", "Garfunkel"), ArtistSeparators.split("Simon & Garfunkel", setOf("&")))
        assertEquals(listOf("A; B"), ArtistSeparators.split("A; B", emptySet()))
    }

    @Test
    fun dropsEmptyPartsAndDuplicates() {
        assertEquals(listOf("A", "B"), ArtistSeparators.split("A;; B; a", defaults))
        assertEquals(listOf(";"), ArtistSeparators.split(";", defaults))
    }
}
