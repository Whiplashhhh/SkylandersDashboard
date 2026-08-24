package net.vanbaelinghem.skylanders.classification;

import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Derives game, category and element from the received relative path.
 *
 * <p>The tree carries metadata that is absent from the files themselves (SPEC.md §5). Rules are
 * applied in order, first match wins. Known inconsistencies in the tree are absorbed here; the
 * source files are never reorganised (CLAUDE.md invariant 4).
 */
@Component
public class PathClassifier {

    private static final String VARIANTS = "Variantes";
    private static final String TRAPS = "Pièges";
    private static final String VEHICLES = "Véhicules";

    public Classification classify(String relativePath) {
        List<String> parts = Arrays.asList(relativePath.split("/"));
        if (parts.size() < 2) {
            return new Classification(Game.UNKNOWN, Category.UNKNOWN, Element.UNKNOWN, false);
        }
        Game game = Game.fromFolder(parts.get(0));
        // Intermediate folders, excluding the game and the file name itself.
        List<String> mid = parts.subList(1, parts.size() - 1);
        boolean variant = mid.contains(VARIANTS);

        // 1 & 2 — <J>/Pièges/<E>[/Variantes]/f.sky
        if (!mid.isEmpty() && TRAPS.equals(mid.get(0))) {
            Element element = mid.size() > 1 ? Element.fromFolder(mid.get(1)) : Element.UNKNOWN;
            return new Classification(game, Category.TRAP, element, variant);
        }
        // 3 — <J>/<E>/Véhicules/f.sky
        if (mid.size() >= 2 && Element.isElementFolder(mid.get(0)) && VEHICLES.equals(mid.get(1))) {
            return new Classification(game, Category.VEHICLE, Element.fromFolder(mid.get(0)), variant);
        }
        // 4 — <J>/<E>/Variantes/f.sky
        if (mid.size() >= 2 && Element.isElementFolder(mid.get(0)) && VARIANTS.equals(mid.get(1))) {
            return new Classification(game, Category.CHARACTER, Element.fromFolder(mid.get(0)), true);
        }
        // 5 — <J>/<Cat>/f.sky
        if (!mid.isEmpty() && Category.isCategoryFolder(mid.get(0))) {
            return new Classification(game, Category.fromFolder(mid.get(0)), Element.UNKNOWN, variant);
        }
        // 6 — <J>/<E>/f.sky
        if (!mid.isEmpty() && Element.isElementFolder(mid.get(0))) {
            return new Classification(game, Category.CHARACTER, Element.fromFolder(mid.get(0)), variant);
        }
        // 7 — default
        return new Classification(game, Category.UNKNOWN, Element.UNKNOWN, variant);
    }
}
