package com.jambo199.vividmusic.ui.online

import android.app.Application
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jambo199.vividmusic.AppGraph
import com.jambo199.vividmusic.online.ArchiveAlbum
import com.jambo199.vividmusic.online.ArchiveItem
import com.jambo199.vividmusic.online.ArchiveTrack
import com.jambo199.vividmusic.online.Downloads
import com.jambo199.vividmusic.online.Episode
import com.jambo199.vividmusic.online.InternetArchive
import com.jambo199.vividmusic.online.Load
import com.jambo199.vividmusic.online.OnlineData
import com.jambo199.vividmusic.online.Podcast
import com.jambo199.vividmusic.online.PodcastFeed
import com.jambo199.vividmusic.online.Podcasts
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

/** Fetches and remembers what the Online page shows: free music and podcasts. */
class OnlineViewModel(app: Application) : AndroidViewModel(app) {
    val data: StateFlow<OnlineData> = AppGraph.online.state

    // region free music (Internet Archive)

    private val _shelves = MutableStateFlow<Map<InternetArchive.Shelf, Load<List<ArchiveItem>>>>(emptyMap())
    val shelves: StateFlow<Map<InternetArchive.Shelf, Load<List<ArchiveItem>>>> = _shelves.asStateFlow()

    private val _archiveSearch = MutableStateFlow<Pair<String, Load<List<ArchiveItem>>>?>(null)
    val archiveSearch: StateFlow<Pair<String, Load<List<ArchiveItem>>>?> = _archiveSearch.asStateFlow()
    private var archiveJob: Job? = null

    fun startArchive() {
        InternetArchive.Shelf.entries.forEach { shelf ->
            if (_shelves.value[shelf] is Load.Ready) return@forEach
            _shelves.value = _shelves.value + (shelf to Load.Loading)
            viewModelScope.launch {
                val result = fetchOrFail { InternetArchive.shelf(shelf) }
                _shelves.value = _shelves.value + (shelf to result)
            }
        }
    }

    fun searchArchive(query: String) {
        archiveJob?.cancel()
        if (query.isBlank()) {
            _archiveSearch.value = null
            return
        }
        _archiveSearch.value = query to Load.Loading
        archiveJob = viewModelScope.launch {
            _archiveSearch.value = query to fetchOrFail { InternetArchive.search(query) }
        }
    }

    private val albums = HashMap<String, MutableStateFlow<Load<ArchiveAlbum>>>()

    fun album(id: String): StateFlow<Load<ArchiveAlbum>> {
        val flow = albums.getOrPut(id) { MutableStateFlow(Load.Loading) }
        if (flow.value !is Load.Ready) {
            flow.value = Load.Loading
            viewModelScope.launch { flow.value = fetchOrFail { InternetArchive.album(id) } }
        }
        return flow
    }

    /** Downloads tracks into Music/Vivid Music/…, where the library finds them. */
    fun downloadTracks(album: ArchiveAlbum, tracks: List<ArchiveTrack>): Int {
        var started = 0
        for (t in tracks) {
            if (AppGraph.online.current.downloads.containsKey(t.url)) continue
            try {
                val id = Downloads.enqueue(getApplication(), t.url, album.downloadPath(t), t.title, Environment.DIRECTORY_MUSIC)
                AppGraph.online.addDownload(t.url, id)
                started++
            } catch (e: Exception) {
                // e.g. no shared storage; reported below by the count.
            }
        }
        return started
    }

    // endregion

    // region podcasts

    private val _topPodcasts = MutableStateFlow<Load<List<Podcast>>>(Load.Loading)
    val topPodcasts: StateFlow<Load<List<Podcast>>> = _topPodcasts.asStateFlow()
    private var podcastsStarted = false

    private val _podcastSearch = MutableStateFlow<Pair<String, Load<List<Podcast>>>?>(null)
    val podcastSearch: StateFlow<Pair<String, Load<List<Podcast>>>?> = _podcastSearch.asStateFlow()
    private var podcastJob: Job? = null

    fun startPodcasts() {
        if (podcastsStarted) return
        podcastsStarted = true
        viewModelScope.launch { _topPodcasts.value = fetchOrFail { Podcasts.top() } }
    }

    fun searchPodcasts(query: String) {
        podcastJob?.cancel()
        if (query.isBlank()) {
            _podcastSearch.value = null
            return
        }
        _podcastSearch.value = query to Load.Loading
        podcastJob = viewModelScope.launch {
            _podcastSearch.value = query to fetchOrFail { Podcasts.search(query) }
        }
    }

    private val feeds = HashMap<String, MutableStateFlow<Load<PodcastFeed>>>()

    fun feed(url: String, refresh: Boolean = false): StateFlow<Load<PodcastFeed>> {
        val flow = feeds.getOrPut(url) { MutableStateFlow(Load.Loading) }
        if (refresh || flow.value !is Load.Ready) {
            if (flow.value !is Load.Ready) flow.value = Load.Loading
            viewModelScope.launch {
                val result = fetchOrFail { Podcasts.feed(url) }
                // Keep showing what we had if a refresh fails.
                if (result is Load.Ready || flow.value !is Load.Ready) flow.value = result
            }
        }
        return flow
    }

    fun toggleSubscription(podcast: Podcast) = AppGraph.online.toggleSubscription(podcast)

    fun downloadEpisode(podcast: Podcast, episode: Episode): Boolean = try {
        val id = Downloads.enqueue(getApplication(), episode.url, episode.downloadPath(podcast), episode.title, Environment.DIRECTORY_PODCASTS)
        AppGraph.online.addDownload(episode.url, id)
        true
    } catch (e: Exception) {
        false
    }

    fun removeDownload(url: String) {
        AppGraph.online.current.downloads[url]?.let { Downloads.cancel(getApplication(), it) }
        AppGraph.online.removeDownload(url)
    }

    // endregion

    // region download progress

    private val _downloadInfo = MutableStateFlow<Map<String, Downloads.Info>>(emptyMap())

    /** Status of every download we started, by address; refreshed while something is downloading. */
    val downloadInfo: StateFlow<Map<String, Downloads.Info>> = _downloadInfo.asStateFlow()
    private var pollJob: Job? = null

    fun watchDownloads() {
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch {
            while (isActive) {
                val ids = AppGraph.online.current.downloads
                val info = withContext(Dispatchers.IO) {
                    ids.mapValues { (_, id) ->
                        try {
                            Downloads.info(getApplication(), id)
                        } catch (e: Exception) {
                            Downloads.Info(Downloads.Status.NONE, 0f, null)
                        }
                    }
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

    private suspend fun <T> fetchOrFail(fetch: () -> T): Load<T> = withContext(Dispatchers.IO) {
        try {
            Load.Ready(fetch())
        } catch (e: Exception) {
            Load.Failed(e.friendlyMessage())
        }
    }
}
