package net.vanbaelinghem.skylanders.api;

import java.time.OffsetDateTime;

/**
 * One slot of the portal layout, empty or occupied.
 *
 * @param toy      the figurine standing there, {@code null} for an empty slot. Carries the same
 *                 shape as a grid entry so the panel can draw a card without a second call.
 * @param placedAt when this figurine was put on this slot, {@code null} when the slot is empty
 * @param beyondGrid {@code true} when the slot sits past the configured grid size — it only
 *                 shows up because something is standing on it (see {@link PortalService})
 * @param trapSlot {@code true} for the portal's single trap keyhole, which takes traps and
 *                 nothing else — and which nothing else takes
 * @param villain  who is locked in the trap standing here, {@code null} otherwise. Lets the
 *                 keyhole show its prisoner rather than the trap: that is the useful picture,
 *                 and an empty trap is already recognisable by its own artwork.
 */
public record PortalSlotView(
        int index,
        ToyView toy,
        OffsetDateTime placedAt,
        boolean beyondGrid,
        boolean trapSlot,
        VillainView villain) {}
