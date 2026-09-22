package net.vanbaelinghem.skylanders.catalog;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** Shape of {@code villains.json}, produced by {@code tools/villains_bootstrap.py}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record VillainFile(List<Villain> villains) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Villain(
            String name,
            String element,
            boolean doomRaider,
            String wiki,
            String summary) {}
}
