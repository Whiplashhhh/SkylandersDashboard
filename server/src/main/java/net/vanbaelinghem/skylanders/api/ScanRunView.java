package net.vanbaelinghem.skylanders.api;

import java.time.OffsetDateTime;
import net.vanbaelinghem.skylanders.domain.ScanRun;

public record ScanRunView(Long id, OffsetDateTime startedAt, OffsetDateTime finishedAt,
                          int filesScanned, int filesChanged, int filesFailed, String trigger) {

    public static ScanRunView of(ScanRun run) {
        return new ScanRunView(run.getId(), run.getStartedAt(), run.getFinishedAt(),
                run.getFilesScanned(), run.getFilesChanged(), run.getFilesFailed(),
                run.getTrigger());
    }
}
