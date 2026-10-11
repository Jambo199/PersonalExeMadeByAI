package com.jambo199.vividmusic.ui.quiz

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Quiz
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jambo199.vividmusic.data.Song
import com.jambo199.vividmusic.ui.LocalNav
import com.jambo199.vividmusic.ui.components.Confetti
import com.jambo199.vividmusic.ui.components.Artwork

private val Correct = Color(0xFF2E7D32)
private val Wrong = Color(0xFFC62828)

/** "Guess the song": hear a few seconds, pick the title. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizScreen() {
    val quiz: QuizViewModel = viewModel()
    val nav = LocalNav.current
    val state by quiz.state.collectAsState()

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Guess the song") },
            navigationIcon = {
                IconButton(onClick = { nav.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
            },
        )
        AnimatedContent(
            targetState = state,
            contentKey = { s -> if (s is QuizState.Round) "round" + s.number else s::class.simpleName },
            transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) },
            label = "quiz",
            modifier = Modifier.fillMaxSize(),
        ) { s ->
            when (s) {
                QuizState.Setup -> Setup(quiz)
                QuizState.Loading -> Centered { Text("Picking songs…", style = MaterialTheme.typography.titleMedium) }
                is QuizState.Unavailable -> Centered {
                    Text(s.message, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { quiz.backToSetup() }) { Text("OK") }
                }
                is QuizState.Round -> RoundView(quiz, s)
                is QuizState.Finished -> FinishedView(quiz, s) { nav.back() }
            }
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) { content() }
}

@Composable
private fun Setup(quiz: QuizViewModel) {
    val source by quiz.source.collectAsState()
    val best by quiz.best.collectAsState()
    val mode by quiz.mode.collectAsState()
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Color(0xFF7C4DFF), Color(0xFFE040FB)))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Quiz, contentDescription = null, tint = Color.White, modifier = Modifier.size(64.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text("How well do you know your music?", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            "${QuizViewModel.ROUNDS} rounds. Hear a few seconds of a song and pick its name (or who it's by). Answer fast and keep a streak going for more points.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuizSource.entries.forEach { s ->
                FilterChip(selected = source == s, onClick = { quiz.selectSource(s) }, label = { Text(s.label) })
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuizMode.entries.forEach { m ->
                FilterChip(selected = mode == m, onClick = { quiz.selectMode(m) }, label = { Text(m.label) })
            }
        }
        Spacer(Modifier.height(24.dp))
        Button(onClick = { quiz.start() }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text("Start", style = MaterialTheme.typography.titleMedium)
        }
        val record = best[QuizViewModel.bestKey(source, mode)] ?: 0
        if (record > 0) {
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.EmojiEvents, contentDescription = null, tint = Color(0xFFFFB300))
                Spacer(Modifier.width(6.dp))
                Text("Best score: $record", style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

@Composable
private fun RoundView(quiz: QuizViewModel, round: QuizState.Round) {
    var left by remember(round.number) { mutableFloatStateOf(1f) }
    LaunchedEffect(round.number, round.answered) {
        while (!round.answered) {
            withFrameMillis {
                val elapsed = android.os.SystemClock.elapsedRealtime() - round.startedAt
                left = (1f - elapsed.toFloat() / QuizViewModel.ROUND_MS).coerceIn(0f, 1f)
            }
        }
    }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Round ${round.number} of ${round.total}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            if (round.streak >= 2) {
                Icon(Icons.Rounded.LocalFireDepartment, contentDescription = null, tint = Color(0xFFFF6D00), modifier = Modifier.size(20.dp))
                Text("${round.streak}", style = MaterialTheme.typography.titleSmall, color = Color(0xFFFF6D00))
                Spacer(Modifier.width(12.dp))
            }
            Text("${round.score} pts", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { if (round.answered) 0f else left },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
        )
        Spacer(Modifier.height(20.dp))
        val answer = round.question.answer
        Box(Modifier.size(200.dp), contentAlignment = Alignment.Center) {
            if (round.answered) {
                Artwork(answer, Modifier.size(200.dp), shape = RoundedCornerShape(20.dp), iconSize = 72.dp)
            } else {
                Listening()
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            when {
                !round.answered -> if (round.question.mode == QuizMode.ARTIST) "Who is this?" else "Which song is this?"
                round.right -> "Right! +${round.gained}"
                round.chosen.isNullOrEmpty() -> "Time's up! It was…"
                else -> "Not quite. It was…"
            },
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = when {
                !round.answered -> MaterialTheme.colorScheme.onSurface
                round.right -> Correct
                else -> Wrong
            },
        )
        if (round.answered) {
            Text("${answer.title} – ${answer.displayArtist}", style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(16.dp))
        round.question.choices.forEach { choice ->
            Choice(choice, round) { quiz.answer(choice) }
            Spacer(Modifier.height(10.dp))
        }
        if (round.answered) {
            Spacer(Modifier.height(6.dp))
            Button(onClick = { quiz.next() }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(if (round.number == round.total) "See your score" else "Next song")
            }
        }
    }
}

/** A pulsing note while the clip plays, so you know to listen. */
@Composable
private fun Listening() {
    val transition = rememberInfiniteTransition(label = "listening")
    val pulse by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(tween(600, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulse",
    )
    Box(
        Modifier
            .size(170.dp)
            .scale(pulse)
            .clip(CircleShape)
            .background(Brush.radialGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.MusicNote, contentDescription = "Listening", tint = Color.White, modifier = Modifier.size(80.dp))
    }
}

@Composable
private fun Choice(song: Song, round: QuizState.Round, onClick: () -> Unit) {
    val isAnswer = round.question.isRight(song)
    val picked = song.key == round.chosen
    val color = when {
        !round.answered -> MaterialTheme.colorScheme.surfaceContainerHigh
        isAnswer -> Correct.copy(alpha = 0.18f)
        picked -> Wrong.copy(alpha = 0.18f)
        else -> MaterialTheme.colorScheme.surfaceContainerLow
    }
    val outline = when {
        round.answered && isAnswer -> Correct
        round.answered && picked -> Wrong
        else -> Color.Transparent
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(color)
            .border(2.dp, outline, RoundedCornerShape(16.dp))
            .clickable(enabled = !round.answered, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            if (round.question.mode == QuizMode.ARTIST) {
                Text(song.displayArtist, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            } else {
                Text(song.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    song.displayArtist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (round.answered && (isAnswer || picked)) {
            Icon(
                if (isAnswer) Icons.Rounded.Check else Icons.Rounded.Close,
                contentDescription = if (isAnswer) "Right answer" else "Your answer",
                tint = if (isAnswer) Correct else Wrong,
            )
        }
    }
}

@Composable
private fun FinishedView(quiz: QuizViewModel, result: QuizState.Finished, onDone: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        FinishedContent(quiz, result, onDone)
        // A new best score deserves confetti (as long as you got at least one right).
        if (result.newBest && result.score > 0) Confetti(burst = result)
    }
}

@Composable
private fun FinishedContent(quiz: QuizViewModel, result: QuizState.Finished, onDone: () -> Unit) {
    Centered {
        Icon(
            Icons.Rounded.EmojiEvents,
            contentDescription = null,
            tint = if (result.newBest) Color(0xFFFFB300) else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(96.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(if (result.newBest) "New best score!" else "Well played", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text("${result.score}", fontSize = 56.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
        Text("points", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        Text(
            "${result.correct} of ${result.total} right • ${verdict(result.correct, result.total)}",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        if (!result.newBest) {
            Text("Best: ${result.best}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(24.dp))
        Button(onClick = { quiz.start() }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Play again") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Done") }
    }
}

private fun verdict(correct: Int, total: Int): String {
    val share = if (total == 0) 0f else correct.toFloat() / total
    return when {
        share >= 1f -> "Perfect ears!"
        share >= 0.8f -> "Music expert"
        share >= 0.5f -> "Solid listener"
        share > 0f -> "Keep listening!"
        else -> "Time for more music"
    }
}
