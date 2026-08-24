package net.vanbaelinghem.skylanders.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import net.vanbaelinghem.skylanders.domain.CatalogToy;
import net.vanbaelinghem.skylanders.domain.CatalogToyRepository;
import net.vanbaelinghem.skylanders.domain.ToyKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Reconciles {@code catalog.json} into {@code catalog_toy} on every startup.
 *
 * <p>Decision of 2026-08-22 (FORMAT.md §7.3): the JSON stays the versioned source of truth and is
 * upserted at boot, rather than being frozen into a Flyway migration. The bootstrap leaves 26
 * entries at {@code REVIEW} that need hand-correction — with this scheme, correcting one is
 * "edit the JSON, restart", not "write a migration".
 *
 * <p>Runs as a {@link SmartInitializingSingleton}, i.e. <em>before</em> the web connector starts
 * accepting traffic. Loading on {@code ApplicationReadyEvent} left a window of a second or so
 * during which {@code /api/toys} answered with an empty roster — technically a valid response,
 * and exactly the kind of thing that reads as a bug.
 */
@Component
public class CatalogLoader implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(CatalogLoader.class);

    private final CatalogToyRepository repository;
    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactions;
    private final String location;

    public CatalogLoader(CatalogToyRepository repository,
                         ResourceLoader resourceLoader,
                         ObjectMapper objectMapper,
                         TransactionTemplate transactions,
                         @Value("${skylanders.catalog.location:classpath:catalog.json}") String location) {
        this.repository = repository;
        this.resourceLoader = resourceLoader;
        this.objectMapper = objectMapper;
        this.transactions = transactions;
        this.location = location;
    }

    @Override
    public void afterSingletonsInstantiated() {
        load();
    }

    public void load() {
        Resource resource = resourceLoader.getResource(location);
        if (!resource.exists()) {
            // Not fatal: the app still lists whatever the agent sends, just without resolved
            // names. Failing to boot over a missing catalogue would be worse than degrading.
            log.warn("catalog.json introuvable a {} — roster non charge, les noms resteront "
                    + "non resolus", location);
            return;
        }
        CatalogFile file;
        try (InputStream in = resource.getInputStream()) {
            file = objectMapper.readValue(in, CatalogFile.class);
        } catch (IOException e) {
            log.warn("catalog.json illisible ({}) : {}", location, e.getMessage());
            return;
        }
        if (file.toys() == null || file.toys().isEmpty()) {
            log.warn("catalog.json ne contient aucune entree ({})", location);
            return;
        }

        CatalogFile loaded = file;
        int[] counters = transactions.execute(status -> {
            int created = 0;
            int updated = 0;
            for (CatalogFile.Toy entry : loaded.toys()) {
                ToyKey key = new ToyKey(entry.toyId(), entry.variantId());
                CatalogToy existing = repository.findById(key).orElse(null);
                if (existing == null) {
                    repository.save(new CatalogToy(key, entry.nameEn(), entry.nameFr(),
                            entry.game(), entry.element(), entry.category(), entry.confidence()));
                    created++;
                } else {
                    existing.update(entry.nameEn(), entry.nameFr(), entry.game(),
                            entry.element(), entry.category(), entry.confidence());
                    updated++;
                }
            }
            return new int[]{created, updated};
        });
        log.info("Catalogue charge depuis {} : {} entrees ({} creees, {} mises a jour)",
                location, file.toys().size(), counters[0], counters[1]);
    }
}
