package com.jambo199.vividmusic.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Quiz
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jambo199.vividmusic.data.Achievement

private fun iconFor(id: String): ImageVector = when (id) {
    "first" -> Icons.Rounded.MusicNote
    "century" -> Icons.Rounded.Star
    "thousand" -> Icons.Rounded.EmojiEvents
    "explorer" -> Icons.Rounded.Explore
    "repeat" -> Icons.Rounded.Repeat
    "marathon" -> Icons.AutoMirrored.Rounded.DirectionsRun
    "devoted" -> Icons.Rounded.CalendarMonth
    "hearts" -> Icons.Rounded.Favorite
    "curator" -> Icons.AutoMirrored.Rounded.QueueMusic
    "ncs" -> Icons.Rounded.Headphones
    "quiz" -> Icons.Rounded.Quiz
    else -> Icons.Rounded.GraphicEq
}

private val BadgeColors = listOf(
    listOf(Color(0xFFFF6F00), Color(0xFFFFCA28)),
    listOf(Color(0xFF6A1B9A), Color(0xFFEC407A)),
    listOf(Color(0xFF0277BD), Color(0xFF26C6DA)),
    listOf(Color(0xFF2E7D32), Color(0xFF9CCC65)),
)

/** Badges three to a row: bright when unlocked, grey with progress when not. */
@Composable
fun BadgeGrid(achievements: List<Achievement>, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        achievements.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { a -> Badge(a, Modifier.weight(1f)) }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun Badge(a: Achievement, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(
                    if (a.unlocked) Brush.linearGradient(BadgeColors[Math.floorMod(a.id.hashCode(), BadgeColors.size)])
                    else Brush.linearGradient(listOf(MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.surfaceContainerHigh)),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (a.unlocked) iconFor(a.id) else Icons.Rounded.Lock,
                contentDescription = null,
                tint = if (a.unlocked) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(30.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(a.title, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, maxLines = 1)
        Text(
            a.description,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
        if (!a.unlocked) {
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { a.fraction },
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
            )
            Text("${a.progress} / ${a.target}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
