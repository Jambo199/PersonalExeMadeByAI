package com.jambo199.vividmusic.data

/** When in the day you listen, and what that makes you. */
object ListeningClock {
    data class TimeOfDay(val title: String, val description: String)

    /** The hour (0–23) with the most listening, or null with under ten minutes in total. */
    fun peakHour(hourMs: List<Long>): Int? {
        if (hourMs.sum() < 10 * 60_000L) return null
        // Smooth over neighbouring hours so one long evening doesn't outweigh a habit.
        val smoothed = (0 until 24).map { h ->
            hourMs.getOrElse(h) { 0L } * 2 + hourMs.getOrElse((h + 23) % 24) { 0L } + hourMs.getOrElse((h + 1) % 24) { 0L }
        }
        return smoothed.indices.maxByOrNull { smoothed[it] }
    }

    fun timeOfDay(hour: Int): TimeOfDay = when (hour) {
        in 5..10 -> TimeOfDay("Early bird", "Music with your morning")
        in 11..16 -> TimeOfDay("Daytime groover", "Your music keeps the day moving")
        in 17..21 -> TimeOfDay("Evening listener", "You wind down with music")
        else -> TimeOfDay("Night owl", "The late hours are when you listen most")
    }

    /** "9 am", "12 pm", "11 pm". */
    fun hourLabel(hour: Int): String = when {
        hour == 0 -> "12 am"
        hour < 12 -> "$hour am"
        hour == 12 -> "12 pm"
        else -> "${hour - 12} pm"
    }
}
