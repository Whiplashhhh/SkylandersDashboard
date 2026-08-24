package net.vanbaelinghem.skylanders.api;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import net.vanbaelinghem.skylanders.classification.Element;
import net.vanbaelinghem.skylanders.classification.Game;
import net.vanbaelinghem.skylanders.domain.CatalogToy;
import net.vanbaelinghem.skylanders.domain.CatalogToyRepository;
import net.vanbaelinghem.skylanders.domain.ToyKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves a figurine's artwork, or a generated badge when there is none.
 *
 * <p>Visuals are Activision copyright, so there is no scraper and nothing is bundled: the folder is
 * filled by hand and mounted from outside the image (SPEC.md §10.3, decision of 2026-08-23).
 * <strong>The application must be fully usable with zero images</strong> — hence the fallback is
 * not an error path but the normal one.
 *
 * <p>Greying out and the padlock for a locked entry are pure CSS on the frontend, applied to this
 * very same image. Never a separate silhouette asset (SPEC.md §10.3).
 */
@RestController
@RequestMapping("/api/images")
@Transactional(readOnly = true)
public class ImageController {

    private final CatalogToyRepository catalog;
    private final Path root;

    public ImageController(CatalogToyRepository catalog,
                           @Value("${skylanders.images.location:./images}") String location) {
        this.catalog = catalog;
        this.root = Path.of(location).toAbsolutePath().normalize();
    }

    @GetMapping("/{toyId}")
    public ResponseEntity<byte[]> image(@PathVariable int toyId) {
        return image(toyId, 0);
    }

    /**
     * Element symbol. The pack already ships one per element
     * ({@code AirSymbolSkylanders.png} and friends, SPEC.md §5.5); {@code tools/import_images.py}
     * copies them into the mounted folder. The server never reads the laptop itself
     * (CLAUDE.md invariant 1), so the files must be placed there deliberately.
     */
    @GetMapping("/element/{name}")
    public ResponseEntity<byte[]> elementIcon(@PathVariable String name) {
        if ("all".equalsIgnoreCase(name)) {
            return served(find("element_all"), this::allElementsBadge);
        }
        // Whitelisted through the enum: the value comes from a URL and must never reach the
        // filesystem as-is.
        Element element = Element.fromLabel(name);
        if (element == Element.UNKNOWN && !Element.UNKNOWN.label().equalsIgnoreCase(name)) {
            return ResponseEntity.notFound().build();
        }
        return served(find("element_" + element.name()), () -> elementBadge(element));
    }

    /** Game logo, supplied by hand as {@code game_TRAP_TEAM.png}; falls back to the game name. */
    @GetMapping("/game/{name}")
    public ResponseEntity<byte[]> gameLogo(@PathVariable String name) {
        Game game;
        try {
            game = Game.valueOf(name.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
        return served(find("game_" + game.name()), () -> gameBadge(game));
    }

    private java.nio.file.Path find(String stem) {
        for (String extension : List.of(".png", ".jpg", ".svg")) {
            Path file = root.resolve(stem + extension);
            if (Files.isReadable(file)) {
                return file;
            }
        }
        return null;
    }

    private ResponseEntity<byte[]> served(Path file, java.util.function.Supplier<byte[]> fallback) {
        if (file != null) {
            try {
                String fileName = file.getFileName().toString();
                MediaType type = fileName.endsWith(".svg") ? MediaType.valueOf("image/svg+xml")
                        : fileName.endsWith(".jpg") ? MediaType.IMAGE_JPEG : MediaType.IMAGE_PNG;
                return ResponseEntity.ok().contentType(type)
                        .cacheControl(CacheControl.maxAge(java.time.Duration.ofHours(6)))
                        .body(Files.readAllBytes(file));
            } catch (IOException ignored) {
                // fall through to the generated badge
            }
        }
        return ResponseEntity.ok().contentType(MediaType.valueOf("image/svg+xml"))
                .cacheControl(CacheControl.noCache()).body(fallback.get());
    }

    private byte[] elementBadge(Element element) {
        return ("""
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64" width="64" height="64"                 role="img" aria-label="%s">
                  <circle cx="32" cy="32" r="28" fill="%s"/>
                  <text x="32" y="33" text-anchor="middle" dominant-baseline="central"                 fill="#fff" font-family="system-ui, sans-serif" font-size="26"                 font-weight="700">%s</text>
                </svg>
                """).formatted(escape(element.label()), element.color(),
                escape(element.label().substring(0, 1))).getBytes(StandardCharsets.UTF_8);
    }

    private byte[] allElementsBadge() {
        StringBuilder slices = new StringBuilder();
        Element[] wheel = {Element.FEU, Element.EAU, Element.VIE, Element.MAGIE,
                Element.TECH, Element.TERRE, Element.AIR, Element.MORT_VIVANT};
        for (int i = 0; i < wheel.length; i++) {
            double a0 = Math.PI * 2 * i / wheel.length - Math.PI / 2;
            double a1 = Math.PI * 2 * (i + 1) / wheel.length - Math.PI / 2;
            slices.append(("<path d=\"M32 32 L%.2f %.2f A28 28 0 0 1 %.2f %.2f Z\" fill=\"%s\"/>")
                    .formatted(32 + 28 * Math.cos(a0), 32 + 28 * Math.sin(a0),
                            32 + 28 * Math.cos(a1), 32 + 28 * Math.sin(a1), wheel[i].color()));
        }
        return ("""
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64" width="64" height="64"                 role="img" aria-label="Tous les éléments">%s<circle cx="32" cy="32" r="12"                 fill="#1d2029"/></svg>
                """).formatted(slices).getBytes(StandardCharsets.UTF_8);
    }

    private byte[] gameBadge(Game game) {
        String label = switch (game) {
            case SPYROS_ADVENTURE -> "Spyro's Adventure";
            case GIANTS -> "Giants";
            case SWAP_FORCE -> "Swap Force";
            case TRAP_TEAM -> "Trap Team";
            case SUPERCHARGERS -> "SuperChargers";
            case IMAGINATORS -> "Imaginators";
            case UNKNOWN -> "Inconnu";
        };
        return ("""
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 220 64" width="220"                 height="64" role="img" aria-label="%s">
                  <rect width="220" height="64" rx="10" fill="#262a35"/>
                  <text x="110" y="33" text-anchor="middle" dominant-baseline="central"                 fill="#e8eaf0" font-family="system-ui, sans-serif" font-size="19"                 font-weight="600">%s</text>
                </svg>
                """).formatted(escape(label), escape(label)).getBytes(StandardCharsets.UTF_8);
    }

    @GetMapping("/{toyId}/{variantId}")
    public ResponseEntity<byte[]> image(@PathVariable int toyId, @PathVariable int variantId) {
        // Path components are ints, so no traversal is reachable here.
        List<String> candidates = List.of(
                toyId + "_variant" + variantId + ".png",
                toyId + "_variant" + variantId + ".jpg",
                toyId + ".png",
                toyId + ".jpg");
        for (String candidate : candidates) {
            Path file = root.resolve(candidate);
            if (Files.isReadable(file)) {
                try {
                    MediaType type = candidate.endsWith(".png")
                            ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG;
                    return ResponseEntity.ok()
                            .contentType(type)
                            .cacheControl(CacheControl.maxAge(java.time.Duration.ofHours(6)))
                            .body(Files.readAllBytes(file));
                } catch (IOException e) {
                    break; // unreadable after all — fall through to the badge
                }
            }
        }
        return ResponseEntity.ok()
                .contentType(MediaType.valueOf("image/svg+xml"))
                .cacheControl(CacheControl.noCache())
                .body(badge(toyId, variantId));
    }

    private byte[] badge(int toyId, int variantId) {
        CatalogToy entry = catalog.findById(new ToyKey(toyId, variantId)).orElse(null);
        String name = entry != null ? entry.getNameFr() : String.valueOf(toyId);
        Element element = entry != null ? Element.fromLabel(entry.getElement()) : Element.UNKNOWN;
        String svg = """
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 200 200" width="200" height="200" \
                role="img" aria-label="%s">
                  <rect width="200" height="200" rx="18" fill="%s"/>
                  <text x="100" y="100" text-anchor="middle" dominant-baseline="central" \
                fill="#ffffff" font-family="system-ui, sans-serif" font-size="72" \
                font-weight="600">%s</text>
                </svg>
                """.formatted(escape(name), element.color(), escape(initials(name)));
        return svg.getBytes(StandardCharsets.UTF_8);
    }

    static String initials(String name) {
        String[] words = name.trim().split("[\\s'-]+");
        StringBuilder out = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty() && out.length() < 2) {
                out.append(Character.toUpperCase(word.charAt(0)));
            }
        }
        return out.isEmpty() ? "?" : out.toString().toUpperCase(Locale.ROOT);
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
