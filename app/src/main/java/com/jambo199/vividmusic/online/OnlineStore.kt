package com.jambo199.vividmusic.online

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Immutable
import com.jambo199.vividmusic.data.writeTextAtomically
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

@Immutable
data class OnlineData(
    val favoriteStations: List<RadioStation> = emptyList(),
    val recentStations: List<RadioStation> = emptyList(),
    val podcasts: List<Podcast> = emptyList(),
    /** Download ids by the address that was downloaded. */
    val downloads: Map<String, Long> = emptyMap(),
) {
    val favoriteIds: Set<String> by lazy { favoriteStations.mapTo(HashSet()) { it.id } }
    val podcastFeeds: Set<String> by lazy { podcasts.mapTo(HashSet()) { it.feedUrl } }
}

/** Favourite and recent radio stations, podcast subscriptions and downloads, in one JSON file. */
class OnlineStore(context: Context, private val scope: CoroutineScope) {
    private val file = File(context.filesDir, "online.json")
    private val _state = MutableStateFlow(load())
    val state: StateFlow<OnlineData> = _state.asStateFlow()
    val current: OnlineData get() = _state.value
    private var saveJob: Job? = null

    @Synchronized
    private fun mutate(transform: (OnlineData) -> OnlineData) {
        val new = transform(_state.value)
        if (new == _state.value) return
        _state.value = new
        saveJob?.cancel()
        saveJob = scope.launch(Dispatchers.IO) {
            delay(300)
            save(new)
        }
    }

    fun toggleFavorite(station: RadioStation) = mutate { d ->
        if (station.id in d.favoriteIds) d.copy(favoriteStations = d.favoriteStations.filterNot { it.id == station.id })
        else d.copy(favoriteStations = listOf(station) + d.favoriteStations)
    }

    fun stationPlayed(station: RadioStation) = mutate { d ->
        d.copy(recentStations = (listOf(station) + d.recentStations.filterNot { it.id == station.id }).take(12))
    }

    fun toggleSubscription(podcast: Podcast) = mutate { d ->
        if (podcast.feedUrl in d.podcastFeeds) d.copy(podcasts = d.podcasts.filterNot { it.feedUrl == podcast.feedUrl })
        else d.copy(podcasts = listOf(podcast) + d.podcasts)
    }

    fun addDownload(url: String, id: Long) = mutate { d -> d.copy(downloads = d.downloads + (url to id)) }

    fun removeDownload(url: String) = mutate { d -> d.copy(downloads = d.downloads - url) }

    private fun load(): OnlineData {
        if (!file.exists()) return OnlineData()
        return try {
            val root = JSONObject(file.readText())
            fun stations(key: String): List<RadioStation> = root.optJSONArray(key)?.let { arr ->
                List(arr.length()) { arr.getJSONObject(it) }.mapNotNull(RadioBrowser::parseStation)
            }.orEmpty()
            val downloads = HashMap<String, Long>()
            root.optJSONObject("downloads")?.let { o -> o.keys().forEach { k -> downloads[k] = o.optLong(k) } }
            OnlineData(
                favoriteStations = stations("favoriteStations"),
                recentStations = stations("recentStations"),
                podcasts = root.optJSONArray("podcasts")?.let { arr ->
                    List(arr.length()) { Podcast.fromJson(arr.getJSONObject(it)) }.filter { it.feedUrl.isNotBlank() }
                }.orEmpty(),
                downloads = downloads,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Could not read online data", e)
            OnlineData()
        }
    }

    @Synchronized
    private fun save(data: OnlineData) {
        try {
            val root = JSONObject()
                .put("favoriteStations", JSONArray().apply { data.favoriteStations.forEach { put(it.toJson()) } })
                .put("recentStations", JSONArray().apply { data.recentStations.forEach { put(it.toJson()) } })
                .put("podcasts", JSONArray().apply { data.podcasts.forEach { put(it.toJson()) } })
                .put("downloads", JSONObject().apply { data.downloads.forEach { (k, v) -> put(k, v) } })
            file.writeTextAtomically(root.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Could not save online data", e)
        }
    }

    fun flush() {
        saveJob?.cancel()
        save(_state.value)
    }

    companion object {
        private const val TAG = "OnlineStore"
    }
}
