package net.vanbaelinghem.skylanders.api;

import java.util.List;

/**
 * The whole portal layout.
 *
 * <p>Every mutation answers with this rather than with the single slot it touched: placing a
 * figurine that already stood somewhere else moves it, so one request can change two slots.
 *
 * @param slotCount how many figurine slots the grid holds. The list is one longer than that —
 *                  the trap keyhole sits at index {@code slotCount} — and can be longer still
 *                  when something is stranded past it (see {@link PortalSlotView#beyondGrid()}).
 * @param trapSlotIndex index of the trap keyhole, so the UI never has to work it out.
 */
public record PortalView(int slotCount, int trapSlotIndex, List<PortalSlotView> slots) {}
