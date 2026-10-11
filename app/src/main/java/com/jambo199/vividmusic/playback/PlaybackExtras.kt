package com.jambo199.vividmusic.playback

import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import android.os.SystemClock
import android.util.Log
import com.jambo199.vividmusic.data.EqSettings
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/** One-off messages from the player for the UI (e.g. "couldn't play this file"). */
object PlayerEvents {
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()
    fun post(message: String) {
        _messages.tryEmit(message)
    }
}

/** Sleep timer shared by the UI (which sets it) and the playback service (which enforces it). */
object SleepTimer {
    sealed interface Mode {
        data object Off : Mode
        data class At(val endElapsedMs: Long, val totalMs: Long) : Mode
        data object EndOfSong : Mode
    }

    private val _mode = MutableStateFlow<Mode>(Mode.Off)
    val mode: StateFlow<Mode> = _mode.asStateFlow()

    fun startMinutes(minutes: Int) {
        val ms = minutes.coerceAtLeast(1) * 60_000L
        _mode.value = Mode.At(SystemClock.elapsedRealtime() + ms, ms)
    }

    fun startEndOfSong() {
        _mode.value = Mode.EndOfSong
    }

    fun extend(minutes: Int) {
        val m = _mode.value
        if (m is Mode.At) _mode.value = m.copy(endElapsedMs = m.endElapsedMs + minutes * 60_000L, totalMs = m.totalMs + minutes * 60_000L)
        else startMinutes(minutes)
    }

    fun cancel() {
        _mode.value = Mode.Off
    }

    fun remainingMs(): Long = when (val m = _mode.value) {
        is Mode.At -> (m.endElapsedMs - SystemClock.elapsedRealtime()).coerceAtLeast(0)
        else -> 0
    }
}

/** Equalizer, bass boost, virtualizer and loudness attached to the player's audio session. */
object AudioEffects {
    data class Info(
        val bandCount: Int,
        val minLevel: Int,
        val maxLevel: Int,
        val centerFreqsHz: List<Int>,
        val presets: List<String>,
        val hasBassBoost: Boolean,
        val hasVirtualizer: Boolean,
        val hasLoudness: Boolean,
    )

    private const val TAG = "AudioEffects"
    private val _info = MutableStateFlow<Info?>(null)
    val info: StateFlow<Info?> = _info.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var loudness: LoudnessEnhancer? = null
    var sessionId: Int = 0
        private set

    @Synchronized
    fun attach(context: Context, audioSessionId: Int, settings: EqSettings) {
        if (audioSessionId == sessionId && equalizer != null) {
            apply(settings)
            return
        }
        release(context)
        if (audioSessionId <= 0) return
        sessionId = audioSessionId
        equalizer = try {
            Equalizer(0, audioSessionId)
        } catch (t: Throwable) {
            Log.w(TAG, "Equalizer unavailable", t)
            null
        }
        bassBoost = try {
            BassBoost(0, audioSessionId)
        } catch (t: Throwable) {
            null
        }
        virtualizer = try {
            Virtualizer(0, audioSessionId)
        } catch (t: Throwable) {
            null
        }
        loudness = try {
            LoudnessEnhancer(audioSessionId)
        } catch (t: Throwable) {
            null
        }
        val eq = equalizer
        _info.value = if (eq != null) {
            try {
                val range = eq.bandLevelRange
                Info(
                    bandCount = eq.numberOfBands.toInt(),
                    minLevel = range[0].toInt(),
                    maxLevel = range[1].toInt(),
                    centerFreqsHz = List(eq.numberOfBands.toInt()) { eq.getCenterFreq(it.toShort()) / 1000 },
                    presets = List(eq.numberOfPresets.toInt()) { eq.getPresetName(it.toShort()) },
                    hasBassBoost = bassBoost?.strengthSupported == true,
                    hasVirtualizer = virtualizer?.strengthSupported == true,
                    hasLoudness = loudness != null,
                )
            } catch (t: Throwable) {
                null
            }
        } else {
            null
        }
        _error.value = if (_info.value == null) "This phone doesn't let apps use its equalizer." else null
        apply(settings)
        try {
            context.sendBroadcast(
                Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
                    .putExtra(AudioEffect.EXTRA_AUDIO_SESSION, audioSessionId)
                    .putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                    .putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC),
            )
        } catch (_: Exception) {
        }
    }

    @Synchronized
    fun apply(s: EqSettings) {
        equalizer?.let { eq ->
            try {
                eq.enabled = s.enabled
                if (s.enabled) {
                    if (s.preset >= 0 && s.preset < eq.numberOfPresets) {
                        eq.usePreset(s.preset.toShort())
                    } else {
                        for (band in 0 until eq.numberOfBands) {
                            val level = s.bandLevels.getOrNull(band) ?: 0
                            eq.setBandLevel(band.toShort(), level.toShort())
                        }
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Equalizer apply failed", t)
            }
        }
        bassBoost?.let {
            try {
                it.enabled = s.enabled && s.bassBoost > 0
                if (it.strengthSupported) it.setStrength(s.bassBoost.coerceIn(0, 1000).toShort())
            } catch (_: Throwable) {
            }
        }
        virtualizer?.let {
            try {
                it.enabled = s.enabled && s.virtualizer > 0
                if (it.strengthSupported) it.setStrength(s.virtualizer.coerceIn(0, 1000).toShort())
            } catch (_: Throwable) {
            }
        }
        loudness?.let {
            try {
                it.setTargetGain(s.loudnessGain.coerceIn(0, 1500))
                it.enabled = s.enabled && s.loudnessGain > 0
            } catch (_: Throwable) {
            }
        }
    }

    /** Band levels a device preset uses, so the sliders can show them. */
    @Synchronized
    fun presetLevels(preset: Int): List<Int>? {
        val eq = equalizer ?: return null
        return try {
            eq.usePreset(preset.toShort())
            List(eq.numberOfBands.toInt()) { eq.getBandLevel(it.toShort()).toInt() }
        } catch (t: Throwable) {
            null
        }
    }

    @Synchronized
    fun release(context: Context) {
        if (sessionId > 0) {
            try {
                context.sendBroadcast(
                    Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
                        .putExtra(AudioEffect.EXTRA_AUDIO_SESSION, sessionId)
                        .putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName),
                )
            } catch (_: Exception) {
            }
        }
        listOf(equalizer, bassBoost, virtualizer, loudness).forEach {
            try {
                it?.release()
            } catch (_: Throwable) {
            }
        }
        equalizer = null
        bassBoost = null
        virtualizer = null
        loudness = null
        sessionId = 0
    }
}
