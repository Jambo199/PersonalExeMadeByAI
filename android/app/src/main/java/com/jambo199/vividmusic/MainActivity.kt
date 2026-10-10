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

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val settings by viewModel.settings.collectAsState()
            VividTheme(settings) {
                VividMusicApp(viewModel)
            }
        }
        if (savedInstanceState == null) handleIntent(intent)
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
    }

    companion object {
        const val EXTRA_OPEN_PLAYER = "vivid.open_player"
    }
}
