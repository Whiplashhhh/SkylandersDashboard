package net.vanbaelinghem.skylanders.ingest;

import jakarta.validation.Valid;
import java.util.Base64;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The one and only channel from the laptop to the server. It flows in a single direction: no
 * endpoint anywhere writes to a laptop path (SPEC.md §9, CLAUDE.md invariant 1).
 */
@RestController
@RequestMapping("/api/ingest")
public class IngestController {

    private final IngestService service;

    public IngestController(IngestService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<IngestResult> ingest(@Valid @RequestBody IngestRequest request) {
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(request.contentBase64());
        } catch (IllegalArgumentException e) {
            throw new IngestRejectedException(HttpStatus.BAD_REQUEST,
                    "contenu base64 invalide : " + e.getMessage());
        }
        IngestResult result = service.ingest(request.relativePath(), raw, request.sha256());
        HttpStatus status = "STORED".equals(result.status()) ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(result);
    }

    @ExceptionHandler(IngestRejectedException.class)
    public ResponseEntity<Map<String, String>> onRejected(IngestRejectedException e) {
        return ResponseEntity.status(e.getStatus()).body(Map.of("error", e.getMessage()));
    }
}
