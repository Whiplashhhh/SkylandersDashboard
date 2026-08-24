#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Agent local — scanne le dossier des .sky et pousse les deltas vers le serveur.

Cet agent est volontairement bete : il lit des octets, les hash, et les envoie.
**Il ne dechiffre rien, ne parse rien, ne calcule ni niveau ni XP.** Toute la
logique de format vit cote serveur (CLAUDE.md, « Architecture en deux
composants »). Un correctif d'offset ne doit jamais demander de redeployer le
laptop.

Lecture seule absolue (CLAUDE.md, invariant 1) : les .sky sont ouverts en "rb"
et jamais ecrits, renommes, deplaces ni supprimes. Le cache local vit en dehors
du dossier source.

Aucune dependance : uniquement la bibliotheque standard.

Usage :
    python3 agent/skylanders_agent.py --config agent/agent.yaml
    python3 agent/skylanders_agent.py --config agent/agent.yaml --once
"""

from __future__ import annotations

import argparse
import base64
import hashlib
import json
import os
import sys
import time
import urllib.error
import urllib.request

DUMP_SIZE = 1024
DEBOUNCE_SECONDS = 2.0
POLL_SECONDS = 2.0
IGNORED_SUFFIXES = (".txt", ".md", ".png", ".jpg", ".jpeg", ".ini")


# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------


def load_config(path: str) -> dict:
    """Lit agent.yaml.

    Sous-ensemble volontairement minimal de YAML : des lignes `cle: valeur`, un
    niveau, pas de listes. C'est tout ce dont ce fichier a besoin (SPEC.md §2.1),
    et ca evite d'imposer PyYAML pour trois cles.
    """
    if not os.path.exists(path):
        raise SystemExit(
            f"ERREUR : configuration introuvable : {path}\n"
            f"        Copier agent/agent.yaml.example et l'adapter."
        )
    config: dict[str, str] = {}
    with open(path, encoding="utf-8") as handle:
        for number, line in enumerate(handle, 1):
            line = line.split("#", 1)[0].strip()
            if not line:
                continue
            if ":" not in line:
                raise SystemExit(f"ERREUR : {path}:{number} : ligne illisible : {line!r}")
            key, _, value = line.partition(":")
            config[key.strip()] = value.strip().strip("'\"")
    for required in ("skylandersRoot", "serverUrl", "token"):
        if not config.get(required):
            raise SystemExit(f"ERREUR : {path} : cle « {required} » manquante ou vide")
    config["skylandersRoot"] = os.path.expanduser(config["skylandersRoot"])
    return config


def cache_path() -> str:
    """Le cache vit HORS du dossier source — on n'y ecrit jamais rien."""
    base = os.environ.get("XDG_CACHE_HOME") or os.path.expanduser("~/.cache")
    directory = os.path.join(base, "skylanders-agent")
    os.makedirs(directory, exist_ok=True)
    return os.path.join(directory, "sent.json")


def load_cache(path: str) -> dict[str, str]:
    try:
        with open(path, encoding="utf-8") as handle:
            data = json.load(handle)
        return data if isinstance(data, dict) else {}
    except (OSError, json.JSONDecodeError):
        # Cache absent ou corrompu : on renvoie tout. 702 Ko, quelques secondes.
        return {}


def save_cache(path: str, cache: dict[str, str]) -> None:
    tmp = path + ".tmp"
    with open(tmp, "w", encoding="utf-8") as handle:
        json.dump(cache, handle, indent=1, sort_keys=True)
    os.replace(tmp, path)


# ---------------------------------------------------------------------------
# Scan
# ---------------------------------------------------------------------------


def iter_dumps(root: str):
    """Rend (chemin relatif POSIX, chemin absolu) pour chaque .sky de 1024 octets."""
    for dirpath, _, filenames in os.walk(root):
        for filename in sorted(filenames):
            lowered = filename.lower()
            if lowered.endswith(IGNORED_SUFFIXES) or lowered == "desktop.ini":
                continue
            if not lowered.endswith(".sky"):
                continue
            absolute = os.path.join(dirpath, filename)
            try:
                size = os.path.getsize(absolute)
            except OSError as exc:
                print(f"WARN  {absolute} : taille illisible ({exc})", file=sys.stderr)
                continue
            if size != DUMP_SIZE:
                print(f"WARN  {absolute} : {size} octets, {DUMP_SIZE} attendus — ignore",
                      file=sys.stderr)
                continue
            relative = os.path.relpath(absolute, root).replace(os.sep, "/")
            yield relative, absolute


def read_dump(absolute: str) -> bytes | None:
    """Lecture seule, stricte sur la taille."""
    try:
        with open(absolute, "rb") as handle:  # "rb" uniquement — jamais "wb"/"r+b"
            data = handle.read(DUMP_SIZE + 1)
    except OSError as exc:
        print(f"WARN  {absolute} : lecture impossible ({exc})", file=sys.stderr)
        return None
    if len(data) != DUMP_SIZE:
        print(f"WARN  {absolute} : {len(data)} octets lus — ignore", file=sys.stderr)
        return None
    return data


# ---------------------------------------------------------------------------
# Envoi
# ---------------------------------------------------------------------------


class IngestClient:
    def __init__(self, server_url: str, token: str, timeout: float = 30.0):
        self.base = server_url.rstrip("/")
        self.endpoint = self.base + "/api/ingest"
        self.token = token
        self.timeout = timeout

    def _post(self, path: str, body: dict) -> tuple[bool, dict | str]:
        request = urllib.request.Request(
            self.base + path, data=json.dumps(body).encode("utf-8"), method="POST",
            headers={"Content-Type": "application/json",
                     "Authorization": f"Bearer {self.token}"})
        try:
            with urllib.request.urlopen(request, timeout=self.timeout) as response:
                raw = response.read().decode("utf-8")
                return True, (json.loads(raw) if raw else {})
        except urllib.error.HTTPError as exc:
            return False, f"HTTP {exc.code} — {exc.read().decode('utf-8', 'replace')[:300]}"
        except urllib.error.URLError as exc:
            return False, f"injoignable — {exc.reason}"

    def start_scan(self, trigger: str) -> int | None:
        """Ouvre un scan_run cote serveur. Un echec n'empeche pas d'ingerer : le journal est
        un confort, pas une condition."""
        ok, body = self._post("/api/ingest/scan", {"trigger": trigger})
        if not ok:
            print(f"  (journal de scan indisponible : {body})", file=sys.stderr)
            return None
        return body.get("id")

    def finish_scan(self, scan_id: int | None, stats: dict) -> None:
        if scan_id is None:
            return
        self._post(f"/api/ingest/scan/{scan_id}/finish", {
            "filesScanned": stats["scanned"],
            "filesChanged": stats["stored"],
            "filesFailed": stats["failed"],
        })

    def send(self, relative: str, content: bytes, digest: str) -> tuple[bool, str]:
        payload = json.dumps({
            "relativePath": relative,
            "contentBase64": base64.b64encode(content).decode("ascii"),
            "sha256": digest,
        }).encode("utf-8")
        request = urllib.request.Request(
            self.endpoint, data=payload, method="POST",
            headers={"Content-Type": "application/json",
                     "Authorization": f"Bearer {self.token}"})
        try:
            with urllib.request.urlopen(request, timeout=self.timeout) as response:
                body = json.loads(response.read().decode("utf-8"))
                return True, body.get("status", str(response.status))
        except urllib.error.HTTPError as exc:
            detail = exc.read().decode("utf-8", "replace")[:300]
            return False, f"HTTP {exc.code} — {detail}"
        except urllib.error.URLError as exc:
            return False, f"injoignable — {exc.reason}"


# ---------------------------------------------------------------------------


def push_if_changed(relative, absolute, cache, client, stats) -> None:
    content = read_dump(absolute)
    if content is None:
        stats["failed"] += 1
        return
    digest = hashlib.sha256(content).hexdigest()
    stats["scanned"] += 1
    if cache.get(relative) == digest:
        return
    ok, detail = client.send(relative, content, digest)
    if ok:
        cache[relative] = digest  # mis a jour seulement apres un envoi reussi
        # Le serveur distingue un enregistrement d'un contenu deja connu. Compter les deux
        # ensemble ferait croire a 702 nouvelles donnees a chaque scan cache vide.
        if detail == "UNCHANGED":
            stats["unchanged"] += 1
        elif detail == "EXCLUDED":
            # Le serveur tient la liste (exclusions.txt) : l'agent envoie tout et se contente
            # de rapporter fidelement ce qui a ete retenu.
            stats["excluded"] += 1
        else:
            stats["stored"] += 1
            print(f"  enregistre  {relative}")
    else:
        stats["failed"] += 1
        print(f"  ECHEC   {relative}  {detail}", file=sys.stderr)


def full_scan(root, cache, client) -> dict:
    stats = {"scanned": 0, "stored": 0, "unchanged": 0, "excluded": 0, "failed": 0}
    for relative, absolute in iter_dumps(root):
        push_if_changed(relative, absolute, cache, client, stats)
    return stats


def watch(root, cache, client) -> None:
    """Surveille le dossier par sondage.

    Le WatchService de SPEC.md §8 suppose Java ; en Python, un sondage toutes les
    2 s sur 702 fichiers coute quelques millisecondes et evite d'ajouter
    `watchdog` en dependance. Le debounce de 2 s reste indispensable : Cemu ecrit
    pendant la partie (SPEC.md §8.2).
    """
    print(f"\nSurveillance de {root} — Ctrl+C pour arreter.")
    known = {rel: os.path.getmtime(abs_) for rel, abs_ in iter_dumps(root)}
    pending: dict[str, float] = {}
    try:
        while True:
            time.sleep(POLL_SECONDS)
            now = time.time()
            for relative, absolute in iter_dumps(root):
                try:
                    mtime = os.path.getmtime(absolute)
                except OSError:
                    continue
                if known.get(relative) != mtime:
                    known[relative] = mtime
                    pending[relative] = now
            ready = [rel for rel, seen in pending.items() if now - seen >= DEBOUNCE_SECONDS]
            for relative in ready:
                del pending[relative]
                absolute = os.path.join(root, relative.replace("/", os.sep))
                stats = {"scanned": 0, "stored": 0, "unchanged": 0, "excluded": 0, "failed": 0}
                scan_id = client.start_scan("WATCH")
                push_if_changed(relative, absolute, cache, client, stats)
                client.finish_scan(scan_id, stats)
                if stats["stored"] or stats["failed"]:
                    save_cache(cache_path(), cache)
    except KeyboardInterrupt:
        print("\nArret demande. Les dernieres donnees recues restent consultables.")


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(
        description="Pousse les dumps .sky modifies vers le serveur Skylanders.",
        epilog="Lecture seule : n'ecrit jamais dans le dossier des .sky.")
    parser.add_argument("--config", default="agent/agent.yaml")
    parser.add_argument("--once", action="store_true",
                        help="scan unique, sans surveillance")
    parser.add_argument("--reset-cache", action="store_true",
                        help="ignore le cache et renvoie tout")
    args = parser.parse_args(argv)

    config = load_config(args.config)
    root = config["skylandersRoot"]
    if not os.path.isdir(root):
        print(f"ERREUR : skylandersRoot introuvable : {root}", file=sys.stderr)
        return 2

    path = cache_path()
    cache = {} if args.reset_cache else load_cache(path)
    client = IngestClient(config["serverUrl"], config["token"])

    print(f"Source  : {root}")
    print(f"Serveur : {config['serverUrl']}")
    print(f"Cache   : {path} ({len(cache)} entrees connues)")
    print("Scan initial...")

    started = time.time()
    scan_id = client.start_scan("MANUAL" if args.once else "STARTUP")
    stats = full_scan(root, cache, client)
    client.finish_scan(scan_id, stats)
    save_cache(path, cache)
    parts = [f"{stats['stored']} enregistres", f"{stats['unchanged']} inchanges"]
    if stats["excluded"]:
        parts.append(f"{stats['excluded']} exclus")
    parts.append(f"{stats['failed']} en echec")
    print(f"Scan termine en {time.time() - started:.1f} s : {stats['scanned']} fichiers, "
          + ", ".join(parts))

    if stats["failed"] and not stats["stored"]:
        print("Aucun envoi n'a abouti — verifier serverUrl et token.", file=sys.stderr)
        return 1
    if args.once:
        return 0
    watch(root, cache, client)
    save_cache(path, cache)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
