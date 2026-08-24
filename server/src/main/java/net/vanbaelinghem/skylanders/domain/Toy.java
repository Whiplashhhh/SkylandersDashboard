package net.vanbaelinghem.skylanders.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/** A figurine for which at least one file has been received. */
@Entity
@Table(name = "toy")
public class Toy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Functional identity key — the UID cannot serve (FORMAT.md §7.1). */
    @Column(name = "file_path", nullable = false, unique = true)
    private String filePath;

    /** Informational only. Deliberately not unique. */
    @Column(nullable = false)
    private byte[] uid;

    @Column(name = "toy_id", nullable = false)
    private int toyId;

    @Column(name = "variant_id", nullable = false)
    private int variantId;

    @Column(name = "game_folder", nullable = false)
    private String gameFolder;

    @Column(name = "element_folder", nullable = false)
    private String elementFolder;

    @Column(name = "category_folder", nullable = false)
    private String categoryFolder;

    @Column(name = "first_seen_at", nullable = false)
    private OffsetDateTime firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    private OffsetDateTime lastSeenAt;

    @Column(name = "is_present", nullable = false)
    private boolean present = true;

    /** The actual unlock moment. Monotonic: once set, never cleared (SPEC.md §7.1). */
    @Column(name = "first_played_at")
    private OffsetDateTime firstPlayedAt;

    @Column(name = "last_saved_at")
    private OffsetDateTime lastSavedAt;

    /** 1024 bytes of the first file received for this path; reference for snapshot deltas. */
    @Column(nullable = false)
    private byte[] baseline;

    protected Toy() {}

    public Toy(String filePath, byte[] uid, int toyId, int variantId,
               String gameFolder, String elementFolder, String categoryFolder,
               OffsetDateTime seenAt, byte[] baseline) {
        this.filePath = filePath;
        this.uid = uid;
        this.toyId = toyId;
        this.variantId = variantId;
        this.gameFolder = gameFolder;
        this.elementFolder = elementFolder;
        this.categoryFolder = categoryFolder;
        this.firstSeenAt = seenAt;
        this.lastSeenAt = seenAt;
        this.baseline = baseline;
    }

    public Long getId() { return id; }
    public String getFilePath() { return filePath; }
    public byte[] getUid() { return uid; }
    public int getToyId() { return toyId; }
    public int getVariantId() { return variantId; }
    public String getGameFolder() { return gameFolder; }
    public String getElementFolder() { return elementFolder; }
    public String getCategoryFolder() { return categoryFolder; }
    public OffsetDateTime getFirstSeenAt() { return firstSeenAt; }
    public OffsetDateTime getLastSeenAt() { return lastSeenAt; }
    public boolean isPresent() { return present; }
    public OffsetDateTime getFirstPlayedAt() { return firstPlayedAt; }
    public OffsetDateTime getLastSavedAt() { return lastSavedAt; }
    public byte[] getBaseline() { return baseline; }

    public void touch(OffsetDateTime at) {
        this.lastSeenAt = at;
        this.present = true;
    }

    public void refreshIdentity(int toyId, int variantId, byte[] uid,
                                String gameFolder, String elementFolder, String categoryFolder) {
        this.toyId = toyId;
        this.variantId = variantId;
        this.uid = uid;
        this.gameFolder = gameFolder;
        this.elementFolder = elementFolder;
        this.categoryFolder = categoryFolder;
    }

    /**
     * Records the unlock. Never moves backwards: a later snapshot that looked factory-default
     * must not "re-lock" a figurine already known to have been played (SPEC.md §7.1).
     */
    public void markPlayedAt(OffsetDateTime moment) {
        if (moment != null && (firstPlayedAt == null || moment.isBefore(firstPlayedAt))) {
            firstPlayedAt = moment;
        }
    }

    public void setLastSavedAt(OffsetDateTime moment) {
        if (moment != null && (lastSavedAt == null || moment.isAfter(lastSavedAt))) {
            lastSavedAt = moment;
        }
    }
}
