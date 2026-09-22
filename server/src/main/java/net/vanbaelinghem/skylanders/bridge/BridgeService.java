package net.vanbaelinghem.skylanders.bridge;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Ephemeral single-PC control channel. Restarting never replays a command. */
@Service
public class BridgeService {
    public record FileEntry(String id, String relativePath) {}
    public record Slot(int index, int toyId, int variantId, String fileId) {}
    public record State(String epoch, long revision, boolean enabled, int capacity, List<Slot> slots) {}
    public record Result(String commandId, boolean ok, String error) {}
    public record Exchange(int version, String session, String availability, State state,
                           List<FileEntry> files, String filesDigest, Result result) {}
    public record Command(String commandId, String session, String command, String epoch,
                          long expectedRevision, long expiresAt, String fileId, Integer slot) {}
    public record Request(String commandId, String command, String epoch, long expectedRevision,
                          String fileId, Integer slot) {}
    public record Outcome(String commandId, String status, String error) {}
    public record View(String availability, State state, List<FileEntry> files, Outcome outcome, boolean busy) {}
    /** {@code needFiles} asks the connector to republish its inventory on the next exchange. */
    public record Delivery(Command command, boolean needFiles) {}

    private final Clock clock;
    private String session;
    private long seenAt;
    private String availability = "CONNECTOR_UNAVAILABLE";
    private State state;
    private List<FileEntry> files = List.of();
    private String filesDigest;
    private Command pending;
    private boolean delivered;
    private Outcome outcome;

    public BridgeService() { this(Clock.systemUTC()); }
    BridgeService(Clock clock) { this.clock = clock; }

    private void expire() {
        if (pending != null && clock.millis() >= pending.expiresAt()) {
            outcome = new Outcome(pending.commandId(), delivered ? "UNKNOWN" : "EXPIRED", "EXPIRED");
            pending = null;
        }
        if (clock.millis() - seenAt > 5000) availability = "CONNECTOR_UNAVAILABLE";
    }

    public synchronized View view() {
        expire();
        return new View(availability, state, files, outcome, pending != null);
    }

    public synchronized Delivery exchange(Exchange input) {
        expire();
        // L'inventaire ne circule que lorsqu'il change : 702 fichiers pesent 104 Ko, et le
        // connecteur sonde deux fois par seconde. Un sondage sans `files` porte seulement son
        // empreinte ; le serveur reclame la liste des qu'elle ne correspond plus a la sienne.
        if (input.version() != 1 || input.session() == null || input.session().length() > 128
                || input.session().isBlank() || (input.files() != null && input.files().size() > 10000)
                || (input.files() == null && input.filesDigest() == null)
                || (input.filesDigest() != null && !input.filesDigest().matches("[a-f0-9]{64}"))) {
            throw rejected(HttpStatus.BAD_REQUEST, "INVALID_EXCHANGE");
        }
        if (input.availability() == null || !List.of("READY", "CEMU_UNAVAILABLE", "PORTAL_DISABLED").contains(input.availability()))
            throw rejected(HttpStatus.BAD_REQUEST, "INVALID_AVAILABILITY");
        validate(input.state());
        if ("READY".equals(input.availability()) && (input.state() == null || !input.state().enabled()))
            throw rejected(HttpStatus.BAD_REQUEST, "INVALID_STATE");
        if (input.files() != null) {
            for (FileEntry file : input.files()) {
                if (file == null || file.id() == null || !file.id().matches("[a-f0-9]{64}")
                        || file.relativePath() == null || file.relativePath().length() > 4096)
                    throw rejected(HttpStatus.BAD_REQUEST, "INVALID_FILE");
            }
        }
        boolean newSession = session != null && !session.equals(input.session());
        if (newSession) {
            // A reconnect starts a fresh command session, even if Cemu survived the outage.
            if (pending != null) outcome = new Outcome(pending.commandId(), "UNKNOWN", "SESSION_CHANGED");
            pending = null;
            files = List.of();
            filesDigest = null;
        }
        session = input.session();
        seenAt = clock.millis();
        availability = input.availability();
        if (input.state() != null) state = input.state();
        // L'empreinte est recalculee ici : celle annoncee par le connecteur sert a comparer,
        // jamais a decrire un contenu que le serveur n'a pas vu.
        if (input.files() != null) {
            files = List.copyOf(input.files());
            filesDigest = digest(files);
        }
        boolean needFiles = input.files() == null && !Objects.equals(filesDigest, input.filesDigest());
        if (needFiles) {
            // Inventaire inconnu : ne pas commander a l'aveugle sur une liste perimee.
            files = List.of();
            filesDigest = null;
        }
        if (pending != null && input.result() != null && pending.commandId().equals(input.result().commandId())) {
            outcome = new Outcome(pending.commandId(), input.result().ok() ? "APPLIED" : "REJECTED", input.result().error());
            pending = null;
        }
        if (pending == null || delivered) return new Delivery(null, needFiles);
        if (!ready() || !pending.epoch().equals(state.epoch()) || pending.expectedRevision() != state.revision()) {
            outcome = new Outcome(pending.commandId(), "REJECTED", "STALE_STATE");
            pending = null;
            return new Delivery(null, needFiles);
        }
        delivered = true;
        return new Delivery(pending, needFiles);
    }

    public synchronized View submit(Request request) {
        expire();
        try { UUID.fromString(request.commandId()); }
        catch (RuntimeException e) { throw rejected(HttpStatus.BAD_REQUEST, "INVALID_COMMAND_ID"); }
        if (outcome != null && outcome.commandId().equals(request.commandId())) return view();
        if (pending != null) {
            if (pending.commandId().equals(request.commandId())) return view();
            throw rejected(HttpStatus.CONFLICT, "COMMAND_IN_PROGRESS");
        }
        if (!ready()) throw rejected(HttpStatus.SERVICE_UNAVAILABLE, "CEMU_UNAVAILABLE");
        if (!state.epoch().equals(request.epoch()) || state.revision() != request.expectedRevision())
            throw rejected(HttpStatus.CONFLICT, "STALE_STATE");
        if ("loadFigure".equals(request.command())) {
            if (files.stream().noneMatch(f -> f.id().equals(request.fileId())))
                throw rejected(HttpStatus.NOT_FOUND, "FILE_UNAVAILABLE");
        } else if ("removeFigure".equals(request.command())) {
            if (request.slot() == null || request.slot() < 0 || request.slot() >= state.capacity())
                throw rejected(HttpStatus.BAD_REQUEST, "INVALID_SLOT");
        } else if (!"clearAll".equals(request.command())) {
            throw rejected(HttpStatus.BAD_REQUEST, "UNKNOWN_COMMAND");
        }
        pending = new Command(request.commandId(), session, request.command(), state.epoch(), state.revision(),
                clock.millis() + 2000, request.fileId(), request.slot());
        delivered = false;
        outcome = new Outcome(pending.commandId(), "PENDING", null);
        return view();
    }

    private boolean ready() {
        return "READY".equals(availability) && state != null && state.enabled();
    }

    private static void validate(State state) {
        if (state == null) return;
        if (state.epoch() == null || state.epoch().isBlank() || state.epoch().length() > 128
                || state.revision() < 0 || state.capacity() != 16 || state.slots() == null
                || state.slots().size() > state.capacity()) throw rejected(HttpStatus.BAD_REQUEST, "INVALID_STATE");
        var indices = new java.util.HashSet<Integer>();
        for (Slot slot : state.slots()) {
            if (slot == null || slot.index() < 0 || slot.index() >= state.capacity() || !indices.add(slot.index()))
                throw rejected(HttpStatus.BAD_REQUEST, "INVALID_SLOT");
        }
    }

    private static ResponseStatusException rejected(HttpStatus status, String reason) {
        return new ResponseStatusException(status, reason);
    }

    /** Empreinte de l'inventaire : identifiants tries, un par ligne. Le connecteur calcule la meme. */
    static String digest(List<FileEntry> files) {
        try {
            var input = files.stream().map(FileEntry::id).sorted().reduce("", (a, b) -> a + b + "\n");
            var bytes = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
            var text = new StringBuilder(64);
            for (byte value : bytes) text.append(String.format("%02x", value));
            return text.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
