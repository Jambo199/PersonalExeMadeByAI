package com.jambo199.vividmusic

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.memory.MemoryCache
import com.jambo199.vividmusic.data.ArtworkLoader
import com.jambo199.vividmusic.data.LibraryRepository
import com.jambo199.vividmusic.data.LyricsRepository
import com.jambo199.vividmusic.data.SettingsStore
import com.jambo199.vividmusic.data.SongArtworkFetcher
import com.jambo199.vividmusic.data.SongArtworkKeyer
import com.jambo199.vividmusic.data.UserDataStore
import com.jambo199.vividmusic.playback.QueueStore
import com.jambo199.vividmusic.update.UpdateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Hand-rolled dependency container; the UI and the playback service share one process. */
object AppGraph {
    lateinit var app: Application
        private set

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val settings by lazy { SettingsStore(app) }
    val artwork by lazy { ArtworkLoader(app) }
    val userData by lazy { UserDataStore(app, scope) }
    val library by lazy { LibraryRepository(app, settings, scope) { artwork.clearMisses() } }
    val lyrics by lazy { LyricsRepository(app, scope) { library.library } }
    val queueStore by lazy { QueueStore(app) }
    val updates by lazy { UpdateManager(app, scope) }

    fun init(application: Application) {
        app = application
    }
}

class VividApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        AppGraph.init(this)
        AppGraph.library.refreshAll()
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .components {
            add(SongArtworkFetcher.Factory(AppGraph.artwork))
            add(SongArtworkKeyer())
        }
        .memoryCache {
            MemoryCache.Builder(this).maxSizePercent(0.2).build()
        }
        .crossfade(true)
        .build()
}
