package com.jambo199.vividmusic.ui.screens

import android.app.Activity
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.media3.common.Player
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.jambo199.vividmusic.AppGraph
import com.jambo199.vividmusic.data.ArtworkStyle
import com.jambo199.vividmusic.data.OnlineLookup
import com.jambo199.vividmusic.data.OnlineLyrics
import com.jambo199.vividmusic.data.PlayerBackground
import com.jambo199.vividmusic.data.Song
import com.jambo199.vividmusic.data.VisualizerStyle
import com.jambo199.vividmusic.data.formatDuration
import com.jambo199.vividmusic.playback.AbRepeat
import com.jambo199.vividmusic.playback.SleepTimer
import com.jambo199.vividmusic.ui.LocalNav
import com.jambo199.vividmusic.ui.LocalSongMenu
import com.jambo199.vividmusic.ui.MainViewModel
import com.jambo199.vividmusic.ui.components.AddToPlaylistDialog
import com.jambo199.vividmusic.ui.components.Artwork
import com.jambo199.vividmusic.ui.components.AuroraBackground
import com.jambo199.vividmusic.ui.components.RingVisualizer
import com.jambo199.vividmusic.ui.components.ShareSongDialog
import com.jambo199.vividmusic.ui.components.Visualizer
import com.jambo199.vividmusic.ui.components.WavySeekBar
import com.jambo199.vividmusic.ui.components.qualityBadge
import com.jambo199.vividmusic.ui.components.readTechnicalInfo
import com.jambo199.vividmusic.ui.components.rememberSpectrum
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
    val abLoop by AbRepeat.loop.collectAsState()
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
    var shareCard by remember { mutableStateOf(false) }

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
    val blurArtwork = settings.playerBackground == PlayerBackground.BLUR && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val aurora = settings.playerBackground == PlayerBackground.AURORA
    // Blurred artwork (under a dark scrim) and the aurora are dark, so everything on them is drawn light.
    val blurBackground = blurArtwork || aurora
    val dark = themeDark || blurBackground
    val artColor = rememberArtworkColor(song)
    val accentTarget = artColor ?: MaterialTheme.colorScheme.primary
    val accent by animateColorAsState(accentTarget, tween(700), label = "accent")
    // "Artwork colour": the whole player in a deep (or, in the light theme, soft) shade of the artwork.
    val solid = settings.playerBackground == PlayerBackground.SOLID
    val solidColor = if (themeDark) lerpToward(accent, Color(0xFF0C0C10), 0.62f) else lerpToward(accent, Color.White, 0.78f)
    val baseBg = when {
        blurBackground -> Color(0xFF101010)
        solid -> solidColor
        else -> MaterialTheme.colorScheme.background
    }
    val topBg by animateColorAsState(
        when (settings.playerBackground) {
            PlayerBackground.PLAIN -> baseBg
            PlayerBackground.SOLID -> lerpToward(solidColor, accent, if (themeDark) 0.12f else 0.1f)
            else -> (artColor ?: MaterialTheme.colorScheme.primaryContainer).copy(alpha = if (dark) 0.55f else 0.4f).compositeOver(baseBg)
        },
        tween(700),
        label = "bg",
    )
    val controlColor = if (dark) Color.White else Color(0xFF1A1A1A)
    // The play button and everything "on": a light tint of the artwork on dark, a deep one on light.
    val playColor = if (dark) lerpToward(accent, Color.White, if (artColor != null) 0.55f else 0.3f) else lerpToward(accent, Color.Black, 0.15f)
    val onPlayColor = if (playColor.luminance() > 0.5f) Color(0xFF1A1A1A) else Color.White

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
        if (blurArtwork) BlurredArtwork(song)
        if (aurora) AuroraBackground(accent, playback.isPlaying)
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
            val landscape = maxWidth > maxHeight
            Column(Modifier.fillMaxSize()) {
                // Top bar: round buttons, like the rest of the player.
                val hasLyrics = lyrics?.songKey == song.key
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PlayerIconButton(Icons.Rounded.KeyboardArrowDown, "Close player", controlColor, CircleShape) { nav.back() }
                    Column(Modifier.weight(1f).padding(horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "Now Playing",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = controlColor,
                        )
                        Text(
                            source.ifBlank { "Your library" },
                            style = MaterialTheme.typography.labelMedium,
                            color = controlColor.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    PlayerIconButton(
                        Icons.AutoMirrored.Rounded.Notes,
                        "Lyrics",
                        controlColor,
                        RoundedCornerShape(16.dp),
                        active = showLyrics,
                        accent = playColor,
                        dot = hasLyrics && !showLyrics,
                    ) { showLyrics = !showLyrics }
                    Spacer(Modifier.width(8.dp))
                    Box {
                        PlayerIconButton(Icons.Rounded.MoreVert, "More", controlColor, RoundedCornerShape(16.dp)) { menuOpen = true }
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
                            DropdownMenuItem(text = { Text("Party mode") }, onClick = {
                                menuOpen = false
                                nav.party()
                            })
                            DropdownMenuItem(text = { Text("Share as a picture") }, onClick = {
                                menuOpen = false
                                shareCard = true
                            })
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        when {
                                            abLoop?.key != song.key -> "A-B repeat: set start"
                                            abLoop?.endMs == null -> "A-B repeat: set end"
                                            else -> "Turn off A-B repeat"
                                        },
                                    )
                                },
                                onClick = {
                                    menuOpen = false
                                    viewModel.toast(AbRepeat.step(song.key, viewModel.player.position()))
                                },
                            )
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
                                    song = song,
                                    karaoke = settings.karaokeLyrics,
                                    onSync = { nav.lyricsSync() },
                                )
                            } else {
                                SwipeableArtwork(
                                    song = song,
                                    isPlaying = playback.playWhenReady,
                                    enabled = settings.swipeArtworkToSkip,
                                    visualizer = settings.visualizer,
                                    vinyl = settings.artworkStyle == ArtworkStyle.VINYL,
                                    beatPulse = settings.beatPulse,
                                    speed = playback.speed,
                                    onNext = { viewModel.player.next() },
                                    onPrevious = { viewModel.player.previous() },
                                    // Double-tap only ever adds, like a like; the heart button takes it away.
                                    onLove = if (song.isOnline) null else {
                                        { if (!AppGraph.userData.isFavorite(song.key)) viewModel.toggleFavorite(song) }
                                    },
                                )
                            }
                        }
                    }
                }

                val controls: @Composable (Modifier) -> Unit = { mod ->
                    Column(mod, verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        // Title and artist, centred.
                        Text(
                            song.title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = controlColor,
                            maxLines = 1,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.basicMarquee(),
                        )
                        Text(
                            song.displayArtist,
                            style = MaterialTheme.typography.titleMedium,
                            color = controlColor.copy(alpha = 0.75f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { viewModel.library.value.artistFor(song.displayArtist)?.let { a -> nav.fromPlayer { artist(a.name) } } }
                                .padding(horizontal = 6.dp),
                        )
                        Spacer(Modifier.height(14.dp))

                        // The wavy seek bar
                        val duration = playback.durationMs.coerceAtLeast(1L)
                        WavySeekBar(
                            progress = if (dragging) dragValue else (position.toFloat() / duration).coerceIn(0f, 1f),
                            playing = playback.isPlaying,
                            activeColor = playColor,
                            trackColor = controlColor.copy(alpha = 0.22f),
                            onSeeking = {
                                dragging = true
                                dragValue = it
                            },
                            onSeek = {
                                viewModel.player.seekTo((it * duration).toLong())
                                position = (it * duration).toLong()
                                dragging = false
                            },
                        )
                        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            val shown = if (dragging) (dragValue * duration).toLong() else position
                            Text(formatDuration(shown), style = MaterialTheme.typography.labelMedium, color = controlColor.copy(alpha = 0.75f))
                            Spacer(Modifier.weight(1f))
                            val loop = abLoop?.takeIf { it.key == song.key }
                            if (loop != null) {
                                // Tap to stop looping.
                                Surface(
                                    onClick = { AbRepeat.clear() },
                                    shape = RoundedCornerShape(8.dp),
                                    color = playColor,
                                ) {
                                    Text(
                                        "A-B " + formatDuration(loop.startMs) + (loop.endMs?.let { "–" + formatDuration(it) } ?: "…") + "  ✕",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = onPlayColor,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    )
                                }
                            } else {
                                qualityBadge(tech, song)?.let { badge ->
                                    Surface(shape = RoundedCornerShape(8.dp), color = controlColor.copy(alpha = 0.1f)) {
                                        Text(
                                            badge,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = controlColor.copy(alpha = 0.8f),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.weight(1f))
                            Text(
                                formatDuration(playback.durationMs.coerceAtLeast(0)),
                                style = MaterialTheme.typography.labelMedium,
                                color = controlColor.copy(alpha = 0.75f),
                            )
                        }
                        Spacer(Modifier.height(14.dp))

                        // Big rounded transport buttons; play/pause is the wide one and rounds off when paused.
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val sideShape = RoundedCornerShape(26.dp)
                            Surface(
                                onClick = { viewModel.player.previous() },
                                shape = sideShape,
                                color = controlColor.copy(alpha = 0.12f),
                                modifier = Modifier.size(76.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous", tint = controlColor, modifier = Modifier.size(36.dp))
                                }
                            }
                            val corner by animateDpAsState(if (playback.playWhenReady) 26.dp else 40.dp, spring(dampingRatio = 0.6f), label = "playShape")
                            Surface(
                                onClick = { viewModel.player.togglePlayPause() },
                                shape = RoundedCornerShape(corner),
                                color = playColor,
                                contentColor = onPlayColor,
                                modifier = Modifier.size(width = 112.dp, height = 84.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    if (playback.isBuffering && playback.playWhenReady) {
                                        CircularProgressIndicator(Modifier.size(32.dp), strokeWidth = 3.dp, color = onPlayColor)
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
                            }
                            val canNext = playback.hasNext || playback.repeatMode != Player.REPEAT_MODE_OFF
                            Surface(
                                onClick = { viewModel.player.next() },
                                enabled = canNext,
                                shape = sideShape,
                                color = controlColor.copy(alpha = 0.12f),
                                modifier = Modifier.size(76.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Rounded.SkipNext,
                                        contentDescription = "Next",
                                        tint = controlColor.copy(alpha = if (canNext) 1f else 0.35f),
                                        modifier = Modifier.size(36.dp),
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(16.dp))

                        // A pill of the other controls.
                        Surface(shape = RoundedCornerShape(50), color = controlColor.copy(alpha = 0.08f)) {
                            Row(
                                Modifier.padding(6.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                ToolButton(
                                    Icons.Rounded.Shuffle,
                                    if (playback.shuffle) "Shuffle on" else "Shuffle off",
                                    active = playback.shuffle,
                                    color = controlColor,
                                    accent = playColor,
                                    onAccent = onPlayColor,
                                ) { viewModel.player.toggleShuffle() }
                                ToolButton(
                                    if (playback.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                                    when (playback.repeatMode) {
                                        Player.REPEAT_MODE_ONE -> "Repeat one"
                                        Player.REPEAT_MODE_ALL -> "Repeat all"
                                        else -> "Repeat off"
                                    },
                                    active = playback.repeatMode != Player.REPEAT_MODE_OFF,
                                    color = controlColor,
                                    accent = playColor,
                                    onAccent = onPlayColor,
                                ) { viewModel.player.cycleRepeat() }
                                // Songs streamed from NCS aren't in the library to favourite.
                                if (!song.isOnline) {
                                    val fav = song.key in userData.favoriteSet
                                    ToolButton(
                                        if (fav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                        if (fav) "Remove from favourites" else "Add to favourites",
                                        active = fav,
                                        color = controlColor,
                                        accent = playColor,
                                        onAccent = onPlayColor,
                                    ) { viewModel.toggleFavorite(song) }
                                }
                                ToolButton(
                                    Icons.Rounded.Bedtime,
                                    when (sleep) {
                                        is SleepTimer.Mode.At -> "Sleep timer: " + sleepLabel()
                                        SleepTimer.Mode.EndOfSong -> "Sleep timer: end of song"
                                        SleepTimer.Mode.Off -> "Sleep"
                                    },
                                    active = sleep != SleepTimer.Mode.Off,
                                    color = controlColor,
                                    accent = playColor,
                                    onAccent = onPlayColor,
                                    onClick = onSleepTimer,
                                )
                                ToolButton(
                                    Icons.Rounded.Speed,
                                    if (playback.speed == 1f) "Speed" else "Speed " + formatSpeed(playback.speed),
                                    active = playback.speed != 1f || playback.pitch != 1f,
                                    color = controlColor,
                                    accent = playColor,
                                    onAccent = onPlayColor,
                                ) { showSpeed = true }
                                ToolButton(
                                    Icons.AutoMirrored.Rounded.QueueMusic,
                                    "Queue",
                                    active = false,
                                    color = controlColor,
                                    accent = playColor,
                                    onAccent = onPlayColor,
                                ) { showQueue = true }
                            }
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
                            .padding(horizontal = 32.dp, vertical = 12.dp),
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
    if (shareCard) ShareSongDialog(song) { shareCard = false }
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

/** A round (or rounded-square) button on a faint tint, for the player's top bar. */
@Composable
private fun PlayerIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color,
    shape: androidx.compose.ui.graphics.Shape,
    active: Boolean = false,
    accent: Color = color,
    dot: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = shape,
        color = if (active) accent else color.copy(alpha = 0.12f),
        modifier = Modifier.size(48.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = label,
                tint = if (active) (if (accent.luminance() > 0.5f) Color.Black else Color.White) else color,
                modifier = Modifier.size(26.dp),
            )
            if (dot) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(9.dp)
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(accent),
                )
            }
        }
    }
}

/** One of the rounded squares in the player's control pill; filled with [accent] when on. */
@Composable
private fun ToolButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    color: Color,
    accent: Color,
    onAccent: Color,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (active) accent else color.copy(alpha = 0.08f),
        modifier = Modifier.size(width = 46.dp, height = 44.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = label, tint = if (active) onAccent else color.copy(alpha = 0.85f), modifier = Modifier.size(22.dp))
        }
    }
}

/**
 * Big album art; drag sideways to skip songs. Shrinks a little while paused, can bounce on the
 * beat, and shows the [visualizer] across its lower part.
 */
@Composable
private fun SwipeableArtwork(
    song: Song,
    isPlaying: Boolean,
    enabled: Boolean,
    visualizer: VisualizerStyle,
    vinyl: Boolean,
    beatPulse: Boolean,
    speed: Float,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onLove: (() -> Unit)?,
) {
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    // The heart that pops up on a double-tap.
    val heart = remember { Animatable(0f) }
    LaunchedEffect(song.key) { offset.snapTo(0f) }
    val scale by animateFloatAsState(if (isPlaying) 1f else 0.86f, spring(dampingRatio = 0.6f, stiffness = 300f), label = "artScale")
    val spectrum = rememberSpectrum(enabled = visualizer != VisualizerStyle.OFF || beatPulse, playing = isPlaying, speed = speed)
    val shape = RoundedCornerShape(28.dp)
    // The record turns while playing and stays where it got to when paused.
    val spin = remember { Animatable(0f) }
    LaunchedEffect(isPlaying, vinyl) {
        if (vinyl && isPlaying) {
            while (true) {
                spin.animateTo(spin.value + 360f, tween(6_000, easing = LinearEasing))
                spin.snapTo(spin.value % 360f)
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val side = minOf(maxWidth, maxHeight)
        val widthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        Box(
            modifier = Modifier
                .size(side)
                .graphicsLayer {
                    val pulse = if (beatPulse) 1f + 0.035f * spectrum.beat else 1f
                    scaleX = scale * pulse
                    scaleY = scale * pulse
                    translationX = offset.value
                    rotationZ = offset.value / widthPx * 8f
                    alpha = 1f - (abs(offset.value) / widthPx).coerceIn(0f, 0.6f)
                }
                .then(if (vinyl) Modifier else Modifier.shadow(24.dp, shape, clip = false))
                .then(
                    if (onLove == null) Modifier else Modifier.pointerInput(song.key) {
                        detectTapGestures(onDoubleTap = {
                            onLove()
                            scope.launch {
                                heart.snapTo(0.01f)
                                heart.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 500f))
                                delay(350)
                                heart.animateTo(0f, tween(250))
                            }
                        })
                    },
                )
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
        ) {
            if (vinyl) {
                val disc = if (visualizer != VisualizerStyle.OFF) 0.8f else 0.94f
                if (visualizer != VisualizerStyle.OFF) {
                    RingVisualizer(spectrum, Color.White.copy(alpha = 0.8f), inner = disc, modifier = Modifier.fillMaxSize())
                }
                VinylRecord(
                    song = song,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxSize(disc)
                        .graphicsLayer { rotationZ = spin.value },
                )
            } else {
                Artwork(song = song, modifier = Modifier.fillMaxSize(), shape = shape, iconSize = 96.dp)
            }
            if (!vinyl && visualizer != VisualizerStyle.OFF) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .fillMaxHeight(0.4f)
                        .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)))),
                ) {
                    Visualizer(
                        state = spectrum,
                        style = visualizer,
                        color = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                    )
                }
            }
            if (heart.value > 0f) {
                Icon(
                    Icons.Rounded.Favorite,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(side * 0.36f)
                        .graphicsLayer {
                            scaleX = heart.value
                            scaleY = heart.value
                            alpha = heart.value.coerceIn(0f, 1f)
                            shadowElevation = 12f
                        },
                )
            }
        }
    }
}

/** A black record with grooves, the album art as its label and a hole in the middle. */
@Composable
private fun VinylRecord(song: Song, modifier: Modifier = Modifier) {
    Box(
        modifier
            .shadow(20.dp, CircleShape)
            .clip(CircleShape)
            .background(Brush.radialGradient(listOf(Color(0xFF2A2A2A), Color(0xFF0E0E0E), Color(0xFF1C1C1C)))),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val outer = size.minDimension / 2
            val step = 3.dp.toPx()
            var r = outer * 0.46f
            while (r < outer * 0.97f) {
                drawCircle(Color.White.copy(alpha = 0.05f), radius = r, style = Stroke(width = 1f))
                r += step
            }
            // A soft shine across the grooves.
            drawCircle(
                Brush.sweepGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.10f), Color.Transparent, Color.White.copy(alpha = 0.10f), Color.Transparent)),
                radius = outer,
            )
        }
        Artwork(song = song, modifier = Modifier.fillMaxSize(0.42f), shape = CircleShape, iconSize = 48.dp)
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(Color(0xFF0E0E0E)),
        )
    }
}
