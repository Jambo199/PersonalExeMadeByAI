package com.jambo199.vividmusic.online

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment

/**
 * Saves online songs and podcast episodes with Android's download manager, so downloads carry
 * on in the background, show progress in the notification shade, and land in the shared Music
 * (or Podcasts) folder where the library picks them up.
 */
object Downloads {
    enum class Status { NONE, QUEUED, RUNNING, DONE, FAILED }

    data class Info(val status: Status, val progress: Float, val localUri: Uri?)

    /** Starts a download to [relativePath] inside the shared [directory]. Returns its id. */
    fun enqueue(
        context: Context,
        url: String,
        relativePath: String,
        title: String,
        directory: String = Environment.DIRECTORY_MUSIC,
    ): Long {
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(title)
            .setDescription("Vivid Music")
            .addRequestHeader("User-Agent", Http.USER_AGENT)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(directory, relativePath)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
        @Suppress("DEPRECATION")
        request.allowScanningByMediaScanner()
        return manager(context).enqueue(request)
    }

    fun info(context: Context, id: Long): Info {
        val query = DownloadManager.Query().setFilterById(id)
        manager(context).query(query)?.use { c ->
            if (!c.moveToFirst()) return Info(Status.NONE, 0f, null)
            val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            val done = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val local = c.getString(c.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))
            val progress = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else 0f
            return when (status) {
                // The download provider's own address works for us without storage permission.
                DownloadManager.STATUS_SUCCESSFUL -> Info(Status.DONE, 1f, manager(context).getUriForDownloadedFile(id) ?: local?.let(Uri::parse))
                DownloadManager.STATUS_FAILED -> Info(Status.FAILED, 0f, null)
                DownloadManager.STATUS_RUNNING -> Info(Status.RUNNING, progress, null)
                else -> Info(Status.QUEUED, progress, null)
            }
        }
        return Info(Status.NONE, 0f, null)
    }

    fun cancel(context: Context, id: Long) {
        manager(context).remove(id)
    }

    /** A file or folder name without characters Android's storage rejects. */
    fun safeName(text: String): String =
        text.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim().trim('.').take(80).ifBlank { "Untitled" }

    private fun manager(context: Context) = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
}
