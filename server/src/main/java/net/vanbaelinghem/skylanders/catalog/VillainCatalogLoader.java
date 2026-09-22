package net.vanbaelinghem.skylanders.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import net.vanbaelinghem.skylanders.domain.CatalogVillain;
import net.vanbaelinghem.skylanders.domain.CatalogVillainRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Reconciles {@code villains.json} into {@code catalog_villain} at startup.
 *
 * <p>Same contract as {@link CatalogLoader}: the JSON is the versioned source of truth and is
 * upserted, so correcting a summary is "edit the file, restart" rather than a migration. A
 * missing file degrades — the villains screen simply lists nothing — instead of stopping boot.
 */
@Component
public class VillainCatalogLoader implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(VillainCatalogLoader.class);

    private final CatalogVillainRepository repository;
    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactions;
    private final String location;

    public VillainCatalogLoader(CatalogVillainRepository repository,
                                ResourceLoader resourceLoader,
                                ObjectMapper objectMapper,
                                TransactionTemplate transactions,
                                @Value("${skylanders.villains.location:classpath:villains.json}")
                                String location) {
        this.repository = repository;
        this.resourceLoader = resourceLoader;
        this.objectMapper = objectMapper;
        this.transactions = transactions;
        this.location = location;
    }

    @Override
    public void afterSingletonsInstantiated() {
        Resource resource = resourceLoader.getResource(location);
        if (!resource.exists()) {
            log.warn("villains.json introuvable a {} — l'ecran Vilains restera vide", location);
            return;
        }
        VillainFile file;
        try (InputStream in = resource.getInputStream()) {
            file = objectMapper.readValue(in, VillainFile.class);
        } catch (IOException e) {
            log.warn("villains.json illisible ({}) : {}", location, e.getMessage());
            return;
        }
        if (file.villains() == null || file.villains().isEmpty()) {
            log.warn("villains.json ne contient aucune entree ({})", location);
            return;
        }
        VillainFile loaded = file;
        transactions.execute(status -> {
            for (VillainFile.Villain entry : loaded.villains()) {
                CatalogVillain existing = repository.findById(entry.name()).orElse(null);
                if (existing == null) {
                    repository.save(new CatalogVillain(entry.name(), entry.element(),
                            entry.doomRaider(), entry.wiki(), entry.summary()));
                } else {
                    existing.update(entry.element(), entry.doomRaider(),
                            entry.wiki(), entry.summary());
                }
            }
            return null;
        });
        log.info("Roster des vilains charge depuis {} : {} entrees", location,
                file.villains().size());
    }
}
