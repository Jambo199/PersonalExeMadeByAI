package com.jambo199.vividmusic.playback

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaButtonReceiver
import com.jambo199.vividmusic.AppGraph

/**
 * Earbud and headset buttons. Android sends them to whichever app last played sound and still has
 * a media session, so after listening to another app (Spotify, say) a double tap would go there
 * even with Vivid Music open.
 */
object HeadsetButtons {
    const val TAG = "HeadsetButtons"
    private const val RATE = 48_000
    private const val SILENCE_MS = 300

    /**
     * Plays a moment of silence, so that Vivid Music is the app that played sound last and the
     * buttons come here. Nothing is heard, and nothing happens while another app is playing.
     * Blocks for about half a second: call it off the main thread.
     */
    fun claim(context: Context): Boolean {
        val audio = context.getSystemService(AudioManager::class.java) ?: return false
        if (audio.isMusicActive) return false
        val frames = RATE * SILENCE_MS / 1000
        val track = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(frames * 2)
                .build()
        } catch (e: Exception) {
            Log.w(TAG, "No audio track for claiming the buttons", e)
            return false
        }
        return try {
            track.write(ShortArray(frames), 0, frames)
            track.play()
            Thread.sleep(SILENCE_MS + 100L)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't claim the buttons", e)
            false
        } finally {
            runCatching { track.stop() }
            track.release()
        }
    }
}

/**
 * Gets earbud and headset buttons once the player has closed (Android hands them to the last app
 * that played). "Play" starts the player again where you left off; with nothing saved to pick up
 * it does nothing, rather than starting a player with nothing to play (which Android would close
 * as a crash).
 */
@OptIn(UnstableApi::class)
class VividMediaButtonReceiver : MediaButtonReceiver() {
    override fun shouldStartForegroundService(context: Context, intent: Intent): Boolean {
        val resume = AppGraph.queueStore.hasSaved()
        Log.i(HeadsetButtons.TAG, if (resume) "Button while closed: picking up where you left off" else "Button while closed: nothing to resume")
        return resume
    }
}
