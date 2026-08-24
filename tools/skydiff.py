#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
skydiff.py — compare deux dumps .sky et localise les octets qui ont bouge.

Outil central de la phase 0 (SPEC.md 4.2 / 4.3) : on dumpe, on realise UNE
action ciblee en jeu, on re-dumpe, on diffe. Les octets qui changent donnent la
carte des offsets.

Strictement en lecture seule : les deux fichiers sont ouverts en "rb" et jamais
ecrits, renommes, deplaces ni supprimes (CLAUDE.md, invariant 1).

Usage :
    python3 tools/skydiff.py <avant.sky> <apres.sky> [options]

Options :
    --const <valeur>   Force la constante de derivation au lieu de la detecter.
                       Accepte une chaine, "hex:20436f7079..." ou "#3".
    --list-consts      Liste les variantes de constante candidates, puis quitte.
    --raw              Affiche aussi les octets chiffres des blocs modifies.
    --all-words        Affiche toutes les fenetres u16/u32 du bloc, pas
                       seulement celles qui couvrent un changement.
    --no-detect        N'affiche pas le detail du classement des constantes.
"""

from __future__ import annotations

import argparse
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import skycrypto as sky  # noqa: E402


def load(path: str, override: bytes | None) -> tuple[bytes, sky.DecryptedDump, object]:
    dump = sky.read_dump(path)
    constant, detection = sky.resolve_constant(dump, override)
    return dump, sky.decrypt_dump(dump, constant), detection


def print_file_header(label: str, path: str, dump: bytes, detection) -> None:
    print(f"{label}  {path}")
    print(f"        SHA-256 {sky.sha256(dump)}")
    written = sky.written_blocks(dump)
    print(f"        blocs de donnees ecrits : {len(written)}/45")
    if detection is not None and not detection.confident:
        state = "aucune donnee a mesurer" if not detection.has_data else "non concluant"
        print(f"        detection de la constante : {state}")


def check_same_toy(before: bytes, after: bytes) -> bool:
    """The key material is dump[0:32]; if it differs, the two files are not the
    same figurine and a byte-level diff of the save area is meaningless."""
    problems: list[str] = []
    if before[0:4] != after[0:4]:
        problems.append(
            f"UID different : {before[0:4].hex().upper()} -> {after[0:4].hex().upper()}"
        )
    if sky.u16le(before, 0x10) != sky.u16le(after, 0x10):
        problems.append(
            f"toy ID (hypothese 0x10) different : "
            f"{sky.u16le(before, 0x10)} -> {sky.u16le(after, 0x10)}"
        )
    if sky.u16le(before, 0x1C) != sky.u16le(after, 0x1C):
        problems.append(
            f"variant ID (hypothese 0x1C) different : "
            f"{sky.u16le(before, 0x1C)} -> {sky.u16le(after, 0x1C)}"
        )
    if before[0:32] != after[0:32] and not problems:
        problems.append("les 32 premiers octets different (materiel de cle)")

    if not problems:
        return True

    print()
    print("!" * 78)
    print("AVERTISSEMENT : les deux fichiers ne semblent pas etre la meme figurine.")
    for problem in problems:
        print(f"   - {problem}")
    print()
    print("   La cle de chaque bloc derive des 32 premiers octets du dump")
    print("   (SPEC.md 3.2). Si ces octets different, les deux fichiers sont")
    print("   dechiffres avec des cles differentes : comparer leur zone de")
    print("   sauvegarde octet a octet n'a aucun sens.")
    print("   Le protocole 4.3 demande deux dumps de la MEME figurine, pris")
    print("   avant et apres une seule action ciblee.")
    print("!" * 78)
    return False


def marker_row(before: bytes, after: bytes) -> str:
    """A '^^' under each byte column that changed, aligned with hex_row()."""
    cells = []
    for index in range(16):
        cells.append("^^" if before[index] != after[index] else "  ")
    groups = [" ".join(cells[i : i + 4]) for i in range(0, 16, 4)]
    return "  ".join(groups)


def print_word_views(
    before: bytes, after: bytes, base: int, changed: list[int], all_words: bool
) -> None:
    """Candidate uint16/uint32 LE readings of the aligned windows that cover a
    change. No claim is made about which one is the real field — that is exactly
    what the operator is here to decide."""
    print("   Interpretations LE :")
    printed = False
    for size, reader in ((2, sky.u16le), (4, sky.u32le)):
        for start in range(0, 16, size):
            window = range(start, start + size)
            if not all_words and not any(index in window for index in changed):
                continue
            old = reader(before, start)
            new = reader(after, start)
            if old == new and not all_words:
                continue
            delta = new - old
            sign = "+" if delta >= 0 else "-"
            print(
                f"     u{size * 8} @ 0x{base + start:04X} : "
                f"{old:>10d} -> {new:>10d}   "
                f"(0x{old:0{size * 2}X} -> 0x{new:0{size * 2}X})   "
                f"delta {sign}{abs(delta)}"
            )
            printed = True
    if not printed:
        print("     (aucune fenetre alignee ne change de valeur)")


def diff_block(
    block: int,
    dec_a: sky.DecryptedDump,
    dec_b: sky.DecryptedDump,
    show_raw: bool,
    all_words: bool,
) -> list[tuple[int, int, int]]:
    """Print the diff of one block; return the list of (offset, old, new)."""
    plain_a = dec_a.block_plain(block)
    plain_b = dec_b.block_plain(block)
    base = block * sky.BLOCK_SIZE

    changed = [i for i in range(16) if plain_a[i] != plain_b[i]]
    state_a, state_b = dec_a.state(block), dec_b.state(block)

    print()
    print("-" * 78)
    transition = f"{state_a} -> {state_b}" if state_a != state_b else state_a
    print(
        f"Bloc {block:02d}  offset 0x{base:04X}  "
        f"[{sky.block_kind(block)}/{transition}]  "
        f"{len(changed)} octet(s) modifie(s)"
    )
    if state_a == sky.STATE_BLANK and state_b == sky.STATE_DECRYPTED:
        print(
            "   PREMIERE ECRITURE : ce bloc etait vierge (zeros bruts) et porte\n"
            "   maintenant des donnees. Signal fort pour SPEC.md 3.5bis."
        )
    elif state_a == sky.STATE_DECRYPTED and state_b == sky.STATE_BLANK:
        print("   EFFACEMENT : ce bloc portait des donnees et est repasse a zero.")

    print(f"   avant    {sky.hex_row(plain_a)}  |{sky.ascii_row(plain_a)}|")
    print(f"   apres    {sky.hex_row(plain_b)}  |{sky.ascii_row(plain_b)}|")
    print(f"            {marker_row(plain_a, plain_b)}")

    if show_raw:
        raw_a, raw_b = dec_a.block_raw(block), dec_b.block_raw(block)
        print(f"   chif.av  {sky.hex_row(raw_a)}")
        print(f"   chif.ap  {sky.hex_row(raw_b)}")

    print("   Octets modifies :")
    for index in changed:
        old, new = plain_a[index], plain_b[index]
        print(
            f"     0x{base + index:04X}  0x{old:02X} -> 0x{new:02X}   "
            f"({old:3d} -> {new:3d}, delta {new - old:+d})"
        )

    print_word_views(plain_a, plain_b, base, changed, all_words)
    return [(base + i, plain_a[i], plain_b[i]) for i in changed]


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(
        description="Compare deux dumps .sky dechiffres et localise les offsets.",
        epilog="Lecture seule : ce script n'ecrit jamais dans les fichiers analyses.",
    )
    parser.add_argument("avant", nargs="?", help="dump .sky avant l'action")
    parser.add_argument("apres", nargs="?", help="dump .sky apres l'action")
    parser.add_argument("--const", help="constante de derivation forcee")
    parser.add_argument("--list-consts", action="store_true")
    parser.add_argument("--raw", action="store_true")
    parser.add_argument("--all-words", action="store_true")
    parser.add_argument("--no-detect", action="store_true")
    args = parser.parse_args(argv)

    if args.list_consts:
        print("Variantes de constante candidates (SPEC.md 3.2) :")
        for index, (label, const) in enumerate(sky.CONSTANT_CANDIDATES):
            print(f"  #{index:<2} {label:38s} {sky.describe_constant(const)}")
        return 0

    if not args.avant or not args.apres:
        parser.error("il faut deux chemins : <avant.sky> <apres.sky>")

    try:
        override = sky.parse_constant_arg(args.const) if args.const else None
    except sky.DumpError as exc:
        print(f"ERREUR : {exc}", file=sys.stderr)
        return 2

    # Report every unusable file, not just the first one.
    failures = []
    loaded = {}
    for label, path in (("avant", args.avant), ("apres", args.apres)):
        try:
            loaded[label] = load(path, override)
        except sky.DumpError as exc:
            failures.append(str(exc))
    if failures:
        for failure in failures:
            print(f"ERREUR : {failure}", file=sys.stderr)
        return 2

    dump_a, dec_a, det_a = loaded["avant"]
    dump_b, dec_b, det_b = loaded["apres"]

    print("=" * 78)
    print_file_header("AVANT ", args.avant, dump_a, det_a)
    print_file_header("APRES ", args.apres, dump_b, det_b)
    print("=" * 78)

    print()
    print("-- CONSTANTE DE DERIVATION " + "-" * 51)
    print(f"   avant : {sky.describe_constant(dec_a.constant)}")
    print(f"   apres : {sky.describe_constant(dec_b.constant)}")
    if dec_a.constant != dec_b.constant:
        print(
            "   ATTENTION : constantes detectees differentes sur les deux fichiers.\n"
            "   Forcer la meme valeur avec --const avant d'interpreter ce diff."
        )
    if not args.no_detect and det_a is not None:
        print("   Detection sur le fichier AVANT :")
        sky.print_constant_ranking(det_a, indent="   ")

    if dump_a == dump_b:
        print()
        print("Les deux fichiers sont identiques octet pour octet. Aucun diff.")
        print(
            "Rappel SPEC.md 8.2 : Cemu ecrit pendant la partie ; verifier que le\n"
            "second dump a bien ete pris apres la sortie du jeu."
        )
        return 0

    same_toy = check_same_toy(dump_a, dump_b)

    changed_blocks = [
        block
        for block in range(sky.BLOCK_COUNT)
        if dec_a.block_plain(block) != dec_b.block_plain(block)
    ]

    print()
    print("-- RESUME " + "-" * 68)
    print(f"   blocs modifies : {len(changed_blocks)}/{sky.BLOCK_COUNT}")
    if changed_blocks:
        print(f"   liste          : {', '.join(f'0x{b:02X}' for b in changed_blocks)}")

    all_changes: list[tuple[int, int, int]] = []
    for block in changed_blocks:
        all_changes.extend(
            diff_block(block, dec_a, dec_b, args.raw, args.all_words)
        )

    print()
    print("=" * 78)
    print(f"TOTAL : {len(all_changes)} octet(s) modifie(s) sur {len(changed_blocks)} bloc(s)")
    if all_changes:
        offsets = ", ".join(f"0x{offset:04X}" for offset, _, _ in all_changes)
        print(f"Offsets : {offsets}")
    print("=" * 78)
    print()
    print("Rappel (CLAUDE.md invariant 2) : un offset localise ici n'est pas encore")
    print("un fait. Le consigner dans FORMAT.md avec son niveau de preuve, et ne le")
    print("passer VERIFIE qu'apres reproduction du diff sur >= 2 figurines.")

    return 0 if same_toy else 1


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
