package net.vanbaelinghem.skylanders.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class VillainMatchTest {

    @Test
    @DisplayName("l'article initial du roster ne casse pas le rapprochement")
    void leadingArticleIsIgnored() {
        // Cas réel : le roster du wiki dit « The Gulper », l'utilisateur avait tapé « Gulper ».
        assertThat(VillainService.matchKey("Gulper"))
                .isEqualTo(VillainService.matchKey("The Gulper"));
    }

    @Test
    @DisplayName("casse, accents et separateurs sont absorbes")
    void foldingAbsorbsTypography() {
        assertThat(VillainService.matchKey("buzzer-beak"))
                .isEqualTo(VillainService.matchKey("Buzzer Beak"));
        assertThat(VillainService.matchKey("Dr. Krankcase"))
                .isEqualTo(VillainService.matchKey("dr krankcase"));
    }

    @Test
    @DisplayName("deux vilains differents ne se confondent pas")
    void distinctVillainsStayDistinct() {
        assertThat(VillainService.matchKey("Chompy"))
                .isNotEqualTo(VillainService.matchKey("Chompy Mage"));
        // « The » n'est retire qu'en tete, et jamais au point de vider le nom.
        assertThat(VillainService.matchKey("Threatpack"))
                .isNotEqualTo(VillainService.matchKey("atpack"));
    }
}
