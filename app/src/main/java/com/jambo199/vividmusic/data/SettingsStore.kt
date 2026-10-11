package com.jambo199.vividmusic.data

import android.content.Context
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

enum class ThemeMode(val label: String) { SYSTEM("Follow system"), LIGHT("Light"), DARK("Dark") }

enum class FolderViewMode { TREE, FLAT }

enum class PlayerBackground(val label: String) { BLUR("Blurred artwork"), GRADIENT("Colour gradient"), PLAIN("Plain") }

/** The pages in the bottom navigation bar. */
enum class MainSection(val label: String) { HOME("Home"), LIBRARY("Library"), FOLDERS("Folders"), ONLINE("Online") }

data class EqSettings(
    val enabled: Boolean = false,
    /** Index of the device preset, or -1 for custom band levels. */
    val preset: Int = -1,
    /** Band levels in millibels, one per band. Empty means flat. */
    val bandLevels: List<Int> = emptyList(),
    /** 0..1000 */
    val bassBoost: Int = 0,
    /** 0..1000 */
    val virtualizer: Int = 0,
    /** Extra loudness in millibels, 0..1500 */
    val loudnessGain: Int = 0,
)

data class Settings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
    val pureBlack: Boolean = false,
    val accentIndex: Int = 0,
    /** Take the app's colours from the playing song's artwork. */
    val artworkColors: Boolean = true,
    val playerBackground: PlayerBackground = PlayerBackground.BLUR,
    val includeAllAudio: Boolean = false,
    val minDurationSec: Int = 10,
    val excludedFolders: Set<String> = emptySet(),
    val pickedFolders: List<String> = emptyList(),
    /** Splits artist tags into several artists; see [ArtistSeparators]. */
    val artistSeparators: Set<String> = ArtistSeparators.DEFAULT,
    val songSort: SongSortField = SongSortField.TITLE,
    val songSortAscending: Boolean = true,
    val albumSort: AlbumSortField = AlbumSortField.TITLE,
    val albumSortAscending: Boolean = true,
    val artistSort: ArtistSortField = ArtistSortField.NAME,
    val artistSortAscending: Boolean = true,
    val folderViewMode: FolderViewMode = FolderViewMode.TREE,
    val folderSongOrder: FolderSongOrder = FolderSongOrder.FILE_NAME,
    val albumGrid: Boolean = true,
    val section: MainSection = MainSection.HOME,
    /** Songs, Albums, Artists or Playlists inside the Library page. */
    val libraryTab: Int = 0,
    val skipSilence: Boolean = false,
    val normalization: Normalization = Normalization.OFF,
    /** Added to ReplayGain for tagged songs, in dB. */
    val replayGainPreamp: Float = 0f,
    /** Applied to songs without ReplayGain tags, in dB. */
    val replayGainUntagged: Float = 0f,
    val fadePlayPause: Boolean = true,
    /** Seconds to fade out the end of a song and fade in the next, 0 for off. */
    val songFadeSec: Int = 0,
    /** Carry on podcasts, audiobooks and other long tracks where they were left. */
    val rememberPositions: Boolean = true,
    val stopOnTaskRemoved: Boolean = false,
    val keepScreenOn: Boolean = false,
    val rememberSpeed: Boolean = true,
    val playbackSpeed: Float = 1f,
    val playbackPitch: Float = 1f,
    val swipeArtworkToSkip: Boolean = true,
    val showLyricsWhenAvailable: Boolean = false,
    /** Look up lyrics on LRCLIB when you open the lyrics of a song that has none. */
    val onlineLyrics: Boolean = true,
    val autoCheckUpdates: Boolean = true,
    /** Most recent first. */
    val searchHistory: List<String> = emptyList(),
    val eq: EqSettings = EqSettings(),
)

/** Settings backed by SharedPreferences and exposed as a StateFlow so every screen reacts. */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<Settings> = _state.asStateFlow()
    val current: Settings get() = _state.value

    @Synchronized
    fun update(transform: (Settings) -> Settings) {
        val old = _state.value
        val new = transform(old)
        if (new == old) return
        _state.value = new
        write(new)
    }

    fun addPickedFolder(uri: String) = update {
        if (uri in it.pickedFolders) it else it.copy(pickedFolders = it.pickedFolders + uri)
    }

    fun removePickedFolder(uri: String) = update { it.copy(pickedFolders = it.pickedFolders - uri) }

    /** Every saved setting with its type, for a backup. */
    fun export(): JSONObject {
        val out = JSONObject()
        prefs.all.forEach { (key, value) ->
            val entry = when (value) {
                is Boolean -> JSONObject().put("t", "b").put("v", value)
                is Int -> JSONObject().put("t", "i").put("v", value)
                is Long -> JSONObject().put("t", "l").put("v", value)
                is Float -> JSONObject().put("t", "f").put("v", value.toDouble())
                is String -> JSONObject().put("t", "s").put("v", value)
                is Set<*> -> JSONObject().put("t", "set").put("v", JSONArray(value.map { it.toString() }))
                else -> null
            }
            if (entry != null) out.put(key, entry)
        }
        return out
    }

    /** Replaces the settings with ones from a backup. */
    @Synchronized
    fun restore(saved: JSONObject) {
        val edit = prefs.edit().clear()
        saved.keys().forEach { key ->
            val e = saved.optJSONObject(key) ?: return@forEach
            when (e.optString("t")) {
                "b" -> edit.putBoolean(key, e.optBoolean("v"))
                "i" -> edit.putInt(key, e.optInt("v"))
                "l" -> edit.putLong(key, e.optLong("v"))
                "f" -> edit.putFloat(key, e.optDouble("v").toFloat())
                "s" -> edit.putString(key, e.optString("v"))
                "set" -> edit.putStringSet(key, e.optJSONArray("v").toStringList().toSet())
            }
        }
        edit.commit()
        _state.value = read()
    }

    private fun read(): Settings {
        val d = Settings()
        return Settings(
            themeMode = enumOr(prefs.getString("themeMode", null), d.themeMode),
            dynamicColor = prefs.getBoolean("dynamicColor", d.dynamicColor),
            pureBlack = prefs.getBoolean("pureBlack", d.pureBlack),
            accentIndex = prefs.getInt("accentIndex", d.accentIndex),
            artworkColors = prefs.getBoolean("artworkColors", d.artworkColors),
            playerBackground = enumOr(prefs.getString("playerBackground", null), d.playerBackground),
            includeAllAudio = prefs.getBoolean("includeAllAudio", d.includeAllAudio),
            minDurationSec = prefs.getInt("minDurationSec", d.minDurationSec),
            excludedFolders = prefs.getStringSet("excludedFolders", null)?.toSet() ?: d.excludedFolders,
            pickedFolders = readList("pickedFolders"),
            artistSeparators = prefs.getStringSet("artistSeparators", null)?.toSet() ?: d.artistSeparators,
            songSort = enumOr(prefs.getString("songSort", null), d.songSort),
            songSortAscending = prefs.getBoolean("songSortAscending", d.songSortAscending),
            albumSort = enumOr(prefs.getString("albumSort", null), d.albumSort),
            albumSortAscending = prefs.getBoolean("albumSortAscending", d.albumSortAscending),
            artistSort = enumOr(prefs.getString("artistSort", null), d.artistSort),
            artistSortAscending = prefs.getBoolean("artistSortAscending", d.artistSortAscending),
            folderViewMode = enumOr(prefs.getString("folderViewMode", null), d.folderViewMode),
            folderSongOrder = enumOr(prefs.getString("folderSongOrder", null), d.folderSongOrder),
            albumGrid = prefs.getBoolean("albumGrid", d.albumGrid),
            section = enumOr(prefs.getString("section", null), d.section),
            libraryTab = prefs.getInt("libraryTab", d.libraryTab),
            skipSilence = prefs.getBoolean("skipSilence", d.skipSilence),
            normalization = enumOr(prefs.getString("normalization", null), d.normalization),
            replayGainPreamp = prefs.getFloat("replayGainPreamp", d.replayGainPreamp),
            replayGainUntagged = prefs.getFloat("replayGainUntagged", d.replayGainUntagged),
            fadePlayPause = prefs.getBoolean("fadePlayPause", d.fadePlayPause),
            songFadeSec = prefs.getInt("songFadeSec", d.songFadeSec),
            rememberPositions = prefs.getBoolean("rememberPositions", d.rememberPositions),
            stopOnTaskRemoved = prefs.getBoolean("stopOnTaskRemoved", d.stopOnTaskRemoved),
            keepScreenOn = prefs.getBoolean("keepScreenOn", d.keepScreenOn),
            rememberSpeed = prefs.getBoolean("rememberSpeed", d.rememberSpeed),
            playbackSpeed = prefs.getFloat("playbackSpeed", d.playbackSpeed),
            playbackPitch = prefs.getFloat("playbackPitch", d.playbackPitch),
            swipeArtworkToSkip = prefs.getBoolean("swipeArtworkToSkip", d.swipeArtworkToSkip),
            showLyricsWhenAvailable = prefs.getBoolean("showLyricsWhenAvailable", d.showLyricsWhenAvailable),
            onlineLyrics = prefs.getBoolean("onlineLyrics", d.onlineLyrics),
            autoCheckUpdates = prefs.getBoolean("autoCheckUpdates", d.autoCheckUpdates),
            searchHistory = readList("searchHistory"),
            eq = EqSettings(
                enabled = prefs.getBoolean("eq.enabled", false),
                preset = prefs.getInt("eq.preset", -1),
                bandLevels = prefs.getString("eq.bands", "").orEmpty()
                    .split(',').mapNotNull { it.trim().toIntOrNull() },
                bassBoost = prefs.getInt("eq.bass", 0),
                virtualizer = prefs.getInt("eq.virtualizer", 0),
                loudnessGain = prefs.getInt("eq.loudness", 0),
            ),
        )
    }

    private fun write(s: Settings) {
        prefs.edit()
            .putString("themeMode", s.themeMode.name)
            .putBoolean("dynamicColor", s.dynamicColor)
            .putBoolean("pureBlack", s.pureBlack)
            .putInt("accentIndex", s.accentIndex)
            .putBoolean("artworkColors", s.artworkColors)
            .putString("playerBackground", s.playerBackground.name)
            .putBoolean("includeAllAudio", s.includeAllAudio)
            .putInt("minDurationSec", s.minDurationSec)
            .putStringSet("excludedFolders", s.excludedFolders)
            .putString("pickedFolders", JSONArray(s.pickedFolders).toString())
            .putStringSet("artistSeparators", s.artistSeparators)
            .putString("songSort", s.songSort.name)
            .putBoolean("songSortAscending", s.songSortAscending)
            .putString("albumSort", s.albumSort.name)
            .putBoolean("albumSortAscending", s.albumSortAscending)
            .putString("artistSort", s.artistSort.name)
            .putBoolean("artistSortAscending", s.artistSortAscending)
            .putString("folderViewMode", s.folderViewMode.name)
            .putString("folderSongOrder", s.folderSongOrder.name)
            .putBoolean("albumGrid", s.albumGrid)
            .putString("section", s.section.name)
            .putInt("libraryTab", s.libraryTab)
            .putBoolean("skipSilence", s.skipSilence)
            .putString("normalization", s.normalization.name)
            .putFloat("replayGainPreamp", s.replayGainPreamp)
            .putFloat("replayGainUntagged", s.replayGainUntagged)
            .putBoolean("fadePlayPause", s.fadePlayPause)
            .putInt("songFadeSec", s.songFadeSec)
            .putBoolean("rememberPositions", s.rememberPositions)
            .putBoolean("stopOnTaskRemoved", s.stopOnTaskRemoved)
            .putBoolean("keepScreenOn", s.keepScreenOn)
            .putBoolean("rememberSpeed", s.rememberSpeed)
            .putFloat("playbackSpeed", s.playbackSpeed)
            .putFloat("playbackPitch", s.playbackPitch)
            .putBoolean("swipeArtworkToSkip", s.swipeArtworkToSkip)
            .putBoolean("showLyricsWhenAvailable", s.showLyricsWhenAvailable)
            .putBoolean("onlineLyrics", s.onlineLyrics)
            .putBoolean("autoCheckUpdates", s.autoCheckUpdates)
            .putString("searchHistory", JSONArray(s.searchHistory).toString())
            .putBoolean("eq.enabled", s.eq.enabled)
            .putInt("eq.preset", s.eq.preset)
            .putString("eq.bands", s.eq.bandLevels.joinToString(","))
            .putInt("eq.bass", s.eq.bassBoost)
            .putInt("eq.virtualizer", s.eq.virtualizer)
            .putInt("eq.loudness", s.eq.loudnessGain)
            .apply()
    }

    private fun readList(key: String): List<String> = try {
        val json = prefs.getString(key, null) ?: return emptyList()
        val arr = JSONArray(json)
        List(arr.length()) { arr.getString(it) }
    } catch (e: Exception) {
        emptyList()
    }

    private inline fun <reified T : Enum<T>> enumOr(name: String?, default: T): T =
        name?.let { n -> enumValues<T>().firstOrNull { it.name == n } } ?: default
}
