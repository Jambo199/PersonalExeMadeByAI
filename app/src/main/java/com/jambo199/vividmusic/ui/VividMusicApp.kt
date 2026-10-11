package com.jambo199.vividmusic.ui

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.jambo199.vividmusic.data.MainSection
import com.jambo199.vividmusic.ui.components.BadgeCelebration
import com.jambo199.vividmusic.ui.components.LocalBottomPadding
import com.jambo199.vividmusic.ui.components.LocalSelection
import com.jambo199.vividmusic.ui.components.MainNavigationBar
import com.jambo199.vividmusic.ui.components.MainNavigationRail
import com.jambo199.vividmusic.ui.components.MiniPlayer
import com.jambo199.vividmusic.ui.components.SelectionBar
import com.jambo199.vividmusic.ui.components.SelectionState
import com.jambo199.vividmusic.ui.components.SongMenuHost
import com.jambo199.vividmusic.ui.discover.DiscoverScreen
import com.jambo199.vividmusic.ui.online.NcsArtistScreen
import com.jambo199.vividmusic.ui.quiz.QuizScreen
import com.jambo199.vividmusic.ui.screens.AlarmScreen
import com.jambo199.vividmusic.ui.screens.AlbumScreen
import com.jambo199.vividmusic.ui.screens.ArtistScreen
import com.jambo199.vividmusic.ui.screens.CarScreen
import com.jambo199.vividmusic.ui.screens.EqualizerScreen
import com.jambo199.vividmusic.ui.screens.FocusScreen
import com.jambo199.vividmusic.ui.screens.FolderScreen
import com.jambo199.vividmusic.ui.screens.GenreScreen
import com.jambo199.vividmusic.ui.screens.MainScreen
import com.jambo199.vividmusic.ui.screens.NowPlayingScreen
import com.jambo199.vividmusic.ui.screens.PartyScreen
import com.jambo199.vividmusic.ui.screens.PermissionScreen
import com.jambo199.vividmusic.ui.screens.PlaylistScreen
import com.jambo199.vividmusic.ui.screens.SearchScreen
import com.jambo199.vividmusic.ui.screens.SettingsScreen
import com.jambo199.vividmusic.ui.screens.SleepTimerDialog
import com.jambo199.vividmusic.ui.screens.StatsScreen
import com.jambo199.vividmusic.ui.screens.WrappedScreen
import kotlinx.coroutines.launch

private val NAV_ROUTES = setOf(Routes.MAIN, Routes.ALBUM, Routes.ARTIST, Routes.FOLDER, Routes.PLAYLIST, Routes.STATS, Routes.GENRE, Routes.NCS_ARTIST)

/** Height of the Material navigation bar, not counting the system navigation bar under it. */
private val NAV_BAR_HEIGHT = 80.dp

@Composable
fun VividMusicApp(viewModel: MainViewModel) {
    val hasPermission by viewModel.hasPermission.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val openPlayer by viewModel.openPlayer.collectAsState()
    var skippedPermission by rememberSaveable { mutableStateOf(false) }
    // Playing a file from another app doesn't need library access, so don't block it.
    LaunchedEffect(openPlayer) {
        if (openPlayer) skippedPermission = true
    }

    val showLibrary = hasPermission || skippedPermission || settings.pickedFolders.isNotEmpty() || openPlayer
    if (!showLibrary) {
        PermissionScreen(viewModel) { skippedPermission = true }
        return
    }

    val navController = rememberNavController()
    val nav = remember(navController) { NavActions(navController) }
    val songMenu = remember { SongMenuState() }
    val selection = remember { SelectionState() }
    val snackbar = remember { SnackbarHostState() }
    val playback by viewModel.playback.collectAsState()
    var sleepDialog by remember { mutableStateOf(false) }
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val showSelection = selection.active
    // The quiz and Discover play their own clips and Wrapped is full screen, so the mini player would only get in the way.
    val showMini = playback.song != null && route !in NO_MINI_PLAYER && !showSelection
    val section by viewModel.section.collectAsState()
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    // The navigation bar stays while browsing (albums, folders...) and hides for the player,
    // settings and search, like most music apps.
    val showNavigation = (route ?: Routes.MAIN) in NAV_ROUTES && !showSelection
    val showBottomBar = showNavigation && !landscape
    val showRail = showNavigation && landscape

    // Leaving a screen or pressing back ends multi-select.
    LaunchedEffect(route) { selection.clear() }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            snackbar.currentSnackbarData?.dismiss()
            launch { snackbar.showSnackbar(message, duration = SnackbarDuration.Short) }
        }
    }
    LaunchedEffect(openPlayer) {
        if (openPlayer) {
            nav.nowPlaying()
            viewModel.consumeOpenPlayer()
        }
    }
    val navRequest by viewModel.navRequest.collectAsState()
    LaunchedEffect(navRequest) {
        when (navRequest) {
            null -> return@LaunchedEffect
            "main" -> navController.popBackStack(Routes.MAIN, inclusive = false)
            "search" -> nav.search()
            "settings" -> nav.settings()
            "equalizer" -> nav.equalizer()
            "stats" -> nav.stats()
            "quiz" -> nav.quiz()
            "wrapped" -> nav.wrapped()
            "alarm" -> nav.alarm()
            "discover" -> nav.discover()
            "party" -> nav.party()
            "car" -> nav.car()
            "focus" -> nav.focus()
            "favorites" -> nav.playlist(com.jambo199.vividmusic.data.SmartPlaylists.FAVORITES)
            "queue" -> nav.nowPlaying()
        }
        viewModel.consumeNavRequest()
    }
    val onSelectSection: (MainSection) -> Unit = { s ->
        val onMain = route == null || route == Routes.MAIN
        if (onMain && s == section) {
            viewModel.reselectSection(s)
        } else {
            if (!onMain) navController.popBackStack(Routes.MAIN, inclusive = false)
            viewModel.selectSection(s)
        }
    }

    val navBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val miniHeight = if (showMini || showSelection) 84.dp else 0.dp
    val barHeight = if (showBottomBar) NAV_BAR_HEIGHT else 0.dp
    val miniLift by animateDpAsState(barHeight, tween(220), label = "miniLift")
    val density = LocalDensity.current
    var railWidth by remember { mutableStateOf(80.dp) }

    CompositionLocalProvider(
        LocalNav provides nav,
        LocalSongMenu provides songMenu,
        LocalSelection provides selection,
        LocalBottomPadding provides navBar + barHeight + miniHeight,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(start = if (showRail) railWidth else 0.dp),
            ) {
                NavHost(
                    navController = navController,
                    startDestination = Routes.MAIN,
                    modifier = Modifier.fillMaxSize(),
                    enterTransition = { fadeIn(tween(220)) + slideInHorizontally(tween(260)) { it / 10 } },
                    exitTransition = { fadeOut(tween(160)) },
                    popEnterTransition = { fadeIn(tween(220)) },
                    popExitTransition = { fadeOut(tween(160)) + slideOutHorizontally(tween(220)) { it / 10 } },
                ) {
                    composable(Routes.MAIN) {
                        MainScreen(viewModel) { sleepDialog = true }
                    }
                    composable(
                        Routes.NOW_PLAYING,
                        enterTransition = { slideInVertically(tween(320)) { it } },
                        exitTransition = { fadeOut(tween(160)) },
                        popEnterTransition = { fadeIn(tween(200)) },
                        popExitTransition = { slideOutVertically(tween(280)) { it } },
                    ) {
                        NowPlayingScreen(viewModel) { sleepDialog = true }
                    }
                    composable(Routes.SEARCH) { SearchScreen(viewModel) }
                    composable(Routes.SETTINGS) { SettingsScreen(viewModel) }
                    composable(Routes.EQUALIZER) { EqualizerScreen(viewModel) }
                    composable(Routes.STATS) { StatsScreen(viewModel) }
                    composable(Routes.QUIZ) { QuizScreen() }
                    composable(Routes.WRAPPED) { WrappedScreen(viewModel) }
                    composable(Routes.ALARM) { AlarmScreen(viewModel) }
                    composable(Routes.DISCOVER) { DiscoverScreen(viewModel) }
                    composable(Routes.PARTY) { PartyScreen(viewModel) }
                    composable(Routes.CAR) { CarScreen(viewModel) }
                    composable(Routes.FOCUS) { FocusScreen(viewModel) }
                    composable(Routes.ALBUM, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                        AlbumScreen(viewModel, entry.arguments?.getLong("id") ?: 0L)
                    }
                    composable(Routes.ARTIST, arguments = listOf(navArgument("name") { type = NavType.StringType })) { entry ->
                        ArtistScreen(viewModel, entry.arguments?.getString("name").orEmpty())
                    }
                    composable(Routes.FOLDER, arguments = listOf(navArgument("key") { type = NavType.StringType })) { entry ->
                        FolderScreen(viewModel, entry.arguments?.getString("key").orEmpty())
                    }
                    composable(Routes.PLAYLIST, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                        PlaylistScreen(viewModel, entry.arguments?.getLong("id") ?: 0L)
                    }
                    composable(Routes.GENRE, arguments = listOf(navArgument("name") { type = NavType.StringType })) { entry ->
                        GenreScreen(viewModel, entry.arguments?.getString("name").orEmpty())
                    }
                    composable(Routes.NCS_ARTIST, arguments = listOf(navArgument("path") { type = NavType.StringType })) { entry ->
                        NcsArtistScreen(viewModel, entry.arguments?.getString("path").orEmpty())
                    }
                }

                AnimatedVisibility(
                    visible = showMini,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut(),
                ) {
                    MiniPlayer(
                        state = playback,
                        position = { viewModel.player.position() },
                        onOpen = { nav.nowPlaying() },
                        onPlayPause = { viewModel.player.togglePlayPause() },
                        onNext = { viewModel.player.next() },
                        onPrevious = { viewModel.player.previous() },
                        modifier = Modifier
                            .navigationBarsPadding()
                            .padding(bottom = miniLift),
                    )
                }

                AnimatedVisibility(
                    visible = showBottomBar,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    enter = slideInVertically(tween(220)) { it },
                    exit = slideOutVertically(tween(220)) { it },
                ) {
                    MainNavigationBar(section, onSelectSection)
                }

                // Registered after NavHost so they take priority over navigating back.
                BackHandler(enabled = showSelection) { selection.clear() }
                // Back from Library or Folders goes Home before leaving the app.
                BackHandler(enabled = !showSelection && route == Routes.MAIN && section != MainSection.HOME) {
                    viewModel.selectSection(MainSection.HOME)
                }

                AnimatedVisibility(
                    visible = showSelection,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut(),
                ) {
                    SelectionBar(
                        selection = selection,
                        onPlay = {
                            viewModel.play(selection.selectedSongs, 0, "Selected songs")
                            selection.clear()
                        },
                        onQueue = {
                            viewModel.addToQueue(selection.selectedSongs)
                            selection.clear()
                        },
                        onPlaylist = {
                            songMenu.addToPlaylist(selection.selectedSongs)
                            selection.clear()
                        },
                        onMore = {
                            songMenu.openMany(selection.selectedSongs)
                            selection.clear()
                        },
                        modifier = Modifier.navigationBarsPadding(),
                    )
                }

                SnackbarHost(
                    hostState = snackbar,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = navBar + barHeight + miniHeight),
                )
            }

            AnimatedVisibility(
                visible = showRail,
                modifier = Modifier.align(Alignment.CenterStart),
                enter = slideInHorizontally { -it },
                exit = slideOutHorizontally { -it },
            ) {
                MainNavigationRail(
                    section,
                    onSelectSection,
                    Modifier.onSizeChanged { railWidth = with(density) { it.width.toDp() } },
                )
            }

            // A badge you've just earned: a banner and confetti over whatever's showing.
            val celebration by viewModel.celebration.collectAsState()
            celebration?.let { c ->
                BadgeCelebration(
                    c.badge,
                    c.more,
                    onDismiss = viewModel::dismissCelebration,
                    onSeeAll = {
                        viewModel.dismissCelebration()
                        nav.stats()
                    },
                )
            }
        }

        SongMenuHost(songMenu, viewModel)
        if (sleepDialog) SleepTimerDialog(viewModel) { sleepDialog = false }
    }
}

private val NO_MINI_PLAYER = setOf<String?>(Routes.NOW_PLAYING, Routes.QUIZ, Routes.WRAPPED, Routes.DISCOVER, Routes.PARTY, Routes.CAR)
