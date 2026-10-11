package com.jambo199.vividmusic.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.jambo199.vividmusic.data.AlarmMusic
import com.jambo199.vividmusic.playback.MusicAlarm
import com.jambo199.vividmusic.ui.LocalNav
import com.jambo199.vividmusic.ui.MainViewModel
import com.jambo199.vividmusic.ui.components.LocalBottomPadding
import kotlinx.coroutines.delay
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/** Wake up to your music: when, which days, what plays, and how gently. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AlarmScreen(viewModel: MainViewModel) {
    val settings by viewModel.settings.collectAsState()
    val nav = LocalNav.current
    val bottom = LocalBottomPadding.current
    val context = LocalContext.current
    var picking by rememberSaveable { mutableStateOf(false) }
    val update = viewModel::updateSettings

    // "Rings in …" counts down, and the exact-alarm permission can change in Android's settings.
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    var onTime by remember { mutableStateOf(MusicAlarm.canRingOnTime(context)) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                now = LocalDateTime.now()
                onTime = MusicAlarm.canRingOnTime(context)
                delay(15_000)
            }
        }
    }
    // Straight after a change, count from this moment rather than the last tick.
    val current = remember(settings, now) { LocalDateTime.now() }
    val next = MusicAlarm.nextRing(settings, current)
    val time = LocalTime.of(settings.alarmHour, settings.alarmMinute)
    val clock = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Music alarm") },
            navigationIcon = {
                IconButton(onClick = { nav.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
            },
        )
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(bottom = bottom + 16.dp),
        ) {
            // The big clock: tap it to change the time.
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color.Transparent,
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .fillMaxWidth(),
            ) {
                Column(
                    Modifier
                        .background(
                            Brush.linearGradient(
                                if (settings.alarmEnabled) listOf(Color(0xFFFF8A65), Color(0xFFFFC107))
                                else listOf(Color(0xFF455A64), Color(0xFF78909C)),
                            ),
                        )
                        .clickable(role = Role.Button, onClickLabel = "Change the time") { picking = true }
                        .padding(20.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.WbSunny, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Wake up to music", color = Color.White, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Switch(
                            checked = settings.alarmEnabled,
                            onCheckedChange = { on -> update { it.copy(alarmEnabled = on) } },
                        )
                    }
                    Text(
                        time.format(clock),
                        color = Color.White,
                        fontSize = 64.sp,
                        lineHeight = 70.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        when {
                            next != null -> MusicAlarm.untilText(current, next) + " · " + daysText(settings.alarmDays)
                            else -> "Off · tap the time to change it"
                        },
                        color = Color.White.copy(alpha = 0.9f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            val ringing by MusicAlarm.ringing.collectAsState()
            if (ringing) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .fillMaxWidth(),
                ) {
                    Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Your alarm is playing", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        TextButton(onClick = { MusicAlarm.snooze(context) }) { Text("Snooze") }
                        TextButton(onClick = { MusicAlarm.stop(context) }) { Text("Stop alarm") }
                    }
                }
            }

            if (settings.alarmEnabled && !onTime && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "Android may ring this a few minutes late. Allow alarms for Vivid Music so it rings on time.",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        TextButton(onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")),
                                )
                            }
                        }) { Text("Allow alarms") }
                    }
                }
            }

            AlarmHeader("Repeat")
            val firstDay = remember { java.time.temporal.WeekFields.of(Locale.getDefault()).firstDayOfWeek }
            Row(
                Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                for (i in 0 until 7) {
                    val day = firstDay.plus(i.toLong())
                    val on = day.value in settings.alarmDays
                    DayToggle(day, on) {
                        update { s -> s.copy(alarmDays = if (on) s.alarmDays - day.value else s.alarmDays + day.value) }
                    }
                }
            }
            FlowRow(
                Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = settings.alarmDays.isEmpty(),
                    onClick = { update { it.copy(alarmDays = emptySet()) } },
                    label = { Text("Just once") },
                )
                FilterChip(
                    selected = settings.alarmDays == WEEKDAYS,
                    onClick = { update { it.copy(alarmDays = WEEKDAYS) } },
                    label = { Text("Weekdays") },
                )
                FilterChip(
                    selected = settings.alarmDays.size == 7,
                    onClick = { update { it.copy(alarmDays = (1..7).toSet()) } },
                    label = { Text("Every day") },
                )
            }

            AlarmHeader("Wake up to")
            FlowRow(
                Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AlarmMusic.entries.forEach { music ->
                    FilterChip(
                        selected = settings.alarmMusic == music,
                        onClick = { update { it.copy(alarmMusic = music) } },
                        label = { Text(music.label) },
                    )
                }
            }
            Text(
                when (settings.alarmMusic) {
                    AlarmMusic.FAVORITES -> "Your favourites, shuffled (or all songs if you have none yet)"
                    AlarmMusic.ALL -> "Your whole library, shuffled"
                    AlarmMusic.SONG_OF_THE_DAY -> "Today's song of the day first, then a shuffle of everything"
                    AlarmMusic.DAILY_MIX -> "A fresh mix of what you play most, new every morning"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )

            Spacer(Modifier.height(8.dp))
            ListItem(
                headlineContent = { Text("Gentle wake-up") },
                supportingContent = { Text("Starts quietly and rises over a minute") },
                trailingContent = {
                    Switch(checked = settings.alarmGentle, onCheckedChange = { on -> update { it.copy(alarmGentle = on) } })
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { update { it.copy(alarmGentle = !it.alarmGentle) } },
            )
            Text(
                "Snooze for ${MusicAlarm.SNOOZE_MINUTES} minutes or stop it from the notification. If your media volume is " +
                    "almost off, the alarm turns it up a little so you'll hear it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = { MusicAlarm.ring(context, force = true) },
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Try it now")
            }
        }
    }

    if (picking) {
        val is24 = DateFormat.is24HourFormat(context)
        val state = rememberTimePickerState(settings.alarmHour, settings.alarmMinute, is24)
        AlertDialog(
            onDismissRequest = { picking = false },
            icon = { Icon(Icons.Rounded.Alarm, contentDescription = null) },
            title = { Text("Wake me at") },
            text = { TimePicker(state) },
            confirmButton = {
                TextButton(onClick = {
                    picking = false
                    // Picking a time switches the alarm on: that's nearly always why you changed it.
                    update { it.copy(alarmHour = state.hour, alarmMinute = state.minute, alarmEnabled = true) }
                }) { Text("Set") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancel") } },
        )
    }
}

private val WEEKDAYS = (1..5).toSet()

private fun daysText(days: Set<Int>): String = when {
    days.isEmpty() -> "once"
    days.size == 7 -> "every day"
    days == WEEKDAYS -> "weekdays"
    days == setOf(6, 7) -> "weekends"
    else -> days.sorted().joinToString(", ") { DayOfWeek.of(it).getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
}

@Composable
private fun DayToggle(day: DayOfWeek, on: Boolean, onToggle: () -> Unit) {
    val name = day.getDisplayName(TextStyle.FULL, Locale.getDefault())
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh)
            .toggleable(value = on, role = Role.Checkbox, onValueChange = { onToggle() })
            .semantics { contentDescription = name },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            day.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
            color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.titleSmall,
        )
    }
}

@Composable
private fun AlarmHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
    )
}
