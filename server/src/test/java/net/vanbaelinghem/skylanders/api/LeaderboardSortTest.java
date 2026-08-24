package net.vanbaelinghem.skylanders.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LeaderboardSortTest {

    private static LeaderboardRow row(String name, Integer xp) {
        return new LeaderboardRow(0, 1, 0, name, name, List.of("TRAP_TEAM"), "Feu", "CHARACTER",
                xp != null, xp != null, xp, false, null, null, null, null, null, null);
    }

    @Test
    @DisplayName("une figurine sans donnée reste en bas, dans les DEUX sens de tri")
    void nullsStayLastWhicheverDirection() {
        List<LeaderboardRow> rows = List.of(row("sans", null), row("basse", 10), row("haute", 900));

        List<String> desc = rows.stream()
                .sorted(LeaderboardService.comparator("xp", false))
                .map(LeaderboardRow::nameFr).toList();
        List<String> asc = rows.stream()
                .sorted(LeaderboardService.comparator("xp", true))
                .map(LeaderboardRow::nameFr).toList();

        // Le piège : inverser un comparateur nullsLast le transforme en nullsFirst, ce qui
        // hissait les 489 figurines sans donnée en tête du classement décroissant.
        assertThat(desc).containsExactly("haute", "basse", "sans");
        assertThat(asc).containsExactly("basse", "haute", "sans");
    }

    @Test
    @DisplayName("chaque colonne annoncée comme triable produit bien un comparateur")
    void everyAdvertisedColumnSorts() {
        for (String key : LeaderboardService.KEYS.keySet()) {
            Comparator<LeaderboardRow> comparator = LeaderboardService.comparator(key, true);
            assertThat(comparator).as(key).isNotNull();
            assertThat(comparator.compare(row("a", 1), row("a", 1))).isZero();
        }
    }
}
