package net.vanbaelinghem.skylanders.api;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.time.OffsetDateTime;
import java.util.stream.Collectors;
import net.vanbaelinghem.skylanders.domain.CatalogToy;
import net.vanbaelinghem.skylanders.domain.CatalogToyRepository;
import net.vanbaelinghem.skylanders.domain.Toy;
import net.vanbaelinghem.skylanders.domain.ToyKey;
import net.vanbaelinghem.skylanders.domain.ToyRepository;
import net.vanbaelinghem.skylanders.domain.ToySnapshot;
import net.vanbaelinghem.skylanders.domain.ToySnapshotRepository;
import net.vanbaelinghem.skylanders.format.DeltaCodec;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Assembles roster views. Kept out of the controllers so the read model has one home. */
@Service
@Transactional(readOnly = true)
public class RosterService {

    /**
     * FORMAT.md §8.7 — the XP field read at logical +0x00 is the legacy one and saturates here.
     * Trap Team's additional XP field has not been located, so any value at the ceiling is
     * reported as possibly truncated rather than presented as a fact (SPEC.md §10.4).
     */
    static final int XP_LEGACY_CEILING = 33000;

    private final CatalogToyRepository catalog;
    private final ToyRepository toys;
    private final ToySnapshotRepository snapshots;

    public RosterService(CatalogToyRepository catalog, ToyRepository toys,
                         ToySnapshotRepository snapshots) {
        this.catalog = catalog;
        this.toys = toys;
        this.snapshots = snapshots;
    }

    public List<ToyView> roster() {
        Map<String, List<Toy>> received = toys.findAll().stream()
                .collect(Collectors.groupingBy(RosterService::identityKey));
        return catalog.findAll().stream()
                .map(entry -> toView(entry, received.get(identityKey(entry))))
                .sorted(Comparator.comparing(ToyView::nameFr))
                .toList();
    }

    public List<ToyView> filtered(String game, String element, String category,
                                  String search, String state) {
        return roster().stream()
                .filter(view -> game == null || matchesGame(view, game))
                .filter(view -> element == null || view.element().equalsIgnoreCase(element))
                .filter(view -> category == null || view.category().equalsIgnoreCase(category))
                .filter(view -> search == null || search.isBlank() || matchesSearch(view, search))
                .filter(view -> state == null || matchesState(view, state))
                .toList();
    }

    public Optional<ToyDetailView> detail(int toyId, int variantId) {
        CatalogToy entry = catalog.findById(new ToyKey(toyId, variantId)).orElse(null);
        List<Toy> matching = toys.findByToyIdAndVariantId(toyId, variantId);
        if (entry == null && matching.isEmpty()) {
            return Optional.empty();
        }
        ToyView view = entry != null ? toView(entry, matching) : fromReceivedOnly(matching);

        SnapshotView latest = matching.stream()
                .map(snapshots::findFirstByToyOrderByCapturedAtDesc)
                .flatMap(Optional::stream)
                .max(Comparator.comparing(ToySnapshot::getCapturedAt))
                .map(RosterService::toSnapshotView)
                .orElse(null);

        List<String> warnings = new ArrayList<>();
        if (latest != null && "UNSUPPORTED_GAME".equals(latest.parseStatus())) {
            warnings.add("Aucun parseur de sauvegarde pour ce jeu : identité seule, "
                    + "progression et déblocage indéterminés.");
        }
        if (entry != null && "REVIEW".equals(entry.getConfidence())) {
            warnings.add("Nom à revoir : plusieurs libellés se rattachent à cette identité.");
        }
        if (latest != null && latest.xp() != null && latest.xp() >= XP_LEGACY_CEILING) {
            warnings.add("XP à " + latest.xp() + " : le champ lu sature à " + XP_LEGACY_CEILING
                    + ", la valeur réelle est peut-être plus haute (FORMAT.md §8.7).");
        }
        // « Jamais posée » ne peut s'affirmer que si la sauvegarde a pu être lue. Sur un jeu
        // sans parseur, le statut honnête est « on ne sait pas » — l'avertissement précédent le
        // dit déjà, et ajouter celui-ci le contredirait (SPEC.md §6.6).
        boolean readable = latest != null && "OK".equals(latest.parseStatus());
        if (readable && view.received() && !view.unlocked()) {
            warnings.add("Fichier reçu mais aucune trace de jeu : figurine jamais posée "
                    + "sur le portail.");
        }
        return Optional.of(new ToyDetailView(view,
                matching.stream().map(Toy::getFilePath).sorted().toList(), latest, warnings));
    }

    public List<SnapshotView> history(int toyId, int variantId) {
        return toys.findByToyIdAndVariantId(toyId, variantId).stream()
                .flatMap(toy -> snapshots.findByToyOrderByCapturedAtAsc(toy).stream())
                .sorted(Comparator.comparing(RosterService::effectiveTime))
                .map(RosterService::toSnapshotView)
                .toList();
    }

    /**
     * When a snapshot belongs on a timeline.
     *
     * <p>The tag carries its own save timestamp (FORMAT.md §5.1, logical +0x40), and that is the
     * moment the figurine was actually played. Ingestion time only says when the agent got around
     * to sending the file — restore a backup, replay a capture, or run the very first full scan,
     * and the two orders diverge. The history graph would then draw a curve that goes backwards.
     */
    static OffsetDateTime effectiveTime(ToySnapshot snapshot) {
        return snapshot.getSavedAt() != null ? snapshot.getSavedAt() : snapshot.getCapturedAt();
    }

    // -- assemblage ----------------------------------------------------------------------------

    static String identityKey(Toy toy) {
        return toy.getToyId() + "/" + toy.getVariantId();
    }

    static String identityKey(CatalogToy entry) {
        return entry.getKey().getToyId() + "/" + entry.getKey().getVariantId();
    }

    private ToyView toView(CatalogToy entry, List<Toy> matching) {
        List<Toy> found = matching == null ? List.of() : matching;
        return new ToyView(
                entry.getKey().getToyId(), entry.getKey().getVariantId(),
                entry.getNameFr(), entry.getNameEn(),
                entry.getGame(), observedGames(found, entry.getGame()),
                entry.getElement(), entry.getCategory(), entry.getConfidence(),
                !found.isEmpty(), firstPlayed(found) != null,
                firstPlayed(found), lastSaved(found));
    }

    /** A received file whose identity is absent from the catalogue (SPEC.md §7.2, cas NEW). */
    private ToyView fromReceivedOnly(List<Toy> matching) {
        Toy first = matching.get(0);
        String label = "Inconnu (" + first.getToyId() + "/" + first.getVariantId() + ")";
        return new ToyView(first.getToyId(), first.getVariantId(), label, label,
                first.getGameFolder(), observedGames(matching, first.getGameFolder()),
                first.getElementFolder(), first.getCategoryFolder(), "NEW",
                true, firstPlayed(matching) != null,
                firstPlayed(matching), lastSaved(matching));
    }

    private static List<String> observedGames(List<Toy> found, String fallback) {
        List<String> games = found.stream().map(Toy::getGameFolder).distinct().sorted().toList();
        return games.isEmpty() ? List.of(fallback) : games;
    }

    private static java.time.OffsetDateTime firstPlayed(List<Toy> found) {
        return found.stream().map(Toy::getFirstPlayedAt).filter(Objects::nonNull)
                .min(Comparator.naturalOrder()).orElse(null);
    }

    private static java.time.OffsetDateTime lastSaved(List<Toy> found) {
        return found.stream().map(Toy::getLastSavedAt).filter(Objects::nonNull)
                .max(Comparator.naturalOrder()).orElse(null);
    }

    static SnapshotView toSnapshotView(ToySnapshot snapshot) {
        Integer upgrades = snapshot.getUpgradesBitfield();
        return new SnapshotView(
                effectiveTime(snapshot),
                snapshot.getCapturedAt(), snapshot.getSavedAt(), snapshot.getXp(),
                snapshot.getGold(), upgrades,
                upgrades == null ? null : Integer.bitCount(upgrades),
                snapshot.getNickname(), snapshot.getPlaytimeSeconds(),
                snapshot.getParseStatus(), snapshot.isHasPlayEvidence(),
                DeltaCodec.changedBlockCount(snapshot.getDelta()));
    }

    // -- filtres -------------------------------------------------------------------------------

    private static boolean matchesGame(ToyView view, String game) {
        return view.games().stream().anyMatch(g -> g.equalsIgnoreCase(game));
    }

    private static boolean matchesState(ToyView view, String state) {
        return switch (state.toLowerCase(Locale.ROOT)) {
            case "unlocked", "owned" -> view.unlocked();
            case "locked" -> !view.unlocked();
            case "received" -> view.received();
            default -> true;
        };
    }

    private static boolean matchesSearch(ToyView view, String search) {
        String needle = fold(search);
        return fold(view.nameFr()).contains(needle) || fold(view.nameEn()).contains(needle);
    }

    /** Accent- and case-insensitive, matching how the UI is actually typed into. */
    static String fold(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace('’', '\'')
                .toLowerCase(Locale.ROOT);
    }
}
