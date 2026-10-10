package com.jambo199.vividmusic.playback

import android.net.Uri
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import com.jambo199.vividmusic.data.Song
import com.jambo199.vividmusic.data.SongSource
import org.json.JSONObject

/** Converts between [Song] and Media3 [MediaItem], keeping everything needed to rebuild the song. */
object SongItems {
    private const val K_MS_ID = "v.msId"
    private const val K_ARTIST = "v.artist"
    private const val K_ALBUM = "v.album"
    private const val K_ALBUM_ARTIST = "v.albumArtist"
    private const val K_ALBUM_ID = "v.albumId"
    private const val K_DURATION = "v.duration"
    private const val K_TRACK = "v.track"
    private const val K_YEAR = "v.year"
    private const val K_ADDED = "v.added"
    private const val K_MODIFIED = "v.modified"
    private const val K_SIZE = "v.size"
    private const val K_PATH = "v.path"
    private const val K_FOLDER = "v.folder"
    private const val K_FILE = "v.file"
    private const val K_MIME = "v.mime"
    private const val K_GENRE = "v.genre"
    private const val K_SOURCE = "v.source"
    private const val K_COVER = "v.cover"
    private const val K_LRC = "v.lrc"

    @OptIn(UnstableApi::class)
    fun toMediaItem(song: Song): MediaItem {
        val extras = Bundle().apply {
            putLong(K_MS_ID, song.mediaStoreId)
            putString(K_ARTIST, song.artist)
            putString(K_ALBUM, song.album)
            putString(K_ALBUM_ARTIST, song.albumArtist)
            putLong(K_ALBUM_ID, song.albumId)
            putLong(K_DURATION, song.durationMs)
            putInt(K_TRACK, song.trackNumber)
            putInt(K_YEAR, song.year)
            putLong(K_ADDED, song.dateAddedSec)
            putLong(K_MODIFIED, song.dateModifiedSec)
            putLong(K_SIZE, song.sizeBytes)
            putString(K_PATH, song.path)
            putString(K_FOLDER, song.folder)
            putString(K_FILE, song.fileName)
            putString(K_MIME, song.mimeType)
            putString(K_GENRE, song.genre)
            putString(K_SOURCE, song.source.name)
            song.coverHint?.let { putString(K_COVER, it.toString()) }
            song.lyricsHint?.let { putString(K_LRC, it.toString()) }
        }
        val metadata = MediaMetadata.Builder()
            .setTitle(song.title)
            .setArtist(song.displayArtist)
            .setAlbumTitle(song.album.ifBlank { null })
            .setAlbumArtist(song.albumArtist.ifBlank { null })
            .setGenre(song.genre.ifBlank { null })
            .setTrackNumber(song.track.takeIf { it > 0 })
            .setDiscNumber(song.disc.takeIf { it > 0 })
            .setReleaseYear(song.year.takeIf { it > 0 })
            .setDurationMs(song.durationMs.takeIf { it > 0 })
            // The service's bitmap loader turns the song uri into its artwork.
            .setArtworkUri(song.uri)
            .setIsPlayable(true)
            .setIsBrowsable(false)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .setExtras(extras)
            .build()
        return MediaItem.Builder()
            .setMediaId(song.key)
            .setUri(song.uri)
            .setMediaMetadata(metadata)
            .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(song.uri).build())
            .build()
    }

    @OptIn(UnstableApi::class)
    fun toSong(item: MediaItem): Song {
        val md = item.mediaMetadata
        val e = md.extras ?: Bundle.EMPTY
        val uri = item.localConfiguration?.uri ?: item.requestMetadata.mediaUri ?: Uri.parse(item.mediaId)
        val fileName = e.getString(K_FILE).orEmpty().ifBlank { uri.lastPathSegment.orEmpty().substringAfterLast('/') }
        return Song(
            key = item.mediaId,
            uri = uri,
            mediaStoreId = e.getLong(K_MS_ID, -1),
            title = md.title?.toString().orEmpty().ifBlank { fileName.substringBeforeLast('.') },
            artist = e.getString(K_ARTIST) ?: md.artist?.toString().orEmpty(),
            album = e.getString(K_ALBUM) ?: md.albumTitle?.toString().orEmpty(),
            albumArtist = e.getString(K_ALBUM_ARTIST) ?: md.albumArtist?.toString().orEmpty(),
            albumId = e.getLong(K_ALBUM_ID, 0),
            durationMs = e.getLong(K_DURATION, md.durationMs ?: 0L),
            trackNumber = e.getInt(K_TRACK, 0),
            year = e.getInt(K_YEAR, 0),
            dateAddedSec = e.getLong(K_ADDED, 0),
            dateModifiedSec = e.getLong(K_MODIFIED, 0),
            sizeBytes = e.getLong(K_SIZE, 0),
            path = e.getString(K_PATH).orEmpty(),
            folder = e.getString(K_FOLDER).orEmpty(),
            fileName = fileName,
            mimeType = e.getString(K_MIME).orEmpty(),
            genre = e.getString(K_GENRE).orEmpty(),
            source = e.getString(K_SOURCE)?.let { name -> SongSource.entries.firstOrNull { it.name == name } }
                ?: SongSource.EXTERNAL,
            coverHint = e.getString(K_COVER)?.let(Uri::parse),
            lyricsHint = e.getString(K_LRC)?.let(Uri::parse),
        )
    }

    fun toJson(song: Song): JSONObject = JSONObject()
        .put("key", song.key)
        .put("uri", song.uri.toString())
        .put("msId", song.mediaStoreId)
        .put("title", song.title)
        .put("artist", song.artist)
        .put("album", song.album)
        .put("albumArtist", song.albumArtist)
        .put("albumId", song.albumId)
        .put("duration", song.durationMs)
        .put("track", song.trackNumber)
        .put("year", song.year)
        .put("added", song.dateAddedSec)
        .put("modified", song.dateModifiedSec)
        .put("size", song.sizeBytes)
        .put("path", song.path)
        .put("folder", song.folder)
        .put("file", song.fileName)
        .put("mime", song.mimeType)
        .put("genre", song.genre)
        .put("source", song.source.name)
        .put("cover", song.coverHint?.toString() ?: "")
        .put("lrc", song.lyricsHint?.toString() ?: "")

    fun fromJson(o: JSONObject): Song = Song(
        key = o.getString("key"),
        uri = Uri.parse(o.getString("uri")),
        mediaStoreId = o.optLong("msId", -1),
        title = o.optString("title"),
        artist = o.optString("artist"),
        album = o.optString("album"),
        albumArtist = o.optString("albumArtist"),
        albumId = o.optLong("albumId"),
        durationMs = o.optLong("duration"),
        trackNumber = o.optInt("track"),
        year = o.optInt("year"),
        dateAddedSec = o.optLong("added"),
        dateModifiedSec = o.optLong("modified"),
        sizeBytes = o.optLong("size"),
        path = o.optString("path"),
        folder = o.optString("folder"),
        fileName = o.optString("file"),
        mimeType = o.optString("mime"),
        genre = o.optString("genre"),
        source = SongSource.entries.firstOrNull { it.name == o.optString("source") } ?: SongSource.EXTERNAL,
        coverHint = o.optString("cover").takeIf { it.isNotBlank() }?.let(Uri::parse),
        lyricsHint = o.optString("lrc").takeIf { it.isNotBlank() }?.let(Uri::parse),
    )
}

fun Song.toMediaItem(): MediaItem = SongItems.toMediaItem(this)
fun MediaItem.toSong(): Song = SongItems.toSong(this)
