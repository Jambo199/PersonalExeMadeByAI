package com.jambo199.vividmusic.online

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException

/** One place in the chart, as Deezer names it. */
data class ChartEntry(val title: String, val artist: String, val durationMs: Long)

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
            ChartEntry(title, t.optJSONObject("artist")?.optString("name").orEmpty().trim(), t.optLong("duration") * 1000)
        }
        return ChartPage(entries, hasMore = o.has("next") && entries.isNotEmpty())
    }
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
