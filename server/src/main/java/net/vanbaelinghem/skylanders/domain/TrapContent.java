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
@Table(name = "trap_content")
public class TrapContent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "toy_pk", nullable = false)
    private Toy toy;

    @Column(name = "captured_at", nullable = false)
    private OffsetDateTime capturedAt;

    @Column(name = "villain_raw_id", nullable = false)
    private int villainRawId;

    @Column(name = "is_empty", nullable = false)
    private boolean empty;

    protected TrapContent() {}

    public TrapContent(Toy toy, OffsetDateTime capturedAt, int villainRawId, boolean empty) {
        this.toy = toy;
        this.capturedAt = capturedAt;
        this.villainRawId = villainRawId;
        this.empty = empty;
    }

    public Long getId() { return id; }
    public Toy getToy() { return toy; }
    public OffsetDateTime getCapturedAt() { return capturedAt; }
    public int getVillainRawId() { return villainRawId; }
    public boolean isEmpty() { return empty; }
}
