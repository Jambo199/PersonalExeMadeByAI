package com.jambo199.vividmusic.ui.online

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jambo199.vividmusic.data.formatDuration
import com.jambo199.vividmusic.online.Load
import com.jambo199.vividmusic.online.NcsTrack
import com.jambo199.vividmusic.ui.LocalNav
import com.jambo199.vividmusic.ui.MainViewModel

/**
 * Everything known about an NCS song: artists, release date, genres, moods, versions, length,
 * how popular it is, and the credit NCS asks video makers to use. Plus play, queue and save.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NcsInfoSheet(track: NcsTrack, viewModel: MainViewModel, onDismiss: () -> Unit) {
    val ncs = ncsViewModel()
    val state = rememberNcsListState(viewModel, ncs)
    val nav = LocalNav.current
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val detailsFlow = remember(track.page) { track.page?.let { ncs.details(it) } }
    val details = detailsFlow?.collectAsState()?.value
    val song = state.song(track)
    val saved = state.saved(track)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 16.dp),
        ) {
            Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                RemoteImage(
                    track.cover,
                    track.title,
                    Modifier
                        .size(96.dp)
                        .clip(RoundedCornerShape(14.dp)),
                ) {
                    Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(track.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Text(track.artists, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull("NCS Release", track.year.takeIf { it > 0 }?.toString()).joinToString(" • "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                SheetAction(Icons.Rounded.PlayArrow, "Play") {
                    viewModel.play(listOf(song), 0, "NCS")
                    onDismiss()
                }
                SheetAction(Icons.Rounded.SkipNext, "Play next") {
                    viewModel.playNext(listOf(song))
                    onDismiss()
                }
                SheetAction(Icons.AutoMirrored.Rounded.QueueMusic, "Add to queue") {
                    viewModel.addToQueue(listOf(song))
                    onDismiss()
                }
                SheetAction(if (saved) Icons.Rounded.DownloadDone else Icons.Rounded.Download, if (saved) "Saved" else "Save", enabled = !saved) {
                    state.save(listOf(track))
                }
                SheetAction(Icons.Rounded.Share, "Share") {
                    val text = "${track.title} – ${track.artists}\n${track.page ?: track.url}"
                    runCatching {
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share"))
                    }
                }
            }
            HorizontalDivider()

            if (track.artistLinks.isNotEmpty()) {
                SheetLabel("Artists")
                FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    track.artistLinks.forEach { a ->
                        AssistChip(
                            onClick = {
                                onDismiss()
                                nav.fromPlayer { ncsArtist(a.path) }
                            },
                            label = { Text(a.name) },
                            leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            InfoLine("Title", track.title)
            InfoLine("Artist", track.artists)
            InfoLine("Released", track.released.ifBlank { null })
            InfoLine("Year", track.year.takeIf { it > 0 }?.toString())
            InfoLine(if (track.genres.size > 1) "Genres" else "Genre", track.genres.ifEmpty { listOf(track.genre) }.filter { it.isNotBlank() }.joinToString(", ").ifBlank { null })
            InfoLine(if (track.moods.size > 1) "Moods" else "Mood", track.moods.joinToString(", ").ifBlank { null })
            InfoLine("Versions", track.versions.ifBlank { null })
            InfoLine("Length", track.durationMs.takeIf { it > 0 }?.let(::formatDuration))
            InfoLine("Popularity", track.rank.takeIf { it > 0 }?.let { "No. $it among the most played NCS songs" })
            InfoLine("Label", "NoCopyrightSounds (NCS)")
            InfoLine("On your phone", if (saved) "Saved in Music › NCS" else "Not saved (streams from NCS)")

            SheetLabel("Using it in a video?")
            when (details) {
                null -> {}
                Load.Loading -> Text(
                    "Getting NCS's credit text…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                is Load.Failed -> Text(
                    "Couldn't get NCS's credit text. ${details.message}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                is Load.Ready -> if (details.data.credit.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                    ) {
                        Row(Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.Top) {
                            Text(details.data.credit, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            IconButton(onClick = {
                                copy(context, details.data.credit)
                                viewModel.toast("Credit copied")
                            }) { Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy credit") }
                        }
                    }
                }
            }
            Text(
                "NCS music is free to use in videos as long as you credit it like this in the description.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                track.page?.let { page ->
                    OutlinedButton(onClick = { open(context, page) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("ncs.io", maxLines = 1)
                    }
                }
                (details as? Load.Ready)?.data?.video?.let { video ->
                    OutlinedButton(onClick = { open(context, video) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.SmartDisplay, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Watch video", maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetAction(icon: ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(64.dp)) {
        FilledTonalIconButton(onClick = onClick, enabled = enabled) { Icon(icon, contentDescription = label) }
        Text(label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 2)
    }
}

@Composable
private fun SheetLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 6.dp),
    )
}

@Composable
private fun InfoLine(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Row(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(110.dp),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

private fun copy(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("NCS credit", text))
}

private fun open(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}
