#!/usr/bin/env bash
# Temporary: prints how ncs.io lists its tracks, to build the NCS page against.
set -u
UA="Mozilla/5.0 (Linux; Android 14) VividMusic-CI"
for url in "https://ncs.io/music-search?q=&genre=&mood=" "https://ncs.io/music-search?q=alan&genre=&mood=&page=2" "https://ncs.io/"; do
  echo "=== $url"
  curl -sSL -m 30 -A "$UA" -o /tmp/ncs.html -w "status %{http_code} size %{size_download} final %{url_effective}\n" "$url" || true
  python3 - <<'PY'
import re
html = open("/tmp/ncs.html", encoding="utf-8", errors="replace").read()
attrs = sorted(set(re.findall(r'\b(data-[a-z0-9_-]+)=', html)))
print("data attributes:", attrs[:80])
for m in list(re.finditer(r'data-url=', html))[:3]:
    print("--- around data-url:")
    print(html[max(0, m.start() - 700): m.start() + 900].replace("\n", " ")[:1600])
links = sorted(set(re.findall(r'href="([^"]*(?:track|download|release)[^"]*)"', html)))
print("track/download links:", links[:25])
print("pagination:", sorted(set(re.findall(r'href="([^"]*page=\d+[^"]*)"', html)))[:10])
print("genre options:", re.findall(r'<option[^>]*value="(\d+)"[^>]*>([^<]+)</option>', html)[:60])
PY
done
