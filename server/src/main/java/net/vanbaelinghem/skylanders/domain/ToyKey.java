package net.vanbaelinghem.skylanders.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

/** Business identity of a model: (toy ID, variant ID). See FORMAT.md §6. */
@Embeddable
public class ToyKey implements Serializable {

    @Column(name = "toy_id", nullable = false)
    private int toyId;

    @Column(name = "variant_id", nullable = false)
    private int variantId;

    protected ToyKey() {}

    public ToyKey(int toyId, int variantId) {
        this.toyId = toyId;
        this.variantId = variantId;
    }

    public int getToyId() {
        return toyId;
    }

    public int getVariantId() {
        return variantId;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ToyKey k && k.toyId == toyId && k.variantId == variantId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(toyId, variantId);
    }
}
