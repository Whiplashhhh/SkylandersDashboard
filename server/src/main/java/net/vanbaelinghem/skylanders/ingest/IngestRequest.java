package net.vanbaelinghem.skylanders.ingest;

import jakarta.validation.constraints.NotBlank;

/**
 * Payload pushed by the agent (SPEC.md §9). The agent hashes and sends raw bytes, nothing else —
 * no parsing ever migrates to it (CLAUDE.md, "Architecture en deux composants").
 */
public record IngestRequest(
        @NotBlank String relativePath,
        @NotBlank String contentBase64,
        @NotBlank String sha256) {}
