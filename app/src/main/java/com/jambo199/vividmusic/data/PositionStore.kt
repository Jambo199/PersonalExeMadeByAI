package com.jambo199.vividmusic.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File

/**
 * Where you stopped in audiobooks, mixes and other long tracks, so they carry on from there.
 * Keeps the most recent [MAX] entries.
 */
class PositionStore(context: Context, private val scope: CoroutineScope) {
    private val file = File(context.filesDir, "positions.json")
    private val _state = MutableStateFlow(load())

    /** Song key to position in ms, most recently saved first. */
    val state: StateFlow<Map<String, Long>> = _state.asStateFlow()
    private var saveJob: Job? = null

    fun get(key: String): Long = _state.value[key] ?: 0L

    @Synchronized
    fun set(key: String, positionMs: Long) {
        val old = _state.value
        if (old[key] == positionMs) return
        // LinkedHashMap keeps insertion order; re-inserting moves the entry to the end.
        val new = LinkedHashMap<String, Long>(old.size + 1)
        old.forEach { (k, v) -> if (k != key) new[k] = v }
        new[key] = positionMs
        while (new.size > MAX) new.remove(new.keys.first())
        _state.value = new
        saveSoon()
    }

    @Synchronized
    fun clear(key: String) {
        if (key !in _state.value) return
        _state.value = _state.value - key
        saveSoon()
    }

    private fun saveSoon() {
        saveJob?.cancel()
        saveJob = scope.launch(Dispatchers.IO) {
            delay(1000)
            save()
        }
    }

    fun flush() {
        saveJob?.cancel()
        save()
    }

    @Synchronized
    private fun save() {
        val o = JSONObject()
        _state.value.forEach { (k, v) -> o.put(k, v) }
        file.writeTextAtomically(o.toString())
    }

    private fun load(): Map<String, Long> = try {
        if (!file.exists()) emptyMap() else {
            val o = JSONObject(file.readText())
            val out = LinkedHashMap<String, Long>()
            o.keys().forEach { k -> out[k] = o.optLong(k) }
            out
        }
    } catch (e: Exception) {
        emptyMap()
    }

    companion object {
        const val MAX = 300

        /** Tracks this long or longer get their position remembered. */
        const val LONG_TRACK_MS = 15L * 60 * 1000

        /** Whether [song] should continue where it was left. */
        fun remembers(song: Song): Boolean =
            song.durationMs >= LONG_TRACK_MS
    }
}
