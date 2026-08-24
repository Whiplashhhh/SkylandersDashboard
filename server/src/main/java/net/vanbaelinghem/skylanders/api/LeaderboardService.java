package net.vanbaelinghem.skylanders.api;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.vanbaelinghem.skylanders.domain.ToySnapshot;
import net.vanbaelinghem.skylanders.domain.ToySnapshotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Builds the ranking. Sorting and paging happen here, never in the browser. */
@Service
@Transactional(readOnly = true)
public class LeaderboardService {

    /** FORMAT.md §8.7 — the legacy XP field saturates here. */
    static final int XP_CEILING = 33000;

    /**
     * Only these fields can be sorted on. A whitelist rather than a free-form column name: the
     * value arrives straight from a query string.
     */
    @SuppressWarnings("rawtypes")
    static final Map<String, Function<LeaderboardRow, Comparable>> KEYS = Map.of(
            "name", r -> RosterService.fold(r.nameFr()),
            "xp", LeaderboardRow::xp,
            "gold", LeaderboardRow::gold,
            "upgrades", LeaderboardRow::upgradesCount,
            "playtime", LeaderboardRow::playtimeSeconds,
            "firstPlayed", LeaderboardRow::firstPlayedAt,
            "lastSaved", LeaderboardRow::lastSavedAt,
            "element", LeaderboardRow::element,
            "category", LeaderboardRow::category,
            "game", r -> String.join(",", r.games()));

    /**
     * Build the comparator for a column and a direction.
     *
     * <p>The direction is applied to the <em>value</em> comparator, never to the whole thing.
     * Reversing a {@code nullsLast} comparator turns it into {@code nullsFirst}, which put all 489
     * figurines with no data at the top of a descending XP ranking. A figurine without a score has
     * no score — it belongs at the bottom either way.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    static Comparator<LeaderboardRow> comparator(String key, boolean ascending) {
        Function<LeaderboardRow, Comparable> extractor = KEYS.get(key);
        Comparator<Comparable> values =
                ascending ? Comparator.naturalOrder() : Comparator.reverseOrder();
        return Comparator.comparing(extractor, Comparator.nullsLast(values));
    }

    private final RosterService roster;
    private final ToySnapshotRepository snapshots;

    public LeaderboardService(RosterService roster, ToySnapshotRepository snapshots) {
        this.roster = roster;
        this.snapshots = snapshots;
    }

    public LeaderboardPage page(String game, String element, String category, String search,
                                String state, String sort, String direction, int page, int size) {
        String sortKey = KEYS.containsKey(sort) ? sort : "xp";
        boolean ascending = "asc".equalsIgnoreCase(direction);
        int pageSize = Math.max(1, Math.min(size, 500));

        Map<String, ToySnapshot> latest = snapshots.findLatestPerToy().stream()
                .collect(Collectors.toMap(
                        s -> s.getToy().getToyId() + "/" + s.getToy().getVariantId(),
                        Function.identity(),
                        (a, b) -> a.getCapturedAt().isAfter(b.getCapturedAt()) ? a : b));

        List<LeaderboardRow> all = roster.filtered(game, element, category, search, state).stream()
                .map(view -> toRow(view, latest.get(view.toyId() + "/" + view.variantId())))
                .toList();

        Comparator<LeaderboardRow> comparator = comparator(sortKey, ascending);
        // Stable tiebreaker so equal scores keep a predictable order between requests — otherwise
        // paging through a column full of ties can show the same row twice.
        comparator = comparator.thenComparing(r -> RosterService.fold(r.nameFr()))
                .thenComparingInt(LeaderboardRow::toyId)
                .thenComparingInt(LeaderboardRow::variantId);

        List<LeaderboardRow> sorted = all.stream().sorted(comparator).toList();
        int total = sorted.size();
        int pageCount = Math.max(1, (int) Math.ceil((double) total / pageSize));
        int current = Math.max(1, Math.min(page, pageCount));
        int from = (current - 1) * pageSize;
        int to = Math.min(from + pageSize, total);

        List<LeaderboardRow> slice = new java.util.ArrayList<>(Math.max(0, to - from));
        for (int i = from; i < to; i++) {
            // Le rang est celui dans le classement COMPLET, pas dans la page : la 3e ligne de la
            // page 2 est 23e, pas 3e.
            slice.add(withRank(sorted.get(i), i + 1));
        }

        return new LeaderboardPage(List.copyOf(slice), total, current, pageSize, pageCount,
                sortKey, ascending ? "asc" : "desc",
                KEYS.keySet().stream().sorted().toList());
    }

    private static LeaderboardRow withRank(LeaderboardRow row, int rank) {
        return new LeaderboardRow(rank, row.toyId(), row.variantId(), row.nameFr(), row.nameEn(),
                row.games(), row.element(), row.category(), row.unlocked(), row.parsable(),
                row.xp(), row.xpCapped(), row.gold(), row.upgradesCount(), row.playtimeSeconds(),
                row.nickname(), row.firstPlayedAt(), row.lastSavedAt());
    }

    private static LeaderboardRow toRow(ToyView view, ToySnapshot snapshot) {
        Integer xp = snapshot == null ? null : snapshot.getXp();
        Integer upgrades = snapshot == null || snapshot.getUpgradesBitfield() == null
                ? null : Integer.bitCount(snapshot.getUpgradesBitfield());
        boolean parsable = snapshot != null && "OK".equals(snapshot.getParseStatus());
        return new LeaderboardRow(
                0, view.toyId(), view.variantId(), view.nameFr(), view.nameEn(), view.games(),
                view.element(), view.category(), view.unlocked(), parsable,
                xp, xp != null && xp >= XP_CEILING,
                snapshot == null ? null : snapshot.getGold(),
                upgrades,
                snapshot == null ? null : snapshot.getPlaytimeSeconds(),
                snapshot == null ? null : snapshot.getNickname(),
                view.firstPlayedAt(), view.lastSavedAt());
    }

}
