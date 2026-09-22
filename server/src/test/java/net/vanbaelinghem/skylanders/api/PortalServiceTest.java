package net.vanbaelinghem.skylanders.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.vanbaelinghem.skylanders.domain.PortalSlot;
import net.vanbaelinghem.skylanders.domain.PortalSlotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class PortalServiceTest {

    /** Stands in for the table: index -> occupant, so the moves are actually observable. */
    private final Map<Integer, PortalSlot> table = new LinkedHashMap<>();
    private PortalSlotRepository slots;
    private RosterService roster;

    private static ToyView toy(int toyId, int variantId) {
        return toy(toyId, variantId, "CHARACTER");
    }

    private static ToyView toy(int toyId, int variantId, String category) {
        return new ToyView(toyId, variantId, "Nom", "Name", "TRAP_TEAM",
                List.of("TRAP_TEAM"), "Feu", category, "OK", true, true, null, null, null);
    }

    @BeforeEach
    void setUp() {
        slots = mock(PortalSlotRepository.class);
        when(slots.findAll()).thenAnswer(call -> new ArrayList<>(table.values()));
        when(slots.save(org.mockito.ArgumentMatchers.<PortalSlot>any())).thenAnswer(call -> {
            PortalSlot slot = call.getArgument(0);
            table.put(slot.getSlotIndex(), slot);
            return slot;
        });
        doAnswer(call -> table.remove(call.<Integer>getArgument(0)))
                .when(slots).deleteBySlotIndex(anyInt());
        doAnswer(call -> {
            int toyId = call.getArgument(0);
            int variantId = call.getArgument(1);
            table.values().removeIf(s -> s.getToyId() == toyId && s.getVariantId() == variantId);
            return null;
        }).when(slots).deleteByToyIdAndVariantId(anyInt(), anyInt());
        doAnswer(call -> {
            table.clear();
            return null;
        }).when(slots).deleteAllInBatch();

        roster = mock(RosterService.class);
        when(roster.view(anyInt(), anyInt())).thenAnswer(
                call -> Optional.of(toy(call.getArgument(0), call.getArgument(1))));
    }

    private PortalService service(int slotCount) {
        // Le contenu des pieges ne joue aucun role dans les regles de pose : un service qui ne
        // trouve jamais de prisonnier suffit, et garde les cas de test lisibles.
        TrapContentService empty = mock(TrapContentService.class);
        return new PortalService(slots, roster, empty, slotCount);
    }

    private static List<Integer> occupiedIndexes(PortalView view) {
        return view.slots().stream().filter(s -> s.toy() != null)
                .map(PortalSlotView::index).toList();
    }

    @Test
    @DisplayName("un emplacement vide est une absence de ligne, pas une ligne vide")
    void emptyGridIsAllEmptySlots() {
        PortalView view = service(4).state();

        assertThat(view.slotCount()).isEqualTo(4);
        // Quatre emplacements de figurines, plus la serrure a piege.
        assertThat(view.slots()).hasSize(5);
        assertThat(view.trapSlotIndex()).isEqualTo(4);
        assertThat(view.slots().get(4).trapSlot()).isTrue();
        assertThat(view.slots().get(3).trapSlot()).isFalse();
        assertThat(view.slots()).allSatisfy(slot -> {
            assertThat(slot.toy()).isNull();
            assertThat(slot.placedAt()).isNull();
        });
    }

    @Test
    @DisplayName("reposer une figurine deja posee la DEPLACE, elle ne se dedouble pas")
    void placingAnAlreadyPlacedFigurineMovesIt() {
        PortalService portal = service(4);
        portal.place(0, 100, 0);

        PortalView after = portal.place(3, 100, 0);

        // Le piege : une figurine physique existe en un exemplaire. Sans le retrait prealable,
        // la meme apparaitrait sur deux emplacements — une disposition impossible a poser.
        assertThat(occupiedIndexes(after)).containsExactly(3);
        assertThat(after.slots().get(3).toy().toyId()).isEqualTo(100);
    }

    @Test
    @DisplayName("poser sur un emplacement occupe remplace l'occupant")
    void placingOnAnOccupiedSlotReplacesTheOccupant() {
        PortalService portal = service(4);
        portal.place(1, 100, 0);

        PortalView after = portal.place(1, 200, 0);

        assertThat(occupiedIndexes(after)).containsExactly(1);
        assertThat(after.slots().get(1).toy().toyId()).isEqualTo(200);
    }

    @Test
    @DisplayName("une identite inconnue du catalogue ET jamais recue est refusee")
    void unknownIdentityIsRejected() {
        when(roster.view(anyInt(), anyInt())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service(4).place(0, 999, 0))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
        assertThat(table).isEmpty();
    }

    @Test
    @DisplayName("un emplacement hors grille est refuse")
    void outOfGridSlotIsRejected() {
        assertThatThrownBy(() -> service(4).place(5, 100, 0))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");
        assertThatThrownBy(() -> service(4).place(-1, 100, 0))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @DisplayName("retrecir la grille ne fait pas disparaitre une figurine posee")
    void shrinkingTheGridStillShowsStrandedFigurines() {
        service(8).place(6, 100, 0);

        PortalView narrowed = service(4).state();

        // Sinon la figurine reste occupee en base, invisible : on ne pourrait plus la reposer
        // ailleurs (l'index unique la refuserait) ni comprendre pourquoi.
        assertThat(narrowed.slotCount()).isEqualTo(4);
        assertThat(narrowed.slots()).hasSize(7);
        assertThat(occupiedIndexes(narrowed)).containsExactly(6);
        assertThat(narrowed.slots().get(6).beyondGrid()).isTrue();
        assertThat(narrowed.slots().get(4).beyondGrid()).isFalse();   // la serrure a piege
        assertThat(narrowed.slots().get(3).beyondGrid()).isFalse();
    }

    @Test
    @DisplayName("liberer un emplacement hors grille reste possible")
    void strandedSlotCanStillBeCleared() {
        service(8).place(6, 100, 0);

        PortalView after = service(4).clearSlot(6);

        assertThat(occupiedIndexes(after)).isEmpty();
        assertThat(after.slots()).hasSize(5);
    }

    /** Marque ces identites comme des pieges pour le roster simule. */
    private void trapsAt(int... toyIds) {
        java.util.Set<Integer> traps = java.util.Arrays.stream(toyIds).boxed()
                .collect(java.util.stream.Collectors.toSet());
        when(roster.view(anyInt(), anyInt())).thenAnswer(call -> {
            int toyId = call.getArgument(0);
            return Optional.of(toy(toyId, call.getArgument(1),
                    traps.contains(toyId) ? "TRAP" : "CHARACTER"));
        });
    }

    @Test
    @DisplayName("un piege ne se pose que dans la serrure a piege")
    void trapOnlyGoesInTheTrapSlot() {
        trapsAt(700);
        PortalService portal = service(4);

        assertThatThrownBy(() -> portal.place(0, 700, 0))
                .isInstanceOf(PortalRejectedException.class)
                .hasMessage("trapGoesInTrapSlot");
        assertThat(occupiedIndexes(portal.state())).isEmpty();

        assertThat(occupiedIndexes(portal.place(4, 700, 0))).containsExactly(4);
    }

    @Test
    @DisplayName("la serrure a piege ne prend que des pieges")
    void trapSlotTakesNothingElse() {
        trapsAt(700);
        PortalService portal = service(4);

        assertThatThrownBy(() -> portal.place(4, 100, 0))
                .isInstanceOf(PortalRejectedException.class)
                .hasMessage("onlyTrapsInTrapSlot");
    }

    @Test
    @DisplayName("une seule serrure : le second piege remplace le premier sans effort")
    void secondTrapReplacesTheFirst() {
        trapsAt(700, 701);
        PortalService portal = service(4);
        portal.place(4, 700, 0);

        // Plus besoin d'une regle « un seul piege » : la serrure unique la tient toute seule.
        PortalView after = portal.place(4, 701, 0);

        assertThat(occupiedIndexes(after)).containsExactly(4);
        assertThat(after.slots().get(4).toy().toyId()).isEqualTo(701);
    }

    @Test
    @DisplayName("les figurines ne sont pas limitees en nombre")
    void charactersAreNotLimited() {
        trapsAt(700);
        PortalService portal = service(4);
        portal.place(4, 700, 0);
        portal.place(0, 100, 0);
        portal.place(1, 200, 0);

        assertThat(occupiedIndexes(portal.state())).containsExactly(0, 1, 4);
    }

    @Test
    @DisplayName("tout vider laisse une grille de la taille configuree")
    void clearAllEmptiesEverySlot() {
        PortalService portal = service(4);
        portal.place(0, 100, 0);
        portal.place(2, 200, 0);

        PortalView after = portal.clearAll();

        assertThat(occupiedIndexes(after)).isEmpty();
        assertThat(after.slots()).hasSize(5);
    }
}
