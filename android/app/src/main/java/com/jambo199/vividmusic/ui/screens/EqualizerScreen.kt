package com.jambo199.vividmusic.ui.screens

import android.content.Intent
import android.media.audiofx.AudioEffect
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jambo199.vividmusic.data.EqSettings
import com.jambo199.vividmusic.playback.AudioEffects
import com.jambo199.vividmusic.ui.LocalNav
import com.jambo199.vividmusic.ui.MainViewModel
import com.jambo199.vividmusic.ui.components.EmptyState
import com.jambo199.vividmusic.ui.components.LocalBottomPadding
import com.jambo199.vividmusic.ui.components.SectionHeader

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EqualizerScreen(viewModel: MainViewModel) {
    val settings by viewModel.settings.collectAsState()
    val info by AudioEffects.info.collectAsState()
    val error by AudioEffects.error.collectAsState()
    val nav = LocalNav.current
    val context = LocalContext.current
    val eq = settings.eq
    fun update(transform: (EqSettings) -> EqSettings) = viewModel.updateSettings { it.copy(eq = transform(it.eq)) }

    val openSystemEq = {
        try {
            context.startActivity(
                Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL)
                    .putExtra(AudioEffect.EXTRA_AUDIO_SESSION, AudioEffects.sessionId)
                    .putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                    .putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC),
            )
        } catch (e: Exception) {
            viewModel.toast("No system equalizer found on this phone")
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Equalizer") },
            navigationIcon = {
                IconButton(onClick = { nav.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
            },
            actions = {
                Switch(
                    checked = eq.enabled,
                    onCheckedChange = { on -> update { it.copy(enabled = on) } },
                    modifier = Modifier.padding(end = 12.dp),
                )
            },
        )
        val i = info
        if (i == null) {
            EmptyState(
                icon = Icons.Rounded.Equalizer,
                title = "Equalizer not ready",
                message = error ?: "Start playing a song, then come back here.",
                action = { OutlinedButton(onClick = openSystemEq) { Text("Open phone's sound effects") } },
            )
            return@Column
        }

        LazyColumn(contentPadding = PaddingValues(bottom = LocalBottomPadding.current + 16.dp)) {
            item {
                if (!eq.enabled) {
                    Text(
                        "Turn on the switch above to apply these settings.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }
            if (i.presets.isNotEmpty()) {
                item { SectionHeader("Presets") }
                item {
                    FlowRow(
                        Modifier.padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = eq.preset < 0,
                            onClick = { update { it.copy(preset = -1) } },
                            label = { Text("Custom") },
                        )
                        i.presets.forEachIndexed { index, name ->
                            FilterChip(
                                selected = eq.preset == index,
                                onClick = {
                                    val levels = AudioEffects.presetLevels(index)
                                    update { it.copy(enabled = true, preset = index, bandLevels = levels ?: it.bandLevels) }
                                },
                                label = { Text(name) },
                            )
                        }
                    }
                }
            }
            item { SectionHeader("Bands") }
            items(i.bandCount) { band ->
                val level = eq.bandLevels.getOrNull(band) ?: 0
                Column(Modifier.padding(horizontal = 16.dp, vertical = 2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(formatFrequency(i.centerFreqsHz.getOrElse(band) { 0 }), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                        Text(
                            "%+.1f dB".format(level / 100f),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.End,
                            modifier = Modifier.width(72.dp),
                        )
                    }
                    Slider(
                        value = level.toFloat(),
                        onValueChange = { v ->
                            update { s ->
                                val levels = MutableList(i.bandCount) { b -> s.bandLevels.getOrNull(b) ?: 0 }
                                levels[band] = v.toInt()
                                s.copy(enabled = true, preset = -1, bandLevels = levels)
                            }
                        },
                        valueRange = i.minLevel.toFloat()..i.maxLevel.toFloat(),
                    )
                }
            }
            item { SectionHeader("Effects") }
            if (i.hasBassBoost) {
                item {
                    EffectSlider("Bass boost", eq.bassBoost, 0..1000) { v -> update { it.copy(enabled = true, bassBoost = v) } }
                }
            }
            if (i.hasVirtualizer) {
                item {
                    EffectSlider("Surround (virtualizer)", eq.virtualizer, 0..1000) { v -> update { it.copy(enabled = true, virtualizer = v) } }
                }
            }
            if (i.hasLoudness) {
                item {
                    EffectSlider("Loudness boost", eq.loudnessGain, 0..1500) { v -> update { it.copy(enabled = true, loudnessGain = v) } }
                }
            }
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(onClick = {
                        update { it.copy(preset = -1, bandLevels = List(i.bandCount) { 0 }, bassBoost = 0, virtualizer = 0, loudnessGain = 0) }
                    }, modifier = Modifier.weight(1f)) { Text("Reset") }
                    Button(onClick = openSystemEq, modifier = Modifier.weight(1f)) { Text("Phone effects") }
                }
            }
        }
    }
}

@Composable
private fun EffectSlider(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 2.dp)) {
        Row {
            Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Text(
                "${(value * 100 / range.last.coerceAtLeast(1))}%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
        )
    }
}

private fun formatFrequency(hz: Int): String = if (hz >= 1000) {
    val k = hz / 1000f
    (if (k % 1f == 0f) "${k.toInt()}" else "%.1f".format(k)) + " kHz"
} else {
    "$hz Hz"
}
