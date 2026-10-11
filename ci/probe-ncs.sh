#!/usr/bin/env bash
# Temporary: checks details of ncs.io the NCS page relies on (filter menus, cover sizes).
set -u
UA="Mozilla/5.0 (Linux; Android 14) VividMusic-CI"
curl -sSL -m 30 -A "$UA" -o /tmp/ncs.html -w "status %{http_code} size %{size_download}\n" "https://ncs.io/music-search?q=&genre=&mood=" || true
python3 - <<'PY' > /tmp/ncs-probe.txt
import re
html = open("/tmp/ncs.html", encoding="utf-8", errors="replace").read()
print("select tags:", re.findall(r"<select[^>]*>", html))
print("rows with player-play:", html.count("player-play"))
covers = re.findall(r'data-cover="([^"]+)"', html)
print("first cover:", covers[:1])
PY
cat /tmp/ncs-probe.txt
cover=$(grep -o 'https://[^"]*100x100[^"]*' /tmp/ncs.html | head -1)
for size in 325x325 850x850; do
  echo "$size: $(curl -s -o /dev/null -m 20 -w '%{http_code} %{content_type}' "${cover/100x100/$size}")"
done
