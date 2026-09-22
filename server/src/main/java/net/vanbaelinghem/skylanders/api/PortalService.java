package net.vanbaelinghem.skylanders.api;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.vanbaelinghem.skylanders.classification.Category;
import net.vanbaelinghem.skylanders.domain.PortalSlot;
import net.vanbaelinghem.skylanders.domain.PortalSlotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * The on-screen portal layout: which figurine stands on which slot.
 *
 * <p>Deliberately dumb. It records a layout and interprets nothing — no completion, no unlock,
 * no game rule. Placing a figurine here says nothing about whether it was ever placed on a real
 * portal; that remains {@code toy.first_played_at} (CLAUDE.md invariant 5).
 *
 * <p>The layout mirrors the one rule the physical hardware really has: figurines on the grid,
 * and a single keyhole for a trap. The rest of the per-game rules — a vehicle slot on
 * SuperChargers, how many figurines a game reads at once — is not modelled; that is a question
 * for the day a Cemu bridge exists.
 */
@Service
public class PortalService {

    private static final Logger log = LoggerFactory.getLogger(PortalService.class);

    private final PortalSlotRepository slots;
    private final RosterService roster;
    private final TrapContentService trapContents;

    /** Emplacements pour figurines. Le piege a le sien, juste apres, et n'est pas compte ici. */
    private final int slotCount;

    public PortalService(PortalSlotRepository slots, RosterService roster,
                         TrapContentService trapContents,
                         @Value("${skylanders.portal.slots:9}") int slotCount) {
        this.slots = slots;
        this.roster = roster;
        this.trapContents = trapContents;
        this.slotCount = slotCount;
    }

    /**
     * Index de l'emplacement reserve au piege : juste apres la grille des figurines.
     *
     * <p>Le portail physique n'a qu'une serrure a piege, et elle est a part. La modeliser comme
     * un emplacement de plus, mais typé, evite d'avoir a repondre « lequel des neuf ? » et rend
     * la regle visible a l'ecran plutot que cachee dans un message d'erreur.
     */
    public int trapSlotIndex() {
        return slotCount;
    }

    @Transactional(readOnly = true)
    public PortalView state() {
        return assemble();
    }

    /**
     * Puts a figurine on a slot.
     *
     * <p>A physical figurine exists once, so placing one that already stands somewhere else
     * moves it rather than cloning it — the same thing a hand does on a real portal, and what
     * dragging a slot onto another slot has to mean.
     */
    @Transactional
    public PortalView place(int index, int toyId, int variantId) {
        requireSlotInGrid(index);
        // Existence check before anything is written: a layout referring to an identity the
        // app cannot name would draw a nameless, pictureless hole.
        ToyView toy = roster.view(toyId, variantId).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "identite inconnue : toy " + toyId + " / variante " + variantId));
        requireRightKindOfSlot(toy, index);
        slots.deleteBySlotIndex(index);
        slots.deleteByToyIdAndVariantId(toyId, variantId);
        // The two deletes must reach the database before the insert, or the unique index on
        // (toy_id, variant_id) fires on a row Hibernate has not got round to removing yet.
        slots.flush();
        slots.save(new PortalSlot(index, toyId, variantId, OffsetDateTime.now()));
        return assemble();
    }

    @Transactional
    public PortalView clearSlot(int index) {
        // No grid check here on purpose: shrinking the grid must never leave a figurine
        // stranded on a slot the user can see but no longer empty.
        slots.deleteBySlotIndex(index);
        return assemble();
    }

    @Transactional
    public PortalView clearAll() {
        // deleteAllInBatch et non deleteAll : cf. PortalSlotRepository — deleteAll() laisserait
        // l'emplacement 0 en place, ce qui se voit tout de suite et ne se devine pas du tout.
        slots.deleteAllInBatch();
        return assemble();
    }

    // -- assemblage ----------------------------------------------------------------------------

    private PortalView assemble() {
        Map<Integer, PortalSlot> occupied = slots.findAll().stream()
                .collect(Collectors.toMap(PortalSlot::getSlotIndex, Function.identity()));

        // Lowering skylanders.portal.slots must not make a placed figurine vanish in silence:
        // the layout extends past the grid so the extra slots stay visible and removable.
        int highest = occupied.keySet().stream().mapToInt(Integer::intValue).max().orElse(-1);
        int span = Math.max(trapSlotIndex() + 1, highest + 1);
        if (span > trapSlotIndex() + 1) {
            long stranded = occupied.keySet().stream().filter(i -> i > trapSlotIndex()).count();
            log.warn("Portail : {} figurine(s) posee(s) au-dela de la grille configuree "
                    + "({} emplacements + serrure a piege) — affichees quand meme pour pouvoir "
                    + "etre retirees", stranded, slotCount);
        }

        List<PortalSlotView> views = new ArrayList<>(span);
        for (int index = 0; index < span; index++) {
            views.add(toView(index, occupied.get(index)));
        }
        return new PortalView(slotCount, trapSlotIndex(), views);
    }

    private PortalSlotView toView(int index, PortalSlot slot) {
        boolean trapSlot = index == trapSlotIndex();
        boolean beyond = index > trapSlotIndex();
        if (slot == null) {
            return new PortalSlotView(index, null, null, beyond, trapSlot, null);
        }
        ToyView toy = roster.view(slot.getToyId(), slot.getVariantId()).orElse(null);
        if (toy == null) {
            // Only reachable if an identity disappeared from both the catalogue and the
            // received files after being placed. Never fail silently on it (CLAUDE.md).
            log.warn("Portail : emplacement {} occupe par une identite introuvable "
                    + "(toy {} / variante {})", index, slot.getToyId(), slot.getVariantId());
        }
        VillainView prisoner = toy != null
                && Category.TRAP.name().equalsIgnoreCase(toy.category())
                ? trapContents.villainIn(slot.getToyId(), slot.getVariantId())
                : null;
        return new PortalSlotView(index, toy, slot.getPlacedAt(), beyond, trapSlot, prisoner);
    }

    /**
     * Un piege va dans la serrure a piege, et rien d'autre n'y va.
     *
     * <p>La contrainte remplace celle, plus faible, du « un seul piege n'importe ou » : puisque
     * la serrure est unique, la limite se tient toute seule et l'utilisateur voit ou poser
     * plutot que de l'apprendre par un refus.
     */
    private void requireRightKindOfSlot(ToyView toy, int target) {
        boolean isTrap = Category.TRAP.name().equalsIgnoreCase(toy.category());
        if (isTrap && target != trapSlotIndex()) {
            throw new PortalRejectedException(Notice.of("trapGoesInTrapSlot"));
        }
        if (!isTrap && target == trapSlotIndex()) {
            throw new PortalRejectedException(Notice.of("onlyTrapsInTrapSlot"));
        }
    }

    /** La serrure a piege fait partie de la grille : c'est le dernier emplacement. */
    private void requireSlotInGrid(int index) {
        if (index < 0 || index > trapSlotIndex()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "emplacement " + index + " hors grille (0.." + trapSlotIndex() + ")");
        }
    }
}
