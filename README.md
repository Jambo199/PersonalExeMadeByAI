# Vivid Music (Android)

A music player for Android phones (built and tested with vivo / Funtouch OS / OriginOS in mind).
It plays the music stored on your phone or SD card, shows album art everywhere, and lets you browse
and play straight from folders.

## Install on your phone

**Option A – direct download (one-time setup).** GitHub only lets the build publish the APK as a
release if workflows may write to the repo:

1. On GitHub open **Settings → Actions → General → Workflow permissions**, choose
   **Read and write permissions** and save. (Then re-run the latest *Android app (Vivid Music)* run,
   or push any change.)
2. On your phone open **https://github.com/Jambo199/PersonalExeMadeByAI/releases/tag/vivid-music-latest**
   and tap **VividMusic.apk**.

**Option B – from the build page.** Open the
[Actions tab](https://github.com/Jambo199/PersonalExeMadeByAI/actions/workflows/android.yml) (signed in),
pick the latest green run and download the **vivid-music-apk** artifact. It's a zip containing the APK.

Then on the phone:

1. Open the downloaded APK. If Android asks, allow your browser / file manager to
   **install unknown apps**, then tap **Install**.
2. Open **Vivid Music** and tap **Allow access to music**.

Every new build installs over the old one and keeps your playlists, favourites and settings.

### Updates

Once it's installed you don't need to come back here: each time Vivid Music opens it checks the
release page, and if there's a newer build it shows **Update available** at the top of the screen.
Tap it – the app downloads the new version, checks it's genuine (same app, newer build, matching
checksum) and opens Android's install screen; tap **Update** there. The first time, Android asks you
to allow *Install unknown apps* for Vivid Music – switch it on and go back, and the install continues.
You can also check by hand, or turn the automatic check off, in **Settings → App updates**. The check
only downloads a tiny `version.json` from GitHub; nothing about you or your music is sent.

### vivo tips (keep music playing with the screen off)

vivo's battery saver can stop background apps. In Vivid Music go to **Settings → Background playback (vivo)**:

- **Allow unrestricted battery use** – opens Android's battery optimisation prompt.
- **vivo background & auto-start settings** – opens vivo's own manager; allow
  *High background power consumption* / *Auto-start* for Vivid Music.

You can also lock the app in the recent-apps screen (pull the card down).

## Features

**Home**
- A greeting with *Shuffle all*, and tiles for Favourites, Recently played, Most played and Recently added
- **Quick picks** – a mix of what you play most, your favourites and songs you haven't heard in a while
- **Keep listening**, **Forgotten favourites**, **Recently added**, your **top artists** and albums you haven't played yet
- **Your listening** stats: time listened, plays, a last-7-days chart, most played songs, artists and albums, and a library summary
- A bottom bar to switch between Home, Library and Folders (a side rail in landscape)

**Library**
- Songs, Albums, Artists and Playlists tabs, all with album-art thumbnails
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
- Shows up in Android's quick-settings media player, which can resume your last song even after the app was closed
- Favourite button in the notification
- Smart shuffle: the current song keeps playing, *Play next* really plays next even when shuffled
- Repeat all / one, gapless playback, skip silence
- Playback speed and pitch
- Sleep timer (minutes or end of song) with a gentle fade-out
- Equalizer with device presets, per-band sliders, bass boost, surround and loudness
- Volume normalisation from ReplayGain tags (per song or per album)
- Pauses when headphones are unplugged; handles calls and other apps' audio
- Resumes your queue and position after closing the app or restarting the phone
- "Open with" support: play audio files straight from your file manager, WhatsApp, Downloads…

**Now playing**
- Big artwork over a blurred copy of itself (or a colour gradient); swipe it to skip
- Synced lyrics (embedded lyrics or `.lrc` files) that follow the song – tap a line to jump there;
  you can also link an `.lrc` file to any song
- Songs without lyrics are looked up on [LRCLIB](https://lrclib.net) when you open the lyrics
  (only the title, artist, album and length are sent; turn it off in Settings)
- Mini player: tap or swipe up to open, swipe sideways to skip
- Drag-to-reorder queue, remove songs, save the queue as a playlist
- Audio quality badge (format, sample rate / bitrate)

**Playlists**
- Your own playlists (create, rename, drag to reorder, delete)
- Import `.m3u`/`.m3u8` playlists from other players, and export any playlist as M3U
- Automatic Favourites, Recently added, Recently played and Most played

**Everything else**
- Colours that follow the album art of the song that's playing, Material You colours from your
  wallpaper (Android 12+), or 18 accent colours
- Light / dark / system theme and a pure-black AMOLED mode
- Landscape layout
- Long-press songs to **select several** – play, queue, add to a playlist, share or delete them together
- Song details (bitrate, sample rate, size, path), share, and delete from device
- **Home-screen widget** with artwork and previous / play-pause / next buttons
- Home-screen shortcuts: long-press the icon for Shuffle all, Favourites, Folders and Search
- **Backup and restore** of settings, playlists, favourites and listening history to one file –
  songs are matched by file path, so it also works on a new phone

## Building it yourself

GitHub Actions builds the APK on every push (`.github/workflows/android.yml`), runs it on an
Android 14 emulator with a generated test library, and publishes it as the `vivid-music-latest`
pre-release. To build locally you need JDK 17 and the Android SDK:

```bash
./gradlew assembleRelease
# app/build/outputs/apk/release/app-release.apk
```

Release builds are signed with `app/vividmusic.jks` so updates install over older builds.
Because this repository is public, that key is public too: anyone could sign an APK that Android
would accept as an update. Only install Vivid Music builds from this repo's own Actions runs or
releases. To use a private key instead, set `SIGNING_KEYSTORE_PATH`, `SIGNING_STORE_PASSWORD`,
`SIGNING_KEY_ALIAS` and `SIGNING_KEY_PASSWORD` when building (switching keys means uninstalling the
old build once).

## Code map

Kotlin sources live in `app/src/main/java/com/jambo199/vividmusic/`.

| Path | What it does |
| --- | --- |
| `data/MediaStoreScanner.kt` | Reads songs from Android's media index |
| `data/FolderScanner.kt` | Scans folders picked with the system folder picker, caches tags |
| `data/LibraryRepository.kt` | Builds albums, artists and the folder tree; watches for changes |
| `data/ArtworkLoader.kt` | Finds thumbnails (system thumbnail, embedded art, folder images) |
| `data/Lyrics.kt` | LRC parsing and lyrics lookup |
| `data/OnlineLyrics.kt` | LRCLIB look-ups, kept on the phone |
| `data/HomeFeed.kt` | Quick picks and the other Home rows |
| `data/Stats.kt` | Numbers for the listening stats screen |
| `data/Backup.kt` | Backup file and restoring it |
| `playback/PlaybackService.kt` | Media3 ExoPlayer + media session (notification, headset, resume) |
| `playback/SmartShuffle.kt` | Shuffle order that respects the current song and "Play next" |
| `playback/PlaybackExtras.kt` | Sleep timer and equalizer/effects |
| `playback/PlayerConnection.kt` | The UI's handle on the player |
| `ui/` | Jetpack Compose screens |
