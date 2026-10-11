package com.jambo199.vividmusic.playback

import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.util.LruCache
import androidx.annotation.OptIn
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.ListeningExecutorService
import com.google.common.util.concurrent.MoreExecutors
import com.jambo199.vividmusic.AppGraph
import com.jambo199.vividmusic.data.ArtworkLoader
import com.jambo199.vividmusic.data.Song
import com.jambo199.vividmusic.data.SongSource
import java.io.IOException
import java.util.concurrent.Callable
import java.util.concurrent.Executors

/**
 * Supplies artwork for the notification, lock screen and Bluetooth/car displays.
 * Media items carry their song key in the metadata extras (rather than an artwork uri, which
 * Android 14+ would try to validate against the audio file); this finds the real thumbnail.
 */
@OptIn(UnstableApi::class)
class ServiceBitmapLoader : BitmapLoader {
    private val executor: ListeningExecutorService =
        MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor())
    private val cache = LruCache<String, Bitmap>(4)

    override fun supportsMimeType(mimeType: String): Boolean = Util.isBitmapFactorySupportedMimeType(mimeType)

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> = executor.submit(
        Callable {
            ArtworkLoader.decodeBytes(data, SIZE) ?: throw IOException("Could not decode artwork")
        },
    )

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> = loadForKey(uri.toString(), uri)

    override fun loadBitmapFromMetadata(metadata: MediaMetadata): ListenableFuture<Bitmap>? {
        val key = metadata.extras?.getString(SongItems.K_KEY)
        if (key != null) {
            cache.get(key)?.let { return Futures.immediateFuture(it) }
            // Our own lookup first (system thumbnail, embedded art, folder image);
            // fall back to the picture the player decoded from the file's tags.
            val data = metadata.artworkData
            return executor.submit(
                Callable {
                    val song = AppGraph.library.findByKey(key) ?: externalSong(Uri.parse(key))
                    val bitmap = AppGraph.artwork.load(song, SIZE)
                        ?: data?.let { ArtworkLoader.decodeBytes(it, SIZE) }
                        ?: throw IOException("No artwork")
                    cache.put(key, bitmap)
                    Log.d(TAG, "Loaded artwork ${bitmap.width}x${bitmap.height} for the media session")
                    bitmap
                },
            )
        }
        metadata.artworkData?.let { return decodeBitmap(it) }
        metadata.artworkUri?.let { return loadBitmap(it) }
        return null
    }

    private fun loadForKey(key: String, uri: Uri): ListenableFuture<Bitmap> {
        cache.get(key)?.let { return Futures.immediateFuture(it) }
        return executor.submit(
            Callable {
                val song = AppGraph.library.findByKey(key) ?: externalSong(uri)
                val bitmap = AppGraph.artwork.load(song, SIZE) ?: throw IOException("No artwork")
                cache.put(key, bitmap)
                bitmap
            },
        )
    }

    private fun externalSong(uri: Uri) = Song(
        key = uri.toString(), uri = uri, mediaStoreId = -1, title = "", artist = "", album = "",
        albumArtist = "", albumId = 0, durationMs = 0, trackNumber = 0, year = 0, dateAddedSec = 0,
        dateModifiedSec = 0, sizeBytes = 0, path = "", folder = "", fileName = "", mimeType = "",
        genre = "", source = if (uri.authority == "media") SongSource.MEDIA_STORE else SongSource.EXTERNAL,
    )

    companion object {
        private const val TAG = "ServiceBitmapLoader"
        private const val SIZE = 512
    }
}
