package com.jambo199.vividmusic.data

import android.content.Context
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

enum class ThemeMode(val label: String) { SYSTEM("Follow system"), LIGHT("Light"), DARK("Dark") }

enum class FolderViewMode { TREE, FLAT }

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
    val includeAllAudio: Boolean = false,
    val minDurationSec: Int = 10,
    val excludedFolders: Set<String> = emptySet(),
    val pickedFolders: List<String> = emptyList(),
    val songSort: SongSortField = SongSortField.TITLE,
    val songSortAscending: Boolean = true,
    val albumSort: AlbumSortField = AlbumSortField.TITLE,
    val albumSortAscending: Boolean = true,
    val artistSort: ArtistSortField = ArtistSortField.NAME,
    val artistSortAscending: Boolean = true,
    val folderViewMode: FolderViewMode = FolderViewMode.TREE,
    val folderSongOrder: FolderSongOrder = FolderSongOrder.FILE_NAME,
    val albumGrid: Boolean = true,
    val lastTab: Int = 0,
    val skipSilence: Boolean = false,
    val stopOnTaskRemoved: Boolean = false,
    val keepScreenOn: Boolean = false,
    val rememberSpeed: Boolean = true,
    val playbackSpeed: Float = 1f,
    val playbackPitch: Float = 1f,
    val swipeArtworkToSkip: Boolean = true,
    val showLyricsWhenAvailable: Boolean = false,
    val autoCheckUpdates: Boolean = true,
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

    private fun read(): Settings {
        val d = Settings()
        return Settings(
            themeMode = enumOr(prefs.getString("themeMode", null), d.themeMode),
            dynamicColor = prefs.getBoolean("dynamicColor", d.dynamicColor),
            pureBlack = prefs.getBoolean("pureBlack", d.pureBlack),
            accentIndex = prefs.getInt("accentIndex", d.accentIndex),
            includeAllAudio = prefs.getBoolean("includeAllAudio", d.includeAllAudio),
            minDurationSec = prefs.getInt("minDurationSec", d.minDurationSec),
            excludedFolders = prefs.getStringSet("excludedFolders", null)?.toSet() ?: d.excludedFolders,
            pickedFolders = readList("pickedFolders"),
            songSort = enumOr(prefs.getString("songSort", null), d.songSort),
            songSortAscending = prefs.getBoolean("songSortAscending", d.songSortAscending),
            albumSort = enumOr(prefs.getString("albumSort", null), d.albumSort),
            albumSortAscending = prefs.getBoolean("albumSortAscending", d.albumSortAscending),
            artistSort = enumOr(prefs.getString("artistSort", null), d.artistSort),
            artistSortAscending = prefs.getBoolean("artistSortAscending", d.artistSortAscending),
            folderViewMode = enumOr(prefs.getString("folderViewMode", null), d.folderViewMode),
            folderSongOrder = enumOr(prefs.getString("folderSongOrder", null), d.folderSongOrder),
            albumGrid = prefs.getBoolean("albumGrid", d.albumGrid),
            lastTab = prefs.getInt("lastTab", d.lastTab),
            skipSilence = prefs.getBoolean("skipSilence", d.skipSilence),
            stopOnTaskRemoved = prefs.getBoolean("stopOnTaskRemoved", d.stopOnTaskRemoved),
            keepScreenOn = prefs.getBoolean("keepScreenOn", d.keepScreenOn),
            rememberSpeed = prefs.getBoolean("rememberSpeed", d.rememberSpeed),
            playbackSpeed = prefs.getFloat("playbackSpeed", d.playbackSpeed),
            playbackPitch = prefs.getFloat("playbackPitch", d.playbackPitch),
            swipeArtworkToSkip = prefs.getBoolean("swipeArtworkToSkip", d.swipeArtworkToSkip),
            showLyricsWhenAvailable = prefs.getBoolean("showLyricsWhenAvailable", d.showLyricsWhenAvailable),
            autoCheckUpdates = prefs.getBoolean("autoCheckUpdates", d.autoCheckUpdates),
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
            .putBoolean("includeAllAudio", s.includeAllAudio)
            .putInt("minDurationSec", s.minDurationSec)
            .putStringSet("excludedFolders", s.excludedFolders)
            .putString("pickedFolders", JSONArray(s.pickedFolders).toString())
            .putString("songSort", s.songSort.name)
            .putBoolean("songSortAscending", s.songSortAscending)
            .putString("albumSort", s.albumSort.name)
            .putBoolean("albumSortAscending", s.albumSortAscending)
            .putString("artistSort", s.artistSort.name)
            .putBoolean("artistSortAscending", s.artistSortAscending)
            .putString("folderViewMode", s.folderViewMode.name)
            .putString("folderSongOrder", s.folderSongOrder.name)
            .putBoolean("albumGrid", s.albumGrid)
            .putInt("lastTab", s.lastTab)
            .putBoolean("skipSilence", s.skipSilence)
            .putBoolean("stopOnTaskRemoved", s.stopOnTaskRemoved)
            .putBoolean("keepScreenOn", s.keepScreenOn)
            .putBoolean("rememberSpeed", s.rememberSpeed)
            .putFloat("playbackSpeed", s.playbackSpeed)
            .putFloat("playbackPitch", s.playbackPitch)
            .putBoolean("swipeArtworkToSkip", s.swipeArtworkToSkip)
            .putBoolean("showLyricsWhenAvailable", s.showLyricsWhenAvailable)
            .putBoolean("autoCheckUpdates", s.autoCheckUpdates)
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
