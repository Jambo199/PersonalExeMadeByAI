package com.jambo199.vividmusic.ui

import android.app.Application
import android.app.RecoverableSecurityException
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jambo199.vividmusic.AppGraph
import com.jambo199.vividmusic.data.Album
import com.jambo199.vividmusic.data.Artist
import com.jambo199.vividmusic.data.FolderNode
import com.jambo199.vividmusic.data.Library
import com.jambo199.vividmusic.data.LibraryState
import com.jambo199.vividmusic.data.Lyrics
import com.jambo199.vividmusic.data.Playlist
import com.jambo199.vividmusic.data.Settings
import com.jambo199.vividmusic.data.SmartPlaylists
import com.jambo199.vividmusic.data.Song
import com.jambo199.vividmusic.data.SongSource
import com.jambo199.vividmusic.data.UserData
import com.jambo199.vividmusic.data.albumComparator
import com.jambo199.vividmusic.data.artistComparator
import com.jambo199.vividmusic.data.folderSongComparator
import com.jambo199.vividmusic.data.searchKey
import com.jambo199.vividmusic.data.songComparator
import com.jambo199.vividmusic.playback.PlaybackState
import com.jambo199.vividmusic.playback.PlayerConnection
import com.jambo199.vividmusic.playback.PlayerEvents
import com.jambo199.vividmusic.playback.QueueEntry
import com.jambo199.vividmusic.playback.SleepTimer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SearchResults(
    val query: String = "",
    val songs: List<Song> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val folders: List<FolderNode> = emptyList(),
) {
    val isEmpty: Boolean get() = songs.isEmpty() && albums.isEmpty() && artists.isEmpty() && folders.isEmpty()
}

/** What the delete flow needs from the UI: either nothing, or a system confirmation dialog. */
sealed interface DeleteRequest {
    data class NeedsConfirmation(val intentSender: IntentSender, val songs: List<Song>) : DeleteRequest
    data class Done(val deleted: Int, val failed: Int) : DeleteRequest
}

@OptIn(FlowPreview::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {
    val player = PlayerConnection(app)

    val settings: StateFlow<Settings> = AppGraph.settings.state
    val libraryState: StateFlow<LibraryState> = AppGraph.library.state
    val userData: StateFlow<UserData> = AppGraph.userData.state
    val playback: StateFlow<PlaybackState> = player.state
    val queue: StateFlow<List<QueueEntry>> = player.queue
    val queueSource: StateFlow<String> = player.source
    val lyrics: StateFlow<Lyrics?> = AppGraph.lyrics.current
    val sleepTimer: StateFlow<SleepTimer.Mode> = SleepTimer.mode

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val _hasPermission = MutableStateFlow(AppGraph.library.hasAudioPermission())
    val hasPermission: StateFlow<Boolean> = _hasPermission.asStateFlow()

    private val _openPlayer = MutableStateFlow(false)

    /** True when something (a notification tap, "Open with") asked to show the player. */
    val openPlayer: StateFlow<Boolean> = _openPlayer.asStateFlow()

    val library: StateFlow<Library> = libraryState.map { it.library }
        .stateIn(viewModelScope, SharingStarted.Eagerly, libraryState.value.library)

    val sortedSongs: StateFlow<List<Song>> = combine(
        library,
        settings.map { it.songSort to it.songSortAscending }.distinctUntilChanged(),
    ) { lib, (field, asc) -> lib.songs.sortedWith(songComparator(field, asc)) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val sortedAlbums: StateFlow<List<Album>> = combine(
        library,
        settings.map { it.albumSort to it.albumSortAscending }.distinctUntilChanged(),
    ) { lib, (field, asc) -> lib.albums.sortedWith(albumComparator(field, asc)) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val sortedArtists: StateFlow<List<Artist>> = combine(
        library,
        settings.map { it.artistSort to it.artistSortAscending }.distinctUntilChanged(),
    ) { lib, (field, asc) -> lib.artists.sortedWith(artistComparator(field, asc)) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // region search

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private data class IndexedSong(val song: Song, val text: String)

    private val searchIndex = library.map { lib ->
        lib.songs.map { IndexedSong(it, searchKey("${it.title} ${it.artist} ${it.album} ${it.fileName}")) }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val searchResults: StateFlow<SearchResults> = combine(_query.debounce(120), searchIndex, library) { q, index, lib ->
        val terms = searchKey(q.trim()).split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (terms.isEmpty()) {
            SearchResults(q)
        } else {
            fun matches(text: String) = terms.all { text.contains(it) }
            SearchResults(
                query = q,
                songs = index.asSequence().filter { matches(it.text) }.map { it.song }.take(300).toList(),
                albums = lib.albums.filter { matches(searchKey("${it.title} ${it.artist}")) }.take(50),
                artists = lib.artists.filter { matches(searchKey(it.name)) }.take(50),
                folders = lib.folders.values.filter { it.totalSongs > 0 && matches(searchKey(it.name)) }.take(50),
            )
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Lazily, SearchResults())

    fun setQuery(q: String) {
        _query.value = q
    }

    // endregion

    init {
        viewModelScope.launch {
            PlayerEvents.messages.collect { _messages.emit(it) }
        }
    }

    fun toast(message: String) {
        _messages.tryEmit(message)
    }

    fun onResume() {
        val granted = AppGraph.library.hasAudioPermission()
        if (granted != _hasPermission.value) {
            _hasPermission.value = granted
            if (granted) viewModelScope.launch { AppGraph.library.refreshMediaStore() }
        }
    }

    fun onPermissionResult() = onResume()

    fun requestOpenPlayer() {
        _openPlayer.value = true
    }

    fun consumeOpenPlayer() {
        _openPlayer.value = false
    }

    private val _navRequest = MutableStateFlow<String?>(null)

    /** A screen to open, from a launcher shortcut (search, settings, equalizer, favorites). */
    val navRequest: StateFlow<String?> = _navRequest.asStateFlow()

    private val _tabRequest = MutableStateFlow<Int?>(null)

    /** A library tab to switch to, from a launcher shortcut. */
    val tabRequest: StateFlow<Int?> = _tabRequest.asStateFlow()

    fun consumeNavRequest() {
        _navRequest.value = null
    }

    fun consumeTabRequest() {
        _tabRequest.value = null
    }

    fun requestTab(tab: Int) {
        _tabRequest.value = tab
    }

    /** Handles launcher shortcuts and other "open at" requests. */
    fun handleShortcut(action: String) {
        when (action) {
            "shuffle" -> viewModelScope.launch {
                // On a cold start the library may still be loading.
                val songs = kotlinx.coroutines.withTimeoutOrNull(15_000) {
                    library.first { it.songs.isNotEmpty() }.songs
                }
                if (songs != null) {
                    shuffle(songs, "All songs")
                    requestOpenPlayer()
                } else {
                    toast("No songs to shuffle yet")
                }
            }
            "player" -> requestOpenPlayer()
            "folders" -> requestTab(3)
            "playlists" -> requestTab(4)
            "songs" -> requestTab(0)
            "albums" -> requestTab(1)
            "artists" -> requestTab(2)
            else -> _navRequest.value = action
        }
    }

    // region playback

    fun play(songs: List<Song>, index: Int = 0, source: String = "") = player.playSongs(songs, index, null, source)

    fun shuffle(songs: List<Song>, source: String = "") {
        if (songs.isEmpty()) return
        player.playSongs(songs, 0, true, source)
    }

    fun playInOrder(songs: List<Song>, source: String = "") = player.playSongs(songs, 0, false, source)

    fun playNext(songs: List<Song>) {
        player.playNext(songs)
        toast(if (songs.size == 1) "Playing next" else "${songs.size} songs playing next")
    }

    fun addToQueue(songs: List<Song>) {
        player.addToQueue(songs)
        toast(if (songs.size == 1) "Added to queue" else "${songs.size} songs added to queue")
    }

    /** Plays a file opened from another app ("Open with"). */
    fun playExternal(uri: Uri, mimeType: String?) {
        viewModelScope.launch {
            val song = withContext(Dispatchers.IO) { externalSong(uri, mimeType) }
            player.playSongs(listOf(song), 0, false, "Opened file")
            requestOpenPlayer()
        }
    }

    private fun externalSong(uri: Uri, mimeType: String?): Song {
        val context = getApplication<Application>()
        AppGraph.library.library.byKey[uri.toString()]?.let { return it }
        var name = uri.lastPathSegment?.substringAfterLast('/') ?: "Audio"
        var size = 0L
        try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
                ?.use { c ->
                    if (c.moveToFirst()) {
                        c.getString(0)?.let { name = it }
                        if (!c.isNull(1)) size = c.getLong(1)
                    }
                }
        } catch (_: Exception) {
        }
        var title = name.substringBeforeLast('.')
        var artist = ""
        var album = ""
        var duration = 0L
        val retriever = android.media.MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() }?.let { title = it }
            artist = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ARTIST).orEmpty()
            album = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ALBUM).orEmpty()
            duration = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (_: Exception) {
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
        return Song(
            key = uri.toString(), uri = uri, mediaStoreId = -1, title = title, artist = artist, album = album,
            albumArtist = "", albumId = 0, durationMs = duration, trackNumber = 0, year = 0,
            dateAddedSec = System.currentTimeMillis() / 1000, dateModifiedSec = 0, sizeBytes = size,
            path = name, folder = "", fileName = name, mimeType = mimeType.orEmpty(), genre = "",
            source = SongSource.EXTERNAL,
        )
    }

    // endregion

    // region library helpers

    fun folderSongs(node: FolderNode): List<Song> = node.songs.sortedWith(folderSongComparator(settings.value.folderSongOrder))

    fun folderTreeSongs(node: FolderNode): List<Song> =
        library.value.songsInTree(node.key, folderSongComparator(settings.value.folderSongOrder))

    fun playlistSongs(id: Long, lib: Library, data: UserData): List<Song> = when (id) {
        SmartPlaylists.FAVORITES -> data.favorites.mapNotNull { lib.byKey[it] }
        SmartPlaylists.RECENTLY_ADDED -> lib.songs.sortedByDescending { it.dateAddedSec }.take(100)
        SmartPlaylists.RECENTLY_PLAYED -> data.recent.mapNotNull { lib.byKey[it] }
        SmartPlaylists.MOST_PLAYED -> data.playCounts.entries.sortedByDescending { it.value }
            .mapNotNull { lib.byKey[it.key] }.take(100)
        else -> data.playlists.firstOrNull { it.id == id }?.songKeys?.mapNotNull { lib.byKey[it] }.orEmpty()
    }

    fun refreshLibrary() {
        AppGraph.library.refreshAll()
        toast("Refreshing library…")
    }

    fun addPickedFolder(uri: Uri) {
        val context = getApplication<Application>()
        try {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) {
        }
        AppGraph.settings.addPickedFolder(uri.toString())
        toast("Folder added – scanning…")
    }

    fun removePickedFolder(uri: String) {
        val context = getApplication<Application>()
        try {
            context.contentResolver.releasePersistableUriPermission(Uri.parse(uri), Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) {
        }
        AppGraph.settings.removePickedFolder(uri)
    }

    fun excludeFolder(path: String) {
        AppGraph.settings.update { it.copy(excludedFolders = it.excludedFolders + path) }
        toast("Folder hidden. Undo it in Settings → Hidden folders")
    }

    fun includeFolder(path: String) = AppGraph.settings.update { it.copy(excludedFolders = it.excludedFolders - path) }

    fun updateSettings(transform: (Settings) -> Settings) = AppGraph.settings.update(transform)

    // endregion

    // region favourites & playlists

    fun toggleFavorite(song: Song) {
        val wasFavorite = AppGraph.userData.isFavorite(song.key)
        AppGraph.userData.toggleFavorite(song.key)
        toast(if (wasFavorite) "Removed from favourites" else "Added to favourites")
    }

    fun createPlaylist(name: String, songs: List<Song> = emptyList()): Long {
        val id = AppGraph.userData.createPlaylist(name, songs.map { it.key })
        toast(if (songs.isEmpty()) "Playlist created" else "Added to new playlist")
        return id
    }

    fun addToPlaylist(playlist: Playlist, songs: List<Song>) {
        AppGraph.userData.addToPlaylist(playlist.id, songs.map { it.key })
        toast("Added to ${playlist.name}")
    }

    fun renamePlaylist(id: Long, name: String) = AppGraph.userData.renamePlaylist(id, name)
    fun deletePlaylist(id: Long) = AppGraph.userData.deletePlaylist(id)
    fun removeFromPlaylist(id: Long, index: Int) = AppGraph.userData.removeFromPlaylist(id, index)
    fun movePlaylistItem(id: Long, from: Int, to: Int) = AppGraph.userData.movePlaylistItem(id, from, to)
    fun clearHistory() {
        AppGraph.userData.clearHistory()
        toast("Listening history cleared")
    }

    fun saveQueueAsPlaylist(name: String) {
        val songs = queue.value.sortedBy { it.index }.map { it.song }
        if (songs.isNotEmpty()) createPlaylist(name, songs)
    }

    /** Writes a playlist as an M3U8 file the user picked. */
    fun exportPlaylist(name: String, songs: List<Song>, target: Uri) {
        val context = getApplication<Application>()
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openOutputStream(target, "wt")?.bufferedWriter(Charsets.UTF_8)?.use { w ->
                        w.write("#EXTM3U\n#PLAYLIST:$name\n")
                        for (song in songs) {
                            w.write("#EXTINF:${song.durationMs / 1000},${song.displayArtist} - ${song.title}\n")
                            w.write(song.path.ifBlank { song.uri.toString() } + "\n")
                        }
                    } != null
                } catch (e: Exception) {
                    false
                }
            }
            toast(if (ok) "Exported ${songs.size} songs" else "Couldn't save the playlist file")
        }
    }

    /** Reads an M3U/M3U8 file and creates a playlist from the songs found in the library. */
    fun importPlaylist(source: Uri) {
        val context = getApplication<Application>()
        viewModelScope.launch {
            val lib = library.value
            val result = withContext(Dispatchers.IO) {
                try {
                    val text = context.contentResolver.openInputStream(source)?.use { String(it.readBytes(), Charsets.UTF_8) }
                        ?: return@withContext null
                    val entries = text.lineSequence().map { it.trim().removePrefix("\uFEFF") }
                        .filter { it.isNotEmpty() && !it.startsWith("#") }.toList()
                    val byPath = lib.songs.associateBy { it.path.lowercase() }
                    val byName = lib.songs.groupBy { it.fileName.lowercase() }
                    val matched = entries.mapNotNull { entry ->
                        val norm = Uri.decode(entry).replace('\\', '/')
                        byPath[norm.lowercase()]
                            ?: lib.byKey[entry]
                            ?: byName[norm.substringAfterLast('/').lowercase()]?.let { candidates ->
                                // Same file name in several folders: prefer the one whose folder matches too.
                                candidates.firstOrNull { c ->
                                    norm.lowercase().endsWith(c.path.lowercase().split('/').takeLast(2).joinToString("/"))
                                } ?: candidates.first()
                            }
                    }
                    val name = context.contentResolver.query(source, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                        ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
                        ?.substringBeforeLast('.') ?: "Imported playlist"
                    Triple(name, matched, entries.size)
                } catch (e: Exception) {
                    null
                }
            }
            if (result == null) {
                toast("Couldn't read that playlist file")
                return@launch
            }
            val (name, songs, total) = result
            if (songs.isEmpty()) {
                toast("None of the $total songs in that file are on this phone")
            } else {
                AppGraph.userData.createPlaylist(name, songs.map { it.key })
                toast("Imported \"$name\" – ${songs.size} of $total songs found")
            }
        }
    }

    // endregion

    // region lyrics

    fun assignLyrics(song: Song, uri: Uri) {
        AppGraph.lyrics.assign(song.key, uri)
        toast("Lyrics file linked")
    }

    fun unassignLyrics(song: Song) = AppGraph.lyrics.unassign(song.key)
    fun hasLyricsFile(song: Song) = AppGraph.lyrics.hasAssignment(song.key)

    // endregion

    // region delete

    /**
     * Deletes files. On Android 11+ the system asks the user to confirm; the UI launches the
     * returned intent and then calls [onDeleteConfirmed].
     */
    fun deleteSongs(songs: List<Song>, onResult: (DeleteRequest) -> Unit) {
        val context = getApplication<Application>()
        viewModelScope.launch {
            val mediaStoreSongs = songs.filter { it.source == SongSource.MEDIA_STORE }
            val folderSongs = songs.filter { it.source == SongSource.FOLDER }
            var deleted = 0
            var failed = 0
            withContext(Dispatchers.IO) {
                for (s in folderSongs) {
                    try {
                        if (DocumentsContract.deleteDocument(context.contentResolver, s.uri)) deleted++ else failed++
                    } catch (e: Exception) {
                        failed++
                    }
                }
            }
            if (mediaStoreSongs.isNotEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val request = MediaStore.createDeleteRequest(context.contentResolver, mediaStoreSongs.map { it.uri })
                if (deleted > 0) onDeleted(folderSongs.map { it.key }.toSet())
                onResult(DeleteRequest.NeedsConfirmation(request.intentSender, mediaStoreSongs))
                return@launch
            }
            val removedKeys = HashSet<String>()
            folderSongs.forEach { removedKeys += it.key }
            for (s in mediaStoreSongs) {
                try {
                    val rows = withContext(Dispatchers.IO) { context.contentResolver.delete(s.uri, null, null) }
                    if (rows > 0) {
                        deleted++
                        removedKeys += s.key
                        // Pre-Android 10 the row delete doesn't always remove the file itself.
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) runCatching { java.io.File(s.path).delete() }
                    } else {
                        failed++
                    }
                } catch (e: SecurityException) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && e is RecoverableSecurityException) {
                        onDeleted(removedKeys)
                        onResult(DeleteRequest.NeedsConfirmation(e.userAction.actionIntent.intentSender, listOf(s)))
                        return@launch
                    }
                    failed++
                } catch (e: Exception) {
                    failed++
                }
            }
            onDeleted(removedKeys)
            onResult(DeleteRequest.Done(deleted, failed))
        }
    }

    /** Called after the system delete dialog returns OK. */
    fun onDeleteConfirmed(songs: List<Song>) {
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
            // On Android 10 the dialog only grants permission; delete now.
            viewModelScope.launch {
                withContext(Dispatchers.IO) {
                    songs.forEach { runCatching { getApplication<Application>().contentResolver.delete(it.uri, null, null) } }
                }
                onDeleted(songs.map { it.key }.toSet())
            }
        } else {
            onDeleted(songs.map { it.key }.toSet())
        }
        toast(if (songs.size == 1) "Deleted" else "${songs.size} songs deleted")
    }

    private fun onDeleted(keys: Set<String>) {
        if (keys.isEmpty()) return
        player.removeKeys(keys)
        AppGraph.userData.forget(keys)
        AppGraph.library.refreshAll()
    }

    // endregion

    override fun onCleared() {
        player.disconnect()
        super.onCleared()
    }
}
