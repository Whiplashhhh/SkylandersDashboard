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

from catalog_bootstrap import fold, levenshtein, squash  # noqa: E402

EXTENSIONS = (".webp", ".png", ".jpg", ".jpeg")

# Suffixes de nom de fichier a ignorer avant appariement. Le lot fourni est nomme
# « Nom_Perso_Icon.webp » : sans cette etape, « Food_Fight_Icon » ne rejoindrait
# jamais « Food Fight ».
NAME_SUFFIXES = ("icon", "icone", "portrait", "art")

GAMES = {
    "SPYROS_ADVENTURE": ("spyros adventure", "spyro s adventure", "spyro adventure", "ssa"),
    "GIANTS": ("giants", "geants"),
    "SWAP_FORCE": ("swap force", "swapforce"),
    "TRAP_TEAM": ("trap team", "trapteam"),
    "SUPERCHARGERS": ("superchargers", "super chargers"),
    "IMAGINATORS": ("imaginators",),
}


def clean_stem(stem: str) -> str:
    """Nom exploitable d'un fichier image : underscores en espaces, suffixe retire."""
    name = stem.replace("_", " ").replace("-", " ").strip()
    lowered = name.lower()
    for suffix in NAME_SUFFIXES:
        if lowered.endswith(" " + suffix):
            name = name[: -len(suffix) - 1].strip()
            break
    return name


# Seuil de recouvrement pour accepter qu'un nom soit contenu dans l'autre. Le bootstrap
# du catalogue utilise un matching bien plus permissif, parce qu'il repond a une autre
# question : « est-ce la meme famille ? » (« Jet Vac » et « Full Blast Jet Vac » en sont
# une). Ici la question est « est-ce le MEME jouet ? », et confondre les deux collerait
# l'image d'une variante sur la figurine de base.
CONTAINMENT_RATIO = 0.7


def match_score(a: str, b: str) -> int | None:
    """Distance entre deux noms replies, ou None s'ils ne designent pas le meme jouet.

    Plus la valeur est basse, meilleur est l'appariement.
    """
    if a == b:
        return 0
    short, long = (a, b) if len(a) <= len(b) else (b, a)
    if short and short in long and len(short) / len(long) >= CONTAINMENT_RATIO:
        return len(long) - len(short)
    for x, y in ((a, b), (a.replace("vv", "w"), b.replace("vv", "w"))):
        distance = levenshtein(x, y)
        if distance <= 2:  # coquilles du pack (SPEC.md 6.4)
            return distance
    return None


def slug(text: str) -> str:
    """Meme regle que ImageController.slug cote serveur : minuscules alphanumeriques."""
    return squash(clean_stem(text))

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
    approx: list[tuple[str, list[dict], int]] = []
    unmatched: list[str] = []

    for filename in images:
        stem, extension = os.path.splitext(filename)
        key = squash(clean_stem(stem))
        targets = index.get(key)
        if targets:
            for entry in targets:
                planned[target_name(entry, extension.lower())] = os.path.join(source, filename)
            exact += 1
            continue

        # Aucun nom identique : on classe TOUS les candidats plausibles et on garde le
        # meilleur. Prendre le premier venu collait « Elite_Spyro » sur le Spyro de base
        # au lieu du Spyro Elite d'Eon, et « Eggsellent_Weeruptor » sur Eruptor.
        scored = []
        for candidate_key, entries in index.items():
            score = match_score(key, candidate_key)
            if score is not None:
                scored.append((score, len(candidate_key), candidate_key, entries))
        if not scored:
            unmatched.append(filename)
            continue

        scored.sort()
        best_score = scored[0][0]
        # Une entree est indexee par son nom francais ET son nom anglais : sans
        # dedoublonnage sur l'identite, elle apparait deux fois dans le rapport.
        chosen: dict[tuple[int, int], dict] = {}
        for item in scored:
            if item[0] != best_score:
                break
            for entry in item[3]:
                chosen.setdefault((entry["toyId"], entry["variantId"]), entry)
        approx.append((filename, list(chosen.values()), best_score))
        if allow_fuzzy:
            for entry in chosen.values():
                planned[target_name(entry, extension.lower())] = os.path.join(source, filename)
            fuzzy += 1

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

    if approx:
        verb = "APPLIQUE" if allow_fuzzy else "PROPOSE"
        print(f"\n  Appariements approximatifs ({verb}) :")
        for filename, entries, score in approx:
            cibles = ", ".join(
                f"{e['nameFr']} [{e['toyId']}/{e['variantId']}]" for e in entries[:3])
            note = "coquille" if score <= 2 else "prefixe/suffixe de variante"
            print(f"    {filename:38s} -> {cibles}   ({note}, ecart {score})")
        if not allow_fuzzy:
            print("    Rien n'a ete retenu : relancer avec --fuzzy apres avoir relu cette liste.")

    if unmatched:
        print(f"\n  Sans cible dans le catalogue ({len(unmatched)}) :")
        for filename in unmatched[:20]:
            print(f"    {filename}")
        if len(unmatched) > 20:
            print(f"    … et {len(unmatched) - 20} autres")
        print("    Ces jouets ne figurent pas dans ton pack : rien a leur associer.")

    print(f"\n  {len(images)} image(s) en entree : {exact} exacte(s), "
          f"{len(approx)} approximative(s), {len(unmatched)} sans cible")

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


def import_villains(source: str, out: str, apply: bool) -> None:
    """Ecrit villain_<slug>.<ext> a partir d'images nommees par vilain.

    Le serveur retrouve l'image en appliquant la meme regle de slug au nom saisi
    dans l'interface : nommer « Buzzer Beak » lie automatiquement le piege a
    « Buzzer_Beak_Icon.webp ». C'est le seul lien possible, aucune source externe
    ne donnant la correspondance identifiant -> nom (SPEC.md §7.2).
    """
    print("=== Images de vilains ===")
    images = [f for f in sorted(os.listdir(source)) if f.lower().endswith(EXTENSIONS)]
    if not images:
        raise SystemExit(f"ERREUR : aucune image dans {source}")
    written = 0
    for filename in images:
        stem, extension = os.path.splitext(filename)
        key = slug(stem)
        if not key:
            print(f"  ignore (nom vide apres nettoyage) : {filename}")
            continue
        destination = os.path.join(out, f"villain_{key}{extension.lower()}")
        if written < 10:
            print(f"  {clean_stem(stem):28s} -> villain_{key}{extension.lower()}")
        if apply:
            shutil.copyfile(os.path.join(source, filename), destination)
        written += 1
    if written > 10:
        print(f"  … et {written - 10} autres")
    print(f"  {written} image(s){' copiee(s)' if apply else ' a copier (simulation)'}")
    print("  Rappel : le nom saisi dans l'interface doit correspondre au nom du fichier.")


def import_games(source: str, out: str, apply: bool) -> None:
    """Ecrit game_<JEU>.<ext> a partir d'images nommees par jeu."""
    print("=== Logos de jeux ===")
    lookup = {alias: constant for constant, aliases in GAMES.items() for alias in aliases}
    images = [f for f in sorted(os.listdir(source)) if f.lower().endswith(EXTENSIONS)]
    matched = 0
    for filename in images:
        stem, extension = os.path.splitext(filename)
        name = clean_stem(stem)
        key = squash(name)
        constant = next((c for alias, c in lookup.items() if squash(alias) == key), None)
        if constant is None:
            constant = next((c for alias, c in lookup.items()
                             if squash(alias) in key or key in squash(alias)), None)
        if constant is None:
            print(f"  NON APPARIE  {filename}  (attendu : {', '.join(sorted(GAMES))})")
            continue
        print(f"  {name:28s} -> game_{constant}{extension.lower()}")
        if apply:
            shutil.copyfile(os.path.join(source, filename),
                            os.path.join(out, f"game_{constant}{extension.lower()}"))
        matched += 1
    print(f"  {matched}/{len(GAMES)} logo(s){' copie(s)' if apply else ' a copier (simulation)'}")


def report_missing(out: str, catalog: list[dict]) -> None:
    have = {f for f in os.listdir(out)} if os.path.isdir(out) else set()
    missing = [e for e in catalog
               if not any(target_name(e, ext) in have for ext in EXTENSIONS)]
    print(f"=== {len(missing)} identite(s) sans image sur {len(catalog)} ===")
    for entry in missing[:40]:
        print(f"  {target_name(entry, EXTENSIONS[0]):18s} {entry['nameFr']}  [{entry['game']}]")
    if len(missing) > 40:
        print(f"  … et {len(missing) - 40} autres")


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(
        description="Remplit le dossier d'images servi par le serveur.",
        epilog="Ne modifie jamais le pack : seule la destination est ecrite.")
    parser.add_argument("--from", dest="source", help="dossier d'images nommees par personnage")
    parser.add_argument("--villains", help="dossier d'images nommees par vilain")
    parser.add_argument("--games", help="dossier de logos nommes par jeu")
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

    if not (args.source or args.elements or args.missing or args.villains or args.games):
        parser.error("rien a faire : passer --from, --villains, --games, --elements ou --missing")

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
    for label, folder, action in (("--villains", args.villains, import_villains),
                                  ("--games", args.games, import_games)):
        if not folder:
            continue
        folder = os.path.expanduser(folder)
        if not os.path.isdir(folder):
            print(f"ERREUR : dossier {label} introuvable : {folder}", file=sys.stderr)
            return 2
        action(folder, out, args.apply)
        print()

    if args.missing:
        report_missing(out, catalog)

    print(f"Dossier destination : {out}")
    if not args.apply:
        print("Aucune ecriture : ajouter --apply pour appliquer.")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
