package com.jambo199.vividmusic.ui.screens

import android.app.Activity
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.media3.common.Player
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.jambo199.vividmusic.data.OnlineLookup
import com.jambo199.vividmusic.data.OnlineLyrics
import com.jambo199.vividmusic.data.PlayerBackground
import com.jambo199.vividmusic.data.Song
import com.jambo199.vividmusic.data.formatDuration
import com.jambo199.vividmusic.playback.SleepTimer
import com.jambo199.vividmusic.ui.LocalNav
import com.jambo199.vividmusic.ui.LocalSongMenu
import com.jambo199.vividmusic.ui.MainViewModel
import com.jambo199.vividmusic.ui.components.AddToPlaylistDialog
import com.jambo199.vividmusic.ui.components.Artwork
import com.jambo199.vividmusic.ui.components.qualityBadge
import com.jambo199.vividmusic.ui.components.readTechnicalInfo
import com.jambo199.vividmusic.ui.theme.rememberArtworkColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun NowPlayingScreen(viewModel: MainViewModel, onSleepTimer: () -> Unit) {
    val playback by viewModel.playback.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val userData by viewModel.userData.collectAsState()
    val lyrics by viewModel.lyrics.collectAsState()
    val lyricsLookup by viewModel.lyricsLookup.collectAsState()
    val sleep by viewModel.sleepTimer.collectAsState()
    val source by viewModel.queueSource.collectAsState()
    val nav = LocalNav.current
    val songMenu = LocalSongMenu.current
    val context = LocalContext.current
    val song = playback.song

    var showLyrics by rememberSaveable { mutableStateOf(settings.showLyricsWhenAvailable) }
    var showQueue by rememberSaveable { mutableStateOf(false) }
    var showSpeed by rememberSaveable { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var addToPlaylist by remember { mutableStateOf(false) }

    val lyricsPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && song != null) viewModel.assignLyrics(song, uri)
    }

    // Keep the screen awake while the player is open, if the user wants that.
    val view = LocalView.current
    DisposableEffect(settings.keepScreenOn) {
        view.keepScreenOn = settings.keepScreenOn
        onDispose { view.keepScreenOn = false }
    }

    val themeDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    // Blurred artwork sits under a dark scrim, so everything on it is drawn light.
    val blurBackground = settings.playerBackground == PlayerBackground.BLUR && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val dark = themeDark || blurBackground
    val artColor = rememberArtworkColor(song)
    val baseBg = if (blurBackground) Color(0xFF101010) else MaterialTheme.colorScheme.background
    val accentTarget = artColor ?: MaterialTheme.colorScheme.primary
    val accent by animateColorAsState(accentTarget, tween(700), label = "accent")
    val topBg by animateColorAsState(
        when (settings.playerBackground) {
            PlayerBackground.PLAIN -> baseBg
            else -> (artColor ?: MaterialTheme.colorScheme.primaryContainer).copy(alpha = if (dark) 0.55f else 0.4f).compositeOver(baseBg)
        },
        tween(700),
        label = "bg",
    )
    val controlColor = if (dark) Color.White else Color(0xFF1A1A1A)
    val sliderColor = if (artColor != null) {
        if (dark) lerpToward(accent, Color.White, if (blurBackground) 0.4f else 0.25f) else lerpToward(accent, Color.Black, 0.25f)
    } else if (blurBackground) {
        Color.White
    } else {
        MaterialTheme.colorScheme.primary
    }

    // Light status bar icons over the dark blurred background, even in the light theme.
    val window = (view.context as? Activity)?.window
    DisposableEffect(blurBackground, themeDark, window) {
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        if (controller != null && blurBackground && !themeDark) {
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = false
        }
        onDispose {
            if (controller != null && blurBackground && !themeDark) {
                controller.isAppearanceLightStatusBars = true
                controller.isAppearanceLightNavigationBars = true
            }
        }
    }

    var position by remember { mutableLongStateOf(viewModel.player.position()) }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(playback.isPlaying, song?.key) {
        while (true) {
            if (!dragging) position = viewModel.player.position()
            delay(if (playback.isPlaying) 200 else 800)
        }
    }

    val tech by produceState<Map<String, String>>(emptyMap(), song?.key) {
        value = song?.let { withContext(Dispatchers.IO) { readTechnicalInfo(context, it) } } ?: emptyMap()
    }

    if (song == null) {
        Box(
            Modifier
                .fillMaxSize()
                .background(baseBg)
                .safeDrawingPadding(),
            contentAlignment = Alignment.Center,
        ) {
            // The blurred-background setting forces a dark background even in the light theme.
            CompositionLocalProvider(LocalContentColor provides controlColor) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.MusicNote, contentDescription = null, modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("Nothing is playing", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))
                    IconButton(onClick = { nav.back() }) { Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Close") }
                }
            }
        }
        return
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(topBg, baseBg))),
    ) {
        if (blurBackground) BlurredArtwork(song)
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
            val landscape = maxWidth > maxHeight
            Column(Modifier.fillMaxSize()) {
                // Top bar
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { nav.back() }) {
                        Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Close player", tint = controlColor, modifier = Modifier.size(30.dp))
                    }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "PLAYING FROM",
                            style = MaterialTheme.typography.labelSmall,
                            color = controlColor.copy(alpha = 0.7f),
                        )
                        Text(
                            source.ifBlank { "Your library" },
                            style = MaterialTheme.typography.labelLarge,
                            color = controlColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "More", tint = controlColor) }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(text = { Text("Add to playlist…") }, onClick = {
                                menuOpen = false
                                addToPlaylist = true
                            })
                            DropdownMenuItem(text = { Text("Song options…") }, onClick = {
                                menuOpen = false
                                songMenu.open(song)
                            })
                            DropdownMenuItem(text = { Text("Equalizer") }, onClick = {
                                menuOpen = false
                                nav.equalizer()
                            })
                            DropdownMenuItem(text = { Text("Link lyrics file (.lrc)…") }, onClick = {
                                menuOpen = false
                                lyricsPicker.launch(arrayOf("*/*"))
                            })
                            if (lyrics?.songKey == song.key && lyrics?.source == OnlineLyrics.SOURCE) {
                                DropdownMenuItem(text = { Text("Remove downloaded lyrics") }, onClick = {
                                    menuOpen = false
                                    viewModel.removeOnlineLyrics(song)
                                })
                            }
                            if (viewModel.hasLyricsFile(song)) {
                                DropdownMenuItem(text = { Text("Unlink lyrics file") }, onClick = {
                                    menuOpen = false
                                    viewModel.unassignLyrics(song)
                                })
                            }
                        }
                    }
                }

                val artArea: @Composable (Modifier) -> Unit = { mod ->
                    Box(mod, contentAlignment = Alignment.Center) {
                        AnimatedContent(targetState = showLyrics, label = "artOrLyrics") { lyricsMode ->
                            if (lyricsMode) {
                                LyricsView(
                                    songKey = song.key,
                                    lyrics = lyrics?.takeIf { it.songKey == song.key },
                                    lookup = lyricsLookup.takeIf { it.songKey == song.key } ?: OnlineLookup(song.key),
                                    autoSearch = settings.onlineLyrics,
                                    onSearchOnline = { force -> viewModel.searchLyricsOnline(force) },
                                    position = { viewModel.player.position() },
                                    isPlaying = playback.isPlaying,
                                    onSeek = { viewModel.player.seekTo(it) },
                                    contentColor = controlColor,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            } else {
                                SwipeableArtwork(
                                    song = song,
                                    isPlaying = playback.playWhenReady,
                                    enabled = settings.swipeArtworkToSkip,
                                    onNext = { viewModel.player.next() },
                                    onPrevious = { viewModel.player.previous() },
                                )
                            }
                        }
                    }
                }

                val controls: @Composable (Modifier) -> Unit = { mod ->
                    Column(mod, verticalArrangement = Arrangement.Center) {
                        // Title + favourite
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    song.title,
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = controlColor,
                                    maxLines = 1,
                                    modifier = Modifier.basicMarquee(),
                                )
                                Text(
                                    song.displayArtist,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = controlColor.copy(alpha = 0.75f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.clickable { nav.fromPlayer { artist(song.displayArtist) } },
                                )
                                Text(
                                    song.displayAlbum,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = controlColor.copy(alpha = 0.6f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.clickable { nav.fromPlayer { album(song.albumId) } },
                                )
                            }
                            val fav = song.key in userData.favoriteSet
                            val favScale by animateFloatAsState(if (fav) 1.15f else 1f, spring(dampingRatio = 0.35f), label = "fav")
                            IconButton(onClick = { viewModel.toggleFavorite(song) }) {
                                Icon(
                                    if (fav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                    contentDescription = if (fav) "Remove from favourites" else "Add to favourites",
                                    tint = if (fav) Color(0xFFFF4F7B) else controlColor,
                                    modifier = Modifier
                                        .size(28.dp)
                                        .graphicsLayer {
                                            scaleX = favScale
                                            scaleY = favScale
                                        },
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))

                        // Seek bar
                        val duration = playback.durationMs.coerceAtLeast(1L)
                        val sliderValue = if (dragging) dragValue else (position.toFloat() / duration).coerceIn(0f, 1f)
                        Slider(
                            value = sliderValue,
                            onValueChange = {
                                dragging = true
                                dragValue = it
                            },
                            onValueChangeFinished = {
                                viewModel.player.seekTo((dragValue * duration).toLong())
                                position = (dragValue * duration).toLong()
                                dragging = false
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = sliderColor,
                                activeTrackColor = sliderColor,
                                inactiveTrackColor = controlColor.copy(alpha = 0.2f),
                            ),
                        )
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            val shown = if (dragging) (dragValue * duration).toLong() else position
                            Text(formatDuration(shown), style = MaterialTheme.typography.labelMedium, color = controlColor.copy(alpha = 0.75f))
                            Spacer(Modifier.weight(1f))
                            qualityBadge(tech, song)?.let { badge ->
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = controlColor.copy(alpha = 0.1f),
                                ) {
                                    Text(
                                        badge,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = controlColor.copy(alpha = 0.8f),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                            }
                            Spacer(Modifier.weight(1f))
                            Text(
                                "-" + formatDuration((playback.durationMs - shown).coerceAtLeast(0)),
                                style = MaterialTheme.typography.labelMedium,
                                color = controlColor.copy(alpha = 0.75f),
                            )
                        }
                        Spacer(Modifier.height(8.dp))

                        // Transport controls
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(onClick = { viewModel.player.toggleShuffle() }) {
                                Icon(
                                    Icons.Rounded.Shuffle,
                                    contentDescription = if (playback.shuffle) "Shuffle on" else "Shuffle off",
                                    tint = if (playback.shuffle) sliderColor else controlColor.copy(alpha = 0.6f),
                                )
                            }
                            IconButton(onClick = { viewModel.player.previous() }, modifier = Modifier.size(56.dp)) {
                                Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous", tint = controlColor, modifier = Modifier.size(40.dp))
                            }
                            FilledIconButton(
                                onClick = { viewModel.player.togglePlayPause() },
                                modifier = Modifier.size(76.dp),
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = sliderColor,
                                    contentColor = if (sliderColor.luminance() > 0.5f) Color.Black else Color.White,
                                ),
                            ) {
                                if (playback.isBuffering && playback.playWhenReady) {
                                    CircularProgressIndicator(Modifier.size(32.dp), strokeWidth = 3.dp, color = androidx.compose.material3.LocalContentColor.current)
                                } else {
                                    AnimatedContent(
                                        targetState = playback.playWhenReady,
                                        transitionSpec = { (scaleIn() + fadeIn()) togetherWith fadeOut() },
                                        label = "playPause",
                                    ) { playing ->
                                        Icon(
                                            if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                            contentDescription = if (playing) "Pause" else "Play",
                                            modifier = Modifier.size(44.dp),
                                        )
                                    }
                                }
                            }
                            IconButton(onClick = { viewModel.player.next() }, modifier = Modifier.size(56.dp), enabled = playback.hasNext || playback.repeatMode != Player.REPEAT_MODE_OFF) {
                                Icon(Icons.Rounded.SkipNext, contentDescription = "Next", tint = controlColor, modifier = Modifier.size(40.dp))
                            }
                            IconButton(onClick = { viewModel.player.cycleRepeat() }) {
                                Icon(
                                    if (playback.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                                    contentDescription = when (playback.repeatMode) {
                                        Player.REPEAT_MODE_ONE -> "Repeat one"
                                        Player.REPEAT_MODE_ALL -> "Repeat all"
                                        else -> "Repeat off"
                                    },
                                    tint = if (playback.repeatMode != Player.REPEAT_MODE_OFF) sliderColor else controlColor.copy(alpha = 0.6f),
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))

                        // Extras
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val hasLyrics = lyrics?.songKey == song.key
                            ExtraButton(
                                icon = Icons.AutoMirrored.Rounded.Notes,
                                label = "Lyrics",
                                active = showLyrics,
                                dot = hasLyrics && !showLyrics,
                                color = controlColor,
                                accent = sliderColor,
                                showLabel = !landscape,
                            ) { showLyrics = !showLyrics }
                            ExtraButton(
                                icon = Icons.Rounded.Bedtime,
                                label = when (sleep) {
                                    is SleepTimer.Mode.At -> sleepLabel()
                                    SleepTimer.Mode.EndOfSong -> "End"
                                    SleepTimer.Mode.Off -> "Sleep"
                                },
                                active = sleep != SleepTimer.Mode.Off,
                                color = controlColor,
                                accent = sliderColor,
                                showLabel = !landscape,
                                onClick = onSleepTimer,
                            )
                            ExtraButton(
                                icon = Icons.Rounded.Speed,
                                label = if (playback.speed == 1f) "Speed" else formatSpeed(playback.speed),
                                active = playback.speed != 1f || playback.pitch != 1f,
                                color = controlColor,
                                accent = sliderColor,
                                showLabel = !landscape,
                            ) { showSpeed = true }
                            ExtraButton(
                                icon = Icons.Rounded.Equalizer,
                                label = "EQ",
                                active = settings.eq.enabled,
                                color = controlColor,
                                accent = sliderColor,
                                showLabel = !landscape,
                            ) { nav.equalizer() }
                            ExtraButton(
                                icon = Icons.AutoMirrored.Rounded.QueueMusic,
                                label = "Queue",
                                active = false,
                                color = controlColor,
                                accent = sliderColor,
                                showLabel = !landscape,
                            ) { showQueue = true }
                        }
                    }
                }

                if (landscape) {
                    Row(
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(32.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        artArea(Modifier.fillMaxHeight().aspectRatio(1f, matchHeightConstraintsFirst = true))
                        controls(Modifier.weight(1f))
                    }
                } else {
                    artArea(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 28.dp, vertical = 12.dp),
                    )
                    controls(
                        Modifier
                            .fillMaxWidth()
                            .widthIn(max = 600.dp)
                            .padding(horizontal = 24.dp)
                            .padding(bottom = 16.dp),
                    )
                }
            }
        }
    }

    if (showQueue) QueueSheet(viewModel) { showQueue = false }
    if (showSpeed) SpeedDialog(viewModel) { showSpeed = false }
    if (addToPlaylist) {
        AddToPlaylistDialog(
            playlists = userData.playlists,
            onDismiss = { addToPlaylist = false },
            onPick = {
                viewModel.addToPlaylist(it, listOf(song))
                addToPlaylist = false
            },
            onCreate = {
                viewModel.createPlaylist(it, listOf(song))
                addToPlaylist = false
            },
        )
    }
}

@Composable
private fun sleepLabel(): String {
    val remaining by produceState(SleepTimer.remainingMs()) {
        while (true) {
            value = SleepTimer.remainingMs()
            delay(1000)
        }
    }
    return formatDuration(remaining)
}

private fun formatSpeed(speed: Float): String = "%.2f".format(speed).trimEnd('0').trimEnd('.', ',') + "×"

private fun lerpToward(from: Color, to: Color, t: Float): Color = androidx.compose.ui.graphics.lerp(from, to, t)

/** The song's artwork, heavily blurred and darkened, filling the player. Android 12+. */
@Composable
private fun BlurredArtwork(song: Song) {
    val context = LocalContext.current
    val request = remember(song.key, song.dateModifiedSec) {
        ImageRequest.Builder(context).data(song).size(256).crossfade(600).build()
    }
    AsyncImage(
        model = request,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxSize()
            .blur(80.dp, BlurredEdgeTreatment.Rectangle),
    )
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.30f), Color.Black.copy(alpha = 0.62f)))),
    )
}

@Composable
private fun ExtraButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    color: Color,
    accent: Color,
    dot: Boolean = false,
    showLabel: Boolean = true,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            Icon(icon, contentDescription = label, tint = if (active) accent else color.copy(alpha = 0.75f))
            if (dot) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(accent),
                )
            }
        }
        if (showLabel) {
            Spacer(Modifier.height(2.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = if (active) accent else color.copy(alpha = 0.75f),
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}

/** Big album art; drag sideways to skip songs. Shrinks a little while paused. */
@Composable
private fun SwipeableArtwork(
    song: Song,
    isPlaying: Boolean,
    enabled: Boolean,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    LaunchedEffect(song.key) { offset.snapTo(0f) }
    val scale by animateFloatAsState(if (isPlaying) 1f else 0.86f, spring(dampingRatio = 0.6f, stiffness = 300f), label = "artScale")

    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val side = minOf(maxWidth, maxHeight)
        val widthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        Artwork(
            song = song,
            modifier = Modifier
                .size(side)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.value
                    rotationZ = offset.value / widthPx * 8f
                    alpha = 1f - (abs(offset.value) / widthPx).coerceIn(0f, 0.6f)
                }
                .shadow(24.dp, RoundedCornerShape(20.dp), clip = false)
                .then(
                    if (!enabled) Modifier else Modifier.pointerInput(song.key) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                val threshold = widthPx * 0.25f
                                val v = offset.value
                                scope.launch {
                                    when {
                                        v < -threshold -> {
                                            offset.animateTo(-widthPx, tween(160))
                                            onNext()
                                        }
                                        v > threshold -> {
                                            offset.animateTo(widthPx, tween(160))
                                            onPrevious()
                                        }
                                    }
                                    offset.animateTo(0f, spring(dampingRatio = 0.7f))
                                }
                            },
                            onHorizontalDrag = { change, amount ->
                                change.consume()
                                scope.launch { offset.snapTo(offset.value + amount) }
                            },
                        )
                    },
                ),
            shape = RoundedCornerShape(20.dp),
            iconSize = 96.dp,
        )
    }
}
