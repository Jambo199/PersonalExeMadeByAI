package com.jambo199.vividmusic.ui.screens

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.jambo199.vividmusic.BuildConfig
import com.jambo199.vividmusic.data.ArtistSeparators
import com.jambo199.vividmusic.data.LibraryRepository
import com.jambo199.vividmusic.data.LibraryTab
import com.jambo199.vividmusic.data.Normalization
import com.jambo199.vividmusic.data.PlayerBackground
import com.jambo199.vividmusic.data.ThemeMode
import com.jambo199.vividmusic.ui.LocalNav
import com.jambo199.vividmusic.ui.MainViewModel
import com.jambo199.vividmusic.ui.components.LocalBottomPadding
import com.jambo199.vividmusic.ui.components.SectionHeader
import com.jambo199.vividmusic.ui.theme.AccentColors
import com.jambo199.vividmusic.update.UpdateState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val settings by viewModel.settings.collectAsState()
    val library by viewModel.library.collectAsState()
    val update by viewModel.update.collectAsState()
    val nav = LocalNav.current
    val context = LocalContext.current
    var themeDialog by remember { mutableStateOf(false) }
    var hiddenDialog by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf(false) }
    val backupSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) viewModel.exportBackup(uri)
    }
    val backupPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.restoreBackup(uri)
    }
    var minDuration by remember(settings.minDurationSec) { mutableFloatStateOf(settings.minDurationSec.toFloat()) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) viewModel.addPickedFolder(uri)
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Settings") },
            navigationIcon = {
                IconButton(onClick = { nav.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
            },
        )
        LazyColumn(contentPadding = PaddingValues(bottom = LocalBottomPadding.current + 16.dp)) {
            item { SectionHeader("Look") }
            item {
                SettingRow(Icons.Rounded.Brightness6, "Theme", settings.themeMode.label) { themeDialog = true }
            }
            item {
                SwitchRow(
                    Icons.Rounded.AutoAwesome,
                    "Colours from album art",
                    "The app takes its colours from the song that's playing",
                    settings.artworkColors,
                ) { v ->
                    viewModel.updateSettings { it.copy(artworkColors = v) }
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                item {
                    SwitchRow(
                        Icons.Rounded.ColorLens,
                        "Wallpaper colours",
                        if (settings.artworkColors) "Use Material You colours from your wallpaper when nothing is playing" else "Use Material You colours from your wallpaper",
                        settings.dynamicColor,
                    ) { v ->
                        viewModel.updateSettings { it.copy(dynamicColor = v) }
                    }
                }
            }
            if (!settings.dynamicColor || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                item {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text("Accent colour", style = MaterialTheme.typography.bodyLarge)
                        FlowRow(
                            Modifier.padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            AccentColors.forEachIndexed { i, (name, color) ->
                                val selected = settings.accentIndex == i
                                Box(
                                    Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                                        .clickable { viewModel.updateSettings { it.copy(accentIndex = i) } },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (selected) Icon(Icons.Rounded.Check, contentDescription = name, tint = Color.White)
                                }
                            }
                        }
                    }
                }
            }
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("Player background", style = MaterialTheme.typography.bodyLarge)
                    FlowRow(
                        Modifier.padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        PlayerBackground.entries
                            // Blurring needs Android 12.
                            .filter { it != PlayerBackground.BLUR || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S }
                            .forEach { option ->
                                FilterChip(
                                    selected = settings.playerBackground == option ||
                                        (option == PlayerBackground.GRADIENT && settings.playerBackground == PlayerBackground.BLUR && Build.VERSION.SDK_INT < Build.VERSION_CODES.S),
                                    onClick = { viewModel.updateSettings { it.copy(playerBackground = option) } },
                                    label = { Text(option.label) },
                                )
                            }
                    }
                }
            }
            item {
                SwitchRow(null, "Pure black", "True black background in dark mode (saves battery on AMOLED screens)", settings.pureBlack) { v ->
                    viewModel.updateSettings { it.copy(pureBlack = v) }
                }
            }

            item { SectionHeader("Library") }
            item {
                SwitchRow(
                    Icons.Rounded.LibraryMusic,
                    "Include all audio",
                    "Also show recordings, voice notes and other non-music audio",
                    settings.includeAllAudio,
                ) { v -> viewModel.updateSettings { it.copy(includeAllAudio = v) } }
            }
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("Hide songs shorter than ${minDuration.toInt()} seconds", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Skips ringtones and short clips",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Slider(
                        value = minDuration,
                        onValueChange = { minDuration = it },
                        onValueChangeFinished = { viewModel.updateSettings { it.copy(minDurationSec = minDuration.toInt()) } },
                        valueRange = 0f..120f,
                        steps = 23,
                    )
                }
            }
            item {
                SettingRow(Icons.Rounded.CreateNewFolder, "Add a folder", "Pick any folder (e.g. on an SD card) to include its music") {
                    picker.launch(null)
                }
            }
            items(settings.pickedFolders, key = { it }) { uri ->
                val name = remember(uri) { runCatching { LibraryRepository.treeUriToReadable(context, Uri.parse(uri)) }.getOrDefault(uri) }
                ListItem(
                    headlineContent = { Text(name) },
                    supportingContent = { Text("Added folder") },
                    leadingContent = { Icon(Icons.Rounded.FolderOpen, contentDescription = null) },
                    trailingContent = {
                        IconButton(onClick = { viewModel.removePickedFolder(uri) }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Remove folder")
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.padding(start = 24.dp),
                )
            }
            item {
                SettingRow(
                    Icons.Rounded.FolderOff,
                    "Hidden folders",
                    if (settings.excludedFolders.isEmpty()) "None – hide a folder from its ⋮ menu" else "${settings.excludedFolders.size} hidden",
                ) { hiddenDialog = true }
            }
            item {
                SettingRow(Icons.Rounded.Refresh, "Rescan library", "${library.songs.size} songs found") { viewModel.refreshLibrary() }
            }

            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("Split artists on", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "A song tagged \"A feat. B\" or \"A; B\" is listed under both artists. Turn on & or / only if your tags use them between names.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ArtistSeparators.OPTIONS.forEach { (sep, label) ->
                            val on = sep in settings.artistSeparators
                            FilterChip(
                                selected = on,
                                onClick = {
                                    viewModel.updateSettings { s ->
                                        s.copy(artistSeparators = if (on) s.artistSeparators - sep else s.artistSeparators + sep)
                                    }
                                },
                                label = { Text(label) },
                            )
                        }
                    }
                }
            }

            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("Library tabs", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Choose which tabs the Library page shows, and their order",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val shown = settings.libraryTabs
                    val order = shown + LibraryTab.entries.filter { it !in shown }
                    fun setTabs(tabs: List<LibraryTab>) = viewModel.updateSettings { it.copy(libraryTabs = tabs, libraryTab = 0) }
                    order.forEach { tab ->
                        val on = tab in shown
                        val index = shown.indexOf(tab)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = on,
                                // At least one tab stays.
                                enabled = !on || shown.size > 1,
                                onCheckedChange = { checked -> setTabs(if (checked) shown + tab else shown - tab) },
                            )
                            Text(tab.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            IconButton(
                                enabled = on && index > 0,
                                onClick = { setTabs(shown.toMutableList().apply { add(index - 1, removeAt(index)) }) },
                            ) { Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Move ${tab.label} up") }
                            IconButton(
                                enabled = on && index in 0 until shown.lastIndex,
                                onClick = { setTabs(shown.toMutableList().apply { add(index + 1, removeAt(index)) }) },
                            ) { Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Move ${tab.label} down") }
                        }
                    }
                }
            }

            item { SectionHeader("Playback") }
            item { SettingRow(Icons.Rounded.Equalizer, "Equalizer", "Bass, treble, presets and effects") { nav.equalizer() } }
            item {
                SwitchRow(Icons.Rounded.Timer, "Skip silence", "Jump over silent parts of tracks", settings.skipSilence) { v ->
                    viewModel.updateSettings { it.copy(skipSilence = v) }
                }
            }
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("Volume normalisation", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Evens out loudness between songs using their ReplayGain tags. Songs without tags play as they are.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Normalization.entries.forEach { option ->
                            FilterChip(
                                selected = settings.normalization == option,
                                onClick = { viewModel.updateSettings { it.copy(normalization = option) } },
                                label = { Text(option.label) },
                            )
                        }
                    }
                    if (settings.normalization != Normalization.OFF) {
                        DbSlider("Adjustment for tagged songs", settings.replayGainPreamp, -15f..15f) { v ->
                            viewModel.updateSettings { it.copy(replayGainPreamp = v) }
                        }
                        DbSlider("Adjustment for songs without tags", settings.replayGainUntagged, -15f..0f) { v ->
                            viewModel.updateSettings { it.copy(replayGainUntagged = v) }
                        }
                    }
                }
            }
            item {
                SwitchRow(null, "Fade on play and pause", "A quick fade instead of an abrupt start or stop", settings.fadePlayPause) { v ->
                    viewModel.updateSettings { it.copy(fadePlayPause = v) }
                }
            }
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("Fade between songs", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Fades out the end of each song and fades in the next",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0, 2, 4, 6, 8, 12).forEach { sec ->
                            FilterChip(
                                selected = settings.songFadeSec == sec,
                                onClick = { viewModel.updateSettings { it.copy(songFadeSec = sec) } },
                                label = { Text(if (sec == 0) "Off" else "$sec s") },
                            )
                        }
                    }
                }
            }
            item {
                SwitchRow(null, "Pause on repeat", "With repeat one on, stop at the end of the song instead of playing it again", settings.pauseOnRepeat) { v ->
                    viewModel.updateSettings { it.copy(pauseOnRepeat = v) }
                }
            }
            item {
                SwitchRow(
                    null,
                    "Continue where you left off",
                    "Audiobooks, mixes and other tracks over 15 minutes start again from where you stopped",
                    settings.rememberPositions,
                ) { v ->
                    viewModel.updateSettings { it.copy(rememberPositions = v) }
                }
            }
            item {
                SwitchRow(null, "Remember playback speed", "Keep speed and pitch between songs and restarts", settings.rememberSpeed) { v ->
                    viewModel.updateSettings { it.copy(rememberSpeed = v) }
                }
            }
            item {
                SwitchRow(null, "Swipe artwork to skip", "Swipe the album art left or right on the player", settings.swipeArtworkToSkip) { v ->
                    viewModel.updateSettings { it.copy(swipeArtworkToSkip = v) }
                }
            }
            item {
                SwitchRow(null, "Open lyrics automatically", "Show lyrics instead of artwork when the player opens", settings.showLyricsWhenAvailable) { v ->
                    viewModel.updateSettings { it.copy(showLyricsWhenAvailable = v) }
                }
            }
            item {
                SwitchRow(
                    null,
                    "Find lyrics online",
                    "When a song has no lyrics, look them up on LRCLIB (lrclib.net) as you open the lyrics. Only the song's title, artist, album and length are sent.",
                    settings.onlineLyrics,
                ) { v ->
                    viewModel.updateSettings { it.copy(onlineLyrics = v) }
                }
            }
            item {
                SwitchRow(
                    null,
                    "Lyrics on lock screen & Dynamic Island",
                    "Shows the line being sung in place of the artist on the lock screen, notification, Dynamic Island and car screens (songs with synced lyrics)",
                    settings.sessionLyrics,
                ) { v ->
                    viewModel.updateSettings { it.copy(sessionLyrics = v) }
                }
            }
            item {
                SwitchRow(null, "Keep screen on", "While the player screen is open", settings.keepScreenOn) { v ->
                    viewModel.updateSettings { it.copy(keepScreenOn = v) }
                }
            }
            item {
                SwitchRow(null, "Stop when app is swiped away", "Otherwise music keeps playing after you close the app", settings.stopOnTaskRemoved) { v ->
                    viewModel.updateSettings { it.copy(stopOnTaskRemoved = v) }
                }
            }

            item { SectionHeader("Background playback (vivo)") }
            item {
                val ignoring = remember { isIgnoringBatteryOptimizations(context) }
                SettingRow(
                    Icons.Rounded.BatteryChargingFull,
                    "Allow unrestricted battery use",
                    if (ignoring) "Done – Android won't stop playback to save battery" else "Recommended so vivo's battery saver doesn't stop your music",
                ) { requestIgnoreBatteryOptimizations(context) }
            }
            item {
                SettingRow(
                    null,
                    "vivo background & auto-start settings",
                    "If music stops when the screen is off, allow \"High background power consumption\" and auto-start for Vivid Music",
                ) { openVivoBackgroundSettings(context) }
            }

            item { SectionHeader("App updates") }
            item {
                val subtitle = when (val u = update) {
                    UpdateState.Checking -> "Checking…"
                    UpdateState.UpToDate -> "You have the latest version (${BuildConfig.VERSION_NAME})"
                    is UpdateState.Available -> "Version ${u.info.versionName} is available – tap to update"
                    is UpdateState.Downloading -> "Downloading version ${u.info.versionName}… ${(u.progress * 100).toInt()}%"
                    is UpdateState.Ready -> "Version ${u.info.versionName} downloaded – tap to install"
                    is UpdateState.Failed -> u.message
                    UpdateState.Idle -> "Installed: ${BuildConfig.VERSION_NAME}"
                }
                SettingRow(Icons.Rounded.SystemUpdate, "Check for updates", subtitle) {
                    when (update) {
                        is UpdateState.Available, is UpdateState.Ready -> viewModel.startUpdate()
                        is UpdateState.Failed -> if ((update as UpdateState.Failed).info != null) viewModel.startUpdate() else viewModel.checkForUpdates()
                        is UpdateState.Downloading, UpdateState.Checking -> Unit
                        else -> viewModel.checkForUpdates()
                    }
                }
            }
            item {
                SwitchRow(null, "Check automatically", "Look for a new version each time the app opens", settings.autoCheckUpdates) { v ->
                    viewModel.updateSettings { it.copy(autoCheckUpdates = v) }
                }
            }

            item { SectionHeader("Backup") }
            item {
                SettingRow(
                    Icons.Rounded.Backup,
                    "Back up",
                    "Save settings, playlists, favourites and listening history to a file",
                ) { backupSaver.launch("vivid-music-backup-${java.time.LocalDate.now()}.json") }
            }
            item {
                SettingRow(Icons.Rounded.Restore, "Restore", "Load a backup file, here or on a new phone") { confirmRestore = true }
            }

            item { SectionHeader("About") }
            item {
                SettingRow(Icons.Rounded.DeleteSweep, "Clear listening history", "Resets Recently played and Most played") { viewModel.clearHistory() }
            }
            item {
                SettingRow(Icons.Rounded.Info, "Vivid Music ${BuildConfig.VERSION_NAME}", "Your personal music player") {}
            }
        }
    }

    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text("Restore a backup?") },
            text = { Text("Your current settings, playlists, favourites and listening history are replaced by the ones in the backup.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRestore = false
                    backupPicker.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*"))
                }) { Text("Choose file") }
            },
            dismissButton = { TextButton(onClick = { confirmRestore = false }) { Text("Cancel") } },
        )
    }

    if (themeDialog) {
        AlertDialog(
            onDismissRequest = { themeDialog = false },
            title = { Text("Theme") },
            text = {
                Column {
                    ThemeMode.entries.forEach { mode ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.updateSettings { it.copy(themeMode = mode) }
                                    themeDialog = false
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = settings.themeMode == mode, onClick = null)
                            Text(mode.label, modifier = Modifier.padding(start = 12.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { themeDialog = false }) { Text("Close") } },
        )
    }

    if (hiddenDialog) {
        AlertDialog(
            onDismissRequest = { hiddenDialog = false },
            title = { Text("Hidden folders") },
            text = {
                if (settings.excludedFolders.isEmpty()) {
                    Text("No folders are hidden. Open a folder and choose ⋮ → Hide this folder.")
                } else {
                    Column {
                        settings.excludedFolders.sorted().forEach { path ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(LibraryRepository.prettyPath(path), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                TextButton(onClick = { viewModel.includeFolder(path) }) { Text("Show") }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { hiddenDialog = false }) { Text("Done") } },
        )
    }
}

@Composable
private fun SettingRow(icon: ImageVector?, title: String, subtitle: String? = null, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = if (subtitle != null) { { Text(subtitle) } } else null,
        leadingContent = { IconSlot(icon) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun SwitchRow(icon: ImageVector?, title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = if (subtitle != null) { { Text(subtitle) } } else null,
        leadingContent = { IconSlot(icon) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable { onChange(!checked) },
    )
}

@Composable
private fun IconSlot(icon: ImageVector?) {
    Box(Modifier.size(24.dp)) {
        if (icon != null) Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}

@SuppressLint("BatteryLife")
private fun requestIgnoreBatteryOptimizations(context: Context) {
    val intents = listOf(
        Intent(AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")),
        Intent(AndroidSettings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
        Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")),
    )
    for (intent in intents) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (_: Exception) {
        }
    }
}

/** vivo/iQOO keep background managers in their own apps; try the known screens, then app info. */
private fun openVivoBackgroundSettings(context: Context) {
    val candidates = listOf(
        ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
        ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"),
        ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
        ComponentName("com.vivo.abe", "com.vivo.applicationbehaviorengine.ui.ExcessivePowerManagerActivity"),
    )
    for (component in candidates) {
        try {
            context.startActivity(Intent().setComponent(component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (_: Exception) {
        }
    }
    try {
        context.startActivity(
            Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    } catch (_: Exception) {
    }
}

/** A labelled slider in whole decibels that saves when you let go. */
@Composable
private fun DbSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    var current by remember(value) { mutableFloatStateOf(value) }
    Column(Modifier.padding(top = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                (if (current > 0) "+" else "") + "%.0f dB".format(current),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = current,
            onValueChange = { current = Math.round(it).toFloat() },
            onValueChangeFinished = { onChange(current) },
            valueRange = range,
        )
    }
}
