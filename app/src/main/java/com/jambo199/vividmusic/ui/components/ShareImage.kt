package com.jambo199.vividmusic.ui.components

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.jambo199.vividmusic.data.Song
import com.jambo199.vividmusic.ui.theme.rememberArtworkColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Shows [content] and records it into [layer], so it can be shared as a picture. */
@Composable
fun Capturable(layer: GraphicsLayer, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier.drawWithContent {
            layer.record { this@drawWithContent.drawContent() }
            drawLayer(layer)
        },
    ) { content() }
}

/** Saves what [layer] shows as a PNG and opens the share sheet for it. */
suspend fun shareLayer(context: Context, layer: GraphicsLayer, fileName: String, title: String) {
    val bitmap = layer.toImageBitmap().asAndroidBitmap()
    val file = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "shares").apply { mkdirs() }
        File(dir, fileName).also { f -> f.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
    }
    runCatching {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("image/png")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, title))
    }
}

/** A lyric line on a card in the song's colours, ready to share. */
@Composable
fun ShareLyricDialog(song: Song, line: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val layer = rememberGraphicsLayer()
    val base = rememberArtworkColor(song) ?: Color(0xFF6A1B9A)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Share this lyric") },
        text = {
            Capturable(layer) {
                Column(
                    Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(Brush.linearGradient(listOf(lerp(base, Color.Black, 0.15f), lerp(base, Color.Black, 0.55f))))
                        .padding(22.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Artwork(song, Modifier.size(44.dp), shape = RoundedCornerShape(10.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(song.title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(song.displayArtist, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    Text(line, color = Color.White, fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(18.dp))
                    Text("Vivid Music", color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    shareLayer(context, layer, "vivid-lyric.png", "Share lyric")
                    onDismiss()
                }
            }) {
                Icon(Icons.Rounded.Share, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Share")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** The song you're listening to as a picture card: big artwork in its own colours. */
@Composable
fun ShareSongDialog(song: Song, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val layer = rememberGraphicsLayer()
    val base = rememberArtworkColor(song) ?: Color(0xFF6A1B9A)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Share what you're playing") },
        text = {
            Capturable(layer) {
                Column(
                    Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(Brush.verticalGradient(listOf(lerp(base, Color.White, 0.1f), lerp(base, Color.Black, 0.6f))))
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("NOW LISTENING", color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(14.dp))
                    Artwork(song, Modifier.size(200.dp), shape = RoundedCornerShape(18.dp), iconSize = 72.dp)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        song.title,
                        color = Color.White,
                        fontSize = 22.sp,
                        lineHeight = 26.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        song.displayArtist,
                        color = Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("Vivid Music", color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    shareLayer(context, layer, "vivid-now-listening.png", "Share song")
                    onDismiss()
                }
            }) {
                Icon(Icons.Rounded.Share, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Share")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
