package net.vanbaelinghem.skylanders.ingest;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.HashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

/**
 * Paths the server refuses to record, read from {@code exclusions.txt}.
 *
 * <p>The same file drives {@code tools/catalog_bootstrap.py}, so the catalogue and the ingestion
 * share one definition of "this file is not mine". Typical case: a dump left behind by whoever
 * assembled the pack, whose hand-edited values would otherwise top the ranking.
 *
 * <p>Excluding is <strong>never</strong> deleting. The {@code .sky} stays untouched on disk
 * (CLAUDE.md invariant 1); removing the line is enough to bring it back on the next scan.
 */
@Component
public class IngestExclusions {

    private static final Logger log = LoggerFactory.getLogger(IngestExclusions.class);

    private final Set<String> excluded = new HashSet<>();

    public IngestExclusions(ResourceLoader resourceLoader,
                            @Value("${skylanders.exclusions.location:classpath:exclusions.txt}")
                            String location) {
        Resource resource = resourceLoader.getResource(location);
        if (!resource.exists()) {
            log.info("Aucune liste d'exclusion ({}) — tous les fichiers sont ingeres", location);
            return;
        }
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                int comment = line.indexOf('#');
                String path = (comment >= 0 ? line.substring(0, comment) : line).trim();
                if (!path.isEmpty()) {
                    excluded.add(normalize(path));
                }
            }
        } catch (IOException e) {
            log.warn("Liste d'exclusion illisible ({}) : {}", location, e.getMessage());
            return;
        }
        if (!excluded.isEmpty()) {
            log.info("{} chemin(s) exclu(s) de l'ingestion", excluded.size());
        }
    }

    public boolean isExcluded(String relativePath) {
        return excluded.contains(normalize(relativePath));
    }

    /** NFC and unified apostrophes, so an accented path matches whatever produced it. */
    private static String normalize(String path) {
        return Normalizer.normalize(path, Normalizer.Form.NFC)
                .replace('\\', '/')
                .replace('’', '\'')
                .trim();
    }
}
