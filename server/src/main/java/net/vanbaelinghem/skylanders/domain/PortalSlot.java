package net.vanbaelinghem.skylanders.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/**
 * One occupied slot of the on-screen portal layout.
 *
 * <p>Holds the canonical identity — (toy ID, variant ID), CLAUDE.md invariant 3 — and not a
 * {@code toy} row: one physical figurine maps to as many {@code toy} rows as it has files, one
 * per game folder, and the UI never sees those.
 *
 * <p>An empty slot is the <em>absence</em> of a row, which is why every column is mandatory.
 */
@Entity
@Table(name = "portal_slot")
public class PortalSlot {

    @Id
    @Column(name = "slot_index", nullable = false)
    private int slotIndex;

    @Column(name = "toy_id", nullable = false)
    private int toyId;

    @Column(name = "variant_id", nullable = false)
    private int variantId;

    @Column(name = "placed_at", nullable = false)
    private OffsetDateTime placedAt;

    protected PortalSlot() {}

    public PortalSlot(int slotIndex, int toyId, int variantId, OffsetDateTime placedAt) {
        this.slotIndex = slotIndex;
        this.toyId = toyId;
        this.variantId = variantId;
        this.placedAt = placedAt;
    }

    public int getSlotIndex() { return slotIndex; }
    public int getToyId() { return toyId; }
    public int getVariantId() { return variantId; }
    public OffsetDateTime getPlacedAt() { return placedAt; }
}
