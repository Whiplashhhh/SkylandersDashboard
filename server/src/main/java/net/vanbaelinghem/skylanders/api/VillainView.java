package net.vanbaelinghem.skylanders.api;

import java.time.OffsetDateTime;

/**
 * @param name  {@code null} while unknown. No external source maps a raw ID to a name, so the
 *              reference fills itself as the user names them (SPEC.md §7.2).
 * @param seenIn how many traps currently hold this villain
 */
public record VillainView(
        int rawId,
        String name,
        String element,
        OffsetDateTime namedByUserAt,
        long seenIn) {}
