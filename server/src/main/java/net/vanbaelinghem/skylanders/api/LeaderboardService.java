package net.vanbaelinghem.skylanders.api;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.vanbaelinghem.skylanders.domain.TrapContent;
import net.vanbaelinghem.skylanders.domain.TrapContentRepository;
import net.vanbaelinghem.skylanders.domain.ToySnapshot;
import net.vanbaelinghem.skylanders.domain.ToySnapshotRepository;
import net.vanbaelinghem.skylanders.domain.Villain;
import net.vanbaelinghem.skylanders.domain.VillainRepository;
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
    static final Map<String, Function<LeaderboardRow, Comparable>> KEYS = Map.ofEntries(
            Map.entry("name", r -> RosterService.fold(r.nameFr())),
            Map.entry("xp", LeaderboardRow::xp),
            Map.entry("gold", LeaderboardRow::gold),
            Map.entry("upgrades", LeaderboardRow::upgradesCount),
            Map.entry("playtime", LeaderboardRow::playtimeSeconds),
            Map.entry("firstPlayed", LeaderboardRow::firstPlayedAt),
            Map.entry("lastSaved", LeaderboardRow::lastSavedAt),
            Map.entry("element", LeaderboardRow::element),
            Map.entry("category", LeaderboardRow::category),
            Map.entry("game", r -> String.join(",", r.games())),
            // Un piège vide ou un vilain non nommé n'a rien à trier : il descend en bas comme
            // n'importe quelle autre donnée absente.
            Map.entry("villain", r -> r.villainName() == null
                    ? null : RosterService.fold(r.villainName())));

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
    private final TrapContentRepository trapContents;
    private final VillainRepository villains;

    public LeaderboardService(RosterService roster, ToySnapshotRepository snapshots,
                              TrapContentRepository trapContents, VillainRepository villains) {
        this.roster = roster;
        this.snapshots = snapshots;
        this.trapContents = trapContents;
        this.villains = villains;
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

        Map<Integer, String> villainNames = villains.findAll().stream()
                .filter(v -> v.getName() != null)
                .collect(Collectors.toMap(Villain::getRawId, Villain::getName, (a, b) -> a));
        Map<String, TrapContent> traps = trapContents.findLatestPerToy().stream()
                .collect(Collectors.toMap(
                        c -> c.getToy().getToyId() + "/" + c.getToy().getVariantId(),
                        Function.identity(),
                        (a, b) -> a.getCapturedAt().isAfter(b.getCapturedAt()) ? a : b));

        List<LeaderboardRow> all = roster.filtered(game, element, category, search, state).stream()
                .map(view -> {
                    String key = view.toyId() + "/" + view.variantId();
                    return toRow(view, latest.get(key), traps.get(key), villainNames);
                })
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
                row.nickname(), row.firstPlayedAt(), row.lastSavedAt(),
                row.villainRawId(), row.villainName(), row.trapEmpty());
    }

    private static LeaderboardRow toRow(ToyView view, ToySnapshot snapshot,
                                        TrapContent trap, Map<Integer, String> villainNames) {
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
                view.firstPlayedAt(), view.lastSavedAt(),
                trap == null ? null : trap.getVillainRawId(),
                trap == null || trap.isEmpty() ? null : villainNames.get(trap.getVillainRawId()),
                trap == null ? null : trap.isEmpty());
    }

}
