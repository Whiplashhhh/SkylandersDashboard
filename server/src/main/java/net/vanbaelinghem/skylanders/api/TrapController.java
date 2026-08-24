package net.vanbaelinghem.skylanders.api;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import net.vanbaelinghem.skylanders.classification.Category;
import net.vanbaelinghem.skylanders.domain.CatalogToy;
import net.vanbaelinghem.skylanders.domain.CatalogToyRepository;
import net.vanbaelinghem.skylanders.domain.Toy;
import net.vanbaelinghem.skylanders.domain.ToyKey;
import net.vanbaelinghem.skylanders.domain.ToyRepository;
import net.vanbaelinghem.skylanders.domain.TrapContent;
import net.vanbaelinghem.skylanders.domain.TrapContentRepository;
import net.vanbaelinghem.skylanders.domain.Villain;
import net.vanbaelinghem.skylanders.domain.VillainRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/traps")
@Transactional(readOnly = true)
public class TrapController {

    private final ToyRepository toys;
    private final CatalogToyRepository catalog;
    private final TrapContentRepository contents;
    private final VillainRepository villains;

    public TrapController(ToyRepository toys, CatalogToyRepository catalog,
                          TrapContentRepository contents, VillainRepository villains) {
        this.toys = toys;
        this.catalog = catalog;
        this.contents = contents;
        this.villains = villains;
    }

    @GetMapping
    public List<TrapView> list(@RequestParam(required = false) Boolean occupied) {
        Map<Integer, String> names = villains.findAll().stream()
                .filter(v -> v.getName() != null)
                .collect(Collectors.toMap(Villain::getRawId, Villain::getName));

        return toys.findByCategoryFolder(Category.TRAP.name()).stream()
                .map(toy -> toView(toy, names))
                .filter(view -> occupied == null || view.empty() != occupied)
                .sorted(Comparator.comparing(TrapView::element).thenComparing(TrapView::trapName))
                .toList();
    }

    private TrapView toView(Toy toy, Map<Integer, String> names) {
        Optional<TrapContent> content =
                contents.findFirstByToyOrderByCapturedAtDesc(toy);
        int villainId = content.map(TrapContent::getVillainRawId).orElse(0);
        boolean empty = content.map(TrapContent::isEmpty).orElse(true);
        String name = catalog.findById(new ToyKey(toy.getToyId(), toy.getVariantId()))
                .map(CatalogToy::getNameFr)
                .orElseGet(() -> fileName(toy));
        return new TrapView(
                toy.getToyId(), toy.getVariantId(), name, toy.getElementFolder(),
                toy.getFilePath(), villainId,
                // null tells the UI to offer the "name this villain" input (SPEC.md §7.2).
                empty ? null : names.get(villainId),
                empty, content.map(TrapContent::getCapturedAt).orElse(null));
    }

    private static String fileName(Toy toy) {
        String path = toy.getFilePath();
        String leaf = path.substring(path.lastIndexOf('/') + 1);
        return leaf.endsWith(".sky") ? leaf.substring(0, leaf.length() - 4) : leaf;
    }
}
