package net.vanbaelinghem.skylanders.ingest;

import org.springframework.http.HttpStatus;

/** A file the server refuses to record. Always logged at WARN with path and reason. */
public class IngestRejectedException extends RuntimeException {

    private final HttpStatus status;

    public IngestRejectedException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
