package com.jambo199.vividmusic.online

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment

/**
 * Saves online songs with Android's download manager, so downloads carry on in the background,
 * show progress in the notification shade, and land in the shared Music folder where the
 * library picks them up.
 */
object Downloads {
    enum class Status { NONE, QUEUED, RUNNING, DONE, FAILED }

    data class Info(val status: Status, val progress: Float)

    /**
     * Starts a download to [relativePath] inside the shared [directory]. Returns its id.
     * [quiet] skips the "download complete" notification (for saving many songs at once);
     * [wifiOnly] waits for Wi-Fi.
     */
    fun enqueue(
        context: Context,
        url: String,
        relativePath: String,
        title: String,
        directory: String = Environment.DIRECTORY_MUSIC,
        quiet: Boolean = false,
        wifiOnly: Boolean = false,
    ): Long {
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(title)
            .setDescription("Vivid Music")
            .addRequestHeader("User-Agent", Http.USER_AGENT)
            .setNotificationVisibility(
                if (quiet) DownloadManager.Request.VISIBILITY_VISIBLE else DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED,
            )
            .setDestinationInExternalPublicDir(directory, relativePath)
            .setAllowedOverMetered(!wifiOnly)
            .setAllowedOverRoaming(false)
        @Suppress("DEPRECATION")
        request.allowScanningByMediaScanner()
        return manager(context).enqueue(request)
    }

    /** Status of many downloads at once (one look-up per few hundred), by id. */
    fun infoAll(context: Context, ids: Collection<Long>): Map<Long, Info> {
        val out = HashMap<Long, Info>(ids.size)
        ids.chunked(400).forEach { chunk ->
            val query = DownloadManager.Query().setFilterById(*chunk.toLongArray())
            manager(context).query(query)?.use { c ->
                val idCol = c.getColumnIndexOrThrow(DownloadManager.COLUMN_ID)
                val statusCol = c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
                val totalCol = c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                val doneCol = c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                while (c.moveToNext()) {
                    val total = c.getLong(totalCol)
                    val progress = if (total > 0) (c.getLong(doneCol).toFloat() / total).coerceIn(0f, 1f) else 0f
                    out[c.getLong(idCol)] = when (c.getInt(statusCol)) {
                        DownloadManager.STATUS_SUCCESSFUL -> Info(Status.DONE, 1f)
                        DownloadManager.STATUS_FAILED -> Info(Status.FAILED, 0f)
                        DownloadManager.STATUS_RUNNING -> Info(Status.RUNNING, progress)
                        else -> Info(Status.QUEUED, progress)
                    }
                }
            }
        }
        return out
    }

    fun cancel(context: Context, id: Long) {
        manager(context).remove(id)
    }

    /** A file or folder name without characters Android's storage rejects. */
    fun safeName(text: String): String =
        text.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim().trim('.').take(80).ifBlank { "Untitled" }

    private fun manager(context: Context) = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
}
