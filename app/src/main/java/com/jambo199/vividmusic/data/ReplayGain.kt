package com.jambo199.vividmusic.data

import kotlin.math.pow

enum class Normalization(val label: String) { OFF("Off"), TRACK("Per song"), ALBUM("Per album") }

/** ReplayGain values read from a file's tags, in dB. */
data class ReplayGain(val trackDb: Float? = null, val albumDb: Float? = null) {
    /**
     * Player volume (0..1) that applies the gain, plus a [preampDb] for tagged songs and
     * [untaggedDb] for songs without tags (like Auxio's adjustments). Songs are only ever turned
     * down: turning quiet songs up would clip, so those just play at full volume.
     */
    fun volume(mode: Normalization, preampDb: Float = 0f, untaggedDb: Float = 0f): Float {
        if (mode == Normalization.OFF) return 1f
        val gain = when (mode) {
            Normalization.TRACK -> trackDb ?: albumDb
            else -> albumDb ?: trackDb
        }
        val db = if (gain != null) gain + preampDb else untaggedDb
        return 10f.pow(db / 20f).coerceIn(0.05f, 1f)
    }

    companion object {
        const val TRACK_KEY = "REPLAYGAIN_TRACK_GAIN"
        const val ALBUM_KEY = "REPLAYGAIN_ALBUM_GAIN"

        /** "-7.32 dB", "+1.5dB" or "-7.32" to a number. */
        fun parseDb(text: String?): Float? =
            text?.trim()?.removeSuffix("dB")?.removeSuffix("DB")?.removeSuffix("db")?.trim()
                ?.removePrefix("+")?.replace(',', '.')?.toFloatOrNull()
                ?.takeIf { it in -60f..60f }
    }
}
