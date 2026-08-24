package net.vanbaelinghem.skylanders.api;

import jakarta.validation.constraints.NotBlank;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import net.vanbaelinghem.skylanders.classification.Category;
import net.vanbaelinghem.skylanders.domain.ToyRepository;
import net.vanbaelinghem.skylanders.domain.TrapContent;
import net.vanbaelinghem.skylanders.domain.TrapContentRepository;
import net.vanbaelinghem.skylanders.domain.Villain;
import net.vanbaelinghem.skylanders.domain.VillainRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Self-healing villain reference (SPEC.md §7.2): the user names, the app remembers. */
@RestController
@RequestMapping("/api/villains")
public class VillainController {

    public record NameRequest(@NotBlank String name, String element) {}

    private final VillainRepository villains;
    private final TrapContentRepository contents;
    private final ToyRepository toys;

    public VillainController(VillainRepository villains, TrapContentRepository contents,
                             ToyRepository toys) {
        this.villains = villains;
        this.contents = contents;
        this.toys = toys;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<VillainView> list() {
        Map<Integer, Long> occupancy = toys.findByCategoryFolder(Category.TRAP.name()).stream()
                .map(contents::findFirstByToyOrderByCapturedAtDesc)
                .flatMap(java.util.Optional::stream)
                .filter(content -> !content.isEmpty())
                .collect(Collectors.groupingBy(TrapContent::getVillainRawId,
                        Collectors.counting()));

        return villains.findAll().stream()
                .map(v -> new VillainView(v.getRawId(), v.getName(), v.getElement(),
                        v.getNamedByUserAt(), occupancy.getOrDefault(v.getRawId(), 0L)))
                .sorted(Comparator.comparingInt(VillainView::rawId))
                .toList();
    }

    @PutMapping("/{rawId}")
    @Transactional
    public ResponseEntity<VillainView> name(@PathVariable int rawId,
                                            @RequestBody NameRequest request) {
        Villain villain = villains.findById(rawId).orElseGet(() -> new Villain(rawId));
        villain.nameIt(request.name().trim(),
                request.element() == null || request.element().isBlank()
                        ? villain.getElement() : request.element().trim(),
                OffsetDateTime.now());
        Villain saved = villains.save(villain);
        return ResponseEntity.ok(new VillainView(saved.getRawId(), saved.getName(),
                saved.getElement(), saved.getNamedByUserAt(), 0L));
    }
}
