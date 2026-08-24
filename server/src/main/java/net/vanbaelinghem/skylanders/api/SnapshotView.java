package net.vanbaelinghem.skylanders.api;

import java.time.OffsetDateTime;

/**
 * One point of a figurine's history.
 *
 * <p>{@code level} is deliberately absent: the XP→level curve has never been measured, and
 * inventing thresholds would breach CLAUDE.md invariant 2. The UI shows raw XP until the curve is
 * established (decision of 2026-08-23).
 */
public record SnapshotView(
        OffsetDateTime capturedAt,
        OffsetDateTime savedAt,
        Integer xp,
        Integer gold,
        Integer upgradesBitfield,
        Integer upgradesCount,
        String nickname,
        Integer playtimeSeconds,
        String parseStatus,
        boolean hasPlayEvidence,
        int changedBlocks) {}
