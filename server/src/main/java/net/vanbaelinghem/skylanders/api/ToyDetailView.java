package net.vanbaelinghem.skylanders.api;

import java.util.List;

/**
 * @param files    every path holding this identity. More than one means duplicate copies of the
 *                 same toy in the pack, not a collision (FORMAT.md §6.2).
 * @param warnings caveats to surface in the UI, as codes rather than sentences — the wording is
 *                 the interface's business, and it is the one that knows the language (SPEC.md
 *                 §10.4).
 */
public record ToyDetailView(
        ToyView toy,
        List<String> files,
        SnapshotView latest,
        List<Notice> warnings) {}
