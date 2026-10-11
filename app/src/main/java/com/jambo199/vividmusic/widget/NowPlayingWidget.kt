package com.jambo199.vividmusic.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.view.KeyEvent
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.jambo199.vividmusic.AppGraph
import com.jambo199.vividmusic.MainActivity
import com.jambo199.vividmusic.R
import com.jambo199.vividmusic.data.Song
import com.jambo199.vividmusic.playback.PlaybackService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Home-screen widget: artwork, song, and previous / play-pause / next buttons. */
class NowPlayingWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        WidgetUpdater.refresh(context)
    }
}

object WidgetUpdater {
    private var lastSong: Song? = null
    private var lastPlaying = false
    private var artKey: String? = null
    private var art: Bitmap? = null
    private var job: Job? = null

    private fun component(context: Context) = ComponentName(context, NowPlayingWidget::class.java)

    private fun hasWidgets(context: Context): Boolean = try {
        AppWidgetManager.getInstance(context).getAppWidgetIds(component(context)).isNotEmpty()
    } catch (e: Exception) {
        false
    }

    /** Called by the playback service whenever the song or play state changes. */
    fun update(context: Context, song: Song?, isPlaying: Boolean) {
        lastSong = song
        lastPlaying = isPlaying
        if (!hasWidgets(context)) return
        val app = context.applicationContext
        job?.cancel()
        job = AppGraph.scope.launch {
            if (song?.key != artKey) {
                art = song?.let { s ->
                    withContext(Dispatchers.IO) { AppGraph.artwork.load(s, ART_SIZE)?.let(::scaleDown) }
                }
                artKey = song?.key
            }
            push(app, song, isPlaying, art)
        }
    }

    /** Redraws from what we know; falls back to the saved queue when nothing has played yet. */
    fun refresh(context: Context) {
        val song = lastSong ?: AppGraph.queueStore.load()?.let { it.songs.getOrNull(it.index) }
        update(context, song, lastPlaying)
    }

    private fun scaleDown(bitmap: Bitmap): Bitmap {
        val max = maxOf(bitmap.width, bitmap.height)
        if (max <= ART_SIZE) return bitmap
        val scale = ART_SIZE.toFloat() / max
        return Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
    }

    private fun push(context: Context, song: Song?, isPlaying: Boolean, art: Bitmap?) {
        val views = RemoteViews(context.packageName, R.layout.widget_now_playing)
        views.setTextViewText(R.id.widget_title, song?.title ?: context.getString(R.string.widget_idle_title))
        views.setTextViewText(R.id.widget_artist, song?.displayArtist ?: context.getString(R.string.widget_idle_subtitle))
        if (art != null) {
            views.setImageViewBitmap(R.id.widget_art, art)
        } else {
            views.setImageViewResource(R.id.widget_art, R.drawable.ic_notification)
        }
        views.setImageViewResource(R.id.widget_play, if (isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play)
        views.setContentDescription(
            R.id.widget_play,
            context.getString(if (isPlaying) R.string.widget_pause else R.string.widget_play),
        )
        if (song == null) {
            // Nothing to resume yet: open the app instead of starting an empty player.
            views.setOnClickPendingIntent(R.id.widget_play, openApp(context, false))
        } else {
            views.setOnClickPendingIntent(R.id.widget_play, action(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
        }
        views.setOnClickPendingIntent(R.id.widget_next, action(context, KeyEvent.KEYCODE_MEDIA_NEXT))
        views.setOnClickPendingIntent(R.id.widget_prev, action(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS))
        views.setOnClickPendingIntent(R.id.widget_root, openApp(context, song != null))
        try {
            AppWidgetManager.getInstance(context).updateAppWidget(component(context), views)
        } catch (_: Exception) {
        }
    }

    private fun openApp(context: Context, showPlayer: Boolean): PendingIntent = PendingIntent.getActivity(
        context,
        if (showPlayer) 2 else 1,
        Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_PLAYER, showPlayer)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun action(context: Context, keyCode: Int): PendingIntent = PendingIntent.getBroadcast(
        context,
        keyCode,
        Intent(context, WidgetActionReceiver::class.java).putExtra(WidgetActionReceiver.EXTRA_KEY, keyCode),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private const val ART_SIZE = 256
}

/**
 * Handles widget buttons. While the player is running the command goes straight to it; when it
 * isn't, only "play" starts it (as a foreground service, allowed after a widget tap), so Android
 * never sees a service start that can't become a foreground service.
 */
class WidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val keyCode = intent.getIntExtra(EXTRA_KEY, 0)
        if (PlaybackService.handleWidgetKey(keyCode)) return
        if (keyCode != KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) return
        val start = Intent(Intent.ACTION_MEDIA_BUTTON)
            .setClass(context, PlaybackService::class.java)
            .putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY))
        try {
            ContextCompat.startForegroundService(context, start)
        } catch (e: Exception) {
            // Background start refused (rare on some OEM builds): open the app instead.
            try {
                context.startActivity(
                    Intent(context, MainActivity::class.java)
                        .putExtra(MainActivity.EXTRA_OPEN_PLAYER, true)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            } catch (_: Exception) {
            }
        }
    }

    companion object {
        const val EXTRA_KEY = "vivid.widget_key"
    }
}
