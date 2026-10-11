package com.jambo199.vividmusic.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Quiz
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jambo199.vividmusic.data.Achievement
import kotlinx.coroutines.delay

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
    "streak" -> Icons.Rounded.LocalFireDepartment
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

/** The round badge itself: bright with its icon when unlocked, a grey padlock when not. */
@Composable
fun BadgeIcon(a: Achievement, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
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
            modifier = Modifier.size(size * 0.47f),
        )
    }
}

/**
 * "Badge unlocked!" sliding in at the top while confetti falls. It goes by itself after a few
 * seconds and never blocks the screen; tap it to see all your badges.
 */
@Composable
fun BadgeCelebration(badge: Achievement, more: Int, onDismiss: () -> Unit, onSeeAll: () -> Unit, modifier: Modifier = Modifier) {
    val pop = remember(badge.id) { Animatable(0f) }
    var shown by remember(badge.id) { mutableStateOf(false) }
    LaunchedEffect(badge.id) {
        shown = true
        pop.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 300f))
        delay(6_000)
        shown = false
        delay(400)
        onDismiss()
    }
    Box(modifier.fillMaxSize()) {
        Confetti(burst = badge.id)
        AnimatedVisibility(
            visible = shown,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(12.dp),
        ) {
            Surface(
                onClick = onSeeAll,
                shape = RoundedCornerShape(24.dp),
                tonalElevation = 6.dp,
                shadowElevation = 12.dp,
                modifier = Modifier
                    .widthIn(max = 440.dp)
                    .fillMaxWidth(),
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    BadgeIcon(
                        badge,
                        56.dp,
                        Modifier.graphicsLayer {
                            scaleX = pop.value
                            scaleY = pop.value
                            rotationZ = (1f - pop.value) * -40f
                        },
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (more > 0) "Badge unlocked! (+$more more)" else "Badge unlocked!",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(badge.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            badge.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                        )
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, contentDescription = "Dismiss") }
                }
            }
        }
    }
}

@Composable
private fun Badge(a: Achievement, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        BadgeIcon(a, 64.dp)
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
