package com.jambo199.vividmusic.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FiberNew
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.GetApp
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.SdStorage
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jambo199.vividmusic.data.FolderNode
import com.jambo199.vividmusic.data.FolderViewMode
import com.jambo199.vividmusic.data.Library
import com.jambo199.vividmusic.data.NaturalOrderComparator
import com.jambo199.vividmusic.data.SmartPlaylists
import com.jambo199.vividmusic.data.pluralize
import com.jambo199.vividmusic.ui.LocalNav
import com.jambo199.vividmusic.ui.MainViewModel
import com.jambo199.vividmusic.ui.components.Artwork
import com.jambo199.vividmusic.ui.components.CollageArtwork
import com.jambo199.vividmusic.ui.components.EmptyState
import com.jambo199.vividmusic.ui.components.LocalBottomPadding
import com.jambo199.vividmusic.ui.components.SectionHeader

/** Top-level entries of the folder tree; a lone storage root is skipped so you start inside it. */
fun topLevelFolders(lib: Library): List<FolderNode> {
    val roots = lib.folderRoots.mapNotNull { lib.folders[it] }
    val storage = roots.filter { !it.isPickedFolder }
    val picked = roots.filter { it.isPickedFolder }
    val storageEntries = if (storage.size == 1) {
        storage[0].childKeys.mapNotNull { lib.folders[it] }
    } else {
        storage
    }
    return picked + storageEntries.sortedWith(compareBy(NaturalOrderComparator) { it.name })
}

@Composable
fun FoldersTab(viewModel: MainViewModel) {
    val library by viewModel.library.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val libraryState by viewModel.libraryState.collectAsState()
    val nav = LocalNav.current
    val bottom = LocalBottomPadding.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) viewModel.addPickedFolder(uri)
    }

    val tree = settings.folderViewMode == FolderViewMode.TREE
    val entries = remember(library, tree) {
        if (tree) {
            topLevelFolders(library)
        } else {
            library.musicFolders.sortedWith(compareBy(NaturalOrderComparator) { it.name })
        }
    }
    // When we start inside the only storage root, songs stored directly in it still need an entry.
    val singleStorageRoot = remember(library) {
        library.folderRoots.mapNotNull { library.folders[it] }.filter { !it.isPickedFolder }.singleOrNull()
    }

    LazyColumn(contentPadding = PaddingValues(bottom = bottom + 8.dp)) {
        item(key = "controls") {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected = tree,
                    onClick = { viewModel.updateSettings { it.copy(folderViewMode = FolderViewMode.TREE) } },
                    label = { Text("Tree") },
                    leadingIcon = { Icon(Icons.Rounded.AccountTree, contentDescription = null, modifier = Modifier.size(18.dp)) },
                )
                FilterChip(
                    selected = !tree,
                    onClick = { viewModel.updateSettings { it.copy(folderViewMode = FolderViewMode.FLAT) } },
                    label = { Text("All folders") },
                    leadingIcon = { Icon(Icons.Rounded.ViewList, contentDescription = null, modifier = Modifier.size(18.dp)) },
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { picker.launch(null) }) {
                    Icon(Icons.Rounded.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add")
                }
            }
        }
        if (entries.isEmpty() && libraryState.hasLoadedOnce) {
            item(key = "empty") {
                EmptyState(
                    icon = Icons.Rounded.FolderOpen,
                    title = "No music folders",
                    message = "Tap \"Add\" to pick any folder on your phone or SD card and play the music inside it.",
                    action = {
                        TextButton(onClick = { picker.launch(null) }) { Text("Choose a folder") }
                    },
                )
            }
        }
        items(entries, key = { it.key }) { node ->
            FolderRow(
                node = node,
                subtitle = if (tree) null else node.displayPath.substringBeforeLast('/'),
                onClick = { nav.folder(node.key) },
            )
        }
        if (tree && singleStorageRoot != null && singleStorageRoot.songs.isNotEmpty() && entries.none { it.key == singleStorageRoot.key }) {
            item(key = "rootsongs") {
                FolderRow(singleStorageRoot, subtitle = "Songs stored directly in internal storage") { nav.folder(singleStorageRoot.key) }
            }
        }
    }
}

@Composable
fun FolderRow(node: FolderNode, subtitle: String? = null, trailing: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            val icon = folderIcon(node)
            Artwork(node.cover, Modifier.size(56.dp), RoundedCornerShape(12.dp), placeholderIcon = icon, iconSize = 28.dp)
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(2.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(node.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val counts = buildList {
                if (node.childKeys.isNotEmpty()) add(pluralize(node.childKeys.size, "folder"))
                add(pluralize(node.totalSongs, "song"))
            }.joinToString(" • ")
            Text(
                subtitle?.let { "$it • $counts" } ?: counts,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        trailing?.invoke()
    }
}

fun folderIcon(node: FolderNode): ImageVector = when {
    node.isPickedFolder && node.parentKey == null -> Icons.Rounded.FolderOpen
    node.name == "Internal storage" -> Icons.Rounded.PhoneAndroid
    node.name == "SD card" || node.name.startsWith("USB") -> Icons.Rounded.SdStorage
    else -> Icons.Rounded.Folder
}

@Composable
fun PlaylistsTab(viewModel: MainViewModel) {
    val userData by viewModel.userData.collectAsState()
    val library by viewModel.library.collectAsState()
    val nav = LocalNav.current
    val bottom = LocalBottomPadding.current
    var creating by remember { mutableStateOf(false) }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importPlaylist(uri)
    }

    LazyColumn(contentPadding = PaddingValues(bottom = bottom + 8.dp)) {
        item(key = "smart") { SectionHeader("For you") }
        val smart = listOf(
            Triple(SmartPlaylists.FAVORITES, Icons.Rounded.Favorite, userData.favorites.count { library.byKey.containsKey(it) }),
            Triple(SmartPlaylists.RECENTLY_ADDED, Icons.Rounded.FiberNew, minOf(100, library.songs.size)),
            Triple(SmartPlaylists.RECENTLY_PLAYED, Icons.Rounded.History, userData.recent.count { library.byKey.containsKey(it) }),
            Triple(SmartPlaylists.MOST_PLAYED, Icons.Rounded.Whatshot, minOf(100, userData.playCounts.keys.count { library.byKey.containsKey(it) })),
        )
        items(smart, key = { "smart${it.first}" }) { (id, icon, count) ->
            PlaylistRow(SmartPlaylists.name(id), pluralize(count, "song"), icon, highlight = true) { nav.playlist(id) }
        }
        item(key = "mine") {
            SectionHeader("Your playlists") {
                TextButton(onClick = {
                    importer.launch(arrayOf("audio/x-mpegurl", "audio/mpegurl", "application/x-mpegurl", "application/vnd.apple.mpegurl", "*/*"))
                }) {
                    Icon(Icons.Rounded.GetApp, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Import")
                }
                TextButton(onClick = { creating = true }) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("New")
                }
            }
        }
        if (userData.playlists.isEmpty()) {
            item(key = "none") {
                Text(
                    "No playlists yet. Tap New, or use \"Add to playlist\" from any song's menu.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
        items(userData.playlists, key = { "p${it.id}" }) { p ->
            val songs = remember(p, library) { p.songKeys.take(40).mapNotNull { library.byKey[it] } }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { nav.playlist(p.id) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CollageArtwork(songs, Modifier.size(56.dp), RoundedCornerShape(12.dp), placeholderIcon = Icons.AutoMirrored.Rounded.PlaylistPlay)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        pluralize(p.songKeys.size, "song"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (creating) {
        NameDialog(
            title = "New playlist",
            initial = "",
            confirm = "Create",
            onDismiss = { creating = false },
            onConfirm = { name ->
                viewModel.createPlaylist(name)
                creating = false
            },
        )
    }
}

@Composable
private fun PlaylistRow(title: String, subtitle: String, icon: ImageVector, highlight: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (highlight) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = if (highlight) MaterialTheme.colorScheme.onPrimaryContainer else Color.Unspecified)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun NameDialog(
    title: String,
    initial: String,
    confirm: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
