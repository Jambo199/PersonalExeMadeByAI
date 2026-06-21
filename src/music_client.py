from __future__ import annotations

from dataclasses import dataclass
from typing import Dict, List

import requests


@dataclass
class MusicItem:
    rank: int
    title: str
    artist: str
    album: str = ""
    release_date: str = ""
    url: str = ""
    note: str = ""


APPLE_RSS_BASE = "https://rss.applemarketingtools.com/api/v2"


def fetch_apple_music_chart(country: str = "gb", entity: str = "songs", limit: int = 25) -> List[MusicItem]:
    country = (country or "gb").lower().strip()
    entity = entity if entity in {"songs", "albums"} else "songs"
    limit = limit if limit in {10, 25, 50, 100} else 25
    url = f"{APPLE_RSS_BASE}/{country}/music/most-played/{limit}/{entity}.json"
    response = requests.get(url, timeout=20, headers={"User-Agent": "PersonalExeMadeByAI/1.0"})
    response.raise_for_status()
    payload = response.json()
    results = payload.get("feed", {}).get("results", [])
    items: List[MusicItem] = []
    for i, item in enumerate(results, start=1):
        artist = item.get("artistName") or item.get("artistUrl") or ""
        album = item.get("collectionName") or item.get("name") if entity == "albums" else item.get("collectionName", "")
        items.append(
            MusicItem(
                rank=i,
                title=item.get("name", ""),
                artist=artist,
                album=album or "",
                release_date=item.get("releaseDate", ""),
                url=item.get("url", ""),
                note=f"Apple Music {country.upper()} most-played {entity}",
            )
        )
    return items


def _m(rank: int, title: str, artist: str) -> MusicItem:
    return MusicItem(rank=rank, title=title, artist=artist, note="Year flashback pick")


# Lightweight local flashback data. It is intentionally small and editable.
# The live/current bit comes from Apple; historical year charts are messy without paid chart databases.
YEAR_CLASSICS: Dict[int, List[MusicItem]] = {
    1980: [_m(1, "Call Me", "Blondie"), _m(2, "Another Brick in the Wall, Pt. 2", "Pink Floyd"), _m(3, "Upside Down", "Diana Ross")],
    1985: [_m(1, "Careless Whisper", "George Michael"), _m(2, "Take On Me", "a-ha"), _m(3, "Everybody Wants to Rule the World", "Tears for Fears")],
    1990: [_m(1, "Nothing Compares 2 U", "Sinéad O'Connor"), _m(2, "Vogue", "Madonna"), _m(3, "Ice Ice Baby", "Vanilla Ice")],
    1995: [_m(1, "Gangsta's Paradise", "Coolio feat. L.V."), _m(2, "Wonderwall", "Oasis"), _m(3, "Waterfalls", "TLC")],
    2000: [_m(1, "Music", "Madonna"), _m(2, "Stan", "Eminem feat. Dido"), _m(3, "It Wasn't Me", "Shaggy feat. RikRok")],
    2001: [_m(1, "Can't Get You Out of My Head", "Kylie Minogue"), _m(2, "Clint Eastwood", "Gorillaz"), _m(3, "In the End", "Linkin Park")],
    2002: [_m(1, "Lose Yourself", "Eminem"), _m(2, "The Scientist", "Coldplay"), _m(3, "Complicated", "Avril Lavigne")],
    2003: [_m(1, "Crazy in Love", "Beyoncé feat. Jay-Z"), _m(2, "Seven Nation Army", "The White Stripes"), _m(3, "Hey Ya!", "Outkast")],
    2004: [_m(1, "Mr. Brightside", "The Killers"), _m(2, "Toxic", "Britney Spears"), _m(3, "Yeah!", "Usher feat. Lil Jon & Ludacris")],
    2005: [_m(1, "Feel Good Inc.", "Gorillaz"), _m(2, "Gold Digger", "Kanye West feat. Jamie Foxx"), _m(3, "You're Beautiful", "James Blunt")],
    2006: [_m(1, "I Write Sins Not Tragedies", "Panic! At The Disco"), _m(2, "Hips Don't Lie", "Shakira feat. Wyclef Jean"), _m(3, "Welcome to the Black Parade", "My Chemical Romance")],
    2007: [_m(1, "Umbrella", "Rihanna feat. Jay-Z"), _m(2, "Stronger", "Kanye West"), _m(3, "Fluorescent Adolescent", "Arctic Monkeys")],
    2008: [_m(1, "Viva la Vida", "Coldplay"), _m(2, "Sex on Fire", "Kings of Leon"), _m(3, "American Boy", "Estelle feat. Kanye West")],
    2009: [_m(1, "Bad Romance", "Lady Gaga"), _m(2, "Empire State of Mind", "Jay-Z feat. Alicia Keys"), _m(3, "Fireflies", "Owl City")],
    2010: [_m(1, "Love the Way You Lie", "Eminem feat. Rihanna"), _m(2, "Rolling in the Deep", "Adele"), _m(3, "Teenage Dream", "Katy Perry")],
    2011: [_m(1, "Somebody That I Used to Know", "Gotye feat. Kimbra"), _m(2, "Pumped Up Kicks", "Foster the People"), _m(3, "We Found Love", "Rihanna feat. Calvin Harris")],
    2012: [_m(1, "Thrift Shop", "Macklemore & Ryan Lewis"), _m(2, "Radioactive", "Imagine Dragons"), _m(3, "Skyfall", "Adele")],
    2013: [_m(1, "Get Lucky", "Daft Punk feat. Pharrell Williams"), _m(2, "Royals", "Lorde"), _m(3, "Do I Wanna Know?", "Arctic Monkeys")],
    2014: [_m(1, "Uptown Funk", "Mark Ronson feat. Bruno Mars"), _m(2, "Take Me to Church", "Hozier"), _m(3, "Chandelier", "Sia")],
    2015: [_m(1, "Hello", "Adele"), _m(2, "Can't Feel My Face", "The Weeknd"), _m(3, "Lean On", "Major Lazer & DJ Snake")],
    2016: [_m(1, "One Dance", "Drake feat. Wizkid & Kyla"), _m(2, "Starboy", "The Weeknd feat. Daft Punk"), _m(3, "Heathens", "Twenty One Pilots")],
    2017: [_m(1, "Shape of You", "Ed Sheeran"), _m(2, "HUMBLE.", "Kendrick Lamar"), _m(3, "XO TOUR Llif3", "Lil Uzi Vert")],
    2018: [_m(1, "SICKO MODE", "Travis Scott"), _m(2, "God's Plan", "Drake"), _m(3, "thank u, next", "Ariana Grande")],
    2019: [_m(1, "bad guy", "Billie Eilish"), _m(2, "Old Town Road", "Lil Nas X"), _m(3, "Blinding Lights", "The Weeknd")],
    2020: [_m(1, "drivers license", "Olivia Rodrigo"), _m(2, "WAP", "Cardi B feat. Megan Thee Stallion"), _m(3, "Dynamite", "BTS")],
    2021: [_m(1, "good 4 u", "Olivia Rodrigo"), _m(2, "MONTERO", "Lil Nas X"), _m(3, "Stay", "The Kid LAROI & Justin Bieber")],
    2022: [_m(1, "As It Was", "Harry Styles"), _m(2, "Anti-Hero", "Taylor Swift"), _m(3, "Bad Habit", "Steve Lacy")],
    2023: [_m(1, "Flowers", "Miley Cyrus"), _m(2, "Kill Bill", "SZA"), _m(3, "vampire", "Olivia Rodrigo")],
    2024: [_m(1, "Not Like Us", "Kendrick Lamar"), _m(2, "Espresso", "Sabrina Carpenter"), _m(3, "Good Luck, Babe!", "Chappell Roan")],
    2025: [_m(1, "APT.", "ROSÉ & Bruno Mars"), _m(2, "BIRDS OF A FEATHER", "Billie Eilish"), _m(3, "Die With A Smile", "Lady Gaga & Bruno Mars")],
}


def year_classics(year: int) -> List[MusicItem]:
    if year in YEAR_CLASSICS:
        return YEAR_CLASSICS[year]
    nearest = min(YEAR_CLASSICS.keys(), key=lambda y: abs(y - year))
    items = YEAR_CLASSICS[nearest]
    return [MusicItem(i.rank, i.title, i.artist, i.album, i.release_date, i.url, f"Nearest stored year: {nearest}") for i in items]


def supported_years() -> List[int]:
    return sorted(YEAR_CLASSICS.keys())
