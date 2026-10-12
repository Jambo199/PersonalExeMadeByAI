package com.jambo199.vividpulse.ui.games

import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.jambo199.vividpulse.games.EpicGame
import com.jambo199.vividpulse.games.Game
import com.jambo199.vividpulse.ui.Routes
import com.jambo199.vividpulse.ui.TabBottomPadding
import com.jambo199.vividpulse.ui.components.AmbientGlow
import com.jambo199.vividpulse.ui.components.GlassCard
import com.jambo199.vividpulse.ui.components.LiveDot
import com.jambo199.vividpulse.ui.components.Pill
import com.jambo199.vividpulse.ui.components.ShimmerBox
import com.jambo199.vividpulse.ui.components.bouncyClick
import com.jambo199.vividpulse.ui.components.entrance
import com.jambo199.vividpulse.ui.components.sharedPicture
import com.jambo199.vividpulse.ui.theme.Accents
import com.jambo199.vividpulse.ui.theme.LocalPulseColors
import com.jambo199.vividpulse.ui.theme.Numbers
import com.jambo199.vividpulse.util.Format
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GamesScreen(open: (String) -> Unit) {
    val vm: GamesViewModel = viewModel()
    val tab by vm.tab.collectAsStateWithLifecycle()
    val online by vm.online.collectAsStateWithLifecycle()
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val chartState = tab.chart?.let { vm.chart(it).collectAsStateWithLifecycle().value }
    val epicState by vm.epic.collectAsStateWithLifecycle()
    val refreshing = chartState?.loading ?: epicState.loading

    Box(Modifier.fillMaxSize()) {
        AmbientGlow(listOf(Accents.games, Accents.upload, Accents.download), Modifier.fillMaxWidth().height(380.dp))
        PullToRefreshBox(
            isRefreshing = refreshing && (chartState?.items ?: epicState.items) != null,
            onRefresh = { vm.refresh() },
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = top + 8.dp, bottom = TabBottomPadding),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Column(Modifier.padding(start = 4.dp).entrance(0)) {
                        Text("Games", style = MaterialTheme.typography.headlineLarge)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val o = online
                            if (o != null) {
                                LiveDot(Accents.battery.start)
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "${Format.compact(o.first)} on Steam · ${Format.compact(o.second)} playing",
                                    style = MaterialTheme.typography.bodyMedium.merge(Numbers),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                Text("What PC gamers are playing and buying", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                item { TabPicker(tab, Modifier.entrance(1)) { vm.select(it) } }
                item {
                    val at = chartState?.fetchedAt ?: epicState.fetchedAt
                    val error = chartState?.error ?: epicState.error.takeIf { tab.chart == null }
                    Text(
                        when {
                            error != null && at != null -> "$error · showing ${ago(at)}'s list"
                            at != null -> "Updated ${ago(at)} · pull down to refresh"
                            else -> " "
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
                if (tab.chart != null) {
                    val s = chartState ?: ListState()
                    val games = s.items
                    when {
                        games != null -> {
                            val history = games.any { it.lastWeekRank != null }
                            itemsIndexed(games, key = { _, g -> "${tab.name}-${g.appId}" }) { i, g ->
                                val modifier = Modifier.entrance(i + 2)
                                if (i < 3 && tab != GamesTab.DEALS) {
                                    HeroGame(g, tab, history, modifier) { open(Routes.game(g.appId)) }
                                } else {
                                    GameRow(g, tab, history, modifier) { open(Routes.game(g.appId)) }
                                }
                            }
                        }
                        s.error != null -> item { ErrorCard(s.error) { vm.refresh() } }
                        else -> items(6) { i -> ShimmerBox(Modifier.fillMaxWidth().height(if (i < 3) 190.dp else 80.dp)) }
                    }
                } else {
                    val games = epicState.items
                    when {
                        games != null && games.isEmpty() -> item { ErrorCard("No free games listed right now") { vm.refresh() } }
                        games != null -> itemsIndexed(games, key = { _, g -> g.url }) { i, g -> EpicCard(g, Modifier.entrance(i + 2)) }
                        epicState.error != null -> item { ErrorCard(epicState.error ?: "") { vm.refresh() } }
                        else -> items(3) { ShimmerBox(Modifier.fillMaxWidth().height(260.dp)) }
                    }
                }
                item {
                    Text(
                        "Charts from Steam and the Epic Games Store, refreshed when you open them.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun TabPicker(selected: GamesTab, modifier: Modifier, onSelect: (GamesTab) -> Unit) {
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GamesTab.entries.forEach { t ->
            val on = t == selected
            val glow by animateFloatAsState(if (on) 1f else 0f, spring(dampingRatio = 0.7f, stiffness = 500f), label = "chip")
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(LocalPulseColors.current.glass)
                    .background(Accents.games.brush, alpha = glow)
                    .bouncyClick { onSelect(t) }
                    .padding(horizontal = 18.dp, vertical = 11.dp),
            ) {
                Text(
                    t.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (on) Color.White else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun HeroGame(g: Game, tab: GamesTab, history: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .bouncyClick(onClick = onClick),
    ) {
        GameImage(g.imageUrl, g.appId, Modifier.fillMaxWidth().aspectRatio(460f / 215f))
        Box(
            Modifier
                .matchParentSize()
                .background(Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.85f))),
        )
        Text(
            "#${g.rank}",
            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Black),
            color = Color.White,
            modifier = Modifier.align(Alignment.TopStart).padding(14.dp),
        )
        Row(Modifier.align(Alignment.TopEnd).padding(14.dp)) { if (history) RankChange(g) }
        Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
            Text(g.name, style = MaterialTheme.typography.titleLarge, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subline(g, tab), style = MaterialTheme.typography.bodyMedium.merge(Numbers), color = Color.White.copy(alpha = 0.85f), maxLines = 1)
        }
    }
}

@Composable
private fun GameRow(g: Game, tab: GamesTab, history: Boolean, modifier: Modifier, onClick: () -> Unit) {
    GlassCard(modifier.fillMaxWidth(), onClick = onClick, padding = 10.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${g.rank}",
                style = MaterialTheme.typography.titleLarge.merge(Numbers),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(34.dp).padding(start = 4.dp),
            )
            GameImage(g.imageUrl, g.appId, Modifier.width(118.dp).aspectRatio(460f / 215f).clip(RoundedCornerShape(12.dp)))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(g.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (tab == GamesTab.DEALS) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(g.price ?: "", style = MaterialTheme.typography.bodyMedium.merge(Numbers), fontWeight = FontWeight.Bold, color = Accents.battery.start)
                        g.originalPrice?.let {
                            Spacer(Modifier.width(6.dp))
                            Text(it, style = MaterialTheme.typography.bodySmall.copy(textDecoration = TextDecoration.LineThrough), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    g.dealEnds?.let { Text("Ends ${endsIn(it * 1000)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                } else {
                    Text(subline(g, tab), style = MaterialTheme.typography.bodySmall.merge(Numbers), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
            Spacer(Modifier.width(6.dp))
            if (g.discountPercent > 0) Pill("-${g.discountPercent}%", Accents.battery) else if (history) RankChange(g)
        }
    }
}

@Composable
private fun RankChange(g: Game) {
    val change = g.rankChange
    when {
        change == null -> Pill("NEW", Accents.upload)
        change > 0 -> Pill("▲$change", Accents.battery)
        change < 0 -> Pill("▼${-change}", Accents.thermal)
        else -> Unit
    }
}

private fun subline(g: Game, tab: GamesTab): String = when (tab) {
    GamesTab.MOST_PLAYED -> listOfNotNull(
        g.playersNow?.let { "${Format.compact(it)} playing" },
        g.peakToday?.let { "peak ${Format.compact(it)}" },
    ).joinToString(" · ")
    else -> listOfNotNull(
        g.price,
        g.discountPercent.takeIf { it > 0 }?.let { "-$it%" },
        g.weeksOnChart?.takeIf { it > 1 }?.let { "$it weeks" },
    ).joinToString(" · ")
}

@Composable
fun GameImage(url: String, appId: Int, modifier: Modifier) {
    val context = LocalContext.current
    AsyncImage(
        model = ImageRequest.Builder(context).data(url).crossfade(250).build(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.sharedPicture("game-$appId").background(LocalPulseColors.current.track),
    )
}

@Composable
private fun EpicCard(g: EpicGame, modifier: Modifier) {
    val context = LocalContext.current
    GlassCard(
        modifier.fillMaxWidth(),
        accent = if (g.freeNow) Accents.battery else Accents.games,
        padding = 0.dp,
        onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(g.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } },
    ) {
        Box {
            AsyncImage(
                model = ImageRequest.Builder(context).data(g.imageUrl).crossfade(250).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(LocalPulseColors.current.track),
            )
            Row(Modifier.align(Alignment.TopStart).padding(12.dp)) {
                Pill(if (g.freeNow) "FREE NOW" else "COMING SOON", if (g.freeNow) Accents.battery else Accents.games)
            }
        }
        Column(Modifier.padding(16.dp)) {
            Text(g.title, style = MaterialTheme.typography.titleLarge)
            Row(verticalAlignment = Alignment.CenterVertically) {
                val ends = g.endsAt
                val starts = g.startsAt
                Text(
                    when {
                        g.freeNow && ends != null -> "Free until ${day(ends)}"
                        !g.freeNow && starts != null -> "Free from ${day(starts)}"
                        else -> "Free"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (g.freeNow) Accents.battery.start else Accents.games.start,
                )
                g.originalPrice?.let {
                    Spacer(Modifier.width(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall.copy(textDecoration = TextDecoration.LineThrough), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (g.description.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(g.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun ErrorCard(message: String, retry: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth(), accent = Accents.thermal, onClick = retry) {
        Text(message, style = MaterialTheme.typography.titleMedium)
        Text("Tap to try again", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun ago(ms: Long): String =
    DateUtils.getRelativeTimeSpanString(ms, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
        .replace("0 minutes ago", "just now").lowercase(Locale.getDefault())

private fun endsIn(ms: Long): String =
    DateUtils.getRelativeTimeSpanString(ms, System.currentTimeMillis(), DateUtils.HOUR_IN_MILLIS).toString().lowercase(Locale.getDefault())

private fun day(ms: Long): String = SimpleDateFormat("EEE d MMM, HH:mm", Locale.getDefault()).format(Date(ms))
