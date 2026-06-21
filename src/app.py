from __future__ import annotations

import queue
import threading
import tkinter as tk
import webbrowser
from tkinter import messagebox, ttk
from typing import Callable, Dict, List, Optional

from cache_cleaner import CacheCleaner, CacheTarget, human_size
from letterboxd_client import (
    Film,
    fetch_favourite_films,
    fetch_letterboxd_popular,
    open_letterboxd_login,
    tmdb_recommendations,
    tmdb_trending,
)
from music_client import MusicItem, fetch_apple_music_chart, supported_years, year_classics
from settings_store import load_settings, save_settings
from version import CURRENT_VERSION
from auto_updater import UpdateInfo, check_for_updates, download_update, install_downloaded_update

APP_TITLE = "PersonalExeMadeByAI"
BG = "#111318"
PANEL = "#171a21"
TEXT = "#e8eaf0"
MUTED = "#aeb4c2"
ACCENT = "#7c5cff"
GREEN = "#3ddc97"
RED = "#ff5d73"


class App(tk.Tk):
    def __init__(self) -> None:
        super().__init__()
        self.settings = load_settings()
        self.title(APP_TITLE)
        self.geometry(self.settings.get("window_geometry", "1180x760"))
        self.configure(bg=BG)
        self.protocol("WM_DELETE_WINDOW", self.on_close)
        self._style()

        header = tk.Frame(self, bg=BG)
        header.pack(fill="x", padx=18, pady=(16, 8))
        tk.Label(header, text=f"PersonalExeMadeByAI v{CURRENT_VERSION}", font=("Segoe UI", 22, "bold"), bg=BG, fg=TEXT).pack(side="left")
        tk.Label(
            header,
            text="clean junk • stalk films politely • vibe-check music",
            font=("Segoe UI", 10),
            bg=BG,
            fg=MUTED,
        ).pack(side="left", padx=14, pady=(8, 0))

        self.notebook = ttk.Notebook(self)
        self.notebook.pack(fill="both", expand=True, padx=18, pady=(0, 18))
        self.cache_tab = CacheTab(self.notebook)
        self.film_tab = FilmTab(self.notebook, self.settings)
        self.music_tab = MusicTab(self.notebook, self.settings)
        self.update_tab = UpdateTab(self.notebook, self.settings)
        self.notebook.add(self.cache_tab, text=" Cache Cleaner ")
        self.notebook.add(self.film_tab, text=" Letterboxd / Films ")
        self.notebook.add(self.music_tab, text=" Music ")
        self.notebook.add(self.update_tab, text=" Updater ")
        if self.settings.get("check_updates_on_start") and self.settings.get("update_source"):
            self.after(1400, lambda: self.update_tab.check_updates(silent=True))

    def _style(self) -> None:
        style = ttk.Style(self)
        try:
            style.theme_use("clam")
        except tk.TclError:
            pass
        style.configure("TNotebook", background=BG, borderwidth=0)
        style.configure("TNotebook.Tab", background=PANEL, foreground=TEXT, padding=(16, 8), borderwidth=0)
        style.map("TNotebook.Tab", background=[("selected", "#232837")], foreground=[("selected", TEXT)])
        style.configure("Treeview", background="#0f1117", fieldbackground="#0f1117", foreground=TEXT, rowheight=28, borderwidth=0)
        style.configure("Treeview.Heading", background="#232837", foreground=TEXT, relief="flat", font=("Segoe UI", 10, "bold"))
        style.map("Treeview", background=[("selected", "#2c3657")])
        style.configure("TButton", padding=(12, 6), font=("Segoe UI", 10))
        style.configure("Accent.TButton", padding=(12, 6), font=("Segoe UI", 10, "bold"))
        style.configure("TLabel", background=PANEL, foreground=TEXT)
        style.configure("TEntry", fieldbackground="#0f1117", foreground=TEXT)
        style.configure("TCombobox", fieldbackground="#0f1117", foreground=TEXT)

    def on_close(self) -> None:
        self.settings["window_geometry"] = self.geometry()
        self.settings["letterboxd_username"] = self.film_tab.username_var.get().strip()
        self.settings["music_country"] = self.music_tab.country_var.get().strip().lower() or "gb"
        try:
            self.settings["music_limit"] = int(self.music_tab.limit_var.get())
        except ValueError:
            self.settings["music_limit"] = 25
        self.settings["update_source"] = self.update_tab.source_var.get().strip()
        self.settings["check_updates_on_start"] = bool(self.update_tab.auto_check_var.get())
        save_settings(self.settings)
        self.destroy()


class BaseTab(tk.Frame):
    def __init__(self, parent) -> None:
        super().__init__(parent, bg=PANEL)

    def run_thread(self, fn: Callable, on_done: Callable[[object, Optional[Exception]], None]) -> None:
        q: queue.Queue = queue.Queue()

        def worker() -> None:
            try:
                q.put((fn(), None))
            except Exception as exc:  # noqa: BLE001
                q.put((None, exc))

        def poll() -> None:
            try:
                result, exc = q.get_nowait()
            except queue.Empty:
                self.after(80, poll)
                return
            on_done(result, exc)

        threading.Thread(target=worker, daemon=True).start()
        poll()

    @staticmethod
    def clear_tree(tree: ttk.Treeview) -> None:
        for item in tree.get_children():
            tree.delete(item)


class CacheTab(BaseTab):
    def __init__(self, parent) -> None:
        super().__init__(parent)
        self.cleaner = CacheCleaner()
        self.targets = self.cleaner.targets()
        self.selected: Dict[str, tk.BooleanVar] = {}
        self.size_labels: Dict[str, tk.Label] = {}
        self.scan_results: Dict[str, object] = {}

        left = tk.Frame(self, bg=PANEL)
        left.pack(side="left", fill="both", expand=True, padx=16, pady=16)
        right = tk.Frame(self, bg=PANEL, width=360)
        right.pack(side="right", fill="y", padx=(0, 16), pady=16)

        tk.Label(left, text="1) Pick what to clean", font=("Segoe UI", 16, "bold"), bg=PANEL, fg=TEXT).pack(anchor="w")
        tk.Label(left, text="Designed to avoid cookies, passwords, saves, documents and downloads.", bg=PANEL, fg=MUTED).pack(anchor="w", pady=(0, 12))

        list_frame = tk.Frame(left, bg=PANEL)
        list_frame.pack(fill="both", expand=True)
        for target in self.targets:
            row = tk.Frame(list_frame, bg="#0f1117", highlightthickness=1, highlightbackground="#242a38")
            row.pack(fill="x", pady=5)
            var = tk.BooleanVar(value=target.selected_by_default)
            self.selected[target.key] = var
            cb = tk.Checkbutton(row, variable=var, bg="#0f1117", activebackground="#0f1117", fg=TEXT, selectcolor="#232837")
            cb.pack(side="left", padx=(8, 4))
            labels = tk.Frame(row, bg="#0f1117")
            labels.pack(side="left", fill="x", expand=True, pady=8)
            tk.Label(labels, text=target.name, bg="#0f1117", fg=TEXT, font=("Segoe UI", 10, "bold")).pack(anchor="w")
            tk.Label(labels, text=target.description, bg="#0f1117", fg=MUTED, wraplength=640, justify="left").pack(anchor="w")
            if target.warning:
                tk.Label(labels, text=f"⚠ {target.warning}", bg="#0f1117", fg="#ffd166", wraplength=640, justify="left").pack(anchor="w")
            size = tk.Label(row, text="not scanned", bg="#0f1117", fg=MUTED, width=14, anchor="e")
            size.pack(side="right", padx=12)
            self.size_labels[target.key] = size

        actions = tk.Frame(right, bg=PANEL)
        actions.pack(fill="x")
        tk.Label(actions, text="Cleaner controls", font=("Segoe UI", 14, "bold"), bg=PANEL, fg=TEXT).pack(anchor="w")
        ttk.Button(actions, text="Scan selected", style="Accent.TButton", command=self.scan_selected).pack(fill="x", pady=(12, 6))
        ttk.Button(actions, text="Clean selected", command=self.clean_selected).pack(fill="x", pady=6)
        ttk.Button(actions, text="Select safe defaults", command=self.select_defaults).pack(fill="x", pady=6)
        ttk.Button(actions, text="Untick all", command=self.untick_all).pack(fill="x", pady=6)

        tk.Label(right, text="Log", font=("Segoe UI", 14, "bold"), bg=PANEL, fg=TEXT).pack(anchor="w", pady=(18, 6))
        self.log_box = tk.Text(right, height=20, bg="#0f1117", fg=TEXT, insertbackground=TEXT, relief="flat", wrap="word")
        self.log_box.pack(fill="both", expand=True)
        self.log("Ready. Scan first, then clean. Close browsers/apps for best results.")

    def chosen_targets(self) -> List[CacheTarget]:
        return [t for t in self.targets if self.selected[t.key].get() and t.patterns]

    def scan_selected(self) -> None:
        targets = self.chosen_targets()
        if not targets:
            messagebox.showinfo(APP_TITLE, "Tick at least one real cache target first.")
            return
        self.log("Scanning selected targets...")

        def task():
            return [self.cleaner.scan(t) for t in targets]

        def done(results, exc):
            if exc:
                self.log(f"Scan failed: {exc}")
                return
            total = 0
            for result in results:
                self.scan_results[result.key] = result
                total += result.bytes_total
                self.size_labels[result.key].config(text=human_size(result.bytes_total), fg=GREEN if result.bytes_total else MUTED)
                self.log(f"{result.name}: {human_size(result.bytes_total)} across {result.files} files")
                if result.errors:
                    self.log(f"  skipped/errors: {len(result.errors)}")
            self.log(f"Scan complete. Estimated cleanable size: {human_size(total)}")

        self.run_thread(task, done)

    def clean_selected(self) -> None:
        targets = self.chosen_targets()
        if not targets:
            messagebox.showinfo(APP_TITLE, "Tick at least one real cache target first.")
            return
        confirm = messagebox.askyesno(APP_TITLE, "Clean selected cache folders now? Locked files will be skipped.")
        if not confirm:
            return
        self.log("Cleaning selected targets...")

        def progress(msg: str) -> None:
            self.after(0, lambda: self.log(msg))

        def task():
            deleted_total = 0
            freed_total = 0
            errors_all: List[str] = []
            for target in targets:
                deleted, freed, errors = self.cleaner.clean(target, progress)
                deleted_total += deleted
                freed_total += freed
                errors_all.extend(errors)
            return deleted_total, freed_total, errors_all

        def done(result, exc):
            if exc:
                self.log(f"Clean failed: {exc}")
                return
            deleted, freed, errors = result
            self.log(f"Done. Removed about {deleted} files/folders, freeing roughly {human_size(freed)}.")
            if errors:
                self.log(f"Skipped/locked/errors: {len(errors)}")
                for err in errors[:8]:
                    self.log(f"  {err}")
            self.scan_selected()

        self.run_thread(task, done)

    def select_defaults(self) -> None:
        for t in self.targets:
            self.selected[t.key].set(t.selected_by_default)

    def untick_all(self) -> None:
        for var in self.selected.values():
            var.set(False)

    def log(self, message: str) -> None:
        self.log_box.insert("end", message + "\n")
        self.log_box.see("end")


class FilmTab(BaseTab):
    def __init__(self, parent, settings: Dict) -> None:
        super().__init__(parent)
        self.favourites: List[Film] = []
        self.username_var = tk.StringVar(value=settings.get("letterboxd_username", ""))
        self.tmdb_var = tk.StringVar(value="")

        top = tk.Frame(self, bg=PANEL)
        top.pack(fill="x", padx=16, pady=16)
        tk.Label(top, text="2) Films / Letterboxd", font=("Segoe UI", 16, "bold"), bg=PANEL, fg=TEXT).grid(row=0, column=0, sticky="w", columnspan=6)
        tk.Label(top, text="Uses your public Letterboxd username. TMDb key is optional for proper recommendations.", bg=PANEL, fg=MUTED).grid(row=1, column=0, sticky="w", columnspan=6, pady=(0, 10))
        tk.Label(top, text="Letterboxd username", bg=PANEL, fg=TEXT).grid(row=2, column=0, sticky="w")
        ttk.Entry(top, textvariable=self.username_var, width=24).grid(row=2, column=1, padx=(8, 14), sticky="w")
        tk.Label(top, text="TMDb API key (optional)", bg=PANEL, fg=TEXT).grid(row=2, column=2, sticky="w")
        ttk.Entry(top, textvariable=self.tmdb_var, width=32, show="•").grid(row=2, column=3, padx=(8, 14), sticky="w")
        ttk.Button(top, text="Open Letterboxd login", command=open_letterboxd_login).grid(row=2, column=4, sticky="w")
        ttk.Button(top, text="TMDb key help", command=lambda: webbrowser.open("https://developer.themoviedb.org/docs/getting-started")).grid(row=2, column=5, padx=(8, 0), sticky="w")

        buttons = tk.Frame(self, bg=PANEL)
        buttons.pack(fill="x", padx=16, pady=(0, 10))
        ttk.Button(buttons, text="Load my favourite films", style="Accent.TButton", command=self.load_favourites).pack(side="left", padx=(0, 8))
        ttk.Button(buttons, text="Show trending films", command=self.load_trending).pack(side="left", padx=8)
        ttk.Button(buttons, text="Similar films I might like", command=self.load_recommendations).pack(side="left", padx=8)
        ttk.Button(buttons, text="Clear list", command=lambda: self.clear_tree(self.tree)).pack(side="left", padx=8)

        self.status = tk.Label(self, text="Double-click a row to open it.", bg=PANEL, fg=MUTED)
        self.status.pack(anchor="w", padx=16)
        self.tree = ttk.Treeview(self, columns=("section", "title", "year", "source", "note", "url"), show="headings")
        self.tree.pack(fill="both", expand=True, padx=16, pady=16)
        for col, label, width in [
            ("section", "Section", 150),
            ("title", "Film", 260),
            ("year", "Year", 80),
            ("source", "Source", 100),
            ("note", "Why / note", 360),
            ("url", "Link", 260),
        ]:
            self.tree.heading(col, text=label)
            self.tree.column(col, width=width, anchor="w")
        self.tree.bind("<Double-1>", self.open_selected_url)

    def set_status(self, text: str) -> None:
        self.status.config(text=text)

    def load_favourites(self) -> None:
        username = self.username_var.get().strip()
        self.set_status("Loading Letterboxd favourites...")

        def task():
            return fetch_favourite_films(username)

        def done(result, exc):
            if exc:
                self.set_status(f"Could not load favourites: {exc}")
                return
            self.favourites = result
            self.add_films("Your favourites", result)
            self.set_status(f"Loaded {len(result)} favourite films.")

        self.run_thread(task, done)

    def load_trending(self) -> None:
        api_key = self.tmdb_var.get().strip()
        self.set_status("Loading trending films...")

        def task():
            return tmdb_trending(api_key) if api_key else fetch_letterboxd_popular()

        def done(result, exc):
            if exc:
                self.set_status(f"Could not load trending: {exc}")
                return
            self.add_films("Trending", result)
            self.set_status(f"Loaded {len(result)} trending films.")

        self.run_thread(task, done)

    def load_recommendations(self) -> None:
        api_key = self.tmdb_var.get().strip()
        if not self.favourites:
            self.load_favourites()
            self.set_status("Loaded favourites first. Press recommendations again after they appear.")
            return
        self.set_status("Finding similar films...")

        def task():
            recs = tmdb_recommendations(api_key, self.favourites) if api_key else []
            if recs:
                return recs
            fallback = fetch_letterboxd_popular(16)
            for f in fallback:
                f.note = "Fallback: add a TMDb API key for real favourite-based recommendations"
            return fallback

        def done(result, exc):
            if exc:
                self.set_status(f"Could not load recommendations: {exc}")
                return
            self.add_films("Similar picks", result)
            self.set_status(f"Loaded {len(result)} similar/fallback picks.")

        self.run_thread(task, done)

    def add_films(self, section: str, films: List[Film]) -> None:
        for film in films:
            self.tree.insert("", "end", values=(section, film.title, film.year, film.source, film.note, film.url))

    def open_selected_url(self, _event=None) -> None:
        item = self.tree.focus()
        if not item:
            return
        values = self.tree.item(item, "values")
        if len(values) >= 6 and values[5]:
            webbrowser.open(values[5])


class MusicTab(BaseTab):
    def __init__(self, parent, settings: Dict) -> None:
        super().__init__(parent)
        self.country_var = tk.StringVar(value=settings.get("music_country", "gb"))
        self.limit_var = tk.StringVar(value=str(settings.get("music_limit", 25)))
        years = supported_years()
        self.year_var = tk.StringVar(value=str(years[-1]))

        top = tk.Frame(self, bg=PANEL)
        top.pack(fill="x", padx=16, pady=16)
        tk.Label(top, text="3) Music", font=("Segoe UI", 16, "bold"), bg=PANEL, fg=TEXT).grid(row=0, column=0, sticky="w", columnspan=8)
        tk.Label(top, text="Live current charts use Apple Music RSS. Year flashbacks are local editable picks.", bg=PANEL, fg=MUTED).grid(row=1, column=0, sticky="w", columnspan=8, pady=(0, 10))
        tk.Label(top, text="Country code", bg=PANEL, fg=TEXT).grid(row=2, column=0, sticky="w")
        ttk.Entry(top, textvariable=self.country_var, width=8).grid(row=2, column=1, padx=(8, 16), sticky="w")
        tk.Label(top, text="Limit", bg=PANEL, fg=TEXT).grid(row=2, column=2, sticky="w")
        ttk.Combobox(top, textvariable=self.limit_var, values=[10, 25, 50, 100], width=8, state="readonly").grid(row=2, column=3, padx=(8, 16), sticky="w")
        tk.Label(top, text="Year", bg=PANEL, fg=TEXT).grid(row=2, column=4, sticky="w")
        ttk.Combobox(top, textvariable=self.year_var, values=years, width=8, state="readonly").grid(row=2, column=5, padx=(8, 16), sticky="w")

        buttons = tk.Frame(self, bg=PANEL)
        buttons.pack(fill="x", padx=16, pady=(0, 10))
        ttk.Button(buttons, text="Trending songs", style="Accent.TButton", command=lambda: self.load_chart("songs")).pack(side="left", padx=(0, 8))
        ttk.Button(buttons, text="Trending albums", command=lambda: self.load_chart("albums")).pack(side="left", padx=8)
        ttk.Button(buttons, text="Popular music by year", command=self.load_year).pack(side="left", padx=8)
        ttk.Button(buttons, text="Clear list", command=lambda: self.clear_tree(self.tree)).pack(side="left", padx=8)

        self.status = tk.Label(self, text="Double-click a row to open Apple Music when a link exists.", bg=PANEL, fg=MUTED)
        self.status.pack(anchor="w", padx=16)
        self.tree = ttk.Treeview(self, columns=("rank", "title", "artist", "album", "date", "note", "url"), show="headings")
        self.tree.pack(fill="both", expand=True, padx=16, pady=16)
        for col, label, width in [
            ("rank", "#", 60),
            ("title", "Title", 300),
            ("artist", "Artist", 240),
            ("album", "Album", 260),
            ("date", "Release", 100),
            ("note", "Note", 260),
            ("url", "Link", 260),
        ]:
            self.tree.heading(col, text=label)
            self.tree.column(col, width=width, anchor="w")
        self.tree.bind("<Double-1>", self.open_selected_url)

    def load_chart(self, entity: str) -> None:
        country = self.country_var.get().strip().lower() or "gb"
        try:
            limit = int(self.limit_var.get())
        except ValueError:
            limit = 25
        self.status.config(text=f"Loading Apple Music {country.upper()} {entity} chart...")

        def task():
            return fetch_apple_music_chart(country=country, entity=entity, limit=limit)

        def done(result, exc):
            if exc:
                self.status.config(text=f"Could not load chart: {exc}")
                return
            self.add_items(result)
            self.status.config(text=f"Loaded {len(result)} {entity}.")

        self.run_thread(task, done)

    def load_year(self) -> None:
        try:
            year = int(self.year_var.get())
        except ValueError:
            year = 2025
        items = year_classics(year)
        self.add_items(items)
        self.status.config(text=f"Loaded {len(items)} local picks for {year}.")

    def add_items(self, items: List[MusicItem]) -> None:
        for item in items:
            self.tree.insert("", "end", values=(item.rank, item.title, item.artist, item.album, item.release_date, item.note, item.url))

    def open_selected_url(self, _event=None) -> None:
        item = self.tree.focus()
        if not item:
            return
        values = self.tree.item(item, "values")
        if len(values) >= 7 and values[6]:
            webbrowser.open(values[6])


class UpdateTab(BaseTab):
    def __init__(self, parent, settings: Dict) -> None:
        super().__init__(parent)
        self.source_var = tk.StringVar(value=settings.get("update_source", ""))
        self.auto_check_var = tk.BooleanVar(value=bool(settings.get("check_updates_on_start", False)))
        self.latest_info: Optional[UpdateInfo] = None
        self.update_available = False

        outer = tk.Frame(self, bg=PANEL)
        outer.pack(fill="both", expand=True, padx=16, pady=16)

        tk.Label(outer, text="4) Auto-updater", font=("Segoe UI", 16, "bold"), bg=PANEL, fg=TEXT).pack(anchor="w")
        tk.Label(
            outer,
            text="Future updates can be checked from a GitHub release or a tiny JSON manifest. This app can replace itself when running as the packaged EXE.",
            bg=PANEL,
            fg=MUTED,
            wraplength=1050,
            justify="left",
        ).pack(anchor="w", pady=(0, 14))

        card = tk.Frame(outer, bg="#0f1117", highlightthickness=1, highlightbackground="#242a38")
        card.pack(fill="x", pady=(0, 12))
        tk.Label(card, text="Update source", bg="#0f1117", fg=TEXT, font=("Segoe UI", 10, "bold")).grid(row=0, column=0, sticky="w", padx=12, pady=(12, 3), columnspan=4)
        tk.Label(
            card,
            text="Use either owner/repo, a GitHub repo URL, or a direct update_manifest.json URL.",
            bg="#0f1117",
            fg=MUTED,
        ).grid(row=1, column=0, sticky="w", padx=12, columnspan=4)
        ttk.Entry(card, textvariable=self.source_var).grid(row=2, column=0, sticky="ew", padx=12, pady=12, columnspan=3)
        ttk.Button(card, text="Save source", command=self.save_source).grid(row=2, column=3, sticky="e", padx=(0, 12), pady=12)
        card.columnconfigure(0, weight=1)
        card.columnconfigure(1, weight=1)
        card.columnconfigure(2, weight=1)

        opts = tk.Frame(outer, bg=PANEL)
        opts.pack(fill="x")
        tk.Checkbutton(
            opts,
            text="Check for updates when the app starts",
            variable=self.auto_check_var,
            bg=PANEL,
            activebackground=PANEL,
            fg=TEXT,
            selectcolor="#232837",
            command=self.save_source,
        ).pack(side="left")

        buttons = tk.Frame(outer, bg=PANEL)
        buttons.pack(fill="x", pady=(12, 10))
        ttk.Button(buttons, text="Check for updates", style="Accent.TButton", command=self.check_updates).pack(side="left", padx=(0, 8))
        self.install_button = ttk.Button(buttons, text="Download + install update", command=self.download_and_install, state="disabled")
        self.install_button.pack(side="left", padx=8)
        ttk.Button(buttons, text="Open release/source", command=self.open_release).pack(side="left", padx=8)
        ttk.Button(buttons, text="Show example manifest", command=self.show_manifest_example).pack(side="left", padx=8)

        self.status = tk.Label(outer, text=f"Current version: v{CURRENT_VERSION}", bg=PANEL, fg=MUTED)
        self.status.pack(anchor="w", pady=(0, 8))

        body = tk.Frame(outer, bg=PANEL)
        body.pack(fill="both", expand=True)
        left = tk.Frame(body, bg=PANEL)
        left.pack(side="left", fill="both", expand=True, padx=(0, 12))
        right = tk.Frame(body, bg=PANEL, width=380)
        right.pack(side="right", fill="both")

        tk.Label(left, text="Release notes", font=("Segoe UI", 13, "bold"), bg=PANEL, fg=TEXT).pack(anchor="w")
        self.notes_box = tk.Text(left, height=16, bg="#0f1117", fg=TEXT, insertbackground=TEXT, relief="flat", wrap="word")
        self.notes_box.pack(fill="both", expand=True, pady=(6, 0))
        self.notes_box.insert("end", "No update checked yet.\n")

        tk.Label(right, text="Updater log", font=("Segoe UI", 13, "bold"), bg=PANEL, fg=TEXT).pack(anchor="w")
        self.log_box = tk.Text(right, height=16, bg="#0f1117", fg=TEXT, insertbackground=TEXT, relief="flat", wrap="word")
        self.log_box.pack(fill="both", expand=True, pady=(6, 0))
        self.log("Ready. Add an update source first.")

    def save_source(self) -> None:
        root = self.winfo_toplevel()
        if hasattr(root, "settings"):
            root.settings["update_source"] = self.source_var.get().strip()
            root.settings["check_updates_on_start"] = bool(self.auto_check_var.get())
            save_settings(root.settings)
        self.log("Updater settings saved.")

    def log(self, message: str) -> None:
        self.log_box.insert("end", message + "\n")
        self.log_box.see("end")

    def set_notes(self, text: str) -> None:
        self.notes_box.delete("1.0", "end")
        self.notes_box.insert("end", text or "No release notes provided.")

    def check_updates(self, silent: bool = False) -> None:
        source = self.source_var.get().strip()
        if not source:
            if not silent:
                messagebox.showinfo(APP_TITLE, "Add an update source first. Example: yourname/PersonalExeMadeByAI or a manifest URL.")
            return
        self.save_source()
        if not silent:
            self.log("Checking for updates...")
        self.status.config(text="Checking for updates...")
        self.install_button.config(state="disabled")

        def task():
            return check_for_updates(source, CURRENT_VERSION)

        def done(result, exc):
            if exc:
                if not silent:
                    self.log(f"Update check failed: {exc}")
                    self.status.config(text=f"Update check failed: {exc}")
                else:
                    self.status.config(text=f"Current version: v{CURRENT_VERSION}")
                return
            info, is_new = result
            self.latest_info = info
            self.update_available = is_new
            notes = f"Latest version: v{info.latest_version}\nSource: {info.source_kind}\n\n{info.release_notes or 'No release notes provided.'}"
            self.set_notes(notes)
            if is_new:
                self.status.config(text=f"Update available: v{info.latest_version}  — current v{CURRENT_VERSION}")
                self.log(f"Update available: v{info.latest_version}")
                self.install_button.config(state="normal")
                if silent:
                    messagebox.showinfo(APP_TITLE, f"Update available: v{info.latest_version}")
            else:
                self.status.config(text=f"You are up to date. Current v{CURRENT_VERSION}, latest v{info.latest_version}.")
                if not silent:
                    self.log("No update needed. You are up to date.")

        self.run_thread(task, done)

    def download_and_install(self) -> None:
        info = self.latest_info
        if not info:
            messagebox.showinfo(APP_TITLE, "Check for updates first.")
            return
        if not self.update_available:
            messagebox.showinfo(APP_TITLE, "No newer update is available.")
            return
        confirm = messagebox.askyesno(APP_TITLE, f"Download and install v{info.latest_version} now?\n\nThe app may close and reopen.")
        if not confirm:
            return
        self.install_button.config(state="disabled")
        self.status.config(text="Downloading update...")

        def progress(msg: str) -> None:
            self.after(0, lambda: self.log(msg))

        def task():
            downloaded = download_update(info, progress)
            return install_downloaded_update(downloaded, progress)

        def done(result, exc):
            if exc:
                self.log(f"Install failed: {exc}")
                self.status.config(text=f"Install failed: {exc}")
                self.install_button.config(state="normal")
                return
            self.log(result)
            self.status.config(text=result)
            if "will close" in str(result).lower():
                self.after(800, self.winfo_toplevel().destroy)
            else:
                messagebox.showinfo(APP_TITLE, result)

        self.run_thread(task, done)

    def open_release(self) -> None:
        if self.latest_info and self.latest_info.homepage_url:
            webbrowser.open(self.latest_info.homepage_url)
            return
        source = self.source_var.get().strip()
        if source.startswith("http"):
            webbrowser.open(source)
        elif "/" in source:
            webbrowser.open(f"https://github.com/{source}/releases/latest")
        else:
            messagebox.showinfo(APP_TITLE, "Add/check an update source first.")

    def show_manifest_example(self) -> None:
        example = ("{\n"
                   "  \"latest_version\": \"1.2.0\",\n"
                   "  \"download_url\": \"https://example.com/PersonalExeMadeByAI.exe\",\n"
                   "  \"sha256\": \"optional_sha256_here\",\n"
                   "  \"release_notes\": \"Cleaner log polish, music fixes, more chaos.\",\n"
                   "  \"homepage_url\": \"https://example.com/releases/PersonalExeMadeByAI-1.2.0\",\n"
                   "  \"mandatory\": false\n"
                   "}")
        self.set_notes(example)
        self.log("Manifest example shown in release notes box.")


if __name__ == "__main__":
    app = App()
    app.mainloop()
