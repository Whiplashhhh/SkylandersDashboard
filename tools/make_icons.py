#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
make_icons.py — genere les icones de la PWA.

Dessine une roue des huit elements sur fond sombre, dans les tailles attendues
par un manifeste web. Les couleurs sont celles de l'enum Element cote serveur,
donc l'icone reste coherente avec les pastilles de l'interface.

Encodeur PNG ecrit ici en stdlib pure (zlib + struct + binascii) : ajouter
Pillow pour trois aplats de couleur serait disproportionne, et le projet tient a
n'avoir aucune dependance evitable.

Usage :
    python3 tools/make_icons.py                       # ecrit dans server/frontend/public
    python3 tools/make_icons.py --out <dossier>
"""

from __future__ import annotations

import argparse
import binascii
import math
import os
import struct
import sys
import zlib

# Memes valeurs que classification/Element.java, dans l'ordre de la roue.
WHEEL = ["#e2603a", "#2f7fd1", "#4fa65b", "#9b5fc0",
         "#d98f2b", "#a9743a", "#7ec8e3", "#7a8b52"]
BACKGROUND = "#14161c"
HUB = "#1d2029"

SIZES = (192, 512)
SUPERSAMPLE = 4  # rend 4x puis moyenne : anticrenelage sans bibliotheque graphique


def rgb(value: str) -> tuple[int, int, int]:
    value = value.lstrip("#")
    return tuple(int(value[i:i + 2], 16) for i in (0, 2, 4))


def wheel_pixel(x: float, y: float, size: float) -> tuple[int, int, int]:
    """Couleur d'un point, en coordonnees pixel."""
    cx = cy = size / 2
    dx, dy = x - cx, y - cy
    radius = math.hypot(dx, dy)
    outer = size * 0.44
    inner = size * 0.17
    if radius > outer:
        return rgb(BACKGROUND)
    if radius < inner:
        return rgb(HUB)
    # atan2 ramene a [0, 2pi) en partant du haut, sens horaire.
    angle = (math.atan2(dy, dx) + math.pi / 2) % (2 * math.pi)
    index = int(angle / (2 * math.pi) * len(WHEEL)) % len(WHEEL)
    return rgb(WHEEL[index])


def render(size: int) -> bytes:
    """Rend l'icone en RGB brut, une ligne apres l'autre."""
    big = size * SUPERSAMPLE
    rows = bytearray()
    weight = SUPERSAMPLE * SUPERSAMPLE
    for y in range(size):
        rows.append(0)  # octet de filtre PNG : aucun
        for x in range(size):
            r = g = b = 0
            for sy in range(SUPERSAMPLE):
                for sx in range(SUPERSAMPLE):
                    px = (x * SUPERSAMPLE + sx + 0.5) / SUPERSAMPLE
                    py = (y * SUPERSAMPLE + sy + 0.5) / SUPERSAMPLE
                    cr, cg, cb = wheel_pixel(px, py, size)
                    r += cr
                    g += cg
                    b += cb
            rows.extend((r // weight, g // weight, b // weight))
    _ = big
    return bytes(rows)


def chunk(kind: bytes, payload: bytes) -> bytes:
    return (struct.pack(">I", len(payload)) + kind + payload
            + struct.pack(">I", binascii.crc32(kind + payload) & 0xFFFFFFFF))


def write_png(path: str, size: int, raw: bytes) -> None:
    header = struct.pack(">IIBBBBB", size, size, 8, 2, 0, 0, 0)  # 8 bits, RGB
    data = (b"\x89PNG\r\n\x1a\n"
            + chunk(b"IHDR", header)
            + chunk(b"IDAT", zlib.compress(raw, 9))
            + chunk(b"IEND", b""))
    with open(path, "wb") as handle:
        handle.write(data)


def main(argv: list[str]) -> int:
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    parser = argparse.ArgumentParser(description="Genere les icones de la PWA.")
    parser.add_argument("--out", default=os.path.join(root, "server", "frontend", "public"))
    args = parser.parse_args(argv)
    os.makedirs(args.out, exist_ok=True)

    for size in SIZES:
        path = os.path.join(args.out, f"icon-{size}.png")
        write_png(path, size, render(size))
        print(f"  {path}  ({os.path.getsize(path)} octets)")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
