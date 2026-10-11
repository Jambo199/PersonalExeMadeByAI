package com.jambo199.vividmusic.data

import androidx.compose.runtime.Immutable

/** Numbers for the "Your listening" screen. */
@Immutable
data class ListeningStats(
    val listenedMs: Long,
    val plays: Int,
    val songsPlayed: Int,
    val favorites: Int,
    val since: Long,
    val topSongs: List<Pair<Song, Int>>,
    val topArtists: List<Pair<Artist, Int>>,
    val topAlbums: List<Pair<Album, Int>>,
    val songCount: Int,
    val albumCount: Int,
    val artistCount: Int,
    val folderCount: Int,
    val libraryDurationMs: Long,
    val librarySizeBytes: Long,
    /** File types and how many songs of each, most common first. */
    val formats: List<Pair<String, Int>>,
) {
    companion object {
        fun from(lib: Library, data: UserData, top: Int = 10): ListeningStats {
            val counts = data.playCounts.filterKeys { it in lib.byKey }
            fun plays(songs: List<Song>) = songs.sumOf { counts[it.key] ?: 0 }
            return ListeningStats(
                listenedMs = data.listenedMs,
                plays = counts.values.sum(),
                songsPlayed = counts.count { it.value > 0 },
                favorites = data.favorites.count { it in lib.byKey },
                since = data.statsSince,
                topSongs = counts.entries
                    .filter { it.value > 0 }
                    .sortedByDescending { it.value }
                    .take(top)
                    .mapNotNull { (k, n) -> lib.byKey[k]?.let { it to n } },
                topArtists = lib.artists
                    .filter { it.name != Song.UNKNOWN_ARTIST }
                    .map { it to plays(it.songs) }
                    .filter { it.second > 0 }
                    .sortedByDescending { it.second }
                    .take(5),
                topAlbums = lib.albums
                    .map { it to plays(it.songs) }
                    .filter { it.second > 0 }
                    .sortedByDescending { it.second }
                    .take(5),
                songCount = lib.songs.size,
                albumCount = lib.albums.size,
                artistCount = lib.artists.size,
                folderCount = lib.musicFolders.size,
                libraryDurationMs = lib.songs.sumOf { it.durationMs },
                librarySizeBytes = lib.songs.sumOf { it.sizeBytes },
                formats = lib.songs
                    .groupingBy { it.extension.ifBlank { "?" } }
                    .eachCount()
                    .entries
                    .sortedByDescending { it.value }
                    .map { it.key to it.value },
            )
        }
    }
}
