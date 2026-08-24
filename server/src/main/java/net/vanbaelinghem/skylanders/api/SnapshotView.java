package net.vanbaelinghem.skylanders.api;

import java.time.OffsetDateTime;

/**
 * One point of a figurine's history.
 *
 * <p>{@code level} is deliberately absent: the XP→level curve has never been measured, and
 * inventing thresholds would breach CLAUDE.md invariant 2. The UI shows raw XP until the curve is
 * established (decision of 2026-08-23).
 *
 * @param at the moment this snapshot belongs at on a timeline: the tag's own save timestamp when
 *           it has one, the ingestion time otherwise. The graph plots against this, never against
 *           {@code capturedAt} alone.
 */
public record SnapshotView(
        OffsetDateTime at,
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
