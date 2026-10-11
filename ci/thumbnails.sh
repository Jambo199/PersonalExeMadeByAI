#!/usr/bin/env bash
# Prints small JPEG previews of key screenshots as base64 (one line each) so they can be
# reviewed from the job log when artifacts can't be downloaded.
set -uo pipefail
DIR="${1:-screenshots}"
shift || true
names=("$@")
[ ${#names[@]} -eq 0 ] && names=(00a-update-available 00b-update-installer 02-songs 02b-multi-select 05-folders 07-folder-neon 09-player 11-lyrics 12-queue 13-notification 16-synced-lyrics 21-dark-player 22-landscape)
for n in "${names[@]}"; do
  f="$DIR/$n.png"
  [ -f "$f" ] || { echo "THUMB $n missing"; continue; }
  ffmpeg -hide_banner -loglevel error -y -i "$f" -vf "scale=240:-2" -q:v 9 "/tmp/$n.jpg"
  echo "THUMB $n $(stat -c %s "/tmp/$n.jpg") $(base64 -w0 "/tmp/$n.jpg")"
done
