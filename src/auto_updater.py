from __future__ import annotations

import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import zipfile
from dataclasses import dataclass
from pathlib import Path
from typing import Callable, Optional
from urllib.parse import urlparse

import requests

from version import APP_EXE_NAME, APP_NAME

ProgressCallback = Callable[[str], None]


@dataclass
class UpdateInfo:
    latest_version: str
    download_url: str
    release_notes: str = ""
    sha256: str = ""
    homepage_url: str = ""
    mandatory: bool = False
    source_kind: str = "manifest"


def _version_parts(value: str) -> list[int]:
    text = value.strip().lower().lstrip("v")
    parts = re.findall(r"\d+", text)
    if not parts:
        return [0]
    return [int(p) for p in parts[:4]]


def is_newer_version(latest: str, current: str) -> bool:
    left = _version_parts(latest)
    right = _version_parts(current)
    width = max(len(left), len(right))
    left += [0] * (width - len(left))
    right += [0] * (width - len(right))
    return left > right


def normalise_source(source: str) -> str:
    return source.strip()


def _github_repo_from_source(source: str) -> Optional[str]:
    source = source.strip()
    if re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", source):
        return source

    parsed = urlparse(source)
    if parsed.netloc.lower() in {"github.com", "www.github.com"}:
        bits = [b for b in parsed.path.split("/") if b]
        if len(bits) >= 2:
            return f"{bits[0]}/{bits[1]}"

    if parsed.netloc.lower() == "api.github.com":
        # https://api.github.com/repos/owner/repo/releases/latest
        bits = [b for b in parsed.path.split("/") if b]
        if len(bits) >= 3 and bits[0] == "repos":
            return f"{bits[1]}/{bits[2]}"
    return None


def _fetch_github_latest(repo: str) -> UpdateInfo:
    url = f"https://api.github.com/repos/{repo}/releases/latest"
    response = requests.get(url, timeout=15, headers={"Accept": "application/vnd.github+json"})
    response.raise_for_status()
    data = response.json()

    assets = data.get("assets") or []
    chosen = None

    def score(asset: dict) -> int:
        name = str(asset.get("name", "")).lower()
        value = 0
        if "personalexemadebyai" in name:
            value += 20
        if name.endswith(".exe"):
            value += 10
        if name.endswith(".zip"):
            value += 8
        return value

    candidates = [a for a in assets if str(a.get("name", "")).lower().endswith((".exe", ".zip"))]
    if candidates:
        chosen = sorted(candidates, key=score, reverse=True)[0]

    if not chosen:
        raise RuntimeError("Latest GitHub release has no .exe or .zip asset to install.")

    digest = str(chosen.get("digest") or "")
    sha256 = ""
    if digest.startswith("sha256:"):
        sha256 = digest.split(":", 1)[1]

    return UpdateInfo(
        latest_version=str(data.get("tag_name") or data.get("name") or "0.0.0").lstrip("v"),
        download_url=str(chosen.get("browser_download_url") or ""),
        release_notes=str(data.get("body") or ""),
        homepage_url=str(data.get("html_url") or f"https://github.com/{repo}/releases/latest"),
        sha256=sha256,
        mandatory=False,
        source_kind="github",
    )


def _fetch_manifest_json(url: str) -> UpdateInfo:
    response = requests.get(url, timeout=15)
    response.raise_for_status()
    data = response.json()
    latest = str(data.get("latest_version") or data.get("version") or "").strip()
    download = str(data.get("download_url") or data.get("url") or "").strip()
    if not latest or not download:
        raise RuntimeError("Update manifest must include latest_version and download_url.")
    return UpdateInfo(
        latest_version=latest.lstrip("v"),
        download_url=download,
        release_notes=str(data.get("release_notes") or data.get("notes") or ""),
        sha256=str(data.get("sha256") or "").lower().replace("sha256:", ""),
        homepage_url=str(data.get("homepage_url") or data.get("release_url") or ""),
        mandatory=bool(data.get("mandatory", False)),
        source_kind="manifest",
    )


def check_for_updates(source: str, current_version: str) -> tuple[UpdateInfo, bool]:
    source = normalise_source(source)
    if not source:
        raise RuntimeError("No update source is configured yet.")

    repo = _github_repo_from_source(source)
    info = _fetch_github_latest(repo) if repo else _fetch_manifest_json(source)
    if not info.download_url:
        raise RuntimeError("The update has no downloadable .exe or .zip file.")
    return info, is_newer_version(info.latest_version, current_version)


def _safe_filename_from_url(url: str) -> str:
    name = Path(urlparse(url).path).name
    if not name or "." not in name:
        name = "PersonalExeMadeByAI-update.exe"
    return re.sub(r"[^A-Za-z0-9_. -]", "_", name)


def download_update(info: UpdateInfo, progress: ProgressCallback = lambda _msg: None) -> Path:
    progress(f"Downloading update v{info.latest_version}...")
    response = requests.get(info.download_url, stream=True, timeout=30)
    response.raise_for_status()
    total = int(response.headers.get("content-length") or 0)
    filename = _safe_filename_from_url(info.download_url)
    out_dir = Path(tempfile.mkdtemp(prefix="PersonalExeMadeByAI_update_"))
    out_path = out_dir / filename

    h = hashlib.sha256()
    done = 0
    last_percent = -1
    with out_path.open("wb") as f:
        for chunk in response.iter_content(chunk_size=1024 * 512):
            if not chunk:
                continue
            f.write(chunk)
            h.update(chunk)
            done += len(chunk)
            if total:
                percent = int(done * 100 / total)
                if percent != last_percent and percent % 10 == 0:
                    progress(f"Download {percent}%...")
                    last_percent = percent
    actual = h.hexdigest().lower()
    if info.sha256 and actual != info.sha256.lower():
        raise RuntimeError("Downloaded update failed SHA256 verification. Not installing it.")
    progress("Download complete.")
    return out_path


def _find_exe_in_zip(zip_path: Path, progress: ProgressCallback) -> Path:
    extract_dir = zip_path.parent / "extracted"
    extract_dir.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(zip_path, "r") as zf:
        zf.extractall(extract_dir)
    exe_matches = list(extract_dir.rglob(APP_EXE_NAME))
    if not exe_matches:
        exe_matches = list(extract_dir.rglob("*.exe"))
    if not exe_matches:
        raise RuntimeError("The update zip did not contain an .exe file.")
    chosen = exe_matches[0]
    progress(f"Found update executable: {chosen.name}")
    return chosen


def _copy_source_update(downloaded_path: Path) -> Path:
    # When running from Python source, replacing app.py is not safe. Instead, stash the update.
    updates_dir = Path.cwd() / "updates"
    updates_dir.mkdir(parents=True, exist_ok=True)
    dest = updates_dir / downloaded_path.name
    shutil.copy2(downloaded_path, dest)
    return dest


def install_downloaded_update(downloaded_path: Path, progress: ProgressCallback = lambda _msg: None) -> str:
    suffix = downloaded_path.suffix.lower()
    if suffix == ".zip":
        new_exe = _find_exe_in_zip(downloaded_path, progress)
    elif suffix == ".exe":
        new_exe = downloaded_path
    else:
        raise RuntimeError("Update file must be a .exe or .zip.")

    if not getattr(sys, "frozen", False):
        saved = _copy_source_update(downloaded_path)
        return (
            "Downloaded the update, but this copy is running from Python source, so I did not replace files automatically.\n\n"
            f"Saved here:\n{saved}\n\n"
            "Build and run the packaged EXE to use one-click self-updating."
        )

    current_exe = Path(sys.executable).resolve()
    if not current_exe.exists():
        raise RuntimeError("Could not locate the running executable.")

    work_dir = downloaded_path.parent
    bat_path = work_dir / "apply_PersonalExeMadeByAI_update.bat"
    pid = os.getpid()
    # Keep it boring and robust: wait for this process to disappear, replace EXE, restart app.
    bat = f'''@echo off
setlocal
chcp 65001 >nul
TITLE Updating {APP_NAME}
echo Updating {APP_NAME}...
echo Please do not close this window.
:waitloop
tasklist /FI "PID eq {pid}" 2>NUL | find "{pid}" >NUL
if not errorlevel 1 (
  timeout /T 1 /NOBREAK >NUL
  goto waitloop
)
copy /Y "{str(new_exe)}" "{str(current_exe)}" >NUL
if errorlevel 1 (
  echo.
  echo Update failed. Try running {APP_NAME} as administrator.
  pause
  exit /B 1
)
start "" "{str(current_exe)}"
del "%~f0" >NUL 2>NUL
'''
    bat_path.write_text(bat, encoding="utf-8")
    subprocess.Popen(["cmd", "/c", str(bat_path)], close_fds=True)
    return "Update is ready. The app will close, replace itself, then reopen."
