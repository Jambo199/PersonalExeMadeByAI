#!/usr/bin/env bash
# Installs the APK on the emulator, walks through the main screens and saves screenshots.
# Fails if the app crashes or stops responding.
set -uo pipefail
APK="$1"
MEDIA="$2"
OUT="$3"
OLD_APK="${4:-}"   # same app numbered build 1, for testing the in-app updater
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
tap() { # tap on the element labelled $1 ($2 = "top" picks the highest match on screen)
  clear_dialogs
  for _ in 1 2 3; do
    if dump; then
      if xy=$(python3 "$HERE/ui.py" /tmp/ui.xml "$1" ${2:-}); then
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

focus_report() { # which text box has focus and what's in it ($1 = when)
  if dump; then
    python3 - "$1" <<'PY'
import sys, xml.etree.ElementTree as ET
nodes = list(ET.parse("/tmp/ui.xml").getroot().iter("node"))
edits = [n for n in nodes if n.get("class", "").endswith("EditText")]
focused = [n for n in nodes if n.get("focused") == "true"]
print("   [%s] text boxes: %s; focused: %s" % (
    sys.argv[1],
    [(n.get("text"), n.get("focused")) for n in edits],
    [(n.get("class", "").split(".")[-1], n.get("text") or n.get("content-desc")) for n in focused],
))
PY
  fi
}
type_into() { # tap the text box labelled $1, then type $2 and press Enter, reporting focus on the way
  tap "$1" || return 1
  focus_report "after tap"
  sleep 1
  focus_report "1s later"
  adb shell input text "$2"
  focus_report "after typing"
  adb shell input keyevent KEYCODE_ENTER
  sleep 1
  focus_report "after enter"
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
# Shared storage can lag a little behind boot; wait until it's writable.
for _ in $(seq 1 30); do
  adb shell "mkdir -p /sdcard/Music /sdcard/Download && test -w /sdcard/Music" 2> /dev/null && break
  sleep 2
done
push_music() {
  adb push "$MEDIA/Music/." /sdcard/Music/ 2>&1 | tail -1
  adb push "$MEDIA/Download/." /sdcard/Download/ 2>&1 | tail -1
}
push_music
expected=$(find "$MEDIA" -type f | wc -l)
found=0
for i in $(seq 1 45); do
  # Wait until the scanner has finished reading tags (files first appear with is_music=0).
  found=$(adb shell "content query --uri content://media/external/audio/media --projection _display_name --where 'is_music=1'" 2>/dev/null | grep -c "Row:" || true)
  echo "media index has $found/$expected music files"
  [ "$found" -ge "$expected" ] && break
  if [ "$i" -eq 8 ] || [ "$i" -eq 20 ]; then
    echo "   re-copying and nudging the media scanner"
    push_music
    find "$MEDIA" -type f | while read -r f; do
      rel="${f#"$MEDIA"/}"
      adb shell "am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d 'file:///sdcard/${rel// /%20}'" > /dev/null 2>&1
    done
  fi
  sleep 2
done
if [ "$found" -lt "$expected" ]; then
  echo "⚠️  the emulator's media scanner didn't index the test songs; library screens will be empty"
  adb shell ls -R /sdcard/Music /sdcard/Download | head -20
fi

if [ -n "$OLD_APK" ] && [ -f "$OLD_APK" ]; then
  echo "== In-app updater (installed as build 1; the release page has a newer build)"
  adb install -r "$OLD_APK" > /dev/null
  adb shell pm grant $PKG android.permission.READ_MEDIA_AUDIO
  adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS
  adb shell appops set $PKG REQUEST_INSTALL_PACKAGES allow
  launch
  shot 00a-update-available 8
  if tap "Update"; then
    for _ in $(seq 1 40); do
      sleep 2
      adb shell dumpsys window | grep -q "mCurrentFocus.*packageinstaller" && break
    done
    shot 00b-update-installer 2
    adb shell dumpsys window | grep -E "mCurrentFocus" | head -1
  fi
  adb logcat -d | grep -E "UpdateManager" | tail -5
  adb shell input keyevent KEYCODE_BACK
  adb shell input keyevent KEYCODE_HOME
  adb uninstall $PKG > /dev/null
fi

echo "== Installing"
adb install -r "$APK"

launch
shot 01-welcome 4
adb shell pm grant $PKG android.permission.READ_MEDIA_AUDIO
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS
adb shell am force-stop $PKG
launch
shot 02-home 6
shortcut songs; shot 02a-songs 3
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
if tap "Speed"; then
  shot 10b-sound-presets 2
  tap "Slowed + reverb" && sleep 3
  adb logcat -d | grep -E "Reverb unavailable|AudioEffects" | tail -3
  tap "Normal"
  adb shell input keyevent KEYCODE_BACK
fi
adb shell dumpsys media_session | grep -E "state=PlaybackState|description=" | head -6
adb shell input keyevent KEYCODE_MEDIA_PAUSE
sleep 1
echo "after pause (state=2 means paused):"
adb shell dumpsys media_session | grep -m1 -oE "state=PlaybackState \{state=[0-9]+" || true
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
shortcut home; shot 14b-home-after-listening 3
tap "Library" && shot 14c-bottom-nav-library 2
tap "Home"
sleep 1
adb shell input swipe 540 1900 540 500 400
adb shell input swipe 540 1900 540 500 400
shot 14d-home-scrolled 2
tap "Synthwave" && shot 14e-genre 3
tap "Home"

echo "== Lyrics song"
shortcut search
sleep 2
adb shell input text "sing"
shot 15-search 3
tap "Sing Along" && sleep 2 && shortcut player && sleep 2 && tap "Lyrics" && shot 16-synced-lyrics 5
echo "lyric line shown to the lock screen / Dynamic Island (as the artist):"
adb shell dumpsys media_session | grep -m2 "description=" || true
adb shell cmd statusbar expand-notifications
shot 16c-notification-lyrics 3
adb shell cmd statusbar collapse
if longtap "These lyrics are synced"; then
  shot 16d-share-lyric 2
  tap "Cancel"
fi

online_song=$(ls "$MEDIA/Music/Online/"*.mp3 2> /dev/null | head -1)
if [ -n "$online_song" ]; then
  echo "== Online lyrics (LRCLIB)"
  title=$(basename "$online_song" .mp3)
  shortcut songs
  tap "$title" && sleep 2 && shortcut player && sleep 2 && tap "Lyrics" && shot 16b-online-lyrics 8
  adb logcat -d | grep -E "OnlineLyrics" | grep -vE "OnlineLyrics:[[:space:]]+at " | tail -8
fi

echo "== NCS"
text_after() { # prints the first visible text after the label $1 (e.g. the first song under a heading)
  dump > /dev/null && python3 - "$1" <<'PY'
import sys, xml.etree.ElementTree as ET
texts = [(n.get("text") or "").strip() for n in ET.parse("/tmp/ui.xml").getroot().iter("node")]
texts = [t for t in texts if t]
label = sys.argv[1]
if label in texts and texts.index(label) + 1 < len(texts):
    print(texts[texts.index(label) + 1])
PY
}
shortcut ncs; shot 32-ncs 15
first_song=$(text_after "Save")
echo "first NCS song: $first_song"
if [ -n "$first_song" ] && tap "$first_song"; then
  sleep 10
  shortcut player
  shot 33-ncs-playing 3
  echo "media session while streaming:"
  adb shell dumpsys media_session | grep -E "state=PlaybackState|description=" | head -4
fi
shortcut ncs
first_genre=$(text_after "All genres")
echo "first genre: $first_genre"
if [ -n "$first_genre" ] && tap "$first_genre"; then shot 34-ncs-genre 10; fi
if tap "Save to phone"; then
  sleep 25
  shot 35-ncs-saved 2
  adb shell ls -l "/sdcard/Music/NCS" 2>&1 | head -5
fi
tap "Save" && shot 36-ncs-save-dialog 2
adb shell input keyevent KEYCODE_BACK
echo "== NCS most popular, song info and artist pages"
shortcut ncs
tap "All genres"
if tap "Most popular"; then shot 37-ncs-popular 30; fi
if tap "Song info"; then
  shot 38-ncs-info 6
  first_artist=$(text_after "Artists")
  echo "first artist in the song info: $first_artist"
  if [ -n "$first_artist" ] && tap "$first_artist"; then shot 39-ncs-artist 10; fi
fi
shortcut ncs
tap "Newest"
if type_into "Search NCS songs and artists" "alan%swalker"; then
  shot 40-ncs-search-artist 10
  if tap "Alan Walker" top; then shot 41-ncs-alan-walker 10; fi
fi
echo "== NCS artists"
shortcut ncs
if tap "Artists"; then shot 42-ncs-artists 20; fi
if tap "A–Z"; then shot 43-ncs-artists-az 8; fi
if type_into "Search NCS artists" "jim"; then
  shot 44-ncs-artist-search 8
  if tap "Jim Yosef" top; then shot 45-ncs-jim-yosef 10; fi
fi
adb logcat -d | grep -E "NcsViewModel" | tail -14
adb logcat -d | grep -E "NcsViewModel|ExoPlayerImplInternal.*rror|DownloadManager" | tail -5

echo "== Fun: Guess the song and Wrapped"
shortcut quiz; shot 46-quiz 4
if tap "Start"; then
  shot 47-quiz-question 2
  first_choice=$(text_after "Which song is this?")
  echo "first choice: $first_choice"
  if [ -n "$first_choice" ] && tap "$first_choice"; then shot 48-quiz-answer 2; fi
fi
adb shell input keyevent KEYCODE_BACK
shortcut wrapped; shot 49-wrapped 3
adb shell input tap 900 1400; shot 50-wrapped-minutes 2
for _ in 1 2 3 4 5 6 7 8; do adb shell input tap 900 1400; sleep 0.5; done
shot 51-wrapped-summary 2
adb shell input keyevent KEYCODE_BACK

echo "== Swipe to discover"
shortcut discover; shot 56-discover 5
if tap "Love it"; then shot 57-discover-next 3; fi
tap "Pass" && sleep 1
adb shell input swipe 300 1000 1000 1050 250; sleep 1
echo "-- favourites after discovering: $(adb shell run-as $PKG cat files/user_data.json 2>/dev/null | python3 -c 'import json,sys; print(len(json.load(sys.stdin).get("favorites",[])))' 2>/dev/null)"
if tap "NCS songs"; then shot 58-discover-ncs 10; fi
adb shell input keyevent KEYCODE_BACK

echo "== Music alarm"
adb shell input keyevent KEYCODE_MEDIA_PAUSE
shortcut alarm; shot 52-alarm 3
tap "Weekdays"
if tap "Try it now"; then
  sleep 6
  echo "-- media session after 'Try it now':"
  adb shell dumpsys media_session | grep -E "state=PlaybackState" | head -3
  adb shell dumpsys notification --noredact | grep -E "android.title=|android.text=" | grep -iE "morning|waking" | head -4
  adb shell cmd statusbar expand-notifications
  shot 53-alarm-ringing 3
  if tap "Stop"; then
    sleep 2
    echo "-- media session after Stop:"
    adb shell dumpsys media_session | grep -E "state=PlaybackState" | head -3
  fi
  adb shell cmd statusbar collapse
fi
if tap "Wake up to music"; then
  shot 54-alarm-picker 2
  tap "Set" && shot 55-alarm-set 2
  echo "-- alarms set by the app:"
  adb shell dumpsys alarm | grep -A4 "$PKG" | grep -E "type=|origWhen|AlarmClock|RTC" | head -8
fi
adb shell input keyevent KEYCODE_BACK

echo "== Other screens"
shortcut settings;  shot 17-settings 3
shortcut equalizer; shot 18-equalizer 3
shortcut favorites; shot 19-favourites 3
shortcut stats;     shot 19b-stats 3
adb shell input swipe 540 1800 540 600 400
adb shell input swipe 540 1800 540 600 400
shot 19c-badges 2
echo "== Vinyl record player"
shortcut settings
tap "Vinyl record" || { adb shell input swipe 540 1700 540 1000 400; sleep 1; tap "Vinyl record"; }
shortcut player; shot 21b-vinyl 5
shortcut settings
tap "Square" || { adb shell input swipe 540 1700 540 1000 400; sleep 1; tap "Square"; }

echo "== Aurora background"
shortcut settings
tap "Aurora" || { adb shell input swipe 540 1700 540 1000 400; sleep 1; tap "Aurora"; }
shortcut player; shot 21c-aurora 5
shortcut settings
tap "Blurred artwork" || { adb shell input swipe 540 1700 540 1000 400; sleep 1; tap "Blurred artwork"; }

echo "== Party mode"
adb shell input keyevent KEYCODE_MEDIA_PLAY
shortcut party; shot 21d-party 4
adb shell input tap 540 300; shot 21e-party-lights 3
adb shell input keyevent KEYCODE_BACK

echo "== Dark mode and landscape"
adb shell cmd uimode night yes
shortcut home;      shot 20-dark-home 4
shortcut songs;     shot 20a-dark-songs 3
shortcut albums;    shot 20b-dark-albums 3
shortcut artists;   shot 20c-dark-artists 3
shortcut folders;   shot 20d-dark-folders 3
shortcut playlists; shot 20e-dark-playlists 3
shortcut settings;  shot 20f-dark-settings 3
shortcut stats;     shot 20g-dark-stats 3
shortcut player; shot 21-dark-player 4
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 1
shot 22-landscape 4
shortcut home; shot 22b-landscape-home 3
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
