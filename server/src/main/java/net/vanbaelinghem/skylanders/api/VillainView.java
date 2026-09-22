package net.vanbaelinghem.skylanders.api;

import java.time.OffsetDateTime;

/**
 * One villain of the roster, captured or not.
 *
 * <p>« Captured » is derived, never stored: it means a trap currently holds a raw id the user has
 * named after this villain. Same discipline as {@code toy.first_played_at} — the presence of a
 * catalogue row proves nothing (CLAUDE.md invariant 5).
 *
 * @param name        the villain's name, always known: it comes from the game's roster
 * @param captured    a trap holds it right now
 * @param rawId       the identifier read on the tag, {@code null} until a trap holding it has been
 *                    named. It is the user's naming that binds the two (SPEC.md §7.2).
 * @param doomRaider  boss of its element
 * @param summary     short extract of the Fandom page (CC BY-SA); {@code wikiUrl} attributes it
 * @param heldIn      how many traps hold it right now
 */
public record VillainView(
        String name,
        String element,
        boolean doomRaider,
        boolean captured,
        Integer rawId,
        String summary,
        String wikiUrl,
        OffsetDateTime capturedAt,
        long heldIn) {}
