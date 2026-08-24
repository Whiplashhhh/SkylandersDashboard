package net.vanbaelinghem.skylanders.classification;

import java.util.Map;

public enum Category {
    CHARACTER,
    TRAP,
    VEHICLE,
    SIDEKICK,
    MINI,
    ITEM,
    ADVENTURE_PACK,
    CHEST,
    CREATION_CRYSTAL,
    GIANT,
    UNKNOWN;

    /** Level-2 folders that name a category rather than an element (SPEC.md §5.2). */
    private static final Map<String, Category> BY_FOLDER = Map.of(
            "Acolytes", SIDEKICK,
            "Minis", MINI,
            "Objets", ITEM,
            "Packs Aventure", ADVENTURE_PACK,
            "Coffres", CHEST,
            "Cristaux de Création", CREATION_CRYSTAL,
            "Géants", GIANT);

    public static boolean isCategoryFolder(String folder) {
        return BY_FOLDER.containsKey(folder);
    }

    public static Category fromFolder(String folder) {
        return BY_FOLDER.getOrDefault(folder, UNKNOWN);
    }
}
