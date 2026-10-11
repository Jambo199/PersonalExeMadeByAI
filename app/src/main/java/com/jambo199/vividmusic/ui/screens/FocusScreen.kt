package com.jambo199.vividmusic.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jambo199.vividmusic.playback.FocusTimer
import com.jambo199.vividmusic.playback.MusicAlarm
import com.jambo199.vividmusic.ui.LocalNav
import com.jambo199.vividmusic.ui.MainViewModel
import com.jambo199.vividmusic.ui.components.LocalBottomPadding
import kotlinx.coroutines.delay

private val FocusColor = Color(0xFFE53935)
private val BreakColor = Color(0xFF43A047)

/** The focus timer: pick a rhythm, press start, and the music works in rounds with breaks. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusScreen(viewModel: MainViewModel) {
    val mode by FocusTimer.mode.collectAsState()
    val playback by viewModel.playback.collectAsState()
    val nav = LocalNav.current
    val context = LocalContext.current
    val bottom = LocalBottomPadding.current
    var planIndex by rememberSaveable { mutableStateOf(0) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(mode) {
        while (true) {
            now = System.currentTimeMillis()
            delay(500)
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Focus timer") },
            navigationIcon = {
                IconButton(onClick = { nav.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
            },
        )
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = bottom + 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val (label, color, left, total, round, plan) = when (val m = mode) {
                is FocusTimer.Mode.Focus -> Phase("Focus", FocusColor, m.endsAt - now, m.endsAt - m.startedAt, m.round, m.plan)
                is FocusTimer.Mode.Break -> Phase("Break", BreakColor, m.endsAt - now, m.endsAt - m.startedAt, m.round, m.plan)
                FocusTimer.Mode.Off -> {
                    val p = FocusTimer.PLANS[planIndex]
                    Phase("Ready", MaterialTheme.colorScheme.primary, p.focusMinutes * 60_000L, p.focusMinutes * 60_000L, 0, p)
                }
            }
            Spacer(Modifier.height(16.dp))
            Box(Modifier.size(260.dp), contentAlignment = Alignment.Center) {
                val track = MaterialTheme.colorScheme.surfaceContainerHighest
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 16.dp.toPx()
                    val inset = stroke / 2
                    val arc = Size(size.width - stroke, size.height - stroke)
                    drawArc(track, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(stroke))
                    val fraction = if (total > 0) (left.toFloat() / total).coerceIn(0f, 1f) else 0f
                    drawArc(color, -90f, 360f * fraction, false, Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Round))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(label.uppercase(), style = MaterialTheme.typography.labelLarge, color = color, fontWeight = FontWeight.Bold)
                    Text(clockText(left), fontSize = 56.sp, fontWeight = FontWeight.Black)
                    if (mode != FocusTimer.Mode.Off) {
                        Text("Round $round of ${plan.rounds}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            // A dot per round: filled once done.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (r in 1..plan.rounds) {
                    val done = r < round || (r == round && mode is FocusTimer.Mode.Break)
                    Box(
                        Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(if (done || r == round) FocusColor.copy(alpha = if (done) 1f else 0.45f) else MaterialTheme.colorScheme.surfaceContainerHighest),
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            when (mode) {
                FocusTimer.Mode.Off -> {
                    Text("Choose your rhythm", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FocusTimer.PLANS.forEachIndexed { i, p ->
                            FilterChip(
                                selected = i == planIndex,
                                onClick = { planIndex = i },
                                label = { Text("${p.focusMinutes} / ${p.breakMinutes} min") },
                            )
                        }
                    }
                    Text(
                        "${plan.rounds} rounds of ${plan.focusMinutes} minutes with music, with a ${plan.breakMinutes}-minute quiet break " +
                            "between them. The music fades out for each break and comes back by itself.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                    Button(
                        onClick = {
                            FocusTimer.start(FocusTimer.PLANS[planIndex])
                            if (!playback.isPlaying) {
                                if (playback.song != null) viewModel.player.play() else viewModel.shuffle(viewModel.library.value.songs, "All songs")
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                    ) { Text("Start focusing", style = MaterialTheme.typography.titleMedium) }
                }
                else -> {
                    if (mode is FocusTimer.Mode.Break) {
                        Button(onClick = { MusicAlarm.endBreakNow(context) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                            Text("Skip the break")
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    OutlinedButton(
                        onClick = {
                            FocusTimer.stop()
                            MusicAlarm.cancelFocusResume(context)
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) { Text("Stop the session") }
                }
            }
        }
    }
}

private data class Phase(
    val label: String,
    val color: Color,
    val leftMs: Long,
    val totalMs: Long,
    val round: Int,
    val plan: FocusTimer.Plan,
)

private fun clockText(ms: Long): String {
    val seconds = ((ms + 999) / 1000).coerceAtLeast(0)
    return "%d:%02d".format(seconds / 60, seconds % 60)
}
