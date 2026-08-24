#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Shared low-level helpers for the phase-0 reverse-engineering tools.

Scope: read-only. Nothing in this module (or in skydump.py / skydiff.py) ever
opens a .sky file for writing, renames it, moves it or deletes it. Files are
opened with mode "rb" exclusively. See CLAUDE.md invariant 1.

What lives here:
  * a dependency-free AES-128-ECB decryption routine (pure stdlib), with an
    optional fast path through `cryptography` when that module is importable;
  * the per-block key derivation described in SPEC.md 3.2;
  * the list of plausible copyright-constant variants and an automatic
    detection routine, because SPEC.md 3.2 explicitly leaves the exact form of
    that constant undetermined.

No field offset is hard-coded here. Locating offsets is the *output* of phase 0,
not an input to it (CLAUDE.md invariant 2).
"""

from __future__ import annotations

import hashlib
import math
from typing import Iterable, NamedTuple

# --------------------------------------------------------------------------
# Physical layout of a Mifare Classic 1K dump (SPEC.md 3.1)
# --------------------------------------------------------------------------

DUMP_SIZE = 1024
BLOCK_SIZE = 16
BLOCK_COUNT = DUMP_SIZE // BLOCK_SIZE  # 64

KIND_UID = "CLAIR/UID"
KIND_TOY = "CLAIR/JOUET"
KIND_TRAILER = "CLAIR/TRAILER"
KIND_ENCRYPTED = "CHIFFRE"


def block_kind(block: int) -> str:
    """Return which of the four categories of SPEC.md 3.2 a block falls into."""
    if block == 0:
        return KIND_UID
    if block == 1:
        return KIND_TOY
    if block % 4 == 3:
        return KIND_TRAILER
    return KIND_ENCRYPTED


def is_encrypted(block: int) -> bool:
    return block_kind(block) == KIND_ENCRYPTED


# Per-block state after processing. Empirical finding of 2026-08-17: in this
# pack, a data block that has never been written by the game is stored as 16
# literal zero bytes, *not* as an encryption of zeros. Running AES over such a
# block yields pseudo-random noise, so blank blocks must be recognised and left
# alone — otherwise every unplayed figurine looks like 720 bytes of garbage.
STATE_PLAIN = "clair"
STATE_DECRYPTED = "dechiffre"
STATE_BLANK = "vierge"


def is_blank_block(dump: bytes, block: int) -> bool:
    """True when an encrypted-category block is 16 zero bytes in the raw file."""
    start = block * BLOCK_SIZE
    return dump[start : start + BLOCK_SIZE] == b"\x00" * BLOCK_SIZE


# --------------------------------------------------------------------------
# GF(2^8) tables, built at import time so no 256-entry literal can be mistyped
# --------------------------------------------------------------------------


def _xtime(a: int) -> int:
    a <<= 1
    if a & 0x100:
        a = (a ^ 0x1B) & 0xFF
    return a


def _gmul(a: int, b: int) -> int:
    p = 0
    for _ in range(8):
        if b & 1:
            p ^= a
        a = _xtime(a)
        b >>= 1
    return p


_EXP = [0] * 512
_LOG = [0] * 256
_x = 1
for _i in range(255):
    _EXP[_i] = _x
    _LOG[_x] = _i
    _x = _gmul(_x, 3)  # 3 is a generator of GF(2^8)*
for _i in range(255, 510):
    _EXP[_i] = _EXP[_i - 255]


def _rotl8(value: int, shift: int) -> int:
    return ((value << shift) | (value >> (8 - shift))) & 0xFF


_SBOX = [0] * 256
for _i in range(256):
    _inv = 0 if _i == 0 else _EXP[255 - _LOG[_i]]
    _SBOX[_i] = (
        _inv
        ^ _rotl8(_inv, 1)
        ^ _rotl8(_inv, 2)
        ^ _rotl8(_inv, 3)
        ^ _rotl8(_inv, 4)
        ^ 0x63
    )

_INV_SBOX = [0] * 256
for _i, _v in enumerate(_SBOX):
    _INV_SBOX[_v] = _i

# Multiplication tables for InvMixColumns.
_MUL9 = [_gmul(_i, 9) for _i in range(256)]
_MUL11 = [_gmul(_i, 11) for _i in range(256)]
_MUL13 = [_gmul(_i, 13) for _i in range(256)]
_MUL14 = [_gmul(_i, 14) for _i in range(256)]


# --------------------------------------------------------------------------
# AES-128 (decryption only — these tools never encrypt anything)
# --------------------------------------------------------------------------


def _expand_key(key: bytes) -> list[bytes]:
    """AES-128 key schedule: 11 round keys of 16 bytes."""
    if len(key) != 16:
        raise ValueError("AES-128 requires a 16-byte key")
    words = [list(key[i * 4 : i * 4 + 4]) for i in range(4)]
    rcon = 1
    for i in range(4, 44):
        temp = list(words[i - 1])
        if i % 4 == 0:
            temp = temp[1:] + temp[:1]
            temp = [_SBOX[b] for b in temp]
            temp[0] ^= rcon
            rcon = _xtime(rcon)
        words.append([words[i - 4][j] ^ temp[j] for j in range(4)])
    return [
        bytes(b for word in words[r * 4 : r * 4 + 4] for b in word) for r in range(11)
    ]


def _add_round_key(state: list[int], round_key: bytes) -> None:
    for i in range(16):
        state[i] ^= round_key[i]


def _inv_shift_rows(state: list[int]) -> list[int]:
    # State index is r + 4*c. Row r is rotated right by r.
    out = [0] * 16
    for r in range(4):
        for c in range(4):
            out[r + 4 * c] = state[r + 4 * ((c - r) % 4)]
    return out


def _inv_mix_columns(state: list[int]) -> None:
    for c in range(4):
        i = 4 * c
        a0, a1, a2, a3 = state[i], state[i + 1], state[i + 2], state[i + 3]
        state[i] = _MUL14[a0] ^ _MUL11[a1] ^ _MUL13[a2] ^ _MUL9[a3]
        state[i + 1] = _MUL9[a0] ^ _MUL14[a1] ^ _MUL11[a2] ^ _MUL13[a3]
        state[i + 2] = _MUL13[a0] ^ _MUL9[a1] ^ _MUL14[a2] ^ _MUL11[a3]
        state[i + 3] = _MUL11[a0] ^ _MUL13[a1] ^ _MUL9[a2] ^ _MUL14[a3]


def _aes128_decrypt_block_py(block: bytes, key: bytes) -> bytes:
    round_keys = _expand_key(key)
    state = list(block)
    _add_round_key(state, round_keys[10])
    for rnd in range(9, 0, -1):
        state = _inv_shift_rows(state)
        state = [_INV_SBOX[b] for b in state]
        _add_round_key(state, round_keys[rnd])
        _inv_mix_columns(state)
    state = _inv_shift_rows(state)
    state = [_INV_SBOX[b] for b in state]
    _add_round_key(state, round_keys[0])
    return bytes(state)


# Optional fast path. Purely an accelerator: the pure-Python implementation
# above stays authoritative and is cross-checked by self_test().
try:  # pragma: no cover - depends on the local environment
    from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes

    def _aes128_ecb_decrypt(data: bytes, key: bytes) -> bytes:
        decryptor = Cipher(algorithms.AES(key), modes.ECB()).decryptor()
        return decryptor.update(data) + decryptor.finalize()

    BACKEND = "cryptography"
except Exception:  # pragma: no cover

    def _aes128_ecb_decrypt(data: bytes, key: bytes) -> bytes:
        return b"".join(
            _aes128_decrypt_block_py(data[i : i + 16], key)
            for i in range(0, len(data), 16)
        )

    BACKEND = "python-pur (stdlib)"


def aes128_ecb_decrypt(data: bytes, key: bytes) -> bytes:
    if len(data) % 16:
        raise ValueError("AES-ECB requires a length multiple of 16")
    return _aes128_ecb_decrypt(data, key)


# --------------------------------------------------------------------------
# Key derivation (SPEC.md 3.2)
#     key(N) = MD5( dump[0x00..0x20] || byte(N) || CONSTANT )
# --------------------------------------------------------------------------

_BASE = "Copyright (C) 2010 Activision. All Rights Reserved."

# The exact form of the constant is left undetermined by SPEC.md 3.2 (trailing
# space? leading space? NUL terminator?). Rather than picking one and hoping,
# the tools try them all and rank the results — see detect_constant().
CONSTANT_CANDIDATES: list[tuple[str, bytes]] = [
    ("espace avant + espace apres", (" " + _BASE + " ").encode("ascii")),
    ("brut, sans espace", _BASE.encode("ascii")),
    ("espace avant seulement", (" " + _BASE).encode("ascii")),
    ("espace apres seulement", (_BASE + " ").encode("ascii")),
    ("brut + NUL", (_BASE + "\x00").encode("ascii")),
    ("espace avant + espace apres + NUL", (" " + _BASE + " \x00").encode("ascii")),
    ("espace apres + NUL", (_BASE + " \x00").encode("ascii")),
    ("NUL avant + espace apres", ("\x00" + _BASE + " ").encode("ascii")),
    ("sans le mot Copyright", "(C) 2010 Activision. All Rights Reserved.".encode("ascii")),
    (
        "sans Copyright, espaces autour",
        " (C) 2010 Activision. All Rights Reserved. ".encode("ascii"),
    ),
    ("annee 2011", _BASE.replace("2010", "2011").encode("ascii")),
    (
        "annee 2011, espaces autour",
        (" " + _BASE.replace("2010", "2011") + " ").encode("ascii"),
    ),
]


def derive_block_key(dump: bytes, block: int, constant: bytes) -> bytes:
    """MD5( first 32 bytes of the dump || block number || constant )."""
    return hashlib.md5(dump[0:32] + bytes([block]) + constant).digest()


class DecryptedDump(NamedTuple):
    raw: bytes
    plain: bytes  # 1024 bytes; offsets line up 1:1 with the raw file
    constant: bytes
    states: tuple[str, ...]  # per-block STATE_* marker, 64 entries

    def state(self, block: int) -> str:
        return self.states[block]

    def block_raw(self, block: int) -> bytes:
        return self.raw[block * BLOCK_SIZE : (block + 1) * BLOCK_SIZE]

    def block_plain(self, block: int) -> bytes:
        return self.plain[block * BLOCK_SIZE : (block + 1) * BLOCK_SIZE]


def decrypt_dump(dump: bytes, constant: bytes) -> DecryptedDump:
    """Return a 1024-byte image where encrypted blocks have been decrypted.

    Plain blocks (0, 1 and every sector trailer) are copied verbatim and blank
    blocks are left as zeros, so offsets in the result line up 1:1 with offsets
    in the raw file.
    """
    if len(dump) != DUMP_SIZE:
        raise ValueError(f"dump de {len(dump)} octets, {DUMP_SIZE} attendus")
    out = bytearray(dump)
    states: list[str] = []
    for block in range(BLOCK_COUNT):
        if not is_encrypted(block):
            states.append(STATE_PLAIN)
            continue
        if is_blank_block(dump, block):
            states.append(STATE_BLANK)
            continue
        start = block * BLOCK_SIZE
        key = derive_block_key(dump, block, constant)
        out[start : start + BLOCK_SIZE] = aes128_ecb_decrypt(
            dump[start : start + BLOCK_SIZE], key
        )
        states.append(STATE_DECRYPTED)
    return DecryptedDump(
        raw=dump, plain=bytes(out), constant=constant, states=tuple(states)
    )


def written_blocks(dump: bytes) -> list[int]:
    """Encrypted-category blocks that actually carry data (non-blank)."""
    return [b for b in range(BLOCK_COUNT) if is_encrypted(b) and not is_blank_block(dump, b)]


# --------------------------------------------------------------------------
# Automatic detection of the constant
# --------------------------------------------------------------------------


def shannon_entropy(data: bytes) -> float:
    """Entropy in bits per byte. AES output sits at ~8.0; real save data far below."""
    if not data:
        return 0.0
    counts = [0] * 256
    for byte in data:
        counts[byte] += 1
    total = len(data)
    return -sum(
        (c / total) * math.log2(c / total) for c in counts if c
    )


class ConstantScore(NamedTuple):
    label: str
    constant: bytes
    zero_ratio: float
    entropy: float
    distinct: int
    sampled_bytes: int

    @property
    def plausible(self) -> bool:
        # A wrong key turns every written block into uniform noise: under 1% zero
        # bytes and ~7.7 bits/byte of entropy. The right key leaves the padding
        # zeros intact — measured at 37-38% zeros / 5.5 bits on this pack.
        return (
            self.sampled_bytes >= BLOCK_SIZE
            and self.zero_ratio >= 0.05
            and self.entropy < 7.0
        )


def score_constant(dump: bytes, constant: bytes, label: str = "") -> ConstantScore:
    """Decrypt with `constant` and measure how un-random the result looks.

    Only blocks that actually carry data are scored. Blank blocks are all-zero
    by definition and would score identically for every candidate, diluting the
    signal to nothing on a never-played figurine.
    """
    decrypted = decrypt_dump(dump, constant)
    payload = b"".join(decrypted.block_plain(b) for b in written_blocks(dump))
    if not payload:
        return ConstantScore(label, constant, 0.0, 0.0, 0, 0)
    return ConstantScore(
        label=label,
        constant=constant,
        zero_ratio=payload.count(0) / len(payload),
        entropy=shannon_entropy(payload),
        distinct=len(set(payload)),
        sampled_bytes=len(payload),
    )


def rank_constants(
    dump: bytes, candidates: Iterable[tuple[str, bytes]] | None = None
) -> list[ConstantScore]:
    """Score every candidate constant, best first."""
    cands = list(CONSTANT_CANDIDATES if candidates is None else candidates)
    scores = [score_constant(dump, const, label) for label, const in cands]
    scores.sort(key=lambda s: (-s.zero_ratio, s.entropy))
    return scores


class Detection(NamedTuple):
    best: ConstantScore
    ranking: list[ConstantScore]
    has_data: bool  # False when the dump has no written block to judge on

    @property
    def constant(self) -> bytes:
        # With nothing to measure, fall back to the first candidate. It is the
        # one empirically confirmed on this pack, and on a fully blank dump the
        # choice has no observable effect anyway.
        return self.best.constant if self.has_data else CONSTANT_CANDIDATES[0][1]

    @property
    def confident(self) -> bool:
        if not self.has_data or not self.best.plausible:
            return False
        runner_up = self.ranking[1] if len(self.ranking) > 1 else None
        return runner_up is None or self.best.zero_ratio > runner_up.zero_ratio * 3


def detect_constant(dump: bytes) -> Detection:
    """Rank every candidate constant against a dump. Never raises on a poor
    result — the caller decides what to do with an implausible best score."""
    ranking = rank_constants(dump)
    return Detection(
        best=ranking[0], ranking=ranking, has_data=bool(written_blocks(dump))
    )


def resolve_constant(
    dump: bytes, override: bytes | None = None
) -> tuple[bytes, Detection | None]:
    """Either honour an explicit --const, or auto-detect."""
    if override is not None:
        return override, None
    detection = detect_constant(dump)
    return detection.constant, detection


# --------------------------------------------------------------------------
# Read-only file access + formatting helpers shared by both tools
# --------------------------------------------------------------------------


class DumpError(Exception):
    """Raised for anything that makes a file unusable as a .sky dump."""


def read_dump(path: str) -> bytes:
    """Read a .sky file. Read-only, and strict about the 1024-byte rule."""
    try:
        with open(path, "rb") as handle:  # "rb" only — never "wb"/"r+b"
            data = handle.read(DUMP_SIZE + 1)
    except IsADirectoryError:
        raise DumpError(f"{path} : est un dossier, pas un fichier")
    except FileNotFoundError:
        raise DumpError(f"{path} : fichier introuvable")
    except PermissionError:
        raise DumpError(f"{path} : lecture refusee (permissions)")
    except OSError as exc:
        raise DumpError(f"{path} : erreur de lecture ({exc})")

    if len(data) != DUMP_SIZE:
        try:
            import os

            actual = os.path.getsize(path)
        except OSError:
            actual = len(data)
        raise DumpError(
            f"{path} : taille de {actual} octets, {DUMP_SIZE} attendus "
            f"(SPEC.md 3.1 / 5.4 — un dump Mifare Classic 1K fait exactement "
            f"1024 octets ; fichier rejete)"
        )
    return data


def hex_row(data: bytes) -> str:
    """16 bytes as 'xx xx xx xx  xx xx xx xx  xx xx xx xx  xx xx xx xx'."""
    parts = [f"{b:02X}" for b in data]
    groups = [" ".join(parts[i : i + 4]) for i in range(0, len(parts), 4)]
    return "  ".join(groups)


def ascii_row(data: bytes) -> str:
    return "".join(chr(b) if 0x20 <= b <= 0x7E else "." for b in data)


def u16le(data: bytes, offset: int) -> int:
    return int.from_bytes(data[offset : offset + 2], "little")


def u32le(data: bytes, offset: int) -> int:
    return int.from_bytes(data[offset : offset + 4], "little")


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def parse_constant_arg(text: str) -> bytes:
    """Turn a --const value into bytes.

    Accepts a literal string, `hex:<hexdigits>` for byte-exact control, or
    `#<n>` to pick candidate number n from --list-consts.
    """
    if text.startswith("hex:"):
        try:
            return bytes.fromhex(text[4:].replace(" ", ""))
        except ValueError as exc:
            raise DumpError(f"--const hex: valeur hexadecimale invalide ({exc})")
    if text.startswith("#"):
        try:
            index = int(text[1:])
        except ValueError:
            raise DumpError(f"--const {text} : numero de candidat invalide")
        if not 0 <= index < len(CONSTANT_CANDIDATES):
            raise DumpError(
                f"--const {text} : candidat hors bornes "
                f"(0..{len(CONSTANT_CANDIDATES) - 1}, cf. --list-consts)"
            )
        return CONSTANT_CANDIDATES[index][1]
    return text.encode("utf-8")


def print_constant_ranking(detection: Detection, indent: str = "  ") -> None:
    """Show why a constant was chosen — the evidence, not just the verdict."""
    if not detection.has_data:
        print(
            f"{indent}Aucun bloc de donnees ecrit dans ce dump : la constante ne peut"
            f"\n{indent}pas etre determinee a partir de ce fichier. Repli sur le"
            f"\n{indent}candidat #0 (sans effet observable ici, tout est a zero)."
        )
        return
    best = detection.best
    verdict = "CONCLUANT" if detection.confident else "NON CONCLUANT"
    print(f"{indent}Verdict : {verdict}")
    for score in detection.ranking:
        marker = "->" if score is best else "  "
        flag = "" if score.plausible else "   (bruit)"
        print(
            f"{indent}{marker} zeros={score.zero_ratio:7.2%}  "
            f"entropie={score.entropy:5.3f} bits/octet  "
            f"valeurs={score.distinct:3d}  {score.label}{flag}"
        )
    print(f"{indent}   (mesure sur {best.sampled_bytes} octets de blocs ecrits)")
    if not detection.confident:
        print(
            f"{indent}ATTENTION : aucun candidat ne se detache nettement sur ce"
            f"\n{indent}fichier — le plus souvent parce qu'il porte trop peu de"
            f"\n{indent}donnees pour que la mesure discrimine. Forcer alors la"
            f"\n{indent}constante etablie sur un fichier riche en donnees :"
            f"\n{indent}    --const '#0'"
            f"\n{indent}Si le resultat reste du bruit, essayer les autres formes"
            f"\n{indent}(--list-consts), voire une variante absente de la liste"
            f"\n{indent}(SPEC.md 3.2 laisse la forme exacte indeterminee)."
        )


def describe_constant(constant: bytes) -> str:
    """Printable, unambiguous rendering (spaces and NULs made visible)."""
    shown = (
        constant.decode("ascii", "replace")
        .replace("\x00", "\\0")
    )
    return f'"{shown}" ({len(constant)} octets)'


# --------------------------------------------------------------------------
# Self-test
# --------------------------------------------------------------------------


def self_test() -> list[str]:
    """Validate the AES implementation. Returns a list of failure messages."""
    failures: list[str] = []

    # FIPS-197 appendix C.1, AES-128.
    key = bytes.fromhex("000102030405060708090a0b0c0d0e0f")
    plain = bytes.fromhex("00112233445566778899aabbccddeeff")
    cipher = bytes.fromhex("69c4e0d86a7b0430d8cdb78070b4c55a")

    got = _aes128_decrypt_block_py(cipher, key)
    if got != plain:
        failures.append(
            f"AES python-pur : FIPS-197 C.1 attendu {plain.hex()}, obtenu {got.hex()}"
        )

    got = aes128_ecb_decrypt(cipher, key)
    if got != plain:
        failures.append(
            f"backend actif ({BACKEND}) : FIPS-197 C.1 attendu {plain.hex()}, "
            f"obtenu {got.hex()}"
        )

    # Cross-check the two implementations over pseudo-random data, so a broken
    # accelerator can never silently replace the reference implementation.
    seed = hashlib.sha256(b"skylanders-selftest").digest()
    blob = b"".join(
        hashlib.sha256(seed + bytes([i])).digest() for i in range(8)
    )  # 256 bytes
    ref = b"".join(
        _aes128_decrypt_block_py(blob[i : i + 16], key) for i in range(0, len(blob), 16)
    )
    if aes128_ecb_decrypt(blob, key) != ref:
        failures.append(
            f"backend actif ({BACKEND}) diverge de l'implementation python-pure"
        )

    return failures


if __name__ == "__main__":
    import sys

    print(f"Backend AES : {BACKEND}")
    problems = self_test()
    if problems:
        for problem in problems:
            print(f"ECHEC : {problem}")
        sys.exit(1)
    print("Auto-test AES : OK")