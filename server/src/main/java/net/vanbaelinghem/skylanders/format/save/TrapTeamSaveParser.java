package net.vanbaelinghem.skylanders.format.save;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import net.vanbaelinghem.skylanders.classification.Category;
import net.vanbaelinghem.skylanders.classification.Game;
import net.vanbaelinghem.skylanders.format.SaveArea;
import net.vanbaelinghem.skylanders.format.SkyCrypto;
import org.springframework.stereotype.Component;

/** The only {@link GameSaveParser} implementation in v1 (SPEC.md §11.1). */
@Component
public class TrapTeamSaveParser implements GameSaveParser {

    /**
     * The tag stores wall-clock timestamps with no zone (FORMAT.md §5.1). They were written by the
     * console the user plays on, so the local zone is the only sensible interpretation.
     */
    private final ZoneId zone = ZoneId.systemDefault();

    @Override
    public boolean supports(Game game) {
        return game == Game.TRAP_TEAM;
    }

    @Override
    public SaveProgress parse(Category category, byte[] raw, byte[] decrypted) {
        // FORMAT.md §5.3 — a trap is a different tag with a different layout. Reading the
        // character fields off one yields garbage: measured on two independent traps, +0x00 reads
        // a constant 256 and both timestamps stay at zero. Reporting "256 XP" for a trap would be
        // a fabricated value, so nothing but the play evidence is claimed here.
        if (category == Category.TRAP) {
            return new SaveProgress(null, null, null, null, null, null, null, null,
                    !SkyCrypto.isNeverPlayed(raw), ParseStatus.OK);
        }
        // FORMAT.md §3 — VÉRIFIÉ on 7 figurines. The criterion is "EVERY data block is blank",
        // never a single block: a partially written dump has been played.
        boolean neverPlayed = SkyCrypto.isNeverPlayed(raw);
        if (neverPlayed) {
            return new SaveProgress(0, null, 0, 0, null, 0, null, null, false, ParseStatus.OK);
        }

        // FORMAT.md §4.3 / §7.2 — always select through the sequence counter.
        SaveArea area = SaveArea.current(decrypted);
        ZonedDateTime savedAt = area.lastSavedAt(zone);
        ZonedDateTime firstWrittenAt = area.firstWrittenAt(zone);

        return new SaveProgress(
                area.xp(),
                null, // see SaveProgress#level — curve unknown, not invented
                area.gold(),
                area.upgradesBitfield(),
                area.nickname(),
                area.playtimeSeconds(),
                savedAt,
                firstWrittenAt,
                true,
                ParseStatus.OK);
    }

    /** FORMAT.md §5.3 — VÉRIFIÉ. {@code 0} means the trap is empty. */
    public TrapContent parseTrap(byte[] raw, byte[] decrypted) {
        if (SkyCrypto.isNeverPlayed(raw)) {
            return new TrapContent(0, true, 0);
        }
        SaveArea area = SaveArea.current(decrypted);
        int villain = area.villainRawId();
        return new TrapContent(villain, villain == 0, area.captureCount());
    }

    public record TrapContent(int villainRawId, boolean empty, int captureCount) {}
}
