package com.jambo199.vividmusic.data

import androidx.compose.runtime.Immutable
import kotlin.math.ln

/** Everything the Home screen shows, worked out from the library and listening history. */
@Immutable
data class HomeFeed(
    val quickPicks: List<Song> = emptyList(),
    val keepListening: List<Album> = emptyList(),
    val recentlyAdded: List<Album> = emptyList(),
    /** True when [artists] is ranked by plays, false when it's just the biggest artists. */
    val artistsByPlays: Boolean = false,
    val artists: List<Artist> = emptyList(),
    val forgottenFavorites: List<Song> = emptyList(),
    val unplayedAlbums: List<Album> = emptyList(),
    val songCount: Int = 0,
    val totalDurationMs: Long = 0,
)

object HomeFeedBuilder {
    private const val QUICK_PICKS = 20
    private const val ROW = 12

    /**
     * [seed] keeps the random parts (quick picks, rediscover) the same for a whole session so
     * the screen doesn't reshuffle every time a song finishes.
     */
    fun build(lib: Library, data: UserData, seed: Long, quickPicksData: UserData = data): HomeFeed {
        if (lib.songs.isEmpty()) return HomeFeed()
        val ranked = rankedArtists(lib, data)
        val byPlays = ranked.size >= 3
        return HomeFeed(
            quickPicks = quickPicks(lib, quickPicksData, seed),
            keepListening = keepListening(lib, data),
            recentlyAdded = recentlyAdded(lib),
            artistsByPlays = byPlays,
            artists = if (byPlays) ranked
            else lib.artists.filter { it.name != Song.UNKNOWN_ARTIST }.sortedByDescending { it.songs.size }.take(ROW),
            forgottenFavorites = forgottenFavorites(lib, data, seed),
            unplayedAlbums = unplayedAlbums(lib, data, seed),
            songCount = lib.songs.size,
            totalDurationMs = lib.songs.sumOf { it.durationMs },
        )
    }

    /**
     * A mix of what you play most, your favourites and a sprinkle of everything else, never more
     * than two songs by the same artist.
     */
    fun quickPicks(lib: Library, data: UserData, seed: Long): List<Song> {
        val recentRank = data.recent.withIndex().associate { (i, k) -> k to i }
        val scored = lib.songs.map { song ->
            val plays = data.playCounts[song.key] ?: 0
            var score = 1.6 * ln(1.0 + plays) + 2.2 * noise(song.key, seed)
            if (song.key in data.favoriteSet) score += 1.4
            // Just-played songs are already fresh in mind; slightly favour the ones a little older.
            recentRank[song.key]?.let { rank -> if (rank < 5) score -= 1.0 else if (rank < 40) score += 0.4 }
            song to score
        }.sortedByDescending { it.second }

        val perArtist = HashMap<String, Int>()
        val picks = ArrayList<Song>(QUICK_PICKS)
        for ((song, _) in scored) {
            val n = perArtist[song.displayArtist] ?: 0
            if (n >= 2 && song.displayArtist != Song.UNKNOWN_ARTIST) continue
            perArtist[song.displayArtist] = n + 1
            picks += song
            if (picks.size == QUICK_PICKS) break
        }
        // Small libraries with only a couple of artists: fill up regardless.
        if (picks.size < QUICK_PICKS) {
            val chosen = picks.mapTo(HashSet()) { it.key }
            scored.asSequence().map { it.first }.filter { it.key !in chosen }.take(QUICK_PICKS - picks.size).forEach { picks += it }
        }
        return picks
    }

    /** Albums of the songs played most recently, newest first. */
    fun keepListening(lib: Library, data: UserData): List<Album> {
        val seen = LinkedHashSet<Long>()
        for (key in data.recent) {
            val song = lib.byKey[key] ?: continue
            seen += song.albumId
            if (seen.size == ROW) break
        }
        return seen.mapNotNull { lib.albumsById[it] }
    }

    fun recentlyAdded(lib: Library): List<Album> =
        lib.albums.sortedByDescending { album -> album.songs.maxOf { it.dateAddedSec } }.take(ROW)

    fun rankedArtists(lib: Library, data: UserData): List<Artist> {
        if (data.playCounts.isEmpty()) return emptyList()
        return lib.artists
            .map { artist -> artist to artist.songs.sumOf { data.playCounts[it.key] ?: 0 } }
            .filter { it.second > 0 && it.first.name != Song.UNKNOWN_ARTIST }
            .sortedByDescending { it.second }
            .take(ROW)
            .map { it.first }
    }

    /** Songs you loved (favourites or played a lot) that haven't come up lately. */
    fun forgottenFavorites(lib: Library, data: UserData, seed: Long): List<Song> {
        val lately = data.recent.take(40).toHashSet()
        return lib.songs
            .filter { it.key !in lately && ((data.playCounts[it.key] ?: 0) >= 3 || it.key in data.favoriteSet) }
            .sortedByDescending { (data.playCounts[it.key] ?: 0) + 3 * noise(it.key, seed) }
            .take(ROW)
            .takeIf { it.size >= 3 }
            .orEmpty()
    }

    /** Albums you've never played anything from. */
    fun unplayedAlbums(lib: Library, data: UserData, seed: Long): List<Album> =
        lib.albums
            .filter { album -> album.songs.none { (data.playCounts[it.key] ?: 0) > 0 } }
            .sortedBy { noise(it.key, seed) }
            .take(ROW)

    /** Stable pseudo-random number in 0..1 for [key] under [seed]. */
    fun noise(key: String, seed: Long): Double {
        var x = key.hashCode().toLong() * -0x61c8864680b583ebL + seed
        x = (x xor (x ushr 30)) * -0x40a7b892e31b1a47L
        x = (x xor (x ushr 27)) * -0x6b2fb644ecceee15L
        x = x xor (x ushr 31)
        return (x ushr 11).toDouble() / (1L shl 53).toDouble()
    }
}
