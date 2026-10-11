package com.jambo199.vividmusic.playback

import android.app.Notification
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList

/**
 * The usual media notification, plus how far into the song it is as the notification's own
 * progress bar. Android's media player reads the position from the session, but phones that
 * draw their own music notification (vivo's notification centre, for one) only show a bar when
 * the notification carries the progress itself. The service re-posts it every few seconds while
 * playing, so the bar keeps moving.
 */
@OptIn(UnstableApi::class)
class ProgressNotificationProvider(private val delegate: MediaNotification.Provider) : MediaNotification.Provider {
    override fun createNotification(
        mediaSession: MediaSession,
        mediaButtonPreferences: ImmutableList<CommandButton>,
        actionFactory: MediaNotification.ActionFactory,
        onNotificationChangedCallback: MediaNotification.Provider.Callback,
    ): MediaNotification {
        val callback = MediaNotification.Provider.Callback { updated ->
            onNotificationChangedCallback.onNotificationChanged(withProgress(mediaSession, updated))
        }
        return withProgress(mediaSession, delegate.createNotification(mediaSession, mediaButtonPreferences, actionFactory, callback))
    }

    override fun handleCustomCommand(session: MediaSession, action: String, extras: Bundle): Boolean =
        delegate.handleCustomCommand(session, action, extras)

    private fun withProgress(session: MediaSession, media: MediaNotification): MediaNotification {
        val player = session.player
        val duration = player.duration
        val extras = media.notification.extras
        if (duration == C.TIME_UNSET || duration <= 0 || player.isCurrentMediaItemLive) {
            extras.remove(Notification.EXTRA_PROGRESS)
            extras.remove(Notification.EXTRA_PROGRESS_MAX)
            return media
        }
        extras.putInt(Notification.EXTRA_PROGRESS_MAX, PROGRESS_MAX)
        extras.putInt(Notification.EXTRA_PROGRESS, progressOf(player.currentPosition, duration))
        extras.putBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false)
        return media
    }

    companion object {
        const val PROGRESS_MAX = 1000

        /** [positionMs] of [durationMs] on a 0..[PROGRESS_MAX] scale. */
        fun progressOf(positionMs: Long, durationMs: Long): Int =
            if (durationMs <= 0) 0 else (positionMs.coerceAtLeast(0) * PROGRESS_MAX / durationMs).toInt().coerceIn(0, PROGRESS_MAX)
    }
}
