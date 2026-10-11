package com.jambo199.vividmusic

import com.jambo199.vividmusic.data.Decade
import com.jambo199.vividmusic.data.ListeningClock
import com.jambo199.vividmusic.data.Mood
import com.jambo199.vividmusic.data.UserData
import com.jambo199.vividmusic.data.UserDataStore
import com.jambo199.vividmusic.data.VoiceCommand
import com.jambo199.vividmusic.data.Wrapped
import com.jambo199.vividmusic.playback.FocusTimer
import com.jambo199.vividmusic.playback.MusicAlarm
import com.jambo199.vividmusic.playback.ShakeDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime

class FunFeaturesTest {
    @Test
    fun twoJoltsMakeAShake() {
        val g = ShakeDetector.Gesture()
        assertFalse(g.onForce(1f, 0))
        assertFalse(g.onForce(3f, 100)) // first jolt
        assertFalse(g.onForce(3.2f, 120)) // same jolt, still going
        assertFalse(g.onForce(1f, 200))
        assertTrue(g.onForce(3f, 400)) // second jolt soon after
    }

    @Test
    fun walkingAndSlowJoltsDontCount() {
        val g = ShakeDetector.Gesture()
        // Footsteps never get near the threshold.
        for (t in 0L until 5000L step 50) assertFalse(g.onForce(1.4f, t))
        // Two jolts too far apart.
        assertFalse(g.onForce(3f, 6000))
        assertFalse(g.onForce(1f, 6100))
        assertFalse(g.onForce(3f, 7000))
    }

    @Test
    fun oneShakeAtATime() {
        val g = ShakeDetector.Gesture()
        g.onForce(3f, 0); g.onForce(1f, 50)
        assertTrue(g.onForce(3f, 100))
        g.onForce(1f, 150)
        g.onForce(3f, 200); g.onForce(1f, 250)
        assertFalse(g.onForce(3f, 300)) // still cooling down
    }

    @Test
    fun personalities() {
        assertEquals("The Newcomer", Wrapped.personality(0, 0, 0, 0).title)
        assertEquals("The Repeat Champion", Wrapped.personality(plays = 30, differentSongs = 8, topSongPlays = 12, topArtistPlays = 14).title)
        assertEquals("The Superfan", Wrapped.personality(plays = 30, differentSongs = 20, topSongPlays = 3, topArtistPlays = 16).title)
        assertEquals("The Explorer", Wrapped.personality(plays = 30, differentSongs = 28, topSongPlays = 2, topArtistPlays = 4).title)
        assertEquals("The Mix Master", Wrapped.personality(plays = 30, differentSongs = 15, topSongPlays = 3, topArtistPlays = 5).title)
        assertEquals("The Loyal Listener", Wrapped.personality(plays = 30, differentSongs = 6, topSongPlays = 6, topArtistPlays = 8).title)
    }

    @Test
    fun alarmRingsNextTimeTheClockGetsThere() {
        // Sunday 11 October 2026, 06:00.
        val now = LocalDateTime.of(2026, 10, 11, 6, 0)
        assertEquals(LocalDateTime.of(2026, 10, 11, 7, 30), MusicAlarm.nextRing(7, 30, emptySet(), now))
        // Already past today: tomorrow.
        assertEquals(LocalDateTime.of(2026, 10, 12, 5, 0), MusicAlarm.nextRing(5, 0, emptySet(), now))
        // Exactly now counts as passed, so a ring doesn't set itself for the same minute again.
        assertEquals(LocalDateTime.of(2026, 10, 12, 6, 0), MusicAlarm.nextRing(6, 0, emptySet(), now))
    }

    @Test
    fun alarmSkipsDaysItIsntSetFor() {
        val sunday = LocalDateTime.of(2026, 10, 11, 9, 0)
        assertEquals(DayOfWeek.SUNDAY, sunday.dayOfWeek)
        val weekdays = (1..5).toSet()
        // Sunday morning, weekdays only: Monday.
        assertEquals(LocalDateTime.of(2026, 10, 12, 7, 0), MusicAlarm.nextRing(7, 0, weekdays, sunday))
        // Friday evening: past Saturday and Sunday to Monday.
        val friday = LocalDateTime.of(2026, 10, 16, 20, 0)
        assertEquals(LocalDateTime.of(2026, 10, 19, 7, 0), MusicAlarm.nextRing(7, 0, weekdays, friday))
        // Only Sundays, asked on Sunday after the time: a week later.
        assertEquals(LocalDateTime.of(2026, 10, 18, 8, 0), MusicAlarm.nextRing(8, 0, setOf(7), sunday))
    }

    @Test
    fun alarmCountdownText() {
        val from = LocalDateTime.of(2026, 10, 11, 22, 25, 30)
        assertEquals("Rings in 9 h 5 min", MusicAlarm.untilText(from, LocalDateTime.of(2026, 10, 12, 7, 30)))
        assertEquals("Rings in 1 min", MusicAlarm.untilText(from, from.plusSeconds(20)))
        assertEquals("Rings in 2 days 1 h", MusicAlarm.untilText(from.withSecond(0), from.withSecond(0).plusDays(2).plusHours(1)))
    }

    @Test
    fun streakCountsDaysInARow() {
        var d = UserData()
        d = UserDataStore.countStreakDay(d, "2026-10-09", "2026-10-08")
        assertEquals(1, d.streak)
        d = UserDataStore.countStreakDay(d, "2026-10-10", "2026-10-09")
        d = UserDataStore.countStreakDay(d, "2026-10-10", "2026-10-09") // same day again: no change
        assertEquals(2, d.streak)
        // Still going the next day, until something is played.
        assertEquals(2, d.currentStreak("2026-10-11", "2026-10-10"))
        // A day missed: gone, and the next day starts again from one.
        assertEquals(0, d.currentStreak("2026-10-12", "2026-10-11"))
        d = UserDataStore.countStreakDay(d, "2026-10-12", "2026-10-11")
        assertEquals(1, d.streak)
        assertEquals(2, d.bestStreak)
    }

    @Test
    fun streakPicksUpEarlierDays() {
        val day = 24L * 60 * 60 * 1000
        val now = java.util.Calendar.getInstance().apply { set(2026, 9, 11, 12, 0) }.timeInMillis
        val daily = mapOf(
            UserDataStore.dayKey(now - day) to 120_000L,
            UserDataStore.dayKey(now - 2 * day) to 60_000L,
            UserDataStore.dayKey(now - 3 * day) to 5_000L, // under a minute: doesn't count
            UserDataStore.dayKey(now - 4 * day) to 300_000L,
        )
        assertEquals(2, UserDataStore.pastStreak(daily, now))
    }

    @Test
    fun decadeNames() {
        assertEquals("80s", Decade(1980, emptyList()).label)
        assertEquals("2000s", Decade(2000, emptyList()).label)
        assertEquals("2020s", Decade(2020, emptyList()).label)
    }

    @Test
    fun voiceCommands() {
        assertEquals(VoiceCommand.Play("Alan Walker"), VoiceCommand.parse("play Alan Walker"))
        assertEquals(VoiceCommand.Play("Faded"), VoiceCommand.parse("Play me Faded please."))
        assertEquals(VoiceCommand.Shuffle("synthwave"), VoiceCommand.parse("shuffle synthwave"))
        assertEquals(VoiceCommand.ShuffleAll, VoiceCommand.parse("Shuffle everything"))
        assertEquals(VoiceCommand.ShuffleAll, VoiceCommand.parse("shuffle"))
        assertEquals(VoiceCommand.Favorites, VoiceCommand.parse("play my favourites"))
        assertEquals(VoiceCommand.Search("Jim Yosef"), VoiceCommand.parse("Jim Yosef"))
        // "Player" isn't "play er".
        assertEquals(VoiceCommand.Search("Playground"), VoiceCommand.parse("Playground"))
    }

    @Test
    fun listeningClock() {
        assertEquals(null, ListeningClock.peakHour(List(24) { 1_000L }))
        val evenings = List(24) { h -> if (h in 21..23) 30 * 60_000L else 0L }
        assertEquals(22, ListeningClock.peakHour(evenings))
        assertEquals("Night owl", ListeningClock.timeOfDay(23).title)
        assertEquals("Early bird", ListeningClock.timeOfDay(7).title)
        assertEquals("12 am", ListeningClock.hourLabel(0))
        assertEquals("9 pm", ListeningClock.hourLabel(21))
    }

    @Test
    fun moodsMatchGenres() {
        assertTrue(Mood.CHILL.matches("Lo-Fi Hip Hop"))
        assertTrue(Mood.ENERGETIC.matches("Drum & Bass"))
        assertTrue(Mood.PARTY.matches("Deep House"))
        assertFalse(Mood.CHILL.matches(""))
        assertFalse(Mood.FOCUS.matches("Dubstep"))
        assertEquals(listOf("Happy", "Euphoric"), Mood.HAPPY.ncsMatches(listOf("Angry", "Happy", "Euphoric", "Dark")))
    }

    @Test
    fun focusRounds() {
        val plan = FocusTimer.Plan(25, 5, 2)
        val focus = FocusTimer.Mode.Focus(endsAt = 1_500_000, round = 1, plan = plan, startedAt = 0)
        val pause = FocusTimer.next(focus, now = 1_500_000)
        assertEquals(FocusTimer.Mode.Break(1_800_000, 1, plan, 1_500_000), pause)
        val second = FocusTimer.next(pause, now = 1_800_000)
        assertEquals(2, (second as FocusTimer.Mode.Focus).round)
        // The last round's focus ends the session: no break after it.
        assertEquals(FocusTimer.Mode.Off, FocusTimer.next(second, now = 3_300_000))
    }
}
