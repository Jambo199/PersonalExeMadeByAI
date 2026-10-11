package com.jambo199.vividmusic.data

/** What you said into the search bar's microphone. */
sealed interface VoiceCommand {
    /** "play Alan Walker": find it and start playing. */
    data class Play(val query: String) : VoiceCommand

    /** "shuffle Synthwave": find it and shuffle it. */
    data class Shuffle(val query: String) : VoiceCommand

    /** "shuffle everything" or just "shuffle". */
    data object ShuffleAll : VoiceCommand

    /** "play my favourites". */
    data object Favorites : VoiceCommand

    /** Anything else is searched for. */
    data class Search(val query: String) : VoiceCommand

    companion object {
        private val ALL = setOf("", "all", "everything", "all songs", "my music", "all my music", "my songs", "music", "something")
        private val FAVORITES = setOf("favourites", "favorites", "my favourites", "my favorites", "my liked songs", "liked songs")

        fun parse(spoken: String): VoiceCommand {
            val text = spoken.trim().trimEnd('.', '!', '?').replace(Regex("\\s+"), " ")
            val lower = text.lowercase()
            fun after(word: String) = text.substring(word.length).trim().removePrefix("me ").trim()
            return when {
                lower == "shuffle" || lower.startsWith("shuffle ") -> {
                    val rest = after("shuffle")
                    if (rest.lowercase() in ALL) ShuffleAll else Shuffle(rest)
                }
                lower == "play" || lower.startsWith("play ") -> {
                    val rest = after("play")
                    when (rest.lowercase()) {
                        in FAVORITES -> Favorites
                        in ALL -> ShuffleAll
                        else -> Play(rest.removeSuffix(" please").removeSuffix(" now").trim())
                    }
                }
                else -> Search(text)
            }
        }
    }
}
