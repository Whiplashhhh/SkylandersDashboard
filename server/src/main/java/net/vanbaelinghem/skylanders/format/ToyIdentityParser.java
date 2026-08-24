package net.vanbaelinghem.skylanders.format;

import java.util.Arrays;
import org.springframework.stereotype.Component;

/**
 * Reads blocks 0 and 1, which are stored in clear and laid out identically on all six games.
 * This is what lets the whole collection be listed without parsing a single save area.
 */
@Component
public class ToyIdentityParser {

    /** FORMAT.md §6 — PROBABLE. */
    private static final int OFFSET_UID = 0x00;
    private static final int UID_LENGTH = 4;

    /** FORMAT.md §6 — VÉRIFIÉ (5 tests de cohérence interne sur 702 fichiers, 0 conflit). */
    private static final int OFFSET_TOY_ID = 0x10;

    /** FORMAT.md §6 — VÉRIFIÉ (codes de variante stables, partition des pièges exacte). */
    private static final int OFFSET_VARIANT_ID = 0x1C;

    public ToyIdentity parse(byte[] dump) {
        SkyCrypto.requireValidSize(dump);
        return new ToyIdentity(
                Arrays.copyOfRange(dump, OFFSET_UID, OFFSET_UID + UID_LENGTH),
                SkyCrypto.u16le(dump, OFFSET_TOY_ID),
                SkyCrypto.u16le(dump, OFFSET_VARIANT_ID));
    }
}
