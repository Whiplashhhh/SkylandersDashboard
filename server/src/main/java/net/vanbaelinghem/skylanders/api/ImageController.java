package net.vanbaelinghem.skylanders.api;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.vanbaelinghem.skylanders.classification.Element;
import net.vanbaelinghem.skylanders.classification.Game;
import net.vanbaelinghem.skylanders.domain.CatalogToy;
import net.vanbaelinghem.skylanders.domain.CatalogToyRepository;
import net.vanbaelinghem.skylanders.domain.ToyKey;
import net.vanbaelinghem.skylanders.domain.Villain;
import net.vanbaelinghem.skylanders.domain.VillainRepository;
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

    private static final Logger log = LoggerFactory.getLogger(ImageController.class);

    /** Extensions acceptées, dans l'ordre de préférence. */
    private static final List<String> EXTENSIONS = List.of(".webp", ".png", ".jpg", ".jpeg");

    private final CatalogToyRepository catalog;
    private final VillainRepository villains;
    private final Path root;

    public ImageController(CatalogToyRepository catalog, VillainRepository villains,
                           @Value("${skylanders.images.location:./images}") String location) {
        this.catalog = catalog;
        this.villains = villains;
        this.root = locate(location);
    }

    /**
     * Resolves the image folder, and says out loud what it found.
     *
     * <p>The path is relative, so it depends on the working directory — and the documented way to
     * start the server is {@code cd server && mvn spring-boot:run}, from where {@code ./images}
     * points at a folder that does not exist. The symptom is not an error but silence: every
     * figurine falls back to its generated badge, which looks like a broken import rather than a
     * misplaced folder. Relative paths are also tried one level up; for the default, a launch
     * from the shared workspace also checks {@code SkylandersDashboard/images}. The outcome is
     * logged (CLAUDE.md: never fail silently on a file).
     */
    private static Path locate(String location) {
        Path configured = Path.of(location).toAbsolutePath().normalize();
        Path chosen = resolveRoot(location, Path.of("").toAbsolutePath());
        if (!Files.isDirectory(chosen)) {
            log.warn("Dossier d'images introuvable ({}) — toutes les figurines tomberont sur "
                    + "leur pastille generee. Renseigner IMAGES_DIR pour y remedier.", configured);
            return chosen;
        }
        long count;
        try (var entries = Files.list(chosen)) {
            count = entries.filter(Files::isRegularFile).count();
        } catch (IOException e) {
            count = -1;
        }
        log.info("Images servies depuis {} ({} fichiers)", chosen, count);
        return chosen;
    }

    static Path resolveRoot(String location, Path workingDirectory) {
        Path requested = Path.of(location);
        Path configured = workingDirectory.resolve(requested).normalize();
        if (Files.isDirectory(configured) || requested.isAbsolute()) {
            return configured;
        }
        Path fromParent = workingDirectory.resolve("..").resolve(requested).normalize();
        if (Files.isDirectory(fromParent)) {
            return fromParent;
        }
        // IntelliJ may launch from the shared workspace containing both repositories.
        // Only infer this location for the default; never replace a custom IMAGES_DIR.
        if (requested.normalize().equals(Path.of("images"))) {
            Path fromWorkspace = workingDirectory.resolve("SkylandersDashboard/images").normalize();
            if (Files.isDirectory(fromWorkspace)) {
                return fromWorkspace;
            }
        }
        return configured;
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

    /**
     * Game logo, supplied by hand as {@code game_TRAP_TEAM.webp}.
     *
     * <p>Returns 404 when the file is missing, deliberately: a generated badge would carry baked
     * colours and stay dark in a light theme. The frontend renders the game name as themed text
     * instead, which is what a fallback should look like.
     */
    @GetMapping("/game/{name}")
    public ResponseEntity<byte[]> gameLogo(@PathVariable String name) {
        Game game;
        try {
            game = Game.valueOf(name.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
        Path file = find("game_" + game.name());
        if (file == null) {
            return ResponseEntity.notFound().build();
        }
        return served(file, () -> new byte[0]);
    }

    private java.nio.file.Path find(String stem) {
        for (String extension : List.of(".webp", ".png", ".jpg", ".jpeg", ".svg")) {
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
                MediaType type = mediaType(fileName);
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


    @GetMapping("/{toyId}/{variantId}")
    public ResponseEntity<byte[]> image(@PathVariable int toyId, @PathVariable int variantId) {
        // Path components are ints, so no traversal is reachable here. The variant-specific file
        // wins over the base one, so a Legendary can carry its own artwork.
        Path file = find(toyId + "_" + variantId);
        if (file == null) {
            file = find(String.valueOf(toyId));
        }
        return served(file, () -> badge(toyId, variantId));
    }

    /**
     * Artwork of the villain locked in a trap, keyed by its raw id.
     *
     * <p>The file is named after the villain, slugified — {@code villain_buzzerbeak.webp}. The
     * server slugifies the name held in the reference the same way, so naming a villain correctly
     * in the UI is what binds it to its picture. That coupling is deliberate: no external source
     * maps a raw id to a name (SPEC.md §7.2), so the user's own naming is the only key available.
     */
    @GetMapping("/villain/{rawId}")
    public ResponseEntity<byte[]> villainImage(@PathVariable int rawId) {
        String name = villains.findById(rawId).map(Villain::getName).orElse(null);
        Path file = name == null ? null : find("villain_" + slug(name));
        if (file == null) {
            file = find("villain_" + rawId);
        }
        final String label = name;
        return served(file, () -> initialsBadge(
                label != null ? label : "?",
                label != null ? Element.UNKNOWN.color() : "#6b7280"));
    }

    /**
     * Villain artwork by name.
     *
     * <p>The roster screen knows villains by name, not by raw id — a villain never captured has
     * no raw id at all, and still deserves its picture. Same file as the raw-id route resolves
     * to: {@code villain_<slug>.<ext>}.
     */
    @GetMapping("/villain/name/{name}")
    public ResponseEntity<byte[]> villainImageByName(@PathVariable String name) {
        // Slugified before touching the filesystem: the value comes from a URL.
        return served(find("villain_" + slug(name)),
                () -> initialsBadge(name, Element.UNKNOWN.color()));
    }

    /** Lowercase alphanumerics only: « Buzzer Beak » and « buzzer-beak » land on the same file. */
    static String slug(String text) {
        String folded = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return folded.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static MediaType mediaType(String fileName) {
        if (fileName.endsWith(".svg")) {
            return MediaType.valueOf("image/svg+xml");
        }
        if (fileName.endsWith(".webp")) {
            return MediaType.valueOf("image/webp");
        }
        if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
            return MediaType.IMAGE_JPEG;
        }
        return MediaType.IMAGE_PNG;
    }

    private byte[] badge(int toyId, int variantId) {
        CatalogToy entry = catalog.findById(new ToyKey(toyId, variantId)).orElse(null);
        String name = entry != null ? entry.getNameFr() : String.valueOf(toyId);
        Element element = entry != null ? Element.fromLabel(entry.getElement()) : Element.UNKNOWN;
        return initialsBadge(name, element.color());
    }

    private byte[] initialsBadge(String name, String color) {
        String svg = """
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 200 200" width="200" height="200" \
                role="img" aria-label="%s">
                  <rect width="200" height="200" rx="18" fill="%s"/>
                  <text x="100" y="100" text-anchor="middle" dominant-baseline="central" \
                fill="#ffffff" font-family="system-ui, sans-serif" font-size="72" \
                font-weight="600">%s</text>
                </svg>
                """.formatted(escape(name), color, escape(initials(name)));
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
