package com.jambo199.vividmusic.ui.online

import android.app.Application
import android.os.Environment
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jambo199.vividmusic.AppGraph
import com.jambo199.vividmusic.data.writeTextAtomically
import com.jambo199.vividmusic.online.ChartProgress
import com.jambo199.vividmusic.online.Downloads
import com.jambo199.vividmusic.online.Load
import com.jambo199.vividmusic.online.Ncs
import com.jambo199.vividmusic.online.NcsArtist
import com.jambo199.vividmusic.online.NcsChart
import com.jambo199.vividmusic.online.NcsFilter
import com.jambo199.vividmusic.online.NcsSeen
import com.jambo199.vividmusic.online.NcsTrack
import com.jambo199.vividmusic.online.NcsTrackDetails
import com.jambo199.vividmusic.online.OnlineData
import com.jambo199.vividmusic.online.friendlyMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File

/** How the NCS page orders songs. */
enum class NcsSort(val label: String) { NEWEST("Newest"), POPULAR("Most popular") }

/** What the NCS page lists: a search and/or genre or mood, loaded a page at a time. */
@Immutable
data class NcsListing(
    val query: String = "",
    val genre: NcsFilter? = null,
    val mood: NcsFilter? = null,
    val sort: NcsSort = NcsSort.NEWEST,
    val tracks: List<NcsTrack> = emptyList(),
    val pages: Int = 0,
    val hasMore: Boolean = true,
    val loading: Boolean = false,
    val error: String? = null,
) {
    val filtered: Boolean get() = query.isNotBlank() || genre != null || mood != null

    /** The most-played chart; a typed search always shows ncs.io's own results. */
    val popular: Boolean get() = sort == NcsSort.POPULAR && query.isBlank()

    fun sameSearch(other: NcsListing) =
        query == other.query && genre == other.genre && mood == other.mood && sort == other.sort

    /** Whether [t] fits the chosen genre and mood (for the chart, which ncs.io can't narrow). */
    fun accepts(t: NcsTrack): Boolean =
        (genre == null || t.genres.any { it.equals(genre.name, ignoreCase = true) }) &&
            (mood == null || t.moods.any { it.equals(mood.name, ignoreCase = true) })
}

/** How the artist list is ordered; [param] is ncs.io's name for it. */
enum class NcsArtistSort(val label: String, val param: String) {
    POPULAR("Popular", "az"),
    AZ("A–Z", "az"),
    ZA("Z–A", "za"),
    LATEST("Recently joined", "latest"),
}

/** One artist in the artist list. */
@Immutable
data class NcsArtistEntry(
    val name: String,
    /** Their page on ncs.io, or "name:" and their name to look it up when opened. */
    val path: String,
    val image: String?,
    val subtitle: String,
    /** Place among the most played artists, 0 when not ranked. */
    val rank: Int = 0,
)

/** The NCS artists being listed, a page at a time. */
@Immutable
data class NcsArtistListing(
    val query: String = "",
    val sort: NcsArtistSort = NcsArtistSort.POPULAR,
    val artists: List<NcsArtistEntry> = emptyList(),
    val pages: Int = 0,
    val hasMore: Boolean = true,
    val loading: Boolean = false,
    val error: String? = null,
) {
    /** Ranked by plays when browsing; a typed search goes through ncs.io's directory. */
    val popular: Boolean get() = sort == NcsArtistSort.POPULAR && query.isBlank()
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
        // The filter menus come with ncs.io's own pages; fetch them when starting on the chart.
        if (_genres.value.isEmpty() && l.popular) loadFilters()
    }

    fun search(query: String) = restart(_listing.value.copy(query = query.trim()))

    fun selectGenre(genre: NcsFilter?) = restart(_listing.value.copy(genre = genre))

    fun selectMood(mood: NcsFilter?) = restart(_listing.value.copy(mood = mood))

    /** Picking an order shows songs again, and leaves any search behind. */
    fun selectSort(sort: NcsSort) {
        _showArtists.value = false
        restart(_listing.value.copy(sort = sort, query = ""))
    }

    fun clearFilters() = restart(_listing.value.copy(genre = null, mood = null))

    private fun restart(wanted: NcsListing) {
        if (wanted.sameSearch(_listing.value) && _listing.value.pages > 0) return
        loadJob?.cancel()
        _listing.value = NcsListing(query = wanted.query, genre = wanted.genre, mood = wanted.mood, sort = wanted.sort)
        if (_listing.value.popular && chart.tracks.isNotEmpty()) showChart() else loadMore()
        if (_genres.value.isEmpty() && _listing.value.popular) loadFilters()
    }

    /** Loads the next page, or tries the last one again after an error. */
    fun loadMore() {
        val l = _listing.value
        if (l.loading || !l.hasMore) return
        _listing.value = l.copy(loading = true, error = null)
        loadJob = viewModelScope.launch {
            if (l.popular) {
                val result = runCatching { loadChartPage() }
                if (!_listing.value.sameSearch(l)) return@launch
                result.onSuccess { showChart() }
                    .onFailure { e -> _listing.value = _listing.value.copy(loading = false, error = e.friendlyMessage()) }
                return@launch
            }
            val result = withContext(Dispatchers.IO) {
                runCatching { Ncs.search(l.query, l.genre?.id, l.mood?.id, l.pages + 1) }
            }
            val now = _listing.value
            if (!now.sameSearch(l)) return@launch
            result.onSuccess { page ->
                if (page.genres.isNotEmpty()) _genres.value = page.genres
                if (page.moods.isNotEmpty()) _moods.value = page.moods
                NcsSeen.add(page.tracks)
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

    private var filtersJob: Job? = null

    private fun loadFilters() {
        if (filtersJob?.isActive == true) return
        filtersJob = viewModelScope.launch {
            withContext(Dispatchers.IO) { runCatching { Ncs.search() } }.onSuccess { page ->
                if (page.genres.isNotEmpty()) _genres.value = page.genres
                if (page.moods.isNotEmpty()) _moods.value = page.moods
            }
        }
    }

    // region most played

    private val chartFile = File(app.cacheDir, "ncs_chart.json")
    private val chartLock = Mutex()
    private var chart = ChartProgress()
    private var chartRead = false

    /** The chart so far, narrowed to the chosen genre and mood. */
    private fun showChart() {
        val l = _listing.value
        val tracks = chart.tracks.filter { l.accepts(it) }
        _listing.value = l.copy(
            tracks = tracks,
            pages = maxOf(1, chart.nextIndex / CHART_PAGE),
            hasMore = chart.hasMore,
            loading = false,
            error = null,
        )
    }

    /** Matches the next places in the chart to their releases on ncs.io. */
    private suspend fun loadChartPage(): Unit = chartLock.withLock {
        withContext(Dispatchers.IO) {
            if (!chartRead) {
                chartRead = true
                val saved = ChartProgress.load(chartFile, System.currentTimeMillis())
                if (saved != null) {
                    chart = saved
                    NcsSeen.add(saved.tracks)
                    return@withContext
                }
            }
            if (!chart.hasMore) return@withContext
            val page = NcsChart.page(chart.nextIndex, CHART_PAGE)
            val gate = Semaphore(4)
            val found = coroutineScope {
                page.entries.map { e ->
                    async { gate.withPermit { Ncs.find(e.title, e.artist)?.copy(durationMs = e.durationMs) } }
                }.awaitAll()
            }
            val seen = chart.tracks.mapTo(HashSet()) { it.id }
            var place = chart.tracks.size
            val fresh = found.filterNotNull().filter { seen.add(it.id) }.map { it.copy(rank = ++place) }
            Log.i(TAG, "Chart places ${chart.nextIndex + 1}-${chart.nextIndex + page.entries.size}: ${found.count { it != null }} found on ncs.io")
            NcsSeen.add(fresh)
            chart = ChartProgress(
                tracks = chart.tracks + fresh,
                nextIndex = chart.nextIndex + page.entries.size,
                hasMore = page.hasMore,
                savedAt = chart.savedAt.takeIf { it > 0 } ?: System.currentTimeMillis(),
            )
            chartFile.writeTextAtomically(chart.toJson())
            Unit
        }
    }

    // endregion

    // region artist list

    private val _showArtists = MutableStateFlow(false)

    /** True while the page lists artists instead of songs. */
    val showArtists: StateFlow<Boolean> = _showArtists.asStateFlow()

    private val _artistList = MutableStateFlow(NcsArtistListing())
    val artistList: StateFlow<NcsArtistListing> = _artistList.asStateFlow()
    private var artistJob: Job? = null

    fun showArtistList() {
        _showArtists.value = true
        val l = _artistList.value
        if (l.pages == 0 && !l.loading && l.error == null) loadMoreArtists()
    }

    fun searchArtists(query: String) = restartArtists(_artistList.value.copy(query = query.trim()))

    fun sortArtists(sort: NcsArtistSort) = restartArtists(_artistList.value.copy(sort = sort, query = ""))

    private fun restartArtists(wanted: NcsArtistListing) {
        val now = _artistList.value
        if (wanted.query == now.query && wanted.sort == now.sort && now.pages > 0) return
        artistJob?.cancel()
        _artistList.value = NcsArtistListing(query = wanted.query, sort = wanted.sort)
        loadMoreArtists()
    }

    /** Loads the next page of artists, or tries again after an error. */
    fun loadMoreArtists() {
        val l = _artistList.value
        if (l.loading || !l.hasMore) return
        _artistList.value = l.copy(loading = true, error = null)
        artistJob = viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    if (l.popular) {
                        popularArtists() to false
                    } else {
                        val page = Ncs.artists(l.query, l.sort.param, l.pages + 1)
                        page.artists.map { NcsArtistEntry(it.name, it.path, it.image, it.tags) } to page.hasMore
                    }
                }
            }
            val now = _artistList.value
            if (now.query != l.query || now.sort != l.sort) return@launch
            result.onSuccess { (found, more) ->
                val seen = now.artists.mapTo(HashSet()) { it.path }
                val fresh = found.filter { seen.add(it.path) }
                _artistList.value = now.copy(
                    artists = now.artists + fresh,
                    pages = l.pages + 1,
                    hasMore = more && fresh.isNotEmpty(),
                    loading = false,
                )
            }.onFailure { e ->
                _artistList.value = now.copy(loading = false, error = e.friendlyMessage())
            }
        }
    }

    @Volatile
    private var chartArtists: List<NcsArtistEntry>? = null

    /** Artists ranked by how much their songs are played (from Deezer's chart). */
    private fun popularArtists(): List<NcsArtistEntry> = chartArtists ?: NcsChart.rankArtists(NcsChart.all())
        .mapIndexed { i, a ->
            NcsArtistEntry(
                name = a.name,
                path = NAME_PREFIX + a.name,
                image = a.picture,
                subtitle = if (a.songs == 1) "1 hit · ${a.topSong}" else "${a.songs} hits · best: ${a.topSong}",
                rank = i + 1,
            )
        }
        .also { chartArtists = it }

    // endregion

    // region artists and song details

    private val artists = HashMap<String, MutableStateFlow<Load<NcsArtist>>>()

    /** An NCS artist's page, most popular songs first. [path] can also be "name:" and a name. */
    fun artist(path: String, retry: Boolean = false): StateFlow<Load<NcsArtist>> {
        val flow = artists.getOrPut(path) { MutableStateFlow(Load.Failed("")) }
        if (flow.value is Load.Failed || (retry && flow.value !is Load.Loading)) {
            flow.value = Load.Loading
            viewModelScope.launch {
                flow.value = withContext(Dispatchers.IO) {
                    try {
                        val real = if (path.startsWith(NAME_PREFIX)) Ncs.findArtistPath(path.removePrefix(NAME_PREFIX)) else path
                        val a = Ncs.artist(real)
                        NcsSeen.add(a.tracks)
                        Load.Ready(a)
                    } catch (e: Exception) {
                        Load.Failed(e.friendlyMessage())
                    }
                }
            }
        }
        return flow
    }

    private val details = HashMap<String, MutableStateFlow<Load<NcsTrackDetails>>>()

    /** The credit text and video link from a song's page on ncs.io. */
    fun details(page: String): StateFlow<Load<NcsTrackDetails>> {
        val flow = details.getOrPut(page) { MutableStateFlow(Load.Failed("")) }
        if (flow.value is Load.Failed) {
            flow.value = Load.Loading
            viewModelScope.launch {
                flow.value = withContext(Dispatchers.IO) {
                    try {
                        Load.Ready(Ncs.details(page))
                    } catch (e: Exception) {
                        Load.Failed(e.friendlyMessage())
                    }
                }
            }
        }
        return flow
    }

    // endregion

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
        if (search.popular) {
            saveChart(search, libraryPaths, wifiOnly)
            return
        }
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

    /** Matches the rest of the chart, then saves all of it (in the chosen genre and mood). */
    private fun saveChart(search: NcsListing, libraryPaths: Set<String>, wifiOnly: Boolean) {
        saveAllJob = viewModelScope.launch {
            var pages = 0
            _saveAll.value = NcsSaveAll(0, 0, running = true)
            try {
                do {
                    loadChartPage()
                    pages++
                    if (_listing.value.sameSearch(search)) showChart()
                    _saveAll.value = NcsSaveAll(pages, 0, running = true)
                } while (chart.hasMore && isActive)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _saveAll.value = NcsSaveAll(pages, 0, running = false, error = e.friendlyMessage())
                return@launch
            }
            val queued = save(chart.tracks.filter { search.accepts(it) }, libraryPaths, quiet = true, wifiOnly = wifiOnly)
            _saveAll.value = NcsSaveAll(pages, queued, running = false)
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

        /** Chart places matched per page (each needs a look-up on ncs.io). */
        private const val CHART_PAGE = 25
        private const val TAG = "NcsViewModel"

        /** Marks an artist known only by name; their ncs.io page is looked up when opened. */
        const val NAME_PREFIX = "name:"
    }
}
