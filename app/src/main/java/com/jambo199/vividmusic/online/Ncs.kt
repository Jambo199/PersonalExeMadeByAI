package com.jambo199.vividmusic.online

import android.net.Uri
import androidx.compose.runtime.Immutable
import com.jambo199.vividmusic.data.Song
import com.jambo199.vividmusic.data.SongSource
import org.json.JSONArray
import org.json.JSONObject

/** An artist as ncs.io links to them, e.g. ("Alan Walker", "/artist/13/alan-walker"). */
@Immutable
data class NcsArtistRef(val name: String, val path: String)

/** One NoCopyrightSounds release, as listed on ncs.io. */
@Immutable
data class NcsTrack(
    /** NCS's own track id. */
    val id: String,
    val title: String,
    /** Artists as NCS writes them, e.g. "gabriawll, Ada Petcu". */
    val artists: String,
    /** The main genre. */
    val genre: String,
    /** e.g. "9 Oct 2026". */
    val released: String,
    /** The full song as an MP3 on NCS's own storage. */
    val url: String,
    /** Small cover (100 px) for lists. */
    val thumbnail: String?,
    /** The track's page on ncs.io, e.g. https://ncs.io/g_desire. */
    val page: String?,
    /** Each artist with their page on ncs.io. */
    val artistLinks: List<NcsArtistRef> = emptyList(),
    /** Every genre NCS tags it with, the main one first. */
    val genres: List<String> = emptyList(),
    val moods: List<String> = emptyList(),
    /** e.g. "Regular, Instrumental". */
    val versions: String = "",
    /** Length when known (from the popularity chart), else 0. */
    val durationMs: Long = 0,
    /** Place in the most-played chart, 1 for the top, 0 when not charted. */
    val rank: Int = 0,
) {
    val key: String get() = Song.NCS_PREFIX + id

    /** The same cover at 325 px, for the player and lock screen. */
    val cover: String? get() = thumbnail?.replace("/100x100/", "/325x325/")

    val year: Int get() = released.takeLast(4).toIntOrNull() ?: 0

    fun toSong(): Song = Song(
        key = key,
        uri = Uri.parse(url),
        mediaStoreId = -1,
        title = title,
        artist = artists,
        album = "NoCopyrightSounds",
        albumArtist = artists,
        albumId = 0,
        durationMs = durationMs,
        trackNumber = 0,
        year = year,
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

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("title", title).put("artists", artists).put("genre", genre)
        .put("released", released).put("url", url).put("thumbnail", thumbnail ?: "").put("page", page ?: "")
        .put("artistLinks", JSONArray().apply { artistLinks.forEach { put(JSONObject().put("name", it.name).put("path", it.path)) } })
        .put("genres", JSONArray(genres)).put("moods", JSONArray(moods)).put("versions", versions)
        .put("durationMs", durationMs).put("rank", rank)

    companion object {
        fun fromJson(o: JSONObject): NcsTrack {
            fun strings(name: String) = o.optJSONArray(name)?.let { a -> List(a.length()) { a.getString(it) } }.orEmpty()
            return NcsTrack(
                id = o.getString("id"),
                title = o.getString("title"),
                artists = o.optString("artists"),
                genre = o.optString("genre"),
                released = o.optString("released"),
                url = o.getString("url"),
                thumbnail = o.optString("thumbnail").ifBlank { null },
                page = o.optString("page").ifBlank { null },
                artistLinks = o.optJSONArray("artistLinks")?.let { a ->
                    List(a.length()) { a.getJSONObject(it).let { x -> NcsArtistRef(x.getString("name"), x.getString("path")) } }
                }.orEmpty(),
                genres = strings("genres"),
                moods = strings("moods"),
                versions = o.optString("versions"),
                durationMs = o.optLong("durationMs"),
                rank = o.optInt("rank"),
            )
        }

        /** What can be told about a song from the player alone (after a restart, say). */
        fun fromSong(song: Song): NcsTrack = NcsTrack(
            id = song.key.removePrefix(Song.NCS_PREFIX),
            title = song.title,
            artists = song.artist,
            genre = song.genre,
            released = song.year.takeIf { it > 0 }?.toString().orEmpty(),
            url = song.uri.toString(),
            thumbnail = song.coverHint?.toString()?.replace("/325x325/", "/100x100/"),
            page = song.path.takeIf { it.startsWith(Ncs.SITE) },
            genres = listOfNotNull(song.genre.ifBlank { null }),
            durationMs = song.durationMs,
        )
    }
}

/** Every NCS release seen this session, so the player's menu can show a song's full details. */
object NcsSeen {
    private val byKey = java.util.concurrent.ConcurrentHashMap<String, NcsTrack>()

    fun add(tracks: List<NcsTrack>) = tracks.forEach { t ->
        // Keep what the chart adds (length, place) when the same song turns up elsewhere.
        val old = byKey[t.key]
        byKey[t.key] = if (old == null) t else t.copy(durationMs = maxOf(t.durationMs, old.durationMs), rank = maxOf(t.rank, old.rank))
    }

    operator fun get(key: String): NcsTrack? = byKey[key]
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

/** An artist's page on ncs.io: their releases, most popular first. */
@Immutable
data class NcsArtist(
    val name: String,
    val path: String,
    val image: String?,
    /** Genres they release in. */
    val tags: String,
    val tracks: List<NcsTrack>,
)

/** The extra details on a track's own page. */
@Immutable
data class NcsTrackDetails(
    /** The text NCS asks video makers to put in their description. */
    val credit: String,
    /** The official video, when NCS links one. */
    val video: String?,
)

/**
 * Reads NoCopyrightSounds' music search on ncs.io. NCS publishes every release there with a
 * free download; the songs themselves come straight from NCS's own storage.
 */
object Ncs {
    const val SITE = "https://ncs.io"

    /** ncs.io may turn away clients that don't look like a browser. */
    private val userAgent by lazy { "Mozilla/5.0 (Linux; Android ${android.os.Build.VERSION.RELEASE}) ${Http.USER_AGENT}" }

    private fun fetch(url: String) = Http.get(url, accept = "text/html", userAgent = userAgent)

    /** Page [page] (from 1) of the newest releases, narrowed by [query], [genre] and [mood] ids. */
    fun search(query: String = "", genre: String? = null, mood: String? = null, page: Int = 1): NcsPage {
        val url = "$SITE/music-search?q=${Http.encode(query.trim())}&genre=${genre.orEmpty()}&mood=${mood.orEmpty()}" +
            if (page > 1) "&page=$page" else ""
        return parse(fetch(url), page)
    }

    /** An artist's page, e.g. [path] "/artist/13/alan-walker". */
    fun artist(path: String): NcsArtist = parseArtist(fetch(SITE + path), path)

    /** The credit text and video link from a track's page ([page] is its full address). */
    fun details(page: String): NcsTrackDetails = parseDetails(fetch(page))

    /**
     * Finds the ncs.io release called [title] by [artist] (names as another service writes
     * them), or null when NCS doesn't have it.
     */
    fun find(title: String, artist: String): NcsTrack? =
        search(baseTitle(title)).tracks.firstOrNull { matches(it, title, artist) }

    // region parsing

    /** An opening <a> tag; quoted values may hold ">" (data-artist carries its own links). */
    private val LINK_TAG = Regex("<a\\s(?:[^>\"']|\"[^\"]*\"|'[^']*')*>")
    private val ATTR = Regex("\\b(data-[a-z]+)=\"([^\"]*)\"")
    private val DATE = Regex(">\\s*(\\d{1,2} [A-Z][a-z]{2} \\d{4})\\s*<")
    private val PAGE_LINK = Regex("<a href=\"(/[^\"/?#]+)\">\\s*<img")
    private val SELECT = Regex("<select[^>]*name=\"([a-z]+)\"[^>]*>(.*?)</select>", RegexOption.DOT_MATCHES_ALL)
    private val OPTION = Regex("<option[^>]*value=\"(\\d+)\"[^>]*>([^<]+)</option>")
    private val TAG_LINK = Regex("<a class=\"tag\"[^>]*href=\"/music-search\\?(genre|mood)=\\d+\"[^>]*>([^<]+)</a>")
    private val ARTIST_LINK = Regex("<a href='(/artist/\\d+/[^']+)'[^>]*>([^<]+)</a>")

    internal fun parse(html: String, page: Int): NcsPage {
        val selects = SELECT.findAll(html).associate { it.groupValues[1] to it.groupValues[2] }
        // ncs.io lists a few twice with different capitals ("Angry", "angry"); keep the first.
        fun options(name: String) = selects[name]?.let { block ->
            OPTION.findAll(block)
                .map { NcsFilter(it.groupValues[1], capitalised(unescape(it.groupValues[2]).trim())) }
                .distinctBy { it.name.lowercase() }
                .toList()
        }.orEmpty()
        val tracks = parseRows(html)
        return NcsPage(
            tracks = tracks,
            hasMore = tracks.isNotEmpty() && Regex("page=${page + 1}(?!\\d)").containsMatchIn(html),
            genres = options("genre"),
            moods = options("mood"),
        )
    }

    /** Every release row (search results and artist pages use the same table). */
    private fun parseRows(html: String): List<NcsTrack> {
        val rows = html.split("<tr").drop(1)
        return rows.mapNotNull { row ->
            val button = LINK_TAG.findAll(row).map { it.value }.firstOrNull { "player-play" in it } ?: return@mapNotNull null
            val attrs = ATTR.findAll(button).associate { it.groupValues[1] to unescape(it.groupValues[2]) }
            val url = attrs["data-url"]?.takeIf { it.startsWith("http") } ?: return@mapNotNull null
            val title = attrs["data-track"].orEmpty().trim()
            if (title.isEmpty()) return@mapNotNull null
            val tags = TAG_LINK.findAll(row).map { it.groupValues[1] to unescape(it.groupValues[2]).trim() }.toList()
            val genre = attrs["data-genre"].orEmpty().trim()
            NcsTrack(
                id = attrs["data-tid"]?.takeIf { it.isNotBlank() } ?: url,
                title = title,
                artists = attrs["data-artistraw"].orEmpty().trim().ifEmpty { "NCS" },
                genre = genre,
                released = DATE.find(row)?.groupValues?.get(1).orEmpty(),
                url = url,
                thumbnail = attrs["data-cover"]?.takeIf { it.startsWith("http") },
                page = PAGE_LINK.find(row)?.groupValues?.get(1)?.let { SITE + it },
                artistLinks = ARTIST_LINK.findAll(attrs["data-artist"].orEmpty())
                    .map { NcsArtistRef(unescape(it.groupValues[2]).trim(), it.groupValues[1]) }
                    .distinctBy { it.path }
                    .toList(),
                genres = (listOf(genre) + tags.filter { it.first == "genre" }.map { it.second })
                    .filter { it.isNotBlank() }
                    .distinctBy { it.lowercase() },
                moods = tags.filter { it.first == "mood" }.map { capitalised(it.second) }.distinctBy { it.lowercase() },
                versions = attrs["data-versions"].orEmpty().trim(),
            )
        }.distinctBy { it.id }
    }

    internal fun parseArtist(html: String, path: String): NcsArtist {
        val info = Regex("<div class=\"info\">\\s*<h5>([^<]+)</h5>\\s*(?:<span class=\"tags\">([^<]*)</span>)?").find(html)
        val name = info?.groupValues?.get(1)
            ?: Regex("<title>([^<|]+)").find(html)?.groupValues?.get(1)
            ?: path.substringAfterLast('/')
        return NcsArtist(
            name = unescape(name).trim(),
            path = path,
            image = Regex("url\\('(https://[^']*/artists/[^']+)'\\)").find(html)?.groupValues?.get(1),
            tags = unescape(info?.groupValues?.get(2).orEmpty()).trim().trimEnd(',').trim(),
            tracks = parseRows(html),
        )
    }

    internal fun parseDetails(html: String): NcsTrackDetails {
        val credit = Regex("<p class=\"p-copy\"[^>]*>(.*?)</p>", RegexOption.DOT_MATCHES_ALL).find(html)?.groupValues?.get(1)
            ?.replace(Regex("<br\\s*/?>"), "\n")
            ?.replace(Regex("<[^>]+>"), "")
            ?.let(::unescape)
            ?.lines()?.map { it.trim() }?.filter { it.isNotEmpty() }?.joinToString("\n")
            .orEmpty()
        return NcsTrackDetails(credit, Regex("Watch:\\s*(\\S+)").find(credit)?.groupValues?.get(1))
    }

    private fun capitalised(text: String) = text.replaceFirstChar(Char::titlecase)

    /** The title without "(feat. …)" parts, which services write differently. */
    internal fun baseTitle(title: String): String = title
        .replace(Regex("\\s*[(\\[](?:feat|ft)\\.?\\s[^)\\]]*[)\\]]", RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\s+(?:feat|ft)\\.\\s.*$", RegexOption.IGNORE_CASE), "")
        .trim()

    private fun norm(text: String): String = text.lowercase()
        .replace("&", " and ")
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()

    /** Whether [track] is the song called [title] by [artist]. */
    internal fun matches(track: NcsTrack, title: String, artist: String): Boolean {
        if (norm(baseTitle(track.title)) != norm(baseTitle(title))) return false
        val a = norm(artist)
        return a.isEmpty() || " ${norm(track.artists)} ".contains(" $a ")
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

    // endregion
}
