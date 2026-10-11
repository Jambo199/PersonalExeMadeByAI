#!/usr/bin/env bash
# Prints small JPEG previews of key screenshots as base64 (one line each) so they can be
# reviewed from the job log when artifacts can't be downloaded.
set -uo pipefail
DIR="${1:-screenshots}"
shift || true
names=("$@")
[ ${#names[@]} -eq 0 ] && names=(46-quiz 47-quiz-question 48-quiz-answer 49-wrapped 50-wrapped-minutes 51-wrapped-summary 42-ncs-artists 43-ncs-artists-az 44-ncs-artist-search 45-ncs-jim-yosef 40-ncs-search-artist 41-ncs-alan-walker 37-ncs-popular 38-ncs-info 39-ncs-artist 32-ncs 33-ncs-playing 02-home 02a-songs 02b-multi-select 05-folders 07-folder-neon 08-playlists 09-player 10b-sound-presets 16b-online-lyrics 14-mini-player 14b-home-after-listening 14c-bottom-nav-library 14d-home-scrolled 14e-genre 17-settings 19b-stats 20-dark-home 20b-dark-albums 20d-dark-folders 21-dark-player 22-landscape 22b-landscape-home 23-after-restart)
for n in "${names[@]}"; do
  f="$DIR/$n.png"
  [ -f "$f" ] || { echo "THUMB $n missing"; continue; }
  w=240; [[ $n == *home* || $n == 3[0-9]-* || $n == 4[0-9]-* ]] && w=360   # pages with the most detail worth checking
  ffmpeg -hide_banner -loglevel error -y -i "$f" -vf "scale=$w:-2" -q:v 9 "/tmp/$n.jpg"
  echo "THUMB $n $(stat -c %s "/tmp/$n.jpg") $(base64 -w0 "/tmp/$n.jpg")"
done
