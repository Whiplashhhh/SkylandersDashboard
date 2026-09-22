package net.vanbaelinghem.skylanders.bridge;

import java.time.Clock;
import java.util.*;
import net.vanbaelinghem.skylanders.api.RosterService;
import net.vanbaelinghem.skylanders.api.ToyView;
import net.vanbaelinghem.skylanders.domain.ToyRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Live graphical layout. Only observed occupants are displayed; no persisted layout is replayed. */
@Service
public class DirectPortalService {
    public record SlotView(int index, Integer cemuIndex, String fileId, ToyView toy,
                           boolean trapSlot, boolean beyondGrid) {}
    public record View(String availability, BridgeService.State state, List<BridgeService.FileEntry> files,
                       int slotCount, int trapSlotIndex, List<SlotView> slots, long layoutRevision,
                       boolean busy, Integer pendingSlot, BridgeService.Outcome outcome) {}
    public record Request(String commandId, String command, String epoch, long expectedRevision,
                          long layoutRevision, Integer index, Integer toyId, Integer variantId, String fileId) {}
    private record Action(Request request, String fileId, BridgeService.Slot moving, String wireId,
                          String stage, long revision, long deadline) {}

    private final BridgeService bridge;
    private final RosterService roster;
    private final ToyRepository toys;
    private final Clock clock;
    private final Map<Integer, BridgeService.Slot> layout = new TreeMap<>();
    private final LinkedHashMap<String, BridgeService.Outcome> outcomes = new LinkedHashMap<>();
    private String epoch, session;
    private long layoutRevision;
    private Action action;
    private BridgeService.Outcome outcome;

    @org.springframework.beans.factory.annotation.Autowired
    public DirectPortalService(BridgeService bridge, RosterService roster, ToyRepository toys) {
        this(bridge, roster, toys, Clock.systemUTC());
    }
    DirectPortalService(BridgeService bridge, RosterService roster, ToyRepository toys, Clock clock) {
        this.bridge = bridge; this.roster = roster; this.toys = toys; this.clock = clock;
    }

    public synchronized BridgeService.Delivery exchange(BridgeService.Exchange input) {
        // Stop a multi-step replacement before a new connector session can advance it.
        if (session != null && !session.equals(input.session()) && action != null)
            finish("UNKNOWN", "SESSION_CHANGED");
        var delivery = bridge.exchange(input);
        session = input.session();
        advance();
        return delivery;
    }

    public synchronized View view() {
        advance();
        var live = bridge.view();
        reconcile(live.state());
        List<SlotView> slots = new ArrayList<>();
        for (int i = 0; i < 10; i++) slots.add(slotView(i, layout.get(i)));
        layout.forEach((i, slot) -> { if (i >= 10) slots.add(slotView(i, slot)); });
        return new View(live.availability(), live.state(), live.files(), 9, 9, slots, layoutRevision,
                action != null || live.busy(), action == null ? null : action.request().index(), outcome);
    }

    private SlotView slotView(int index, BridgeService.Slot slot) {
        return new SlotView(index, slot == null ? null : slot.index(), slot == null ? null : slot.fileId(),
                slot == null ? null : toy(slot), index == 9, index >= 10);
    }
    private ToyView toy(BridgeService.Slot slot) {
        return roster.view(slot.toyId(), slot.variantId()).orElseGet(() -> new ToyView(
                slot.toyId(), slot.variantId(), "#" + slot.toyId() + " / " + slot.variantId(),
                "#" + slot.toyId(), null, List.of(), null, null, null, false, false, null, null, null));
    }
    private void reconcile(BridgeService.State state) {
        if (state == null) return;
        var before = new TreeMap<>(layout);
        if (!Objects.equals(epoch, state.epoch())) { layout.clear(); epoch = state.epoch(); }
        layout.entrySet().removeIf(e -> !state.slots().contains(e.getValue()));
        for (var slot : state.slots()) {
            if (layout.containsValue(slot)) continue;
            boolean trap = "TRAP".equals(toy(slot).category());
            int target = trap && !layout.containsKey(9) ? 9 : -1;
            if (!trap) for (int i = 0; i < 9; i++) if (!layout.containsKey(i)) { target = i; break; }
            if (target < 0) { target = 10; while (layout.containsKey(target)) target++; }
            layout.put(target, slot);
        }
        if (!before.equals(layout)) layoutRevision++;
    }
    private void position(int index, BridgeService.Slot slot) {
        layout.values().removeIf(slot::equals);
        layout.put(index, slot);
        layoutRevision++;
    }

    public synchronized View submit(Request request) {
        view();
        try { UUID.fromString(request.commandId()); }
        catch (RuntimeException e) { throw reject("INVALID_COMMAND_ID"); }
        if (outcomes.containsKey(request.commandId())) return view();
        if (action != null && action.request().commandId().equals(request.commandId())) return view();
        var live = bridge.view();
        if (action != null || live.busy()) throw reject("COMMAND_IN_PROGRESS");
        if (!"READY".equals(live.availability())) throw reject("CEMU_UNAVAILABLE");
        if (!Objects.equals(request.epoch(), live.state().epoch())
                || request.expectedRevision() != live.state().revision()
                || request.layoutRevision() != layoutRevision) throw reject("STALE_STATE");
        if (!List.of("place", "remove", "clear").contains(Objects.toString(request.command(), "")))
            throw reject("UNKNOWN_COMMAND");
        if (!"clear".equals(request.command()) && (request.index() == null || request.index() < 0
                || request.index() >= 26 || ("place".equals(request.command()) && request.index() >= 10)))
            throw reject("INVALID_SLOT");
        var occupant = request.index() == null ? null : layout.get(request.index());
        String fileId = null;
        BridgeService.Slot moving = null;
        if ("place".equals(request.command())) {
            if (request.toyId() == null || request.variantId() == null) throw reject("INVALID_REQUEST");
            var model = roster.view(request.toyId(), request.variantId()).orElseThrow(() -> reject("FILE_UNAVAILABLE"));
            if ((request.index() == 9) != "TRAP".equals(model.category())) throw reject("WRONG_SLOT_TYPE");
            var paths = toys.findByToyIdAndVariantId(request.toyId(), request.variantId()).stream()
                    .map(t -> t.getFilePath()).toList();
            var candidates = live.files().stream().filter(f -> paths.contains(f.relativePath())).toList();
            if (request.fileId() != null) candidates = candidates.stream().filter(f -> f.id().equals(request.fileId())).toList();
            if (candidates.isEmpty()) throw reject("FILE_UNAVAILABLE");
            if (candidates.size() != 1) throw reject("CHOOSE_COPY");
            fileId = candidates.getFirst().id();
            String selected = fileId;
            moving = live.state().slots().stream().filter(s -> selected.equals(s.fileId())).findFirst().orElse(null);
            if (moving != null && moving.equals(occupant)) {
                record(request.commandId(), "APPLIED", null); return view();
            }
        }
        action = new Action(request, fileId, moving, null, null, live.state().revision(), clock.millis() + 5000);
        outcome = new BridgeService.Outcome(request.commandId(), "PENDING", null);
        try {
            if ("clear".equals(request.command())) send("clearAll", null);
            else if (occupant != null) send("removeFigure", occupant.index());
            else if ("remove".equals(request.command())) finish("APPLIED", null);
            else placeAfterRemoval();
        } catch (ResponseStatusException e) { finish("REJECTED", e.getReason()); }
        return view();
    }

    private void send(String command, Integer slot) {
        var state = bridge.view().state();
        String wireId = UUID.randomUUID().toString();
        bridge.submit(new BridgeService.Request(wireId, command, state.epoch(), state.revision(), action.fileId(), slot));
        action = new Action(action.request(), action.fileId(), action.moving(), wireId, command,
                state.revision(), action.deadline());
    }
    private void placeAfterRemoval() {
        if (action.moving() != null) {
            if (!bridge.view().state().slots().contains(action.moving())) { finish("REJECTED", "STALE_STATE"); return; }
            position(action.request().index(), action.moving());
            finish("APPLIED", null);
        } else send("loadFigure", null);
    }
    private void advance() {
        if (action == null) return;
        var live = bridge.view();
        if (clock.millis() >= action.deadline()) { finish("UNKNOWN", "EXPIRED"); return; }
        if (!"READY".equals(live.availability()) || !Objects.equals(action.request().epoch(), live.state().epoch())) {
            finish("UNKNOWN", "CEMU_UNAVAILABLE"); return;
        }
        if (live.busy()) return;
        var result = live.outcome();
        if (result == null || !Objects.equals(action.wireId(), result.commandId())) {
            finish("UNKNOWN", "STALE_STATE"); return;
        }
        if (!"APPLIED".equals(result.status())) { finish(result.status(), result.error()); return; }
        // Native edits between steps invalidate the replacement instead of being overwritten.
        if ("removeFigure".equals(action.stage()) && "place".equals(action.request().command())) {
            if (live.state().revision() != action.revision() + 1) { finish("REJECTED", "STALE_STATE"); return; }
            reconcile(live.state());
            try { placeAfterRemoval(); } catch (ResponseStatusException e) { finish("REJECTED", e.getReason()); }
        } else if ("loadFigure".equals(action.stage())) {
            var loaded = live.state().slots().stream().filter(s -> action.fileId().equals(s.fileId())).findFirst();
            if (loaded.isEmpty()) { finish("UNKNOWN", "STALE_STATE"); return; }
            reconcile(live.state());
            position(action.request().index(), loaded.get());
            finish("APPLIED", null);
        } else finish("APPLIED", null);
    }
    private void finish(String status, String error) {
        record(action.request().commandId(), status, error);
        action = null;
    }
    private void record(String id, String status, String error) {
        outcome = new BridgeService.Outcome(id, status, error);
        outcomes.put(id, outcome);
        if (outcomes.size() > 128) outcomes.remove(outcomes.keySet().iterator().next());
    }
    private static ResponseStatusException reject(String code) {
        return new ResponseStatusException(HttpStatus.CONFLICT, code);
    }
}
