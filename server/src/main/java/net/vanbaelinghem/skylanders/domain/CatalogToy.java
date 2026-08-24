package net.vanbaelinghem.skylanders.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Full roster, owned or not (SPEC.md §7.1). This table never says whether the user has played
 * with a toy — that is {@code toy.first_played_at} and nothing else.
 */
@Entity
@Table(name = "catalog_toy")
public class CatalogToy {

    @EmbeddedId
    private ToyKey key;

    @Column(name = "name_en", nullable = false)
    private String nameEn;

    @Column(name = "name_fr", nullable = false)
    private String nameFr;

    @Column(nullable = false)
    private String game;

    @Column(nullable = false)
    private String element;

    @Column(nullable = false)
    private String category;

    @Column(nullable = false)
    private String confidence;

    protected CatalogToy() {}

    public CatalogToy(ToyKey key, String nameEn, String nameFr,
                      String game, String element, String category, String confidence) {
        this.key = key;
        this.nameEn = nameEn;
        this.nameFr = nameFr;
        this.game = game;
        this.element = element;
        this.category = category;
        this.confidence = confidence;
    }

    public ToyKey getKey() { return key; }
    public String getNameEn() { return nameEn; }
    public String getNameFr() { return nameFr; }
    public String getGame() { return game; }
    public String getElement() { return element; }
    public String getCategory() { return category; }
    public String getConfidence() { return confidence; }

    public void update(String nameEn, String nameFr, String game,
                       String element, String category, String confidence) {
        this.nameEn = nameEn;
        this.nameFr = nameFr;
        this.game = game;
        this.element = element;
        this.category = category;
        this.confidence = confidence;
    }
}
