package com.jambo199.vividmusic.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.jambo199.vividmusic.update.UpdateInfo
import com.jambo199.vividmusic.update.UpdateState

/** Card at the top of the library that offers, downloads and installs a new version. */
@Composable
fun UpdateBanner(
    state: UpdateState,
    dismissed: Boolean,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val info: UpdateInfo? = when (state) {
        is UpdateState.Available -> state.info.takeUnless { dismissed }
        is UpdateState.Downloading -> state.info
        is UpdateState.Ready -> state.info
        is UpdateState.Failed -> state.info
        else -> null
    }
    AnimatedVisibility(
        visible = info != null,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
        modifier = modifier,
    ) {
        val version = info?.versionName.orEmpty()
        val (title, subtitle) = when (state) {
            is UpdateState.Downloading -> "Downloading update…" to "Version $version • ${(state.progress * 100).toInt()}%"
            is UpdateState.Ready -> "Update ready to install" to "Tap Install, then confirm on the next screen"
            is UpdateState.Failed -> "Update didn't finish" to state.message
            else -> "Update available" to "Version $version is ready – tap to download and install"
        }
        val action: String? = when (state) {
            is UpdateState.Downloading -> null
            is UpdateState.Ready -> "Install"
            is UpdateState.Failed -> "Retry"
            else -> "Update"
        }
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(16.dp))
                .clickable(enabled = action != null, onClick = onUpdate),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Column(Modifier.padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.SystemUpdate, contentDescription = null, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.titleSmall)
                        Text(subtitle, style = MaterialTheme.typography.bodySmall)
                    }
                    if (state is UpdateState.Available) {
                        TextButton(onClick = onDismiss) { Text("Later") }
                    }
                    if (action != null) {
                        Button(onClick = onUpdate) { Text(action) }
                    }
                }
                if (state is UpdateState.Downloading) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 6.dp),
                    )
                }
            }
        }
    }
}
