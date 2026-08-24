package net.vanbaelinghem.skylanders.classification;

import java.util.Map;

/** Level-1 folder of the source tree (SPEC.md §5.1). */
public enum Game {
    SPYROS_ADVENTURE,
    GIANTS,
    SWAP_FORCE,
    TRAP_TEAM,
    SUPERCHARGERS,
    IMAGINATORS,
    UNKNOWN;

    private static final Map<String, Game> BY_FOLDER = Map.of(
            "Skylanders Spyros Adventure", SPYROS_ADVENTURE,
            "Skylanders Giants", GIANTS,
            "Skylanders Swap Force", SWAP_FORCE,
            "Skylanders Trap Team", TRAP_TEAM,
            "Skylanders SuperChargers", SUPERCHARGERS,
            "Skylanders Imaginators", IMAGINATORS);

    public static Game fromFolder(String folder) {
        return BY_FOLDER.getOrDefault(folder, UNKNOWN);
    }
}
