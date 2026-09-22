package net.vanbaelinghem.skylanders.api;

import java.util.Optional;
import net.vanbaelinghem.skylanders.domain.Toy;
import net.vanbaelinghem.skylanders.domain.ToyRepository;
import net.vanbaelinghem.skylanders.domain.TrapContent;
import net.vanbaelinghem.skylanders.domain.TrapContentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What a trap holds, by identity rather than by row.
 *
 * <p>One physical trap has as many {@code toy} rows as it has files, so "who is inside" has to be
 * asked across all of them and answered with the most recent reading. Kept in one place because
 * both the traps screen and the portal's keyhole need the same answer.
 */
@Service
@Transactional(readOnly = true)
public class TrapContentService {

    private final ToyRepository toys;
    private final TrapContentRepository contents;
    private final VillainService villains;

    public TrapContentService(ToyRepository toys, TrapContentRepository contents,
                              VillainService villains) {
        this.toys = toys;
        this.contents = contents;
        this.villains = villains;
    }

    /** The latest reading for this trap identity, whichever file it came from. */
    public Optional<TrapContent> latest(int toyId, int variantId) {
        return toys.findByToyIdAndVariantId(toyId, variantId).stream()
                .map(contents::findFirstByToyOrderByCapturedAtDesc)
                .flatMap(Optional::stream)
                .max(java.util.Comparator.comparing(TrapContent::getCapturedAt));
    }

    /**
     * The villain locked in this trap, or {@code null}.
     *
     * <p>{@code null} covers two different situations on purpose — empty trap, or occupied by a
     * raw id nobody has named yet. The caller knows which from {@link TrapContent#isEmpty()}, and
     * the interface says « ? » rather than inventing a name (SPEC.md §7.2).
     */
    public VillainView villainIn(int toyId, int variantId) {
        return latest(toyId, variantId)
                .filter(content -> !content.isEmpty())
                .map(content -> villains.forRawId(content.getVillainRawId()))
                .orElse(null);
    }

    /** Convenience for callers that already hold the toy row. */
    public Optional<TrapContent> latest(Toy toy) {
        return contents.findFirstByToyOrderByCapturedAtDesc(toy);
    }
}
