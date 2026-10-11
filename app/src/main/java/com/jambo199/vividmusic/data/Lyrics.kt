package com.jambo199.vividmusic.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.nio.charset.Charset

@Immutable
data class LyricLine(val timeMs: Long, val text: String)

@Immutable
data class Lyrics(
    val songKey: String,
    /** Time-synced lines when the source had timestamps. */
    val lines: List<LyricLine>,
    val plain: String,
    val source: String,
) {
    val isSynced: Boolean get() = lines.isNotEmpty()
}

object LrcParser {
    private val TIME = Regex("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?]")
    private val OFFSET = Regex("\\[offset:\\s*([+-]?\\d+)]", RegexOption.IGNORE_CASE)
    private val TAG = Regex("^\\[[a-zA-Z]+:.*]$")

    /** Parses LRC text. Returns an empty list when the text has no timestamps. */
    fun parse(text: String): List<LyricLine> {
        val offset = OFFSET.find(text)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val out = ArrayList<LyricLine>()
        text.lineSequence().forEach { raw ->
            val line = raw.trim()
            val stamps = TIME.findAll(line).toList()
            if (stamps.isEmpty()) return@forEach
            val content = line.substring(stamps.last().range.last + 1).trim()
                .replace(Regex("<\\d{1,3}:\\d{1,2}(?:[.:]\\d{1,3})?>"), "") // word-level timing
                .trim()
            for (m in stamps) {
                val min = m.groupValues[1].toLong()
                val sec = m.groupValues[2].toLong()
                val fracRaw = m.groupValues[3]
                val frac = when (fracRaw.length) {
                    0 -> 0L
                    1 -> fracRaw.toLong() * 100
                    2 -> fracRaw.toLong() * 10
                    else -> fracRaw.take(3).toLong()
                }
                val t = (min * 60_000 + sec * 1000 + frac - offset).coerceAtLeast(0)
                out += LyricLine(t, content)
            }
        }
        return out.sortedBy { it.timeMs }
    }

    /** Text without LRC tags, for unsynced display. */
    fun stripTags(text: String): String = text.lineSequence()
        .map { it.replace(TIME, "").trim() }
        .filterNot { TAG.matches(it) }
        .joinToString("\n")
        .trim()
}

/**
 * Finds lyrics for the current song: a user-chosen .lrc file, a sidecar .lrc next to the song,
 * or lyrics embedded in the file's tags (reported by the player).
 */
class LyricsRepository(
    private val context: Context,
    private val scope: CoroutineScope,
    private val library: () -> Library,
) {
    private val _current = MutableStateFlow<Lyrics?>(null)
    val current: StateFlow<Lyrics?> = _current.asStateFlow()

    private val assignmentsFile = File(context.filesDir, "lyrics_assignments.json")
    private val assignments: MutableMap<String, String> = loadAssignments()

    private var currentKey: String? = null
    private var embedded: String? = null
    private var job: Job? = null

    /** Called by the playback service whenever the playing song changes. */
    fun onSongChanged(key: String?) {
        if (key == currentKey) return
        currentKey = key
        embedded = null
        reload()
    }

    /** Called by the playback service when the player found lyrics inside the file. */
    fun onEmbeddedLyrics(key: String?, text: String?) {
        if (key != currentKey || text.isNullOrBlank() || text == embedded) return
        embedded = text
        reload()
    }

    /** Remembers a lyrics file the user picked for a song. */
    fun assign(songKey: String, lrcUri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(lrcUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) {
        }
        assignments[songKey] = lrcUri.toString()
        saveAssignments()
        if (songKey == currentKey) reload()
    }

    fun unassign(songKey: String) {
        if (assignments.remove(songKey) != null) {
            saveAssignments()
            if (songKey == currentKey) reload()
        }
    }

    fun hasAssignment(songKey: String) = songKey in assignments

    private fun reload() {
        val key = currentKey
        val embeddedText = embedded
        job?.cancel()
        if (key == null) {
            _current.value = null
            return
        }
        job = scope.launch {
            val lyrics = withContext(Dispatchers.IO) { find(key, embeddedText) }
            if (key == currentKey) _current.value = lyrics
        }
    }

    private fun find(key: String, embeddedText: String?): Lyrics? {
        assignments[key]?.let { uri ->
            readUri(Uri.parse(uri))?.let { return build(key, it, "Lyrics file") }
        }
        val song = library().byKey[key]
        song?.lyricsHint?.let { uri -> readUri(uri)?.let { return build(key, it, "Lyrics file") } }
        if (song != null && song.source == SongSource.MEDIA_STORE) {
            val base = song.path.substringBeforeLast('.')
            for (ext in listOf(".lrc", ".LRC", ".txt")) {
                val f = File(base + ext)
                try {
                    if (f.isFile && f.canRead()) return build(key, readText(f.readBytes()), "Lyrics file")
                } catch (_: Exception) {
                }
            }
        }
        if (!embeddedText.isNullOrBlank()) return build(key, embeddedText, "Embedded lyrics")
        return null
    }

    private fun build(key: String, text: String, source: String): Lyrics? {
        if (text.isBlank()) return null
        val lines = LrcParser.parse(text)
        val plain = if (lines.isEmpty()) text.trim() else LrcParser.stripTags(text)
        return Lyrics(key, lines, plain, source)
    }

    private fun readUri(uri: Uri): String? = try {
        context.contentResolver.openInputStream(uri)?.use { readText(it.readBytes()) }
    } catch (e: Exception) {
        null
    }

    private fun readText(bytes: ByteArray): String {
        // UTF-8 first; fall back to Windows-1252 for old LRC files.
        val utf8 = String(bytes, Charsets.UTF_8)
        if (!utf8.contains('�')) return utf8.removePrefix("﻿")
        if (bytes.size >= 2 && (bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte())) return String(bytes, Charsets.UTF_16LE).removePrefix("﻿")
        if (bytes.size >= 2 && (bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte())) return String(bytes, Charsets.UTF_16BE).removePrefix("﻿")
        return try {
            String(bytes, Charset.forName("windows-1252"))
        } catch (e: Exception) {
            utf8
        }
    }

    private fun loadAssignments(): MutableMap<String, String> = try {
        val map = HashMap<String, String>()
        if (assignmentsFile.exists()) {
            val o = JSONObject(assignmentsFile.readText())
            o.keys().forEach { k -> map[k] = o.getString(k) }
        }
        map
    } catch (e: Exception) {
        HashMap()
    }

    private fun saveAssignments() {
        try {
            assignmentsFile.writeText(JSONObject(HashMap(assignments)).toString())
        } catch (_: Exception) {
        }
    }
}

/** Pulls lyrics out of tag frames the player decoded (ID3 USLT, Vorbis LYRICS, MP4 ©lyr). */
object EmbeddedLyrics {
    fun parseUslt(data: ByteArray): String? {
        if (data.size < 5) return null
        val encoding = data[0].toInt()
        val charset = when (encoding) {
            1 -> Charsets.UTF_16
            2 -> Charsets.UTF_16BE
            3 -> Charsets.UTF_8
            else -> Charsets.ISO_8859_1
        }
        val wide = encoding == 1 || encoding == 2
        // Skip 3-byte language code, then the null-terminated content descriptor.
        var i = 4
        if (wide) {
            while (i + 1 < data.size && !(data[i].toInt() == 0 && data[i + 1].toInt() == 0)) i += 2
            i += 2
        } else {
            while (i < data.size && data[i].toInt() != 0) i++
            i += 1
        }
        if (i >= data.size) return null
        return String(data, i, data.size - i, charset).trim('\u0000', '﻿', ' ', '\n')
            .takeIf { it.isNotBlank() }
    }
}
