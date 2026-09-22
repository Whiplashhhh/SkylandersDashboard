package net.vanbaelinghem.skylanders.bridge;

import static org.junit.jupiter.api.Assertions.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class BridgeServiceTest {
    static class TestClock extends Clock {
        long now = 10000;
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return Instant.ofEpochMilli(now); }
    }
    final TestClock clock = new TestClock();
    final BridgeService service = new BridgeService(clock);
    final String file = "a".repeat(64);
    BridgeService.Exchange input(String session, long revision, BridgeService.Result result) {
        return new BridgeService.Exchange(1, session, "READY",
                new BridgeService.State("cemu", revision, true, 16, List.of()),
                List.of(new BridgeService.FileEntry(file, "game/figure.sky")), null, result);
    }
    BridgeService.Request request(long revision) {
        return new BridgeService.Request(UUID.randomUUID().toString(), "loadFigure", "cemu", revision, file, null);
    }
    @Test void onlyAcknowledgementMarksAppliedAndDeliveryIsOnce() {
        service.exchange(input("pc", 0, null));
        var request = request(0);
        assertEquals("PENDING", service.submit(request).outcome().status());
        assertNotNull(service.exchange(input("pc", 0, null)).command());
        assertNull(service.exchange(input("pc", 0, null)).command());
        service.exchange(input("pc", 1, new BridgeService.Result(request.commandId(), true, "")));
        assertEquals("APPLIED", service.view().outcome().status());
        assertEquals(1, service.view().state().revision());
        assertFalse(service.submit(request).busy());
    }
    @Test void queuedAndAmbiguousCommandsExpireWithoutReplay() {
        service.exchange(input("pc", 0, null));
        service.submit(request(0));
        clock.now += 2100;
        assertEquals("EXPIRED", service.view().outcome().status());
        assertNull(service.exchange(input("pc", 0, null)).command());
        service.submit(request(0));
        service.exchange(input("pc", 0, null));
        clock.now += 2100;
        assertEquals("UNKNOWN", service.view().outcome().status());
        assertNull(service.exchange(input("pc", 1, null)).command());
    }
    @Test void reconnectDiscardsPendingAndNativeChangeInvalidatesQueuedCommand() {
        service.exchange(input("pc", 0, null));
        service.submit(request(0));
        assertNull(service.exchange(input("new-pc", 0, null)).command());
        assertEquals("UNKNOWN", service.view().outcome().status());
        service.submit(request(0));
        assertNull(service.exchange(input("new-pc", 1, null)).command());
        assertEquals("STALE_STATE", service.view().outcome().error());
    }
    @Test void missingFileInvalidSlotAndOfflineAreRejected() {
        assertThrows(ResponseStatusException.class, () -> service.submit(request(0)));
        service.exchange(input("pc", 0, null));
        assertThrows(ResponseStatusException.class, () -> service.submit(new BridgeService.Request(
                UUID.randomUUID().toString(), "loadFigure", "cemu", 0, "unknown", null)));
        assertThrows(ResponseStatusException.class, () -> service.submit(new BridgeService.Request(
                UUID.randomUUID().toString(), "removeFigure", "cemu", 0, null, 16)));
        clock.now += 6000;
        assertEquals("CONNECTOR_UNAVAILABLE", service.view().availability());
        assertThrows(ResponseStatusException.class, () -> service.submit(request(0)));
    }
    @Test void unavailableCemuPreservesLastObservedStateWithoutAllowingCommands() {
        service.exchange(input("pc", 4, null));
        service.exchange(new BridgeService.Exchange(1, "pc", "CEMU_UNAVAILABLE", null, List.of(), null, null));
        assertEquals(4, service.view().state().revision());
        assertEquals("CEMU_UNAVAILABLE", service.view().availability());
        assertThrows(ResponseStatusException.class, () -> service.submit(request(4)));
        assertThrows(ResponseStatusException.class, () -> service.exchange(
                new BridgeService.Exchange(1, "pc", "READY", null, List.of(), null, null)));
    }


    @Test
    void inventoryTravelsOnlyWhenItChanges() {
        service.exchange(input("pc", 0, null));
        var known = BridgeService.digest(List.of(new BridgeService.FileEntry(file, "game/figure.sky")));

        // Sondage sans liste, empreinte connue : le serveur garde l'inventaire recu.
        var delivery = service.exchange(new BridgeService.Exchange(1, "pc", "READY",
                new BridgeService.State("cemu", 0, true, 16, List.of()), null, known, null));
        assertFalse(delivery.needFiles());
        assertEquals(1, service.view().files().size());

        // Empreinte inconnue : le serveur reclame la liste et n'agit plus sur une liste perimee.
        delivery = service.exchange(new BridgeService.Exchange(1, "pc", "READY",
                new BridgeService.State("cemu", 0, true, 16, List.of()), null, "b".repeat(64), null));
        assertTrue(delivery.needFiles());
        assertEquals(List.of(), service.view().files());
    }

    @Test
    void reconnectionForcesAFreshInventory() {
        service.exchange(input("pc", 0, null));
        var known = BridgeService.digest(List.of(new BridgeService.FileEntry(file, "game/figure.sky")));
        var delivery = service.exchange(new BridgeService.Exchange(1, "autre-pc", "READY",
                new BridgeService.State("cemu", 0, true, 16, List.of()), null, known, null));
        assertTrue(delivery.needFiles());
        assertEquals(List.of(), service.view().files());
    }
}
