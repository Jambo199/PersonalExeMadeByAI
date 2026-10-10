package com.jambo199.vividmusic.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class UserData(
    val playlists: List<Playlist> = emptyList(),
    val favorites: List<String> = emptyList(),
    val recent: List<String> = emptyList(),
    val playCounts: Map<String, Int> = emptyMap(),
    val nextPlaylistId: Long = 1,
) {
    val favoriteSet: Set<String> by lazy { favorites.toSet() }
}

/** Playlists, favourites and listening history stored as one small JSON file. */
class UserDataStore(context: Context, private val scope: CoroutineScope) {
    private val file = File(context.filesDir, "user_data.json")
    private val _state = MutableStateFlow(load())
    val state: StateFlow<UserData> = _state.asStateFlow()
    val current: UserData get() = _state.value
    private var saveJob: Job? = null

    @Synchronized
    private fun mutate(transform: (UserData) -> UserData) {
        val new = transform(_state.value)
        if (new == _state.value) return
        _state.value = new
        saveJob?.cancel()
        saveJob = scope.launch(Dispatchers.IO) {
            delay(400)
            save(new)
        }
    }

    fun isFavorite(key: String) = key in current.favoriteSet

    fun toggleFavorite(key: String) = mutate { d ->
        if (key in d.favoriteSet) d.copy(favorites = d.favorites - key) else d.copy(favorites = listOf(key) + d.favorites)
    }

    fun setFavorite(keys: List<String>, favorite: Boolean) = mutate { d ->
        if (favorite) d.copy(favorites = keys.filter { it !in d.favoriteSet } + d.favorites)
        else d.copy(favorites = d.favorites.filter { it !in keys.toSet() })
    }

    fun createPlaylist(name: String, songs: List<String> = emptyList()): Long {
        var id = 0L
        mutate { d ->
            id = d.nextPlaylistId
            d.copy(
                playlists = d.playlists + Playlist(id, name.trim().ifBlank { "Playlist $id" }, songs, System.currentTimeMillis()),
                nextPlaylistId = id + 1,
            )
        }
        return id
    }

    fun renamePlaylist(id: Long, name: String) = mutate { d ->
        d.copy(playlists = d.playlists.map { if (it.id == id) it.copy(name = name.trim().ifBlank { it.name }) else it })
    }

    fun deletePlaylist(id: Long) = mutate { d -> d.copy(playlists = d.playlists.filterNot { it.id == id }) }

    fun addToPlaylist(id: Long, keys: List<String>) = mutate { d ->
        d.copy(playlists = d.playlists.map { if (it.id == id) it.copy(songKeys = it.songKeys + keys) else it })
    }

    fun removeFromPlaylist(id: Long, index: Int) = mutate { d ->
        d.copy(playlists = d.playlists.map { p ->
            if (p.id == id && index in p.songKeys.indices) p.copy(songKeys = p.songKeys.filterIndexed { i, _ -> i != index }) else p
        })
    }

    fun movePlaylistItem(id: Long, from: Int, to: Int) = mutate { d ->
        d.copy(playlists = d.playlists.map { p ->
            if (p.id != id || from !in p.songKeys.indices || to !in p.songKeys.indices) p
            else p.copy(songKeys = p.songKeys.toMutableList().apply { add(to, removeAt(from)) })
        })
    }

    /** Called by the player once a song has been listened to for a while. */
    fun recordPlay(key: String) = mutate { d ->
        d.copy(
            recent = (listOf(key) + d.recent.filter { it != key }).take(MAX_RECENT),
            playCounts = d.playCounts + (key to (d.playCounts[key] ?: 0) + 1),
        )
    }

    fun clearHistory() = mutate { it.copy(recent = emptyList(), playCounts = emptyMap()) }

    /** Drops references to deleted files. */
    fun forget(keys: Set<String>) = mutate { d ->
        d.copy(
            favorites = d.favorites.filter { it !in keys },
            recent = d.recent.filter { it !in keys },
            playCounts = d.playCounts.filterKeys { it !in keys },
            playlists = d.playlists.map { p -> p.copy(songKeys = p.songKeys.filter { it !in keys }) },
        )
    }

    fun flush() {
        saveJob?.cancel()
        save(_state.value)
    }

    private fun load(): UserData {
        if (!file.exists()) return UserData()
        return try {
            val root = JSONObject(file.readText())
            val playlists = root.optJSONArray("playlists")?.let { arr ->
                List(arr.length()) { i ->
                    val o = arr.getJSONObject(i)
                    Playlist(
                        id = o.getLong("id"),
                        name = o.optString("name"),
                        songKeys = o.optJSONArray("songs").toStringList(),
                        createdAt = o.optLong("created"),
                    )
                }
            }.orEmpty()
            val counts = HashMap<String, Int>()
            root.optJSONObject("playCounts")?.let { o -> o.keys().forEach { k -> counts[k] = o.optInt(k) } }
            UserData(
                playlists = playlists,
                favorites = root.optJSONArray("favorites").toStringList(),
                recent = root.optJSONArray("recent").toStringList(),
                playCounts = counts,
                nextPlaylistId = root.optLong("nextId", (playlists.maxOfOrNull { it.id } ?: 0) + 1),
            )
        } catch (e: Exception) {
            Log.e(TAG, "Could not read user data", e)
            UserData()
        }
    }

    @Synchronized
    private fun save(data: UserData) {
        try {
            val root = JSONObject()
            root.put("playlists", JSONArray().apply {
                data.playlists.forEach { p ->
                    put(JSONObject().put("id", p.id).put("name", p.name).put("created", p.createdAt).put("songs", JSONArray(p.songKeys)))
                }
            })
            root.put("favorites", JSONArray(data.favorites))
            root.put("recent", JSONArray(data.recent))
            root.put("playCounts", JSONObject().apply { data.playCounts.forEach { (k, v) -> put(k, v) } })
            root.put("nextId", data.nextPlaylistId)
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(root.toString())
            if (!tmp.renameTo(file)) {
                file.writeText(root.toString())
                tmp.delete()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Could not save user data", e)
        }
    }

    companion object {
        private const val TAG = "UserDataStore"
        private const val MAX_RECENT = 200
    }
}

internal fun JSONArray?.toStringList(): List<String> =
    if (this == null) emptyList() else List(length()) { optString(it) }
