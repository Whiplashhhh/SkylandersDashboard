package net.vanbaelinghem.skylanders.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/**
 * Self-populating villain reference (SPEC.md §7.2). No external source maps a raw ID to a name:
 * the UI shows "Vilain inconnu (0x1E)" with an input, the user names it, the app persists it.
 */
@Entity
@Table(name = "villain")
public class Villain {

    @Id
    @Column(name = "raw_id")
    private int rawId;

    private String name;

    private String element;

    @Column(name = "named_by_user_at")
    private OffsetDateTime namedByUserAt;

    protected Villain() {}

    public Villain(int rawId) {
        this.rawId = rawId;
    }

    public int getRawId() { return rawId; }
    public String getName() { return name; }
    public String getElement() { return element; }
    public OffsetDateTime getNamedByUserAt() { return namedByUserAt; }

    public void nameIt(String name, String element, OffsetDateTime at) {
        this.name = name;
        this.element = element;
        this.namedByUserAt = at;
    }
}
