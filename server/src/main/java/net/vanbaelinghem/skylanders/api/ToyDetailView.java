package net.vanbaelinghem.skylanders.api;

import java.util.List;

/**
 * @param files    every path holding this identity. More than one means duplicate copies of the
 *                 same toy in the pack, not a collision (FORMAT.md §6.2).
 * @param warnings human-readable caveats to surface in the UI, e.g. an unsupported game or an
 *                 identity resolved from a name that needs review (SPEC.md §10.4).
 */
public record ToyDetailView(
        ToyView toy,
        List<String> files,
        SnapshotView latest,
        List<String> warnings) {}
