# PersonalExeMadeByAI

A Windows desktop app with four sections:

1. **Cache Cleaner** - choose what to clean, scan first, then delete selected cache folders.
2. **Letterboxd / Films** - load your public favourite films from your Letterboxd username, show trending films, and get recommendations with an optional TMDb API key.
3. **Music** - show live Apple Music most-played songs/albums and local year-flashback picks.
4. **Updater** - check for new versions from a GitHub Release or JSON manifest, then download and replace the EXE automatically.

Current app version: **1.1.1**

## Safety notes

The cache cleaner only targets cache/temp folders. It does **not** target cookies, saved passwords, documents, downloads, music files, pictures, videos, game saves, or account folders.

Close browsers, Discord, Spotify, Steam and games before cleaning for best results. Locked files are skipped.

## Why no Letterboxd password login?

Letterboxd's API access is request-only, so this app uses your public Letterboxd profile instead of storing your password. Press **Open Letterboxd login** if you want to log in through your normal browser.

For proper favourite-based recommendations, add a TMDb API key in the app. The key is session-only in the UI and is not saved by the app.

## Run without building

Double-click:

```bat
run.bat
```

## Build the Windows exe

Double-click:

```bat
build_exe.bat
```

The finished app will appear at:

```text
dist\PersonalExeMadeByAI.exe
```

## Auto-updater setup

The updater is built into the app now, but it needs somewhere online to check for updates.

### Easiest option: GitHub Releases

1. Make a GitHub repo, for example `yourname/PersonalExeMadeByAI`.
2. Build the EXE with `build_exe.bat`.
3. Create a GitHub Release with a newer tag, for example `v1.2.0`.
4. Upload `dist\PersonalExeMadeByAI.exe` as a release asset.
5. In the app, open the **Updater** tab.
6. Put this as the update source:

```text
yourname/PersonalExeMadeByAI
```

The app will read the latest GitHub release, find the `.exe` or `.zip`, download it, close itself, replace the old EXE, and reopen.

### Alternative: JSON manifest

Host a tiny JSON file somewhere public, then paste its URL into the app's **Updater** tab.

Example:

```json
{
  "latest_version": "1.2.0",
  "download_url": "https://example.com/PersonalExeMadeByAI.exe",
  "sha256": "optional_sha256_here",
  "release_notes": "Cleaner log polish, music fixes, more chaos.",
  "homepage_url": "https://example.com/releases/PersonalExeMadeByAI-1.2.0",
  "mandatory": false
}
```

Notes:

- `latest_version` must be newer than `src/version.py`.
- `download_url` must point to a `.exe` or `.zip` containing an EXE.
- `sha256` is optional, but safer.
- One-click self-replace only works when running the packaged EXE. If you run via `run.bat`, it will download the update into an `updates` folder instead.

## Requirements

- Windows 10/11
- Python 3.11 or newer recommended
- Internet connection for Letterboxd, TMDb, Apple Music chart data and update checks

## Editing the app

Main files:

- `src/app.py` - UI
- `src/cache_cleaner.py` - cache scan/clean logic
- `src/letterboxd_client.py` - Letterboxd/TMDb film fetching
- `src/music_client.py` - Apple Music charts + year flashback picks
- `src/auto_updater.py` - update checking, downloading and EXE replacement
- `src/version.py` - current version number
- `src/settings_store.py` - saved username/country/window/updater settings
