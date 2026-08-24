package net.vanbaelinghem.skylanders.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/** Journal of ingestions (SPEC.md §7.1). Opened and closed explicitly by the agent. */
@Entity
@Table(name = "scan_run")
public class ScanRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;

    @Column(name = "finished_at")
    private OffsetDateTime finishedAt;

    @Column(name = "files_scanned", nullable = false)
    private int filesScanned;

    @Column(name = "files_changed", nullable = false)
    private int filesChanged;

    @Column(name = "files_failed", nullable = false)
    private int filesFailed;

    @Column(name = "trigger", nullable = false)
    private String trigger;

    protected ScanRun() {}

    public ScanRun(String trigger, OffsetDateTime startedAt) {
        this.trigger = trigger;
        this.startedAt = startedAt;
    }

    public Long getId() { return id; }
    public OffsetDateTime getStartedAt() { return startedAt; }
    public OffsetDateTime getFinishedAt() { return finishedAt; }
    public int getFilesScanned() { return filesScanned; }
    public int getFilesChanged() { return filesChanged; }
    public int getFilesFailed() { return filesFailed; }
    public String getTrigger() { return trigger; }

    public void finish(OffsetDateTime at, int scanned, int changed, int failed) {
        this.finishedAt = at;
        this.filesScanned = scanned;
        this.filesChanged = changed;
        this.filesFailed = failed;
    }
}
