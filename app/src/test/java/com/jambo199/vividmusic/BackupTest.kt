package com.jambo199.vividmusic

import com.jambo199.vividmusic.data.Playlist
import com.jambo199.vividmusic.data.UserData
import com.jambo199.vividmusic.data.UserDataStore
import org.junit.Assert.assertEquals
import org.junit.Test

class BackupTest {
    private val data = UserData(
        playlists = listOf(Playlist(1, "Road trip", listOf("old/1", "keep/2", "old/1"), 0)),
        favorites = listOf("old/1", "new/1"),
        recent = listOf("keep/2", "old/1"),
        playCounts = mapOf("old/1" to 3, "new/1" to 2, "keep/2" to 1),
    )

    @Test
    fun collectsEverySongKey() {
        assertEquals(setOf("old/1", "keep/2", "new/1"), UserDataStore.keysIn(data))
    }

    @Test
    fun remapsKeysEverywhereAndMergesDuplicates() {
        val out = UserDataStore.remap(data, mapOf("old/1" to "new/1"))
        assertEquals(listOf("new/1", "keep/2", "new/1"), out.playlists[0].songKeys)
        assertEquals(listOf("new/1"), out.favorites)
        assertEquals(listOf("keep/2", "new/1"), out.recent)
        assertEquals(mapOf("new/1" to 5, "keep/2" to 1), out.playCounts)
    }

    @Test
    fun emptyMapChangesNothing() {
        assertEquals(data, UserDataStore.remap(data, emptyMap()))
    }
}
