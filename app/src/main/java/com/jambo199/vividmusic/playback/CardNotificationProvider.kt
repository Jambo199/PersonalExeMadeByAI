package com.jambo199.vividmusic.playback

import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.MoreExecutors
import com.jambo199.vividmusic.AppGraph
import com.jambo199.vividmusic.R
import com.jambo199.vividmusic.data.NotificationStyle

/**
 * The music notification. By default it's a plain notification card: artwork, title, artist, a
 * progress bar with the time played, and previous / play-pause / next. Phones that
 * draw their own card for media notifications (vivo's notification centre, for one) leave the
 * progress bar out of those, but they do show it on a plain card. A plain card has room for
 * three buttons, so favourite stays in the app. With the "phone's media player" setting,
 * [system] makes the usual media notification instead, which is what Android's quick-settings
 * media player is drawn from. Headphone, Bluetooth and car controls work with either.
 */
@OptIn(UnstableApi::class)
class CardNotificationProvider(
    private val context: Context,
    private val system: MediaNotification.Provider,
) : MediaNotification.Provider {
    private var artFor: String? = null
    private var art: Bitmap? = null

    override fun createNotification(
        mediaSession: MediaSession,
        mediaButtonPreferences: ImmutableList<CommandButton>,
        actionFactory: MediaNotification.ActionFactory,
        onNotificationChangedCallback: MediaNotification.Provider.Callback,
    ): MediaNotification {
        if (AppGraph.settings.current.notificationStyle == NotificationStyle.SYSTEM) {
            return system.createNotification(mediaSession, mediaButtonPreferences, actionFactory, onNotificationChangedCallback)
        }
        ensureChannel()
        val player = mediaSession.player
        val metadata = player.mediaMetadata
        val key = player.currentMediaItem?.mediaId

        // Artwork: straight away if it's cached, otherwise post again once it's loaded.
        if (key != artFor) {
            artFor = key
            art = null
            val future = mediaSession.bitmapLoader.loadBitmapFromMetadata(metadata)
            if (future != null) {
                if (future.isDone) {
                    art = runCatching { future.get() }.getOrNull()
                } else {
                    Futures.addCallback(
                        future,
                        object : FutureCallback<Bitmap> {
                            override fun onSuccess(result: Bitmap) {
                                if (artFor != key) return
                                art = result
                                onNotificationChangedCallback.onNotificationChanged(
                                    build(mediaSession, actionFactory),
                                )
                            }

                            override fun onFailure(t: Throwable) = Unit
                        },
                        MoreExecutors.directExecutor(),
                    )
                }
            }
        }
        return build(mediaSession, actionFactory)
    }

    private fun build(session: MediaSession, actions: MediaNotification.ActionFactory): MediaNotification {
        val player = session.player
        val metadata = player.mediaMetadata
        val playing = player.playWhenReady && player.playbackState != Player.STATE_ENDED
        val duration = player.duration
        val position = player.currentPosition.coerceAtLeast(0)

        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(metadata.title ?: metadata.displayTitle)
            // The artist, or the line being sung when lyrics are showing.
            .setContentText(metadata.artist ?: metadata.albumArtist)
            .setLargeIcon(art)
            .setContentIntent(session.sessionActivity)
            .setDeleteIntent(actions.createMediaActionPendingIntent(session, Player.COMMAND_STOP.toLong()))
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(playing)

        if (duration != C.TIME_UNSET && duration > 0 && !player.isCurrentMediaItemLive) {
            builder.setProgress(
                ProgressNotificationProvider.PROGRESS_MAX,
                ProgressNotificationProvider.progressOf(position, duration),
                false,
            )
            // The time played ticks along by itself between updates.
            if (playing) {
                val speed = player.playbackParameters.speed.coerceAtLeast(0.1f)
                builder.setShowWhen(true)
                    .setUsesChronometer(true)
                    .setWhen(System.currentTimeMillis() - (position / speed).toLong())
            }
        }

        builder.addAction(
            actions.createMediaAction(session, icon(R.drawable.ic_widget_prev), "Previous", Player.COMMAND_SEEK_TO_PREVIOUS),
        )
        builder.addAction(
            actions.createMediaAction(
                session,
                icon(if (playing) R.drawable.ic_widget_pause else R.drawable.ic_widget_play),
                if (playing) "Pause" else "Play",
                Player.COMMAND_PLAY_PAUSE,
            ),
        )
        builder.addAction(
            actions.createMediaAction(session, icon(R.drawable.ic_widget_next), "Next", Player.COMMAND_SEEK_TO_NEXT),
        )
        return MediaNotification(DefaultMediaNotificationProvider.DEFAULT_NOTIFICATION_ID, builder.build())
    }

    override fun handleCustomCommand(session: MediaSession, action: String, extras: Bundle): Boolean =
        system.handleCustomCommand(session, action, extras)

    private fun icon(res: Int) = IconCompat.createWithResource(context, res)

    private fun ensureChannel() {
        NotificationManagerCompat.from(context).createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName("Now playing")
                .setDescription("The song that's playing, with its controls")
                .setShowBadge(false)
                .build(),
        )
    }

    private companion object {
        const val CHANNEL = "now_playing"
    }
}
