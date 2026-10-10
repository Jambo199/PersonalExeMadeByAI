package com.jambo199.vividmusic.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Scans folders the user picked with the system folder picker (Storage Access Framework).
 * This works even for folders Android's media index skips (".nomedia", app folders, SD cards).
 */
class FolderScanner(private val context: Context) {

    private val metaCache = MetadataCache(File(context.filesDir, "folder_metadata.json"))

    data class TreeScan(
        val treeUri: String,
        val rootKey: String,
        val rootName: String,
        val songs: List<Song>,
        /** Every folder key found in the tree (including empty ones) mapped to its parent key. */
        val folders: Map<String, String?>,
    )

    fun rootKeyFor(treeUri: String): String = "picked:" + Integer.toHexString(treeUri.hashCode())

    /** Lists the files quickly; metadata comes from the cache or the file name. */
    fun listTree(treeUriString: String): TreeScan? {
        val treeUri = Uri.parse(treeUriString)
        val rootKey = rootKeyFor(treeUriString)
        val rootDocId = try {
            DocumentsContract.getTreeDocumentId(treeUri)
        } catch (e: Exception) {
            return null
        }
        val rootName = queryDisplayName(DocumentsContract.buildDocumentUriUsingTree(treeUri, rootDocId))
            ?: rootDocId.substringAfterLast(':').substringAfterLast('/').ifBlank { "Folder" }

        val songs = ArrayList<Song>()
        val folders = LinkedHashMap<String, String?>()
        folders[rootKey] = null
        var visited = 0

        fun walk(docId: String, folderKey: String, displayPath: String, depth: Int) {
            if (depth > 24 || visited > 50_000) return
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
            val audio = ArrayList<FileEntry>()
            val subdirs = ArrayList<Pair<String, String>>()
            var cover: Uri? = null
            var bestCoverRank = Int.MAX_VALUE
            val lyrics = HashMap<String, Uri>()
            try {
                context.contentResolver.query(
                    childrenUri,
                    arrayOf(
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        DocumentsContract.Document.COLUMN_MIME_TYPE,
                        DocumentsContract.Document.COLUMN_SIZE,
                        DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                    ),
                    null, null, null,
                )?.use { c ->
                    while (c.moveToNext()) {
                        visited++
                        val id = c.getString(0) ?: continue
                        val name = c.getString(1) ?: continue
                        val mime = c.getString(2).orEmpty()
                        val size = if (c.isNull(3)) 0L else c.getLong(3)
                        val modified = if (c.isNull(4)) 0L else c.getLong(4)
                        val lower = name.lowercase()
                        when {
                            mime == DocumentsContract.Document.MIME_TYPE_DIR -> {
                                if (!name.startsWith(".")) subdirs += id to name
                            }
                            isAudio(lower, mime) -> audio += FileEntry(id, name, mime, size, modified)
                            lower.endsWith(".lrc") -> lyrics[lower.substringBeforeLast('.')] =
                                DocumentsContract.buildDocumentUriUsingTree(treeUri, id)
                            else -> {
                                val rank = coverRank(lower)
                                if (rank < bestCoverRank) {
                                    bestCoverRank = rank
                                    cover = DocumentsContract.buildDocumentUriUsingTree(treeUri, id)
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not list $displayPath", e)
                return
            }

            for (f in audio) {
                val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, f.id)
                val key = uri.toString()
                val cached = metaCache.get(key, f.size, f.modified)
                val baseName = f.name.substringBeforeLast('.')
                songs += Song(
                    key = key,
                    uri = uri,
                    mediaStoreId = -1,
                    title = cached?.title?.ifBlank { null } ?: baseName,
                    artist = cached?.artist.orEmpty(),
                    album = cached?.album?.ifBlank { null } ?: displayPath.substringAfterLast('/'),
                    albumArtist = cached?.albumArtist.orEmpty(),
                    albumId = 0,
                    durationMs = cached?.durationMs ?: 0L,
                    trackNumber = cached?.track ?: 0,
                    year = cached?.year ?: 0,
                    dateAddedSec = f.modified / 1000,
                    dateModifiedSec = f.modified / 1000,
                    sizeBytes = f.size,
                    path = "$displayPath/${f.name}",
                    folder = folderKey,
                    fileName = f.name,
                    mimeType = f.mime,
                    genre = cached?.genre.orEmpty(),
                    source = SongSource.FOLDER,
                    coverHint = cover,
                    lyricsHint = lyrics[f.name.lowercase().substringBeforeLast('.')],
                ).withSyntheticAlbumId()
            }
            for ((id, name) in subdirs) {
                val childKey = "$folderKey/$name"
                folders[childKey] = folderKey
                walk(id, childKey, "$displayPath/$name", depth + 1)
            }
        }

        walk(rootDocId, rootKey, rootName, 0)
        return TreeScan(treeUriString, rootKey, rootName, songs, folders)
    }

    /** True when some songs still only have file-name metadata. */
    fun needsMetadata(song: Song): Boolean = metaCache.get(song.key, song.sizeBytes, song.dateModifiedSec * 1000) == null

    /** Reads tags with MediaMetadataRetriever and caches them. Slow, so run off the main thread. */
    fun readMetadata(song: Song): Song {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, song.uri)
            fun meta(key: Int) = retriever.extractMetadata(key)?.trim().orEmpty()
            val entry = CachedMeta(
                size = song.sizeBytes,
                modified = song.dateModifiedSec * 1000,
                title = meta(MediaMetadataRetriever.METADATA_KEY_TITLE),
                artist = meta(MediaMetadataRetriever.METADATA_KEY_ARTIST),
                album = meta(MediaMetadataRetriever.METADATA_KEY_ALBUM),
                albumArtist = meta(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST),
                genre = meta(MediaMetadataRetriever.METADATA_KEY_GENRE),
                durationMs = meta(MediaMetadataRetriever.METADATA_KEY_DURATION).toLongOrNull() ?: 0L,
                track = parseTrack(
                    meta(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER),
                    meta(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER),
                ),
                year = meta(MediaMetadataRetriever.METADATA_KEY_YEAR).take(4).toIntOrNull()
                    ?: meta(MediaMetadataRetriever.METADATA_KEY_DATE).take(4).toIntOrNull() ?: 0,
            )
            metaCache.put(song.key, entry)
            song.copy(
                title = entry.title.ifBlank { song.title },
                artist = entry.artist,
                album = entry.album.ifBlank { song.album },
                albumArtist = entry.albumArtist,
                genre = entry.genre,
                durationMs = entry.durationMs,
                trackNumber = entry.track,
                year = entry.year,
            ).withSyntheticAlbumId()
        } catch (e: Exception) {
            // Unreadable file: remember that so we don't retry on every scan.
            metaCache.put(
                song.key,
                CachedMeta(song.sizeBytes, song.dateModifiedSec * 1000, "", "", "", "", "", 0, 0, 0),
            )
            song
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }

    fun saveCache() = metaCache.save()

    fun queryDisplayName(uri: Uri): String? = try {
        context.contentResolver.query(
            uri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null,
        )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    } catch (e: Exception) {
        null
    }

    private data class FileEntry(val id: String, val name: String, val mime: String, val size: Long, val modified: Long)

    companion object {
        private const val TAG = "FolderScanner"

        val AUDIO_EXTENSIONS = setOf(
            "mp3", "m4a", "aac", "flac", "ogg", "oga", "opus", "wav", "wma", "amr", "mka", "3gp",
            "mp4a", "m4b", "aif", "aiff", "ape", "wv", "mid", "midi", "xmf", "imy", "ota", "rtttl",
        )

        private val COVER_NAMES = listOf("cover", "folder", "front", "album", "albumart", "albumartsmall", "artwork")

        fun isAudio(lowerName: String, mime: String): Boolean {
            val ext = lowerName.substringAfterLast('.', "")
            return mime.startsWith("audio/") || ext in AUDIO_EXTENSIONS ||
                mime == "application/ogg" || mime == "application/x-flac"
        }

        private fun coverRank(lowerName: String): Int {
            val ext = lowerName.substringAfterLast('.', "")
            if (ext !in setOf("jpg", "jpeg", "png", "webp")) return Int.MAX_VALUE
            val base = lowerName.substringBeforeLast('.')
            val idx = COVER_NAMES.indexOf(base)
            return if (idx >= 0) idx else if (base.startsWith("albumart")) COVER_NAMES.size else Int.MAX_VALUE
        }

        private fun parseTrack(track: String, disc: String): Int {
            val t = track.substringBefore('/').trim().toIntOrNull() ?: 0
            val d = disc.substringBefore('/').trim().toIntOrNull() ?: 0
            return d * 1000 + t
        }
    }
}

/** Gives picked-folder songs a stable album id that never collides with MediaStore ids. */
fun Song.withSyntheticAlbumId(): Song {
    val hash = (folder + "\u0000" + album.lowercase() + "\u0000" + albumArtist.lowercase()).hashCode().toLong()
    return copy(albumId = -(hash and 0x7fffffffL) - 1)
}

internal data class CachedMeta(
    val size: Long,
    val modified: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumArtist: String,
    val genre: String,
    val durationMs: Long,
    val track: Int,
    val year: Int,
)

/** Small JSON cache so picked folders don't need their tags re-read on every launch. */
internal class MetadataCache(private val file: File) {
    private val map = ConcurrentHashMap<String, CachedMeta>()
    @Volatile private var loaded = false
    @Volatile private var dirty = false

    private fun ensureLoaded() {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            try {
                if (file.exists()) {
                    val root = JSONObject(file.readText())
                    for (key in root.keys()) {
                        val o = root.getJSONObject(key)
                        map[key] = CachedMeta(
                            size = o.optLong("s"),
                            modified = o.optLong("m"),
                            title = o.optString("t"),
                            artist = o.optString("a"),
                            album = o.optString("al"),
                            albumArtist = o.optString("aa"),
                            genre = o.optString("g"),
                            durationMs = o.optLong("d"),
                            track = o.optInt("n"),
                            year = o.optInt("y"),
                        )
                    }
                }
            } catch (e: Exception) {
                map.clear()
            }
            loaded = true
        }
    }

    fun get(key: String, size: Long, modified: Long): CachedMeta? {
        ensureLoaded()
        val m = map[key] ?: return null
        return if (m.size == size && m.modified == modified) m else null
    }

    fun put(key: String, meta: CachedMeta) {
        ensureLoaded()
        map[key] = meta
        dirty = true
    }

    @Synchronized
    fun save() {
        if (!dirty) return
        try {
            val root = JSONObject()
            for ((k, m) in map) {
                root.put(
                    k,
                    JSONObject()
                        .put("s", m.size).put("m", m.modified).put("t", m.title).put("a", m.artist)
                        .put("al", m.album).put("aa", m.albumArtist).put("g", m.genre)
                        .put("d", m.durationMs).put("n", m.track).put("y", m.year),
                )
            }
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(root.toString())
            tmp.renameTo(file)
            dirty = false
        } catch (e: Exception) {
            Log.w("MetadataCache", "Could not save cache", e)
        }
    }
}
