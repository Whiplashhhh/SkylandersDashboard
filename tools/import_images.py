#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
import_images.py — remplit le dossier d'images servi par le serveur.

Deux usages, combinables :

  --from <dossier>   apparie un lot d'images nommees par personnage
                     (« Wildfire.png », « Spyro.png »…) aux identites de
                     catalog.json, et les ecrit sous <toyId>_<variantId>.<ext>.

  --elements         copie les symboles d'element deja presents dans le pack
                     (« AirSymbolSkylanders.png »…, SPEC.md §5.5) sous
                     element_<ELEMENT>.png.

**Simulation par defaut.** Rien n'est ecrit tant que --apply n'est pas passe :
un appariement approximatif se relit avant de s'appliquer.

Le serveur n'a aucun acces au systeme de fichiers du laptop (CLAUDE.md,
invariant 1). Cet outil tourne donc ici, et depose les fichiers dans le dossier
que le serveur monte en volume. Il ne modifie jamais le pack : la source est
ouverte en lecture seule, seule la destination est ecrite.

Usage :
    python3 tools/import_images.py --elements --out ./images
    python3 tools/import_images.py --from ~/mes-images --out ./images
    python3 tools/import_images.py --from ~/mes-images --out ./images --apply
    python3 tools/import_images.py --out ./images --missing     # ce qu'il reste a fournir
"""

from __future__ import annotations

import argparse
import json
import os
import shutil
import sys
import unicodedata
from collections import defaultdict

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from catalog_bootstrap import close_enough, fold, squash  # noqa: E402

EXTENSIONS = (".png", ".jpg", ".jpeg", ".webp")

# Nom anglais du symbole dans le pack -> nom de la constante Element cote serveur.
SYMBOLS = {
    "air": "AIR", "water": "EAU", "fire": "FEU", "life": "VIE", "magic": "MAGIE",
    "tech": "TECH", "earth": "TERRE", "undead": "MORT_VIVANT", "light": "LUMIERE",
    "dark": "TENEBRES",
}


def load_catalog(path: str) -> list[dict]:
    if not os.path.exists(path):
        raise SystemExit(f"ERREUR : {path} introuvable — lancer d'abord catalog_bootstrap.py")
    with open(path, encoding="utf-8") as handle:
        return json.load(handle)["toys"]


def target_name(entry: dict, extension: str) -> str:
    return f"{entry['toyId']}_{entry['variantId']}{extension}"


# ---------------------------------------------------------------------------
# Symboles d'element
# ---------------------------------------------------------------------------


def import_elements(pack: str, out: str, apply: bool) -> None:
    print("=== Symboles d'element ===")
    found: dict[str, str] = {}
    for dirpath, _, filenames in os.walk(pack):
        for filename in filenames:
            lowered = filename.lower()
            if "symbolskylanders" not in lowered:
                continue
            english = lowered.split("symbolskylanders")[0]
            constant = SYMBOLS.get(english)
            if not constant:
                print(f"  ignore (element inconnu) : {filename}")
                continue
            # Le meme symbole existe dans plusieurs jeux : le premier suffit.
            found.setdefault(constant, os.path.join(dirpath, filename))

    for constant, source in sorted(found.items()):
        destination = os.path.join(out, f"element_{constant}.png")
        print(f"  {constant:12s} <- {os.path.relpath(source, pack)}")
        if apply:
            shutil.copyfile(source, destination)
    missing = sorted(set(SYMBOLS.values()) - set(found))
    if missing:
        print(f"  absents du pack : {', '.join(missing)} — un badge genere prendra le relais")
    print(f"  {len(found)} symbole(s){' copie(s)' if apply else ' a copier (simulation)'}")


# ---------------------------------------------------------------------------
# Images de figurines
# ---------------------------------------------------------------------------


def build_index(catalog: list[dict]) -> dict[str, list[dict]]:
    index: dict[str, list[dict]] = defaultdict(list)
    for entry in catalog:
        for name in (entry["nameFr"], entry["nameEn"]):
            index[squash(name)].append(entry)
    return index


def import_figures(source: str, out: str, catalog: list[dict],
                   apply: bool, allow_fuzzy: bool, fill_variants: bool) -> None:
    print("=== Images de figurines ===")
    index = build_index(catalog)
    by_toy: dict[int, list[dict]] = defaultdict(list)
    for entry in catalog:
        by_toy[entry["toyId"]].append(entry)

    images = [f for f in sorted(os.listdir(source))
              if f.lower().endswith(EXTENSIONS)]
    if not images:
        raise SystemExit(f"ERREUR : aucune image dans {source}")

    planned: dict[str, str] = {}
    exact = fuzzy = 0
    unmatched: list[str] = []

    for filename in images:
        stem, extension = os.path.splitext(filename)
        key = squash(stem)
        targets = index.get(key)
        kind = "exact"
        if not targets:
            candidates = [e for k, entries in index.items() if close_enough(key, k)
                          for e in entries]
            if candidates and allow_fuzzy:
                targets, kind = candidates, "approx"
            elif candidates:
                print(f"  APPROX  {filename!r} ~ {candidates[0]['nameFr']!r} "
                      f"— ignore, relancer avec --fuzzy pour l'accepter")
                unmatched.append(filename)
                continue
            else:
                unmatched.append(filename)
                continue

        for entry in targets:
            planned[target_name(entry, extension.lower())] = os.path.join(source, filename)
        if kind == "exact":
            exact += 1
        else:
            fuzzy += 1
            names = ", ".join(sorted({e["nameFr"] for e in targets}))
            print(f"  approx  {filename!r} -> {names}")

    if fill_variants:
        # Une variante sans image propre reprend celle de sa forme de base : « Wildfire Sombre »
        # ressemble suffisamment a « Wildfire » pour que ce soit utile en attendant mieux.
        added = 0
        for toy_id, entries in by_toy.items():
            base = next((e for e in entries
                         if target_name(e, ".png") in planned
                         or target_name(e, ".jpg") in planned), None)
            if not base:
                continue
            source_file = planned.get(target_name(base, ".png")) \
                or planned.get(target_name(base, ".jpg"))
            for entry in entries:
                if any(target_name(entry, ext) in planned for ext in (".png", ".jpg")):
                    continue
                extension = os.path.splitext(source_file)[1].lower()
                planned[target_name(entry, extension)] = source_file
                added += 1
        print(f"  {added} variante(s) recuperent l'image de leur forme de base")

    print(f"\n  {len(images)} image(s) en entree : {exact} exacte(s), {fuzzy} approximative(s), "
          f"{len(unmatched)} non appariee(s)")
    for filename in unmatched[:20]:
        print(f"    NON APPARIEE  {filename}")
    if len(unmatched) > 20:
        print(f"    … et {len(unmatched) - 20} autres")

    print(f"  {len(planned)} fichier(s) a ecrire dans {out}")
    if apply:
        for name, origin in sorted(planned.items()):
            shutil.copyfile(origin, os.path.join(out, name))
        print("  ecrit.")
    else:
        for name, origin in sorted(planned.items())[:10]:
            print(f"    {name}  <- {os.path.basename(origin)}")
        if len(planned) > 10:
            print(f"    … et {len(planned) - 10} autres")
        print("  SIMULATION — relancer avec --apply pour ecrire.")


def report_missing(out: str, catalog: list[dict]) -> None:
    have = {f for f in os.listdir(out)} if os.path.isdir(out) else set()
    missing = [e for e in catalog
               if not any(target_name(e, ext) in have for ext in EXTENSIONS)]
    print(f"=== {len(missing)} identite(s) sans image sur {len(catalog)} ===")
    for entry in missing[:40]:
        print(f"  {target_name(entry, '.png'):18s} {entry['nameFr']}  [{entry['game']}]")
    if len(missing) > 40:
        print(f"  … et {len(missing) - 40} autres")


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(
        description="Remplit le dossier d'images servi par le serveur.",
        epilog="Ne modifie jamais le pack : seule la destination est ecrite.")
    parser.add_argument("--from", dest="source", help="dossier d'images nommees par personnage")
    parser.add_argument("--elements", action="store_true", help="copier les symboles du pack")
    parser.add_argument("--missing", action="store_true", help="lister ce qu'il reste a fournir")
    parser.add_argument("--out", default="images", help="dossier destination (defaut: ./images)")
    parser.add_argument("--pack", default="~/Games/Cemu/skylanders")
    parser.add_argument("--catalog", default="catalog.json")
    parser.add_argument("--apply", action="store_true", help="ecrire pour de vrai")
    parser.add_argument("--fuzzy", action="store_true",
                        help="accepter les appariements approximatifs")
    parser.add_argument("--fill-variants", action="store_true",
                        help="donner aux variantes l'image de leur forme de base")
    args = parser.parse_args(argv)

    if not (args.source or args.elements or args.missing):
        parser.error("rien a faire : passer --from, --elements ou --missing")

    out = os.path.expanduser(args.out)
    if args.apply:
        os.makedirs(out, exist_ok=True)
    catalog = load_catalog(args.catalog)

    if args.elements:
        pack = os.path.expanduser(args.pack)
        if not os.path.isdir(pack):
            print(f"ERREUR : pack introuvable : {pack}", file=sys.stderr)
            return 2
        import_elements(pack, out, args.apply)
        print()
    if args.source:
        source = os.path.expanduser(args.source)
        if not os.path.isdir(source):
            print(f"ERREUR : dossier source introuvable : {source}", file=sys.stderr)
            return 2
        import_figures(source, out, catalog, args.apply, args.fuzzy, args.fill_variants)
        print()
    if args.missing:
        report_missing(out, catalog)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
