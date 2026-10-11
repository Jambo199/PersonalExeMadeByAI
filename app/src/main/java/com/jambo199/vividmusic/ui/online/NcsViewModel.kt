package com.jambo199.vividmusic.ui.online

import android.app.Application
import android.os.Environment
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jambo199.vividmusic.AppGraph
import com.jambo199.vividmusic.online.Downloads
import com.jambo199.vividmusic.online.Ncs
import com.jambo199.vividmusic.online.NcsFilter
import com.jambo199.vividmusic.online.NcsTrack
import com.jambo199.vividmusic.online.OnlineData
import com.jambo199.vividmusic.online.friendlyMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** What the NCS page lists: a search and/or genre or mood, loaded a page at a time. */
@Immutable
data class NcsListing(
    val query: String = "",
    val genre: NcsFilter? = null,
    val mood: NcsFilter? = null,
    val tracks: List<NcsTrack> = emptyList(),
    val pages: Int = 0,
    val hasMore: Boolean = true,
    val loading: Boolean = false,
    val error: String? = null,
) {
    val filtered: Boolean get() = query.isNotBlank() || genre != null || mood != null

    fun sameSearch(other: NcsListing) = query == other.query && genre == other.genre && mood == other.mood
}

/** Progress of "save every song". */
@Immutable
data class NcsSaveAll(val pagesRead: Int, val queued: Int, val running: Boolean, val error: String? = null)

/** Browses NoCopyrightSounds on ncs.io and saves songs into Music › NCS. */
class NcsViewModel(app: Application) : AndroidViewModel(app) {
    private val _listing = MutableStateFlow(NcsListing())
    val listing: StateFlow<NcsListing> = _listing.asStateFlow()

    private val _genres = MutableStateFlow<List<NcsFilter>>(emptyList())
    val genres: StateFlow<List<NcsFilter>> = _genres.asStateFlow()
    private val _moods = MutableStateFlow<List<NcsFilter>>(emptyList())
    val moods: StateFlow<List<NcsFilter>> = _moods.asStateFlow()

    val data: StateFlow<OnlineData> = AppGraph.online.state
    private var loadJob: Job? = null

    fun start() {
        val l = _listing.value
        if (l.pages == 0 && !l.loading && l.error == null) loadMore()
    }

    fun search(query: String) = restart(_listing.value.copy(query = query.trim()))

    fun selectGenre(genre: NcsFilter?) = restart(_listing.value.copy(genre = genre))

    fun selectMood(mood: NcsFilter?) = restart(_listing.value.copy(mood = mood))

    private fun restart(wanted: NcsListing) {
        if (wanted.sameSearch(_listing.value) && _listing.value.pages > 0) return
        loadJob?.cancel()
        _listing.value = NcsListing(query = wanted.query, genre = wanted.genre, mood = wanted.mood)
        loadMore()
    }

    /** Loads the next page, or tries the last one again after an error. */
    fun loadMore() {
        val l = _listing.value
        if (l.loading || !l.hasMore) return
        _listing.value = l.copy(loading = true, error = null)
        loadJob = viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { Ncs.search(l.query, l.genre?.id, l.mood?.id, l.pages + 1) }
            }
            val now = _listing.value
            if (!now.sameSearch(l)) return@launch
            result.onSuccess { page ->
                if (page.genres.isNotEmpty()) _genres.value = page.genres
                if (page.moods.isNotEmpty()) _moods.value = page.moods
                val seen = now.tracks.mapTo(HashSet()) { it.id }
                val fresh = page.tracks.filter { seen.add(it.id) }
                _listing.value = now.copy(
                    tracks = now.tracks + fresh,
                    pages = l.pages + 1,
                    hasMore = page.hasMore && fresh.isNotEmpty(),
                    loading = false,
                )
            }.onFailure { e ->
                _listing.value = now.copy(loading = false, error = e.friendlyMessage())
            }
        }
    }

    // region saving

    private val musicRoot: File get() = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)

    /** Whether [track] is already on the phone (saved earlier, by us or by hand). */
    fun isSaved(track: NcsTrack, libraryPaths: Set<String>): Boolean {
        val path = File(musicRoot, track.downloadPath).absolutePath
        return path in libraryPaths
    }

    /** Starts downloading [tracks] that aren't saved or downloading yet. Returns how many started. */
    fun save(tracks: List<NcsTrack>, libraryPaths: Set<String>, quiet: Boolean = tracks.size > 1, wifiOnly: Boolean = false): Int {
        var started = 0
        for (t in tracks) {
            if (AppGraph.online.current.downloads.containsKey(t.url)) continue
            if (isSaved(t, libraryPaths) || File(musicRoot, t.downloadPath).exists()) continue
            try {
                val id = Downloads.enqueue(getApplication(), t.url, t.downloadPath, "${t.title} – ${t.artists}", Environment.DIRECTORY_MUSIC, quiet, wifiOnly)
                AppGraph.online.addDownload(t.url, id)
                started++
            } catch (e: Exception) {
                // e.g. no shared storage; the count tells the user.
            }
        }
        return started
    }

    /** Forgets a failed download so it can be started again. */
    fun forget(track: NcsTrack) {
        AppGraph.online.current.downloads[track.url]?.let { runCatching { Downloads.cancel(getApplication(), it) } }
        AppGraph.online.removeDownload(track.url)
    }

    private val _saveAll = MutableStateFlow<NcsSaveAll?>(null)
    val saveAll: StateFlow<NcsSaveAll?> = _saveAll.asStateFlow()
    private var saveAllJob: Job? = null

    /**
     * Goes through every page of the current list (all of NCS when nothing is chosen) and
     * queues each song. Downloads wait for Wi-Fi when [wifiOnly].
     */
    fun saveEverything(libraryPaths: Set<String>, wifiOnly: Boolean) {
        if (saveAllJob?.isActive == true) return
        val search = _listing.value
        saveAllJob = viewModelScope.launch {
            var page = 1
            var queued = 0
            _saveAll.value = NcsSaveAll(0, 0, running = true)
            while (isActive) {
                val result = withContext(Dispatchers.IO) {
                    runCatching { Ncs.search(search.query, search.genre?.id, search.mood?.id, page) }
                }
                val p = result.getOrElse { e ->
                    _saveAll.value = NcsSaveAll(page - 1, queued, running = false, error = e.friendlyMessage())
                    return@launch
                }
                queued += save(p.tracks, libraryPaths, quiet = true, wifiOnly = wifiOnly)
                _saveAll.value = NcsSaveAll(page, queued, running = true)
                if (!p.hasMore) break
                page++
                delay(PAGE_PAUSE_MS) // go easy on ncs.io
            }
            _saveAll.value = NcsSaveAll(page, queued, running = false)
        }
    }

    fun stopSaveEverything() {
        saveAllJob?.cancel()
        _saveAll.value = _saveAll.value?.copy(running = false)
    }

    fun dismissSaveEverything() {
        if (saveAllJob?.isActive != true) _saveAll.value = null
    }

    private val _downloadInfo = MutableStateFlow<Map<String, Downloads.Info>>(emptyMap())

    /** Status of every download we started, by address; refreshed while the page is open. */
    val downloadInfo: StateFlow<Map<String, Downloads.Info>> = _downloadInfo.asStateFlow()
    private var pollJob: Job? = null

    fun watchDownloads() {
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch {
            while (isActive) {
                val ids = AppGraph.online.current.downloads
                val info = withContext(Dispatchers.IO) {
                    val byId = try {
                        Downloads.infoAll(getApplication(), ids.values)
                    } catch (e: Exception) {
                        emptyMap()
                    }
                    ids.mapValues { (_, id) -> byId[id] ?: Downloads.Info(Downloads.Status.NONE, 0f) }
                }
                _downloadInfo.value = info
                val busy = info.values.any { it.status == Downloads.Status.RUNNING || it.status == Downloads.Status.QUEUED }
                delay(if (busy) 1_000 else 5_000)
            }
        }
    }

    fun stopWatchingDownloads() {
        pollJob?.cancel()
    }

    // endregion

    companion object {
        private const val PAGE_PAUSE_MS = 400L
    }
}
