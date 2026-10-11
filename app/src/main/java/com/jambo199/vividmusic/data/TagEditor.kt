package com.jambo199.vividmusic.data

import android.app.RecoverableSecurityException
import android.content.Context
import android.content.IntentSender
import android.media.MediaScannerConnection
import android.os.Build
import android.util.Log
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.audio.exceptions.CannotReadException
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.TagOptionSingleton
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
    companion object {
        fun of(song: Song) = TagEdits(
            title = song.title,
            artist = song.artist,
            album = song.album,
            albumArtist = song.albumArtist,
            genre = song.genre,
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

    fun write(context: Context, song: Song, edits: TagEdits): Result {
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

    private fun Tag.put(key: FieldKey, value: String) {
        val v = value.trim()
        try {
            if (v.isEmpty()) deleteField(key) else setField(key, v)
        } catch (e: Exception) {
            // Some formats don't have every field (e.g. album artist in old WAV); skip those.
        }
    }
}
