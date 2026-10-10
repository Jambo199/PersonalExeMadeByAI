package com.jambo199.vividmusic.data

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log

/** Reads every audio file Android's media index knows about. */
object MediaStoreScanner {
    private const val TAG = "MediaStoreScanner"

    fun scan(context: Context, includeAllAudio: Boolean): List<Song> {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val columns = buildList {
            add(MediaStore.Audio.Media._ID)
            add(MediaStore.Audio.Media.TITLE)
            add(MediaStore.Audio.Media.ARTIST)
            add(MediaStore.Audio.Media.ALBUM)
            add(MediaStore.Audio.Media.ALBUM_ID)
            add(MediaStore.Audio.Media.DURATION)
            add(MediaStore.Audio.Media.TRACK)
            add(MediaStore.Audio.Media.YEAR)
            add(MediaStore.Audio.Media.DATE_ADDED)
            add(MediaStore.Audio.Media.DATE_MODIFIED)
            add(MediaStore.Audio.Media.SIZE)
            add(MediaStore.Audio.Media.DATA)
            add(MediaStore.Audio.Media.DISPLAY_NAME)
            add(MediaStore.Audio.Media.MIME_TYPE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add("relative_path")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                add("album_artist")
                add("genre")
            }
        }.toTypedArray()

        val selection = if (includeAllAudio) null else "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val songs = ArrayList<Song>()
        try {
            context.contentResolver.query(collection, columns, selection, null, null)?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val durationCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val trackCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
                val yearCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
                val addedCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                val modifiedCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
                val sizeCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val dataCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val nameCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val mimeCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                val relCol = c.getColumnIndex("relative_path")
                val albumArtistCol = c.getColumnIndex("album_artist")
                val genreCol = c.getColumnIndex("genre")

                while (c.moveToNext()) {
                    val id = c.getLong(idCol)
                    val uri = ContentUris.withAppendedId(collection, id)
                    val displayName = c.str(nameCol)
                    val path = c.str(dataCol).ifBlank {
                        val rel = if (relCol >= 0) c.str(relCol) else ""
                        Environment.getExternalStorageDirectory().absolutePath + "/" + rel + displayName
                    }
                    val fileName = displayName.ifBlank { path.substringAfterLast('/') }
                    val folder = path.substringBeforeLast('/', "").ifBlank { "/" }
                    songs += Song(
                        key = uri.toString(),
                        uri = uri,
                        mediaStoreId = id,
                        title = c.str(titleCol).ifBlank { fileName.substringBeforeLast('.') },
                        artist = cleanTag(c.str(artistCol)),
                        album = cleanTag(c.str(albumCol)),
                        albumArtist = if (albumArtistCol >= 0) cleanTag(c.str(albumArtistCol)) else "",
                        albumId = c.getLong(albumIdCol),
                        durationMs = c.getLong(durationCol),
                        trackNumber = c.getInt(trackCol),
                        year = c.getInt(yearCol),
                        dateAddedSec = c.getLong(addedCol),
                        dateModifiedSec = c.getLong(modifiedCol),
                        sizeBytes = c.getLong(sizeCol),
                        path = path,
                        folder = folder,
                        fileName = fileName,
                        mimeType = c.str(mimeCol),
                        genre = if (genreCol >= 0) cleanTag(c.str(genreCol)) else "",
                        source = SongSource.MEDIA_STORE,
                    )
                }
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "No permission to read the media library", e)
        } catch (e: Exception) {
            Log.e(TAG, "Media library query failed", e)
        }
        return songs
    }

    private fun Cursor.str(index: Int): String = if (index < 0 || isNull(index)) "" else getString(index) ?: ""

    /** MediaStore reports missing tags as "<unknown>". */
    fun cleanTag(value: String): String {
        val v = value.trim()
        return if (v.equals("<unknown>", ignoreCase = true)) "" else v
    }
}
