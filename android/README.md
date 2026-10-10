# Vivid Music (Android)

A music player for Android phones (built and tested with vivo / Funtouch OS / OriginOS in mind).
It plays the music stored on your phone or SD card, shows album art everywhere, and lets you browse
and play straight from folders.

## Install on your phone

1. On your phone, open the latest pre-release:
   **https://github.com/Jambo199/PersonalExeMadeByAI/releases/tag/vivid-music-latest**
2. Tap **VividMusic.apk** to download it, then open the download.
3. If Android asks, allow your browser / file manager to **install unknown apps**, then tap **Install**.
4. Open **Vivid Music** and tap **Allow access to music**.

Every new build installs over the old one and keeps your playlists, favourites and settings.

### vivo tips (keep music playing with the screen off)

vivo's battery saver can stop background apps. In Vivid Music go to **Settings → Background playback (vivo)**:

- **Allow unrestricted battery use** – opens Android's battery optimisation prompt.
- **vivo background & auto-start settings** – opens vivo's own manager; allow
  *High background power consumption* / *Auto-start* for Vivid Music.

You can also lock the app in the recent-apps screen (pull the card down).

## Features

**Library**
- Songs, Albums, Artists, Folders and Playlists tabs, all with album-art thumbnails
- Sort songs by title, artist, album, date added, length, year or file name (A–Z fast scroller)
- Album grid or list, artist pages with their albums, album pages with disc/track order
- Search across songs, albums, artists and folders (ignores accents and case)
- Hide short clips (ringtones, voice notes) and hide whole folders
- Library updates automatically when music is added or removed

**Folders**
- Browse your storage as a **folder tree** (Internal storage, SD card, …) or a flat list of every music folder
- Folder thumbnails come from the music inside them
- Play or shuffle a folder, or *everything inside it including subfolders*
- **Add any folder** with the system folder picker – even folders Android's media scanner skips
  (`.nomedia`, app folders, SD cards). Cover images (`cover.jpg`, `folder.jpg`, …) and `.lrc`
  lyrics next to the songs are picked up automatically
- Folder songs sort naturally by file name (Track 2 before Track 10), or by title / track / date

**Playback**
- Background playback with notification, lock-screen and Bluetooth/headset controls
- Favourite button in the notification
- Smart shuffle: the current song keeps playing, *Play next* really plays next even when shuffled
- Repeat all / one, gapless playback, skip silence
- Playback speed and pitch
- Sleep timer (minutes or end of song) with a gentle fade-out
- Equalizer with device presets, per-band sliders, bass boost, surround and loudness
- Pauses when headphones are unplugged; handles calls and other apps' audio
- Resumes your queue and position after closing the app or restarting the phone
- "Open with" support: play audio files straight from your file manager, WhatsApp, Downloads…

**Now playing**
- Big artwork that tints the screen with its colours; swipe it to skip
- Synced lyrics (embedded lyrics or `.lrc` files) that follow the song – tap a line to jump there;
  you can also link an `.lrc` file to any song
- Drag-to-reorder queue, remove songs, save the queue as a playlist
- Audio quality badge (format, sample rate / bitrate)

**Playlists**
- Your own playlists (create, rename, reorder, delete)
- Automatic Favourites, Recently added, Recently played and Most played

**Everything else**
- Material You colours from your wallpaper (Android 12+) or 8 accent colours
- Light / dark / system theme and a pure-black AMOLED mode
- Landscape layout
- Long-press songs to **select several** – play, queue, add to a playlist, share or delete them together
- Song details (bitrate, sample rate, size, path), share, and delete from device
- **Home-screen widget** with artwork and previous / play-pause / next buttons
- Home-screen shortcuts: long-press the icon for Shuffle all, Favourites, Folders and Search

## Building it yourself

GitHub Actions builds the APK on every push (`.github/workflows/android.yml`), runs it on an
Android 14 emulator with a generated test library, and publishes it as the `vivid-music-latest`
pre-release. To build locally you need JDK 17 and the Android SDK:

```bash
cd android
./gradlew assembleRelease
# app/build/outputs/apk/release/app-release.apk
```

Release builds are signed with `app/vividmusic.jks` so updates install over older builds.
To use your own key, set `SIGNING_KEYSTORE_PATH`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS` and
`SIGNING_KEY_PASSWORD` when building.

## Code map

| Path | What it does |
| --- | --- |
| `data/MediaStoreScanner.kt` | Reads songs from Android's media index |
| `data/FolderScanner.kt` | Scans folders picked with the system folder picker, caches tags |
| `data/LibraryRepository.kt` | Builds albums, artists and the folder tree; watches for changes |
| `data/ArtworkLoader.kt` | Finds thumbnails (system thumbnail, embedded art, folder images) |
| `data/Lyrics.kt` | LRC parsing and lyrics lookup |
| `playback/PlaybackService.kt` | Media3 ExoPlayer + media session (notification, headset, resume) |
| `playback/SmartShuffle.kt` | Shuffle order that respects the current song and "Play next" |
| `playback/PlaybackExtras.kt` | Sleep timer and equalizer/effects |
| `playback/PlayerConnection.kt` | The UI's handle on the player |
| `ui/` | Jetpack Compose screens |
