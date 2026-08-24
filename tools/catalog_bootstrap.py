#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
catalog_bootstrap.py — ferme la phase 0 et amorce le catalogue.

Deux objectifs, dans cet ordre :

  1. VALIDER les offsets du toy ID (0x10) et du variant ID (0x1C), qui sont
     encore au niveau HYPOTHESE dans FORMAT.md 6. C'est le dernier critere de
     sortie de la phase 0 (SPEC.md 12).

  2. Produire un premier catalog.json (SPEC.md 6.5), a corriger ensuite a la
     main. Ce n'est un sous-produit : la validation vient d'abord.

La validation ne depend d'AUCUNE source externe. Le pack etant complet
(SPEC.md 6.6), la coherence interne suffit :

  * un toy ID doit designer un seul personnage — plusieurs noms pour un meme
    toy ID ne sont acceptables que s'ils forment une famille de variantes
    (« Jet Vac » et « Full Blast Jet Vac » partagent leur toy ID par
    construction, et se distinguent par le variant ID) ;
  * un personnage doit avoir un seul toy ID ;
  * deux fichiers partageant un toy ID doivent avoir des variant ID distincts,
    sinon ils revendiquent la meme identite.

Une divergence massive (>10 %) signerait un offset faux — c'est le canari
annonce en SPEC.md 6.3.

Lecture seule : les .sky sont ouverts en "rb" et jamais ecrits (CLAUDE.md,
invariant 1).

Usage :
    python3 tools/catalog_bootstrap.py [--root <dossier>] [--out catalog.json]
    python3 tools/catalog_bootstrap.py --verbose      # detaille chaque anomalie
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
import unicodedata
from collections import Counter, defaultdict

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import skycrypto as sky  # noqa: E402

DEFAULT_ROOT = os.path.expanduser("~/Games/Cemu/skylanders")
DEFAULT_EXCLUSIONS = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                                  "exclusions.txt")


def load_exclusions(path: str) -> set[str]:
    """Chemins relatifs a ignorer (cf. exclusions.txt).

    Le fichier reste sur le disque et n'est jamais modifie : on refuse seulement
    de le cataloguer. Le serveur lit la meme liste, pour que les deux moities du
    systeme aient exactement la meme notion de « ce fichier ne me concerne pas ».
    """
    if not os.path.exists(path):
        return set()
    excluded = set()
    with open(path, encoding="utf-8") as handle:
        for line in handle:
            line = line.split("#", 1)[0].strip()
            if line:
                excluded.add(normalize(line).replace(os.sep, "/"))
    return excluded

# --------------------------------------------------------------------------
# Referentiels de chemin (SPEC.md 5.2)
# --------------------------------------------------------------------------

GAMES = {
    "Skylanders Spyros Adventure": "SPYROS_ADVENTURE",
    "Skylanders Giants": "GIANTS",
    "Skylanders Swap Force": "SWAP_FORCE",
    "Skylanders Trap Team": "TRAP_TEAM",
    "Skylanders SuperChargers": "SUPERCHARGERS",
    "Skylanders Imaginators": "IMAGINATORS",
}

ELEMENTS = {
    "Air", "Eau", "Feu", "Lumière", "Magie",
    "Mort-Vivant", "Ténèbres", "Terre", "Tech", "Vie",
}

# "Kaos" n'est pas un element du jeu mais occupe la meme place dans
# l'arborescence (Imaginators/Kaos, Trap Team/Pièges/Kaos). Observe dans le
# pack le 2026-08-22 — absorbe tel quel, on ne reorganise pas (invariant 4).
PSEUDO_ELEMENTS = {"Kaos"}

CATEGORIES = {
    "Acolytes": "SIDEKICK",
    "Minis": "MINI",
    "Objets": "ITEM",
    "Packs Aventure": "ADVENTURE_PACK",
    "Coffres": "CHEST",
    "Cristaux de Création": "CREATION_CRYSTAL",
    "Géants": "GIANT",
}

# Consommables : plusieurs exemplaires du meme modele sont normaux, ils
# partagent legitimement toy ID et variant ID (mesure du 2026-08-18 : jusqu'a
# 55 fichiers pour un meme couple). Exclus des tests d'unicite.
CONSUMABLE_CATEGORIES = {"CHEST", "CREATION_CRYSTAL"}

# Suffixes de variante francais (SPEC.md 6.2 etape 3), le plus long d'abord
# pour que « Main du Destin Légendaire » ne soit pas coupe par « Légendaire »
# avant d'avoir teste les suffixes composes.
VARIANT_FR_TO_EN = {
    "Élite d'Eon": "Eon's Elite", "Légendaire": "Legendary", "Série 2": "Series 2",
    "LightCore": "LightCore", "Power Blue": "Power Blue", "Doré": "Golden",
    "Platine": "Platinum", "Sombre": "Dark", "Nitro": "Nitro",
    "(Haut)": "(Top)", "(Bas)": "(Bottom)",
}
# Le plus long d'abord, pour que « Main du Destin Légendaire » ne soit pas
# coupe par « Légendaire » avant d'avoir teste les suffixes composes.
VARIANT_SUFFIXES = sorted(VARIANT_FR_TO_EN, key=len, reverse=True)

# --------------------------------------------------------------------------
# Normalisation (SPEC.md 6.2 etape 1, CLAUDE.md « Conventions »)
# --------------------------------------------------------------------------


def normalize(text: str) -> str:
    """NFC, apostrophes unifiees, espaces reduits."""
    text = unicodedata.normalize("NFC", text)
    text = text.replace("’", "'").replace("ʼ", "'")
    return re.sub(r"\s+", " ", text).strip()


def fold(text: str) -> str:
    """Forme repliee pour comparaison : sans accents, sans casse."""
    decomposed = unicodedata.normalize("NFD", normalize(text))
    stripped = "".join(c for c in decomposed if not unicodedata.combining(c))
    return stripped.lower()


def squash(text: str) -> str:
    """Forme repliee ET depouillee de toute ponctuation, pour le matching
    tolerant du bootstrap (SPEC.md 6.4). JAMAIS en runtime."""
    return re.sub(r"[^a-z0-9]", "", fold(text))


def levenshtein(a: str, b: str) -> int:
    if a == b:
        return 0
    if len(a) < len(b):
        a, b = b, a
    previous = list(range(len(b) + 1))
    for i, ca in enumerate(a, 1):
        current = [i]
        for j, cb in enumerate(b, 1):
            current.append(min(previous[j] + 1, current[j - 1] + 1,
                               previous[j - 1] + (ca != cb)))
        previous = current
    return previous[-1]


def close_enough(a: str, b: str) -> bool:
    """Meme personne, aux coquilles du pack pres (SPEC.md 6.4).

    Absorbe « Cobra Candabra »/« Cobra Cadabra », « Tri-Tip »/« Tritip »,
    « Flashwing »/« Flash Wing », « Bad_Juju »/« Badjuju », « VVind-Up »/
    « Wind Up ». Bootstrap uniquement, revue humaine obligatoire.
    """
    sa, sb = squash(a), squash(b)
    if not sa or not sb:
        return False
    if sa in sb or sb in sa:
        return True
    if levenshtein(sa, sb) <= 2:
        return True
    # « vv » saisi pour « w » — coquille de translitteration du pack
    return levenshtein(sa.replace("vv", "w"), sb.replace("vv", "w")) <= 2


# --------------------------------------------------------------------------
# Tables de correspondance FR -> EN, lues dans le pack
# --------------------------------------------------------------------------


def load_correspondences(path: str) -> dict[str, str]:
    """Parse les tables markdown de « Correspondances FR.md » -> {fr: en}.

    Le fichier contient les deux formes d'apostrophe pour certaines entrees
    (« Dragon's Peak » / « Dragon's Peak ») : la normalisation les fusionne, ce
    qui est exactement l'effet recherche.
    """
    if not os.path.exists(path):
        raise SystemExit(
            f"ERREUR : table de correspondances introuvable : {path}\n"
            f"        Utiliser --correspondances pour en indiquer une autre."
        )
    mapping: dict[str, str] = {}
    row = re.compile(r"^\|\s*(.+?)\s*\|\s*(.+?)\s*\|\s*$")
    with open(path, encoding="utf-8") as handle:
        for line in handle:
            match = row.match(line)
            if not match:
                continue
            english, french = match.group(1), match.group(2)
            if english.startswith("-") or english.lower() == "anglais":
                continue
            # Les lignes generiques (« Legendary Nom », « Bronze Chest n »)
            # decrivent un motif, pas une entree : elles sont traitees par les
            # suffixes de variante et par la logique consommables.
            if " Nom" in english or english.endswith(" n"):
                continue
            mapping[fold(french)] = normalize(english)
    return mapping


# --------------------------------------------------------------------------
# Classification du chemin (SPEC.md 5.2, premiere regle gagnante)
# --------------------------------------------------------------------------


def classify(parts: list[str]) -> tuple[str, str, str, bool]:
    """(game, category, element, is_variant_folder) depuis les segments du chemin."""
    game = GAMES.get(parts[0], "UNKNOWN")
    rest = parts[1:-1]  # segments intermediaires, hors nom de fichier
    known_element = lambda s: s in ELEMENTS or s in PSEUDO_ELEMENTS  # noqa: E731

    # 1 & 2 : <J>/Pièges/<E>[/Variantes]/f.sky
    if rest and rest[0] == "Pièges":
        element = rest[1] if len(rest) > 1 else "UNKNOWN"
        return game, "TRAP", element, "Variantes" in rest

    # 3 : <J>/<E>/Véhicules/f.sky
    if len(rest) >= 2 and known_element(rest[0]) and rest[1] == "Véhicules":
        return game, "VEHICLE", rest[0], "Variantes" in rest

    # 4 : <J>/<E>/Variantes/f.sky
    if len(rest) >= 2 and known_element(rest[0]) and rest[1] == "Variantes":
        return game, "CHARACTER", rest[0], True

    # 5 : <J>/<Cat>/f.sky
    if rest and rest[0] in CATEGORIES:
        return game, CATEGORIES[rest[0]], "UNKNOWN", "Variantes" in rest

    # 6 : <J>/<E>/f.sky
    if rest and known_element(rest[0]):
        return game, "CHARACTER", rest[0], False

    # 7 : defaut
    return game, "UNKNOWN", "UNKNOWN", False


# --------------------------------------------------------------------------
# Decomposition du nom de fichier (SPEC.md 6.2)
# --------------------------------------------------------------------------


def split_annotation(stem: str) -> tuple[str, str | None]:
    """Detache l'annotation utilisateur apres « - ».

    Pour un piege, la partie AVANT le tiret est le vilain et celle d'APRES le
    piege (« Buzzer Beak - Avis de Tempête ») : les deux segments restent
    disponibles, c'est l'appelant qui choisit.
    """
    if " - " not in stem:
        return stem, None
    head, _, tail = stem.partition(" - ")
    return head.strip(), tail.strip()


def split_variant(name: str) -> tuple[str, str | None]:
    """Detache un suffixe de variante francais. Retourne (base, suffixe)."""
    for suffix in VARIANT_SUFFIXES:
        if name.endswith(" " + suffix):
            return name[: -len(suffix) - 1].strip(), suffix
        if name.endswith(suffix) and suffix.startswith("("):
            return name[: -len(suffix)].strip(), suffix
    return name, None


# --------------------------------------------------------------------------
# Lecture d'un fichier
# --------------------------------------------------------------------------


class Entry:
    __slots__ = (
        "rel", "game", "category", "element", "is_variant", "stem",
        "annotation", "base_fr", "variant_suffix", "name_en", "resolved",
        "toy_id", "variant_id", "uid", "played",
    )

    def __init__(self, **kw):
        for key, value in kw.items():
            setattr(self, key, value)


def read_entry(root: str, path: str, corr: dict[str, str]) -> Entry | None:
    rel = normalize(os.path.relpath(path, root))
    parts = rel.split(os.sep)
    try:
        dump = sky.read_dump(path)
    except sky.DumpError as exc:
        print(f"WARN  {rel} : {exc}", file=sys.stderr)
        return None

    game, category, element, is_variant_folder = classify(parts)
    stem = normalize(os.path.splitext(parts[-1])[0])
    head, annotation = split_annotation(stem)

    # Pour un piege, le nom du PIEGE est l'annotation quand elle existe
    # (« Buzzer Beak - Avis de Tempête » : le tag est le piege, pas le vilain).
    subject = annotation if (category == "TRAP" and annotation) else head

    # Le nom complet est teste EN PREMIER : « Mouton Platine » est un objet a
    # part entiere (Platinum Sheep), pas un « Mouton » en variante Platine.
    if fold(subject) in corr:
        base_fr, variant_suffix = subject, None
    else:
        base_fr, variant_suffix = split_variant(subject)

    english = corr.get(fold(base_fr))
    # Les noms de personnages ne sont pas traduits (SPEC.md 6.2 etape 4) : en
    # l'absence d'entree dans la table, le nom passe tel quel.
    resolved = english is not None
    name_en = english if resolved else base_fr
    # La variante redevient un prefixe en anglais (SPEC.md 6.2 etape 5).
    if variant_suffix:
        prefix = VARIANT_FR_TO_EN[variant_suffix]
        name_en = f"{name_en} {prefix}" if prefix.startswith("(") else f"{prefix} {name_en}"

    return Entry(
        rel=rel, game=game, category=category, element=element,
        is_variant=is_variant_folder or variant_suffix is not None,
        stem=stem, annotation=annotation, base_fr=base_fr,
        variant_suffix=variant_suffix, name_en=name_en, resolved=resolved,
        toy_id=sky.u16le(dump, 0x10), variant_id=sky.u16le(dump, 0x1C),
        uid=dump[0:4].hex().upper(),
        played=bool(sky.written_blocks(dump)),
    )


# --------------------------------------------------------------------------
# Validation — le coeur de la phase 0
# --------------------------------------------------------------------------


def same_family(names: list[str]) -> bool:
    """Vrai si tous les noms partagent un noyau commun.

    « Jet Vac » / « Full Blast Jet Vac » : le plus court est contenu dans les
    autres, c'est une famille de variantes, pas une collision d'identite.
    """
    ordered = sorted(names, key=len)
    core = ordered[0]
    return all(close_enough(core, other) for other in ordered[1:])


def identity_name(entry: Entry) -> str:
    """Nom servant de cle d'identite.

    Les moities Swap Force (Haut)/(Bas) sont deux tags physiques distincts :
    leur suffixe fait partie de l'identite et ne doit pas etre replie.
    """
    if entry.variant_suffix in ("(Haut)", "(Bas)"):
        return f"{entry.base_fr} {entry.variant_suffix}"
    return entry.base_fr


def validate(entries: list[Entry], verbose: bool) -> dict:
    traps = [e for e in entries if e.category == "TRAP"]
    consumables = [e for e in entries if e.category in CONSUMABLE_CATEGORIES]
    testable = [e for e in entries
                if e.category not in CONSUMABLE_CATEGORIES and e.category != "TRAP"]

    print("=" * 78)
    print("VALIDATION DES OFFSETS D'IDENTITE (FORMAT.md 6)")
    print("=" * 78)
    print(f"Fichiers analyses : {len(entries)}")
    print(f"   pieges          : {len(traps)}   (test dedie — structure propre, cf. test 1)")
    print(f"   consommables    : {len(consumables)}   (Coffres, Cristaux — exemplaires multiples)")
    print(f"   personnages/obj : {len(testable)}")

    failures: list[str] = []

    # -- Test 1 : pieges — le toy ID encode l'element, le variant ID la forme
    by_toy_trap: dict[int, set[str]] = defaultdict(set)
    for entry in traps:
        by_toy_trap[entry.toy_id].add(entry.element)
    mixed = {t: els for t, els in by_toy_trap.items() if len(els) > 1}
    trap_dups = [
        (t, v) for (t, v), n in
        Counter((e.toy_id, e.variant_id) for e in traps).items() if n > 1
    ]
    elements_seen = {e.element for e in traps}
    print()
    print("-- Test 1 : pieges — toy ID = element, variant ID = forme " + "-" * 18)
    print(f"   toy ID distincts : {len(by_toy_trap)}   elements distincts : {len(elements_seen)}")
    print(f"   toy ID couvrant plusieurs elements : {len(mixed)}")
    print(f"   couples (toy ID, variant ID) en doublon : {len(trap_dups)}")
    if verbose:
        for toy_id in sorted(by_toy_trap):
            count = sum(1 for e in traps if e.toy_id == toy_id)
            print(f"      {toy_id} -> {next(iter(by_toy_trap[toy_id])):12s} ({count} pieges)")
    if mixed or trap_dups:
        failures.append("pieges : partition par element ou unicite du variant ID rompue")

    # -- Test 2 : une identite (toy ID, variant ID) designe un seul modele
    by_identity: dict[tuple[int, int], list[Entry]] = defaultdict(list)
    for entry in testable:
        by_identity[(entry.toy_id, entry.variant_id)].append(entry)

    duplicate_files, name_conflicts, uid_mismatch = [], [], []
    for key, group in sorted(by_identity.items()):
        names = sorted({identity_name(e) for e in group})
        if len(group) > 1:
            duplicate_files.append((key, group))
            if len({e.uid for e in group}) > 1:
                uid_mismatch.append((key, group))
        if len(names) > 1 and not same_family(names):
            name_conflicts.append((key, names))
    print()
    print("-- Test 2 : une identite (toy ID, variant ID) = un seul modele " + "-" * 14)
    print(f"   identites distinctes : {len(by_identity)}")
    print(f"   identites portees par plusieurs fichiers : {len(duplicate_files)}")
    print(f"      dont UID incoherent entre les copies  : {len(uid_mismatch)}")
    print(f"   identites a noms sans lien (CONFLIT)     : {len(name_conflicts)}")
    if verbose:
        for key, group in duplicate_files:
            print(f"      toy {key[0]} / variant {key[1]} — {len(group)} fichiers :")
            for entry in group:
                print(f"          {entry.rel}")
        for key, names in name_conflicts:
            print(f"      CONFLIT toy {key[0]} / variant {key[1]} : {names}")
    if name_conflicts or uid_mismatch:
        failures.append("identites : noms sans lien ou UID incoherent")

    # -- Test 3 : un modele -> un toy ID (aux conventions connues pres)
    by_name: dict[str, list[Entry]] = defaultdict(list)
    for entry in testable:
        by_name[fold(identity_name(entry))].append(entry)
    own_toy_id, unexplained = [], []
    for name, group in sorted(by_name.items()):
        ids = {e.toy_id for e in group}
        if len(ids) == 1:
            continue
        # Convention observee sur Spyro's Adventure : une variante dont le
        # variant ID vaut 0 ne peut pas etre distinguee par ce champ, elle
        # recoit donc son PROPRE toy ID. C'est le cas des Legendaires
        # (toy ID = base + 400) comme de Spyro Sombre (28 pour un Spyro a 16).
        # Les jeux suivants encodent la variante dans le variant ID et n'ont
        # plus besoin de cette echappatoire.
        primary = {e.toy_id for e in group if e.variant_suffix is None}
        extras = [e for e in group if e.toy_id not in primary]
        if primary and all(e.variant_suffix is not None and e.variant_id == 0
                           for e in extras):
            own_toy_id.append((name, sorted(ids), extras))
        else:
            unexplained.append((name, sorted(ids)))
    print()
    print("-- Test 3 : un modele a-t-il un seul toy ID ? " + "-" * 31)
    print(f"   noms distincts : {len(by_name)}")
    print(f"   variantes a variant ID = 0, donc a toy ID propre : {len(own_toy_id)}")
    print(f"   inexpliques (CONFLIT) : {len(unexplained)}")
    if verbose:
        for name, ids, extras in own_toy_id:
            detail = ", ".join(f"{e.variant_suffix}={e.toy_id}" for e in extras)
            print(f"      toy ID propre  {name!r} -> {ids}   ({detail})")
        for name, ids in unexplained:
            print(f"      CONFLIT {name!r} -> {ids}")
    if unexplained:
        failures.append("un meme modele porte plusieurs toy ID sans convention connue")

    # -- Test 4 : codes de variante recurrents
    by_suffix: dict[str, Counter] = defaultdict(Counter)
    for entry in entries:
        by_suffix[entry.variant_suffix or "(aucun)"][entry.variant_id] += 1
    print()
    print("-- Test 4 : le variant ID code-t-il le type de variante ? " + "-" * 18)
    for suffix, counter in sorted(by_suffix.items(), key=lambda kv: -sum(kv[1].values())):
        top = ", ".join(f"{v} x{n}" for v, n in counter.most_common(3))
        print(f"   {suffix:16s} {sum(counter.values()):4d} fichiers   codes dominants : {top}")

    # -- Test 5 : reverse-mapping FR -> EN
    translatable = [e for e in entries
                    if e.category in {"TRAP", "ITEM", "ADVENTURE_PACK", "VEHICLE"}]
    resolved = [e for e in translatable if e.resolved]
    rate = 100.0 * len(resolved) / max(1, len(translatable))
    print()
    print("-- Test 5 : reverse-mapping FR -> EN (hors personnages) " + "-" * 20)
    print(f"   entrees traduisibles : {len(translatable)}   resolues : {len(resolved)}   -> {rate:.2f} %")
    if verbose:
        for entry in translatable:
            if not entry.resolved:
                print(f"      NON RESOLU [{entry.category}] {entry.rel} -> {entry.base_fr!r}")

    print()
    print("=" * 78)
    if failures:
        print("VERDICT : INCOHERENCE — un des deux offsets est probablement faux.")
        for problem in failures:
            print(f"          - {problem}")
        print("          NE PAS passer FORMAT.md 6 en VERIFIE.")
    else:
        print("VERDICT : offsets 0x10 (toy ID) et 0x1C (variant ID) COHERENTS.")
        print(f"          Aucun conflit inexplique sur les {len(entries)} fichiers retenus.")
        print("          Le canari de SPEC.md 6.3 ne chante pas.")
    print("=" * 78)

    return {
        "files": len(entries), "identities": len(by_identity),
        "trap_toy_ids": len(by_toy_trap), "duplicate_files": len(duplicate_files),
        "name_conflicts": len(name_conflicts), "variant_own_toy_id": len(own_toy_id),
        "unexplained": len(unexplained), "rate_translation": round(rate, 2),
        "ok": not failures,
    }


# --------------------------------------------------------------------------
# Sortie catalog.json (SPEC.md 6.5)
# --------------------------------------------------------------------------


def build_catalog(entries: list[Entry]) -> list[dict]:
    by_key: dict[tuple[int, int], list[Entry]] = defaultdict(list)
    for entry in entries:
        by_key[(entry.toy_id, entry.variant_id)].append(entry)

    catalog = []
    for (toy_id, variant_id), group in sorted(by_key.items()):
        first = group[0]
        names = sorted({e.base_fr for e in group})
        # SPEC.md 6.6 : VALIDATED = l'entree est confirmee par le recoupement.
        # Ici le recoupement est interne (le pack EST le roster) : une identite
        # sans ambiguite de nom est validee. Les noms de personnages ne passent
        # pas par la table de traduction et n'ont pas a le faire.
        passthrough = first.category in {
            "CHARACTER", "MINI", "SIDEKICK", "GIANT", "CHEST", "CREATION_CRYSTAL"
        }
        if len(names) > 1:
            confidence = "REVIEW"
        elif first.resolved or passthrough:
            confidence = "VALIDATED"
        else:
            confidence = "NEW"
        # Le nom francais doit porter sa variante, sinon « Deja Vu » et
        # « Deja Vu Légendaire » sont indiscernables dans l'interface.
        name_fr = first.base_fr
        if first.variant_suffix:
            name_fr = f"{name_fr} {first.variant_suffix}"
        # Les libelles metier sont en francais dans toute l'application (CLAUDE.md,
        # « Conventions »). Emettre « UNKNOWN » ici mettait un mot anglais dans une interface
        # francaise, et donnait deux vocabulaires pour la meme notion entre catalog_toy et toy.
        element = "Inconnu" if first.element == "UNKNOWN" else first.element
        catalog.append({
            "toyId": toy_id,
            "variantId": variant_id,
            "nameEn": first.name_en,
            "nameFr": name_fr,
            "game": first.game,
            "element": element,
            "category": first.category,
            "confidence": confidence,
            "files": [e.rel for e in sorted(group, key=lambda e: e.rel)],
        })
    return catalog


# --------------------------------------------------------------------------


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(
        description="Valide les offsets d'identite et amorce catalog.json.",
        epilog="Lecture seule : n'ecrit jamais dans le dossier source.",
    )
    parser.add_argument("--root", default=DEFAULT_ROOT)
    parser.add_argument("--correspondances", default=None)
    parser.add_argument("--exclusions", default=DEFAULT_EXCLUSIONS)
    parser.add_argument("--out", default="catalog.json")
    parser.add_argument("--no-write", action="store_true",
                        help="valide sans produire catalog.json")
    parser.add_argument("--verbose", action="store_true")
    args = parser.parse_args(argv)

    root = os.path.expanduser(args.root)
    if not os.path.isdir(root):
        print(f"ERREUR : dossier introuvable : {root}", file=sys.stderr)
        return 2
    corr_path = args.correspondances or os.path.join(root, "Correspondances FR.md")
    corr = load_correspondences(corr_path)
    print(f"Table de correspondances : {len(corr)} entrees ({corr_path})")

    paths = []
    for dirpath, _, filenames in os.walk(root):
        for filename in filenames:
            if filename.lower().endswith(".sky"):
                paths.append(os.path.join(dirpath, filename))
    paths.sort()

    excluded = load_exclusions(args.exclusions)
    entries = [e for e in (read_entry(root, p, corr) for p in paths) if e]
    if excluded:
        kept = [e for e in entries if e.rel.replace(os.sep, "/") not in excluded]
        for entry in entries:
            if entry.rel.replace(os.sep, "/") in excluded:
                print(f"EXCLU  {entry.rel}  (cf. {os.path.basename(args.exclusions)})")
        entries = kept
    print(f"Fichiers .sky retenus    : {len(entries)} / {len(paths)}")
    print()

    stats = validate(entries, args.verbose)

    if not args.no_write:
        catalog = build_catalog(entries)
        payload = {
            "generatedBy": "tools/catalog_bootstrap.py",
            "source": "bootstrap automatique — a corriger et versionner a la main (SPEC.md 6.5)",
            "stats": {k: v for k, v in stats.items()},
            "toys": catalog,
        }
        with open(args.out, "w", encoding="utf-8") as handle:
            json.dump(payload, handle, ensure_ascii=False, indent=2)
            handle.write("\n")
        print()
        print(f"catalog.json ecrit : {args.out}  ({len(catalog)} entrees)")

    return 0 if stats["ok"] else 1


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
