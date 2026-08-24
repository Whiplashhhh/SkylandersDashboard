package net.vanbaelinghem.skylanders.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "toy_snapshot")
public class ToySnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "toy_pk", nullable = false)
    private Toy toy;

    @Column(name = "captured_at", nullable = false)
    private OffsetDateTime capturedAt;

    @Column(name = "content_hash", nullable = false)
    private byte[] contentHash;

    /** Blocks differing from {@link Toy#getBaseline()}; see {@code DeltaCodec}. */
    @Column(nullable = false)
    private byte[] delta;

    private Integer xp;
    private Integer level;
    private Integer gold;

    @Column(name = "upgrades_bitfield")
    private Integer upgradesBitfield;

    private String nickname;

    @Column(name = "playtime_seconds")
    private Integer playtimeSeconds;

    @Column(name = "saved_at")
    private OffsetDateTime savedAt;

    @Column(name = "parse_status", nullable = false)
    private String parseStatus;

    @Column(name = "has_play_evidence", nullable = false)
    private boolean hasPlayEvidence;

    protected ToySnapshot() {}

    public ToySnapshot(Toy toy, OffsetDateTime capturedAt, byte[] contentHash, byte[] delta,
                       Integer xp, Integer level, Integer gold, Integer upgradesBitfield,
                       String nickname, Integer playtimeSeconds, OffsetDateTime savedAt,
                       String parseStatus, boolean hasPlayEvidence) {
        this.toy = toy;
        this.capturedAt = capturedAt;
        this.contentHash = contentHash;
        this.delta = delta;
        this.xp = xp;
        this.level = level;
        this.gold = gold;
        this.upgradesBitfield = upgradesBitfield;
        this.nickname = nickname;
        this.playtimeSeconds = playtimeSeconds;
        this.savedAt = savedAt;
        this.parseStatus = parseStatus;
        this.hasPlayEvidence = hasPlayEvidence;
    }

    public Long getId() { return id; }
    public Toy getToy() { return toy; }
    public OffsetDateTime getCapturedAt() { return capturedAt; }
    public byte[] getContentHash() { return contentHash; }
    public byte[] getDelta() { return delta; }
    public Integer getXp() { return xp; }
    public Integer getLevel() { return level; }
    public Integer getGold() { return gold; }
    public Integer getUpgradesBitfield() { return upgradesBitfield; }
    public String getNickname() { return nickname; }
    public Integer getPlaytimeSeconds() { return playtimeSeconds; }
    public OffsetDateTime getSavedAt() { return savedAt; }
    public String getParseStatus() { return parseStatus; }
    public boolean isHasPlayEvidence() { return hasPlayEvidence; }
}
