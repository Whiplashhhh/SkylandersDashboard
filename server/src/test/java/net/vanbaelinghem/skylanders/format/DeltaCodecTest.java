package net.vanbaelinghem.skylanders.format;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import net.vanbaelinghem.skylanders.Fixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DeltaCodecTest {

    @Test
    @DisplayName("un delta appliqué à la référence reconstruit le dump à l'octet près")
    void roundTripsOnRealDumps() {
        byte[] baseline = Fixtures.dump("food_fight_virgin");
        byte[] current = Fixtures.dump("wildfire_played");

        byte[] delta = DeltaCodec.encode(baseline, current);
        assertThat(DeltaCodec.apply(baseline, delta)).isEqualTo(current);
    }

    @Test
    @DisplayName("un dump identique à sa référence produit un delta vide")
    void identicalDumpsProduceEmptyDelta() {
        byte[] dump = Fixtures.dump("wildfire_played");
        assertThat(DeltaCodec.encode(dump, dump)).isEmpty();
        assertThat(DeltaCodec.apply(dump, new byte[0])).isEqualTo(dump);
    }

    @Test
    @DisplayName("le delta par bloc coûte nettement moins que le dump entier")
    void blockDeltaIsSmallerThanTheDump() {
        byte[] baseline = Fixtures.dump("food_fight_virgin");
        byte[] current = Fixtures.dump("wildfire_played");
        byte[] delta = DeltaCodec.encode(baseline, current);

        assertThat(delta.length).isLessThan(SkyCrypto.DUMP_SIZE);
        assertThat(DeltaCodec.changedBlockCount(delta)).isEqualTo(delta.length / 17);
    }

    @Test
    @DisplayName("un delta de taille invalide est refusé plutôt que mal interprété")
    void rejectsMalformedDelta() {
        byte[] baseline = Fixtures.dump("food_fight_virgin");
        assertThatThrownBy(() -> DeltaCodec.apply(baseline, new byte[]{1, 2, 3}))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
