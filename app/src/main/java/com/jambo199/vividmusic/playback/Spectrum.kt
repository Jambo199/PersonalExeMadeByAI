package com.jambo199.vividmusic.playback

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The music's loudness in a few frequency bands, for the player's visualizer. It listens to the
 * audio as the player hands it to the speaker (no microphone, so no permission needed), and keeps
 * each moment until it is actually heard, since audio is prepared a little ahead.
 */
@OptIn(UnstableApi::class)
object Spectrum {
    const val BANDS = 28

    /** Samples per analysis (about 46 ms), taken every [HOP] samples so frames overlap. */
    private const val SIZE = 2048
    private const val HOP = 1024
    private const val MAX_QUEUED = 160

    /** One moment of music: band levels 0..1 and how strong the beat is, 0..1. */
    class Frame(val atSec: Double, val levels: FloatArray, val beat: Float)

    /** How many visualizers are on screen; the audio is only analysed while some are. */
    @Volatile
    var watchers = 0

    // region audio thread

    private var sampleRate = 44_100
    private var channels = 2
    private var pcm16 = true
    private val ring = FloatArray(SIZE)
    private val ordered = FloatArray(SIZE)
    private var ringPos = 0
    private var sinceLast = 0
    private var processedFrames = 0L
    private var bassAverage = 0f
    private val queue = ArrayDeque<Frame>()

    val sink = object : TeeAudioProcessor.AudioBufferSink {
        override fun flush(sampleRateHz: Int, channelCount: Int, encoding: Int) {
            sampleRate = sampleRateHz.coerceAtLeast(1)
            channels = channelCount.coerceAtLeast(1)
            pcm16 = encoding == C.ENCODING_PCM_16BIT
            ring.fill(0f)
            sinceLast = 0
            processedFrames = 0
            synchronized(queue) { queue.clear() }
            clockNeedsStart = true
        }

        override fun handleBuffer(buffer: ByteBuffer) {
            if (clockNeedsStart && playing) startClock(System.nanoTime())
            val bytesPerFrame = 2 * channels
            val frames = buffer.remaining() / bytesPerFrame
            if (watchers <= 0 || !pcm16) {
                processedFrames += frames
                return
            }
            val data = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
            for (f in 0 until frames) {
                var sum = 0f
                for (c in 0 until channels) sum += data.short.toFloat()
                ring[ringPos] = sum / channels / 32768f
                ringPos = (ringPos + 1) % SIZE
                processedFrames++
                if (++sinceLast == HOP) {
                    sinceLast = 0
                    for (i in 0 until SIZE) ordered[i] = ring[(ringPos + i) % SIZE]
                    val levels = analyse(ordered, sampleRate)
                    val bass = (levels[0] + levels[1] + levels[2]) / 3f
                    bassAverage += (bass - bassAverage) * 0.05f
                    val beat = ((bass - bassAverage) * 4f).coerceIn(0f, 1f)
                    synchronized(queue) {
                        queue.addLast(Frame(processedFrames.toDouble() / sampleRate, levels, beat))
                        while (queue.size > MAX_QUEUED) queue.removeFirst()
                    }
                }
            }
        }
    }

    // endregion

    // region clock: which processed moment is being heard now

    @Volatile
    private var playing = false

    @Volatile
    private var clockNeedsStart = true

    @Volatile
    private var startNs = 0L
    private var pausedAtNs = 0L

    private fun startClock(now: Long) {
        startNs = now
        clockNeedsStart = false
    }

    /** Called by the player service when playback starts or stops. */
    fun onPlaying(isPlaying: Boolean, now: Long = System.nanoTime()) {
        if (isPlaying == playing) return
        playing = isPlaying
        if (isPlaying) {
            // Prepared audio waits in the speaker's buffer while paused, then carries on.
            if (clockNeedsStart) startClock(now) else startNs += now - pausedAtNs
        } else {
            pausedAtNs = now
        }
    }

    /** The moment being heard at [now], or null when nothing is playing. */
    fun frameAt(now: Long, speed: Float): Frame? {
        if (!playing || clockNeedsStart) return null
        val heard = (now - startNs) / 1e9 * speed
        synchronized(queue) {
            var latest: Frame? = null
            while (queue.isNotEmpty() && queue.first().atSec <= heard) latest = queue.removeFirst()
            return latest
        }
    }

    // endregion

    // region analysis

    private val window = FloatArray(SIZE) { (0.5 - 0.5 * cos(2 * PI * it / (SIZE - 1))).toFloat() }

    /**
     * Band levels 0..1 for [samples] (mono, -1..1, a power of two long): log-spaced bands from
     * 50 Hz up to 16 kHz, each the loudest frequency in it, with highs lifted a little since
     * music is naturally quieter up there.
     */
    fun analyse(samples: FloatArray, sampleRate: Int): FloatArray {
        val n = samples.size
        val re = FloatArray(n) { samples[it] * (if (n == SIZE) window[it] else 1f) }
        val im = FloatArray(n)
        fft(re, im)
        val out = FloatArray(BANDS)
        val nyquist = sampleRate / 2.0
        val low = 50.0
        val high = minOf(16_000.0, nyquist)
        for (b in 0 until BANDS) {
            val from = low * (high / low).pow(b.toDouble() / BANDS)
            val to = low * (high / low).pow((b + 1.0) / BANDS)
            val first = (from / nyquist * (n / 2)).toInt().coerceIn(1, n / 2 - 1)
            val last = (to / nyquist * (n / 2)).toInt().coerceIn(first, n / 2 - 1)
            var peak = 0f
            for (k in first..last) peak = max(peak, sqrt(re[k] * re[k] + im[k] * im[k]))
            val db = 20 * log10((peak * 4 / n).coerceAtLeast(1e-6f)) + b * 0.45f
            out[b] = ((db + 60f) / 54f).coerceIn(0f, 1f)
        }
        return out
    }

    /** In-place radix-2 FFT. */
    private fun fft(re: FloatArray, im: FloatArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                var t = re[i]; re[i] = re[j]; re[j] = t
                t = im[i]; im[i] = im[j]; im[j] = t
            }
        }
        var len = 2
        while (len <= n) {
            val angle = -2 * PI / len
            val wr = cos(angle).toFloat()
            val wi = sin(angle).toFloat()
            var i = 0
            while (i < n) {
                var cr = 1f
                var ci = 0f
                for (k in 0 until len / 2) {
                    val ar = re[i + k + len / 2] * cr - im[i + k + len / 2] * ci
                    val ai = re[i + k + len / 2] * ci + im[i + k + len / 2] * cr
                    re[i + k + len / 2] = re[i + k] - ar
                    im[i + k + len / 2] = im[i + k] - ai
                    re[i + k] += ar
                    im[i + k] += ai
                    val nr = cr * wr - ci * wi
                    ci = cr * wi + ci * wr
                    cr = nr
                }
                i += len
            }
            len = len shl 1
        }
    }

    // endregion
}
