#!/usr/bin/env bash
# Installs the APK on the emulator, walks through the main screens and saves screenshots.
# Fails if the app crashes or stops responding.
set -uo pipefail
APK="$1"
MEDIA="$2"
OUT="$3"
PKG=com.jambo199.vividmusic
HERE="$(cd "$(dirname "$0")" && pwd)"
mkdir -p "$OUT"

dump() { # writes the UI hierarchy to /tmp/ui.xml; prints why if it can't
  local out
  out=$(adb shell uiautomator dump /sdcard/ui.xml 2>&1)
  if ! echo "$out" | grep -q "dumped to"; then
    echo "   (uiautomator: ${out//$'\n'/ })"
    return 1
  fi
  adb pull /sdcard/ui.xml /tmp/ui.xml > /dev/null 2>&1
}
clear_dialogs() { # "X isn't responding" dialogs from the emulator's own apps
  adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS > /dev/null 2>&1
  if dump && grep -q "isn't responding" /tmp/ui.xml; then
    xy=$(python3 "$HERE/ui.py" /tmp/ui.xml "Wait") && adb shell input tap $xy && echo "   (dismissed a system 'not responding' dialog)"
  fi
}
shot() {
  sleep "${2:-2}"
  clear_dialogs
  adb exec-out screencap -p > "$OUT/$1.png" && echo "📸 $1"
  if dump; then echo "   texts: $(python3 "$HERE/ui.py" /tmp/ui.xml --texts)"; fi
}
launch() { adb shell am start -W -n "$PKG/.MainActivity" "$@" > /dev/null; }
shortcut() { launch --es vivid.shortcut "$1"; }
tap() { # tap on the element labelled $1
  clear_dialogs
  for _ in 1 2 3; do
    if dump; then
      if xy=$(python3 "$HERE/ui.py" /tmp/ui.xml "$1"); then
        adb shell input tap $xy
        echo "👆 $1 at $xy"
        return 0
      fi
      echo "   '$1' not among: $(python3 "$HERE/ui.py" /tmp/ui.xml --texts | cut -c1-400)"
    fi
    sleep 1
  done
  echo "⚠️  could not find '$1' on screen"
  return 1
}

longtap() { # long-press the element labelled $1
  if dump && xy=$(python3 "$HERE/ui.py" /tmp/ui.xml "$1"); then
    set -- $xy
    adb shell input swipe "$1" "$2" "$1" "$2" 900
    echo "👆 long-pressed at $xy"
    return 0
  fi
  echo "⚠️  could not find '$1' to long-press"
  return 1
}

adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard || true
# Don't let ANR/crash dialogs from the emulator's own apps cover the screenshots.
adb shell settings put global hide_error_dialogs 1 || true
adb shell settings put secure anr_show_background 0 || true
adb logcat -c

echo "== Copying test music"
adb shell mkdir -p /sdcard/Music /sdcard/Download
adb push "$MEDIA/Music/." /sdcard/Music/ > /dev/null
adb push "$MEDIA/Download/." /sdcard/Download/ > /dev/null
expected=$(find "$MEDIA" -type f | wc -l)
for i in $(seq 1 30); do
  # Wait until the scanner has finished reading tags (files first appear with is_music=0).
  found=$(adb shell "content query --uri content://media/external/audio/media --projection _display_name --where 'is_music=1'" 2>/dev/null | grep -c "Row:" || true)
  echo "media index has $found/$expected music files"
  [ "$found" -ge "$expected" ] && break
  if [ "$i" -eq 5 ]; then
    # Nudge the media scanner if adb push wasn't picked up automatically.
    find "$MEDIA" -type f | while read -r f; do
      rel="${f#"$MEDIA"/}"
      adb shell "am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d 'file:///sdcard/${rel// /%20}'" > /dev/null 2>&1
    done
  fi
  sleep 2
done

echo "== Installing"
adb install -r "$APK"

launch
shot 01-welcome 4
adb shell pm grant $PKG android.permission.READ_MEDIA_AUDIO
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS
adb shell am force-stop $PKG
launch
shot 02-songs 6
if longtap "Alpha Wave"; then
  sleep 1
  tap "Beta Drift"
  shot 02b-multi-select 2
  adb shell input keyevent KEYCODE_BACK
fi
shortcut albums;    shot 03-albums 3
shortcut artists;   shot 04-artists 3
shortcut folders;   shot 05-folders 3
tap "Music" && shot 06-folder-music 3
tap "Neon Dreams" && shot 07-folder-neon 3
shortcut playlists; shot 08-playlists 3

echo "== Playback"
shortcut shuffle;   shot 09-player 6
shot 10-player-later 4
adb shell dumpsys media_session | grep -E "state=PlaybackState|description=" | head -6
adb shell input keyevent KEYCODE_MEDIA_PAUSE
sleep 1
tap "Lyrics" && shot 11-lyrics 2
tap "Lyrics"
tap "Queue" && shot 12-queue 3
adb shell input keyevent KEYCODE_BACK
adb shell input keyevent KEYCODE_MEDIA_PLAY
adb shell cmd statusbar expand-notifications
shot 13-notification 3
adb shell cmd statusbar collapse
adb shell input keyevent KEYCODE_BACK
shot 14-mini-player 3

echo "== Lyrics song"
shortcut search
sleep 2
adb shell input text "sing"
shot 15-search 3
tap "Sing Along" && sleep 2 && shortcut player && sleep 2 && tap "Lyrics" && shot 16-synced-lyrics 5

echo "== Other screens"
shortcut settings;  shot 17-settings 3
shortcut equalizer; shot 18-equalizer 3
shortcut favorites; shot 19-favourites 3

echo "== Dark mode and landscape"
adb shell cmd uimode night yes
shortcut songs;  shot 20-dark-songs 4
shortcut player; shot 21-dark-player 4
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 1
shot 22-landscape 4
adb shell settings put system user_rotation 0
adb shell cmd uimode night no

echo "== Restart keeps the queue"
adb shell am force-stop $PKG
launch
shot 23-after-restart 5

echo "== Checking for crashes"
adb logcat -d > "$OUT/logcat.txt"
grep -E "ServiceBitmapLoader|PlaybackService|ExoPlayerImpl|MediaSessionService" "$OUT/logcat.txt" | tail -25 || true
echo "-- system errors mentioning our session:"
grep -B6 -A3 "sanitizeMediaMetadata" "$OUT/logcat.txt" | head -40 || true
grep -E "vividmusic|AndroidRuntime" "$OUT/logcat.txt" | grep -E " E " | grep -v "jdwp" | tail -30 || true
if grep -qE "FATAL EXCEPTION|ANR in $PKG" "$OUT/logcat.txt"; then
  echo "❌ The app crashed:"
  grep -A 40 -E "FATAL EXCEPTION|ANR in" "$OUT/logcat.txt" | head -150
  exit 1
fi
echo "✅ No crashes"
