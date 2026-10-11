package com.jambo199.vividmusic.ui.quiz

import android.app.Application
import android.content.Context
import android.os.SystemClock
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
import com.jambo199.vividmusic.online.ChartProgress
import com.jambo199.vividmusic.online.Http
import com.jambo199.vividmusic.online.Ncs
import com.jambo199.vividmusic.online.friendlyMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.random.Random

/** Where the quiz takes its songs from. */
enum class QuizSource(val label: String) { LIBRARY("My songs"), NCS("NCS hits") }

@Immutable
data class QuizQuestion(val answer: Song, val choices: List<Song>, val startMs: Long)

sealed interface QuizState {
    data object Setup : QuizState
    data object Loading : QuizState
    data class Unavailable(val message: String) : QuizState

    @Immutable
    data class Round(
        val number: Int,
        val total: Int,
        val question: QuizQuestion,
        /** When the clip started (elapsed realtime), for the countdown. */
        val startedAt: Long,
        val score: Int,
        val streak: Int,
        val correct: Int,
        /** The picked song's key once answered ("" when time ran out). */
        val chosen: String? = null,
        val gained: Int = 0,
    ) : QuizState {
        val answered: Boolean get() = chosen != null
    }

    @Immutable
    data class Finished(val score: Int, val correct: Int, val total: Int, val best: Int, val newBest: Boolean) : QuizState
}

/**
 * "Guess the song": plays a few seconds from the middle of a song and asks which one it is.
 * Uses its own little player, so the queue is left alone (it pauses while the quiz plays).
 */
@OptIn(UnstableApi::class)
class QuizViewModel(app: Application) : AndroidViewModel(app) {
    private val _state = MutableStateFlow<QuizState>(QuizState.Setup)
    val state: StateFlow<QuizState> = _state.asStateFlow()

    private val _source = MutableStateFlow(QuizSource.LIBRARY)
    val source: StateFlow<QuizSource> = _source.asStateFlow()

    private val prefs = app.getSharedPreferences("quiz", Context.MODE_PRIVATE)
    private val _best = MutableStateFlow(QuizSource.entries.associateWith { prefs.getInt("best.${it.name}", 0) })
    val best: StateFlow<Map<QuizSource, Int>> = _best.asStateFlow()

    private var player: ExoPlayer? = null
    private var pool: List<Song> = emptyList()
    private var asked = HashSet<String>()
    private var timeout: Job? = null
    private val random = Random(System.nanoTime())

    fun selectSource(source: QuizSource) {
        _source.value = source
    }

    fun start() {
        _state.value = QuizState.Loading
        viewModelScope.launch {
            val songs = try {
                withContext(Dispatchers.IO) { songsFor(_source.value) }
            } catch (e: Exception) {
                _state.value = QuizState.Unavailable("Couldn't get NCS songs: ${e.friendlyMessage()}")
                return@launch
            }
            // Different titles only, or two choices could look the same.
            pool = songs.distinctBy { it.title.lowercase() }
            if (pool.size < 4) {
                _state.value = QuizState.Unavailable(
                    if (_source.value == QuizSource.LIBRARY) "The quiz needs at least 4 different songs. Add some music, or try NCS hits."
                    else "Not enough NCS songs came through. Try again in a moment.",
                )
                return@launch
            }
            asked = HashSet()
            nextRound(number = 1, score = 0, streak = 0, correct = 0)
        }
    }

    private fun songsFor(source: QuizSource): List<Song> = when (source) {
        QuizSource.LIBRARY -> AppGraph.library.library.songs.filter { it.durationMs >= MIN_SONG_MS || it.durationMs == 0L }
        QuizSource.NCS -> {
            // The most played chart when it's been fetched, else the newest releases.
            val chart = ChartProgress.load(File(getApplication<Application>().cacheDir, "ncs_chart.json"), System.currentTimeMillis())
            val tracks = chart?.tracks ?: (1..3).flatMap { Ncs.search(page = it).tracks }
            tracks.map { it.toSong() }
        }
    }

    private fun nextRound(number: Int, score: Int, streak: Int, correct: Int) {
        val total = minOf(ROUNDS, pool.size)
        if (number > total) {
            finish(score, correct, total)
            return
        }
        val answer = pool.filter { it.key !in asked }.randomOrNull(random) ?: pool.random(random)
        asked += answer.key
        val others = pool.filter { it.key != answer.key }.shuffled(random).take(3)
        val choices = (others + answer).shuffled(random)
        // Somewhere in the middle, where songs are most recognisable.
        val length = answer.durationMs.takeIf { it > 0 } ?: NCS_GUESS_MS
        val startMs = if (length > CLIP_MS * 3) (length * (0.25 + random.nextDouble() * 0.35)).toLong() else 0L
        val question = QuizQuestion(answer, choices, startMs)
        play(question)
        _state.value = QuizState.Round(number, total, question, SystemClock.elapsedRealtime(), score, streak, correct)
        timeout?.cancel()
        timeout = viewModelScope.launch {
            delay(ROUND_MS)
            answer(null)
        }
    }

    /** Picks [song] as the answer (null when time ran out). */
    fun answer(song: Song?) {
        val round = _state.value as? QuizState.Round ?: return
        if (round.answered) return
        timeout?.cancel()
        val right = song?.key == round.question.answer.key
        val elapsed = SystemClock.elapsedRealtime() - round.startedAt
        val gained = if (right) 100 + ((ROUND_MS - elapsed).coerceAtLeast(0) / 100).toInt() + round.streak * 25 else 0
        _state.value = round.copy(
            chosen = song?.key ?: "",
            gained = gained,
            score = round.score + gained,
            streak = if (right) round.streak + 1 else 0,
            correct = round.correct + if (right) 1 else 0,
        )
        // Let the song carry on a moment so you hear what it was.
        timeout = viewModelScope.launch {
            delay(REVEAL_MS)
            player?.pause()
        }
    }

    fun next() {
        val round = _state.value as? QuizState.Round ?: return
        if (!round.answered) return
        nextRound(round.number + 1, round.score, round.streak, round.correct)
    }

    fun backToSetup() {
        stop()
        _state.value = QuizState.Setup
    }

    private fun finish(score: Int, correct: Int, total: Int) {
        stop()
        val source = _source.value
        val previous = _best.value[source] ?: 0
        val newBest = score > previous
        if (newBest) {
            prefs.edit().putInt("best.${source.name}", score).apply()
            _best.value = _best.value + (source to score)
        }
        _state.value = QuizState.Finished(score, correct, total, maxOf(score, previous), newBest)
    }

    private fun play(question: QuizQuestion) {
        val p = player ?: createPlayer().also { player = it }
        p.setMediaItem(MediaItem.fromUri(question.answer.uri), question.startMs)
        p.prepare()
        p.play()
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

    fun stop() {
        timeout?.cancel()
        player?.release()
        player = null
    }

    override fun onCleared() {
        stop()
    }

    companion object {
        const val ROUNDS = 10
        const val ROUND_MS = 15_000L
        private const val CLIP_MS = 10_000L
        private const val REVEAL_MS = 4_000L
        private const val MIN_SONG_MS = 20_000L

        /** NCS releases don't say how long they are; most run about three minutes. */
        private const val NCS_GUESS_MS = 180_000L
    }
}
