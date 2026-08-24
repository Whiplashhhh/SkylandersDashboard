package net.vanbaelinghem.skylanders.ingest;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Set;
import net.vanbaelinghem.skylanders.api.ScanRunView;
import net.vanbaelinghem.skylanders.domain.ScanRun;
import net.vanbaelinghem.skylanders.domain.ScanRunRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Scan lifecycle, deliberately placed under {@code /api/ingest} so it inherits the shared-token
 * filter: opening a scan is a write coming from the agent, not something the UI does.
 */
@RestController
@RequestMapping("/api/ingest/scan")
public class ScanLifecycleController {

    private static final Set<String> TRIGGERS = Set.of("STARTUP", "WATCH", "MANUAL", "SCHEDULED");

    public record StartRequest(String trigger) {}

    public record FinishRequest(int filesScanned, int filesChanged, int filesFailed) {}

    private final ScanRunRepository runs;

    public ScanLifecycleController(ScanRunRepository runs) {
        this.runs = runs;
    }

    @PostMapping
    @Transactional
    public ResponseEntity<ScanRunView> start(@RequestBody(required = false) StartRequest request) {
        String trigger = request == null || request.trigger() == null
                ? "MANUAL" : request.trigger().toUpperCase(Locale.ROOT);
        if (!TRIGGERS.contains(trigger)) {
            throw new IngestRejectedException(HttpStatus.BAD_REQUEST,
                    "trigger inconnu : " + trigger + " (attendu " + TRIGGERS + ")");
        }
        ScanRun run = runs.save(new ScanRun(trigger, OffsetDateTime.now()));
        return ResponseEntity.status(HttpStatus.CREATED).body(ScanRunView.of(run));
    }

    @PostMapping("/{id}/finish")
    @Transactional
    public ResponseEntity<ScanRunView> finish(@PathVariable Long id,
                                              @RequestBody FinishRequest request) {
        ScanRun run = runs.findById(id).orElse(null);
        if (run == null) {
            return ResponseEntity.notFound().build();
        }
        run.finish(OffsetDateTime.now(), request.filesScanned(),
                request.filesChanged(), request.filesFailed());
        return ResponseEntity.ok(ScanRunView.of(run));
    }

}
