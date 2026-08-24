package net.vanbaelinghem.skylanders.api;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * One line of the ranking.
 *
 * <p>Every numeric column comes from the tag itself and is `VÉRIFIÉ` in FORMAT.md §5.1. Character
 * stats of the game — vie, vitesse, armure, chance — are deliberately absent: they are not written
 * on the figurine, they are attributes of the character shared by every copy of it. Showing them
 * would mean importing an unmeasured external source (decision of 2026-08-24).
 *
 * @param rank      1-based position in the current sort, computed after filtering
 * @param xpCapped  the XP field saturates at 33 000 (FORMAT.md §8.7); the UI marks such a value
 * @param villainName {@code null} on an occupied trap means the raw id has no name yet — the UI
 *                    offers to name it, which is how the reference fills itself (SPEC.md §7.2)
 */
public record LeaderboardRow(
        int rank,
        int toyId,
        int variantId,
        String nameFr,
        String nameEn,
        List<String> games,
        String element,
        String category,
        boolean unlocked,
        boolean parsable,
        Integer xp,
        boolean xpCapped,
        Integer gold,
        Integer upgradesCount,
        Integer playtimeSeconds,
        String nickname,
        OffsetDateTime firstPlayedAt,
        OffsetDateTime lastSavedAt,
        Integer villainRawId,
        String villainName,
        Boolean trapEmpty) {}
