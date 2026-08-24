package net.vanbaelinghem.skylanders.format.save;

import java.time.ZonedDateTime;

/**
 * Progress read from the current save area.
 *
 * @param level            <strong>always {@code null} for now.</strong> SPEC.md §7.1 wants the
 *                         level derived from XP, but the XP→level curve is not in FORMAT.md: two
 *                         figurines gave only three level observations. Inventing thresholds would
 *                         breach CLAUDE.md invariant 2, so the field stays empty until measured.
 * @param hasPlayEvidence  FORMAT.md §3 — the single source of truth for "unlocked".
 */
public record SaveProgress(
        Integer xp,
        Integer level,
        Integer gold,
        Integer upgradesBitfield,
        String nickname,
        Integer playtimeSeconds,
        ZonedDateTime savedAt,
        ZonedDateTime firstWrittenAt,
        boolean hasPlayEvidence,
        ParseStatus parseStatus) {

    public static SaveProgress unsupported(boolean hasPlayEvidence) {
        return new SaveProgress(null, null, null, null, null, null, null, null,
                hasPlayEvidence, ParseStatus.UNSUPPORTED_GAME);
    }
}
