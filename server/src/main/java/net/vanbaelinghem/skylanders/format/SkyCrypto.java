package net.vanbaelinghem.skylanders.format;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

/**
 * Low-level decryption of a {@code .sky} dump.
 *
 * <p>Everything here is measured, not assumed — see {@code FORMAT.md} §2. No JCE provider beyond
 * the JDK is required: MD5 for key derivation, AES-128-ECB for the block cipher.
 */
public final class SkyCrypto {

    /** FORMAT.md §1 — VÉRIFIÉ : a Mifare Classic 1K dump is exactly 1024 bytes. */
    public static final int DUMP_SIZE = 1024;

    public static final int BLOCK_SIZE = 16;
    public static final int BLOCK_COUNT = DUMP_SIZE / BLOCK_SIZE;

    /**
     * FORMAT.md §2.1 — VÉRIFIÉ (2026-08-18, 119/119 files carrying ≥20 written blocks).
     *
     * <p>Leading AND trailing space, 53 ASCII bytes, no NUL terminator. Eleven other plausible
     * forms were measured and every one produced uniform noise (&lt;1.1% zero bytes, ~7.7 bits of
     * entropy per byte) where this one yields 37–94% zeros.
     */
    private static final byte[] COPYRIGHT_CONSTANT =
            " Copyright (C) 2010 Activision. All Rights Reserved. ".getBytes(StandardCharsets.US_ASCII);

    /** FORMAT.md §2.2 — VÉRIFIÉ : the key derives from the first 32 bytes of the dump. */
    private static final int KEY_MATERIAL_LENGTH = 32;

    private SkyCrypto() {}

    /**
     * FORMAT.md §2.3 — PROBABLE. Block 0 (UID), block 1 (toy identity) and every sector trailer
     * ({@code N % 4 == 3}) are stored in clear; all other blocks are AES-encrypted.
     */
    public static boolean isEncryptedBlock(int block) {
        return block > 1 && block % 4 != 3;
    }

    /**
     * FORMAT.md §3 — VÉRIFIÉ (7 figurines). A data block the game has never written is stored as
     * sixteen literal zero bytes, <em>not</em> as the encryption of zeros. Running AES over such a
     * block yields pseudo-random noise, so blank blocks must be recognised and left alone.
     */
    public static boolean isBlankBlock(byte[] dump, int block) {
        int start = block * BLOCK_SIZE;
        for (int i = start; i < start + BLOCK_SIZE; i++) {
            if (dump[i] != 0) {
                return false;
            }
        }
        return true;
    }

    /** True when the dump carries no written data block at all — i.e. never played. */
    public static boolean isNeverPlayed(byte[] dump) {
        for (int block = 0; block < BLOCK_COUNT; block++) {
            if (isEncryptedBlock(block) && !isBlankBlock(dump, block)) {
                return false;
            }
        }
        return true;
    }

    /** FORMAT.md §2.2 — {@code key(N) = MD5(dump[0x00..0x20] || byte(N) || CONSTANT)}. */
    public static byte[] deriveKey(byte[] dump, int block) {
        MessageDigest md5;
        try {
            md5 = MessageDigest.getInstance("MD5");
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("MD5 absent de la JVM", e);
        }
        md5.update(dump, 0, KEY_MATERIAL_LENGTH);
        md5.update((byte) block);
        md5.update(COPYRIGHT_CONSTANT);
        return md5.digest();
    }

    /**
     * Decrypt every encrypted, non-blank block. Plain blocks are copied verbatim and blank blocks
     * are left as zeros, so offsets in the result line up 1:1 with offsets in the raw file.
     */
    public static byte[] decrypt(byte[] dump) {
        requireValidSize(dump);
        byte[] out = Arrays.copyOf(dump, DUMP_SIZE);
        for (int block = 0; block < BLOCK_COUNT; block++) {
            if (!isEncryptedBlock(block) || isBlankBlock(dump, block)) {
                continue;
            }
            int start = block * BLOCK_SIZE;
            try {
                Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
                cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(deriveKey(dump, block), "AES"));
                cipher.doFinal(dump, start, BLOCK_SIZE, out, start);
            } catch (GeneralSecurityException e) {
                throw new IllegalStateException("dechiffrement du bloc " + block + " impossible", e);
            }
        }
        return out;
    }

    public static void requireValidSize(byte[] dump) {
        if (dump == null || dump.length != DUMP_SIZE) {
            throw new IllegalArgumentException(
                    "dump de " + (dump == null ? "null" : dump.length)
                            + " octets, " + DUMP_SIZE + " attendus (FORMAT.md §1)");
        }
    }

    static int u16le(byte[] data, int offset) {
        return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8);
    }
}
