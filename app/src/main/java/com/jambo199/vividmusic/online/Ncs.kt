package com.jambo199.vividmusic.online

import android.net.Uri
import androidx.compose.runtime.Immutable
import com.jambo199.vividmusic.data.Song
import com.jambo199.vividmusic.data.SongSource

/** One NoCopyrightSounds release, as listed on ncs.io. */
@Immutable
data class NcsTrack(
    /** NCS's own track id. */
    val id: String,
    val title: String,
    /** Artists as NCS writes them, e.g. "gabriawll, Ada Petcu". */
    val artists: String,
    val genre: String,
    /** e.g. "9 Oct 2026". */
    val released: String,
    /** The full song as an MP3 on NCS's own storage. */
    val url: String,
    /** Small cover (100 px) for lists. */
    val thumbnail: String?,
    /** The track's page on ncs.io, e.g. https://ncs.io/g_desire. */
    val page: String?,
) {
    val key: String get() = Song.NCS_PREFIX + id

    /** The same cover at 325 px, for the player and lock screen. */
    val cover: String? get() = thumbnail?.replace("/100x100/", "/325x325/")

    fun toSong(): Song = Song(
        key = key,
        uri = Uri.parse(url),
        mediaStoreId = -1,
        title = title,
        artist = artists,
        album = "NoCopyrightSounds",
        albumArtist = artists,
        albumId = 0,
        durationMs = 0,
        trackNumber = 0,
        year = released.takeLast(4).toIntOrNull() ?: 0,
        dateAddedSec = 0,
        dateModifiedSec = 0,
        sizeBytes = 0,
        path = page ?: url,
        folder = "",
        fileName = url.substringAfterLast('/'),
        mimeType = "audio/mpeg",
        genre = genre,
        source = SongSource.ONLINE,
        coverHint = cover?.let(Uri::parse),
    )

    /** Where a download goes, under Music/. */
    val downloadPath: String
        get() = "NCS/" + Downloads.safeName("$artists - $title") + "." + url.substringAfterLast('.', "mp3").substringBefore('?').take(4)
}

@Immutable
data class NcsFilter(val id: String, val name: String)

@Immutable
data class NcsPage(
    val tracks: List<NcsTrack>,
    val hasMore: Boolean,
    val genres: List<NcsFilter>,
    val moods: List<NcsFilter>,
)

/**
 * Reads NoCopyrightSounds' music search on ncs.io. NCS publishes every release there with a
 * free download; the songs themselves come straight from NCS's own storage.
 */
object Ncs {
    const val SITE = "https://ncs.io"

    /** ncs.io may turn away clients that don't look like a browser. */
    private val userAgent by lazy { "Mozilla/5.0 (Linux; Android ${android.os.Build.VERSION.RELEASE}) ${Http.USER_AGENT}" }

    /** Page [page] (from 1) of the newest releases, narrowed by [query], [genre] and [mood] ids. */
    fun search(query: String = "", genre: String? = null, mood: String? = null, page: Int = 1): NcsPage {
        val url = "$SITE/music-search?q=${Http.encode(query.trim())}&genre=${genre.orEmpty()}&mood=${mood.orEmpty()}" +
            if (page > 1) "&page=$page" else ""
        return parse(Http.get(url, accept = "text/html", userAgent = userAgent), page)
    }

    /** An opening <a> tag; quoted values may hold ">" (data-artist carries its own links). */
    private val LINK_TAG = Regex("<a\\s(?:[^>\"']|\"[^\"]*\"|'[^']*')*>")
    private val ATTR = Regex("\\b(data-[a-z]+)=\"([^\"]*)\"")
    private val DATE = Regex(">\\s*(\\d{1,2} [A-Z][a-z]{2} \\d{4})\\s*<")
    private val PAGE_LINK = Regex("<a href=\"(/[^\"/?#]+)\">\\s*<img")
    private val SELECT = Regex("<select[^>]*name=\"([a-z]+)\"[^>]*>(.*?)</select>", RegexOption.DOT_MATCHES_ALL)
    private val OPTION = Regex("<option[^>]*value=\"(\\d+)\"[^>]*>([^<]+)</option>")

    internal fun parse(html: String, page: Int): NcsPage {
        // Each release is one table row; the play button carries most of what we need.
        val rows = html.split("<tr").drop(1)
        val tracks = rows.mapNotNull { row ->
            val button = LINK_TAG.findAll(row).map { it.value }.firstOrNull { "player-play" in it } ?: return@mapNotNull null
            val attrs = ATTR.findAll(button).associate { it.groupValues[1] to unescape(it.groupValues[2]) }
            val url = attrs["data-url"]?.takeIf { it.startsWith("http") } ?: return@mapNotNull null
            val title = attrs["data-track"].orEmpty().trim()
            if (title.isEmpty()) return@mapNotNull null
            NcsTrack(
                id = attrs["data-tid"]?.takeIf { it.isNotBlank() } ?: url,
                title = title,
                artists = attrs["data-artistraw"].orEmpty().trim().ifEmpty { "NCS" },
                genre = attrs["data-genre"].orEmpty().trim(),
                released = DATE.find(row)?.groupValues?.get(1).orEmpty(),
                url = url,
                thumbnail = attrs["data-cover"]?.takeIf { it.startsWith("http") },
                page = PAGE_LINK.find(row)?.groupValues?.get(1)?.let { SITE + it },
            )
        }.distinctBy { it.id }
        val selects = SELECT.findAll(html).associate { it.groupValues[1] to it.groupValues[2] }
        // ncs.io lists a few twice with different capitals ("Angry", "angry"); keep the first.
        fun options(name: String) = selects[name]?.let { block ->
            OPTION.findAll(block)
                .map { NcsFilter(it.groupValues[1], unescape(it.groupValues[2]).trim().replaceFirstChar(Char::titlecase)) }
                .distinctBy { it.name.lowercase() }
                .toList()
        }.orEmpty()
        return NcsPage(
            tracks = tracks,
            hasMore = tracks.isNotEmpty() && Regex("page=${page + 1}(?!\\d)").containsMatchIn(html),
            genres = options("genre"),
            moods = options("mood"),
        )
    }

    /** Decodes the HTML entities ncs.io uses (&amp;, &#039;, …). */
    internal fun unescape(text: String): String {
        if ('&' !in text) return text
        return Regex("&(#x?[0-9a-fA-F]+|[a-z]+);").replace(text) { m ->
            val name = m.groupValues[1]
            when {
                name.startsWith("#x") || name.startsWith("#X") -> name.drop(2).toIntOrNull(16)?.let { String(Character.toChars(it)) }
                name.startsWith("#") -> name.drop(1).toIntOrNull()?.let { String(Character.toChars(it)) }
                else -> ENTITIES[name]
            } ?: m.value
        }
    }

    private val ENTITIES = mapOf("amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ")
}
