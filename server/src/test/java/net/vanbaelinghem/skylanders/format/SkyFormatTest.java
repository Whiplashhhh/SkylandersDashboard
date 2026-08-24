package net.vanbaelinghem.skylanders.format;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.ZoneId;
import net.vanbaelinghem.skylanders.Fixtures;
import net.vanbaelinghem.skylanders.classification.Category;
import net.vanbaelinghem.skylanders.format.save.SaveProgress;
import net.vanbaelinghem.skylanders.format.save.TrapTeamSaveParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Parsing checked against real dumps whose truth was recorded in-game. */
class SkyFormatTest {

    private final ToyIdentityParser identity = new ToyIdentityParser();
    private final TrapTeamSaveParser saves = new TrapTeamSaveParser();

    @Test
    @DisplayName("le dechiffrement laisse une majorite de zeros — c'est ce qui valide la constante")
    void decryptionYieldsPlaintextNotNoise() {
        byte[] raw = Fixtures.dump("wildfire_played");
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
        // A wrong constant produces uniform noise: under 1.1% zeros, measured on eleven wrong
        // variants (FORMAT.md §2.1). The right one measured 37-94%.
        assertThat(total).isPositive();
        assertThat((double) zeros / total).isGreaterThan(0.20);
    }

    @Test
    @DisplayName("Wildfire — or, XP, surnom et ameliorations releves a l'ecran")
    void readsCharacterProgress() {
        JsonNode truth = Fixtures.truth("wildfire_played");
        byte[] raw = Fixtures.dump("wildfire_played");
        byte[] decrypted = SkyCrypto.decrypt(raw);

        ToyIdentity id = identity.parse(raw);
        assertThat(id.toyId()).isEqualTo(truth.get("toyId").asInt());
        assertThat(id.variantId()).isEqualTo(truth.get("variantId").asInt());

        SaveProgress progress = saves.parse(Category.CHARACTER, raw, decrypted);
        assertThat(progress.hasPlayEvidence()).isTrue();
        assertThat(progress.gold()).isEqualTo(truth.get("gold").asInt());
        assertThat(progress.xp()).isEqualTo(truth.get("xp").asInt());
        assertThat(progress.nickname()).isEqualTo(truth.get("nickname").asText());
        assertThat(progress.upgradesBitfield()).isEqualTo(truth.get("upgradesBitfield").asInt());
        assertThat(progress.playtimeSeconds()).isEqualTo(truth.get("playtimeSeconds").asInt());

        assertThat(progress.savedAt()).isNotNull();
        assertThat(progress.savedAt().toLocalDateTime())
                .hasToString(truth.get("lastSavedAt").asText());
        assertThat(progress.firstWrittenAt().toLocalDateTime())
                .hasToString(truth.get("firstWrittenAt").asText());
    }

    @Test
    @DisplayName("le niveau reste null — la courbe XP->niveau n'est pas mesuree, on ne l'invente pas")
    void levelIsNotInvented() {
        byte[] raw = Fixtures.dump("wildfire_played");
        SaveProgress progress = saves.parse(Category.CHARACTER, raw, SkyCrypto.decrypt(raw));
        assertThat(progress.level()).isNull();
    }

    @Test
    @DisplayName("Food Fight vierge — aucun bloc ecrit, donc jamais posee sur le portail")
    void detectsNeverPlayed() {
        JsonNode truth = Fixtures.truth("food_fight_virgin");
        byte[] raw = Fixtures.dump("food_fight_virgin");

        assertThat(SkyCrypto.isNeverPlayed(raw)).isEqualTo(truth.get("neverPlayed").asBoolean());
        assertThat(identity.parse(raw).toyId()).isEqualTo(truth.get("toyId").asInt());

        SaveProgress progress = saves.parse(Category.CHARACTER, raw, SkyCrypto.decrypt(raw));
        assertThat(progress.hasPlayEvidence()).isFalse();
        assertThat(progress.gold()).isZero();
    }

    @Test
    @DisplayName("Buzzer Beak dans Avis de Tempete — le cas de test fourni avec le pack")
    void readsTrappedVillain() {
        JsonNode truth = Fixtures.truth("buzzer_beak_storm_warning");
        byte[] raw = Fixtures.dump("buzzer_beak_storm_warning");
        byte[] decrypted = SkyCrypto.decrypt(raw);

        assertThat(identity.parse(raw).toyId()).isEqualTo(truth.get("toyId").asInt());
        TrapTeamSaveParser.TrapContent content = saves.parseTrap(raw, decrypted);
        assertThat(content.villainRawId()).isEqualTo(truth.get("villainRawId").asInt());
        assertThat(content.empty()).isFalse();
    }

    @Test
    @DisplayName("Slobber Trap dans la Fiole du Deluge — recoupe sur un fichier independant")
    void readsSecondTrappedVillain() {
        JsonNode truth = Fixtures.truth("flood_flask_slobber_trap");
        byte[] raw = Fixtures.dump("flood_flask_slobber_trap");

        assertThat(identity.parse(raw).toyId()).isEqualTo(truth.get("toyId").asInt());
        assertThat(saves.parseTrap(raw, SkyCrypto.decrypt(raw)).villainRawId())
                .isEqualTo(truth.get("villainRawId").asInt());
    }

    @Test
    @DisplayName("un piege ne rapporte aucun champ de progression de personnage")
    void trapReportsNoCharacterFields() {
        byte[] raw = Fixtures.dump("buzzer_beak_storm_warning");
        SaveProgress progress = saves.parse(Category.TRAP, raw, SkyCrypto.decrypt(raw));
        // +0x00 lit un 256 constant sur les pieges : le rapporter comme « 256 XP » serait
        // une valeur fabriquee (FORMAT.md §5.3).
        assertThat(progress.xp()).isNull();
        assertThat(progress.gold()).isNull();
        assertThat(progress.savedAt()).isNull();
        assertThat(progress.hasPlayEvidence()).isTrue();
    }

    @Test
    @DisplayName("la zone courante est celle au compteur le plus eleve, y compris au bouclage")
    void sequenceCounterSelectsCurrentArea() {
        byte[] decrypted = SkyCrypto.decrypt(Fixtures.dump("wildfire_played"));
        SaveArea a0 = SaveArea.of(decrypted, SaveArea.AREA_0);
        SaveArea a1 = SaveArea.of(decrypted, SaveArea.AREA_1);
        SaveArea current = SaveArea.current(decrypted);

        assertThat(current.sequenceCounter())
                .isEqualTo(Math.max(a0.sequenceCounter(), a1.sequenceCounter()));
        // Le compteur est un u8 : 0 vient APRES 255, pas avant.
        assertThat(SaveArea.isAhead(0, 255)).isTrue();
        assertThat(SaveArea.isAhead(255, 0)).isFalse();
        assertThat(SaveArea.isAhead(69, 68)).isTrue();
    }

    @Test
    @DisplayName("la zone perimee est un enregistrement parfaitement bien forme")
    void staleAreaLooksPerfectlyPlausible() {
        byte[] decrypted = SkyCrypto.decrypt(Fixtures.dump("wildfire_played"));
        SaveArea current = SaveArea.current(decrypted);
        SaveArea stale = current.blocks() == SaveArea.AREA_0
                ? SaveArea.of(decrypted, SaveArea.AREA_1)
                : SaveArea.of(decrypted, SaveArea.AREA_0);

        // Tout le danger de FORMAT.md §7.2 : la zone perimee n'a rien d'aberrant. Elle porte un
        // horodatage valide, un or credible, un temps de jeu coherent — simplement ceux de la
        // sauvegarde PRECEDENTE. Aucun controle de plausibilite ne peut la demasquer ; seul le
        // compteur le peut.
        assertThat(SaveArea.isAhead(current.sequenceCounter(), stale.sequenceCounter())).isTrue();
        assertThat(stale.lastSavedAt(ZoneId.systemDefault())).isNotNull();
        assertThat(stale.gold()).isNotNegative();
        assertThat(stale.playtimeSeconds()).isPositive();
        assertThat(stale.playtimeSeconds()).isLessThanOrEqualTo(current.playtimeSeconds());
        assertThat(stale.xp()).isLessThanOrEqualTo(current.xp());
    }

    @Test
    @DisplayName("l'horodatage du tag est une heure murale, independante du fuseau")
    void timestampsAreWallClock() {
        byte[] decrypted = SkyCrypto.decrypt(Fixtures.dump("wildfire_played"));
        SaveArea area = SaveArea.current(decrypted);
        assertThat(area.firstWrittenAt(ZoneId.of("UTC")).toLocalDateTime())
                .isEqualTo(area.firstWrittenAt(ZoneId.of("Europe/Paris")).toLocalDateTime());
    }
}
