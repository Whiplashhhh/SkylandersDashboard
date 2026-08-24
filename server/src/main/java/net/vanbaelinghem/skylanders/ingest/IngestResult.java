package net.vanbaelinghem.skylanders.ingest;

public record IngestResult(String status, String relativePath, Long snapshotId, String detail) {

    public static IngestResult unchanged(String path) {
        return new IngestResult("UNCHANGED", path, null, "hash identique au dernier snapshot");
    }

    public static IngestResult excluded(String path) {
        return new IngestResult("EXCLUDED", path, null,
                "chemin present dans exclusions.txt — non enregistre, fichier intact");
    }

    public static IngestResult stored(String path, Long snapshotId, String detail) {
        return new IngestResult("STORED", path, snapshotId, detail);
    }
}
