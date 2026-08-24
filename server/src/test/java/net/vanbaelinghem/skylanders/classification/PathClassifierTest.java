package net.vanbaelinghem.skylanders.classification;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Rules of SPEC.md §5.2, including the tree inconsistencies the app absorbs as-is. */
class PathClassifierTest {

    private final PathClassifier classifier = new PathClassifier();

    @Test
    void classifiesACharacter() {
        Classification c = classifier.classify("Skylanders Trap Team/Feu/Wildfire.sky");
        assertThat(c.game()).isEqualTo(Game.TRAP_TEAM);
        assertThat(c.category()).isEqualTo(Category.CHARACTER);
        assertThat(c.element()).isEqualTo(Element.FEU);
        assertThat(c.variantFolder()).isFalse();
    }

    @Test
    void classifiesATrapUnderItsElement() {
        Classification c = classifier.classify(
                "Skylanders Trap Team/Pièges/Air/Buzzer Beak - Avis de Tempête.sky");
        assertThat(c.category()).isEqualTo(Category.TRAP);
        assertThat(c.element()).isEqualTo(Element.AIR);
    }

    @Test
    @DisplayName("Kaos occupe la place d'un élément dans l'arborescence — absorbé tel quel")
    void absorbsTheKaosPseudoElement() {
        Classification c = classifier.classify("Skylanders Trap Team/Pièges/Kaos/Piège Kaos.sky");
        assertThat(c.category()).isEqualTo(Category.TRAP);
        assertThat(c.element()).isEqualTo(Element.KAOS);
    }

    @Test
    @DisplayName("les variantes de personnage sont dans un sous-dossier, celles de véhicule à plat")
    void handlesTheVehicleVariantAnomaly() {
        Classification character = classifier.classify(
                "Skylanders Trap Team/Feu/Variantes/Wildfire Sombre.sky");
        assertThat(character.category()).isEqualTo(Category.CHARACTER);
        assertThat(character.variantFolder()).isTrue();

        // SPEC.md §5.3 : pas de sous-dossier Variantes/ pour les véhicules, le statut se déduit
        // du nom. La classification reste VEHICLE dans les deux cas.
        Classification vehicle = classifier.classify(
                "Skylanders SuperChargers/Feu/Véhicules/Bolide Ardent Doré.sky");
        assertThat(vehicle.category()).isEqualTo(Category.VEHICLE);
        assertThat(vehicle.element()).isEqualTo(Element.FEU);
        assertThat(vehicle.variantFolder()).isFalse();
    }

    @Test
    void classifiesCategoryFolders() {
        assertThat(classifier.classify("Skylanders Trap Team/Minis/Bop.sky").category())
                .isEqualTo(Category.MINI);
        assertThat(classifier.classify("Skylanders Giants/Acolytes/Barkley.sky").category())
                .isEqualTo(Category.SIDEKICK);
        assertThat(classifier.classify("Skylanders Imaginators/Coffres/Coffre Or 1.sky").category())
                .isEqualTo(Category.CHEST);
        assertThat(classifier.classify("Skylanders Giants/Géants/Eye Brawl.sky").category())
                .isEqualTo(Category.GIANT);
    }

    @Test
    @DisplayName("un chemin inconnu retombe sur UNKNOWN sans lever d'exception")
    void fallsBackGracefully() {
        Classification c = classifier.classify("Jeu Inconnu/Dossier Bizarre/truc.sky");
        assertThat(c.game()).isEqualTo(Game.UNKNOWN);
        assertThat(c.category()).isEqualTo(Category.UNKNOWN);
        assertThat(c.element()).isEqualTo(Element.UNKNOWN);
    }
}
