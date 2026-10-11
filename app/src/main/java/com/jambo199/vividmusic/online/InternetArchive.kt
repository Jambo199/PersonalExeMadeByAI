package com.jambo199.vividmusic.online

import android.net.Uri
import androidx.compose.runtime.Immutable
import com.jambo199.vividmusic.data.NaturalOrderComparator
import com.jambo199.vividmusic.data.Song
import com.jambo199.vividmusic.data.SongSource
import org.json.JSONArray
import org.json.JSONObject

@Immutable
data class ArchiveItem(
    val id: String,
    val title: String,
    val creator: String,
    val date: String,
) {
    val cover: String get() = "https://archive.org/services/img/$id"
    val year: String get() = date.take(4).takeIf { y -> y.length == 4 && y.all(Char::isDigit) }.orEmpty()
}

@Immutable
data class ArchiveTrack(
    val fileName: String,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val track: Int,
    val url: String,
)

@Immutable
data class ArchiveAlbum(
    val item: ArchiveItem,
    val description: String,
    val license: String,
    val tracks: List<ArchiveTrack>,
) {
    fun songs(): List<Song> = tracks.map { toSong(it) }

    fun toSong(t: ArchiveTrack): Song = Song(
        key = Song.ARCHIVE_PREFIX + item.id + "/" + t.fileName,
        uri = Uri.parse(t.url),
        mediaStoreId = -1,
        title = t.title,
        artist = t.artist.ifBlank { item.creator },
        album = item.title,
        albumArtist = item.creator,
        albumId = 0,
        durationMs = t.durationMs,
        trackNumber = t.track,
        year = item.year.toIntOrNull() ?: 0,
        dateAddedSec = 0,
        dateModifiedSec = 0,
        sizeBytes = 0,
        path = t.url,
        folder = "",
        fileName = t.fileName.substringAfterLast('/'),
        mimeType = "",
        genre = "",
        source = SongSource.ONLINE,
        coverHint = Uri.parse(item.cover),
    )

    /** Where a download of [t] goes, under Music/. */
    fun downloadPath(t: ArchiveTrack): String {
        val ext = t.fileName.substringAfterLast('.', "mp3")
        val number = if (t.track > 0) "%02d - ".format(t.track) else ""
        return listOf(
            "Vivid Music",
            Downloads.safeName(item.creator.ifBlank { "Internet Archive" }),
            Downloads.safeName(item.title),
            Downloads.safeName(number + t.title) + "." + ext,
        ).joinToString("/")
    }
}

/**
 * Free, legal music from the Internet Archive (archive.org): the Live Music Archive (concerts
 * from bands that allow sharing them) and netlabel releases under Creative Commons licences.
 * No account or key needed.
 */
object InternetArchive {
    enum class Shelf(val title: String, val subtitle: String, val query: String, val sort: String) {
        LIVE("Live concerts", "Most played shows from the Live Music Archive", "collection:(etree)", "downloads desc"),
        NETLABELS("Netlabel releases", "Free albums from independent labels", "collection:(netlabels)", "downloads desc"),
        NEW_LIVE("Just added concerts", "Fresh uploads from the Live Music Archive", "collection:(etree)", "addeddate desc"),
    }

    private const val COLLECTIONS = "collection:(etree OR netlabels)"

    fun shelf(shelf: Shelf, rows: Int = 30): List<ArchiveItem> = search(null, shelf.query, shelf.sort, rows)

    fun search(text: String?, scope: String = COLLECTIONS, sort: String = "downloads desc", rows: Int = 50): List<ArchiveItem> {
        val q = buildString {
            if (!text.isNullOrBlank()) append("(").append(text.trim()).append(") AND ")
            append("mediatype:(audio) AND ").append(scope)
        }
        val fields = listOf("identifier", "title", "creator", "date").joinToString("") { "&fl%5B%5D=$it" }
        val url = "https://archive.org/advancedsearch.php?q=${Http.encode(q)}$fields&rows=$rows&page=1&output=json" +
            "&sort%5B%5D=${Http.encode(sort)}"
        return parseSearch(Http.get(url))
    }

    fun album(id: String): ArchiveAlbum = parseAlbum(id, Http.get("https://archive.org/metadata/${Http.encode(id)}"))

    fun parseSearch(json: String): List<ArchiveItem> {
        val docs = JSONObject(json).optJSONObject("response")?.optJSONArray("docs") ?: return emptyList()
        return List(docs.length()) { docs.getJSONObject(it) }.map { d ->
            ArchiveItem(
                id = d.optString("identifier"),
                title = text(d, "title").ifBlank { d.optString("identifier") },
                creator = text(d, "creator"),
                date = text(d, "date"),
            )
        }.filter { it.id.isNotBlank() }
    }

    /** Audio formats the Archive offers, best for streaming first. */
    private val FORMATS = listOf(
        "VBR MP3", "MP3", "128Kbps MP3", "64Kbps MP3", "Ogg Vorbis", "Flac", "24bit Flac", "Apple Lossless Audio", "WAVE",
    )

    fun parseAlbum(id: String, json: String): ArchiveAlbum {
        val root = JSONObject(json)
        val meta = root.optJSONObject("metadata") ?: JSONObject()
        val item = ArchiveItem(
            id = id,
            title = text(meta, "title").ifBlank { id },
            creator = text(meta, "creator"),
            date = text(meta, "date"),
        )
        val files = root.optJSONArray("files") ?: JSONArray()
        val all = List(files.length()) { files.getJSONObject(it) }
        val format = FORMATS.firstOrNull { f -> all.any { it.optString("format") == f } }
        val tracks = all
            .filter { format != null && it.optString("format") == format }
            .map { f ->
                val name = f.optString("name")
                ArchiveTrack(
                    fileName = name,
                    title = f.optString("title").trim().ifBlank { name.substringAfterLast('/').substringBeforeLast('.') },
                    artist = f.optString("artist").ifBlank { f.optString("creator") }.trim(),
                    durationMs = parseLength(f.optString("length")),
                    track = f.optString("track").takeWhile(Char::isDigit).toIntOrNull() ?: 0,
                    url = "https://archive.org/download/${Http.encodePath(id)}/${Http.encodePath(name)}",
                )
            }
            .sortedWith(compareBy<ArchiveTrack> { if (it.track > 0) it.track else Int.MAX_VALUE }.thenBy(NaturalOrderComparator) { it.fileName })
        return ArchiveAlbum(
            item = item,
            description = stripHtml(text(meta, "description")),
            license = meta.optString("licenseurl"),
            tracks = tracks,
        )
    }

    /** "312.45" seconds or "5:12" / "1:02:03". */
    fun parseLength(text: String): Long {
        val t = text.trim()
        if (t.isEmpty()) return 0
        if (':' in t) {
            val parts = t.split(':').map { it.toDoubleOrNull() ?: return 0 }
            return (parts.fold(0.0) { acc, p -> acc * 60 + p } * 1000).toLong()
        }
        return ((t.toDoubleOrNull() ?: 0.0) * 1000).toLong()
    }

    /** Archive fields can be a string or a list of strings. */
    private fun text(o: JSONObject, key: String): String {
        val v = o.opt(key) ?: return ""
        return when (v) {
            is JSONArray -> List(v.length()) { v.optString(it) }.filter { it.isNotBlank() }.joinToString(", ")
            else -> v.toString()
        }.trim()
    }

    fun stripHtml(html: String): String = html
        .replace(Regex("(?i)<br\\s*/?>|</p>"), "\n")
        .replace(Regex("<[^>]+>"), "")
        .replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&nbsp;", " ")
        .lines().joinToString("\n") { it.trim() }
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()

    /** "Creative Commons BY-NC-SA" for a licence URL, or null when there isn't one. */
    fun licenseName(url: String): String? {
        if (url.isBlank()) return null
        val cc = Regex("creativecommons\\.org/(licenses|publicdomain)/([a-z-]+)").find(url)
        return when {
            cc == null -> "See licence"
            cc.groupValues[1] == "publicdomain" -> "Public domain"
            else -> "Creative Commons " + cc.groupValues[2].uppercase()
        }
    }
}
