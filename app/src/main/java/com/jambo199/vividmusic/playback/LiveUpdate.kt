package com.jambo199.vividmusic.playback

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.jambo199.vividmusic.MainActivity
import com.jambo199.vividmusic.R

/**
 * The playing song as an Android 16 "Live Update": the status-bar chip on Android 16+, and the
 * island on phones that show Live Updates there (vivo's Origin Island on OriginOS 6, among
 * others). Music players' own media notifications can't be Live Updates, so this is a second,
 * plain notification that only exists while music plays.
 */
class LiveUpdate(private val context: Context) {
    private val manager = NotificationManagerCompat.from(context)
    private var shown = false

    /** What was last posted, so unchanged updates aren't sent again. */
    private var last: List<Any?> = emptyList()

    fun show(
        title: String,
        text: String,
        chip: String,
        artwork: Bitmap?,
        positionMs: Long,
        durationMs: Long,
        speed: Float,
    ) {
        // Progress moves in whole seconds; the chronometer covers the time in between.
        val signature = listOf(title, text, chip, artwork?.generationId, durationMs, positionMs / 15_000)
        if (shown && signature == last) return
        last = signature
        if (!canNotify()) return
        ensureChannel()
        // A chronometer from the song's start keeps the elapsed time ticking without updates.
        val startedAt = System.currentTimeMillis() - (positionMs / speed.coerceAtLeast(0.1f)).toLong()
        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setLargeIcon(artwork)
            .setContentIntent(openPlayer())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(true)
            .setWhen(startedAt)
            .setUsesChronometer(true)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addExtras(
                Bundle().apply {
                    putBoolean(EXTRA_REQUEST_PROMOTED_ONGOING, true)
                    putString(EXTRA_SHORT_CRITICAL_TEXT, chip)
                },
            )
        if (durationMs > 0) {
            builder.setProgress(1000, (positionMs * 1000 / durationMs).toInt().coerceIn(0, 1000), false)
        }
        try {
            manager.notify(NOTIFICATION_ID, builder.build())
            shown = true
        } catch (_: SecurityException) {
        }
    }

    fun hide() {
        if (!shown) return
        shown = false
        last = emptyList()
        manager.cancel(NOTIFICATION_ID)
    }

    private fun canNotify(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return manager.areNotificationsEnabled()
    }

    private fun ensureChannel() {
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName("Now playing (Live Update)")
                .setDescription("The song that's playing, for the status-bar chip and the Dynamic Island on Android 16 and later")
                .setShowBadge(false)
                .build(),
        )
    }

    private fun openPlayer(): PendingIntent = PendingIntent.getActivity(
        context,
        NOTIFICATION_ID,
        Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_PLAYER, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    companion object {
        private const val CHANNEL = "live_update"
        private const val NOTIFICATION_ID = 2004

        // The Android 16 extras, by name, so the app builds against an older SDK.
        const val EXTRA_REQUEST_PROMOTED_ONGOING = "android.requestPromotedOngoing"
        const val EXTRA_SHORT_CRITICAL_TEXT = "android.shortCriticalText"

        /** Live Updates arrived in Android 16. */
        val supported: Boolean get() = Build.VERSION.SDK_INT >= 36

        /**
         * Whether the user lets this app post Live Updates (Android 16's
         * NotificationManager.canPostPromotedNotifications), or null when unknown.
         */
        fun allowed(context: Context): Boolean? {
            if (!supported) return null
            return try {
                val manager = context.getSystemService(NotificationManager::class.java)
                NotificationManager::class.java.getMethod("canPostPromotedNotifications").invoke(manager) as? Boolean
            } catch (_: Exception) {
                null
            }
        }

        /** Android's page for allowing this app's Live Updates. */
        fun settingsIntent(context: Context): Intent =
            Intent("android.settings.MANAGE_APP_PROMOTED_NOTIFICATIONS")
                .putExtra("android.provider.extra.APP_PACKAGE", context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        /** Chip text: short enough for a status-bar pill. */
        fun chipText(title: String, line: String?): String {
            val source = line?.takeIf { it.isNotBlank() } ?: title
            return if (source.length <= CHIP_MAX) source else source.take(CHIP_MAX - 1).trimEnd() + "…"
        }

        private const val CHIP_MAX = 14
    }
}
