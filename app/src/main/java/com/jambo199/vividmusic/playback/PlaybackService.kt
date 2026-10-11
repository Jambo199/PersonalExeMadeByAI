package com.jambo199.vividmusic.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.extractor.metadata.id3.BinaryFrame
import androidx.media3.extractor.metadata.id3.InternalFrame
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.extractor.metadata.vorbis.VorbisComment
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.jambo199.vividmusic.AppGraph
import com.jambo199.vividmusic.MainActivity
import com.jambo199.vividmusic.R
import com.jambo199.vividmusic.data.EmbeddedLyrics
import com.jambo199.vividmusic.data.ReplayGain
import com.jambo199.vividmusic.data.Song
import com.jambo199.vividmusic.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Background playback: owns ExoPlayer and the media session that drives the notification,
 * lock screen, headset buttons, Bluetooth and Android Auto controls.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaLibraryService() {

    private lateinit var exo: ExoPlayer
    private var session: MediaLibrarySession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var consecutiveErrors = 0
    private var countedCurrent = false
    private var replayGain = ReplayGain()
    private var fading = false
    private var unsavedListeningMs = 0L
    private var saveQueueJob: Job? = null
    private var saveStateJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        val settings = AppGraph.settings.current

        exo = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
        exo.setShuffleOrder(SmartShuffleOrder(0))
        exo.skipSilenceEnabled = settings.skipSilence
        if (settings.rememberSpeed) {
            exo.playbackParameters = PlaybackParameters(settings.playbackSpeed, settings.playbackPitch)
        }
        exo.addListener(playerListener)

        val openPlayer = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_PLAYER, true)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaLibrarySession.Builder(this, ShuffleAwarePlayer(exo), SessionCallback())
            .setSessionActivity(openPlayer)
            .setBitmapLoader(ServiceBitmapLoader())
            .build()

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this).build().apply {
                setSmallIcon(R.drawable.ic_notification)
            },
        )

        restoreQueue()
        AudioEffects.attach(this, exo.audioSessionId, settings.eq)
        AppGraph.lyrics.onSongChanged(exo.currentMediaItem?.mediaId)
        updateCustomLayout()
        observeState()
        updateWidget()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

    /** Widget buttons, applied directly while the service is alive. */
    private fun handleKey(keyCode: Int) {
        when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                val ended = exo.playbackState == Player.STATE_ENDED
                if (exo.playWhenReady && !ended && exo.playbackState != Player.STATE_IDLE) {
                    exo.pause()
                } else {
                    if (exo.playbackState == Player.STATE_IDLE) exo.prepare()
                    if (ended) exo.seekToDefaultPosition(exo.currentTimeline.getFirstWindowIndex(exo.shuffleModeEnabled).coerceAtLeast(0))
                    exo.play()
                }
            }
            KeyEvent.KEYCODE_MEDIA_NEXT -> exo.seekToNext()
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> exo.seekToPrevious()
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0 ||
            AppGraph.settings.current.stopOnTaskRemoved
        ) {
            saveStateNow(sync = true)
            player?.pause()
            stopSelf()
        }
    }

    override fun onDestroy() {
        saveQueueNow(sync = true)
        saveStateNow(sync = true)
        WidgetUpdater.update(this, currentSong(), false)
        flushListeningTime()
        AppGraph.userData.flush()
        AudioEffects.release(this)
        session?.run {
            player.release()
            release()
        }
        session = null
        instance = null
        scope.cancel()
        super.onDestroy()
    }

    // region state observers

    private fun observeState() {
        scope.launch {
            AppGraph.settings.state.map { it.skipSilence }.distinctUntilChanged().collect {
                exo.skipSilenceEnabled = it
            }
        }
        scope.launch {
            AppGraph.settings.state.map { it.eq }.distinctUntilChanged().collect { AudioEffects.apply(it) }
        }
        scope.launch {
            AppGraph.settings.state.map { it.normalization }.distinctUntilChanged().collect { applyNormalization() }
        }
        scope.launch {
            AppGraph.userData.state.map { it.favoriteSet }.distinctUntilChanged().collect { updateCustomLayout() }
        }
        scope.launch {
            SleepTimer.mode.collectLatest { mode ->
                exo.pauseAtEndOfMediaItems = mode is SleepTimer.Mode.EndOfSong
                if (mode is SleepTimer.Mode.At) {
                    val remaining = mode.endElapsedMs - SystemClock.elapsedRealtime()
                    if (remaining > FADE_MS) delay(remaining - FADE_MS)
                    try {
                        fading = true
                        val start = exo.volume
                        val steps = 30
                        for (i in 1..steps) {
                            exo.volume = start * (1f - i / steps.toFloat())
                            delay(FADE_MS / steps)
                        }
                        exo.pause()
                    } finally {
                        fading = false
                        applyNormalization()
                    }
                    SleepTimer.cancel()
                }
            }
        }
        // Counts a play once a song has been heard for 30s (or half of a short song),
        // and saves the position regularly so a crash or reboot loses little.
        scope.launch {
            var lastSave = 0L
            while (isActive) {
                delay(1000)
                if (!exo.isPlaying) continue
                unsavedListeningMs += 1000
                if (unsavedListeningMs >= 30_000) flushListeningTime()
                val duration = exo.duration
                val threshold = if (duration > 0) minOf(30_000L, duration / 2) else 30_000L
                if (!countedCurrent && exo.currentPosition >= threshold) {
                    countedCurrent = true
                    exo.currentMediaItem?.mediaId?.let { AppGraph.userData.recordPlay(it) }
                }
                val now = SystemClock.elapsedRealtime()
                if (now - lastSave > 10_000) {
                    lastSave = now
                    saveStateSoon()
                }
            }
        }
    }

    private fun flushListeningTime() {
        AppGraph.userData.addListeningTime(unsavedListeningMs)
        unsavedListeningMs = 0
    }

    private fun currentSong(): Song? {
        if (!::exo.isInitialized) return null
        val item = exo.currentMediaItem ?: return null
        return AppGraph.library.findByKey(item.mediaId) ?: item.toSong()
    }

    private fun updateWidget() = WidgetUpdater.update(
        this,
        currentSong(),
        exo.playWhenReady && exo.playbackState != Player.STATE_ENDED && exo.playbackState != Player.STATE_IDLE,
    )

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (events.containsAny(
                    Player.EVENT_MEDIA_ITEM_TRANSITION,
                    Player.EVENT_PLAY_WHEN_READY_CHANGED,
                    Player.EVENT_PLAYBACK_STATE_CHANGED,
                    Player.EVENT_TIMELINE_CHANGED,
                )
            ) {
                updateWidget()
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            countedCurrent = false
            AppGraph.lyrics.onSongChanged(mediaItem?.mediaId)
            updateCustomLayout()
            saveStateSoon()
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) saveQueueSoon()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                consecutiveErrors = 0
            } else {
                saveStateSoon()
                flushListeningTime()
            }
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) = saveStateSoon()

        override fun onRepeatModeChanged(repeatMode: Int) = saveStateSoon()

        override fun onPlayerError(error: PlaybackException) {
            val title = exo.currentMediaItem?.mediaMetadata?.title ?: "this song"
            consecutiveErrors++
            if (consecutiveErrors < MAX_SKIPS && exo.mediaItemCount > 1 && exo.hasNextMediaItem()) {
                PlayerEvents.post("Couldn't play \"$title\" – skipped (${reasonOf(error)})")
                exo.seekToNextMediaItem()
                exo.prepare()
            } else {
                PlayerEvents.post("Couldn't play \"$title\" (${reasonOf(error)})")
            }
        }

        override fun onTracksChanged(tracks: Tracks) {
            AppGraph.lyrics.onEmbeddedLyrics(exo.currentMediaItem?.mediaId, findEmbeddedLyrics(tracks))
            replayGain = findReplayGain(tracks)
            applyNormalization()
        }

        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            AudioEffects.attach(this@PlaybackService, audioSessionId, AppGraph.settings.current.eq)
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM &&
                SleepTimer.mode.value is SleepTimer.Mode.EndOfSong
            ) {
                SleepTimer.cancel()
            }
        }

        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
            if (AppGraph.settings.current.rememberSpeed) {
                AppGraph.settings.update {
                    it.copy(playbackSpeed = playbackParameters.speed, playbackPitch = playbackParameters.pitch)
                }
            }
        }
    }

    private fun reasonOf(error: PlaybackException): String = when (error.errorCode) {
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> "file not found"
        PlaybackException.ERROR_CODE_IO_NO_PERMISSION -> "no permission"
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        -> "format not supported"
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED -> "file is damaged"
        else -> error.errorCodeName.lowercase().replace('_', ' ')
    }

    /** Sets the volume for the song's ReplayGain tags (unless the sleep timer is fading out). */
    private fun applyNormalization() {
        if (!::exo.isInitialized || fading) return
        exo.volume = replayGain.volume(AppGraph.settings.current.normalization)
    }

    private fun findReplayGain(tracks: Tracks): ReplayGain {
        var track: Float? = null
        var album: Float? = null
        fun take(key: String?, value: String?) {
            when (key?.uppercase()) {
                ReplayGain.TRACK_KEY -> track = track ?: ReplayGain.parseDb(value)
                ReplayGain.ALBUM_KEY -> album = album ?: ReplayGain.parseDb(value)
            }
        }
        for (group in tracks.groups) {
            for (i in 0 until group.length) {
                val metadata = group.getTrackFormat(i).metadata ?: continue
                for (j in 0 until metadata.length()) {
                    when (val entry = metadata.get(j)) {
                        is TextInformationFrame -> if (entry.id == "TXXX") take(entry.description, entry.values.firstOrNull())
                        is VorbisComment -> take(entry.key, entry.value)
                        is InternalFrame -> take(entry.description, entry.text)
                    }
                }
            }
        }
        return ReplayGain(track, album)
    }

    private fun findEmbeddedLyrics(tracks: Tracks): String? {
        for (group in tracks.groups) {
            for (i in 0 until group.length) {
                val metadata = group.getTrackFormat(i).metadata ?: continue
                for (j in 0 until metadata.length()) {
                    when (val entry = metadata.get(j)) {
                        is BinaryFrame -> if (entry.id == "USLT") {
                            EmbeddedLyrics.parseUslt(entry.data)?.let { return it }
                        }
                        is TextInformationFrame -> {
                            val desc = entry.description?.uppercase()
                            if (entry.id == "USLT" || desc in LYRICS_KEYS) {
                                entry.values.firstOrNull()?.takeIf { it.isNotBlank() }?.let { return it }
                            }
                        }
                        is VorbisComment -> {
                            val k = entry.key.uppercase()
                            if (k in LYRICS_KEYS) return entry.value
                        }
                    }
                }
            }
        }
        return null
    }

    // endregion

    // region notification favourite button

    private fun updateCustomLayout() {
        val s = session ?: return
        val key = exo.currentMediaItem?.mediaId
        val favorite = key != null && AppGraph.userData.isFavorite(key)
        val button = CommandButton.Builder()
            .setDisplayName(getString(if (favorite) R.string.unfavorite else R.string.favorite))
            .setIconResId(if (favorite) R.drawable.ic_favorite else R.drawable.ic_favorite_border)
            .setSessionCommand(FAVORITE_COMMAND)
            .build()
        s.setCustomLayout(ImmutableList.of(button))
    }

    private inner class SessionCallback : MediaLibrarySession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
                .add(FAVORITE_COMMAND)
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(commands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction == ACTION_FAVORITE) {
                exo.currentMediaItem?.mediaId?.let { AppGraph.userData.toggleFavorite(it) }
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }

        // Controllers can't pass file uris across, so rebuild them from the media id.
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> {
            val resolved = mediaItems.map { item ->
                if (item.localConfiguration != null) {
                    item
                } else {
                    val uri = item.requestMetadata.mediaUri ?: android.net.Uri.parse(item.mediaId)
                    item.buildUpon().setUri(uri).build()
                }
            }.toMutableList()
            return Futures.immediateFuture(resolved)
        }

        // Browsing (system media controls, Android Auto, car head units). The "recent" root lets
        // Android's Quick Settings player offer to resume the last song after the app closed.
        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val id = if (params?.isRecent == true) RECENT_ROOT else BROWSE_ROOT
            return Futures.immediateFuture(LibraryResult.ofItem(folderItem(id, getString(R.string.app_name)), params))
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val song = AppGraph.library.findByKey(mediaId)
                ?: (0 until exo.mediaItemCount).map { exo.getMediaItemAt(it) }.firstOrNull { it.mediaId == mediaId }?.toSong()
            return Futures.immediateFuture(
                if (song != null) LibraryResult.ofItem(song.toMediaItem(), null)
                else LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE),
            )
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val items: List<MediaItem> = when (parentId) {
                RECENT_ROOT -> listOfNotNull(
                    exo.currentMediaItem ?: AppGraph.queueStore.load()?.let { it.songs.getOrNull(it.index)?.toMediaItem() },
                )
                BROWSE_ROOT -> {
                    val queue = List(exo.mediaItemCount) { exo.getMediaItemAt(it) }
                    queue.ifEmpty { AppGraph.queueStore.load()?.songs?.map { it.toMediaItem() }.orEmpty() }
                }
                else -> emptyList()
            }
            val from = (page.coerceAtLeast(0).toLong() * pageSize.coerceAtLeast(1)).coerceAtMost(items.size.toLong()).toInt()
            val to = (from + pageSize.coerceAtLeast(1)).coerceAtMost(items.size)
            return Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.copyOf(items.subList(from, to)), params))
        }

        // Lets headphones/Bluetooth "play" and the system media controls restart the last queue.
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val saved = AppGraph.queueStore.load()
                ?: return Futures.immediateFailedFuture(UnsupportedOperationException("Nothing to resume"))
            // Same modes as when the queue was saved (restoreQueue does this for app launches).
            exo.repeatMode = saved.repeatMode
            exo.shuffleModeEnabled = saved.shuffle
            return Futures.immediateFuture(
                MediaSession.MediaItemsWithStartPosition(
                    saved.songs.map { it.toMediaItem() },
                    saved.index,
                    saved.positionMs,
                ),
            )
        }
    }

    // endregion

    // region persistence

    private fun restoreQueue() {
        if (exo.mediaItemCount > 0) return
        val saved = AppGraph.queueStore.load() ?: return
        exo.setMediaItems(saved.songs.map { it.toMediaItem() }, saved.index, saved.positionMs)
        exo.repeatMode = saved.repeatMode
        if (saved.shuffle) {
            exo.setShuffleOrder(SmartShuffleOrder.startingWith(saved.index, saved.songs.size))
            exo.shuffleModeEnabled = true
        }
    }

    private fun saveQueueSoon() {
        saveQueueJob?.cancel()
        saveQueueJob = scope.launch {
            delay(600)
            saveQueueNow()
        }
    }

    private fun saveQueueNow(sync: Boolean = false) {
        if (!::exo.isInitialized) return
        val songs = List(exo.mediaItemCount) { exo.getMediaItemAt(it).toSong() }
        val store = AppGraph.queueStore
        if (sync) {
            store.saveQueue(songs)
        } else {
            scope.launch { withContext(Dispatchers.IO) { store.saveQueue(songs) } }
            saveStateSoon()
        }
    }

    private fun saveStateSoon() {
        saveStateJob?.cancel()
        saveStateJob = scope.launch {
            delay(300)
            saveStateNow()
        }
    }

    private fun saveStateNow(sync: Boolean = false) {
        if (!::exo.isInitialized) return
        val index = exo.currentMediaItemIndex
        val position = exo.currentPosition
        val shuffle = exo.shuffleModeEnabled
        val repeat = exo.repeatMode
        val store = AppGraph.queueStore
        if (sync) {
            store.saveState(index, position, shuffle, repeat)
        } else {
            scope.launch { withContext(Dispatchers.IO) { store.saveState(index, position, shuffle, repeat) } }
        }
    }

    // endregion

    private fun folderItem(id: String, title: String): MediaItem = MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(
            androidx.media3.common.MediaMetadata.Builder()
                .setTitle(title)
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(androidx.media3.common.MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                .build(),
        )
        .build()

    companion object {
        private var instance: PlaybackService? = null

        /** Sends a widget button to the running player. Returns false if the service isn't running. */
        fun handleWidgetKey(keyCode: Int): Boolean {
            val service = instance ?: return false
            if (service.exo.mediaItemCount == 0) return false
            service.handleKey(keyCode)
            return true
        }

        private const val BROWSE_ROOT = "vivid:root"
        private const val RECENT_ROOT = "vivid:recent"
        private const val FADE_MS = 8_000L
        private const val MAX_SKIPS = 6
        private val LYRICS_KEYS = setOf("LYRICS", "UNSYNCEDLYRICS", "SYNCEDLYRICS", "USLT")
        const val ACTION_FAVORITE = "vivid.favorite"
        val FAVORITE_COMMAND = SessionCommand(ACTION_FAVORITE, Bundle.EMPTY)
    }
}
