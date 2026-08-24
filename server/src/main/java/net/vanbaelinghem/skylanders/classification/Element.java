package net.vanbaelinghem.skylanders.classification;

import java.util.HashMap;
import java.util.Map;

/**
 * Element folder. Labels stay French — they are business data, and the pack is French
 * (CLAUDE.md, "Conventions").
 */
public enum Element {
    AIR("Air", "#7ec8e3"),
    EAU("Eau", "#2f7fd1"),
    FEU("Feu", "#e2603a"),
    LUMIERE("Lumière", "#e8c33c"),
    MAGIE("Magie", "#9b5fc0"),
    MORT_VIVANT("Mort-Vivant", "#7a8b52"),
    TENEBRES("Ténèbres", "#4a4458"),
    TERRE("Terre", "#a9743a"),
    TECH("Tech", "#d98f2b"),
    VIE("Vie", "#4fa65b"),
    /**
     * Not an element of the game, but it occupies the same slot in the tree
     * ({@code Imaginators/Kaos}, {@code Trap Team/Pièges/Kaos}) and the trap toy ID 220 is
     * allocated to it exactly like a real element (FORMAT.md §6.2). Absorbed as-is — the source
     * tree is never reorganised (CLAUDE.md invariant 4).
     */
    KAOS("Kaos", "#8b3a3a"),
    UNKNOWN("Inconnu", "#6b7280");

    private static final Map<String, Element> BY_FOLDER = new HashMap<>();

    static {
        for (Element element : values()) {
            BY_FOLDER.put(element.label, element);
        }
    }

    private final String label;

    /** Colour of the generated fallback badge when no image exists (SPEC.md §10.3). */
    private final String color;

    Element(String label, String color) {
        this.label = label;
        this.color = color;
    }

    public String label() {
        return label;
    }

    public String color() {
        return color;
    }

    public static Element fromLabel(String label) {
        return BY_FOLDER.getOrDefault(label, UNKNOWN);
    }

    public static boolean isElementFolder(String folder) {
        return BY_FOLDER.containsKey(folder) && BY_FOLDER.get(folder) != UNKNOWN;
    }

    public static Element fromFolder(String folder) {
        return BY_FOLDER.getOrDefault(folder, UNKNOWN);
    }
}
