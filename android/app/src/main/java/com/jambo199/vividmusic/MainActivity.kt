package com.jambo199.vividmusic

import android.os.Bundle
import androidx.activity.ComponentActivity

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    companion object {
        const val EXTRA_OPEN_PLAYER = "vivid.open_player"
    }
}
