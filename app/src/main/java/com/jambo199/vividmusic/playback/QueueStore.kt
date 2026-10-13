package com.jambo199.vividmusic.playback

import android.content.Context
import android.util.Log
import com.jambo199.vividmusic.data.Song
import com.jambo199.vividmusic.data.writeTextAtomically
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Remembers the play queue and position so playback resumes after the app or phone restarts. */
class QueueStore(context: Context) {
    private val queueFile = File(context.filesDir, "queue.json")
    private val stateFile = File(context.filesDir, "queue_state.json")
    private val prefs = context.getSharedPreferences("queue", Context.MODE_PRIVATE)

    data class Saved(
        val songs: List<Song>,
        val index: Int,
        val positionMs: Long,
        val shuffle: Boolean,
        val repeatMode: Int,
    )

    private val _source = MutableStateFlow(prefs.getString("source", "") ?: "")

    /** Where the current queue came from, e.g. "Album · Abbey Road". */
    val source: StateFlow<String> = _source.asStateFlow()

    fun setSource(label: String) {
        _source.value = label
        prefs.edit().putString("source", label).apply()
    }

    @Synchronized
    fun saveQueue(songs: List<Song>) {
        val arr = JSONArray()
        songs.forEach { arr.put(SongItems.toJson(it)) }
        queueFile.writeTextAtomically(arr.toString())
    }

    @Synchronized
    fun saveState(index: Int, positionMs: Long, shuffle: Boolean, repeatMode: Int) {
        val o = JSONObject()
            .put("index", index)
            .put("position", positionMs)
            .put("shuffle", shuffle)
            .put("repeat", repeatMode)
        stateFile.writeTextAtomically(o.toString())
    }

    /** Whether there's a queue to pick up again, without reading it. */
    fun hasSaved(): Boolean = queueFile.length() > 2

    @Synchronized
    fun load(): Saved? = try {
        if (!queueFile.exists()) {
            null
        } else {
            val arr = JSONArray(queueFile.readText())
            val songs = List(arr.length()) { SongItems.fromJson(arr.getJSONObject(it)) }
            val state = if (stateFile.exists()) JSONObject(stateFile.readText()) else JSONObject()
            if (songs.isEmpty()) {
                null
            } else {
                Saved(
                    songs = songs,
                    index = state.optInt("index", 0).coerceIn(0, songs.lastIndex),
                    positionMs = state.optLong("position", 0).coerceAtLeast(0),
                    shuffle = state.optBoolean("shuffle", false),
                    repeatMode = state.optInt("repeat", 0),
                )
            }
        }
    } catch (e: Exception) {
        Log.w("QueueStore", "Could not restore queue", e)
        null
    }
}
