package com.jambo199.vividmusic.online

import android.net.Uri
import android.util.Xml
import androidx.compose.runtime.Immutable
import com.jambo199.vividmusic.data.Song
import com.jambo199.vividmusic.data.SongSource
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Locale

@Immutable
data class Podcast(
    val feedUrl: String,
    val title: String,
    val author: String,
    val artwork: String,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("feedUrl", feedUrl).put("title", title).put("author", author).put("artwork", artwork)

    companion object {
        fun fromJson(o: JSONObject) = Podcast(o.optString("feedUrl"), o.optString("title"), o.optString("author"), o.optString("artwork"))
    }
}

@Immutable
data class Episode(
    val title: String,
    val url: String,
    val mimeType: String,
    val published: Long,
    val durationMs: Long,
    val description: String,
    val image: String,
) {
    val key: String get() = Song.PODCAST_PREFIX + url

    /** [localUri] is the downloaded file, when there is one. */
    fun toSong(podcast: Podcast, localUri: Uri? = null): Song = Song(
        key = key,
        uri = localUri ?: Uri.parse(url),
        mediaStoreId = -1,
        title = title,
        artist = podcast.title,
        album = podcast.title,
        albumArtist = podcast.author,
        albumId = 0,
        durationMs = durationMs,
        trackNumber = 0,
        year = 0,
        dateAddedSec = published / 1000,
        dateModifiedSec = 0,
        sizeBytes = 0,
        path = url,
        folder = "",
        fileName = url.substringBefore('?').substringAfterLast('/'),
        mimeType = mimeType,
        genre = "Podcast",
        source = SongSource.ONLINE,
        coverHint = image.ifBlank { podcast.artwork }.takeIf { it.startsWith("http") }?.let(Uri::parse),
    )

    fun downloadPath(podcast: Podcast): String {
        val ext = url.substringBefore('?').substringAfterLast('.', "mp3").take(4).ifBlank { "mp3" }
        return "Vivid Music/${Downloads.safeName(podcast.title)}/${Downloads.safeName(title)}.$ext"
    }
}

@Immutable
data class PodcastFeed(val podcast: Podcast, val description: String, val episodes: List<Episode>)

/** Podcast search and charts from Apple's public directory, and the shows' own RSS feeds. */
object Podcasts {
    fun search(term: String): List<Podcast> {
        val url = "https://itunes.apple.com/search?media=podcast&entity=podcast&limit=40&term=${Http.encode(term)}"
        return parseDirectory(Http.get(url))
    }

    /** The most popular shows in [country] right now. */
    fun top(country: String = Locale.getDefault().country.ifBlank { "us" }): List<Podcast> {
        val cc = country.lowercase().takeIf { it.length == 2 } ?: "us"
        val chart = JSONObject(Http.get("https://rss.applemarketingtools.com/api/v2/$cc/podcasts/top/30/podcasts.json"))
        val results = chart.optJSONObject("feed")?.optJSONArray("results") ?: return emptyList()
        val ids = List(results.length()) { results.getJSONObject(it).optString("id") }.filter { it.isNotBlank() }
        if (ids.isEmpty()) return emptyList()
        // The chart has no feed addresses; one lookup fetches them for every show.
        val found = parseDirectory(Http.get("https://itunes.apple.com/lookup?id=${ids.joinToString(",")}&entity=podcast"))
        return found
    }

    fun feed(feedUrl: String): PodcastFeed = parseFeed(feedUrl, Http.get(feedUrl, accept = "application/rss+xml, application/xml, text/xml, */*"))

    fun parseDirectory(json: String): List<Podcast> {
        val results = JSONObject(json).optJSONArray("results") ?: return emptyList()
        return List(results.length()) { results.getJSONObject(it) }.mapNotNull { r ->
            val feed = r.optString("feedUrl")
            if (feed.isBlank()) return@mapNotNull null
            Podcast(
                feedUrl = feed,
                title = r.optString("collectionName").ifBlank { r.optString("trackName") },
                author = r.optString("artistName"),
                artwork = r.optString("artworkUrl600").ifBlank { r.optString("artworkUrl100") },
            )
        }
    }

    fun parseFeed(feedUrl: String, xml: String): PodcastFeed {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(StringReader(xml))

        var title = ""
        var author = ""
        var artwork = ""
        var description = ""
        val episodes = ArrayList<Episode>()

        var inItem = false
        var itemTitle = ""
        var itemUrl = ""
        var itemType = ""
        var itemDate = 0L
        var itemDuration = 0L
        var itemDescription = ""
        var itemImage = ""

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "item" -> {
                        inItem = true
                        itemTitle = ""; itemUrl = ""; itemType = ""; itemDate = 0; itemDuration = 0; itemDescription = ""; itemImage = ""
                    }
                    "enclosure" -> if (inItem) {
                        itemUrl = parser.getAttributeValue(null, "url").orEmpty().trim()
                        itemType = parser.getAttributeValue(null, "type").orEmpty()
                    }
                    "itunes:image" -> {
                        val href = parser.getAttributeValue(null, "href").orEmpty()
                        if (inItem) itemImage = href else if (artwork.isBlank()) artwork = href
                    }
                    "title" -> {
                        val t = text(parser).trim()
                        if (inItem) itemTitle = t else if (title.isBlank()) title = t
                    }
                    "itunes:author" -> if (!inItem) author = text(parser).trim()
                    "pubDate" -> if (inItem) itemDate = parseDate(text(parser))
                    "itunes:duration" -> if (inItem) itemDuration = parseDuration(text(parser))
                    "description", "itunes:summary", "content:encoded" -> {
                        val body = text(parser)
                        if (inItem) {
                            if (itemDescription.isBlank()) itemDescription = InternetArchive.stripHtml(body)
                        } else if (description.isBlank()) {
                            description = InternetArchive.stripHtml(body)
                        }
                    }
                    "url" -> if (!inItem && artwork.isBlank()) artwork = text(parser).trim() // <image><url>
                }
            } else if (event == XmlPullParser.END_TAG && parser.name == "item") {
                inItem = false
                if (itemUrl.startsWith("http") && (itemType.isBlank() || itemType.startsWith("audio") || itemType.startsWith("video/mp4") || itemType == "application/octet-stream")) {
                    episodes += Episode(itemTitle.ifBlank { "Episode" }, itemUrl, itemType, itemDate, itemDuration, itemDescription.take(600), itemImage)
                }
            }
            event = parser.next()
        }
        return PodcastFeed(
            podcast = Podcast(feedUrl, title.ifBlank { feedUrl }, author, artwork),
            description = description.take(1000),
            episodes = episodes.sortedByDescending { it.published },
        )
    }

    /** The element's text, or "" when it holds markup instead (some feeds nest tags). */
    private fun text(parser: XmlPullParser): String = try {
        parser.nextText()
    } catch (e: Exception) {
        ""
    }

    /** "3600", "62:03" or "1:02:03". */
    fun parseDuration(text: String): Long {
        val parts = text.trim().split(':').map { it.trim().toDoubleOrNull() ?: return 0 }
        if (parts.isEmpty() || parts.size > 3) return 0
        return (parts.fold(0.0) { acc, p -> acc * 60 + p } * 1000).toLong()
    }

    private val DATE_FORMATS = listOf("EEE, dd MMM yyyy HH:mm:ss Z", "EEE, d MMM yyyy HH:mm:ss Z", "EEE, dd MMM yyyy HH:mm:ss zzz", "dd MMM yyyy HH:mm:ss Z")

    fun parseDate(text: String): Long {
        val t = text.trim()
        for (pattern in DATE_FORMATS) {
            try {
                return SimpleDateFormat(pattern, Locale.US).parse(t)?.time ?: continue
            } catch (_: Exception) {
            }
        }
        return 0
    }
}
