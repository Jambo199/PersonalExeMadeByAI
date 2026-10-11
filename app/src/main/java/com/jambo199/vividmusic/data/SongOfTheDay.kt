package com.jambo199.vividmusic.data

import kotlin.random.Random

/** The same song all day long, a different one tomorrow. */
object SongOfTheDay {
    fun pick(lib: Library, day: String): Song? {
        // Proper songs rather than clips, when there are some.
        val songs = lib.songs.filter { it.durationMs >= 60_000 }.ifEmpty { lib.songs }
        if (songs.isEmpty()) return null
        val sorted = songs.sortedBy { it.key }
        return sorted[Random(day.hashCode()).nextInt(sorted.size)]
    }
}
