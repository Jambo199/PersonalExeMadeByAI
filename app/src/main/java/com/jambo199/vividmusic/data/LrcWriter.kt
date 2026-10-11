package com.jambo199.vividmusic.data

/** Writes lyrics timed by hand as an .lrc file. */
object LrcWriter {
    /** The lines worth timing: the words, without blank lines or [tags]. */
    fun linesOf(plain: String): List<String> = plain.lines()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !(it.startsWith("[") && it.endsWith("]")) }

    /** "[01:23.45]" */
    fun stamp(ms: Long): String {
        val cs = ms.coerceAtLeast(0) / 10
        return "[%02d:%02d.%02d]".format(cs / 6000, cs / 100 % 60, cs % 100)
    }

    /** An .lrc with a title/artist header and one timed line each; [times] can be shorter than [lines]. */
    fun write(title: String, artist: String, lines: List<String>, times: List<Long>): String = buildString {
        if (title.isNotBlank()) append("[ti:").append(title).append("]\n")
        if (artist.isNotBlank()) append("[ar:").append(artist).append("]\n")
        append("[by:Vivid Music]\n")
        lines.zip(times).forEach { (line, t) -> append(stamp(t)).append(line).append('\n') }
    }
}
