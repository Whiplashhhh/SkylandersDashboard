package net.vanbaelinghem.skylanders.api;

import java.time.OffsetDateTime;

/**
 * A trap and what it currently holds.
 *
 * <p>A trap holds <strong>one villain at a time</strong>. The list of every villain ever captured
 * lives in the Wii U save, not on the tags, and is out of scope (SPEC.md §3.6).
 */
public record TrapView(
        int toyId,
        int variantId,
        String trapName,
        String element,
        String filePath,
        int villainRawId,
        String villainName,
        boolean empty,
        OffsetDateTime capturedAt) {}
