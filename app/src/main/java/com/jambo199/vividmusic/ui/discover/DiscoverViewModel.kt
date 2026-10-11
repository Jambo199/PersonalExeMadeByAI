package com.jambo199.vividmusic.ui.discover

import android.app.Application
import android.content.Context
import android.os.Environment
import androidx.annotation.OptIn
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.jambo199.vividmusic.AppGraph
import com.jambo199.vividmusic.data.Song
import com.jambo199.vividmusic.data.UserData
import com.jambo199.vividmusic.online.ChartProgress
import com.jambo199.vividmusic.online.Downloads
import com.jambo199.vividmusic.online.Http
import com.jambo199.vividmusic.online.Ncs
import com.jambo199.vividmusic.online.NcsTrack
import com.jambo199.vividmusic.online.friendlyMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import kotlin.random.Random

/** Where the cards come from. */
enum class DiscoverSource(val label: String) { LIBRARY("Unplayed songs"), NCS("NCS songs") }

@Immutable
data class DiscoverState(
    val source: DiscoverSource = DiscoverSource.LIBRARY,
    val loading: Boolean = true,
    val cards: List<Song> = emptyList(),
    val index: Int = 0,
    val loved: List<Song> = emptyList(),
    val passed: Int = 0,
    val sound: Boolean = true,
    val message: String? = null,
) {
    val current: Song? get() = cards.getOrNull(index)
    val upNext: Song? get() = cards.getOrNull(index + 1)
    val finished: Boolean get() = !loading && index >= cards.size
}

/** Picks the cards: songs you haven't played (or barely have), never ones you passed on lately. */
object DiscoverPile {
    const val SIZE = 20

    fun deal(songs: List<Song>, data: UserData, passed: Set<String>, random: Random): List<Song> {
        val candidates = songs.filter {
            it.key !in data.favoriteSet && it.key !in passed && (it.durationMs == 0L || it.durationMs >= 30_000)
        }
        val unplayed = candidates.filter { (data.playCounts[it.key] ?: 0) == 0 }.shuffled(random)
        val barely = candidates.filter { (data.playCounts[it.key] ?: 0) in 1..2 }.shuffled(random)
        return (unplayed + barely).take(SIZE)
    }
}

/**
 * "Swipe to discover": one song at a time with a preview from its middle. Swipe right to keep it
 * (a favourite, or for NCS songs, saved to your phone), left to pass. Uses its own little player
 * so your queue is left alone.
 */
@OptIn(UnstableApi::class)
class DiscoverViewModel(app: Application) : AndroidViewModel(app) {
    private val _state = MutableStateFlow(DiscoverState())
    val state: StateFlow<DiscoverState> = _state.asStateFlow()

    private val prefs = app.getSharedPreferences("discover", Context.MODE_PRIVATE)
    private var player: ExoPlayer? = null
    private var fade: Job? = null
    private var ncsTracks: Map<String, NcsTrack> = emptyMap()
    private var resumeOnStart = false

    init {
        deal(DiscoverSource.LIBRARY)
    }

    /** A fresh pile of cards from [source]. */
    fun deal(source: DiscoverSource = _state.value.source) {
        player?.pause()
        val sound = _state.value.sound
        _state.value = DiscoverState(source = source, loading = true, sound = sound)
        viewModelScope.launch {
            val cards = try {
                when (source) {
                    DiscoverSource.LIBRARY -> libraryCards()
                    DiscoverSource.NCS -> ncsCards()
                }
            } catch (e: Exception) {
                _state.value = DiscoverState(source = source, loading = false, sound = sound, message = "Couldn't get NCS songs: ${e.friendlyMessage()}")
                return@launch
            }
            if (_state.value.source != source) return@launch
            _state.value = DiscoverState(
                source = source,
                loading = false,
                cards = cards,
                sound = sound,
                message = if (cards.isEmpty()) emptyMessage(source) else null,
            )
            preview()
        }
    }

    private suspend fun libraryCards(): List<Song> {
        val lib = withTimeoutOrNull(15_000) { AppGraph.library.state.first { it.hasLoadedOnce }.library } ?: AppGraph.library.library
        val data = AppGraph.userData.current
        return withContext(Dispatchers.Default) { DiscoverPile.deal(lib.songs, data, passedRecently(), Random(System.nanoTime())) }
    }

    private suspend fun ncsCards(): List<Song> = withContext(Dispatchers.IO) {
        // The most played chart when it's been fetched, else the newest releases.
        val chart = ChartProgress.load(File(getApplication<Application>().cacheDir, "ncs_chart.json"), System.currentTimeMillis())
        val tracks = chart?.tracks ?: (1..3).flatMap { Ncs.search(page = it).tracks }
        val onPhone = AppGraph.library.library.songs.mapTo(HashSet()) { it.path }
        val passed = passedRecently()
        val fresh = tracks.filter { it.key !in passed && File(musicRoot, it.downloadPath).absolutePath !in onPhone }
        ncsTracks = fresh.associateBy { it.key }
        fresh.shuffled().take(DiscoverPile.SIZE).map { it.toSong() }
    }

    private fun emptyMessage(source: DiscoverSource) = when (source) {
        DiscoverSource.LIBRARY -> "You've played everything in your library. Impressive! Try NCS songs for something new."
        DiscoverSource.NCS -> "You've been through all the NCS songs we could find. Check back after the next releases."
    }

    fun keep() = decide(keep = true)

    fun pass() = decide(keep = false)

    private fun decide(keep: Boolean) {
        val s = _state.value
        val song = s.current ?: return
        var message: String? = null
        if (keep) {
            if (song.isOnline) {
                message = saveNcs(song)
            } else {
                AppGraph.userData.setFavorite(listOf(song.key), true)
            }
        } else {
            rememberPassed(song.key)
        }
        _state.value = s.copy(
            index = s.index + 1,
            loved = if (keep) s.loved + song else s.loved,
            passed = s.passed + if (keep) 0 else 1,
            message = message,
        )
        preview()
    }

    /** Starts saving an NCS song into Music › NCS. Returns a problem to show, if there was one. */
    private fun saveNcs(song: Song): String? {
        val track = ncsTracks[song.key] ?: return null
        if (AppGraph.online.current.downloads.containsKey(track.url)) return null
        return try {
            val id = Downloads.enqueue(getApplication(), track.url, track.downloadPath, "${track.title} – ${track.artists}", Environment.DIRECTORY_MUSIC)
            AppGraph.online.addDownload(track.url, id)
            null
        } catch (e: Exception) {
            "Couldn't save \"${track.title}\": ${e.friendlyMessage()}"
        }
    }

    fun toggleSound() {
        val on = !_state.value.sound
        _state.value = _state.value.copy(sound = on)
        if (on) preview() else player?.pause()
    }

    fun dismissMessage() {
        _state.value = _state.value.copy(message = null)
    }

    /** Plays a bit of the top card, fading in so it doesn't start with a jolt. */
    private fun preview() {
        val s = _state.value
        val song = s.current
        fade?.cancel()
        if (song == null || !s.sound) {
            player?.pause()
            return
        }
        val p = player ?: createPlayer().also { player = it }
        val length = song.durationMs
        val start = when {
            length > 90_000 -> (length * 0.35).toLong()
            song.isOnline -> 60_000L
            else -> 0L
        }
        p.setMediaItem(MediaItem.fromUri(song.uri), start)
        p.prepare()
        p.volume = 0f
        p.play()
        fade = viewModelScope.launch {
            for (i in 1..15) {
                delay(100)
                p.volume = i / 15f
            }
        }
    }

    fun onStop() {
        resumeOnStart = player?.isPlaying == true
        player?.pause()
    }

    fun onStart() {
        if (resumeOnStart) player?.play()
        resumeOnStart = false
    }

    private fun createPlayer(): ExoPlayer {
        val http = DefaultHttpDataSource.Factory().setUserAgent(Http.USER_AGENT).setAllowCrossProtocolRedirects(true)
        return ExoPlayer.Builder(getApplication())
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(getApplication(), http)))
            // Taking audio focus pauses whatever was playing.
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                true,
            )
            .build()
    }

    /** Songs passed on in the last [FORGET_DAYS] days don't come up again. */
    private fun passedRecently(): Set<String> {
        val cutoff = System.currentTimeMillis() - FORGET_DAYS * DAY_MS
        return prefs.all.filter { (_, v) -> (v as? Long ?: 0L) > cutoff }.keys
    }

    private fun rememberPassed(key: String) {
        val now = System.currentTimeMillis()
        val edit = prefs.edit().putLong(key, now)
        // Keep the list from growing forever.
        prefs.all.forEach { (k, v) -> if ((v as? Long ?: 0L) < now - FORGET_DAYS * DAY_MS) edit.remove(k) }
        edit.apply()
    }

    override fun onCleared() {
        fade?.cancel()
        player?.release()
        player = null
    }

    private val musicRoot: File get() = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)

    companion object {
        private const val FORGET_DAYS = 60L
        private const val DAY_MS = 24L * 60 * 60 * 1000
    }
}
