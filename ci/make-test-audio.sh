#!/usr/bin/env bash
# Builds a small fake music library (tags, cover art, lyrics, odd file names) for the emulator test.
set -euo pipefail
OUT="${1:-testmedia}"
rm -rf "$OUT"
mkdir -p "$OUT/Music/Neon Dreams" "$OUT/Music/Chill" "$OUT/Music/Lyrics" "$OUT/Download/Mixtape" "$OUT/covers"

if ! command -v ffmpeg >/dev/null; then
  sudo apt-get update -qq && sudo apt-get install -y -qq ffmpeg >/dev/null
fi
ff() { ffmpeg -hide_banner -loglevel error -y "$@"; }

ff -f lavfi -i "mandelbrot=s=480x480" -frames:v 1 "$OUT/covers/neon.png"
ff -f lavfi -i "testsrc2=s=480x480" -frames:v 1 "$OUT/covers/chill.png"
ff -f lavfi -i "smptehdbars=s=480x480" -frames:v 1 "$OUT/covers/night.png"

mp3_with_cover() { # out freq secs cover title artist album track year
  ff -f lavfi -i "sine=frequency=$2:duration=$3" -i "$4" -map 0:a -map 1:v \
    -c:a libmp3lame -b:a 96k -c:v png -disposition:v attached_pic -id3v2_version 3 \
    -metadata title="$5" -metadata artist="$6" -metadata album="$7" -metadata track="$8" -metadata date="$9" \
    -metadata genre="Synthwave" "$1"
}

mp3_with_cover "$OUT/Music/Neon Dreams/01 Alpha Wave.mp3" 440 48 "$OUT/covers/neon.png" "Alpha Wave" "Vivid Tester" "Neon Dreams" "1/3" 2024
mp3_with_cover "$OUT/Music/Neon Dreams/02 Beta Drift.mp3" 523 52 "$OUT/covers/neon.png" "Beta Drift" "Vivid Tester" "Neon Dreams" "2/3" 2024
mp3_with_cover "$OUT/Music/Neon Dreams/03 Gamma Rise.mp3" 659 45 "$OUT/covers/neon.png" "Gamma Rise" "Vivid Tester" "Neon Dreams" "3/3" 2024

ff -f lavfi -i "sine=frequency=330:duration=60" -i "$OUT/covers/chill.png" -map 0:a -map 1:v \
  -c:a aac -b:a 96k -c:v png -disposition:v attached_pic \
  -metadata title="Sunset Boulevard" -metadata artist="Lo-Fi Robot" -metadata album="Evening Tapes" -metadata track=1 -metadata date=2023 -metadata genre="Lo-Fi" \
  "$OUT/Music/Chill/Sunset Boulevard.m4a"

ff -f lavfi -i "sine=frequency=294:duration=55" -i "$OUT/covers/night.png" -map 0:a -map 1:v \
  -c:a flac -c:v png -disposition:v attached_pic \
  -metadata title="Night Drive" -metadata artist="Lo-Fi Robot" -metadata album="Evening Tapes" -metadata track=2 -metadata date=2023 -metadata genre="Lo-Fi" \
  "$OUT/Music/Chill/Night Drive.flac"

LYRICS=$'[00:00.50]Hello from Vivid Music\n[00:04.00]These lyrics are synced\n[00:08.00]Each line lights up in time\n[00:12.00]Tap a line to jump there\n[00:16.00]La la la\n[00:20.00]The end'
ff -f lavfi -i "sine=frequency=392:duration=40" -c:a libmp3lame -b:a 96k -id3v2_version 3 \
  -metadata title="Sing Along" -metadata artist="Karaoke Bot" -metadata album="Words" -metadata genre="Pop" -metadata LYRICS="$LYRICS" \
  "$OUT/Music/Lyrics/Sing Along.mp3"

# No tags or art at all, and names that need natural sorting.
ff -f lavfi -i "sine=frequency=220:duration=35" -c:a libmp3lame -b:a 64k -map_metadata -1 "$OUT/Download/Mixtape/Track 2.mp3"
ff -f lavfi -i "sine=frequency=247:duration=35" -c:a libmp3lame -b:a 64k -map_metadata -1 "$OUT/Download/Mixtape/Track 10.mp3"

# A real song's title and length (tones only, of course) so the test can find its lyrics on LRCLIB.
real=$(curl -fsS -m 20 -A "VividMusic CI (https://github.com/Jambo199/PersonalExeMadeByAI)" \
  "https://lrclib.net/api/search?track_name=Never%20Gonna%20Give%20You%20Up&artist_name=Rick%20Astley" || true)
row=$(echo "${real:-[]}" | jq -r '[.[] | select(.syncedLyrics != null and .duration > 0)][0] // empty
  | [(.duration | round), .trackName, .artistName, .albumName] | @tsv' 2>/dev/null || true)
if [ -n "$row" ]; then
  IFS=$'\t' read -r dur track artist album <<< "$row"
  mkdir -p "$OUT/Music/Online"
  ff -f lavfi -i "sine=frequency=262:duration=$dur" -ac 1 -c:a libmp3lame -b:a 32k -id3v2_version 3 \
    -metadata title="$track" -metadata artist="$artist" -metadata album="$album" \
    "$OUT/Music/Online/$track.mp3"
  echo "LRCLIB test song: $track / $artist / $album / ${dur}s"
else
  echo "LRCLIB unreachable; skipping the online lyrics song"
fi

rm -rf "$OUT/covers"
find "$OUT" -type f | sort
