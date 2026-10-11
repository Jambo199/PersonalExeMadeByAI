package com.jambo199.vividmusic

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.jambo199.vividmusic.ui.MainViewModel
import com.jambo199.vividmusic.ui.VividMusicApp
import com.jambo199.vividmusic.ui.theme.VividTheme
import com.jambo199.vividmusic.ui.theme.rememberArtworkSeed

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val settings by viewModel.settings.collectAsState()
            val song by viewModel.currentSong.collectAsState()
            val seed = if (settings.artworkColors) rememberArtworkSeed(song) else null
            VividTheme(settings, seed) {
                VividMusicApp(viewModel)
            }
        }
        if (savedInstanceState == null) handleIntent(intent)
        AppGraph.updates.checkOnLaunch(AppGraph.settings.current.autoCheckUpdates)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        viewModel.player.connect()
    }

    override fun onResume() {
        super.onResume()
        viewModel.onResume()
        AppGraph.updates.onResume()
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) viewModel.player.disconnect()
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val data = intent.data
        if (intent.action == Intent.ACTION_VIEW && data != null) {
            viewModel.playExternal(data, intent.type)
        } else if (intent.getBooleanExtra(EXTRA_OPEN_PLAYER, false)) {
            viewModel.requestOpenPlayer()
        }
        intent.getStringExtra(EXTRA_SHORTCUT)?.let { viewModel.handleShortcut(it) }
    }

    companion object {
        const val EXTRA_OPEN_PLAYER = "vivid.open_player"
        const val EXTRA_SHORTCUT = "vivid.shortcut"
    }
}
