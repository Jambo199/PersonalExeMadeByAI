#!/usr/bin/env bash
# Temporary: how ncs.io lists its artists.
set -u
UA="Mozilla/5.0 (Linux; Android 14) VividMusic-CI"
get() { curl -sSL -m 30 -A "$UA" -o "$2" -w "status %{http_code} size %{size_download} final %{url_effective}\n" "$1" || true; }
get "https://ncs.io/artists" /tmp/a.html
python3 - <<'PY'
import re
h = open("/tmp/a.html", encoding="utf-8", errors="replace").read()
i = h.find("Latest Artists")
print("sort/filter area:", re.sub(r'\s+', ' ', h[max(0, i-1500):i+2500]))
links = sorted(set(re.findall(r'href="([^"]*artists[^"]*)"', h)))
print("artist list links:", links[:40])
m = [x.start() for x in re.finditer(r"/artist/\d+/", h)]
if m:
    print("first artist item:", re.sub(r'\s+', ' ', h[max(0, m[0]-900):m[0]+900]))
print("artist links count:", len(set(re.findall(r'/artist/\d+/[a-z0-9-]+', h))))
print("forms:", re.findall(r'<form[^>]*>', h))
print("inputs:", re.findall(r'<input[^>]*>', h)[:10])
PY
for u in "https://ncs.io/artists?sort=az" "https://ncs.io/artists?order=az" "https://ncs.io/artists?q=jim" "https://ncs.io/artists?page=2"; do
  echo "-- $u"; get "$u" /tmp/b.html
  python3 -c "
import re
h=open('/tmp/b.html',encoding='utf-8',errors='replace').read()
print('artists:', re.findall(r'/artist/\d+/([a-z0-9-]+)', h)[:20])"
done
for q in "jim yosef" "elektronomia"; do
  get "https://ncs.io/music-search?q=$(python3 -c "import urllib.parse,sys; print(urllib.parse.quote_plus(sys.argv[1]))" "$q")&genre=&mood=" /tmp/q.html > /dev/null
  python3 -c "
import re
h=open('/tmp/q.html',encoding='utf-8',errors='replace').read()
print('$q artists:', sorted(set(re.findall(r\"href='(/artist/\d+/[^']+)'\", h)))[:10], 'tracks:', len(re.findall('player-play', h)))"
done
