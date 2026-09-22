package net.vanbaelinghem.skylanders.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The portal layout, read and written by the UI.
 *
 * <p>Unlike {@code /api/ingest}, these writes come from the browser, not from the agent, and so
 * sit outside the shared-token filter — same regime as naming a villain (SPEC.md §9): the tailnet
 * is the boundary.
 */
@RestController
@RequestMapping("/api/portal")
public class PortalController {

    /** Identity of the figurine to place — the canonical couple, never a file path. */
    public record PlaceRequest(int toyId, int variantId) {}

    private final PortalService portal;

    public PortalController(PortalService portal) {
        this.portal = portal;
    }

    @GetMapping
    public PortalView state() {
        return portal.state();
    }

    @PutMapping("/slots/{index}")
    public PortalView place(@PathVariable int index, @RequestBody PlaceRequest request) {
        return portal.place(index, request.toyId(), request.variantId());
    }

    @DeleteMapping("/slots/{index}")
    public PortalView clearSlot(@PathVariable int index) {
        return portal.clearSlot(index);
    }

    @PostMapping("/clear")
    public PortalView clearAll() {
        return portal.clearAll();
    }

    /** 409 : la pose est comprise mais impossible — la raison part sous forme de code. */
    @ExceptionHandler(PortalRejectedException.class)
    public ResponseEntity<Notice> rejected(PortalRejectedException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(e.notice());
    }
}
