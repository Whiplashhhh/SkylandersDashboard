#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
skydump.py — affiche le contenu dechiffre complet d'un dump .sky, bloc par bloc.

Outil de phase 0 (SPEC.md 4.2). Strictement en lecture seule : le fichier est
ouvert en "rb" et jamais ecrit, renomme, deplace ni supprime (CLAUDE.md,
invariant 1).

Aucun offset n'est interprete comme acquis. Les seules lectures nommees sont
celles de SPEC.md 3.3/3.4, affichees dans une section explicitement marquee
HYPOTHESE et separee du dump brut.

Usage :
    python3 tools/skydump.py <fichier.sky> [options]

Options :
    --const <valeur>   Force la constante de derivation au lieu de la detecter.
                       Accepte une chaine, "hex:20436f7079..." ou "#3" (cf.
                       --list-consts).
    --list-consts      Liste les variantes de constante candidates, puis quitte.
    --words            Ajoute l'interpretation uint16/uint32 LE de chaque bloc.
    --raw              Affiche aussi les octets chiffres a cote du clair.
    --strings          Recherche les chaines ASCII et UTF-16LE dans le clair.
    --blank            Affiche aussi les blocs vierges (masques par defaut).
    --no-detect        N'affiche pas le detail du classement des constantes.
"""

from __future__ import annotations

import argparse
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import skycrypto as sky  # noqa: E402


def print_header(path: str, dump: bytes) -> None:
    print("=" * 78)
    print(f"FICHIER   {path}")
    print(f"TAILLE    {len(dump)} octets")
    print(f"SHA-256   {sky.sha256(dump)}")
    print(f"BACKEND   AES : {sky.BACKEND}")
    print("=" * 78)


def print_identity(dump: bytes) -> None:
    """Named readings from SPEC.md 3.3/3.4 — hypotheses, not established facts."""
    print()
    print("-- HYPOTHESES D'IDENTITE (SPEC.md 3.3/3.4 — NON VERIFIE) " + "-" * 21)
    print("   A confirmer par diff avant tout usage en production (CLAUDE.md inv. 2).")
    print(f"   0x00  UID (4 o)          : {dump[0:4].hex().upper()}")
    print(f"   0x04  donnees fabricant  : {dump[4:16].hex().upper()}")
    print(
        f"   0x10  toy ID    u16 LE    : {sky.u16le(dump, 0x10):5d}"
        f"   (0x{sky.u16le(dump, 0x10):04X})"
    )
    print(
        f"   0x1C  variant ID u16 LE   : {sky.u16le(dump, 0x1C):5d}"
        f"   (0x{sky.u16le(dump, 0x1C):04X})   [confiance faible]"
    )
    print(f"   0x1E  2 derniers octets   : {dump[0x1E:0x20].hex().upper()}")


def print_summary(dump: bytes) -> None:
    written = sky.written_blocks(dump)
    total_enc = [b for b in range(sky.BLOCK_COUNT) if sky.is_encrypted(b)]
    print()
    print("-- ETAT DE LA ZONE DE DONNEES " + "-" * 48)
    print(f"   blocs de donnees      : {len(total_enc)}")
    print(f"   dont ecrits (non nuls): {len(written)}")
    print(f"   dont vierges (a zero) : {len(total_enc) - len(written)}")
    if written:
        print(f"   blocs ecrits          : {', '.join(f'0x{b:02X}' for b in written)}")
    else:
        print(
            "   -> Zone de sauvegarde entierement vierge dans le fichier brut.\n"
            "      Candidat fort pour la signature « jamais joue » (SPEC.md 3.5bis).\n"
            "      A confirmer par le protocole 4.3bis avant d'en faire un critere."
        )


def print_blocks(
    dec: sky.DecryptedDump, show_raw: bool, show_words: bool, show_blank: bool
) -> None:
    print()
    print("-- CONTENU BLOC PAR BLOC " + "-" * 53)
    hidden = 0
    for block in range(sky.BLOCK_COUNT):
        state = dec.state(block)
        data = dec.block_plain(block)
        if state == sky.STATE_BLANK and not show_blank:
            hidden += 1
            continue
        offset = block * sky.BLOCK_SIZE
        tag = f"{sky.block_kind(block)}/{state}"
        print()
        print(f"Bloc {block:02d}  offset 0x{offset:04X}  [{tag}]")
        print(f"   clair    {sky.hex_row(data)}  |{sky.ascii_row(data)}|")
        if show_raw and state == sky.STATE_DECRYPTED:
            raw = dec.block_raw(block)
            print(f"   chiffre  {sky.hex_row(raw)}  |{sky.ascii_row(raw)}|")
        if show_words:
            u16s = " ".join(f"{sky.u16le(data, i):5d}" for i in range(0, 16, 2))
            u32s = " ".join(f"{sky.u32le(data, i):10d}" for i in range(0, 16, 4))
            print(f"   u16 LE   {u16s}")
            print(f"   u32 LE   {u32s}")
    if hidden:
        print()
        print(f"   ({hidden} blocs vierges masques — utiliser --blank pour les voir)")


def find_strings(data: bytes, minimum: int = 3) -> None:
    print()
    print("-- CHAINES CANDIDATES " + "-" * 56)
    found = False

    current: list[int] = []
    start = 0
    for index, byte in enumerate(data + b"\x00"):
        if 0x20 <= byte <= 0x7E:
            if not current:
                start = index
            current.append(byte)
        else:
            if len(current) >= minimum:
                print(f"   ASCII      0x{start:04X}  {bytes(current).decode('ascii')!r}")
                found = True
            current = []

    for parity in (0, 1):
        chars: list[str] = []
        start = 0
        for index in range(parity, len(data) - 1, 2):
            low, high = data[index], data[index + 1]
            if high == 0 and 0x20 <= low <= 0x7E:
                if not chars:
                    start = index
                chars.append(chr(low))
            else:
                if len(chars) >= minimum:
                    print(f"   UTF-16LE   0x{start:04X}  {''.join(chars)!r}")
                    found = True
                chars = []
        if len(chars) >= minimum:
            print(f"   UTF-16LE   0x{start:04X}  {''.join(chars)!r}")
            found = True

    if not found:
        print("   (aucune)")


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(
        description="Affiche le contenu dechiffre d'un dump .sky, bloc par bloc.",
        epilog="Lecture seule : ce script n'ecrit jamais dans le fichier analyse.",
    )
    parser.add_argument("fichier", nargs="?", help="chemin du fichier .sky")
    parser.add_argument("--const", help="constante de derivation forcee")
    parser.add_argument("--list-consts", action="store_true")
    parser.add_argument("--words", action="store_true")
    parser.add_argument("--raw", action="store_true")
    parser.add_argument("--strings", action="store_true")
    parser.add_argument("--blank", action="store_true")
    parser.add_argument("--no-detect", action="store_true")
    args = parser.parse_args(argv)

    if args.list_consts:
        print("Variantes de constante candidates (SPEC.md 3.2) :")
        for index, (label, const) in enumerate(sky.CONSTANT_CANDIDATES):
            print(f"  #{index:<2} {label:38s} {sky.describe_constant(const)}")
        return 0

    if not args.fichier:
        parser.error("chemin du fichier .sky manquant")

    try:
        dump = sky.read_dump(args.fichier)
        override = sky.parse_constant_arg(args.const) if args.const else None
    except sky.DumpError as exc:
        print(f"ERREUR : {exc}", file=sys.stderr)
        return 2

    constant, detection = sky.resolve_constant(dump, override)

    print_header(args.fichier, dump)
    print()
    print("-- CONSTANTE DE DERIVATION " + "-" * 51)
    print(f"   {sky.describe_constant(constant)}")
    if detection is None:
        print("   (forcee par --const, aucune detection effectuee)")
    elif not args.no_detect:
        sky.print_constant_ranking(detection, indent="   ")

    print_identity(dump)
    print_summary(dump)

    dec = sky.decrypt_dump(dump, constant)
    print_blocks(dec, show_raw=args.raw, show_words=args.words, show_blank=args.blank)

    if args.strings:
        find_strings(dec.plain)

    print()
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
