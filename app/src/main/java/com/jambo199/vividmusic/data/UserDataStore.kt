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
import java.util.Calendar

data class UserData(
    val playlists: List<Playlist> = emptyList(),
    val favorites: List<String> = emptyList(),
    val recent: List<String> = emptyList(),
    val playCounts: Map<String, Int> = emptyMap(),
    val nextPlaylistId: Long = 1,
    /** Total time spent listening, in milliseconds. */
    val listenedMs: Long = 0,
    /** Listening time per day ("2026-10-11" to ms), last [UserDataStore.DAYS_KEPT] days. */
    val dailyMs: Map<String, Long> = emptyMap(),
    /** When listening stats started being kept. */
    val statsSince: Long = 0,
    /** Days in a row with at least a minute of music, ending on [streakDay]. */
    val streak: Int = 0,
    /** The last day ("2026-10-11") counted in [streak]. */
    val streakDay: String = "",
    val bestStreak: Int = 0,
) {
    val favoriteSet: Set<String> by lazy { favorites.toSet() }

    /** The streak still going: it lasts until a whole day goes by without music. */
    fun currentStreak(today: String, yesterday: String): Int =
        if (streakDay == today || streakDay == yesterday) streak else 0
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

    /** Called by the player with time spent playing since the last call. */
    fun addListeningTime(ms: Long, now: Long = System.currentTimeMillis()) {
        if (ms <= 0) return
        val today = dayKey(now)
        val yesterday = dayKey(now - DAY_MS)
        val oldest = dayKey(now - (DAYS_KEPT - 1) * DAY_MS)
        mutate { d ->
            val next = d.copy(
                listenedMs = d.listenedMs + ms,
                dailyMs = (d.dailyMs + (today to (d.dailyMs[today] ?: 0L) + ms)).filterKeys { it >= oldest },
                statsSince = d.statsSince.takeIf { it > 0 } ?: now,
            )
            if (next.streakDay == today || (next.dailyMs[today] ?: 0L) < STREAK_MS) {
                next
            } else {
                // Streaks started before they were tracked are picked up from the daily totals.
                val base = if (next.streakDay.isEmpty()) next.copy(streak = pastStreak(next.dailyMs, now), streakDay = yesterday) else next
                countStreakDay(base, today, yesterday)
            }
        }
    }

    fun clearHistory() = mutate {
        it.copy(recent = emptyList(), playCounts = emptyMap(), listenedMs = 0, dailyMs = emptyMap(), statsSince = 0)
    }

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
            parse(JSONObject(file.readText()))
        } catch (e: Exception) {
            Log.e(TAG, "Could not read user data", e)
            UserData()
        }
    }

    @Synchronized
    private fun save(data: UserData) {
        try {
            file.writeTextAtomically(toJson(data).toString())
        } catch (e: Exception) {
            Log.e(TAG, "Could not save user data", e)
        }
    }

    /** Everything, for a backup. */
    fun export(): JSONObject = toJson(current)

    /** Replaces everything with a backup, after its song keys were matched to this phone's. */
    fun restore(data: UserData) {
        saveJob?.cancel()
        synchronized(this) { _state.value = data }
        save(data)
    }

    companion object {
        private const val TAG = "UserDataStore"
        private const val MAX_RECENT = 200
        const val DAYS_KEPT = 30
        private const val DAY_MS = 24L * 60 * 60 * 1000

        fun parse(root: JSONObject): UserData {
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
            return UserData(
                playlists = playlists,
                favorites = root.optJSONArray("favorites").toStringList(),
                recent = root.optJSONArray("recent").toStringList(),
                playCounts = counts,
                nextPlaylistId = root.optLong("nextId", (playlists.maxOfOrNull { it.id } ?: 0) + 1),
                listenedMs = root.optLong("listenedMs"),
                dailyMs = root.optJSONObject("dailyMs")?.let { o -> o.keys().asSequence().associateWith { o.optLong(it) } }.orEmpty(),
                statsSince = root.optLong("statsSince"),
                streak = root.optInt("streak"),
                streakDay = root.optString("streakDay"),
                bestStreak = root.optInt("bestStreak"),
            )
        }

        fun toJson(data: UserData): JSONObject {
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
            root.put("listenedMs", data.listenedMs)
            root.put("dailyMs", JSONObject().apply { data.dailyMs.forEach { (k, v) -> put(k, v) } })
            root.put("statsSince", data.statsSince)
            root.put("streak", data.streak)
            root.put("streakDay", data.streakDay)
            root.put("bestStreak", data.bestStreak)
            return root
        }

        /** A day with this much music counts towards the streak. */
        const val STREAK_MS = 60_000L

        /** [data] once [day] has had its first full minute of music. */
        fun countStreakDay(data: UserData, day: String, previousDay: String): UserData {
            if (data.streakDay == day) return data
            val streak = if (data.streakDay == previousDay) data.streak + 1 else 1
            return data.copy(streak = streak, streakDay = day, bestStreak = maxOf(data.bestStreak, streak))
        }

        /** Days in a row before the day of [now] with a minute or more of music. */
        fun pastStreak(dailyMs: Map<String, Long>, now: Long): Int {
            var days = 0
            while ((dailyMs[dayKey(now - (days + 1) * DAY_MS)] ?: 0L) >= STREAK_MS) days++
            return days
        }

        /** Every song key the data mentions. */
        fun keysIn(data: UserData): Set<String> = buildSet {
            addAll(data.favorites)
            addAll(data.recent)
            addAll(data.playCounts.keys)
            data.playlists.forEach { addAll(it.songKeys) }
        }

        /** The same data with song keys swapped through [map] (keys not in it stay). */
        fun remap(data: UserData, map: Map<String, String>): UserData {
            if (map.isEmpty()) return data
            fun k(key: String) = map[key] ?: key
            val counts = HashMap<String, Int>()
            data.playCounts.forEach { (key, n) -> counts.merge(k(key), n, Int::plus) }
            return data.copy(
                favorites = data.favorites.map(::k).distinct(),
                recent = data.recent.map(::k).distinct(),
                playCounts = counts,
                playlists = data.playlists.map { p -> p.copy(songKeys = p.songKeys.map(::k)) },
            )
        }

        /** Local calendar day, sortable as text. */
        fun dayKey(time: Long): String {
            val c = Calendar.getInstance().apply { timeInMillis = time }
            return "%04d-%02d-%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
        }
    }
}

internal fun JSONArray?.toStringList(): List<String> =
    if (this == null) emptyList() else List(length()) { optString(it) }
