from __future__ import annotations

import json
import os
from pathlib import Path
from typing import Any, Dict

APP_NAME = "PersonalExeMadeByAI"


def config_dir() -> Path:
    base = os.getenv("APPDATA") or os.getenv("LOCALAPPDATA") or str(Path.home())
    path = Path(base) / APP_NAME
    path.mkdir(parents=True, exist_ok=True)
    return path


def config_path() -> Path:
    return config_dir() / "settings.json"


DEFAULT_SETTINGS: Dict[str, Any] = {
    "letterboxd_username": "",
    "music_country": "gb",
    "music_limit": 25,
    "window_geometry": "1180x760",
    "update_source": "",
    "check_updates_on_start": False,
}


def load_settings() -> Dict[str, Any]:
    path = config_path()
    if not path.exists():
        return DEFAULT_SETTINGS.copy()
    try:
        with path.open("r", encoding="utf-8") as f:
            data = json.load(f)
        merged = DEFAULT_SETTINGS.copy()
        merged.update({k: v for k, v in data.items() if k in DEFAULT_SETTINGS})
        return merged
    except Exception:
        return DEFAULT_SETTINGS.copy()


def save_settings(settings: Dict[str, Any]) -> None:
    safe = DEFAULT_SETTINGS.copy()
    safe.update({k: v for k, v in settings.items() if k in DEFAULT_SETTINGS})
    with config_path().open("w", encoding="utf-8") as f:
        json.dump(safe, f, indent=2)
