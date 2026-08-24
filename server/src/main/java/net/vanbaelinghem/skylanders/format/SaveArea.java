package net.vanbaelinghem.skylanders.format;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.stream.IntStream;

/**
 * One of the two mirror save areas of a tag, presented in <em>logical</em> space.
 *
 * <p>FORMAT.md §4 — VÉRIFIÉ. The tag carries two interchangeable save areas. Sector trailers cut
 * physically through the middle of fields, so the data blocks must be concatenated with the
 * trailers skipped before any offset means anything. The nickname straddling blocks {@code 0x0A}
 * and {@code 0x0C} is the direct proof: contiguous in logical space, fragmented in physical space.
 *
 * <p><strong>Never read a field without going through {@link #current}.</strong> Reading the stale
 * area does not yield an absurd value — it yields the <em>previous</em> save, which no plausibility
 * check will ever catch (FORMAT.md §7.2).
 */
public record SaveArea(byte[] logical, int[] blocks) {

    /** FORMAT.md §4.1 — data blocks of area 0 and area 1, sector trailers excluded. */
    public static final int[] AREA_0 = IntStream.range(0x08, 0x24).filter(b -> b % 4 != 3).toArray();
    public static final int[] AREA_1 = IntStream.range(0x24, 0x40).filter(b -> b % 4 != 3).toArray();

    /** 21 data blocks of 16 bytes. */
    public static final int LOGICAL_SIZE = 21 * SkyCrypto.BLOCK_SIZE;

    // -- Offsets, all LOGICAL. See FORMAT.md §5.1 for the evidence behind each one. ------------

    /** FORMAT.md §5.1 — VÉRIFIÉ. Cumulative XP; does not reset on level-up. */
    private static final int OFF_XP = 0x00;
    /** FORMAT.md §5.1 — VÉRIFIÉ. Seven exact in-game values matched across two figurines. */
    private static final int OFF_GOLD = 0x03;
    /** FORMAT.md §5.1 — VÉRIFIÉ. Playtime in seconds, monotonic. */
    private static final int OFF_PLAYTIME = 0x05;
    /** FORMAT.md §4.3 — VÉRIFIÉ. Selects the current area; predicted 7/7 captures. */
    private static final int OFF_SEQUENCE = 0x09;
    /** FORMAT.md §5.1 — VÉRIFIÉ (characters) / §5.3 — VÉRIFIÉ (traps: the trapped villain). */
    private static final int OFF_UPGRADES_OR_VILLAIN = 0x10;
    /** FORMAT.md §5.3 — PROBABLE. Number of villains ever captured in this trap. */
    private static final int OFF_CAPTURE_COUNT = 0x01;
    /** FORMAT.md §5.1 — VÉRIFIÉ. UTF-16LE, spans blocks 0x0A and 0x0C. */
    private static final int OFF_NICKNAME = 0x20;
    private static final int NICKNAME_MAX_BYTES = 0x20;
    /** FORMAT.md §5.1 — VÉRIFIÉ. [minute u8, hour u8, day u8, month u8, year u16 LE]. */
    private static final int OFF_LAST_SAVED = 0x40;
    /** FORMAT.md §5.1 — VÉRIFIÉ. Same layout; frozen on the very first write. */
    private static final int OFF_FIRST_WRITTEN = 0x50;

    /** Build the logical view of one area from a decrypted dump. */
    public static SaveArea of(byte[] decrypted, int[] blocks) {
        byte[] logical = new byte[blocks.length * SkyCrypto.BLOCK_SIZE];
        for (int i = 0; i < blocks.length; i++) {
            System.arraycopy(decrypted, blocks[i] * SkyCrypto.BLOCK_SIZE,
                    logical, i * SkyCrypto.BLOCK_SIZE, SkyCrypto.BLOCK_SIZE);
        }
        return new SaveArea(logical, blocks);
    }

    /**
     * FORMAT.md §4.3 / §7.2 — return the area to actually read: the one whose sequence counter is
     * ahead. The counter is a {@code u8} and wraps, so "ahead" is modular, not a plain comparison.
     */
    public static SaveArea current(byte[] decrypted) {
        SaveArea a0 = of(decrypted, AREA_0);
        SaveArea a1 = of(decrypted, AREA_1);
        int c0 = a0.sequenceCounter();
        int c1 = a1.sequenceCounter();
        if (c0 == c1) {
            return a0; // both blank, or an ambiguity the caller should have rejected
        }
        return isAhead(c0, c1) ? a0 : a1;
    }

    /** True when {@code a} is the more recent of two wrapping {@code u8} counters. */
    static boolean isAhead(int a, int b) {
        return ((a - b) & 0xFF) < 0x80;
    }

    public int sequenceCounter() {
        return logical[OFF_SEQUENCE] & 0xFF;
    }

    public int xp() {
        return u16(OFF_XP);
    }

    public int gold() {
        return u16(OFF_GOLD);
    }

    public int playtimeSeconds() {
        return u16(OFF_PLAYTIME);
    }

    public int upgradesBitfield() {
        return logical[OFF_UPGRADES_OR_VILLAIN] & 0xFF;
    }

    /** FORMAT.md §5.3 — VÉRIFIÉ. {@code 0} means the trap is empty. */
    public int villainRawId() {
        return logical[OFF_UPGRADES_OR_VILLAIN] & 0xFF;
    }

    public int captureCount() {
        return logical[OFF_CAPTURE_COUNT] & 0xFF;
    }

    /** Nickname, or {@code null} when unset. UTF-16LE, NUL-terminated. */
    public String nickname() {
        StringBuilder text = new StringBuilder();
        for (int i = OFF_NICKNAME; i < OFF_NICKNAME + NICKNAME_MAX_BYTES; i += 2) {
            char c = (char) SkyCrypto.u16le(logical, i);
            if (c == 0) {
                break;
            }
            text.append(c);
        }
        return text.isEmpty() ? null : text.toString();
    }

    public ZonedDateTime lastSavedAt(ZoneId zone) {
        return timestamp(OFF_LAST_SAVED, zone);
    }

    /** FORMAT.md §5.1 — the tag's own record of when it was first placed on a portal. */
    public ZonedDateTime firstWrittenAt(ZoneId zone) {
        return timestamp(OFF_FIRST_WRITTEN, zone);
    }

    private ZonedDateTime timestamp(int offset, ZoneId zone) {
        int minute = logical[offset] & 0xFF;
        int hour = logical[offset + 1] & 0xFF;
        int day = logical[offset + 2] & 0xFF;
        int month = logical[offset + 3] & 0xFF;
        int year = SkyCrypto.u16le(logical, offset + 4);
        if (year < 2010 || year > 2100 || month < 1 || month > 12
                || day < 1 || day > 31 || hour > 23 || minute > 59) {
            return null; // never written, or a value we refuse to trust
        }
        return ZonedDateTime.of(LocalDateTime.of(year, month, day, hour, minute), zone);
    }

    private int u16(int offset) {
        return SkyCrypto.u16le(logical, offset);
    }
}
