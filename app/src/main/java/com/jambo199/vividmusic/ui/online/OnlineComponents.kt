package com.jambo199.vividmusic.ui.online

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.jambo199.vividmusic.online.RadioStation
import com.jambo199.vividmusic.ui.components.PlayingIndicator
import com.jambo199.vividmusic.ui.components.placeholderBrush

/** One OnlineViewModel for the whole app, so lists stay loaded while you move between screens. */
@Composable
fun onlineViewModel(): OnlineViewModel {
    val activity = LocalContext.current as ComponentActivity
    return viewModel(viewModelStoreOwner = activity)
}

@Composable
fun OnlineSearchField(placeholder: String, initial: String = "", onSearch: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial) }
    val keyboard = LocalSoftwareKeyboardController.current
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = {
            if (text.isNotEmpty()) {
                IconButton(onClick = {
                    text = ""
                    onSearch("")
                }) { Icon(Icons.Rounded.Close, contentDescription = "Clear") }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = {
            keyboard?.hide()
            onSearch(text)
        }),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
fun LoadingRow() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
fun ErrorRow(message: String, onRetry: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Rounded.CloudOff, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(8.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        FilledTonalButton(onClick = onRetry) { Text("Try again") }
    }
}

@Composable
fun SectionTitle(title: String, subtitle: String? = null, action: (@Composable () -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, top = 20.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        action?.invoke()
    }
}

/** A picture from the internet over a colourful placeholder (the placeholder stays if it fails). */
@Composable
fun RemoteImage(url: String?, seed: String, modifier: Modifier, fit: Boolean = false, placeholder: @Composable () -> Unit = {}) {
    var loaded by remember(url) { mutableStateOf(false) }
    Box(
        // Logos (fit) sit on a plain tile once loaded, since many have transparent edges.
        modifier.background(if (fit && loaded) SolidColor(MaterialTheme.colorScheme.surfaceContainerHighest) else placeholderBrush(seed)),
        contentAlignment = Alignment.Center,
    ) {
        if (!loaded) placeholder()
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = if (fit) ContentScale.Fit else ContentScale.Crop,
                onSuccess = { loaded = true },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(if (fit) 6.dp else 0.dp),
            )
        }
    }
}

@Composable
fun StationLogo(station: RadioStation, size: Dp) {
    RemoteImage(
        url = station.favicon.takeIf { it.startsWith("http") },
        seed = station.name,
        fit = true,
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp)),
    ) {
        Icon(Icons.Rounded.Radio, contentDescription = null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(size / 2))
    }
}

@Composable
fun StationRow(
    station: RadioStation,
    favorite: Boolean,
    playing: Boolean?,
    onPlay: () -> Unit,
    onFavorite: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(contentAlignment = Alignment.Center) {
            StationLogo(station, 52.dp)
            if (playing != null) {
                Box(
                    Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center,
                ) { PlayingIndicator(playing, color = Color.White) }
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                station.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (playing != null) FontWeight.SemiBold else FontWeight.Normal,
                color = if (playing != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                station.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onFavorite) {
            Icon(
                if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = if (favorite) "Remove ${station.name} from your stations" else "Add ${station.name} to your stations",
                tint = if (favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Square cover with a title and subtitle underneath, for sideways shelves. */
@Composable
fun CoverCard(image: String?, title: String, subtitle: String, width: Dp = 148.dp, round: Boolean = false, onClick: () -> Unit) {
    Column(
        Modifier
            .width(width)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(4.dp),
    ) {
        RemoteImage(
            url = image,
            seed = title,
            modifier = Modifier
                .size(width - 8.dp)
                .clip(if (round) RoundedCornerShape(50) else RoundedCornerShape(12.dp)),
        )
        Spacer(Modifier.height(8.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (subtitle.isNotBlank()) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun <T> CoverShelf(items: List<T>, key: (T) -> Any, content: @Composable (T) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(items, key = key) { content(it) }
    }
}
