package net.vanbaelinghem.skylanders.api;

import java.util.List;
import net.vanbaelinghem.skylanders.domain.ScanRunRepository;
import org.springframework.data.domain.Limit;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read-only history of ingestions (SPEC.md §9). */
@RestController
@RequestMapping("/api/scans")
@Transactional(readOnly = true)
public class ScanController {

    private final ScanRunRepository runs;

    public ScanController(ScanRunRepository runs) {
        this.runs = runs;
    }

    @GetMapping
    public List<ScanRunView> list(
            @RequestParam(defaultValue = "50") int limit) {
        return runs.findByOrderByStartedAtDesc(Limit.of(Math.max(1, Math.min(limit, 500))))
                .stream()
                .map(ScanRunView::of)
                .toList();
    }
}
