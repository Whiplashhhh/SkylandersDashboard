package net.vanbaelinghem.skylanders.format;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

/**
 * Block-level delta between a dump and its baseline (FORMAT.md §7.3).
 *
 * <p>Encoding: a sequence of {@code [block index u8][16 bytes]} records, in ascending block order.
 *
 * <p>The granularity is deliberate. Measured on real data (2026-08-23): a byte-level delta storing
 * {@code (offset, value)} pairs costs <em>98%</em> of the raw file, because the format rewrites
 * whole 16-byte blocks — change one byte and its fifteen neighbours change too. Block granularity
 * costs 35%, and gzip of the whole dump 56%.
 *
 * <p>Deltas are computed against the <strong>baseline</strong>, never against the previous
 * snapshot. A chain would be 2.7× smaller, but SPEC.md §8.2 mandates <em>rejecting</em> a doubtful
 * snapshot, so gaps in the chain are expected by design and would make every later link
 * unrecoverable. Against the baseline, each snapshot reconstructs on its own.
 */
public final class DeltaCodec {

    private static final int RECORD_SIZE = 1 + SkyCrypto.BLOCK_SIZE;

    private DeltaCodec() {}

    public static byte[] encode(byte[] baseline, byte[] current) {
        SkyCrypto.requireValidSize(baseline);
        SkyCrypto.requireValidSize(current);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int block = 0; block < SkyCrypto.BLOCK_COUNT; block++) {
            int start = block * SkyCrypto.BLOCK_SIZE;
            if (Arrays.equals(baseline, start, start + SkyCrypto.BLOCK_SIZE,
                    current, start, start + SkyCrypto.BLOCK_SIZE)) {
                continue;
            }
            out.write(block);
            out.write(current, start, SkyCrypto.BLOCK_SIZE);
        }
        return out.toByteArray();
    }

    public static byte[] apply(byte[] baseline, byte[] delta) {
        SkyCrypto.requireValidSize(baseline);
        if (delta.length % RECORD_SIZE != 0) {
            throw new IllegalArgumentException(
                    "delta de " + delta.length + " octets, multiple de " + RECORD_SIZE + " attendu");
        }
        byte[] out = Arrays.copyOf(baseline, SkyCrypto.DUMP_SIZE);
        for (int i = 0; i < delta.length; i += RECORD_SIZE) {
            int block = delta[i] & 0xFF;
            if (block >= SkyCrypto.BLOCK_COUNT) {
                throw new IllegalArgumentException("index de bloc hors bornes : " + block);
            }
            System.arraycopy(delta, i + 1, out, block * SkyCrypto.BLOCK_SIZE, SkyCrypto.BLOCK_SIZE);
        }
        return out;
    }

    public static int changedBlockCount(byte[] delta) {
        return delta.length / RECORD_SIZE;
    }
}
