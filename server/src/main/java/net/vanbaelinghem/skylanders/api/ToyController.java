package net.vanbaelinghem.skylanders.api;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read side for the UI. Serves the <strong>full roster</strong>, unlocked or not (SPEC.md
 * §10.1bis) — a locked entry is a normal card, greyed out, never an absent one.
 */
@RestController
@RequestMapping("/api/toys")
public class ToyController {

    private final RosterService roster;

    public ToyController(RosterService roster) {
        this.roster = roster;
    }

    @GetMapping
    public List<ToyView> list(@RequestParam(required = false) String game,
                              @RequestParam(required = false) String element,
                              @RequestParam(required = false) String category,
                              @RequestParam(required = false) String search,
                              @RequestParam(required = false) String state) {
        return roster.filtered(game, element, category, search, state);
    }

    @GetMapping("/{toyId}/{variantId}")
    public ResponseEntity<ToyDetailView> detail(@PathVariable int toyId,
                                                @PathVariable int variantId) {
        return roster.detail(toyId, variantId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{toyId}/{variantId}/history")
    public List<SnapshotView> history(@PathVariable int toyId, @PathVariable int variantId) {
        return roster.history(toyId, variantId);
    }
}
