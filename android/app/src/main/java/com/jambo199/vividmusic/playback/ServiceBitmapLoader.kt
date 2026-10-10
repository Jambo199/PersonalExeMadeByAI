package com.jambo199.vividmusic.playback

import android.graphics.Bitmap
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
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
 * Media items carry the song uri as their artwork uri; this resolves it to the real thumbnail.
 */
@OptIn(UnstableApi::class)
class ServiceBitmapLoader : BitmapLoader {
    private val executor: ListeningExecutorService =
        MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor())

    override fun supportsMimeType(mimeType: String): Boolean = Util.isBitmapFactorySupportedMimeType(mimeType)

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> = executor.submit(
        Callable {
            ArtworkLoader.decodeBytes(data, SIZE) ?: throw IOException("Could not decode artwork")
        },
    )

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> = executor.submit(
        Callable {
            val key = uri.toString()
            val song = AppGraph.library.findByKey(key) ?: externalSong(uri)
            AppGraph.artwork.load(song, SIZE) ?: throw IOException("No artwork")
        },
    )

    private fun externalSong(uri: Uri) = Song(
        key = uri.toString(), uri = uri, mediaStoreId = -1, title = "", artist = "", album = "",
        albumArtist = "", albumId = 0, durationMs = 0, trackNumber = 0, year = 0, dateAddedSec = 0,
        dateModifiedSec = 0, sizeBytes = 0, path = "", folder = "", fileName = "", mimeType = "",
        genre = "", source = if (uri.authority == "media") SongSource.MEDIA_STORE else SongSource.EXTERNAL,
    )

    companion object {
        private const val SIZE = 512
    }
}
