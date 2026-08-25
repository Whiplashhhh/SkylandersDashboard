package net.vanbaelinghem.skylanders.api;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.vanbaelinghem.skylanders.classification.Element;
import net.vanbaelinghem.skylanders.classification.Game;
import net.vanbaelinghem.skylanders.domain.ToySnapshot;
import net.vanbaelinghem.skylanders.domain.ToySnapshotRepository;
import net.vanbaelinghem.skylanders.domain.Villain;
import net.vanbaelinghem.skylanders.domain.VillainRepository;
import net.vanbaelinghem.skylanders.format.save.TrapTeamSaveParser;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stats")
@Transactional(readOnly = true)
public class StatsController {

    private final RosterService roster;
    private final ToySnapshotRepository snapshots;
    private final VillainRepository villains;
    private final TrapTeamSaveParser trapTeamParser;

    public StatsController(RosterService roster, ToySnapshotRepository snapshots,
                           VillainRepository villains, TrapTeamSaveParser trapTeamParser) {
        this.roster = roster;
        this.snapshots = snapshots;
        this.villains = villains;
        this.trapTeamParser = trapTeamParser;
    }

    @GetMapping
    public StatsView stats(@RequestParam(defaultValue = "10") int topSize) {
        List<ToyView> entries = roster.roster();

        Map<String, int[]> perGame = new LinkedHashMap<>();
        for (ToyView view : entries) {
            // An identity present in two games counts in both — that is what the folders say
            // (FORMAT.md §8.6).
            for (String game : view.games()) {
                int[] counters = perGame.computeIfAbsent(game, g -> new int[3]);
                counters[0]++;
                if (view.received()) {
                    counters[1]++;
                }
                if (view.unlocked()) {
                    counters[2]++;
                }
            }
        }
        List<StatsView.GameStats> byGame = perGame.entrySet().stream()
                .map(e -> new StatsView.GameStats(e.getKey(), e.getValue()[0], e.getValue()[1],
                        e.getValue()[2], parserExists(e.getKey())))
                .sorted(Comparator.comparing(StatsView.GameStats::game))
                .toList();

        Map<String, int[]> perElement = new LinkedHashMap<>();
        for (ToyView view : entries) {
            int[] counters = perElement.computeIfAbsent(view.element(), e -> new int[2]);
            counters[0]++;
            if (view.unlocked()) {
                counters[1]++;
            }
        }
        List<StatsView.ElementStats> byElement = perElement.entrySet().stream()
                .map(e -> new StatsView.ElementStats(e.getKey(),
                        Element.fromLabel(e.getKey()).color(), e.getValue()[0], e.getValue()[1]))
                .sorted(Comparator.comparing(StatsView.ElementStats::element))
                .toList();

        Map<String, ToyView> byIdentity = new LinkedHashMap<>();
        entries.forEach(v -> byIdentity.put(v.toyId() + "/" + v.variantId(), v));
        List<StatsView.TopEntry> topXp = snapshots.findLatestPerToy().stream()
                .filter(s -> s.getXp() != null && s.getXp() > 0)
                .sorted(Comparator.comparing(ToySnapshot::getXp).reversed())
                .limit(Math.max(1, Math.min(topSize, 100)))
                .map(s -> {
                    ToyView view = byIdentity.get(
                            s.getToy().getToyId() + "/" + s.getToy().getVariantId());
                    return new StatsView.TopEntry(
                            s.getToy().getToyId(), s.getToy().getVariantId(),
                            view != null ? view.nameFr() : s.getToy().getFilePath(),
                            s.getToy().getGameFolder(),
                            s.getXp(), s.getGold(), s.getPlaytimeSeconds());
                })
                .toList();

        int unlocked = (int) entries.stream().filter(ToyView::unlocked).count();
        int received = (int) entries.stream().filter(ToyView::received).count();
        List<Villain> allVillains = villains.findAll();
        StatsView.Totals totals = new StatsView.Totals(entries.size(), received, unlocked,
                allVillains.size(),
                (int) allVillains.stream().map(Villain::getName).filter(Objects::nonNull).count());

        List<Notice> caveats = new ArrayList<>();
        List<String> unparsed = byGame.stream()
                .filter(g -> !g.parserAvailable())
                .map(StatsView.GameStats::game)
                .toList();
        if (!unparsed.isEmpty()) {
            caveats.add(Notice.of("gamesWithoutParser", Map.of("games", unparsed)));
        }
        caveats.add(Notice.of("levelNotShown"));
        return new StatsView(totals, byGame, byElement, topXp, caveats);
    }

    private boolean parserExists(String gameName) {
        try {
            return trapTeamParser.supports(Game.valueOf(gameName));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
