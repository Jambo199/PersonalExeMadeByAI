package com.jambo199.vividmusic.online

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException

/** One place in the chart, as Deezer names it. */
data class ChartEntry(val title: String, val artist: String, val durationMs: Long, val artistPicture: String? = null)

/** An artist ranked by how much their songs in the chart are played. */
data class ChartArtist(
    val name: String,
    val picture: String?,
    /** How many of their songs are in the chart. */
    val songs: Int,
    /** Their highest-placed song, and where (1 for the top). */
    val topSong: String,
    val bestPlace: Int,
)

data class ChartPage(val entries: List<ChartEntry>, val hasMore: Boolean)

/**
 * NCS releases ranked by how much they're played, from Deezer's public catalogue (no account
 * or key; Deezer ranks its top 300). Each entry is then found on ncs.io, where the song itself
 * comes from.
 */
object NcsChart {
    /** Chart entries [index] onwards. */
    fun page(index: Int, count: Int): ChartPage {
        val q = Http.encode("label:\"NCS\"")
        return parse(Http.get("https://api.deezer.com/search?q=$q&order=RANKING&index=$index&limit=$count"))
    }

    internal fun parse(json: String): ChartPage {
        val o = JSONObject(json)
        o.optJSONObject("error")?.let { throw IOException(it.optString("message").ifBlank { "Deezer couldn't send the chart" }) }
        val data = o.optJSONArray("data") ?: JSONArray()
        val entries = List(data.length()) { data.getJSONObject(it) }.mapNotNull { t ->
            val title = t.optString("title").trim()
            if (title.isEmpty()) return@mapNotNull null
            val artist = t.optJSONObject("artist")
            ChartEntry(
                title = title,
                artist = artist?.optString("name").orEmpty().trim(),
                durationMs = t.optLong("duration") * 1000,
                artistPicture = artist?.optString("picture_medium")?.takeIf { it.startsWith("http") },
            )
        }
        return ChartPage(entries, hasMore = o.has("next") && entries.isNotEmpty())
    }

    /** The whole chart (Deezer ranks up to 300), a few requests at most. */
    fun all(): List<ChartEntry> {
        val out = ArrayList<ChartEntry>()
        while (out.size < MAX) {
            val p = page(out.size, 100)
            out += p.entries
            if (!p.hasMore) break
        }
        return out
    }

    /**
     * Artists by how popular their songs are: each song scores more the higher it's placed, so
     * an artist with several hits beats one with a single song just above them.
     */
    fun rankArtists(entries: List<ChartEntry>): List<ChartArtist> {
        data class Tally(val name: String, var picture: String?, var points: Int, var songs: Int, val topSong: String, val bestPlace: Int)
        val byName = LinkedHashMap<String, Tally>()
        entries.forEachIndexed { i, e ->
            if (e.artist.isBlank()) return@forEachIndexed
            val place = i + 1
            val t = byName.getOrPut(e.artist.lowercase()) { Tally(e.artist, e.artistPicture, 0, 0, e.title, place) }
            t.points += entries.size + 1 - place
            t.songs++
            if (t.picture == null) t.picture = e.artistPicture
        }
        return byName.values.sortedWith(compareByDescending<Tally> { it.points }.thenBy { it.bestPlace })
            .map { ChartArtist(it.name, it.picture, it.songs, it.topSong, it.bestPlace) }
    }

    private const val MAX = 300
}

/** The chart matched to ncs.io so far, kept for a few days so it opens instantly. */
data class ChartProgress(
    val tracks: List<NcsTrack> = emptyList(),
    /** The next chart place to look at. */
    val nextIndex: Int = 0,
    val hasMore: Boolean = true,
    val savedAt: Long = 0,
) {
    fun toJson(): String = JSONObject()
        .put("tracks", JSONArray().apply { tracks.forEach { put(it.toJson()) } })
        .put("nextIndex", nextIndex)
        .put("hasMore", hasMore)
        .put("savedAt", savedAt)
        .toString()

    companion object {
        const val MAX_AGE_MS = 3L * 24 * 60 * 60 * 1000

        fun load(file: File, now: Long): ChartProgress? = try {
            if (!file.exists()) null else {
                val o = JSONObject(file.readText())
                val p = ChartProgress(
                    tracks = o.getJSONArray("tracks").let { a -> List(a.length()) { NcsTrack.fromJson(a.getJSONObject(it)) } },
                    nextIndex = o.optInt("nextIndex"),
                    hasMore = o.optBoolean("hasMore", true),
                    savedAt = o.optLong("savedAt"),
                )
                p.takeIf { now - it.savedAt in 0..MAX_AGE_MS && it.tracks.isNotEmpty() }
            }
        } catch (e: Exception) {
            null
        }
    }
}
