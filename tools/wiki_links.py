#!/usr/bin/env python3
"""Resout le titre de la page Skylanders Fandom de chaque entree de catalog.json.

Pourquoi un outil hors ligne plutot qu'une regle appliquee a l'execution : les noms du
catalogue viennent des noms de fichiers du pack, qui sont francises, parfois colles
(« Hoodsickle » pour « Hood Sickle »), et porteurs de coquilles connues (« Drill Seargeant »,
« Cobra Candabra », « Bushwack »). Une regle qui devine produirait des liens morts sans le
dire. Ici chaque titre ecrit dans catalog.json a ete **verifie** contre le wiki ; une entree
sans titre resolu n'aura simplement pas de bouton, ce qui est un etat honnete.

Regle metier, confirmee sur le wiki :
  - une variante « repeinte » (Series 2, LightCore, Turbo, Fizzy Frenzy…) n'a pas de page :
    elle pointe vers celle du personnage de base ;
  - Dark, Legendary, Elite (« Eon's Elite » dans le pack) et Nitro ont bien la leur.

    python3 tools/wiki_links.py            # ecrit catalog.json
    python3 tools/wiki_links.py --dry-run  # montre le resultat sans rien ecrire
"""
import argparse
import collections
import json
import re
import sys
import unicodedata
import urllib.parse
import urllib.request

API = "https://skylanders.fandom.com/api.php"
UA = {"User-Agent": "SkylandersDashboard/0.1 (catalog link check)"}

# Marqueurs de variante qui meritent leur propre page, et leur nom sur le wiki. Le pack est
# francise, d'ou « Sombre » et « Legendaire » a cote des marqueurs anglais. Les clefs sont
# indexees par fold() : c'est sous cette forme qu'elles seront cherchees.
OWN_PAGE = {"dark": "Dark", "sombre": "Dark",
            "legendary": "Legendary", "legendaire": "Legendary",
            "nitro": "Nitro",
            "eon's elite": "Elite", "elite": "Elite"}
# Les Swap Force sont dumpes en deux moities ; le wiki ne connait que le personnage.
HALF = re.compile(r"\s*\((?:Top|Bottom)\)\s*$", re.I)


def fold(text):
    """Casse, accents et separateurs effaces : « Déjà Vu » et « Deja-Vu » se rejoignent."""
    stripped = "".join(c for c in unicodedata.normalize("NFD", text)
                       if not unicodedata.combining(c))
    return re.sub(r"[^a-z0-9]", "", stripped.lower())


def distance(a, b):
    """Levenshtein, pour absorber les coquilles du pack (Bushwack / Bushwhack)."""
    if a == b:
        return 0
    previous = list(range(len(b) + 1))
    for i, ca in enumerate(a, 1):
        current = [i]
        for j, cb in enumerate(b, 1):
            current.append(min(previous[j] + 1, current[j - 1] + 1,
                               previous[j - 1] + (ca != cb)))
        previous = current
    return previous[-1]


def spellings(name):
    """Ecritures plausibles d'un meme libelle, de la plus fidele a la plus liberale."""
    n = HALF.sub("", name).strip()
    out = [n, n.replace("_", " "), n.replace("_", ". "), n.replace("_", "-"),
           n.replace("-", " "), n.replace("_", " ").replace("-", " ")]
    out += [x.replace(" ", "-") for x in list(out)]
    out += [re.sub(r"(?<=[a-z])(?=[A-Z])", " ", n)]
    # Le pack colle en un mot ce que le wiki separe : Kingpen/King Pen, Airstrike/Air Strike,
    # Neocortex/Neo Cortex. On ne sait pas ou couper, alors on propose toutes les coupures et
    # c'est le wiki qui tranche. Chaque morceau est capitalise : MediaWiki ne releve que la
    # premiere lettre d'un titre, « Wild storm » et « Wild Storm » sont deux pages.
    # « Dr_Neocortex » demande les deux a la fois : le point du pack et la coupure du wiki.
    for spaced in {n.replace("_", " ").replace("-", " "), n, n.replace("_", ". ")}:
        words = [w for w in re.split(r"[\s_-]+", spaced) if w]
        for index, word in enumerate(words):
            if len(word) < 6:
                continue
            for cut in range(3, len(word) - 2):
                for glue in (" ", "-"):
                    pieces = list(words)
                    pieces[index:index + 1] = [word[:cut].capitalize(),
                                               word[cut:].capitalize()]
                    out.append(glue.join(pieces) if len(pieces) == 2
                               else " ".join(pieces[:index] + [glue.join(pieces[index:index + 2])]
                                             + pieces[index + 2:]))
    collapsed = re.sub(r"[\s_-]+", "", n)
    out += [collapsed, collapsed.capitalize()]
    seen, ordered = set(), []
    for candidate in (re.sub(r"\s+", " ", x).strip() for x in out):
        if candidate and candidate not in seen:
            seen.add(candidate)
            ordered.append(candidate)
    return ordered


def marker_of(full, base):
    """Ce qui distingue une variante de sa base, ou None si les noms sont sans rapport.

    « Eon's Elite Terrafin » sur « Terrafin » donne « Eon's Elite » ; « Airstrike-Eggbomber »
    sur « Airstrike » donne « Eggbomber ». La comparaison passe par fold() pour survivre aux
    separateurs et aux accents, mais ce qui est renvoye est decoupe dans le nom d'origine.
    """
    folded_full, folded_base = fold(full), fold(base)
    if folded_full == folded_base or folded_base not in folded_full:
        return None
    marker = (full[: len(full) - len(base)] if folded_full.endswith(folded_base)
              else full[len(base):]).strip(" -_")
    return marker or None


def api(params):
    url = API + "?" + urllib.parse.urlencode({**params, "format": "json"})
    with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=30) as r:
        return json.load(r)


def existing(titles):
    """Les titres que le wiki sert vraiment, redirections suivies."""
    found, titles = {}, sorted({t for t in titles if t})
    for i in range(0, len(titles), 50):
        chunk = titles[i:i + 50]
        query = api({"action": "query", "redirects": 1, "titles": "|".join(chunk)})["query"]
        normalized = {x["from"]: x["to"] for x in query.get("normalized", [])}
        redirects = {x["from"]: x["to"] for x in query.get("redirects", [])}
        live = {p["title"] for p in query["pages"].values() if "missing" not in p}
        for title in chunk:
            step = normalized.get(title, title)
            found[title] = redirects.get(step, step) in live
        print(f"  verification {min(i + 50, len(titles))}/{len(titles)}", file=sys.stderr)
    return found


# Indexe une fois pour toutes sous la forme normalisee, sinon « Eon's Elite » ne se
# reconnaitrait jamais : fold() en fait « eonselite », apostrophe et espace compris.
OWN_PAGE_BY_FOLD = {fold(k): v for k, v in OWN_PAGE.items()}

SUBPAGE = re.compile(r"/.*$")
DISAMBIG = re.compile(r"\s*\([^)]*\)\s*$")


def from_hit(title):
    """Titres a tenter depuis un resultat de recherche.

    La recherche remonte volontiers « Boom Bloom/Gallery » ou « Dark King Pen » sans jamais
    servir la page principale : on remonte donc a la page parente et on retire un marqueur de
    variante eventuel.
    """
    out = [title, SUBPAGE.sub("", title)]
    for candidate in list(out):
        head = candidate.split(" ", 1)
        if len(head) == 2 and fold(head[0]) in OWN_PAGE_BY_FOLD:
            out.append(head[1])
    return list(dict.fromkeys(out))


def search(name):
    """Dernier recours : la recherche du wiki, acceptee seulement si le nom colle."""
    try:
        results = api({"action": "query", "list": "search", "srsearch": name,
                       "srlimit": 8})["query"]["search"]
    except Exception as error:                                    # noqa: BLE001
        print(f"  recherche impossible pour {name!r} : {error}", file=sys.stderr)
        return None
    target = fold(name)
    for hit in results:
        for title in from_hit(hit["title"]):
            # « Grave Clobber (character) » EST la page : la parenthese ne compte que pour la
            # comparaison, pas pour le lien.
            gap = distance(fold(DISAMBIG.sub("", title)), target)
            # Identique une fois normalise, ou a une ou deux lettres pres sur un nom assez
            # long pour que la coincidence soit exclue.
            if gap == 0 or (gap <= 2 and len(target) >= 8):
                return title
    return None


def resolve(catalog):
    toys = catalog["toys"]
    base = {}
    for toy in toys:
        current = base.get(toy["toyId"])
        if current is None or toy["variantId"] < current["variantId"]:
            base[toy["toyId"]] = toy

    # 1. Les bases d'abord : tout le reste s'appuie sur leur titre.
    base_titles = {}
    probes = {t["toyId"]: spellings(t["nameEn"]) for t in base.values()}
    found = existing([s for candidates in probes.values() for s in candidates])
    for toy_id, candidates in probes.items():
        hit = next((c for c in candidates if found.get(c)), None)
        if hit:
            base_titles[toy_id] = hit
    missing = [t for t in base.values() if t["toyId"] not in base_titles]
    print(f"  recherche floue sur {len(missing)} bases non trouvees", file=sys.stderr)
    for toy in missing:
        hit = search(HALF.sub("", toy["nameEn"]).strip())
        if hit:
            base_titles[toy["toyId"]] = hit

    # 2. Les variantes, construites sur le titre corrige de leur base.
    probes = {}
    for toy in toys:
        key = (toy["toyId"], toy["variantId"])
        full = HALF.sub("", toy["nameEn"]).strip()
        base_toy = base[toy["toyId"]]
        base_title = base_titles.get(toy["toyId"])
        if toy["variantId"] == base_toy["variantId"]:
            probes[key] = [base_title] if base_title else spellings(full)
            continue
        marker = marker_of(full, HALF.sub("", base_toy["nameEn"]).strip())
        own = OWN_PAGE_BY_FOLD.get(fold(marker)) if marker else None
        candidates = []
        if own:
            candidates += [own + " " + s for s in ([base_title] if base_title else [])
                           + spellings(base_toy["nameEn"])]
        elif marker is not None and base_title:
            candidates.append(base_title)          # repeinte : la page est celle de la base
        candidates += spellings(full)
        if base_title:
            candidates.append(base_title)
        probes[key] = list(dict.fromkeys(candidates))

    found = existing([c for candidates in probes.values() for c in candidates])
    resolved = {}
    for toy in toys:
        key = (toy["toyId"], toy["variantId"])
        hit = next((c for c in probes[key] if found.get(c)), None)
        if hit:
            resolved[key] = hit
    return resolved


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--catalog", default="catalog.json")
    args = parser.parse_args()

    with open(args.catalog, encoding="utf-8") as handle:
        catalog = json.load(handle)
    resolved = resolve(catalog)

    misses, unlinked = collections.Counter(), []
    for toy in catalog["toys"]:
        title = resolved.get((toy["toyId"], toy["variantId"]))
        if title:
            toy["wiki"] = title.replace(" ", "_")
        else:
            toy.pop("wiki", None)
            misses[toy["category"]] += 1
            unlinked.append(toy)

    total = len(catalog["toys"])
    print(f"\n{len(resolved)}/{total} entrees liees ({100 * len(resolved) / total:.1f} %)")
    print("sans page connue, par categorie :", dict(misses))
    # Pieges, coffres et cristaux n'ont pas de page individuelle sur le wiki : normal.
    # Tout le reste merite un oeil, c'est la que se cachent les coquilles non absorbees.
    surprising = [t for t in unlinked
                  if t["category"] not in ("TRAP", "CHEST", "CREATION_CRYSTAL")]
    if surprising:
        print(f"\n{len(surprising)} entree(s) sans lien hors pieges/coffres/cristaux :")
        for toy in surprising:
            print(f"  {toy['category']:10s} {toy['toyId']:5d}/{toy['variantId']:<6d} "
                  f"{toy['nameEn']!r}")

    if args.dry_run:
        print("\n--dry-run : catalog.json inchange")
        return
    with open(args.catalog, "w", encoding="utf-8") as handle:
        json.dump(catalog, handle, ensure_ascii=False, indent=2)
        handle.write("\n")
    print(f"\n{args.catalog} mis a jour")


if __name__ == "__main__":
    main()
