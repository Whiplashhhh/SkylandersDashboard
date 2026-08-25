package net.vanbaelinghem.skylanders.api;

import java.util.List;

/**
 * Aggregates for the statistics screen.
 *
 * @param caveats warnings the UI must show, as codes. Completion is only meaningful for a game
 *                whose {@code GameSaveParser} exists (SPEC.md §6.6): elsewhere nothing can be
 *                unlocked, and that is an honest "we don't know", not a zero.
 */
public record StatsView(
        Totals totals,
        List<GameStats> byGame,
        List<ElementStats> byElement,
        List<TopEntry> topXp,
        List<Notice> caveats) {

    public record Totals(int rosterSize, int received, int unlocked,
                         int villainsKnown, int villainsNamed) {}

    /**
     * @param parserAvailable false ⇒ {@code unlocked} is structurally 0 and completion is
     *                        undefined, not zero.
     */
    public record GameStats(String game, int roster, int received, int unlocked,
                            boolean parserAvailable) {}

    public record ElementStats(String element, String color, int roster, int unlocked) {}

    public record TopEntry(int toyId, int variantId, String nameFr, String game,
                           Integer xp, Integer gold, Integer playtimeSeconds) {}
}
