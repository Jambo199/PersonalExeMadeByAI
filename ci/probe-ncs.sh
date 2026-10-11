#!/usr/bin/env bash
# Temporary: track pages, Alan Walker on NCS, and Deezer's ranking of NCS releases.
set -u
UA="Mozilla/5.0 (Linux; Android 14) VividMusic-CI"
get() { curl -sSL -m 30 -A "$UA" -o "$2" -w "status %{http_code} size %{size_download} final %{url_effective}\n" "$1" || true; }

for slug in g_desire dreamer; do
  echo "=== track page /$slug"
  get "https://ncs.io/$slug" /tmp/track.html
  python3 - <<'PY'
import re, html as H
h = open("/tmp/track.html", encoding="utf-8", errors="replace").read()
body = re.sub(r'<script.*?</script>|<style.*?</style>', ' ', h, flags=re.S)
i = body.find('</header>')
i = i if i > 0 else 0
j = body.find('<footer')
text = re.sub(r'<[^>]+>', '\n', body[i:j if j > 0 else None])
text = "\n".join(l.strip() for l in H.unescape(text).splitlines() if l.strip())
print("visible text:\n" + text[:3000])
print("links:", sorted(set(l for l in re.findall(r'href="(https?://[^"]+)"', h) if 'ncs.io/static' not in l))[:40])
print("data attrs:", sorted(set(re.findall(r'\b(data-[a-z]+)=', h))))
m = re.search(r'<article[^>]*module[^>]*>(.{0,2500})', body, re.S)
print("first module:", re.sub(r'\s+', ' ', m.group(1))[:2500] if m else None)
PY
done

echo "=== alan walker artist page"
get "https://ncs.io/artist/13/alan-walker" /tmp/aw.html
python3 - <<'PY'
import re
h = open("/tmp/aw.html", encoding="utf-8", errors="replace").read()
print("headings:", [re.sub(r'<[^>]+>|\s+', ' ', x).strip() for x in re.findall(r'<h5[^>]*>(.*?)</h5>', h, re.S)])
print("tracks:", list(zip(re.findall(r'data-track="([^"]+)"', h), re.findall(r'data-artistraw="([^"]+)"', h))))
print("tags:", re.findall(r'<span class="tags">([^<]*)</span>', h)[:3])
print("image:", re.findall(r"artists/[^']+325x325[^']+", h)[:2])
PY
for q in fade spectre force "sky high" "heroes tonight" "on & on" invincible; do
  get "https://ncs.io/music-search?q=$(python3 -c "import urllib.parse,sys; print(urllib.parse.quote_plus(sys.argv[1]))" "$q")&genre=&mood=" /tmp/q.html > /dev/null
  python3 -c "
import re,sys
h=open('/tmp/q.html',encoding='utf-8',errors='replace').read()
print('q=$q:', list(zip(re.findall(r'data-track=\"([^\"]+)\"', h), re.findall(r'data-artistraw=\"([^\"]+)\"', h)))[:6])"
done

echo "=== deezer"
get 'https://api.deezer.com/search?q=label:%22NCS%22&order=RANKING&limit=40' /tmp/dz.json
python3 - <<'PY'
import json
d = json.load(open("/tmp/dz.json"))
print("total:", d.get("total"), "error:", d.get("error"), "next:", d.get("next"))
for t in d.get("data", [])[:40]:
    print(t.get("rank"), "|", t.get("title"), "|", t.get("artist", {}).get("name"), "|", t.get("album", {}).get("title"), "|", t.get("duration"))
if d.get("data"):
    open("/tmp/first_id", "w").write(str(d["data"][0]["id"]))
PY
get 'https://api.deezer.com/search?q=label:%22NoCopyrightSounds%22&order=RANKING&limit=10' /tmp/dz2.json
python3 -c "
import json; d=json.load(open('/tmp/dz2.json')); print('NoCopyrightSounds label total:', d.get('total'), [ (t['title'], t['artist']['name']) for t in d.get('data',[])[:10]])"
get 'https://api.deezer.com/search?q=artist:%22Alan%20Walker%22%20label:%22NCS%22&order=RANKING' /tmp/dz3.json
python3 -c "
import json; d=json.load(open('/tmp/dz3.json')); print('alan walker NCS:', d.get('total'), [ (t['title'], t['artist']['name']) for t in d.get('data',[])[:10]])"
if [ -s /tmp/first_id ]; then
  get "https://api.deezer.com/track/$(cat /tmp/first_id)" /tmp/dzt.json
  python3 -c "
import json; d=json.load(open('/tmp/dzt.json')); print({k: d.get(k) for k in ['title','isrc','duration','release_date','bpm','gain','rank','explicit_lyrics']}, 'label?', d.get('album',{}).get('title'), [c['name'] for c in d.get('contributors',[])])"
fi
