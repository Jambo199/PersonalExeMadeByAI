package com.jambo199.vividmusic.data

/**
 * Splits artist tags like "A; B" or "A feat. B" into separate artists, so each gets the song on
 * their page. Which separators count is a setting: "&" or "/" would wrongly split names like
 * "Simon & Garfunkel" or "AC/DC", so those are off unless chosen.
 */
object ArtistSeparators {
    const val FEAT = "feat."

    /** Separator to the label shown in settings. */
    val OPTIONS = listOf(";" to ";", "/" to "/", "&" to "&", "," to ",", FEAT to "feat. / ft.")

    val DEFAULT = setOf(";", FEAT)

    private val BRACKETED_FEAT = Regex("\\s*[(\\[]\\s*(?:feat\\.?|ft\\.?|featuring)\\s+([^)\\]]+)[)\\]]", RegexOption.IGNORE_CASE)
    private const val FEAT_PATTERN = "\\s+(?:feat\\.?|ft\\.?|featuring)\\s+"

    fun split(text: String, separators: Set<String>): List<String> {
        if (separators.isEmpty() || text.isBlank()) return listOf(text)
        var source = text
        val patterns = ArrayList<String>()
        if (FEAT in separators) {
            source = BRACKETED_FEAT.replace(source) { " feat. " + it.groupValues[1] }
            patterns += FEAT_PATTERN
        }
        separators.filter { it != FEAT }.forEach { patterns += Regex.escape(it) }
        val parts = source.split(Regex(patterns.joinToString("|"), RegexOption.IGNORE_CASE))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
        return parts.ifEmpty { listOf(text) }
    }
}
