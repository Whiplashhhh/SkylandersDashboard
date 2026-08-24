package net.vanbaelinghem.skylanders.format.save;

import net.vanbaelinghem.skylanders.classification.Category;
import net.vanbaelinghem.skylanders.classification.Game;

/**
 * One implementation per game (SPEC.md §11.1). A game without an implementation yields
 * {@link ParseStatus#UNSUPPORTED_GAME}: the toy stays listed with its identity, without progress.
 * Adding a game must never break an existing one.
 */
public interface GameSaveParser {

    boolean supports(Game game);

    /**
     * @param category  the save layout depends on the kind of toy: a trap does not carry the
     *                  character progress fields at all (FORMAT.md §5.3)
     * @param raw       the 1024 raw bytes, needed for the never-played test (FORMAT.md §3)
     * @param decrypted the same dump with encrypted blocks decrypted
     */
    SaveProgress parse(Category category, byte[] raw, byte[] decrypted);
}
