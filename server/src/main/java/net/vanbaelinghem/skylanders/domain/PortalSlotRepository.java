package net.vanbaelinghem.skylanders.domain;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Slots are always removed by a derived query or in batch, never through
 * {@link JpaRepository#delete} or {@link JpaRepository#deleteAll()}.
 *
 * <p>Those two ask the entity whether it is new before removing it, and Spring Data reads a
 * primitive identifier of {@code 0} as "not saved yet" — so they silently skip slot 0, the
 * first slot of the grid. Derived deletes and {@code deleteAllInBatch()} go straight to SQL.
 */
public interface PortalSlotRepository extends JpaRepository<PortalSlot, Integer> {

    void deleteBySlotIndex(int slotIndex);

    void deleteByToyIdAndVariantId(int toyId, int variantId);
}
