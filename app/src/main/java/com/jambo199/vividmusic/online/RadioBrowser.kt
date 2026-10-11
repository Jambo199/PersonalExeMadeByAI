package com.jambo199.vividmusic.online

import android.net.Uri
import androidx.compose.runtime.Immutable
import com.jambo199.vividmusic.data.Song
import com.jambo199.vividmusic.data.SongSource
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

@Immutable
data class RadioStation(
    val id: String,
    val name: String,
    val url: String,
    val favicon: String,
    val country: String,
    val countryCode: String,
    val tags: List<String>,
    val codec: String,
    val bitrate: Int,
    /** HLS streams need to be told apart, the player can't guess them from the address. */
    val hls: Boolean = false,
) {
    /** "Jazz, Soul · France · MP3 128 kbps" */
    val subtitle: String
        get() = listOfNotNull(
            tags.take(2).joinToString(", ") { it.replaceFirstChar(Char::uppercase) }.ifBlank { null },
            country.ifBlank { null },
            listOfNotNull(codec.ifBlank { null }, bitrate.takeIf { it > 0 }?.let { "$it kbps" }).joinToString(" ").ifBlank { null },
        ).joinToString(" · ")

    fun toSong(): Song = Song(
        key = Song.RADIO_PREFIX + id,
        uri = Uri.parse(url),
        mediaStoreId = -1,
        title = name,
        artist = listOfNotNull(tags.firstOrNull()?.replaceFirstChar(Char::uppercase), country.ifBlank { null }).joinToString(" · ")
            .ifBlank { "Internet radio" },
        album = name,
        albumArtist = "",
        albumId = 0,
        durationMs = 0,
        trackNumber = 0,
        year = 0,
        dateAddedSec = 0,
        dateModifiedSec = 0,
        sizeBytes = 0,
        path = url,
        folder = "",
        fileName = "",
        mimeType = if (hls) HLS_MIME else codec,
        genre = tags.firstOrNull().orEmpty(),
        source = SongSource.ONLINE,
        coverHint = favicon.takeIf { it.startsWith("http") }?.let(Uri::parse),
    )

    fun toJson(): JSONObject = JSONObject()
        .put("stationuuid", id)
        .put("name", name)
        .put("url_resolved", url)
        .put("favicon", favicon)
        .put("country", country)
        .put("countrycode", countryCode)
        .put("tags", tags.joinToString(","))
        .put("codec", codec)
        .put("bitrate", bitrate)
        .put("hls", if (hls) 1 else 0)

    companion object {
        const val HLS_MIME = "application/x-mpegURL"

        /** Rebuilds a station from what the player knows about it (for the heart on the player). */
        fun fromSong(song: Song) = RadioStation(
            id = song.key.removePrefix(Song.RADIO_PREFIX),
            name = song.album.ifBlank { song.title },
            url = song.path.ifBlank { song.uri.toString() },
            favicon = song.coverHint?.toString().orEmpty(),
            country = "",
            countryCode = "",
            tags = listOf(song.genre).filter { it.isNotBlank() },
            codec = if (song.mimeType == HLS_MIME) "" else song.mimeType,
            bitrate = 0,
            hls = song.mimeType == HLS_MIME,
        )
    }
}

@Immutable
data class RadioCountry(val code: String, val name: String, val stations: Int)

/**
 * The community Radio Browser directory (radio-browser.info): tens of thousands of internet radio
 * stations, free to use, no account or key.
 */
object RadioBrowser {
    private val servers = listOf(
        "https://de1.api.radio-browser.info",
        "https://nl1.api.radio-browser.info",
        "https://at1.api.radio-browser.info",
        "https://de2.api.radio-browser.info",
    )

    fun top(limit: Int = 60): List<RadioStation> =
        stations("/json/stations/topclick/$limit?hidebroken=true")

    fun search(name: String, limit: Int = 80): List<RadioStation> =
        stations("/json/stations/search?name=${Http.encode(name)}&limit=$limit&hidebroken=true&order=clickcount&reverse=true")

    fun byCountry(code: String, limit: Int = 100): List<RadioStation> =
        stations("/json/stations/search?countrycode=${Http.encode(code)}&limit=$limit&hidebroken=true&order=clickcount&reverse=true")

    fun byTag(tag: String, limit: Int = 100): List<RadioStation> =
        stations("/json/stations/search?tag=${Http.encode(tag)}&limit=$limit&hidebroken=true&order=clickcount&reverse=true")

    fun countries(): List<RadioCountry> {
        val arr = JSONArray(fetch("/json/countries?order=stationcount&reverse=true&hidebroken=true"))
        return List(arr.length()) { arr.getJSONObject(it) }
            .map { RadioCountry(it.optString("iso_3166_1"), it.optString("name"), it.optInt("stationcount")) }
            .filter { it.code.length == 2 && it.stations > 0 }
    }

    /** Tells the directory a station was played, which keeps its popularity lists useful. */
    fun reportPlay(id: String) {
        try {
            fetch("/json/url/${Http.encode(id)}")
        } catch (_: IOException) {
        }
    }

    private fun stations(path: String): List<RadioStation> = parseStations(fetch(path))

    /** Tries the mirrors in turn, starting at a random one to spread the load. */
    private fun fetch(path: String): String {
        var last: IOException? = null
        val start = servers.indices.random()
        for (i in servers.indices) {
            val server = servers[(start + i) % servers.size]
            try {
                return Http.get(server + path)
            } catch (e: IOException) {
                last = e
            }
        }
        throw last ?: IOException("No radio directory server answered")
    }

    fun parseStations(json: String): List<RadioStation> {
        val arr = JSONArray(json)
        val seen = HashSet<String>()
        return List(arr.length()) { arr.getJSONObject(it) }
            .mapNotNull(::parseStation)
            // The directory often lists the same stream several times.
            .filter { seen.add(it.url) }
    }

    fun parseStation(o: JSONObject): RadioStation? {
        val url = o.optString("url_resolved").ifBlank { o.optString("url") }.trim()
        if (!url.startsWith("http")) return null
        return RadioStation(
            id = o.optString("stationuuid"),
            name = o.optString("name").trim().ifBlank { "Unnamed station" },
            url = url,
            favicon = o.optString("favicon").trim(),
            country = o.optString("country").trim(),
            countryCode = o.optString("countrycode").trim(),
            tags = o.optString("tags").split(',').map { it.trim() }.filter { it.isNotEmpty() },
            codec = o.optString("codec").trim().let { if (it.equals("UNKNOWN", ignoreCase = true)) "" else it },
            bitrate = o.optInt("bitrate"),
            hls = o.optInt("hls") == 1 || url.substringBefore('?').endsWith(".m3u8", ignoreCase = true),
        )
    }
}
