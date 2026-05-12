#!/usr/bin/env python3
"""
Xtream Playlist Info Checker
Analizza tutti i campi recuperabili da una playlist Xtream Codes.
"""

import requests
import sys
import random
from datetime import datetime

GREEN  = "\033[92m"
RED    = "\033[91m"
YELLOW = "\033[93m"
CYAN   = "\033[96m"
BOLD   = "\033[1m"
DIM    = "\033[2m"
RESET  = "\033[0m"

OK  = f"{GREEN}✅{RESET}"
NO  = f"{RED}❌{RESET}"

SESSION = requests.Session()
SESSION.headers.update({"User-Agent": "Mozilla/5.0"})

def check(val) -> str:
    if val is None:
        return NO
    if isinstance(val, str) and val.strip() in ("", "N/A", "0", "null"):
        return NO
    if isinstance(val, (int, float)) and val == 0:
        return NO
    return OK

def section(title: str):
    print(f"\n{BOLD}{CYAN}{'═' * 60}{RESET}")
    print(f"{BOLD}{CYAN}  {title}{RESET}")
    print(f"{BOLD}{CYAN}{'═' * 60}{RESET}")

def row(label: str, value, icon: str = None):
    icon = icon or check(value)
    val_str = str(value)[:70] if value not in (None, "", 0) else f"{DIM}(assente){RESET}"
    print(f"  {icon}  {BOLD}{label:<28}{RESET} {val_str}")

def api(base_url: str, params: dict):
    try:
        r = SESSION.get(base_url, params=params, timeout=15)
        r.raise_for_status()
        return r.json()
    except Exception as e:
        print(f"{RED}[ERRORE] {e}{RESET}")
        return None

def ask_repeat(label: str) -> bool:
    """Chiede all'utente se vuole analizzare un altro campione casuale."""
    ans = input(f"\n{YELLOW}  🔄  Analizzare un altro {label} casuale? (s/n): {RESET}").strip().lower()
    return ans in ("s", "si", "sì", "y", "yes", "1")

# ─────────────────────────────────────────────────────
# FUNZIONE: analisi film (VOD)
# ─────────────────────────────────────────────────────
def analizza_vod(BASE, AUTH, vod_streams, vod_cats):
    section("FILM (VOD)")

    n_vod_cats = len(vod_cats) if vod_cats else 0
    n_vod      = len(vod_streams) if vod_streams else 0

    row("Totale categorie VOD", n_vod_cats or None)
    row("Totale film",          n_vod or None)

    if not vod_streams:
        print(f"  {NO}  {DIM}Nessun film disponibile{RESET}")
        return

    while True:
        sample_vod = random.choice(vod_streams)
        vod_id = sample_vod.get("stream_id") or sample_vod.get("vod_id")

        print(f"\n{BOLD}  📽  Campi lista VOD (esempio: '{sample_vod.get('name','?')}'):{RESET}")
        row("Nome",               sample_vod.get("name"))
        row("Poster/Icona",       sample_vod.get("stream_icon"))
        row("Valutazione",        sample_vod.get("rating"))
        row("Valutazione /5",     sample_vod.get("rating_5based"))
        row("Categoria ID",       sample_vod.get("category_id"))
        row("Formato container",  sample_vod.get("container_extension"))
        row("Data aggiunta",      sample_vod.get("added"))

        print(f"\n{BOLD}  🔍  Campi dettaglio VOD (get_vod_info):{RESET}")
        vod_info = api(BASE, {**AUTH, "action": "get_vod_info", "vod_id": vod_id})

        if vod_info:
            mi = vod_info.get("info", {})
            md = vod_info.get("movie_data", {})

            row("Trama/Plot",         mi.get("plot") or mi.get("description"))
            row("Anno di uscita",     mi.get("releasedate") or mi.get("release_date") or mi.get("year"))
            row("Durata (stringa)",   mi.get("duration"))
            row("Durata (secondi)",   mi.get("duration_secs"))
            row("Genere",             mi.get("genre"))
            row("Cast",               mi.get("cast"))
            row("Regista",            mi.get("director"))
            row("Valutazione IMDb",   mi.get("rating"))
            row("Valutazione /5",     mi.get("rating_5based"))
            row("Paese produzione",   mi.get("country"))
            row("Lingua audio",       mi.get("audio") or md.get("audio"))
            row("Codec video",        mi.get("video") or md.get("video"))
            row("Backdrop/Banner",    (mi.get("backdrop_path") or [None])[0] if isinstance(mi.get("backdrop_path"), list) else mi.get("backdrop_path"))
            row("Trailer YouTube",    mi.get("youtube_trailer"))
            row("TMDB ID",            mi.get("tmdb_id") or mi.get("tmdb"))
            row("Kinopoisk URL",      mi.get("kinopoisk_url"))
            row("Età consigliata",    mi.get("age"))
            row("Bitrate",            md.get("bitrate"))
        else:
            print(f"  {NO}  {DIM}Impossibile recuperare i dettagli del film{RESET}")

        if not ask_repeat("film"):
            break

# ─────────────────────────────────────────────────────
# FUNZIONE: analisi serie TV
# ─────────────────────────────────────────────────────
def analizza_series(BASE, AUTH, series_list, series_cats):
    section("SERIE TV")

    n_series_cats = len(series_cats) if series_cats else 0
    n_series      = len(series_list) if series_list else 0

    row("Totale categorie Serie", n_series_cats or None)
    row("Totale serie",           n_series or None)

    if not series_list:
        print(f"  {NO}  {DIM}Nessuna serie disponibile{RESET}")
        return

    while True:
        sample_series = random.choice(series_list)
        series_id     = sample_series.get("series_id")

        print(f"\n{BOLD}  📺  Campi lista Serie (esempio: '{sample_series.get('name','?')}'):{RESET}")
        row("Nome",               sample_series.get("name"))
        row("Cover/Poster",       sample_series.get("cover"))
        row("Trama breve",        sample_series.get("plot"))
        row("Cast",               sample_series.get("cast"))
        row("Regista",            sample_series.get("director"))
        row("Genere",             sample_series.get("genre"))
        row("Data rilascio",      sample_series.get("releaseDate"))
        row("Ultima modifica",    sample_series.get("last_modified"))
        row("Valutazione",        sample_series.get("rating"))
        row("Categoria ID",       sample_series.get("category_id"))
        row("Backdrop",           sample_series.get("backdrop_path"))
        row("Trailer YouTube",    sample_series.get("youtube_trailer"))
        row("Durata episodio",    sample_series.get("episode_run_time"))

        print(f"\n{BOLD}  🔍  Campi dettaglio Serie (get_series_info):{RESET}")
        series_info = api(BASE, {**AUTH, "action": "get_series_info", "series_id": series_id})

        if series_info:
            si2 = series_info.get("info", {})
            eps = series_info.get("episodes", {})

            n_seasons  = len(eps) if eps else 0
            total_eps  = sum(len(v) for v in eps.values()) if eps else 0

            row("Numero stagioni",   n_seasons or None)
            row("Totale episodi",    total_eps or None)

            if eps:
                for season_num, ep_list in sorted(eps.items(), key=lambda x: int(x[0]) if str(x[0]).isdigit() else 0)[:3]:
                    ep_sample = ep_list[0] if ep_list else {}
                    ep_info   = ep_sample.get("info", {}) if isinstance(ep_sample.get("info"), dict) else {}
                    print(f"\n    {BOLD}Stagione {season_num} ({len(ep_list)} episodi) — Ep. campione:{RESET}")
                    row("  Titolo episodio",  ep_sample.get("title"))
                    row("  Trama episodio",   ep_sample.get("plot") or ep_info.get("plot"))
                    row("  Durata",           ep_info.get("duration"))
                    row("  Data rilascio",    ep_info.get("releasedate"))
                    row("  Valutazione",      ep_info.get("rating"))
                    row("  Numero episodio",  ep_sample.get("episode_num"))
                    row("  Formato",          ep_sample.get("container_extension"))
                if n_seasons > 3:
                    print(f"    {DIM}... e altre {n_seasons - 3} stagioni{RESET}")

            row("Trama serie",       si2.get("plot"))
            row("Cast",              si2.get("cast"))
            row("Regista",           si2.get("director"))
            row("Genere",            si2.get("genre"))
            row("Valutazione",       si2.get("rating"))
            row("Backdrop",          si2.get("backdrop_path"))
            row("Trailer YouTube",   si2.get("youtube_trailer"))
        else:
            print(f"  {NO}  {DIM}Impossibile recuperare i dettagli della serie{RESET}")

        if not ask_repeat("serie"):
            break

# ─────────────────────────────────────────────────────
# FUNZIONE: analisi canali live (eseguita una volta sola)
# ─────────────────────────────────────────────────────
def analizza_live(BASE, AUTH):
    section("CANALI LIVE")

    live_cats    = api(BASE, {**AUTH, "action": "get_live_categories"})
    live_streams = api(BASE, {**AUTH, "action": "get_live_streams"})
    n_live_cats  = len(live_cats) if live_cats else 0
    n_live       = len(live_streams) if live_streams else 0

    row("Totale categorie Live", n_live_cats or None)
    row("Totale canali Live",    n_live or None)

    if live_streams:
        sample = random.choice(live_streams)
        print(f"\n{BOLD}  📡  Campi per canale (esempio: '{sample.get('name','?')}'):{RESET}")
        row("Nome canale",          sample.get("name"))
        row("Stream ID",            sample.get("stream_id"))
        row("Logo/Icona",           sample.get("stream_icon"))
        row("EPG Channel ID",       sample.get("epg_channel_id"))
        row("Categoria ID",         sample.get("category_id"))
        row("Data aggiunta",        sample.get("added"))
        row("Tipo stream",          sample.get("stream_type"))
        row("TV Archive attivo",    sample.get("tv_archive"))
        row("Durata archivio (d)",  sample.get("tv_archive_duration"))

        print(f"\n{BOLD}  📅  EPG (Guida TV) — campione:{RESET}")
        epg_data = api(BASE, {**AUTH, "action": "get_short_epg",
                              "stream_id": sample.get("stream_id"), "limit": 1})
        epg_list = (epg_data or {}).get("epg_listings", [])
        if epg_list:
            epg = epg_list[0]
            row("Titolo EPG",       epg.get("title"))
            row("Descrizione EPG",  epg.get("description") or epg.get("lang", {}).get("desc"))
            row("Ora inizio",       epg.get("start"))
            row("Ora fine",         epg.get("end") or epg.get("stop"))
        else:
            print(f"  {NO}  {DIM}Nessun dato EPG per questo canale{RESET}")

    return n_live, n_live_cats

# ══════════════════════════════════════════════════════════════════════════════
# MAIN
# ══════════════════════════════════════════════════════════════════════════════
print(f"\n{BOLD}{'═'*60}")
print("   🔍  XTREAM PLAYLIST INFO CHECKER")
print(f"{'═'*60}{RESET}\n")

server   = input("  Inserisci SERVER (es: http://server.com:8080): ").strip().rstrip("/")
username = input("  Username: ").strip()
password = input("  Password: ").strip()

BASE = f"{server}/player_api.php"
AUTH = {"username": username, "password": password}

print(f"\n{YELLOW}  Connessione e pre-caricamento dati...{RESET}")

# Autenticazione
data = api(BASE, AUTH)
if not data or data.get("user_info", {}).get("auth") == 0:
    print(f"{RED}  ❌ Autenticazione fallita. Controlla le credenziali.{RESET}")
    sys.exit(1)

# ── 1. Account ──────────────────────────────────────────────────────────────
section("1. INFORMAZIONI ACCOUNT")
ui = data.get("user_info", {})
si = data.get("server_info", {})

exp_raw = ui.get("exp_date")
exp_fmt = None
if exp_raw:
    try:
        exp_fmt = datetime.fromtimestamp(int(exp_raw)).strftime("%d/%m/%Y %H:%M")
    except:
        exp_fmt = str(exp_raw)

print(f"\n{BOLD}  Account:{RESET}")
row("Status",             ui.get("status"))
row("Scadenza",           exp_fmt)
row("Connessioni attive", ui.get("active_cons"))
row("Max connessioni",    ui.get("max_connections"))
row("Trial",              "Sì" if str(ui.get("is_trial","0")) == "1" else None)
row("Formato output",     ", ".join(ui.get("allowed_output_formats", [])))
row("Data creazione",     ui.get("created_at"))

print(f"\n{BOLD}  Server:{RESET}")
row("URL",                si.get("url"))
row("Porta HTTP",         si.get("port"))
row("Porta HTTPS",        si.get("https_port"))
row("Porta RTMP",         si.get("rtmp_port"))
row("Timezone",           si.get("timezone"))
row("Orario server",      si.get("time_now"))

# ── 2. Canali Live (una sola analisi) ───────────────────────────────────────
n_live, n_live_cats = analizza_live(BASE, AUTH)

# Pre-carica VOD e Serie (una volta sola, poi si riusa nel loop)
print(f"\n{YELLOW}  Caricamento catalogo film e serie...{RESET}", end="", flush=True)
vod_cats    = api(BASE, {**AUTH, "action": "get_vod_categories"})
vod_streams = api(BASE, {**AUTH, "action": "get_vod_streams"})
series_cats = api(BASE, {**AUTH, "action": "get_series_categories"})
series_list = api(BASE, {**AUTH, "action": "get_series"})
print(f" {OK}")

# ── 3. Loop Film ─────────────────────────────────────────────────────────────
analizza_vod(BASE, AUTH, vod_streams, vod_cats)

# ── 4. Loop Serie ────────────────────────────────────────────────────────────
analizza_series(BASE, AUTH, series_list, series_cats)

# ── 5. Riepilogo ─────────────────────────────────────────────────────────────
section("5. RIEPILOGO CONTENUTI DISPONIBILI")
for label, val in [
    ("Canali Live",       n_live),
    ("Categorie Live",    n_live_cats),
    ("Film (VOD)",        len(vod_streams) if vod_streams else 0),
    ("Categorie VOD",     len(vod_cats) if vod_cats else 0),
    ("Serie TV",          len(series_list) if series_list else 0),
    ("Categorie Serie",   len(series_cats) if series_cats else 0),
]:
    icon  = OK if val else NO
    count = f"{BOLD}{GREEN}{val:,}{RESET}" if val else f"{DIM}0{RESET}"
    print(f"  {icon}  {label:<28} {count}")

print(f"\n{DIM}  Script completato.{RESET}\n")
