package com.jambo199.vividmusic.playback

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.jambo199.vividmusic.AppGraph
import com.jambo199.vividmusic.MainActivity
import com.jambo199.vividmusic.R
import com.jambo199.vividmusic.data.AlarmMusic
import com.jambo199.vividmusic.data.HomeFeedBuilder
import com.jambo199.vividmusic.data.Library
import com.jambo199.vividmusic.data.Settings
import com.jambo199.vividmusic.data.Song
import com.jambo199.vividmusic.data.SongOfTheDay
import com.jambo199.vividmusic.data.UserData
import com.jambo199.vividmusic.data.UserDataStore
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToInt

/** Wake up to music: sets the alarm with Android, and works out what it plays. */
object MusicAlarm {
    const val ACTION_RING = "com.jambo199.vividmusic.alarm.RING"
    const val ACTION_SNOOZE = "com.jambo199.vividmusic.alarm.SNOOZE"
    const val ACTION_STOP = "com.jambo199.vividmusic.alarm.STOP"
    const val ACTION_FOCUS_RESUME = "com.jambo199.vividmusic.focus.RESUME"
    const val EXTRA_SNOOZED = "vivid.alarm.snoozed"
    private const val EXTRA_FOCUS_MIN = "vivid.focus.focus"
    private const val EXTRA_BREAK_MIN = "vivid.focus.break"
    private const val EXTRA_ROUNDS = "vivid.focus.rounds"
    private const val EXTRA_ROUND = "vivid.focus.round"
    private const val FOCUS_REQUEST = 74
    const val SNOOZE_MINUTES = 9L

    /** How long the gentle wake-up takes to reach full volume. */
    const val GENTLE_MS = 60_000L
    const val NOTIFICATION_ID = 2002
    private const val CHANNEL = "alarm"
    private const val RING_REQUEST = 70
    private const val SNOOZE_REQUEST = 71

    /** When an alarm at [hour]:[minute] next rings after [now]; [days] are 1 = Monday … 7 = Sunday. */
    fun nextRing(hour: Int, minute: Int, days: Set<Int>, now: LocalDateTime): LocalDateTime {
        var at = now.toLocalDate().atTime(hour, minute)
        if (!at.isAfter(now)) at = at.plusDays(1)
        val valid = days.filter { it in 1..7 }
        if (valid.isNotEmpty()) {
            while (at.dayOfWeek.value !in valid) at = at.plusDays(1)
        }
        return at
    }

    fun nextRing(s: Settings, now: LocalDateTime = LocalDateTime.now()): LocalDateTime? =
        if (s.alarmEnabled) nextRing(s.alarmHour, s.alarmMinute, s.alarmDays, now) else null

    /** "Rings in 8 h 5 min", rounded up to the minute. */
    fun untilText(from: LocalDateTime, to: LocalDateTime): String {
        val minutes = (Duration.between(from, to).seconds + 59).coerceAtLeast(0) / 60
        val days = minutes / (24 * 60)
        val hours = minutes / 60 % 24
        val mins = minutes % 60
        val parts = buildList {
            if (days > 0) add(if (days == 1L) "1 day" else "$days days")
            if (hours > 0) add("$hours h")
            if (mins > 0 || isEmpty()) add("$mins min")
        }
        return "Rings in " + parts.joinToString(" ")
    }

    /** The wake-up music, in the order it plays; empty when there's nothing to play. */
    fun songsFor(music: AlarmMusic, lib: Library, data: UserData, now: Long): List<Song> {
        val all = lib.songs
        if (all.isEmpty()) return emptyList()
        val day = UserDataStore.dayKey(now)
        return when (music) {
            AlarmMusic.FAVORITES -> data.favorites.mapNotNull { lib.byKey[it] }.shuffled().ifEmpty { all.shuffled() }
            AlarmMusic.ALL -> all.shuffled()
            AlarmMusic.SONG_OF_THE_DAY -> {
                val first = SongOfTheDay.pick(lib, day)
                listOfNotNull(first) + all.filter { it.key != first?.key }.shuffled()
            }
            AlarmMusic.DAILY_MIX ->
                HomeFeedBuilder.dailyMixes(lib, data, day.hashCode().toLong()).firstOrNull()?.songs?.shuffled() ?: all.shuffled()
        }
    }

    /** True when Android lets the app ring on the dot (otherwise it may be a few minutes late). */
    fun canRingOnTime(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true
    }

    /** Sets (or clears) the alarm with Android to match the settings. */
    fun schedule(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val ring = ringIntent(context, RING_REQUEST, snoozed = false)
        val next = nextRing(AppGraph.settings.current)
        if (next == null) {
            alarms.cancel(ring)
            return
        }
        set(context, alarms, next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), ring)
    }

    private fun set(context: Context, alarms: AlarmManager, at: Long, operation: PendingIntent) {
        try {
            if (canRingOnTime(context)) {
                // An alarm clock: shows in the status bar and wakes the phone from deep sleep.
                alarms.setAlarmClock(AlarmManager.AlarmClockInfo(at, openAlarmPage(context)), operation)
            } else {
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation)
            }
        } catch (_: SecurityException) {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, operation)
        }
    }

    /**
     * Starts the wake-up music. Unless [force]d, music that's already playing carries on; it
     * would be odd for an alarm to interrupt what you're listening to.
     */
    fun ring(context: Context, force: Boolean) {
        if (PlaybackService.ringIfRunning(force)) return
        val start = Intent(context, PlaybackService::class.java).setAction(ACTION_RING)
        try {
            ContextCompat.startForegroundService(context, start)
        } catch (_: Exception) {
            // Android refused to start music from the background: a tap on this will.
            notify(context, missedNotification(context))
        }
    }

    fun snooze(context: Context) {
        PlaybackService.stopAlarm()
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val at = LocalDateTime.now().plusMinutes(SNOOZE_MINUTES)
        set(context, alarms, at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), ringIntent(context, SNOOZE_REQUEST, snoozed = true))
        val time = at.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
        notify(
            context,
            builder(context)
                .setContentTitle("Snoozed until $time")
                .setContentText("Your music comes back in $SNOOZE_MINUTES minutes")
                .setSilent(true)
                .addAction(0, "Cancel snooze", actionIntent(context, ACTION_STOP))
                .build(),
        )
    }

    fun stop(context: Context) {
        PlaybackService.stopAlarm()
        context.getSystemService(AlarmManager::class.java)?.cancel(ringIntent(context, SNOOZE_REQUEST, snoozed = true))
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    /** A sleeping phone with the media volume turned right down would never wake you. */
    fun raiseVolume(context: Context) {
        val audio = context.getSystemService(AudioManager::class.java) ?: return
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val least = (max * 0.4f).roundToInt().coerceAtLeast(1)
        if (audio.getStreamVolume(AudioManager.STREAM_MUSIC) < least) {
            runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, least, 0) }
        }
    }

    /** Shown for the moment between the alarm going off and the music's own notification. */
    fun startingNotification(context: Context, title: String = "Good morning"): Notification =
        builder(context).setContentTitle(title).setContentText("Starting your music…").setSilent(true).build()

    // region focus timer breaks

    /** Sets an alarm to bring the music back when [pause] is over. */
    fun scheduleFocusResume(context: Context, pause: FocusTimer.Mode.Break) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val operation = focusIntent(context, pause.plan, pause.round)
        try {
            if (canRingOnTime(context)) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, pause.endsAt, operation)
            else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, pause.endsAt, operation)
        } catch (_: SecurityException) {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, pause.endsAt, operation)
        }
    }

    fun cancelFocusResume(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(focusIntent(context, FocusTimer.PLANS.first(), 0))
    }

    /** The break is over (or was skipped): the next round starts and the music comes back. */
    fun resumeFocus(context: Context, plan: FocusTimer.Plan, round: Int) {
        cancelFocusResume(context)
        FocusTimer.resumeAfterBreak(plan, round)
        if (PlaybackService.resumeFocusIfRunning()) return
        try {
            ContextCompat.startForegroundService(context, Intent(context, PlaybackService::class.java).setAction(ACTION_FOCUS_RESUME))
        } catch (_: Exception) {
            notify(
                context,
                builder(context)
                    .setContentTitle("Break's over")
                    .setContentText("Tap to bring your music back")
                    .setContentIntent(openPlayer(context))
                    .setAutoCancel(true)
                    .build(),
            )
        }
    }

    /** Skips the rest of the current break. */
    fun endBreakNow(context: Context) {
        val pause = FocusTimer.mode.value as? FocusTimer.Mode.Break ?: return
        resumeFocus(context, pause.plan, pause.round)
    }

    internal fun onFocusAlarm(context: Context, intent: Intent) {
        val plan = FocusTimer.Plan(
            intent.getIntExtra(EXTRA_FOCUS_MIN, 25),
            intent.getIntExtra(EXTRA_BREAK_MIN, 5),
            intent.getIntExtra(EXTRA_ROUNDS, 4),
        )
        resumeFocus(context, plan, intent.getIntExtra(EXTRA_ROUND, 1))
    }

    private fun focusIntent(context: Context, plan: FocusTimer.Plan, round: Int): PendingIntent = PendingIntent.getBroadcast(
        context,
        FOCUS_REQUEST,
        Intent(context, AlarmReceiver::class.java)
            .setAction(ACTION_FOCUS_RESUME)
            .putExtra(EXTRA_FOCUS_MIN, plan.focusMinutes)
            .putExtra(EXTRA_BREAK_MIN, plan.breakMinutes)
            .putExtra(EXTRA_ROUNDS, plan.rounds)
            .putExtra(EXTRA_ROUND, round),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    // endregion

    /** "Good morning" with Snooze and Stop, while the wake-up music plays. */
    fun showRinging(context: Context, music: AlarmMusic) = notify(
        context,
        builder(context)
            .setContentTitle("Good morning")
            .setContentText("Waking you up with ${music.label.lowercase()}")
            .setContentIntent(openPlayer(context))
            .setAutoCancel(true)
            .addAction(0, "Snooze $SNOOZE_MINUTES min", actionIntent(context, ACTION_SNOOZE))
            .addAction(0, "Stop", actionIntent(context, ACTION_STOP))
            .build(),
    )

    fun showNothingToPlay(context: Context) = notify(
        context,
        builder(context)
            .setContentTitle("Your music alarm went off")
            .setContentText("There were no songs to play. Add some music, then try again.")
            .setContentIntent(openAlarmPage(context))
            .setAutoCancel(true)
            .build(),
    )

    fun cancelRinging(context: Context) = NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)

    private fun missedNotification(context: Context): Notification = builder(context)
        .setContentTitle("Good morning")
        .setContentText("Tap to start your wake-up music")
        .setContentIntent(
            PendingIntent.getActivity(
                context,
                RING_REQUEST,
                Intent(context, MainActivity::class.java)
                    .putExtra(MainActivity.EXTRA_SHORTCUT, "wake")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )
        .setAutoCancel(true)
        .build()

    private fun builder(context: Context): NotificationCompat.Builder {
        NotificationManagerCompat.from(context).createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_HIGH)
                .setName("Music alarm")
                .setDescription("Your wake-up music: snooze or stop it from here")
                // The music is the alarm; the notification itself stays quiet.
                .setSound(null, null)
                .setVibrationEnabled(false)
                .build(),
        )
        return NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
    }

    private fun notify(context: Context, notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    private fun ringIntent(context: Context, request: Int, snoozed: Boolean): PendingIntent = PendingIntent.getBroadcast(
        context,
        request,
        Intent(context, AlarmReceiver::class.java).setAction(ACTION_RING).putExtra(EXTRA_SNOOZED, snoozed),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun actionIntent(context: Context, action: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        action.hashCode(),
        Intent(context, AlarmReceiver::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun openAlarmPage(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        SNOOZE_REQUEST + 1,
        Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_SHORTCUT, "alarm")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun openPlayer(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        SNOOZE_REQUEST + 2,
        Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_PLAYER, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

/** The alarm going off, its Snooze and Stop buttons, and re-setting it after a reboot or update. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            MusicAlarm.ACTION_RING -> {
                MusicAlarm.ring(context, force = false)
                if (!intent.getBooleanExtra(MusicAlarm.EXTRA_SNOOZED, false)) {
                    // A one-off alarm switches itself off; a repeating one sets the next day.
                    if (AppGraph.settings.current.alarmDays.isEmpty()) {
                        AppGraph.settings.update { it.copy(alarmEnabled = false) }
                    }
                    MusicAlarm.schedule(context)
                }
            }
            MusicAlarm.ACTION_SNOOZE -> MusicAlarm.snooze(context)
            MusicAlarm.ACTION_FOCUS_RESUME -> MusicAlarm.onFocusAlarm(context, intent)
            MusicAlarm.ACTION_STOP -> MusicAlarm.stop(context)
            // Booted, updated, the clock or time zone changed, or exact alarms were allowed.
            else -> MusicAlarm.schedule(context)
        }
    }
}
