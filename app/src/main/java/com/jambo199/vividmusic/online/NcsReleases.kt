package com.jambo199.vividmusic.online

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.jambo199.vividmusic.AppGraph
import com.jambo199.vividmusic.MainActivity
import com.jambo199.vividmusic.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/** Tells you when NoCopyrightSounds puts out new songs, by checking ncs.io twice a day. */
object NcsReleases {
    private const val WORK = "ncs-releases"
    private const val CHANNEL = "ncs"
    private const val NOTIFICATION_ID = 2003
    private const val KEPT = 300

    /** Starts or stops the background check to match [enabled]. */
    fun schedule(context: Context, enabled: Boolean) {
        val work = WorkManager.getInstance(context)
        if (!enabled) {
            work.cancelUniqueWork(WORK)
            return
        }
        val request = PeriodicWorkRequestBuilder<CheckWorker>(12, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        work.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** The ids in [latest] (newest first) not seen before. */
    fun fresh(latest: List<String>, seen: Set<String>): List<String> = latest.filter { it !in seen }

    /** The notification text for [titles], newest first: "Title – Artist and 2 more". */
    fun summary(titles: List<String>): String = when (titles.size) {
        0 -> ""
        1 -> titles[0]
        else -> "${titles[0]} and ${titles.size - 1} more"
    }

    class CheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            if (!AppGraph.settings.current.ncsAlerts) return Result.success()
            val tracks = try {
                withContext(Dispatchers.IO) { Ncs.search().tracks }
            } catch (e: Exception) {
                return Result.retry()
            }
            if (tracks.isEmpty()) return Result.success()
            val prefs = applicationContext.getSharedPreferences("ncs_releases", Context.MODE_PRIVATE)
            val seen = prefs.getStringSet("seen", null)
            val ids = tracks.map { it.id }
            // The very first check only learns what's out already.
            if (seen != null) {
                val fresh = fresh(ids, seen).toSet()
                val newTracks = tracks.filter { it.id in fresh }
                if (newTracks.isNotEmpty()) notify(applicationContext, newTracks)
            }
            prefs.edit().putStringSet("seen", (ids + seen.orEmpty()).distinct().take(KEPT).toSet()).apply()
            return Result.success()
        }
    }

    private fun notify(context: Context, tracks: List<NcsTrack>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val manager = NotificationManagerCompat.from(context)
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName("New NCS releases")
                .setDescription("When NoCopyrightSounds puts out new songs")
                .build(),
        )
        val open = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_SHORTCUT, "ncs")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val titles = tracks.map { "${it.title} – ${it.artists}" }
        val style = NotificationCompat.InboxStyle()
        titles.take(6).forEach { style.addLine(it) }
        if (titles.size > 6) style.setSummaryText("and ${titles.size - 6} more")
        manager.notify(
            NOTIFICATION_ID,
            NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(if (tracks.size == 1) "New on NCS" else "${tracks.size} new songs on NCS")
                .setContentText(summary(titles))
                .setStyle(style)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build(),
        )
    }
}
