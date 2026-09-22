#!/usr/bin/env python3
"""Construit villains.json : le roster des vilains de Trap Team, depuis le wiki Fandom.

Ce fichier est le pendant de catalog.json pour les vilains : un referentiel versionne,
genere une fois puis corrigeable a la main. Il ne remplace pas villain.raw_id, qui reste
nomme par l'utilisateur (SPEC.md §7.2) — aucune source externe ne donne la correspondance
identifiant brut -> nom. Il fournit la LISTE de ce qui existe, pour que l'ecran Vilains
puisse afficher « ? » sur ce qui n'a pas encore ete capture.

Ce que l'outil NE fait PAS : telecharger la moindre image. Les visuels sont sous copyright
Activision, le depot n'embarque rien et n'a pas de scraper (SPEC.md §10.3). L'outil se
contente de rappeler sous quel nom deposer les fichiers a la main.

Le texte des resumes vient du wiki Fandom, sous licence CC BY-SA : il est tronque a quelques
phrases et chaque vilain garde le lien vers sa page, qui fait l'attribution.

    python3 tools/villains_bootstrap.py            # ecrit villains.json
    python3 tools/villains_bootstrap.py --dry-run
"""
import argparse
import json
import re
import sys
import unicodedata
import urllib.parse
import urllib.request

API = "https://skylanders.fandom.com/api.php"
UA = {"User-Agent": "SkylandersDashboard/0.1 (villain roster bootstrap)"}
SOURCE_PAGE = "Skylanders: Trap Team"

# Le pack et l'interface sont en francais (CLAUDE.md, « Conventions »).
ELEMENTS = {
    "Air": "Air", "Dark": "Ténèbres", "Earth": "Terre", "Fire": "Feu",
    "Kaos": "Kaos", "Light": "Lumière", "Life": "Vie", "Magic": "Magie",
    "Tech": "Tech", "Undead": "Mort-Vivant", "Water": "Eau",
}

# Le premier vilain de chaque section est le Doom Raider de l'element — sauf pour Magie, qui
# n'en a pas. Le wiki est explicite : « there is no Doom Raider for the Magic element », et
# precise que Kaos, lui, est bien identifie comme Doom Raider par les sources officielles.
# Sans cette exception, Bomb Shell serait promu boss par accident.
NO_DOOM_RAIDER = {"Magic"}

ENTRY = re.compile(r"\{\{CharElemListEntry\|([^|]*)\|([^|}\n]+)(.*)$")
SECTION = re.compile(r"^===\s*([^=]+?)\s*===")
SUFFIX = re.compile(r"suffix=(\([^)]*\))")


def api(params):
    url = API + "?" + urllib.parse.urlencode({**params, "format": "json",
                                              "formatversion": 2})
    with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=30) as r:
        return json.load(r)


def wikitext(page):
    """Le texte de la page, redirections suivies.

    « Tae Kwon Crow » n'est qu'une redirection vers « Tae Kwon Crow (character) » : sans
    redirects=1, l'outil ne recoltait que la ligne #REDIRECT et rendait une fiche vide.
    """
    data = api({"action": "parse", "page": page, "prop": "wikitext", "redirects": 1})
    if "error" in data:
        raise SystemExit(f"page introuvable : {page} ({data['error'].get('info')})")
    return data["parse"]["wikitext"], data["parse"]["title"]


def roster():
    """Les vilains listes par la page du jeu, dans l'ordre, section par section."""
    text, _ = wikitext(SOURCE_PAGE)
    start = text.find("==List of Villains==")
    if start < 0:
        raise SystemExit("section « List of Villains » absente : la page a change de forme")
    out, element = [], None
    for line in text[start:].split("\n"):
        section = SECTION.match(line)
        if section:
            element = section.group(1)
            if element not in ELEMENTS:
                element = None          # « Videos » et consorts
            continue
        entry = ENTRY.match(line)
        if not entry or element is None:
            continue
        name = entry.group(2).strip()
        suffix = SUFFIX.search(entry.group(3))
        first = not any(v["element"] == element for v in out)
        out.append({
            "name": name,
            "element": element,
            # Le titre de page differe du nom quand plusieurs articles se le disputent :
            # « Buzzer Beak (villain) » face au Skylander du meme nom.
            "wiki": (name + " " + suffix.group(1)) if suffix else name,
            "doomRaider": first and element not in NO_DOOM_RAIDER,
        })
    return out


CURLY = re.compile(r"\{\{[^{}]*\}\}")
REF = re.compile(r"<ref[^>]*>.*?</ref>|<ref[^>]*/>", re.S)
TAG = re.compile(r"<[^>]+>")
LINK_PIPED = re.compile(r"\[\[[^\]|]*\|([^\]]*)\]\]")
LINK_PLAIN = re.compile(r"\[\[([^\]]*)\]\]")
FILE_LINE = re.compile(r"^\s*\[\[(File|Image):.*$", re.I)


def summary(page, sentences=3, limit=420):
    """Les premieres phrases de l'article, en texte nu.

    Extrait court et attribue : le wiki est sous CC BY-SA, et chaque fiche porte le lien
    vers la page d'origine.
    """
    try:
        text, resolved = wikitext(page)
    except SystemExit as error:
        print(f"  {page} : {error}", file=sys.stderr)
        return None, page
    # Les modeles s'imbriquent (l'infobox en tete), d'ou le retrait repete.
    for _ in range(8):
        cleaned = CURLY.sub("", text)
        if cleaned == text:
            break
        text = cleaned
    text = REF.sub("", text)
    for line in text.split("\n"):
        line = line.strip()
        if not line or line.startswith(("=", "*", "|", "{", "[")) or FILE_LINE.match(line):
            continue
        line = LINK_PIPED.sub(r"\1", line)
        line = LINK_PLAIN.sub(r"\1", line)
        line = TAG.sub("", line).replace("'''", "").replace("''", "")
        line = re.sub(r"\s+", " ", line).strip()
        if len(line) < 60:
            continue
        parts = re.split(r"(?<=[.!?])\s+", line)
        out = " ".join(parts[:sentences]).strip()
        if len(out) > limit:
            out = out[:limit].rsplit(" ", 1)[0] + "…"
        return out, resolved
    return None, resolved


def slug(text):
    """Meme regle que ImageController.slug : minuscules alphanumeriques uniquement."""
    folded = "".join(c for c in unicodedata.normalize("NFD", text)
                     if not unicodedata.combining(c))
    return re.sub(r"[^a-z0-9]", "", folded.lower())


def report_missing(folder, catalogue):
    """Ce qu'il reste a deposer a la main dans le dossier d'images.

    Aucun telechargement : les visuels sont sous copyright Activision, le depot n'embarque
    rien et n'a pas de scraper (SPEC.md §10.3). L'outil dit seulement quoi fournir, et sous
    quel nom le serveur ira le chercher.
    """
    import os
    try:
        with open(catalogue, encoding="utf-8") as handle:
            villains = json.load(handle)["villains"]
    except OSError:
        raise SystemExit(f"{catalogue} introuvable — lancer l'outil sans --missing d'abord")
    present = {os.path.splitext(name)[0] for name in os.listdir(folder)} \
        if os.path.isdir(folder) else set()
    missing = [v for v in villains if v["image"] not in present]
    print(f"{len(villains) - len(missing)}/{len(villains)} vilains illustres")
    for villain in missing:
        print(f"  {villain['image']}.webp   <- {villain['name']} ({villain['element']})")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--out", default="villains.json")
    parser.add_argument("--no-summaries", action="store_true",
                        help="saute la collecte des resumes (une requete par vilain)")
    parser.add_argument("--missing", metavar="DOSSIER",
                        help="liste les images de vilains absentes de ce dossier, puis sort")
    args = parser.parse_args()

    if args.missing:
        report_missing(args.missing, args.out)
        return

    villains = roster()
    print(f"{len(villains)} vilains listes", file=sys.stderr)
    if not args.no_summaries:
        for index, villain in enumerate(villains, 1):
            villain["summary"], resolved = summary(villain["wiki"])
            # On retient la cible de la redirection : un lien direct vaut mieux qu'un rebond.
            villain["wiki"] = resolved
            print(f"  resume {index}/{len(villains)} {villain['name']}", file=sys.stderr)

    for villain in villains:
        villain["element"] = ELEMENTS[villain["element"]]
        villain["wiki"] = villain["wiki"].replace(" ", "_")
        # Rappel du nom de fichier attendu : le serveur cherche villain_<slug>.<ext> dans le
        # dossier d'images, slugifie depuis le NOM (ImageController.villainImage).
        villain["image"] = "villain_" + slug(villain["name"])

    document = {
        "generatedBy": "tools/villains_bootstrap.py",
        "source": f"https://skylanders.fandom.com/wiki/{urllib.parse.quote(SOURCE_PAGE)}",
        "license": "Textes extraits du wiki Skylanders (Fandom), CC BY-SA.",
        "villains": villains,
    }
    missing = [v["name"] for v in villains if not v.get("summary")]
    print(f"\ndoom raiders : {[v['name'] for v in villains if v['doomRaider']]}", file=sys.stderr)
    if missing:
        print(f"sans resume : {missing}", file=sys.stderr)
    if args.dry_run:
        json.dump(document, sys.stdout, ensure_ascii=False, indent=2)
        return
    with open(args.out, "w", encoding="utf-8") as handle:
        json.dump(document, handle, ensure_ascii=False, indent=2)
        handle.write("\n")
    print(f"{args.out} ecrit ({len(villains)} vilains)", file=sys.stderr)


if __name__ == "__main__":
    main()
