#!/usr/bin/env bash
# Temporary: looks at ncs.io for popularity sorting, track pages, artist pages and Alan Walker.
set -u
UA="Mozilla/5.0 (Linux; Android 14) VividMusic-CI"
get() { curl -sSL -m 30 -A "$UA" -o "$2" -w "status %{http_code} size %{size_download} final %{url_effective}\n" "$1" || true; }

echo "=== search page form"
get "https://ncs.io/music-search?q=&genre=&mood=" /tmp/search.html
python3 - <<'PY'
import re
h = open("/tmp/search.html", encoding="utf-8", errors="replace").read()
m = re.search(r'<form[^>]*music-search-form.*?</form>', h, re.S)
form = m.group(0) if m else ""
form = re.sub(r'<option[^>]*>[^<]*</option>', '', form)
print(re.sub(r'\s+', ' ', form)[:3000])
print("sort/order/popular mentions:", sorted(set(re.findall(r'[^"\s>]*(?:sort|order|popular|trending|views|plays)[^"\s<]*', h, re.I)))[:40])
print("first slug:", re.findall(r'<a href="(/[^"/?#]+)">\s*<img', h)[:3])
print("first artist link:", re.findall(r"href='(/artist/[^']+)'", h)[:3])
PY

echo "=== home page sections"
get "https://ncs.io/" /tmp/home.html
python3 - <<'PY'
import re
h = open("/tmp/home.html", encoding="utf-8", errors="replace").read()
for m in re.finditer(r'<h[1-5][^>]*>(.*?)</h[1-5]>', h, re.S):
    t = re.sub(r'<[^>]+>', ' ', m.group(1)); t = re.sub(r'\s+', ' ', t).strip()
    if t: print("heading:", t[:80], "@", m.start())
i = h.find("Popular Releases")
if i > 0:
    seg = h[i:i+30000]
    print("popular section links:", re.findall(r'href="([^"]+)"', seg[:600]))
    print("popular tracks in next 30k:", re.findall(r'data-track="([^"]+)"', seg)[:30])
    print("popular dates:", re.findall(r'<p title="([0-9-]+)"', seg)[:30])
print("nav links:", sorted(set(re.findall(r'href="(https://ncs\.io/[a-z-]+|/[a-z-]+)"', h)))[:60])
PY

echo "=== candidate popularity urls"
for u in "https://ncs.io/music-search?q=&genre=&mood=&sort=popular" "https://ncs.io/music-search?q=&genre=&mood=&order=popular" "https://ncs.io/popular" "https://ncs.io/music?sort=popular" "https://ncs.io/releases?sort=popular" "https://ncs.io/music"; do
  echo "-- $u"
  get "$u" /tmp/c.html
  python3 -c "
import re
h=open('/tmp/c.html',encoding='utf-8',errors='replace').read()
print('tracks:', re.findall(r'data-track=\"([^\"]+)\"', h)[:8])"
done

echo "=== track page"
slug=$(python3 -c "
import re
h=open('/tmp/search.html',encoding='utf-8',errors='replace').read()
print((re.findall(r'<a href=\"(/[^\"/?#]+)\">\s*<img', h) or [''])[0])")
get "https://ncs.io$slug" /tmp/track.html
python3 - <<'PY'
import re, html as H
h = open("/tmp/track.html", encoding="utf-8", errors="replace").read()
body = re.sub(r'<script.*?</script>|<style.*?</style>', ' ', h, flags=re.S)
i = body.find('<article')
text = re.sub(r'<[^>]+>', '\n', body[i:])
text = "\n".join(l.strip() for l in H.unescape(text).splitlines() if l.strip())
print("visible text:\n" + text[:3500])
print("links:", sorted(set(re.findall(r'href="(https?://[^"]+)"', h)))[:50])
print("data attrs:", sorted(set(re.findall(r'\b(data-[a-z]+)=', h))))
for m in list(re.finditer(r'(?i)(bpm|key|label|isrc|duration|lyrics|copy|credit|instrumental|release date)', body))[:12]:
    print("--", re.sub(r'\s+', ' ', body[max(0, m.start()-200):m.start()+300]))
PY

echo "=== artist page"
artist=$(python3 -c "
import re
h=open('/tmp/search.html',encoding='utf-8',errors='replace').read()
print((re.findall(r\"href='(/artist/[^']+)'\", h) or [''])[0])")
get "https://ncs.io$artist" /tmp/artist.html
python3 - <<'PY'
import re
h = open("/tmp/artist.html", encoding="utf-8", errors="replace").read()
print("tracks:", re.findall(r'data-track="([^"]+)"', h)[:40])
print("rows (<tr):", h.count("<tr"), "player-play:", h.count("player-play"))
print("pagination:", sorted(set(re.findall(r'href="([^"]*page=\d+[^"]*)"', h)))[:10])
i = h.find("player-play")
print(re.sub(r'\s+', ' ', h[max(0, i-1500):i+1200]))
m = re.search(r'<img[^>]+(?:artist|avatar)[^>]*>', h)
print("artist image:", m.group(0) if m else None)
PY

echo "=== alan walker"
get "https://ncs.io/music-search?q=alan+walker&genre=&mood=" /tmp/aw.html
python3 -c "
import re
h=open('/tmp/aw.html',encoding='utf-8',errors='replace').read()
print('tracks:', list(zip(re.findall(r'data-track=\"([^\"]+)\"', h), re.findall(r'data-artistraw=\"([^\"]+)\"', h))))
print('artist links:', sorted(set(re.findall(r\"href='(/artist/[^']+)'\", h))))"
get "https://ncs.io/music-search?q=walker&genre=&mood=" /tmp/aw2.html
python3 -c "
import re
h=open('/tmp/aw2.html',encoding='utf-8',errors='replace').read()
print('walker tracks:', list(zip(re.findall(r'data-track=\"([^\"]+)\"', h), re.findall(r'data-artistraw=\"([^\"]+)\"', h))))"
