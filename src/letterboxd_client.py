from __future__ import annotations

import re
import time
import webbrowser
from dataclasses import dataclass
from typing import Dict, Iterable, List, Optional
from urllib.parse import quote_plus, urljoin

import requests
from bs4 import BeautifulSoup

LETTERBOXD_BASE = "https://letterboxd.com"
HEADERS = {
    "User-Agent": "PersonalExeMadeByAI/1.0 (+personal desktop app; respectful lightweight requests)",
    "Accept-Language": "en-GB,en;q=0.9",
}


@dataclass
class Film:
    title: str
    year: str = ""
    url: str = ""
    note: str = ""
    source: str = "Letterboxd"


def open_letterboxd_login() -> None:
    webbrowser.open("https://letterboxd.com/sign-in/")


def fetch_favourite_films(username: str, limit: int = 12) -> List[Film]:
    username = _clean_username(username)
    if not username:
        raise ValueError("Enter your Letterboxd username first.")
    html = _get(f"{LETTERBOXD_BASE}/{username}/")
    soup = BeautifulSoup(html, "html.parser")

    # Best case: locate the favourite films module then parse posters inside it.
    candidates = []
    for heading in soup.find_all(string=re.compile(r"favorite films|favourite films", re.I)):
        section = heading.find_parent(["section", "div"])
        if section:
            candidates.extend(_extract_films(section))

    # Fallback: Letterboxd usually stores poster metadata in data-film-name attrs.
    if not candidates:
        candidates = _extract_films(soup)

    # Profile pages can contain other posters. In normal Letterboxd layout, favourites are first 4 posters.
    return _dedupe(candidates)[:limit]


def fetch_letterboxd_popular(limit: int = 24) -> List[Film]:
    urls = [
        f"{LETTERBOXD_BASE}/films/popular/this/week/",
        f"{LETTERBOXD_BASE}/films/popular/",
    ]
    last_error: Exception | None = None
    for url in urls:
        try:
            soup = BeautifulSoup(_get(url), "html.parser")
            films = _dedupe(_extract_films(soup))
            if films:
                for f in films:
                    f.note = "Popular on Letterboxd"
                return films[:limit]
        except Exception as exc:  # noqa: BLE001
            last_error = exc
    if last_error:
        raise last_error
    return []


def tmdb_recommendations(api_key: str, favourites: Iterable[Film], limit: int = 24) -> List[Film]:
    api_key = api_key.strip()
    if not api_key:
        return []
    session = requests.Session()
    session.headers.update({"User-Agent": HEADERS["User-Agent"]})
    found: List[Film] = []
    seen = set()
    for fav in list(favourites)[:4]:
        movie_id = _tmdb_find_movie(session, api_key, fav)
        if not movie_id:
            continue
        data = _tmdb_get(session, f"https://api.themoviedb.org/3/movie/{movie_id}/recommendations", api_key)
        for item in data.get("results", []):
            title = item.get("title") or item.get("name") or ""
            if not title:
                continue
            year = (item.get("release_date") or "")[:4]
            key = (title.lower(), year)
            if key in seen:
                continue
            seen.add(key)
            found.append(
                Film(
                    title=title,
                    year=year,
                    url=f"https://www.themoviedb.org/movie/{item.get('id')}",
                    note=f"Recommended because of {fav.title}",
                    source="TMDb",
                )
            )
            if len(found) >= limit:
                return found
        time.sleep(0.2)
    return found[:limit]


def tmdb_trending(api_key: str, limit: int = 24) -> List[Film]:
    api_key = api_key.strip()
    if not api_key:
        return []
    session = requests.Session()
    data = _tmdb_get(session, "https://api.themoviedb.org/3/trending/movie/week", api_key)
    out: List[Film] = []
    for item in data.get("results", [])[:limit]:
        out.append(
            Film(
                title=item.get("title", ""),
                year=(item.get("release_date") or "")[:4],
                url=f"https://www.themoviedb.org/movie/{item.get('id')}",
                note="Trending this week on TMDb",
                source="TMDb",
            )
        )
    return [f for f in out if f.title]


def _clean_username(username: str) -> str:
    username = username.strip().strip("/")
    username = username.replace("https://letterboxd.com/", "")
    return username.split("/")[0]


def _get(url: str) -> str:
    response = requests.get(url, headers=HEADERS, timeout=20)
    response.raise_for_status()
    return response.text


def _extract_films(soup_or_tag) -> List[Film]:
    selectors = [
        "[data-film-name]",
        ".film-poster",
        ".poster-container",
        "li.poster-container",
    ]
    films: List[Film] = []
    seen_elements = set()
    for selector in selectors:
        for el in soup_or_tag.select(selector):
            ident = id(el)
            if ident in seen_elements:
                continue
            seen_elements.add(ident)
            film = _film_from_element(el)
            if film and film.title:
                films.append(film)
    return films


def _film_from_element(el) -> Optional[Film]:
    title = el.get("data-film-name") or el.get("data-item-name") or ""
    year = el.get("data-film-release-year") or ""
    slug = el.get("data-film-slug") or el.get("data-target-link") or ""

    if not title:
        img = el.find("img") if hasattr(el, "find") else None
        if img:
            title = img.get("alt") or img.get("title") or ""
    if not title:
        title_attr = el.get("title") or el.get("aria-label") or ""
        title = re.sub(r"\s*\(\d{4}\)\s*$", "", title_attr).strip()
        m = re.search(r"(19|20)\d{2}", title_attr)
        if m and not year:
            year = m.group(0)

    url = ""
    if slug:
        if slug.startswith("http"):
            url = slug
        elif slug.startswith("/"):
            url = urljoin(LETTERBOXD_BASE, slug)
        else:
            url = f"{LETTERBOXD_BASE}/film/{slug}/"
    else:
        link = el.find("a", href=True) if hasattr(el, "find") else None
        if link:
            url = urljoin(LETTERBOXD_BASE, link["href"])

    title = re.sub(r"\s+", " ", title).strip()
    if not title:
        return None
    return Film(title=title, year=year, url=url, source="Letterboxd")


def _dedupe(films: Iterable[Film]) -> List[Film]:
    out: List[Film] = []
    seen = set()
    for film in films:
        key = (film.title.lower(), film.year, film.url)
        if key not in seen:
            seen.add(key)
            out.append(film)
    return out


def _tmdb_find_movie(session: requests.Session, api_key: str, film: Film) -> Optional[int]:
    params = {"api_key": api_key, "query": film.title, "include_adult": "false"}
    if film.year:
        params["year"] = film.year
    response = session.get("https://api.themoviedb.org/3/search/movie", params=params, timeout=20)
    response.raise_for_status()
    results = response.json().get("results", [])
    if not results and film.year:
        params.pop("year", None)
        response = session.get("https://api.themoviedb.org/3/search/movie", params=params, timeout=20)
        response.raise_for_status()
        results = response.json().get("results", [])
    return results[0].get("id") if results else None


def _tmdb_get(session: requests.Session, url: str, api_key: str) -> Dict:
    response = session.get(url, params={"api_key": api_key}, timeout=20)
    response.raise_for_status()
    return response.json()
