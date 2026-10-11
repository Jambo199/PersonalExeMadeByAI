package com.jambo199.vividmusic.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

/** Owns the music library: MediaStore songs plus songs from folders the user picked. */
class LibraryRepository(
    private val context: Context,
    private val settings: SettingsStore,
    private val scope: CoroutineScope,
    private val onLibraryChanged: () -> Unit = {},
) {
    private val _state = MutableStateFlow(LibraryState())
    val state: StateFlow<LibraryState> = _state.asStateFlow()
    val library: Library get() = _state.value.library

    private val folderScanner = FolderScanner(context)
    private val scanMutex = Mutex()
    private val enrichMutex = Mutex()
    private var mediaStoreSongs: List<Song> = emptyList()
    private val pickedScans = LinkedHashMap<String, FolderScanner.TreeScan>()
    private var observerJob: Job? = null
    private var observerRegistered = false

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            observerJob?.cancel()
            observerJob = scope.launch {
                delay(1500)
                refreshMediaStore()
            }
        }
    }

    init {
        // Only "include all audio" changes what we ask MediaStore for; the other filters are
        // applied to the songs we already have.
        scope.launch {
            settings.state
                .map { it.includeAllAudio }
                .distinctUntilChanged()
                .drop(1)
                .collect { refreshMediaStore() }
        }
        scope.launch {
            settings.state
                .map { Triple(it.minDurationSec, it.excludedFolders, it.artistSeparators) }
                .distinctUntilChanged()
                .drop(1)
                .collect { scanMutex.withLock { publish(scanning = false) } }
        }
        scope.launch {
            settings.state
                .map { it.pickedFolders }
                .distinctUntilChanged()
                .drop(1)
                .collect { refreshPickedFolders() }
        }
    }

    fun hasAudioPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    fun findByKey(key: String): Song? = library.byKey[key]

    /** Full rescan of everything. */
    fun refreshAll() {
        scope.launch {
            refreshMediaStore()
            refreshPickedFolders()
        }
    }

    suspend fun refreshMediaStore() {
        scanMutex.withLock {
            _state.update { it.copy(isScanning = true) }
            val hasPermission = hasAudioPermission()
            if (hasPermission && !observerRegistered) {
                context.contentResolver.registerContentObserver(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, observer,
                )
                observerRegistered = true
            }
            mediaStoreSongs = if (hasPermission) {
                withContext(Dispatchers.IO) { MediaStoreScanner.scan(context, settings.current.includeAllAudio) }
            } else {
                emptyList()
            }
            publish(scanning = false)
        }
    }

    suspend fun refreshPickedFolders() {
        val uris = settings.current.pickedFolders
        scanMutex.withLock {
            _state.update { it.copy(isScanning = true, scanMessage = if (uris.isEmpty()) null else "Scanning folders…") }
            pickedScans.keys.retainAll(uris.toSet())
            for (uri in uris) {
                val scan = withContext(Dispatchers.IO) { folderScanner.listTree(uri) } ?: continue
                pickedScans[uri] = scan
            }
            publish(scanning = false)
        }
        enrichPickedMetadata()
    }

    /**
     * Reads tags for picked-folder songs that only have file names so far. The library is
     * republished every couple of seconds so big folders fill in without rebuilding constantly.
     */
    private suspend fun enrichPickedMetadata() = enrichMutex.withLock {
        val pending = pickedScans.values.flatMap { scan -> scan.songs.filter { folderScanner.needsMetadata(it) } }
        if (pending.isEmpty()) return@withLock
        val total = pending.size
        val done = AtomicInteger(0)
        val batch = java.util.concurrent.ConcurrentHashMap<String, Song>()
        var lastPublish = 0L
        withContext(Dispatchers.IO) {
            pending.chunked(32).forEach { chunk ->
                chunk.map { song ->
                    async {
                        batch[song.key] = folderScanner.readMetadata(song)
                        done.incrementAndGet()
                    }
                }.awaitAll()
                val now = android.os.SystemClock.elapsedRealtime()
                if (now - lastPublish > 2000 || done.get() == total) {
                    lastPublish = now
                    scanMutex.withLock {
                        applyUpdates(HashMap(batch))
                        batch.clear()
                        _state.update { it.copy(scanMessage = "Reading tags ${done.get()}/$total") }
                        publish(scanning = true)
                    }
                }
            }
            folderScanner.saveCache()
        }
        scanMutex.withLock {
            if (batch.isNotEmpty()) applyUpdates(HashMap(batch))
            _state.update { it.copy(scanMessage = null) }
            publish(scanning = false)
        }
    }

    private fun applyUpdates(updated: Map<String, Song>) {
        for ((uri, scan) in pickedScans.entries.toList()) {
            pickedScans[uri] = scan.copy(songs = scan.songs.map { updated[it.key] ?: it })
        }
    }

    private suspend fun publish(scanning: Boolean) {
        val s = settings.current
        val minMs = s.minDurationSec * 1000L
        val excluded = s.excludedFolders
        val scans = pickedScans.values.toList()
        val msSongs = mediaStoreSongs
        val library = withContext(Dispatchers.Default) {
            val filteredMs = msSongs.filter { song ->
                (song.durationMs <= 0 || song.durationMs >= minMs) && excluded.none { isInside(song.folder, it) }
            }
            buildLibrary(filteredMs, scans, minMs, s.artistSeparators)
        }
        _state.update {
            it.copy(library = library, isScanning = scanning, hasLoadedOnce = true, scanMessage = if (scanning) it.scanMessage else null)
        }
        onLibraryChanged()
    }

    private fun isInside(folder: String, parent: String): Boolean =
        folder == parent || folder.startsWith(parent.trimEnd('/') + "/")

    private fun buildLibrary(msSongs: List<Song>, scans: List<FolderScanner.TreeScan>, minMs: Long, separators: Set<String>): Library {
        // Songs from picked folders appear everywhere unless the same file is already indexed.
        val known = msSongs.mapTo(HashSet()) { it.fileName.lowercase() to it.sizeBytes }
        val pickedSongs = scans.flatMap { it.songs }
            .filter { it.durationMs <= 0 || it.durationMs >= minMs }
        val allSongs = msSongs + pickedSongs.filter { (it.fileName.lowercase() to it.sizeBytes) !in known }

        val albums = allSongs.groupBy { it.albumId }.map { (id, songs) ->
            val sorted = songs.sortedWith(albumTrackOrder)
            val first = sorted.first()
            val artist = first.albumArtist.ifBlank {
                songs.groupingBy { it.displayArtist }.eachCount().maxByOrNull { it.value }?.key ?: Song.UNKNOWN_ARTIST
            }
            Album(id = id, title = first.displayAlbum, artist = artist, year = songs.maxOf { it.year }, songs = sorted)
        }

        val albumsBySong = HashMap<String, Album>()
        albums.forEach { album -> album.songs.forEach { albumsBySong[it.key] = album } }
        // Each song goes under every artist in its tag; names that differ only in case are merged.
        val byArtist = LinkedHashMap<String, Pair<String, MutableList<Song>>>()
        for (song in allSongs) {
            for (name in ArtistSeparators.split(song.displayArtist, separators)) {
                byArtist.getOrPut(name.lowercase()) { name to ArrayList() }.second += song
            }
        }
        val artists = byArtist.values.map { (name, songs) ->
            val artistAlbums = songs.mapNotNull { albumsBySong[it.key] }.distinctBy { it.id }
                .sortedWith(compareBy<Album> { it.year }.thenBy { sortKey(it.title) })
            Artist(name = name, songs = songs.sortedWith(compareBy<Song> { sortKey(it.album) }.thenBy { it.trackNumber }), albums = artistAlbums)
        }

        val (folders, roots) = buildFolderTree(msSongs, pickedSongs, scans)
        return Library(
            songs = allSongs,
            albums = albums,
            artists = artists,
            folders = folders,
            folderRoots = roots,
            byKey = (allSongs + pickedSongs).associateBy { it.key },
            artistSeparators = separators,
        )
    }

    private fun buildFolderTree(
        msSongs: List<Song>,
        pickedSongs: List<Song>,
        scans: List<FolderScanner.TreeScan>,
    ): Pair<Map<String, FolderNode>, List<String>> {
        class Mutable(val key: String, val parent: String?) {
            val children = LinkedHashSet<String>()
            val songs = ArrayList<Song>()
            var total = 0
            var cover: Song? = null
        }

        val nodes = HashMap<String, Mutable>()
        val roots = LinkedHashSet<String>()
        val pickedRootNames = HashMap<String, String>()

        // MediaStore folders: walk up each path to its storage root.
        for (song in msSongs) {
            var key = song.folder
            val root = storageRootOf(key)
            var child: String? = null
            while (true) {
                val parent = if (key == root) null else key.substringBeforeLast('/', "").ifBlank { null }
                val node = nodes.getOrPut(key) { Mutable(key, parent) }
                if (child != null) node.children += child
                if (child == null) node.songs += song
                node.total++
                if (node.cover == null) node.cover = song
                if (parent == null || key == root) {
                    roots += key
                    break
                }
                child = key
                key = parent
            }
        }

        // Picked folders keep their own structure, including empty subfolders.
        for (scan in scans) {
            pickedRootNames[scan.rootKey] = scan.rootName
            for ((key, parent) in scan.folders) {
                nodes.getOrPut(key) { Mutable(key, parent) }
                if (parent != null) nodes.getOrPut(parent) { Mutable(parent, scan.folders[parent]) }.children += key
            }
            roots += scan.rootKey
        }
        for (song in pickedSongs) {
            var key: String? = song.folder
            var direct = true
            while (key != null) {
                val node = nodes[key] ?: break
                if (direct) node.songs += song
                node.total++
                if (node.cover == null) node.cover = song
                direct = false
                key = node.parent
            }
        }

        val finished = HashMap<String, FolderNode>(nodes.size)
        for ((key, m) in nodes) {
            val isPicked = key.startsWith("picked:")
            val rootKey = if (isPicked) key.substringBefore('/') else null
            val name = when {
                isPicked && rootKey == key -> pickedRootNames[key] ?: "Folder"
                isPicked -> key.substringAfterLast('/')
                else -> storageName(key) ?: key.substringAfterLast('/').ifBlank { key }
            }
            val displayPath = when {
                isPicked -> (pickedRootNames[rootKey] ?: "Folder") + key.removePrefix(rootKey!!)
                else -> prettyPath(key)
            }
            finished[key] = FolderNode(
                key = key,
                name = name,
                displayPath = displayPath,
                parentKey = m.parent,
                childKeys = m.children.toList(),
                songs = m.songs,
                totalSongs = m.total,
                cover = m.cover,
                isPickedFolder = isPicked,
            )
        }
        return finished to roots.toList()
    }

    companion object {
        private val EMULATED = Regex("^/storage/emulated/\\d+")
        private val VOLUME = Regex("^/storage/[^/]+")
        private val SD_CARD = Regex("^/storage/[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}$")

        fun storageRootOf(path: String): String {
            EMULATED.find(path)?.let { return it.value }
            if (!path.startsWith("/storage/emulated")) VOLUME.find(path)?.let { return it.value }
            val parts = path.split('/').filter { it.isNotEmpty() }
            return if (parts.isEmpty()) "/" else "/" + parts.take(2).joinToString("/")
        }

        fun storageName(path: String): String? = when {
            path == primaryStoragePath() || EMULATED.matches(path) -> "Internal storage"
            SD_CARD.matches(path) -> "SD card"
            path.startsWith("/storage/") && path.count { it == '/' } == 2 && path != "/storage/emulated" -> "USB / external storage"
            else -> null
        }

        fun prettyPath(path: String): String {
            val root = storageRootOf(path)
            val name = storageName(root) ?: return path
            return name + path.removePrefix(root)
        }

        private fun primaryStoragePath(): String = try {
            Environment.getExternalStorageDirectory().absolutePath
        } catch (e: Exception) {
            "/storage/emulated/0"
        }

        fun treeUriToReadable(context: Context, uri: Uri): String =
            FolderScanner(context).queryDisplayName(
                android.provider.DocumentsContract.buildDocumentUriUsingTree(
                    uri, android.provider.DocumentsContract.getTreeDocumentId(uri),
                ),
            ) ?: uri.lastPathSegment.orEmpty()
    }
}
