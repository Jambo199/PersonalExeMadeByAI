package com.jambo199.vividmusic.data

import android.net.Uri
import androidx.compose.runtime.Immutable

enum class SongSource { MEDIA_STORE, FOLDER, EXTERNAL }

/**
 * One playable audio file. [key] is the content uri as a string and is used everywhere as the
 * stable identity (queue, playlists, favourites, play counts).
 */
@Immutable
data class Song(
    val key: String,
    val uri: Uri,
    val mediaStoreId: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumArtist: String,
    val albumId: Long,
    val durationMs: Long,
    /** MediaStore style track number: disc * 1000 + track. */
    val trackNumber: Int,
    val year: Int,
    val dateAddedSec: Long,
    val dateModifiedSec: Long,
    val sizeBytes: Long,
    /** Absolute path for MediaStore songs, display path for picked folders. */
    val path: String,
    /** Key of the folder that directly contains this song. */
    val folder: String,
    val fileName: String,
    val mimeType: String,
    val genre: String,
    val source: SongSource,
    /** Folder cover image found next to the file (picked folders only). */
    val coverHint: Uri? = null,
    /** Sidecar .lrc file found next to the file (picked folders only). */
    val lyricsHint: Uri? = null,
) {
    val track: Int get() = trackNumber % 1000
    val disc: Int get() = trackNumber / 1000
    val displayArtist: String get() = artist.ifBlank { UNKNOWN_ARTIST }
    val displayAlbum: String get() = album.ifBlank { UNKNOWN_ALBUM }
    val extension: String get() = fileName.substringAfterLast('.', "").uppercase()

    companion object {
        const val UNKNOWN_ARTIST = "Unknown artist"
        const val UNKNOWN_ALBUM = "Unknown album"
    }
}

@Immutable
data class Album(
    val id: Long,
    val title: String,
    val artist: String,
    val year: Int,
    val songs: List<Song>,
) {
    val cover: Song get() = songs.first()
    val durationMs: Long get() = songs.sumOf { it.durationMs }
    val key: String get() = id.toString()
}

@Immutable
data class Artist(
    val name: String,
    val songs: List<Song>,
    val albums: List<Album>,
) {
    val cover: Song get() = songs.first()
}

@Immutable
data class Genre(
    val name: String,
    val songs: List<Song>,
) {
    val cover: Song get() = songs.first()
}

@Immutable
data class FolderNode(
    val key: String,
    val name: String,
    /** Human readable location shown under the name. */
    val displayPath: String,
    val parentKey: String?,
    val childKeys: List<String>,
    /** Songs directly inside this folder. */
    val songs: List<Song>,
    /** Songs in this folder and every subfolder. */
    val totalSongs: Int,
    /** Song used for the folder thumbnail. */
    val cover: Song?,
    val isPickedFolder: Boolean,
)

@Immutable
data class Playlist(
    val id: Long,
    val name: String,
    val songKeys: List<String>,
    val createdAt: Long,
)

/** Built-in playlists shown above the user's own playlists. */
object SmartPlaylists {
    const val FAVORITES = -1L
    const val RECENTLY_ADDED = -2L
    const val RECENTLY_PLAYED = -3L
    const val MOST_PLAYED = -4L

    fun name(id: Long): String = when (id) {
        FAVORITES -> "Favourites"
        RECENTLY_ADDED -> "Recently added"
        RECENTLY_PLAYED -> "Recently played"
        MOST_PLAYED -> "Most played"
        else -> ""
    }
}

@Immutable
data class Library(
    val songs: List<Song> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val folders: Map<String, FolderNode> = emptyMap(),
    /** Entry points of the folder tree (storage roots and picked folders). */
    val folderRoots: List<String> = emptyList(),
    val byKey: Map<String, Song> = emptyMap(),
) {
    val albumsById: Map<Long, Album> by lazy { albums.associateBy { it.id } }
    val artistsByName: Map<String, Artist> by lazy { artists.associateBy { it.name } }

    /**
     * Genres from the songs' tags, biggest first. Multi-genre tags ("Rock; Pop") count for each,
     * and spelling differences in case are merged.
     */
    val genres: List<Genre> by lazy {
        val groups = LinkedHashMap<String, Pair<String, MutableList<Song>>>()
        for (song in songs) {
            song.genre.split(';', '\u0000')
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.equals("<unknown>", ignoreCase = true) }
                // "Rock; rock" must not list the song twice.
                .distinctBy { it.lowercase() }
                .forEach { name -> groups.getOrPut(name.lowercase()) { name to ArrayList() }.second += song }
        }
        val order = compareBy<Song>({ sortKey(it.displayArtist) }, { sortKey(it.displayAlbum) }, { it.trackNumber }, { sortKey(it.title) })
        groups.values.map { (name, list) -> Genre(name, list.sortedWith(order)) }.sortedByDescending { it.songs.size }
    }

    fun genre(name: String): Genre? = genres.firstOrNull { it.name.equals(name, ignoreCase = true) }

    /** Folders that directly contain songs, for the flat folder list. */
    val musicFolders: List<FolderNode> by lazy { folders.values.filter { it.songs.isNotEmpty() } }

    /** Every song in [folderKey] and its subfolders, folder by folder in display order. */
    fun songsInTree(folderKey: String, songOrder: Comparator<Song>): List<Song> {
        val out = ArrayList<Song>()
        fun walk(key: String) {
            val node = folders[key] ?: return
            out += node.songs.sortedWith(songOrder)
            node.childKeys
                .mapNotNull { folders[it] }
                .sortedWith(compareBy(NaturalOrderComparator) { it.name })
                .forEach { walk(it.key) }
        }
        walk(folderKey)
        return out
    }
}

@Immutable
data class LibraryState(
    val library: Library = Library(),
    val isScanning: Boolean = false,
    val hasLoadedOnce: Boolean = false,
    val scanMessage: String? = null,
)
