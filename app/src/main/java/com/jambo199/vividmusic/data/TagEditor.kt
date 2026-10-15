package com.jambo199.vividmusic.data

import android.app.RecoverableSecurityException
import android.content.Context
import android.content.IntentSender
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.os.Build
import android.util.Log
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.audio.exceptions.CannotReadException
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.TagOptionSingleton
import org.jaudiotagger.tag.images.AndroidArtwork
import org.jaudiotagger.tag.reference.PictureTypes
import java.io.File

/** The tags the editor can change. Blank fields are removed from the file. */
data class TagEdits(
    val title: String,
    val artist: String,
    val album: String,
    val albumArtist: String,
    val genre: String,
    val year: String,
    val track: String,
) {
    /**
     * These tags with [m]'s filled in: all of them, or with [fillOnly] just the missing ones. A
     * title that's only the file's name ([fileBase]) and placeholders like "<unknown>" count as missing.
     */
    fun with(m: TagMatch, fillOnly: Boolean, fileBase: String = ""): TagEdits {
        fun missing(old: String) = !TagText.known(old)
        fun pick(old: String, new: String, isMissing: Boolean = missing(old)) =
            if (new.isBlank() || (fillOnly && !isMissing)) old else new.trim()
        return TagEdits(
            title = pick(title, m.title, missing(title) || title.equals(fileBase, ignoreCase = true)),
            artist = pick(artist, m.artist),
            album = pick(album, m.album),
            albumArtist = pick(albumArtist, m.albumArtist),
            genre = pick(genre, m.genre),
            year = pick(year, m.year),
            track = pick(track, m.track),
        )
    }

    companion object {
        fun of(song: Song) = TagEdits(
            title = song.title,
            artist = song.artist.takeIf(TagText::known).orEmpty(),
            album = song.album.takeIf(TagText::known).orEmpty(),
            albumArtist = song.albumArtist.takeIf(TagText::known).orEmpty(),
            genre = song.genre.takeIf(TagText::known).orEmpty(),
            year = song.year.takeIf { it > 0 }?.toString().orEmpty(),
            track = song.track.takeIf { it > 0 }?.toString().orEmpty(),
        )
    }
}

/**
 * Writes tags into the audio file itself (AIMP-style tag editor). The file is copied to a
 * scratch file, edited there with jaudiotagger, then written back over the original, so a failed
 * edit never leaves a half-changed file.
 */
object TagEditor {
    sealed interface Result {
        data object Done : Result
        data class NeedsPermission(val intentSender: IntentSender) : Result
        data class Failed(val message: String) : Result
    }

    private val EDITABLE = setOf("mp3", "m4a", "mp4", "m4b", "aac", "flac", "ogg", "oga", "opus", "wma", "aif", "aiff", "dsf", "wav")

    fun canEdit(song: Song): Boolean =
        (song.source == SongSource.MEDIA_STORE || song.source == SongSource.FOLDER) &&
            song.fileName.substringAfterLast('.', "").lowercase() in EDITABLE

    init {
        // Use Android's image classes instead of the desktop ones.
        TagOptionSingleton.getInstance().isAndroid = true
    }

    /** Writes [edits] (and [artwork], a JPEG or PNG, as the cover when given) into the song's file. */
    fun write(context: Context, song: Song, edits: TagEdits, artwork: ByteArray? = null): Result {
        val ext = song.fileName.substringAfterLast('.', "").lowercase()
        val dir = File(context.cacheDir, "tag_edit").apply { mkdirs() }
        val temp = File(dir, "edit.$ext")
        return try {
            context.contentResolver.openInputStream(song.uri)?.use { input -> temp.outputStream().use { input.copyTo(it) } }
                ?: return Result.Failed("Couldn't open the file")
            val audio = AudioFileIO.read(temp)
            val tag = audio.tagOrCreateAndSetDefault
            tag.put(FieldKey.TITLE, edits.title)
            tag.put(FieldKey.ARTIST, edits.artist)
            tag.put(FieldKey.ALBUM, edits.album)
            tag.put(FieldKey.ALBUM_ARTIST, edits.albumArtist)
            tag.put(FieldKey.GENRE, edits.genre)
            tag.put(FieldKey.YEAR, edits.year.filter(Char::isDigit))
            tag.put(FieldKey.TRACK, edits.track.filter(Char::isDigit))
            if (artwork != null) {
                try {
                    tag.deleteArtworkField()
                } catch (_: Exception) {
                }
                tag.setField(CoverArt(artwork))
            }
            audio.commit()
            context.contentResolver.openOutputStream(song.uri, "wt")?.use { out -> temp.inputStream().use { it.copyTo(out) } }
                ?: return Result.Failed("Couldn't write the file")
            // Let Android's media index read the new tags.
            if (song.source == SongSource.MEDIA_STORE && song.path.isNotBlank()) {
                MediaScannerConnection.scanFile(context, arrayOf(song.path), null, null)
            }
            Result.Done
        } catch (e: SecurityException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && e is RecoverableSecurityException) {
                Result.NeedsPermission(e.userAction.actionIntent.intentSender)
            } else {
                Result.Failed("Android didn't allow changing this file")
            }
        } catch (e: CannotReadException) {
            Result.Failed("This kind of file can't be edited")
        } catch (e: Exception) {
            Log.w("TagEditor", "Couldn't save tags", e)
            Result.Failed("Couldn't save the tags (${e.javaClass.simpleName})")
        } finally {
            temp.delete()
        }
    }

    /**
     * A cover for jaudiotagger. Its Android picture class can't measure an image, which FLAC and
     * Ogg covers need, so this one measures it with Android's own decoder.
     */
    private class CoverArt(bytes: ByteArray) : AndroidArtwork() {
        init {
            binaryData = bytes
            mimeType = if (bytes.size > 1 && bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte()) "image/png" else "image/jpeg"
            pictureType = PictureTypes.DEFAULT_ID
            description = ""
        }

        override fun setImageFromData(): Boolean {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(binaryData, 0, binaryData.size, bounds)
            width = bounds.outWidth
            height = bounds.outHeight
            return bounds.outWidth > 0
        }
    }

    private fun Tag.put(key: FieldKey, value: String) {
        val v = value.trim()
        try {
            if (v.isEmpty()) deleteField(key) else setField(key, v)
        } catch (e: Exception) {
            // Some formats don't have every field (e.g. album artist in old WAV); skip those.
        }
    }
}
