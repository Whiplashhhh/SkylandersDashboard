package net.vanbaelinghem.skylanders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import net.vanbaelinghem.skylanders.classification.Category;
import net.vanbaelinghem.skylanders.classification.Classification;
import net.vanbaelinghem.skylanders.classification.Element;
import net.vanbaelinghem.skylanders.classification.Game;
import net.vanbaelinghem.skylanders.classification.PathClassifier;
import net.vanbaelinghem.skylanders.format.SkyCrypto;
import net.vanbaelinghem.skylanders.format.ToyIdentity;
import net.vanbaelinghem.skylanders.format.ToyIdentityParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Non-regression sweep over the whole pack (CLAUDE.md, "Test de non-régression global").
 *
 * <p>A sharp drop in any of these rates means an offset broke. The test is skipped when the pack
 * is not on the machine — it exercises the user's own data, which is not in the repository.
 */
class PackRegressionTest {

    private static final Path ROOT =
            Path.of(System.getProperty("user.home"), "Games", "Cemu", "skylanders");

    /** Measured 2026-08-23: 702 files, all classified, 11 trap toy IDs for 11 elements. */
    private static final int EXPECTED_FILE_COUNT = 702;

    private static List<Path> dumps;

    @BeforeAll
    static void collect() throws IOException {
        assumeTrue(Files.isDirectory(ROOT), "pack absent de cette machine — test ignore");
        try (Stream<Path> walk = Files.walk(ROOT)) {
            dumps = walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".sky"))
                    .sorted()
                    .toList();
        }
    }

    @Test
    @DisplayName("les 702 fichiers font 1024 octets et se classent tous")
    void everyFileIsWellFormedAndClassified() throws IOException {
        assertThat(dumps).hasSize(EXPECTED_FILE_COUNT);
        PathClassifier classifier = new PathClassifier();
        List<String> unclassified = new ArrayList<>();
        for (Path path : dumps) {
            assertThat(Files.size(path)).as(path.toString()).isEqualTo(SkyCrypto.DUMP_SIZE);
            Classification c = classifier.classify(ROOT.relativize(path).toString().replace('\\', '/'));
            if (c.game() == Game.UNKNOWN || c.category() == Category.UNKNOWN) {
                unclassified.add(path.toString());
            }
        }
        assertThat(unclassified).isEmpty();
    }

    @Test
    @DisplayName("tout dump doté d'une vraie zone de sauvegarde se déchiffre en clair lisible")
    void decryptionStaysPlausibleAcrossThePack() throws IOException {
        int sampled = 0;
        int implausible = 0;
        for (Path path : dumps) {
            byte[] raw = Files.readAllBytes(path);
            int written = 0;
            for (int block = 0; block < SkyCrypto.BLOCK_COUNT; block++) {
                if (SkyCrypto.isEncryptedBlock(block) && !SkyCrypto.isBlankBlock(raw, block)) {
                    written++;
                }
            }
            // Les objets Imaginators a empreinte minuscule n'utilisent pas ce schema de
            // chiffrement (FORMAT.md §8.2). Le seuil porte sur le NOMBRE DE BLOCS ECRITS, pas sur
            // la categorie : la mesure du 2026-08-23 donne une separation nette et sans
            // recouvrement — 142 fichiers a 4 ou 5 blocs, tous bruites ; 140 fichiers a 11 blocs
            // ou plus, tous propres ; et AUCUN fichier entre 6 et 10 blocs. N'importe quel seuil
            // de cet intervalle vide convient, ce qui rend le test insensible a son choix exact.
            if (written < 6) {
                continue;
            }
            byte[] decrypted = SkyCrypto.decrypt(raw);
            int zeros = 0;
            int total = 0;
            for (int block = 0; block < SkyCrypto.BLOCK_COUNT; block++) {
                if (!SkyCrypto.isEncryptedBlock(block) || SkyCrypto.isBlankBlock(raw, block)) {
                    continue;
                }
                for (int i = block * 16; i < block * 16 + 16; i++) {
                    total++;
                    if (decrypted[i] == 0) {
                        zeros++;
                    }
                }
            }
            sampled++;
            if ((double) zeros / total < 0.20) {
                implausible++;
            }
        }
        // 140 fichiers au 2026-08-23, 140 conformes. Ce nombre ne peut que croitre en jouant.
        assertThat(sampled).isGreaterThanOrEqualTo(140);
        assertThat(sampled).as("des fichiers ont cesse d'etre vus comme ecrits").isPositive();
        assertThat(implausible)
                .as("un déchiffrement bruité signale une constante ou une clé cassée")
                .isZero();
    }

    @Test
    @DisplayName("chez les pièges, un toy ID = un élément et les variant ID restent distincts")
    void trapIdentityPartitionHolds() throws IOException {
        PathClassifier classifier = new PathClassifier();
        ToyIdentityParser parser = new ToyIdentityParser();
        Map<Integer, Element> elementByToyId = new HashMap<>();
        Set<String> seenIdentities = new HashSet<>();
        List<String> collisions = new ArrayList<>();
        int traps = 0;

        for (Path path : dumps) {
            String relative = ROOT.relativize(path).toString().replace('\\', '/');
            Classification c = classifier.classify(relative);
            if (c.category() != Category.TRAP) {
                continue;
            }
            traps++;
            ToyIdentity id = parser.parse(Files.readAllBytes(path));
            Element previous = elementByToyId.putIfAbsent(id.toyId(), c.element());
            if (previous != null && previous != c.element()) {
                collisions.add("toy ID " + id.toyId() + " : " + previous + " et " + c.element());
            }
            if (!seenIdentities.add(id.toyId() + "/" + id.variantId())) {
                collisions.add("identité dupliquée " + id.toyId() + "/" + id.variantId()
                        + " sur " + relative);
            }
        }
        assertThat(traps).isGreaterThan(50);
        assertThat(collisions).isEmpty();
        assertThat(elementByToyId).hasSize(11); // 10 éléments + Kaos (FORMAT.md §6.2)
    }
}
