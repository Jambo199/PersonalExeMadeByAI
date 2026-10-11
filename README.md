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
- A bottom bar to switch between Home, Library, Folders and NCS (a side rail in landscape)

**NCS (NoCopyrightSounds)**
- Every NCS release straight from [ncs.io](https://ncs.io), **newest** or **most popular** first
  (ranked by plays on Deezer's charts), with genre and mood filters
- Search by song or artist; artists you search for show up first, and each artist has a page
  with all their NCS releases, most popular first (play, shuffle or save them all)
- **Artists**: browse every NCS artist – most played first, A–Z, Z–A or recently joined – or
  search for one (Jim Yosef, Elektronomia, Alan Walker…)
- Tap ⋮ (or long-press) a song for **all its details**: artists, release date and year, genres,
  moods, versions (regular/instrumental), length, chart place, and the credit text NCS asks you
  to use in videos (copy it in one tap), plus links to ncs.io and the official video
- Tap to stream (no ads, no account), or **save** songs into Music › NCS so they join your library
  and play offline. Save one song, the list you're looking at, or every NCS song (Wi-Fi only if
  you like); songs you already have are skipped, so running it again picks up new releases
- Saved songs play from the phone even when you start them from the NCS page
- **New release alerts**: a notification when NCS puts out new songs (checked twice a day; turn it
  off in Settings › NCS)

**Library**
- Songs, Albums, Artists and Playlists tabs, all with album-art thumbnails – reorder or hide tabs in Settings
- Artist tags like "A; B" or "A feat. B" list the song under each artist (choose the separators)
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
- One music notification with a **progress bar** that moves as the song plays (also on phones like
  vivo that draw their own music card), and the **line being sung** in place of the artist for songs
  with synced lyrics (lock screen, car screens…). Note: vivo's island only shows music from apps on
  vivo's own list, so sideloaded players can't appear there
- Shows up in Android's quick-settings media player, which can resume your last song even after the app was closed
- Favourite button in the notification
- Smart shuffle: the current song keeps playing, *Play next* really plays next even when shuffled,
  and songs by the same artist are spread out instead of bunching up (can be turned off)
- Repeat all / one, gapless playback, skip silence
- Playback speed and pitch
- Sleep timer (minutes or end of song) with a gentle fade-out
- Equalizer with device presets, your own saved presets, per-band sliders, bass boost, surround and loudness
- Volume normalisation from ReplayGain tags (per song or per album), with a pre-amp and an
  adjustment for untagged songs
- Fade between songs, a short fade on play/pause, and "pause on repeat"
- A-B repeat to loop part of a song
- Audiobooks, mixes and other tracks over 15 minutes continue where you stopped
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

**Fun stuff** (the extras are also one tap away in Home's "More to try" row)
- **Live visualizer** over the artwork (bars, wave or mirror), and artwork that **bounces to the beat**
- **Vinyl record** player: the artwork spins on a record while music plays
- **Aurora** player background: soft clouds of the artwork's colours drifting while music plays
- **Party mode** (player ⋮ menu): the whole screen becomes a light show to the beat – sweeping
  beams, a ring of bars and colours that change on every beat (strobe flashes only if you turn them on),
  plus the phone's **flashlight flashing to the beat** and a buzz on every beat if you like
- **Sound presets**: Nightcore, Slowed, Slowed + reverb, Chipmunk, Deep voice and Concert hall,
  plus room / hall / plate **reverb** on anything
- **Karaoke lyrics**: the line being sung fills with colour as it goes; long-press a line to
  share it as a picture card in the song's colours
- **Add lyrics** to songs that have none by pasting them in
- **Sync lyrics yourself**: songs with plain lyrics get a "Sync these lyrics yourself" button –
  tap along as each line starts and they're saved as a synced .lrc for that song
- **Guess the song**: hear a few seconds and pick the right title (or **name the artist**) – from
  your songs or NCS hits, with points for speed and streaks
- **Voice search**: tap the mic in Search and say a name – or "play Alan Walker", "shuffle
  synthwave", "play my favourites" to start the music straight away
- **Share as a picture** (player ⋮ menu): a "now listening" card in the song's colours
- **Your Wrapped**: a story of your top songs, artists, minutes, favourite genre and day, and your
  listening personality, ready to share
- **Swipe to discover**: songs you've never played, one card at a time with a preview – swipe
  right to love it, left to pass; or swipe through NCS songs and save the ones you like
- **Listening streaks**: days in a row with music, shown on Home and the stats page
- **Badges** to unlock (Century, Marathon, On fire, Night owl, Party starter, Crate digger, Golden ears…) on
  the stats page, celebrated with confetti
- **Song of the day** and three **daily mixes** on Home, made from the genres and artists you play
- **What's your mood?** on Home: Chill, Energetic, Happy, Focus, Party or Mellow – a mix from your
  songs' genres, topped up with NCS songs of that mood when you haven't got many
- **Focus timer** (overflow menu): rounds of music with quiet breaks (25/5, 50/10 or 15/3 minutes);
  the music fades out for each break and comes back by itself
- **Listening clock** on the stats page (and in Wrapped): early bird, daytime groover, evening
  listener or night owl
- **Double-tap the artwork** to love a song, with a heart that pops up
- **Confetti** when you unlock a badge or beat your best quiz score
- **Car mode** (overflow menu): huge buttons on a dark screen that stays on, swipe anywhere to skip
- **Shake to skip** (Settings)
- **Music alarm**: wake up to your favourites, all songs, the song of the day or a daily mix –
  once, weekdays or the days you pick, gently rising over a minute, with Snooze and Stop in the
  notification (overflow menu › Music alarm)

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
| `online/` | NCS (ncs.io) client, most-played chart, downloads |
| `ui/online/` | The NCS page, artist pages and song info |
| `playback/PlaybackService.kt` | Media3 ExoPlayer + media session (notification, headset, resume) |
| `playback/SmartShuffle.kt` | Shuffle order that respects the current song and "Play next" |
| `playback/PlaybackExtras.kt` | Sleep timer and equalizer/effects |
| `playback/PlayerConnection.kt` | The UI's handle on the player |
| `ui/` | Jetpack Compose screens |
