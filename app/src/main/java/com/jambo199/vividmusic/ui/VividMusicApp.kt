package com.jambo199.vividmusic.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.jambo199.vividmusic.ui.components.LocalBottomPadding
import com.jambo199.vividmusic.ui.components.LocalSelection
import com.jambo199.vividmusic.ui.components.SelectionBar
import com.jambo199.vividmusic.ui.components.SelectionState
import com.jambo199.vividmusic.ui.components.MiniPlayer
import com.jambo199.vividmusic.ui.components.SongMenuHost
import com.jambo199.vividmusic.ui.screens.AlbumScreen
import com.jambo199.vividmusic.ui.screens.ArtistScreen
import com.jambo199.vividmusic.ui.screens.EqualizerScreen
import com.jambo199.vividmusic.ui.screens.FolderScreen
import com.jambo199.vividmusic.ui.screens.LibraryScreen
import com.jambo199.vividmusic.ui.screens.NowPlayingScreen
import com.jambo199.vividmusic.ui.screens.PermissionScreen
import com.jambo199.vividmusic.ui.screens.PlaylistScreen
import com.jambo199.vividmusic.ui.screens.SearchScreen
import com.jambo199.vividmusic.ui.screens.SettingsScreen
import com.jambo199.vividmusic.ui.screens.SleepTimerDialog
import kotlinx.coroutines.launch

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
    val showMini = playback.song != null && route != Routes.NOW_PLAYING && !showSelection

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
            "search" -> nav.search()
            "settings" -> nav.settings()
            "equalizer" -> nav.equalizer()
            "favorites" -> nav.playlist(com.jambo199.vividmusic.data.SmartPlaylists.FAVORITES)
            "queue" -> nav.nowPlaying()
        }
        viewModel.consumeNavRequest()
    }
    val tabRequest by viewModel.tabRequest.collectAsState()
    LaunchedEffect(tabRequest) {
        // Make sure the library is showing so it can switch tabs.
        if (tabRequest != null && route != null && route != Routes.LIBRARY) {
            navController.popBackStack(Routes.LIBRARY, inclusive = false)
        }
    }

    val navBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val miniHeight = if (showMini || showSelection) 84.dp else 0.dp

    CompositionLocalProvider(
        LocalNav provides nav,
        LocalSongMenu provides songMenu,
        LocalSelection provides selection,
        LocalBottomPadding provides navBar + miniHeight,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            NavHost(
                navController = navController,
                startDestination = Routes.LIBRARY,
                modifier = Modifier.fillMaxSize(),
                enterTransition = { fadeIn(tween(220)) + slideInHorizontally(tween(260)) { it / 10 } },
                exitTransition = { fadeOut(tween(160)) },
                popEnterTransition = { fadeIn(tween(220)) },
                popExitTransition = { fadeOut(tween(160)) + slideOutHorizontally(tween(220)) { it / 10 } },
            ) {
                composable(Routes.LIBRARY) {
                    LibraryScreen(viewModel) { sleepDialog = true }
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
                    modifier = Modifier.navigationBarsPadding(),
                )
            }

            // Registered after NavHost so it takes priority over navigating back.
            BackHandler(enabled = showSelection) { selection.clear() }

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
                    .padding(bottom = navBar + miniHeight),
            )
        }

        SongMenuHost(songMenu, viewModel)
        if (sleepDialog) SleepTimerDialog(viewModel) { sleepDialog = false }
    }
}
