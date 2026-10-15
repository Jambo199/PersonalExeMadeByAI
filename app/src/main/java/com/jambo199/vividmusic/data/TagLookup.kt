package com.jambo199.vividmusic.data

import android.util.Log
import com.jambo199.vividmusic.online.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Tags for a song suggested by one source, and how well they fit it ([score], 0–100). */
data class TagMatch(
    val source: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val albumArtist: String = "",
    val genre: String = "",
    val year: String = "",
    val track: String = "",
    val durationMs: Long = 0,
    /** A big cover picture, and another place to try when that one isn't there. */
    val artworkUrl: String? = null,
    val artworkFallback: String? = null,
    /** A small cover for the list. */
    val thumbUrl: String? = null,
    /** Deezer's track and album IDs, for filling in the rest once it's picked. */
    val deezerTrack: Long = 0,
    val deezerAlbum: Long = 0,
    val score: Int = 0,
) {
    val summary: String get() = listOf(artist, album, year).filter { it.isNotBlank() }.joinToString(" · ")
}

/** What a song is, as far as can be told, for looking it up. */
data class TagQuery(val title: String, val artist: String, val album: String, val durationMs: Long) {
    companion object {
        fun of(song: Song): TagQuery {
            val fileBase = song.fileName.substringBeforeLast('.')
            val guess = LocalTags.guess(song.fileName, LocalTags.folders(song.path))
            val tagged = TagText.known(song.title) && !song.title.equals(fileBase, ignoreCase = true)
            return TagQuery(
                title = if (tagged) song.title.trim() else guess?.title ?: TagText.junkless(fileBase),
                artist = song.artist.takeIf(TagText::known)?.trim() ?: guess?.artist.orEmpty(),
                album = song.album.takeIf(TagText::known)?.trim() ?: guess?.album.orEmpty(),
                durationMs = song.durationMs,
            )
        }
    }
}

/** Small helpers for the words in tags. */
object TagText {
    private val UNKNOWN = setOf("<unknown>", "unknown", "unknown artist", "unknown album", Song.UNKNOWN_ARTIST.lowercase(), Song.UNKNOWN_ALBUM.lowercase())

    /** A real value, not empty or a placeholder like "<unknown>". */
    fun known(text: String?): Boolean = !text.isNullOrBlank() && text.trim().lowercase() !in UNKNOWN

    private val JUNK = Regex(
        "\\s*[(\\[](official|lyrics?|lyric video|audio|video|music video|hq|hd|4k|visuali[sz]er|explicit|clean)[^)\\]]*[)\\]]",
        RegexOption.IGNORE_CASE,
    )

    /** Without "(Official Video)", "[Lyrics]", "_" for spaces and the like, which come from video downloads. */
    fun junkless(text: String): String = text
        .replace('_', ' ')
        .replace(JUNK, "")
        .replace(Regex("\\s+"), " ")
        .trim()

    /** Lower case, no accents, brackets, "feat. X" or punctuation: for comparing. */
    fun normalize(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .lowercase(Locale.ROOT)
        .replace(Regex("[(\\[][^)\\]]*[)\\]]"), " ")
        .replace(Regex("\\s(feat|ft|featuring)\\b.*$"), " ")
        .replace("&", " and ")
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()
}

/**
 * How alike two names are (0–1): the same words in any order, one name inside the other, or a
 * few letters off (typos, "Pt." for "Part").
 */
object TagScore {
    fun similarity(a: String, b: String): Double {
        val x = TagText.normalize(a)
        val y = TagText.normalize(b)
        if (x.isEmpty() || y.isEmpty()) return 0.0
        if (x == y) return 1.0
        val wx = x.split(' ').toSet()
        val wy = y.split(' ').toSet()
        val common = wx.intersect(wy).size
        val words = common.toDouble() / maxOf(wx.size, wy.size)
        val shorter = minOf(wx.size, wy.size)
        // "One More Time" in "One More Time Radio Edit"; a single word inside a long name counts less.
        val inside = if (common == shorter) (if (shorter >= 2) 0.85 else 0.5) else 0.0
        val letters = 1.0 - editDistance(x, y).toDouble() / maxOf(x.length, y.length)
        return maxOf(words, inside, letters)
    }

    /** Artists, also when one side lists more of them ("Daft Punk" and "Daft Punk, Romanthony"). */
    fun artistSimilarity(a: String, b: String): Double {
        val first = { s: String -> s.split(Regex("\\s*(,|&|/|;|\\bx\\b|\\band\\b|\\bwith\\b|\\bfeat\\.?|\\bft\\.?)\\s*", RegexOption.IGNORE_CASE)).first() }
        return maxOf(similarity(a, b), similarity(first(a), first(b)) * 0.95)
    }

    /** How well [m] fits the song in [q], 0–100. */
    fun score(q: TagQuery, m: TagMatch): Int {
        val title = similarity(q.title, m.title)
        val artist = if (TagText.known(q.artist)) artistSimilarity(q.artist, m.artist) else 0.6
        val diff = abs(q.durationMs - m.durationMs)
        val length = when {
            q.durationMs <= 0 || m.durationMs <= 0 -> 0.6
            diff <= 2_000 -> 1.0
            diff <= 5_000 -> 0.85
            diff <= 10_000 -> 0.6
            diff <= 30_000 -> 0.3
            else -> 0.0
        }
        var s = 0.5 * title + 0.3 * artist + 0.2 * length
        if (TagText.known(q.album) && m.album.isNotBlank() && similarity(q.album, m.album) >= 0.8) s += 0.05
        // A very different length is another recording (a live one, a long mix).
        if (length == 0.0) s = minOf(s, 0.6)
        var result = (s * 100).roundToInt().coerceIn(0, 100)
        // With no artist to go on, a title alone ("Intro", "Track 2") is never sure enough to pick by itself.
        if (!TagText.known(q.artist)) result = minOf(result, 75)
        return result
    }

    private fun editDistance(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            }
            val t = prev
            prev = cur
            cur = t
        }
        return prev[b.length]
    }
}

/**
 * Tags worked out on the phone, from the file's name and its folders: "01 - Artist - Title.mp3"
 * in "Artist/Album (2001)/", and the like.
 */
object LocalTags {
    const val SOURCE = "File and folder names"

    // Folders that say nothing about the music in them.
    private val GENERIC = setOf(
        "", "0", "music", "musique", "música", "musica", "musik", "download", "downloads", "audio", "audios", "songs", "song",
        "mp3", "media", "sdcard", "storage", "emulated", "documents", "telegram", "whatsapp", "whatsapp audio", "recordings",
        "ringtones", "notifications", "alarms", "podcasts", "unknown", "various", "various artists", "misc", "new folder",
        "bluetooth", "snaptube", "vidmate", "music downloads", "my music",
    )

    private val DASH = Regex("\\s+[-–—]\\s+")
    private val TRACK_FIRST = Regex("^(?:(\\d)[-.])?(\\d{1,3})(\\s*[.)]\\s*|\\s+[-–—]\\s+|\\s+)(\\D.*)$")
    private val YEAR = Regex("[(\\[]((?:19|20)\\d\\d)[)\\]]|^((?:19|20)\\d\\d)\\s*[-–—.]\\s+|\\s+[-–—]\\s+((?:19|20)\\d\\d)$")

    /** The folders a file is in, nearest first. */
    fun folders(path: String): List<String> = path.replace('\\', '/').split('/').dropLast(1).reversed().filter { it.isNotBlank() }

    fun guess(fileName: String, folders: List<String>): TagMatch? {
        var name = TagText.junkless(fileName.substringBeforeLast('.'))
        if (name.isBlank()) return null
        var track = ""
        TRACK_FIRST.matchEntire(name)?.let { m ->
            // "10 Years - Wasteland" is a band, not track 10: a track number has a dot, bracket or
            // dash after it ("3. ", "03) ", "3 - "), or a leading zero ("03 Song").
            val number = m.groupValues[2]
            val rest = m.groupValues[4].trim()
            if (rest.isNotEmpty() && (m.groupValues[3].isNotBlank() || (number.length >= 2 && number.startsWith("0")))) {
                track = number.trimStart('0').ifEmpty { "0" }
                name = rest
            }
        }
        val parts = name.split(DASH).map { it.trim() }.filter { it.isNotEmpty() }
        var artist = ""
        var title = name
        if (parts.size >= 2) {
            artist = parts.first()
            title = parts.last()
        }
        var album = ""
        var year = ""
        var albumArtist = ""
        val near = folders.firstOrNull()?.trim().orEmpty()
        if (near.lowercase() !in GENERIC && !near.startsWith(".")) {
            var folder = near
            YEAR.find(folder)?.let { m ->
                year = m.groupValues.drop(1).first { it.isNotEmpty() }
                folder = folder.removeRange(m.range).trim()
            }
            val folderParts = folder.split(DASH).map { it.trim() }.filter { it.isNotEmpty() }
            if (folderParts.size >= 2) {
                albumArtist = folderParts.first()
                album = folderParts.drop(1).joinToString(" - ")
            } else {
                album = folder
                // Artist/Album/ layouts.
                folders.getOrNull(1)?.trim()?.takeIf { it.lowercase() !in GENERIC && !it.startsWith(".") }?.let { albumArtist = it }
            }
        }
        if (artist.isBlank()) artist = albumArtist
        if (title.isBlank()) return null
        // Nothing learnt beyond the title the song already shows: not worth suggesting.
        if (artist.isBlank() && album.isBlank() && track.isBlank() && year.isBlank()) return null
        return TagMatch(
            source = SOURCE,
            title = title,
            artist = artist,
            album = album,
            albumArtist = albumArtist.takeIf { it.isNotBlank() && !it.equals(artist, ignoreCase = true) }.orEmpty(),
            year = year,
            track = track,
        )
    }
}

/** What the online sources found for a song, best first, and the sources that couldn't be reached. */
data class TagSearch(val matches: List<TagMatch>, val unreachable: List<String>)

/**
 * Looks songs up in online music catalogues: iTunes, Deezer and MusicBrainz (free, no account
 * needed). Each answer gets a score for how well it fits the song.
 */
class TagLookup {
    /** Blocking calls inside; [batch] goes easier on the catalogues (and skips iTunes when the others are sure). */
    suspend fun search(q: TagQuery, batch: Boolean = false): TagSearch = withContext(Dispatchers.IO) {
        if (q.title.isBlank()) return@withContext TagSearch(emptyList(), emptyList())
        val unreachable = mutableListOf<String>()
        suspend fun from(name: String, block: () -> List<TagMatch>): List<TagMatch> = try {
            block()
        } catch (e: Exception) {
            Log.w(TAG, "$name: $e")
            synchronized(unreachable) { unreachable += name }
            emptyList()
        }
        val found = coroutineScope {
            val deezer = async { from(DEEZER) { deezer(q) } }
            val musicBrainz = async { from(MUSICBRAINZ) { musicBrainz(q) } }
            val first = deezer.await() + musicBrainz.await()
            val sure = first.maxOfOrNull { TagScore.score(q, it) } ?: 0
            if (batch && sure >= 92) first else first + from(ITUNES) { iTunes(q) }
        }
        TagSearch(rank(q, found), unreachable)
    }

    /** Fills in what a match's source only gives on a second look (Deezer's year, track number and genre). */
    suspend fun complete(m: TagMatch): TagMatch = withContext(Dispatchers.IO) {
        if (m.deezerTrack <= 0) return@withContext m
        try {
            val track = JSONObject(throttled(DEEZER) { Http.get("$DEEZER_API/track/${m.deezerTrack}") })
            val albumId = track.optJSONObject("album")?.optLong("id") ?: m.deezerAlbum
            val album = if (albumId > 0) JSONObject(throttled(DEEZER) { Http.get("$DEEZER_API/album/$albumId") }) else JSONObject()
            Parse.deezerDetails(m, track, album)
        } catch (e: Exception) {
            Log.w(TAG, "Deezer details: $e")
            m
        }
    }

    /** The match's cover as JPEG or PNG bytes, or null. */
    suspend fun artwork(m: TagMatch): ByteArray? = withContext(Dispatchers.IO) {
        listOfNotNull(m.artworkUrl, m.artworkFallback).firstNotNullOfOrNull { url ->
            try {
                Http.getBytes(url, maxBytes = 6_000_000).takeIf(::isImage)
            } catch (e: Exception) {
                Log.i(TAG, "No cover at $url: $e")
                null
            }
        }
    }

    private fun iTunes(q: TagQuery): List<TagMatch> {
        val country = Locale.getDefault().country.takeIf { it.length == 2 } ?: "US"
        val term = listOf(q.artist, searchTitle(q.title)).filter { it.isNotBlank() }.joinToString(" ")
        val body = throttled(ITUNES) {
            Http.get("https://itunes.apple.com/search?term=${Http.encode(term)}&media=music&entity=song&limit=10&country=$country")
        }
        return Parse.iTunes(body)
    }

    private fun deezer(q: TagQuery): List<TagMatch> {
        val title = searchTitle(q.title)
        val query = if (TagText.known(q.artist)) "artist:\"${q.artist.replace("\"", "")}\" track:\"${title.replace("\"", "")}\"" else title
        var results = Parse.deezer(throttled(DEEZER) { Http.get("$DEEZER_API/search?q=${Http.encode(query)}&limit=10") })
        // The strict search finds nothing when a name is spelt a bit differently; try a plain one.
        if (results.isEmpty() && TagText.known(q.artist)) {
            results = Parse.deezer(throttled(DEEZER) { Http.get("$DEEZER_API/search?q=${Http.encode("${q.artist} $title")}&limit=10") })
        }
        return results
    }

    private fun musicBrainz(q: TagQuery): List<TagMatch> {
        fun quoted(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
        val query = buildString {
            append("recording:").append(quoted(searchTitle(q.title)))
            if (TagText.known(q.artist)) append(" AND artist:").append(quoted(q.artist))
        }
        val body = throttled(MUSICBRAINZ) {
            Http.get("https://musicbrainz.org/ws/2/recording?query=${Http.encode(query)}&fmt=json&limit=10")
        }
        return Parse.musicBrainz(body)
    }

    companion object {
        private const val TAG = "TagLookup"
        const val ITUNES = "iTunes"
        const val DEEZER = "Deezer"
        const val MUSICBRAINZ = "MusicBrainz"
        private const val DEEZER_API = "https://api.deezer.com"

        // How often each catalogue may be asked: MusicBrainz asks for one a second, iTunes about
        // twenty a minute, Deezer fifty in five seconds.
        private val GAP_MS = mapOf(MUSICBRAINZ to 1_100L, ITUNES to 3_000L, DEEZER to 120L)
        private val lastAsked = HashMap<String, Long>()

        private fun <T> throttled(source: String, block: () -> T): T {
            val gap = GAP_MS[source] ?: 0L
            val wait = synchronized(lastAsked) {
                val now = System.currentTimeMillis()
                val next = maxOf(now, (lastAsked[source] ?: 0L) + gap)
                lastAsked[source] = next
                next - now
            }
            if (wait > 0) Thread.sleep(wait)
            return block()
        }

        /** The title without "(Remastered 2011)", "feat. X" and video-download clutter, for searching. */
        fun searchTitle(title: String): String = LyricsQuery(TagText.junkless(title), "", "", 0).searchTitle

        /** Scored, best first, one of each (the same song from two sources keeps the better-filled one). */
        fun rank(q: TagQuery, found: List<TagMatch>): List<TagMatch> {
            val scored = found.map { it.copy(score = TagScore.score(q, it)) }
            fun filled(m: TagMatch) = listOf(m.album, m.year, m.track, m.genre, m.artworkUrl.orEmpty()).count { it.isNotBlank() }
            return scored
                .sortedWith(compareByDescending<TagMatch> { it.score }.thenByDescending { filled(it) })
                .distinctBy { listOf(TagText.normalize(it.title), TagText.normalize(it.artist), TagText.normalize(it.album)) }
                .take(12)
        }

        /** The match to use without asking: the best, when it's at least [minScore]. */
        fun best(matches: List<TagMatch>, minScore: Int): TagMatch? = matches.firstOrNull()?.takeIf { it.score >= minScore }

        fun isImage(b: ByteArray): Boolean =
            b.size > 8 && ((b[0] == 0xFF.toByte() && b[1] == 0xD8.toByte()) || (b[0] == 0x89.toByte() && b[1] == 'P'.code.toByte() && b[2] == 'N'.code.toByte()))
    }

    /** Reads each catalogue's answers. */
    object Parse {
        fun iTunes(body: String): List<TagMatch> {
            val results = JSONObject(body).optJSONArray("results") ?: return emptyList()
            return objects(results).filter { it.optString("kind") == "song" || it.has("trackName") }.mapNotNull { o ->
                val title = o.optString("trackName").ifBlank { return@mapNotNull null }
                val art = o.optString("artworkUrl100").ifBlank { null }
                TagMatch(
                    source = ITUNES,
                    title = title,
                    artist = o.optString("artistName"),
                    album = o.optString("collectionName").replace(Regex("\\s+-\\s+(Single|EP)$"), ""),
                    albumArtist = o.optString("collectionArtistName").ifBlank { o.optString("artistName") },
                    genre = o.optString("primaryGenreName"),
                    year = o.optString("releaseDate").take(4).takeIf { it.all(Char::isDigit) }.orEmpty(),
                    track = o.optInt("trackNumber", 0).takeIf { it > 0 }?.toString().orEmpty(),
                    durationMs = o.optLong("trackTimeMillis", 0),
                    artworkUrl = art?.replace("100x100bb", "1000x1000bb"),
                    thumbUrl = art?.replace("100x100bb", "200x200bb"),
                )
            }
        }

        fun deezer(body: String): List<TagMatch> {
            val o = JSONObject(body)
            o.optJSONObject("error")?.let { throw java.io.IOException("Deezer: ${it.optString("message")}") }
            val data = o.optJSONArray("data") ?: return emptyList()
            return objects(data).mapNotNull { t ->
                val title = t.optString("title").ifBlank { return@mapNotNull null }
                val album = t.optJSONObject("album")
                TagMatch(
                    source = DEEZER,
                    title = title,
                    artist = t.optJSONObject("artist")?.optString("name").orEmpty(),
                    album = album?.optString("title").orEmpty(),
                    durationMs = t.optLong("duration", 0) * 1000,
                    artworkUrl = album?.optString("cover_xl")?.ifBlank { null },
                    thumbUrl = album?.optString("cover_medium")?.ifBlank { null },
                    deezerTrack = t.optLong("id", 0),
                    deezerAlbum = album?.optLong("id", 0) ?: 0,
                )
            }
        }

        fun deezerDetails(m: TagMatch, track: JSONObject, album: JSONObject): TagMatch {
            val date = track.optString("release_date").ifBlank { album.optString("release_date") }
            val genre = album.optJSONObject("genres")?.optJSONArray("data")?.let { objects(it).firstOrNull()?.optString("name") }.orEmpty()
            return m.copy(
                year = date.take(4).takeIf { it.length == 4 && it.all(Char::isDigit) } ?: m.year,
                track = track.optInt("track_position", 0).takeIf { it > 0 }?.toString() ?: m.track,
                genre = genre.ifBlank { m.genre },
                albumArtist = album.optJSONObject("artist")?.optString("name")?.ifBlank { null } ?: m.albumArtist,
            )
        }

        fun musicBrainz(body: String): List<TagMatch> {
            val recordings = JSONObject(body).optJSONArray("recordings") ?: return emptyList()
            return objects(recordings).mapNotNull { r ->
                val title = r.optString("title").ifBlank { return@mapNotNull null }
                val artist = credit(r.optJSONArray("artist-credit"))
                val release = bestRelease(r.optJSONArray("releases"))
                val group = release?.optJSONObject("release-group")
                val trackNo = release?.optJSONArray("media")?.optJSONObject(0)?.optJSONArray("track")?.optJSONObject(0)
                    ?.optString("number")?.filter(Char::isDigit).orEmpty()
                val date = r.optString("first-release-date").ifBlank { release?.optString("date").orEmpty() }
                val releaseId = release?.optString("id")?.ifBlank { null }
                val groupId = group?.optString("id")?.ifBlank { null }
                val genre = r.optJSONArray("tags")?.let { tags -> objects(tags).maxByOrNull { it.optInt("count") }?.optString("name") }.orEmpty()
                TagMatch(
                    source = MUSICBRAINZ,
                    title = title,
                    artist = artist,
                    album = release?.optString("title").orEmpty(),
                    albumArtist = credit(release?.optJSONArray("artist-credit")).takeIf { it.isNotBlank() && it != artist }.orEmpty(),
                    genre = genre.split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.titlecase(Locale.ROOT) } },
                    year = date.take(4).takeIf { it.length == 4 && it.all(Char::isDigit) }.orEmpty(),
                    track = trackNo.trimStart('0'),
                    durationMs = r.optLong("length", 0),
                    artworkUrl = releaseId?.let { "https://coverartarchive.org/release/$it/front-1200" },
                    artworkFallback = groupId?.let { "https://coverartarchive.org/release-group/$it/front-1200" },
                    thumbUrl = releaseId?.let { "https://coverartarchive.org/release/$it/front-250" },
                )
            }
        }

        /** "Artist feat. Other" from an artist-credit list. */
        private fun credit(a: JSONArray?): String =
            a?.let { objects(it).joinToString("") { c -> c.optString("name").ifBlank { c.optJSONObject("artist")?.optString("name").orEmpty() } + c.optString("joinphrase") } }
                ?.trim().orEmpty()

        /** The album to file it under: an official album first, then singles and EPs, compilations last; the earliest. */
        private fun bestRelease(releases: JSONArray?): JSONObject? {
            val list = releases?.let(::objects).orEmpty()
            fun rank(r: JSONObject): Int {
                val g = r.optJSONObject("release-group")
                val type = g?.optString("primary-type").orEmpty()
                val secondary = g?.optJSONArray("secondary-types")?.length() ?: 0
                return (if (r.optString("status") == "Official") 0 else 4) +
                    (if (secondary > 0) 2 else 0) +
                    when (type) { "Album" -> 0; "EP", "Single" -> 1; else -> 2 }
            }
            return list.minWithOrNull(compareBy<JSONObject> { rank(it) }.thenBy { it.optString("date").ifBlank { "9999" } })
        }

        private fun objects(a: JSONArray): List<JSONObject> = (0 until a.length()).mapNotNull { a.optJSONObject(it) }
    }
}
