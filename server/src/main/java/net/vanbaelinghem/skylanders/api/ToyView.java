package net.vanbaelinghem.skylanders.api;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * One roster entry as the Collection grid sees it.
 *
 * @param originGame the game recorded in {@code catalog.json} — the first one the toy appeared in
 * @param games      every game whose folder actually holds a file for this identity. Decision of
 *                   2026-08-23: the game filter uses <em>this</em>, not {@code originGame}. Eight
 *                   sidekicks ship under both Giants and Trap Team with the same identity and the
 *                   same UID — the same physical toy — and filtering on a single origin would hide
 *                   them from one of the two games (FORMAT.md §8.6).
 * @param unlocked   derived, never stored (SPEC.md §7.1). True only when a save showed real
 *                   evidence of play; the mere presence of a file means nothing, the pack ships all
 *                   702 from day one.
 * @param wikiUrl    Fandom page for this figurine, or {@code null} when none is known — traps,
 *                   chests and creation crystals have no page of their own. Built from a title
 *                   verified offline (see {@code tools/wiki_links.py}); the UI shows no button
 *                   rather than a link that would 404. A repaint points at the base character's
 *                   page, but Dark, Legendary, Elite and Nitro each have their own.
 */
public record ToyView(
        int toyId,
        int variantId,
        String nameFr,
        String nameEn,
        String originGame,
        List<String> games,
        String element,
        String category,
        String confidence,
        boolean received,
        boolean unlocked,
        OffsetDateTime firstPlayedAt,
        OffsetDateTime lastSavedAt,
        String wikiUrl) {}
