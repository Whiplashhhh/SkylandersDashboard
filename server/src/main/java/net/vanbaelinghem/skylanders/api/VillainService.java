package net.vanbaelinghem.skylanders.api;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.vanbaelinghem.skylanders.classification.Category;
import net.vanbaelinghem.skylanders.domain.CatalogVillain;
import net.vanbaelinghem.skylanders.domain.CatalogVillainRepository;
import net.vanbaelinghem.skylanders.domain.ToyRepository;
import net.vanbaelinghem.skylanders.domain.TrapContent;
import net.vanbaelinghem.skylanders.domain.TrapContentRepository;
import net.vanbaelinghem.skylanders.domain.Villain;
import net.vanbaelinghem.skylanders.domain.VillainRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The villain roster and what is currently held.
 *
 * <p>Two references meet here and must not be confused. {@code catalog_villain} says what the game
 * contains — 46 villains, fixed. {@code villain} maps a <em>raw id read on a tag</em> to a name,
 * and only the user can fill it, because no external source knows that mapping (SPEC.md §7.2).
 * A villain counts as captured when a trap holds a raw id whose user-given name matches it.
 *
 * <p>So an unnamed raw id sitting in a trap leaves its villain shown as « ? ». That is honest:
 * the app genuinely does not know who is in there until someone says so.
 */
@Service
@Transactional(readOnly = true)
public class VillainService {

    private static final String WIKI_BASE = "https://skylanders.fandom.com/wiki/";

    /**
     * Folded name used to match a user-given name against the roster.
     *
     * <p>Two liberties on top of {@link RosterService#fold}: separators are dropped, so
     * « buzzer-beak » meets « Buzzer Beak »; and a leading article is dropped, because the
     * roster says « The Gulper » where a user naturally types « Gulper ».
     *
     * <p>This is not the fuzzy matching CLAUDE.md forbids at runtime — no edit distance, no
     * guessing. The article is removed only when it is a <em>word</em> of its own, before
     * separators go: strip it from the raw letters instead and « Threatpack » would become
     * « atpack ».
     */
    static String matchKey(String name) {
        String folded = RosterService.fold(name).replaceAll("^the[\\s-]+", "");
        return folded.replaceAll("[^a-z0-9]", "");
    }

    private final CatalogVillainRepository catalog;
    private final VillainRepository villains;
    private final TrapContentRepository contents;
    private final ToyRepository toys;

    public VillainService(CatalogVillainRepository catalog, VillainRepository villains,
                          TrapContentRepository contents, ToyRepository toys) {
        this.catalog = catalog;
        this.villains = villains;
        this.contents = contents;
        this.toys = toys;
    }

    /** What each trap holds right now, keyed by the folded name the user gave the raw id. */
    private Map<String, Held> held() {
        Map<Integer, Villain> named = new HashMap<>();
        for (Villain villain : villains.findAll()) {
            named.put(villain.getRawId(), villain);
        }
        Map<String, Held> byName = new HashMap<>();
        for (var toy : toys.findByCategoryFolder(Category.TRAP.name())) {
            Optional<TrapContent> content = contents.findFirstByToyOrderByCapturedAtDesc(toy);
            if (content.isEmpty() || content.get().isEmpty()) {
                continue;
            }
            Villain villain = named.get(content.get().getVillainRawId());
            if (villain == null || villain.getName() == null || villain.getName().isBlank()) {
                continue;   // identifiant brut pas encore nomme : le vilain reste un « ? »
            }
            byName.merge(matchKey(villain.getName()),
                    new Held(villain.getRawId(), content.get().getCapturedAt(), 1),
                    Held::merge);
        }
        return byName;
    }

    public List<VillainView> roster() {
        Map<String, Held> held = held();
        List<VillainView> out = new ArrayList<>();
        for (CatalogVillain entry : catalog.findAll()) {
            Held now = held.get(matchKey(entry.getName()));
            out.add(new VillainView(
                    entry.getName(), entry.getElement(), entry.isDoomRaider(),
                    now != null, now == null ? null : now.rawId(),
                    entry.getSummary(), wikiUrl(entry.getWiki()),
                    now == null ? null : now.capturedAt(), now == null ? 0 : now.count()));
        }
        // Le boss de l'element d'abord, le reste par ordre alphabetique : c'est l'ordre du jeu
        // et celui de la page du wiki, pas un tri arbitraire.
        out.sort(Comparator.comparing(VillainView::element)
                .thenComparing(Comparator.comparing(VillainView::doomRaider).reversed())
                .thenComparing(VillainView::name));
        return out;
    }

    public Optional<VillainView> byName(String name) {
        String needle = matchKey(name);
        return roster().stream()
                .filter(view -> matchKey(view.name()).equals(needle))
                .findFirst();
    }

    /** Le vilain que ce piege contient, vu par le roster — {@code null} si le nom manque. */
    public VillainView forRawId(int rawId) {
        return villains.findById(rawId)
                .map(Villain::getName)
                .filter(name -> name != null && !name.isBlank())
                .flatMap(this::byName)
                .orElse(null);
    }

    /** Les noms connus pour un element, pour proposer un choix plutot qu'un champ libre. */
    public List<String> namesFor(String element) {
        return catalog.findAll().stream()
                .filter(v -> element == null || element.isBlank()
                        || v.getElement().equalsIgnoreCase(element))
                .map(CatalogVillain::getName)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    static String wikiUrl(String title) {
        if (title == null || title.isBlank()) {
            return null;
        }
        return WIKI_BASE + java.net.URLEncoder
                .encode(title, java.nio.charset.StandardCharsets.UTF_8)
                .replace("+", "_")
                .replace("%28", "(").replace("%29", ")")
                .replace("%3A", ":").replace("%2C", ",")
                .replace("%27", "'").replace("%21", "!");
    }

    private record Held(int rawId, OffsetDateTime capturedAt, long count) {
        Held merge(Held other) {
            return new Held(rawId,
                    capturedAt == null || (other.capturedAt != null
                            && other.capturedAt.isAfter(capturedAt)) ? other.capturedAt : capturedAt,
                    count + other.count);
        }
    }
}
