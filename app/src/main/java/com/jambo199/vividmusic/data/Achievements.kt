package com.jambo199.vividmusic.data

/** Something to unlock by listening. [progress] counts up to [target]. */
data class Achievement(
    val id: String,
    val title: String,
    val description: String,
    val progress: Long,
    val target: Long,
) {
    val unlocked: Boolean get() = progress >= target
    val fraction: Float get() = (progress.toFloat() / target).coerceIn(0f, 1f)
}

/** The things you can unlock in Vivid Music, worked out from what you've done so far. */
object Achievements {
    private const val HOUR = 60L * 60 * 1000

    fun from(lib: Library, data: UserData, quizBest: Int): List<Achievement> {
        val counts = data.playCounts.filter { it.value > 0 }
        val plays = counts.values.sum().toLong()
        val bestDay = data.dailyMs.values.maxOrNull() ?: 0L
        val activeDays = data.dailyMs.count { it.value > 0 }.toLong()
        val topSongPlays = counts.values.maxOrNull()?.toLong() ?: 0L
        val ncsSaved = lib.songs.count { "/Music/NCS/" in it.path }.toLong()
        val lossless = lib.songs.count { it.extension in LOSSLESS }.toLong()
        val playlists = data.playlists.size.toLong()
        return listOf(
            Achievement("first", "First note", "Play your first song", plays, 1),
            Achievement("century", "Century", "Reach 100 plays", plays, 100),
            Achievement("thousand", "Thousand club", "Reach 1,000 plays", plays, 1_000),
            Achievement("explorer", "Explorer", "Play 50 different songs", counts.size.toLong(), 50),
            Achievement("repeat", "On repeat", "Play one song 25 times", topSongPlays, 25),
            Achievement("marathon", "Marathon", "Listen for 3 hours in one day", bestDay / 60_000, 3 * HOUR / 60_000),
            Achievement("devoted", "Devoted", "Listen on 7 different days", activeDays, 7),
            Achievement("hearts", "Heart collector", "Favourite 25 songs", data.favorites.size.toLong(), 25),
            Achievement("curator", "Curator", "Make 3 playlists", playlists, 3),
            Achievement("ncs", "NCS fan", "Save 10 NCS songs to your phone", ncsSaved, 10),
            Achievement("quiz", "Quiz whiz", "Score 1,500 in Guess the song", quizBest.toLong(), 1_500),
            Achievement("lossless", "Golden ears", "Have 10 lossless (FLAC, WAV…) songs", lossless, 10),
        )
    }

    private val LOSSLESS = setOf("FLAC", "WAV", "ALAC", "AIFF", "AIF", "APE", "WV", "DSF")
}
