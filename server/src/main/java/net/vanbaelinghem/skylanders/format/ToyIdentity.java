package net.vanbaelinghem.skylanders.format;

import java.util.HexFormat;

/**
 * Identity of a toy, readable on all six games (SPEC.md §3.4).
 *
 * @param uid       tag UID, 4 bytes. Informational only — <strong>never an identifier</strong>:
 *                  609 distinct UIDs for 702 files, up to 10 files sharing one (FORMAT.md §7.1).
 * @param toyId     FORMAT.md §6 — VÉRIFIÉ. For a <em>trap</em> this encodes the element, not the
 *                  model (FORMAT.md §6.2).
 * @param variantId FORMAT.md §6 — VÉRIFIÉ. Encodes the variant type; {@code 6145} = Series 2 on
 *                  24 files out of 24.
 */
public record ToyIdentity(byte[] uid, int toyId, int variantId) {

    public String uidHex() {
        return HexFormat.of().withUpperCase().formatHex(uid);
    }
}
