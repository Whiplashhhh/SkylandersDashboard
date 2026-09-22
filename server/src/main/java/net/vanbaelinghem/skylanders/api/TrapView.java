package net.vanbaelinghem.skylanders.api;

import java.time.OffsetDateTime;

/**
 * A trap and what it currently holds.
 *
 * <p>A trap holds <strong>one villain at a time</strong>. The list of every villain ever captured
 * lives in the Wii U save, not on the tags, and is out of scope (SPEC.md §3.6).
 *
 * @param category always {@code TRAP}. Carried so that a trap coming from this screen looks
 *                 like any other figurine to the code that places it on the portal — without
 *                 it, the portal aimed a trap at the figurine grid instead of the keyhole.
 * @param villain the roster entry for what is inside — name, element, story, wiki link — or
 *                {@code null} when the trap is empty <em>or</em> when its raw id has not been
 *                named yet. Those two are told apart by {@code empty}: an occupied trap with no
 *                villain is an honest « someone is in there, nobody said who ».
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
        OffsetDateTime capturedAt,
        String category,
        VillainView villain) {}
