package net.vanbaelinghem.skylanders.catalog;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** Shape of {@code catalog.json} (SPEC.md §6.5), produced by {@code tools/catalog_bootstrap.py}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CatalogFile(List<Toy> toys) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Toy(
            int toyId,
            int variantId,
            String nameEn,
            String nameFr,
            String game,
            String element,
            String category,
            String confidence) {}
}
