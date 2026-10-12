#!/usr/bin/env bash
# Prints small JPEG previews of every screenshot as base64 (one line each) so they can be
# reviewed from the job log.
set -uo pipefail
DIR="${1:-screenshots}"
for f in "$DIR"/*.png; do
  [ -f "$f" ] || continue
  n=$(basename "$f" .png)
  ffmpeg -hide_banner -loglevel error -y -i "$f" -vf "scale=300:-2" -q:v 9 "/tmp/$n.jpg"
  echo "THUMB $n $(stat -c %s "/tmp/$n.jpg") $(base64 -w0 "/tmp/$n.jpg")"
done
