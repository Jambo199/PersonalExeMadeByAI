from __future__ import annotations

import fnmatch
import os
import shutil
import stat
from dataclasses import dataclass
from pathlib import Path
from typing import Callable, Dict, Iterable, List, Tuple


@dataclass(frozen=True)
class CacheTarget:
    key: str
    name: str
    description: str
    patterns: Tuple[str, ...]
    selected_by_default: bool = True
    warning: str = ""


@dataclass
class ScanResult:
    key: str
    name: str
    paths: List[Path]
    files: int
    bytes_total: int
    errors: List[str]


class CacheCleaner:
    """Safe-ish Windows cache cleaner.

    It intentionally targets cache/temp folders only. It does not delete browser cookies,
    saved passwords, downloads, documents, game saves, or app configuration folders.
    """

    def __init__(self) -> None:
        self.env = {k.upper(): v for k, v in os.environ.items()}

    @staticmethod
    def targets() -> List[CacheTarget]:
        la = r"%LOCALAPPDATA%"
        ra = r"%APPDATA%"
        win = r"%WINDIR%"
        return [
            CacheTarget(
                "windows_temp",
                "Windows temp folders",
                "User temp and system temp files that are safe to remove when not in use.",
                (r"%TEMP%", rf"{la}\Temp", rf"{win}\Temp"),
                True,
                "Some locked files will be skipped.",
            ),
            CacheTarget(
                "crash_dumps",
                "Crash dumps",
                "Old app crash dump files.",
                (rf"{la}\CrashDumps",),
                True,
            ),
            CacheTarget(
                "directx_shader",
                "DirectX / GPU shader caches",
                "AMD/NVIDIA/DirectX shader caches. Games may stutter briefly while rebuilding these.",
                (
                    rf"{la}\D3DSCache",
                    rf"{la}\AMD\DxCache",
                    rf"{la}\AMD\GLCache",
                    rf"{la}\NVIDIA\DXCache",
                    rf"{la}\NVIDIA\GLCache",
                ),
                True,
                "Games may rebuild shaders on next launch.",
            ),
            CacheTarget(
                "browser_cache",
                "Browser cache only",
                "Chrome, Edge and Firefox cache folders only. Cookies/logins/history are not targeted.",
                (
                    rf"{la}\Google\Chrome\User Data\*\Cache",
                    rf"{la}\Google\Chrome\User Data\*\Code Cache",
                    rf"{la}\Google\Chrome\User Data\*\GPUCache",
                    rf"{la}\Microsoft\Edge\User Data\*\Cache",
                    rf"{la}\Microsoft\Edge\User Data\*\Code Cache",
                    rf"{la}\Microsoft\Edge\User Data\*\GPUCache",
                    rf"{ra}\Mozilla\Firefox\Profiles\*\cache2",
                    rf"{la}\Mozilla\Firefox\Profiles\*\cache2",
                ),
                False,
                "Close browsers first for best results.",
            ),
            CacheTarget(
                "discord_cache",
                "Discord cache",
                "Discord/Discord PTB/Canary Cache, Code Cache and GPUCache folders.",
                (
                    rf"{ra}\Discord\Cache",
                    rf"{ra}\Discord\Code Cache",
                    rf"{ra}\Discord\GPUCache",
                    rf"{ra}\discordptb\Cache",
                    rf"{ra}\discordptb\Code Cache",
                    rf"{ra}\discordptb\GPUCache",
                    rf"{ra}\discordcanary\Cache",
                    rf"{ra}\discordcanary\Code Cache",
                    rf"{ra}\discordcanary\GPUCache",
                ),
                True,
                "Close Discord first for best results.",
            ),
            CacheTarget(
                "spotify_cache",
                "Spotify cache",
                "Spotify local cache. Does not delete playlists or account data.",
                (rf"{la}\Spotify\Data", rf"{la}\Packages\SpotifyAB.SpotifyMusic_*\LocalCache\Spotify\Data"),
                False,
                "Close Spotify first.",
            ),
            CacheTarget(
                "steam_web_cache",
                "Steam web cache",
                "Steam embedded browser cache.",
                (rf"{la}\Steam\htmlcache", rf"{la}\Steam\widevine", rf"%PROGRAMFILES(X86)%\Steam\appcache\httpcache"),
                False,
                "Close Steam first.",
            ),
            CacheTarget(
                "recycle_bin_note",
                "Recycle Bin note",
                "Windows Recycle Bin is not auto-emptied by this app, so nothing important is silently destroyed.",
                tuple(),
                False,
                "Use Windows Disk Cleanup/Storage Sense if you want to empty it.",
            ),
        ]

    def expand_pattern(self, pattern: str) -> List[Path]:
        expanded = os.path.expandvars(pattern)
        # Some variables like PROGRAMFILES(X86) can fail with expandvars if absent.
        for key, value in os.environ.items():
            expanded = expanded.replace(f"%{key}%", value)
        if any(ch in expanded for ch in "*?"):
            parent = Path(expanded).anchor or "."
            # pathlib glob requires splitting at first wildcard for robust Windows-style paths.
            return [Path(p) for p in _glob_windows(expanded)]
        p = Path(expanded)
        return [p] if p.exists() else []

    def resolve_target_paths(self, target: CacheTarget) -> List[Path]:
        seen = set()
        paths: List[Path] = []
        for pattern in target.patterns:
            for p in self.expand_pattern(pattern):
                try:
                    rp = p.resolve(strict=False)
                except Exception:
                    rp = p
                key = str(rp).lower()
                if key not in seen and p.exists():
                    seen.add(key)
                    paths.append(p)
        return paths

    def scan(self, target: CacheTarget) -> ScanResult:
        errors: List[str] = []
        paths = self.resolve_target_paths(target)
        files = 0
        total = 0
        for path in paths:
            try:
                if path.is_file() or path.is_symlink():
                    files += 1
                    total += _safe_size(path)
                elif path.is_dir():
                    for child in path.rglob("*"):
                        try:
                            if child.is_file() or child.is_symlink():
                                files += 1
                                total += _safe_size(child)
                        except Exception as exc:
                            errors.append(f"{child}: {exc}")
            except Exception as exc:
                errors.append(f"{path}: {exc}")
        return ScanResult(target.key, target.name, paths, files, total, errors)

    def clean(
        self,
        target: CacheTarget,
        progress: Callable[[str], None] | None = None,
    ) -> Tuple[int, int, List[str]]:
        """Delete contents inside target folders and exact files.

        Returns (deleted_files, freed_bytes_estimate, errors)
        """
        errors: List[str] = []
        deleted = 0
        freed = 0
        for root in self.resolve_target_paths(target):
            if not _path_is_cache_like(root):
                errors.append(f"Skipped suspicious path: {root}")
                continue
            if progress:
                progress(f"Cleaning {root}")
            if root.is_file() or root.is_symlink():
                size = _safe_size(root)
                if _delete_one(root, errors):
                    deleted += 1
                    freed += size
                continue
            if root.is_dir():
                try:
                    # Delete children, keep the cache folder itself to avoid breaking apps expecting it.
                    for child in list(root.iterdir()):
                        size = _measure_path(child, errors)
                        count = _count_files(child, errors)
                        if _delete_one(child, errors):
                            deleted += max(count, 1)
                            freed += size
                except Exception as exc:
                    errors.append(f"{root}: {exc}")
        return deleted, freed, errors


def _glob_windows(pattern: str) -> List[str]:
    # pathlib on Linux during tests does not understand Windows globs, but on Windows glob.glob does.
    import glob

    return glob.glob(pattern)


def _safe_size(path: Path) -> int:
    try:
        return path.stat().st_size
    except Exception:
        return 0


def _measure_path(path: Path, errors: List[str]) -> int:
    if path.is_file() or path.is_symlink():
        return _safe_size(path)
    total = 0
    if path.is_dir():
        try:
            for child in path.rglob("*"):
                if child.is_file() or child.is_symlink():
                    total += _safe_size(child)
        except Exception as exc:
            errors.append(f"{path}: {exc}")
    return total


def _count_files(path: Path, errors: List[str]) -> int:
    if path.is_file() or path.is_symlink():
        return 1
    count = 0
    if path.is_dir():
        try:
            for child in path.rglob("*"):
                if child.is_file() or child.is_symlink():
                    count += 1
        except Exception as exc:
            errors.append(f"{path}: {exc}")
    return count


def _make_writable(func, path, _exc_info):
    try:
        os.chmod(path, stat.S_IWRITE)
        func(path)
    except Exception:
        pass


def _delete_one(path: Path, errors: List[str]) -> bool:
    try:
        if path.is_symlink() or path.is_file():
            path.unlink(missing_ok=True)
            return True
        if path.is_dir():
            shutil.rmtree(path, onerror=_make_writable)
            return True
        return False
    except Exception as exc:
        errors.append(f"{path}: {exc}")
        return False


def _path_is_cache_like(path: Path) -> bool:
    text = str(path).lower().replace("/", "\\")
    allowed_bits = [
        "\\temp",
        "\\cache",
        "\\code cache",
        "\\gpucache",
        "\\d3dscache",
        "\\dxcache",
        "\\glcache",
        "\\crashdumps",
        "\\htmlcache",
        "\\httpcache",
        "\\widevine",
        "\\spotify\\data",
    ]
    dangerous_bits = ["\\documents", "\\downloads", "\\desktop", "\\pictures", "\\videos", "\\music"]
    if any(bit in text for bit in dangerous_bits):
        return False
    return any(bit in text for bit in allowed_bits)


def human_size(num: int) -> str:
    units = ["B", "KB", "MB", "GB", "TB"]
    value = float(num)
    for unit in units:
        if value < 1024 or unit == units[-1]:
            return f"{value:.1f} {unit}" if unit != "B" else f"{int(value)} B"
        value /= 1024
    return f"{num} B"
