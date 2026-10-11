package com.jambo199.vividmusic.data

/**
 * Moods to pick from on Home. Each matches songs by words in their genre tags, and NCS's own
 * mood tags when your library hasn't got enough.
 */
enum class Mood(val label: String, val genreWords: List<String>, val ncsMoods: List<String>) {
    CHILL(
        "Chill",
        listOf("chill", "lofi", "lo-fi", "ambient", "downtempo", "acoustic", "jazz", "lounge", "r&b", "soul", "trip hop", "trip-hop"),
        listOf("Chill", "Laid Back", "Peaceful", "Dreamy", "Relaxed", "Calm"),
    ),
    ENERGETIC(
        "Energetic",
        listOf("edm", "electro", "dubstep", "drum", "dnb", "trap", "hardstyle", "rock", "metal", "techno", "bass", "house", "electronic"),
        listOf("Energetic", "Powerful", "Epic", "Intense", "Hype", "Fierce", "Angry"),
    ),
    HAPPY(
        "Happy",
        listOf("pop", "funk", "disco", "reggae", "indie", "dance", "tropical", "ska"),
        listOf("Happy", "Euphoric", "Uplifting", "Playful", "Hopeful", "Joyful", "Funny"),
    ),
    FOCUS(
        "Focus",
        listOf("ambient", "classical", "instrumental", "piano", "lofi", "lo-fi", "soundtrack", "score", "post-rock", "orchestral"),
        listOf("Dreamy", "Mysterious", "Peaceful", "Suspense"),
    ),
    PARTY(
        "Party",
        listOf("party", "dance", "edm", "house", "hip hop", "hip-hop", "rap", "reggaeton", "electro", "club", "big room"),
        listOf("Euphoric", "Energetic", "Hype", "Funky", "Glamorous"),
    ),
    MELLOW(
        "Mellow",
        listOf("sad", "blues", "emo", "ballad", "singer-songwriter", "folk", "acoustic", "soul"),
        listOf("Sad", "Melancholic", "Emotional", "Romantic", "Sexy"),
    ),
    ;

    /** Whether a song tagged [genre] suits this mood. */
    fun matches(genre: String): Boolean {
        val g = genre.lowercase()
        return g.isNotBlank() && genreWords.any { it in g }
    }

    /** Which of NCS's mood tags ([names]) suit this mood. */
    fun ncsMatches(names: List<String>): List<String> = names.filter { n -> ncsMoods.any { it.equals(n.trim(), ignoreCase = true) } }
}
