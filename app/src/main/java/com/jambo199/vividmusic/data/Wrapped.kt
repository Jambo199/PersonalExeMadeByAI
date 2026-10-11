package com.jambo199.vividmusic.data

/** A listening "personality" for the Wrapped story. */
data class ListeningPersonality(val title: String, val description: String)

/** Everything the Wrapped story shows, worked out from your listening so far. */
data class Wrapped(
    val since: Long,
    val listenedMs: Long,
    val plays: Int,
    val differentSongs: Int,
    val topSongs: List<Pair<Song, Int>>,
    val topArtists: List<Pair<Artist, Int>>,
    val topGenre: Pair<String, Int>?,
    /** The day you listened most ("yyyy-MM-dd") and for how long. */
    val busiestDay: Pair<String, Long>?,
    /** Days with any listening. */
    val activeDays: Int,
    val personality: ListeningPersonality,
) {
    val minutes: Long get() = listenedMs / 60_000

    /** Something the same length, to make the minutes feel real. */
    val comparison: String
        get() {
            val m = minutes
            return when {
                m >= 60 * 24 -> "That's ${"%.1f".format(m / (60f * 24))} whole days of music."
                m >= 240 -> "That's like watching ${m / 120} films back to back."
                m >= 30 -> "That's ${m / 3} three-minute songs' worth."
                else -> "Every minute counts. Keep listening!"
            }
        }

    /** Whether there's enough to make a story about. */
    val hasStory: Boolean get() = plays >= 3 && topSongs.isNotEmpty()

    companion object {
        fun from(lib: Library, data: UserData): Wrapped {
            val counts = data.playCounts.filter { it.value > 0 && it.key in lib.byKey }
            fun plays(songs: List<Song>) = songs.sumOf { counts[it.key] ?: 0 }
            val topSongs = counts.entries.sortedByDescending { it.value }.take(5).mapNotNull { (k, n) -> lib.byKey[k]?.let { it to n } }
            val topArtists = lib.artists
                .filter { it.name != Song.UNKNOWN_ARTIST }
                .map { it to plays(it.songs) }
                .filter { it.second > 0 }
                .sortedByDescending { it.second }
                .take(5)
            val genreCounts = HashMap<String, Int>()
            counts.forEach { (key, n) ->
                val genre = lib.byKey[key]?.genre?.trim().orEmpty()
                if (genre.isNotEmpty()) genreCounts[genre] = (genreCounts[genre] ?: 0) + n
            }
            val plays = counts.values.sum()
            return Wrapped(
                since = data.statsSince,
                listenedMs = data.listenedMs,
                plays = plays,
                differentSongs = counts.size,
                topSongs = topSongs,
                topArtists = topArtists,
                topGenre = genreCounts.maxByOrNull { it.value }?.toPair(),
                busiestDay = data.dailyMs.filter { it.value > 0 }.maxByOrNull { it.value }?.toPair(),
                activeDays = data.dailyMs.count { it.value > 0 },
                personality = personality(plays, counts.size, topSongs.firstOrNull()?.second ?: 0, topArtists.firstOrNull()?.second ?: 0),
            )
        }

        /** A light-hearted label from how spread out your listening is. */
        fun personality(plays: Int, differentSongs: Int, topSongPlays: Int, topArtistPlays: Int): ListeningPersonality {
            if (plays == 0) return ListeningPersonality("The Newcomer", "Your story is just getting started.")
            val variety = differentSongs.toFloat() / plays
            return when {
                topSongPlays * 3 >= plays && plays >= 6 ->
                    ListeningPersonality("The Repeat Champion", "When you love a song, you really love it. On repeat, again and again.")
                topArtistPlays * 2 >= plays && plays >= 6 ->
                    ListeningPersonality("The Superfan", "One artist has your heart, and you're not shy about it.")
                variety >= 0.8f ->
                    ListeningPersonality("The Explorer", "Always after something new. Hardly a song gets played twice.")
                variety >= 0.4f ->
                    ListeningPersonality("The Mix Master", "A healthy blend of old favourites and fresh finds.")
                else ->
                    ListeningPersonality("The Loyal Listener", "You know what you like and you keep coming back to it.")
            }
        }
    }
}
