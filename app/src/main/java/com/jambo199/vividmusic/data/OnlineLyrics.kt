package com.jambo199.vividmusic.data

import android.content.Context
import android.util.Log
import com.jambo199.vividmusic.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import kotlin.math.abs

/**
 * Looks up lyrics on LRCLIB (lrclib.net), a free and open lyrics database, and keeps what it
 * finds on the phone so each song is only looked up once.
 */
class OnlineLyrics(context: Context) {
    sealed interface Result {
        data class Found(val text: String) : Result
        data object NotFound : Result
        data class Failed(val message: String) : Result
    }

    private val dir = File(context.filesDir, "lyrics_online")

    fun cached(song: Song): String? = try {
        file(song, "lrc").takeIf { it.isFile }?.readText()
    } catch (e: IOException) {
        null
    }

    /** True when an earlier look-up came back empty recently, so we don't keep asking. */
    fun knownMissing(song: Song): Boolean {
        val marker = file(song, "none")
        return marker.isFile && System.currentTimeMillis() - marker.lastModified() < MISSING_TTL_MS
    }

    /** Forgets downloaded lyrics (they were wrong) and doesn't look them up again by itself. */
    fun remove(song: Song) {
        file(song, "lrc").delete()
        markMissing(song)
    }

    /** Blocking network call; run it off the main thread. */
    fun fetch(song: Song): Result {
        val query = LyricsQuery.from(song)
        if (query.title.isBlank()) return Result.NotFound
        return try {
            val text = exactMatch(query) ?: searchMatch(query)
            if (text == null) {
                markMissing(song)
                Result.NotFound
            } else {
                dir.mkdirs()
                file(song, "lrc").writeTextAtomically(text)
                file(song, "none").delete()
                Result.Found(text)
            }
        } catch (e: IOException) {
            Log.w(TAG, "Lyrics look-up failed", e)
            Result.Failed("Couldn't reach LRCLIB. Check your internet connection.")
        } catch (e: Exception) {
            Log.w(TAG, "Lyrics look-up failed", e)
            Result.Failed("Couldn't read the lyrics from LRCLIB.")
        }
    }

    private fun exactMatch(q: LyricsQuery): String? {
        if (q.artist.isBlank() || q.durationSec <= 0) return null
        val params = buildMap {
            put("track_name", q.title)
            put("artist_name", q.artist)
            if (q.album.isNotBlank()) put("album_name", q.album)
            put("duration", q.durationSec.toString())
        }
        val body = get("/api/get", params) ?: return null
        return lyricsOf(JSONObject(body))
    }

    private fun searchMatch(q: LyricsQuery): String? {
        val params = if (q.artist.isNotBlank()) {
            mapOf("track_name" to q.searchTitle, "artist_name" to q.artist)
        } else {
            mapOf("q" to q.searchTitle)
        }
        val body = get("/api/search", params) ?: return null
        val results = JSONArray(body)
        return pickBest(List(results.length()) { results.getJSONObject(it) }, q.durationSec)
    }

    /** GET returning the body, or null for 404. */
    private fun get(path: String, params: Map<String, String>): String? {
        val query = params.entries.joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, "UTF-8")}" }
        val conn = URL("$BASE$path?$query").openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("User-Agent", "VividMusic/${BuildConfig.VERSION_NAME} (https://github.com/Jambo199/PersonalExeMadeByAI)")
            conn.setRequestProperty("Accept", "application/json")
            return when (val code = conn.responseCode) {
                200 -> conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
                404 -> null
                else -> throw IOException("LRCLIB answered $code")
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun file(song: Song, ext: String) = File(dir, "${digest(song.key)}.$ext")

    private fun markMissing(song: Song) {
        try {
            dir.mkdirs()
            // Rewriting it refreshes the time it was made, which is what knownMissing() checks.
            file(song, "none").writeText("")
        } catch (_: IOException) {
        }
    }

    companion object {
        private const val TAG = "OnlineLyrics"
        private const val BASE = "https://lrclib.net"
        private const val MISSING_TTL_MS = 14L * 24 * 60 * 60 * 1000
        const val SOURCE = "LRCLIB"

        private fun digest(key: String): String =
            MessageDigest.getInstance("SHA-1").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }

        /** Synced lyrics if there are any, otherwise plain ones. */
        fun lyricsOf(o: JSONObject): String? {
            if (o.optBoolean("instrumental")) return "[00:00.00]♪ Instrumental ♪"
            val synced = o.optString("syncedLyrics").takeUnless { o.isNull("syncedLyrics") }
            val plain = o.optString("plainLyrics").takeUnless { o.isNull("plainLyrics") }
            return synced?.takeIf { it.isNotBlank() } ?: plain?.takeIf { it.isNotBlank() }
        }

        /**
         * Of the search results, the one closest in length to the song (they must be within a few
         * seconds, or it's probably a different recording), preferring synced lyrics.
         */
        fun pickBest(results: List<JSONObject>, durationSec: Int): String? {
            val usable = results.filter { lyricsOf(it) != null }
            val close = if (durationSec > 0) {
                usable.filter { abs(it.optDouble("duration", -100.0) - durationSec) <= 4.0 }
            } else {
                usable.take(1)
            }
            val best = close.minWithOrNull(
                compareBy<JSONObject> { if (it.isNull("syncedLyrics") || it.optString("syncedLyrics").isBlank()) 1 else 0 }
                    .thenBy { abs(it.optDouble("duration", 0.0) - durationSec) },
            ) ?: return null
            return lyricsOf(best)
        }
    }
}

/** What we know about a song, tidied up for searching. */
data class LyricsQuery(
    val title: String,
    val artist: String,
    val album: String,
    val durationSec: Int,
) {
    /** Title without "(Official Video)", "[Remastered]", "feat. X" and the like. */
    val searchTitle: String
        get() = title
            .replace(Regex("\\s*[(\\[][^)\\]]*[)\\]]"), "")
            .replace(Regex("\\s+(feat\\.?|ft\\.?|featuring)\\s+.*$", RegexOption.IGNORE_CASE), "")
            .trim()
            .ifBlank { title }

    companion object {
        fun from(song: Song): LyricsQuery {
            // Leading track numbers from file names: "03 - Song", "03. Song".
            var title = song.title.trim().replace(Regex("^\\d{1,3}\\s*[-.]\\s+"), "")
            var artist = song.artist.trim().takeUnless { it.equals("<unknown>", ignoreCase = true) }.orEmpty()
            // Untagged files are often named "Artist - Title".
            if (artist.isBlank() && title.contains(" - ")) {
                artist = title.substringBefore(" - ").trim()
                title = title.substringAfter(" - ").trim()
            }
            val album = song.album.trim().takeUnless { it.equals("<unknown>", ignoreCase = true) }.orEmpty()
            return LyricsQuery(title, artist, album, ((song.durationMs + 500) / 1000).toInt())
        }
    }
}
