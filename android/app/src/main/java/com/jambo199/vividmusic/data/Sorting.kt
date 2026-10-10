package com.jambo199.vividmusic.data

import java.text.Normalizer

/** Compares strings the way people expect file names to sort: "Track 2" before "Track 10". */
object NaturalOrderComparator : Comparator<String> {
    override fun compare(a: String, b: String): Int {
        var i = 0
        var j = 0
        while (i < a.length && j < b.length) {
            val ca = a[i]
            val cb = b[j]
            if (ca.isDigit() && cb.isDigit()) {
                val startA = i
                val startB = j
                while (i < a.length && a[i].isDigit()) i++
                while (j < b.length && b[j].isDigit()) j++
                val numA = a.substring(startA, i).trimStart('0')
                val numB = b.substring(startB, j).trimStart('0')
                if (numA.length != numB.length) return numA.length - numB.length
                val cmp = numA.compareTo(numB)
                if (cmp != 0) return cmp
            } else {
                val cmp = ca.lowercaseChar().compareTo(cb.lowercaseChar())
                if (cmp != 0) return cmp
                i++
                j++
            }
        }
        return (a.length - i) - (b.length - j)
    }
}

private val ARTICLE = Regex("^(the|a|an)\\s+", RegexOption.IGNORE_CASE)

/** Sort key that ignores case and a leading "The"/"A". */
fun sortKey(text: String): String = text.trim().replace(ARTICLE, "").lowercase()

private val COMBINING_MARKS = Regex("\\p{Mn}+")

/** Lowercase, accent-free text for search matching. */
fun searchKey(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD).replace(COMBINING_MARKS, "").lowercase()

enum class SongSortField(val label: String) {
    TITLE("Title"),
    ARTIST("Artist"),
    ALBUM("Album"),
    DATE_ADDED("Date added"),
    DURATION("Duration"),
    YEAR("Year"),
    FILE_NAME("File name"),
}

enum class AlbumSortField(val label: String) {
    TITLE("Title"),
    ARTIST("Artist"),
    YEAR("Year"),
    SONG_COUNT("Number of songs"),
}

enum class ArtistSortField(val label: String) {
    NAME("Name"),
    SONG_COUNT("Number of songs"),
    ALBUM_COUNT("Number of albums"),
}

enum class FolderSongOrder(val label: String) {
    FILE_NAME("File name"),
    TITLE("Title"),
    TRACK("Track number"),
    DATE_ADDED("Date added"),
}

fun songComparator(field: SongSortField, ascending: Boolean): Comparator<Song> {
    val base: Comparator<Song> = when (field) {
        SongSortField.TITLE -> compareBy<Song> { sortKey(it.title) }
        SongSortField.ARTIST -> compareBy<Song> { sortKey(it.displayArtist) }
            .thenBy { sortKey(it.album) }.thenBy { it.trackNumber }
        SongSortField.ALBUM -> compareBy<Song> { sortKey(it.displayAlbum) }.thenBy { it.trackNumber }
        SongSortField.DATE_ADDED -> compareBy<Song> { it.dateAddedSec }
        SongSortField.DURATION -> compareBy<Song> { it.durationMs }
        SongSortField.YEAR -> compareBy<Song> { it.year }.thenBy { sortKey(it.album) }.thenBy { it.trackNumber }
        SongSortField.FILE_NAME -> Comparator { a, b -> NaturalOrderComparator.compare(a.fileName, b.fileName) }
    }
    return if (ascending) base else base.reversed()
}

fun folderSongComparator(order: FolderSongOrder): Comparator<Song> = when (order) {
    FolderSongOrder.FILE_NAME -> Comparator { a, b -> NaturalOrderComparator.compare(a.fileName, b.fileName) }
    FolderSongOrder.TITLE -> compareBy<Song> { sortKey(it.title) }
    FolderSongOrder.TRACK -> compareBy<Song> { it.trackNumber }
        .then(Comparator { a, b -> NaturalOrderComparator.compare(a.fileName, b.fileName) })
    FolderSongOrder.DATE_ADDED -> compareByDescending<Song> { it.dateAddedSec }
}

fun albumComparator(field: AlbumSortField, ascending: Boolean): Comparator<Album> {
    val base: Comparator<Album> = when (field) {
        AlbumSortField.TITLE -> compareBy<Album> { sortKey(it.title) }
        AlbumSortField.ARTIST -> compareBy<Album> { sortKey(it.artist) }.thenBy { it.year }
        AlbumSortField.YEAR -> compareBy<Album> { it.year }.thenBy { sortKey(it.title) }
        AlbumSortField.SONG_COUNT -> compareBy<Album> { it.songs.size }
    }
    return if (ascending) base else base.reversed()
}

fun artistComparator(field: ArtistSortField, ascending: Boolean): Comparator<Artist> {
    val base: Comparator<Artist> = when (field) {
        ArtistSortField.NAME -> compareBy<Artist> { sortKey(it.name) }
        ArtistSortField.SONG_COUNT -> compareBy<Artist> { it.songs.size }
        ArtistSortField.ALBUM_COUNT -> compareBy<Artist> { it.albums.size }
    }
    return if (ascending) base else base.reversed()
}

/** Order songs inside an album: disc, track, then file name. */
val albumTrackOrder: Comparator<Song> = compareBy<Song> { it.disc }
    .thenBy { if (it.track == 0) Int.MAX_VALUE else it.track }
    .then(Comparator { a, b -> NaturalOrderComparator.compare(a.fileName, b.fileName) })

/** Letter shown on the fast scroller for a sort field. */
fun scrollLabel(song: Song, field: SongSortField): String? = when (field) {
    SongSortField.TITLE -> sortKey(song.title).firstOrNull()?.uppercase()
    SongSortField.ARTIST -> sortKey(song.displayArtist).firstOrNull()?.uppercase()
    SongSortField.ALBUM -> sortKey(song.displayAlbum).firstOrNull()?.uppercase()
    SongSortField.FILE_NAME -> song.fileName.firstOrNull()?.uppercase()
    SongSortField.YEAR -> song.year.takeIf { it > 0 }?.toString()
    else -> null
}

fun formatDuration(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

fun formatTotalDuration(ms: Long): String {
    val totalMin = ms / 60_000
    val h = totalMin / 60
    val m = totalMin % 60
    return when {
        h > 0 -> "${h} h ${m} min"
        else -> "${m} min"
    }
}

fun formatSize(bytes: Long): String = when {
    bytes >= 1L shl 30 -> "%.2f GB".format(bytes / (1L shl 30).toDouble())
    bytes >= 1L shl 20 -> "%.1f MB".format(bytes / (1L shl 20).toDouble())
    bytes >= 1L shl 10 -> "%.0f KB".format(bytes / (1L shl 10).toDouble())
    else -> "$bytes B"
}

fun pluralize(count: Int, singular: String, plural: String = singular + "s"): String =
    "$count ${if (count == 1) singular else plural}"
