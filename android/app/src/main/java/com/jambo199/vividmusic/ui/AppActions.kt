package com.jambo199.vividmusic.ui

import android.net.Uri
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation.NavHostController
import com.jambo199.vividmusic.data.Song

object Routes {
    const val LIBRARY = "library"
    const val NOW_PLAYING = "now_playing"
    const val SEARCH = "search"
    const val SETTINGS = "settings"
    const val EQUALIZER = "equalizer"
    const val ALBUM = "album/{id}"
    const val ARTIST = "artist/{name}"
    const val FOLDER = "folder/{key}"
    const val PLAYLIST = "playlist/{id}"
}

@Stable
class NavActions(private val nav: NavHostController) {
    fun album(id: Long) = nav.navigate("album/$id") { launchSingleTop = true }
    fun artist(name: String) = nav.navigate("artist/${Uri.encode(name)}") { launchSingleTop = true }
    fun folder(key: String) = nav.navigate("folder/${Uri.encode(key)}")
    fun playlist(id: Long) = nav.navigate("playlist/$id") { launchSingleTop = true }
    fun nowPlaying() = nav.navigate(Routes.NOW_PLAYING) { launchSingleTop = true }
    fun search() = nav.navigate(Routes.SEARCH) { launchSingleTop = true }
    fun settings() = nav.navigate(Routes.SETTINGS) { launchSingleTop = true }
    fun equalizer() = nav.navigate(Routes.EQUALIZER) { launchSingleTop = true }
    fun back() {
        nav.popBackStack()
    }

    /** Leaves the player and opens a library page. */
    fun fromPlayer(open: NavActions.() -> Unit) {
        if (nav.currentDestination?.route == Routes.NOW_PLAYING) nav.popBackStack()
        open()
    }
}

val LocalNav = staticCompositionLocalOf<NavActions> { error("NavActions not provided") }

/** Which song(s) the options sheet is open for, and where it was opened from. */
data class SongMenuRequest(
    val songs: List<Song>,
    val playlistId: Long? = null,
    val playlistIndex: Int = -1,
)

@Stable
class SongMenuState {
    var request by mutableStateOf<SongMenuRequest?>(null)
        private set

    fun open(song: Song, playlistId: Long? = null, playlistIndex: Int = -1) {
        request = SongMenuRequest(listOf(song), playlistId, playlistIndex)
    }

    fun openMany(songs: List<Song>) {
        if (songs.isNotEmpty()) request = SongMenuRequest(songs)
    }

    fun close() {
        request = null
    }

    /** Songs waiting for the "Add to playlist" dialog. */
    var playlistRequest by mutableStateOf<List<Song>?>(null)

    fun addToPlaylist(songs: List<Song>) {
        if (songs.isNotEmpty()) playlistRequest = songs
    }
}

val LocalSongMenu = staticCompositionLocalOf<SongMenuState> { error("SongMenuState not provided") }
