package com.jambo199.vividmusic.data

import com.jambo199.vividmusic.BuildConfig
import org.json.JSONObject

/**
 * One JSON file with settings, playlists, favourites, history and linked lyrics. Songs are
 * stored by key and file path, so a backup still finds them after a reinstall or on a new
 * phone where the same files got different ids.
 */
object Backup {
    private const val FORMAT = "vivid-music-backup"

    data class Restored(val playlists: Int, val songsFound: Int, val songsMissing: Int)

    fun build(settings: SettingsStore, userData: UserDataStore, lyrics: LyricsRepository, library: Library): JSONObject {
        val data = userData.current
        val paths = JSONObject()
        UserDataStore.keysIn(data).forEach { k -> library.byKey[k]?.let { paths.put(k, it.path) } }
        return JSONObject()
            .put("format", FORMAT)
            .put("version", 1)
            .put("app", BuildConfig.VERSION_NAME)
            .put("created", System.currentTimeMillis())
            .put("settings", settings.export())
            .put("userData", UserDataStore.toJson(data))
            .put("lyricsLinks", lyrics.exportAssignments())
            .put("paths", paths)
    }

    /** Throws IllegalArgumentException with a readable message for files that aren't backups. */
    fun restore(json: JSONObject, settings: SettingsStore, userData: UserDataStore, lyrics: LyricsRepository, library: Library): Restored {
        require(json.optString("format") == FORMAT) { "That file isn't a Vivid Music backup" }
        val data = UserDataStore.parse(json.getJSONObject("userData"))
        val remap = matchSongs(UserDataStore.keysIn(data), json.optJSONObject("paths"), library)
        json.optJSONObject("settings")?.let { settings.restore(it) }
        userData.restore(UserDataStore.remap(data, remap))
        json.optJSONObject("lyricsLinks")?.let { lyrics.restoreAssignments(it, remap) }
        val keys = UserDataStore.keysIn(data)
        val found = keys.count { it in library.byKey || it in remap }
        return Restored(data.playlists.size, found, keys.size - found)
    }

    /** Old key to this phone's key, for songs whose id changed: same path, or else a unique file name. */
    fun matchSongs(keys: Set<String>, paths: JSONObject?, library: Library): Map<String, String> {
        if (paths == null) return emptyMap()
        val byPath = library.songs.associateBy { it.path }
        val byName = library.songs.groupBy { it.fileName }.filterValues { it.size == 1 }.mapValues { it.value[0] }
        val out = HashMap<String, String>()
        for (k in keys) {
            if (k in library.byKey) continue
            val path = paths.optString(k).takeIf { it.isNotEmpty() } ?: continue
            val song = byPath[path] ?: byName[path.substringAfterLast('/')] ?: continue
            out[k] = song.key
        }
        return out
    }
}
