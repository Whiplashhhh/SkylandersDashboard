package net.vanbaelinghem.skylanders.bridge;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import net.vanbaelinghem.skylanders.api.*;
import net.vanbaelinghem.skylanders.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class DirectPortalServiceTest {
    final BridgeServiceTest.TestClock clock = new BridgeServiceTest.TestClock();
    final BridgeService bridge = new BridgeService(clock);
    final RosterService roster = mock(RosterService.class);
    final ToyRepository toys = mock(ToyRepository.class);
    final DirectPortalService portal = new DirectPortalService(bridge, roster, toys, clock);
    final String a = "a".repeat(64), b = "b".repeat(64), c = "c".repeat(64);
    List<BridgeService.FileEntry> files = new ArrayList<>();
    String session = "pc";
    long revision;
    List<BridgeService.Slot> observed = new ArrayList<>();

    @BeforeEach void setup() {
        model(1, "FIGURE", a, "game/one.sky");
        model(2, "FIGURE", b, "game/two.sky");
        model(3, "TRAP", c, "game/trap.sky");
        exchange(null);
    }
    void model(int id, String category, String file, String path) {
        when(roster.view(id, 0)).thenReturn(Optional.of(new ToyView(id, 0, "Toy " + id, "Toy " + id,
                "TRAP_TEAM", List.of(), "FIRE", category, null, true, false, null, null, null)));
        var toy = mock(Toy.class);
        when(toy.getFilePath()).thenReturn(path);
        when(toys.findByToyIdAndVariantId(id, 0)).thenReturn(List.of(toy));
        files.add(new BridgeService.FileEntry(file, path));
    }
    BridgeService.Delivery exchange(BridgeService.Result result) {
        return portal.exchange(new BridgeService.Exchange(1, session, "READY",
                new BridgeService.State("cemu", revision, true, 16, List.copyOf(observed)), files, result));
    }
    DirectPortalService.Request request(String command, Integer index, Integer toy, String file) {
        var view = portal.view();
        return new DirectPortalService.Request(UUID.randomUUID().toString(), command, "cemu", revision,
                view.layoutRevision(), index, toy, toy == null ? null : 0, file);
    }
    BridgeService.Command place(int index, int toy, String file) {
        portal.submit(request("place", index, toy, file));
        return exchange(null).command();
    }
    void ack(BridgeService.Command command) {
        exchange(new BridgeService.Result(command.commandId(), true, ""));
    }
    DirectPortalService.SlotView slot(int index) {
        return portal.view().slots().stream().filter(s -> s.index() == index).findFirst().orElseThrow();
    }
    @Test void droppedFigureUsesObservedInternalIndexAndDoesNotUnlock() {
        var command = place(7, 1, a);
        assertEquals("loadFigure", command.command()); assertNull(slot(7).toy());
        assertTrue(portal.view().busy());
        observed.add(new BridgeService.Slot(13, 1, 0, a)); revision++; ack(command);
        assertEquals(13, slot(7).cemuIndex()); assertEquals(1, slot(7).toy().toyId());
        assertFalse(slot(7).toy().unlocked());
        portal.submit(request("remove", 7, null, null));
        assertEquals(13, exchange(null).command().slot());
    }
    @Test void movingAnAlreadyLoadedCopyDoesNotReloadIt() {
        observed.add(new BridgeService.Slot(12, 1, 0, a)); revision++; exchange(null);
        var request = request("place", 8, 1, a); portal.submit(request);
        assertNull(exchange(null).command()); assertEquals(12, slot(8).cemuIndex()); assertNull(slot(0).toy());
        portal.submit(request); assertNull(exchange(null).command());
    }
    @Test void replacementWaitsForRemovalBeforeLoadingAndShowsOnlyObservedState() {
        observed.add(new BridgeService.Slot(14, 1, 0, a)); revision++; exchange(null);
        var removal = place(0, 2, b);
        assertEquals("removeFigure", removal.command()); assertEquals(14, removal.slot());
        assertEquals(1, slot(0).toy().toyId());
        observed.clear(); revision++; ack(removal);
        var load = exchange(null).command();
        assertEquals("loadFigure", load.command()); assertEquals(b, load.fileId()); assertNull(slot(0).toy());
        observed.add(new BridgeService.Slot(6, 2, 0, b)); revision++; ack(load);
        assertEquals(2, slot(0).toy().toyId()); assertEquals("APPLIED", portal.view().outcome().status());
    }
    @Test void invalidReplacementLeavesTheActualEmptySlotAndReportsFailure() {
        observed.add(new BridgeService.Slot(14, 1, 0, a)); revision++; exchange(null);
        var removal = place(0, 2, b); observed.clear(); revision++; ack(removal);
        var load = exchange(null).command();
        exchange(new BridgeService.Result(load.commandId(), false, "INVALID_DUMP"));
        assertNull(slot(0).toy()); assertEquals("INVALID_DUMP", portal.view().outcome().error());
        assertNull(exchange(null).command());
    }
    @Test void reconnectionBetweenReplacementStepsNeverReplaysPlacement() {
        observed.add(new BridgeService.Slot(10, 1, 0, a)); revision++; exchange(null);
        var removal = place(0, 2, b); observed.clear(); revision++; session = "new-pc"; ack(removal);
        assertEquals("UNKNOWN", portal.view().outcome().status());
        assertNull(exchange(null).command()); assertNull(slot(0).toy());
    }
    @Test void nativeEditBetweenStepsCancelsTheRemainingPlacement() {
        observed.add(new BridgeService.Slot(10, 1, 0, a)); revision++; exchange(null);
        var removal = place(0, 2, b);
        observed.clear(); observed.add(new BridgeService.Slot(11, 3, 0, c)); revision += 2; ack(removal);
        assertEquals("STALE_STATE", portal.view().outcome().error());
        assertNull(exchange(null).command()); assertEquals(11, slot(9).cemuIndex());
    }
    @Test void trapRulesExactCopyResolutionAndMissingFilesAreCheckedBeforeRemoval() {
        observed.add(new BridgeService.Slot(7, 1, 0, a)); revision++; exchange(null);
        assertEquals("WRONG_SLOT_TYPE", assertThrows(ResponseStatusException.class,
                () -> portal.submit(request("place", 0, 3, c))).getReason());
        assertEquals("FILE_UNAVAILABLE", assertThrows(ResponseStatusException.class,
                () -> portal.submit(request("place", 0, 2, a))).getReason());
        var duplicate = mock(Toy.class); when(duplicate.getFilePath()).thenReturn("other/one.sky");
        var original = toys.findByToyIdAndVariantId(1, 0).getFirst();
        when(toys.findByToyIdAndVariantId(1, 0)).thenReturn(List.of(original, duplicate));
        files.add(new BridgeService.FileEntry("d".repeat(64), "other/one.sky")); exchange(null);
        assertEquals("CHOOSE_COPY", assertThrows(ResponseStatusException.class,
                () -> portal.submit(request("place", 1, 1, null))).getReason());
        assertNull(exchange(null).command()); assertEquals(1, slot(0).toy().toyId());
    }
    @Test void graphicalMoveInvalidatesAnotherWindowsOldLayout() {
        observed.add(new BridgeService.Slot(12, 1, 0, a)); revision++; exchange(null);
        var stale = request("remove", 0, null, null); portal.submit(request("place", 4, 1, a));
        assertEquals("STALE_STATE", assertThrows(ResponseStatusException.class, () -> portal.submit(stale)).getReason());
        assertNull(exchange(null).command());
    }
    @Test void nativeOverflowRemainsVisibleAndClearNeedsNoIndex() {
        for (int i = 0; i < 12; i++) observed.add(new BridgeService.Slot(i, 1, 0, null));
        revision++; exchange(null);
        assertEquals(12, portal.view().slots().stream().filter(s -> s.toy() != null).count());
        assertEquals(3, portal.view().slots().stream().filter(DirectPortalService.SlotView::beyondGrid).count());
        portal.submit(request("clear", null, null, null));
        assertEquals("clearAll", exchange(null).command().command());
    }
    @Test void expiredActionNeverLoadsAfterALateRemovalAcknowledgement() {
        observed.add(new BridgeService.Slot(10, 1, 0, a)); revision++; exchange(null);
        var removal = place(0, 2, b); clock.now += 6000;
        observed.clear(); revision++; ack(removal);
        assertEquals("UNKNOWN", portal.view().outcome().status()); assertNull(exchange(null).command());
    }
}
