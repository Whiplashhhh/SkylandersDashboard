package net.vanbaelinghem.skylanders.api;

import jakarta.validation.constraints.NotBlank;
import java.time.OffsetDateTime;
import java.util.List;
import net.vanbaelinghem.skylanders.domain.Villain;
import net.vanbaelinghem.skylanders.domain.VillainRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The villain roster, and the naming that binds a raw id to it.
 *
 * <p>Reading serves all 46 villains of the game, captured or not — an uncaptured one is a normal
 * entry the UI shows as « ? », never an absent one (same treatment as a locked figurine,
 * SPEC.md §10.1bis). Writing stays what it always was: the user names a raw id, and the
 * self-populating reference fills itself (SPEC.md §7.2).
 */
@RestController
@RequestMapping("/api/villains")
public class VillainController {

    public record NameRequest(@NotBlank String name, String element) {}

    private final VillainService service;
    private final VillainRepository villains;

    public VillainController(VillainService service, VillainRepository villains) {
        this.service = service;
        this.villains = villains;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<VillainView> list(@RequestParam(required = false) String element,
                                  @RequestParam(required = false) Boolean captured) {
        return service.roster().stream()
                .filter(v -> element == null || v.element().equalsIgnoreCase(element))
                .filter(v -> captured == null || v.captured() == captured)
                .toList();
    }

    /**
     * Names known for an element, so the trap screen can offer a choice instead of a free field.
     *
     * <p>Typing « Buzzer Beack » once used to create a villain nobody could ever match again;
     * picking from the roster keeps the naming and the reference in step.
     */
    @GetMapping("/names")
    @Transactional(readOnly = true)
    public List<String> names(@RequestParam(required = false) String element) {
        return service.namesFor(element);
    }

    @GetMapping("/{name}")
    @Transactional(readOnly = true)
    public ResponseEntity<VillainView> detail(@PathVariable String name) {
        return service.byName(name).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/raw/{rawId}")
    @Transactional
    public ResponseEntity<VillainView> name(@PathVariable int rawId,
                                            @RequestBody NameRequest request) {
        Villain villain = villains.findById(rawId).orElseGet(() -> new Villain(rawId));
        villain.nameIt(request.name().trim(),
                request.element() == null || request.element().isBlank()
                        ? villain.getElement() : request.element().trim(),
                OffsetDateTime.now());
        villains.save(villain);
        return ResponseEntity.ok(service.forRawId(rawId));
    }
}
