package net.vanbaelinghem.skylanders.bridge;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/bridge/portal")
public class DirectPortalController {
    private final DirectPortalService portal;
    public DirectPortalController(DirectPortalService portal) { this.portal = portal; }
    @GetMapping public DirectPortalService.View state() { return portal.view(); }
    @PostMapping public DirectPortalService.View command(@RequestBody DirectPortalService.Request request) {
        return portal.submit(request);
    }
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> rejected(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode()).body(Map.of("error", error.getReason()));
    }
}
