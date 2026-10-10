package com.jambo199.vividmusic.data

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Size
import androidx.core.graphics.drawable.toDrawable
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.key.Keyer
import coil.request.Options
import coil.size.pxOrElse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * Finds the best thumbnail for a song: the system thumbnail (embedded art or folder image),
 * embedded art read directly from the file, or a cover image in the same folder.
 */
class ArtworkLoader(private val context: Context) {
    private val misses: MutableSet<String> = ConcurrentHashMap.newKeySet()

    fun clearMisses() = misses.clear()

    /** Returns a software bitmap at least roughly [sizePx] wide, or null when the song has no art. */
    fun load(song: Song, sizePx: Int): Bitmap? {
        val size = sizePx.coerceIn(64, 1600)
        val missKey = song.key + "@" + song.dateModifiedSec
        if (missKey in misses) return null
        val bitmap = try {
            loadInternal(song, size)
        } catch (e: OutOfMemoryError) {
            null
        } catch (e: Exception) {
            null
        }
        if (bitmap == null) misses += missKey
        return bitmap?.let(::ensureSoftware)
    }

    private fun loadInternal(song: Song, size: Int): Bitmap? = when (song.source) {
        SongSource.MEDIA_STORE -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                systemThumbnail(song.uri, size) ?: folderCover(song, size)
            } else {
                albumArt(song.albumId, size) ?: embedded(song.uri, size) ?: folderCover(song, size)
            }
        }
        SongSource.FOLDER, SongSource.EXTERNAL -> embedded(song.uri, size)
            ?: song.coverHint?.let { decodeUri(it, size) }
            ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && song.uri.authority == "media") systemThumbnail(song.uri, size) else null
    }

    private fun systemThumbnail(uri: Uri, size: Int): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return try {
            context.contentResolver.loadThumbnail(uri, Size(size, size), null)
        } catch (e: Exception) {
            null
        }
    }

    private fun albumArt(albumId: Long, size: Int): Bitmap? {
        if (albumId <= 0) return null
        return decodeUri(ContentUris.withAppendedId(ALBUM_ART, albumId), size)
    }

    fun embeddedBytes(uri: Uri): ByteArray? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.embeddedPicture
        } catch (e: Exception) {
            null
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun embedded(uri: Uri, size: Int): Bitmap? = embeddedBytes(uri)?.let { decodeBytes(it, size) }

    private fun folderCover(song: Song, size: Int): Bitmap? {
        val dir = File(song.folder)
        for (name in COVER_FILES) {
            val f = File(dir, name)
            try {
                if (f.isFile && f.canRead()) {
                    decodeFile(f, size)?.let { return it }
                }
            } catch (_: SecurityException) {
                return null
            }
        }
        return null
    }

    private fun decodeUri(uri: Uri, size: Int): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0) {
            null
        } else {
            val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, size) }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        }
    } catch (e: Exception) {
        null
    }

    private fun decodeFile(file: File, size: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0) return null
        val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, size) }
        return BitmapFactory.decodeFile(file.absolutePath, opts)
    }

    companion object {
        private val ALBUM_ART: Uri = Uri.parse("content://media/external/audio/albumart")
        private val COVER_FILES = listOf(
            "cover.jpg", "cover.png", "folder.jpg", "folder.png", "front.jpg", "front.png",
            "album.jpg", "album.png", "AlbumArt.jpg", "albumart.jpg", "Cover.jpg", "Folder.jpg",
        )

        fun decodeBytes(bytes: ByteArray, size: Int): Bitmap? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0) return null
            val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, size) }
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        }

        private fun sampleSize(width: Int, height: Int, target: Int): Int {
            var sample = 1
            while (width / (sample * 2) >= target && height / (sample * 2) >= target) sample *= 2
            return sample
        }

        /** Hardware bitmaps can't be read by Palette or sent through notifications. */
        fun ensureSoftware(bitmap: Bitmap): Bitmap =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && bitmap.config == Bitmap.Config.HARDWARE) {
                bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: bitmap
            } else {
                bitmap
            }
    }
}

/** Lets Coil load artwork straight from a [Song]. */
class SongArtworkFetcher(
    private val song: Song,
    private val options: Options,
    private val loader: ArtworkLoader,
) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val w = options.size.width.pxOrElse { 512 }
        val h = options.size.height.pxOrElse { 512 }
        val bitmap = withContext(Dispatchers.IO) { loader.load(song, maxOf(w, h)) }
            ?: throw IOException("No artwork for ${song.fileName}")
        return DrawableResult(
            drawable = bitmap.toDrawable(options.context.resources),
            isSampled = true,
            dataSource = DataSource.DISK,
        )
    }

    class Factory(private val loader: ArtworkLoader) : Fetcher.Factory<Song> {
        override fun create(data: Song, options: Options, imageLoader: ImageLoader): Fetcher =
            SongArtworkFetcher(data, options, loader)
    }
}

class SongArtworkKeyer : Keyer<Song> {
    override fun key(data: Song, options: Options): String = "art:${data.key}:${data.dateModifiedSec}"
}
