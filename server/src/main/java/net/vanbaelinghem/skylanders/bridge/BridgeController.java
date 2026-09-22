package net.vanbaelinghem.skylanders.bridge;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/bridge")
public class BridgeController {
    private final BridgeService bridge;
    private final DirectPortalService portal;
    public BridgeController(BridgeService bridge, DirectPortalService portal) { this.bridge = bridge; this.portal = portal; }
    @GetMapping public BridgeService.View state() { return bridge.view(); }
    @PostMapping("/commands") public BridgeService.View command(@RequestBody BridgeService.Request request) {
        return bridge.submit(request);
    }
    @PostMapping("/exchange") public BridgeService.Delivery exchange(@RequestBody BridgeService.Exchange input) {
        return portal.exchange(input);
    }
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> rejected(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode()).body(Map.of("error", error.getReason()));
    }
}
