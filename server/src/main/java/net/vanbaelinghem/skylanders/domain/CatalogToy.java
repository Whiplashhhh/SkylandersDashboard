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

    /**
     * Verified Fandom page title, or {@code null} when no page matches.
     *
     * <p>Resolved offline by {@code tools/wiki_links.py} and versioned in {@code catalog.json} —
     * never derived at runtime. The pack's names are French, sometimes run together
     * (« Hoodsickle » for « Hood Sickle ») and carry known typos, so a rule
     * that guessed would hand out dead links without saying so. {@code null} is an ordinary
     * state: traps, chests and creation crystals have no page of their own.
     */
    @Column
    private String wiki;

    protected CatalogToy() {}

    public CatalogToy(ToyKey key, String nameEn, String nameFr,
                      String game, String element, String category, String confidence,
                      String wiki) {
        this.key = key;
        this.nameEn = nameEn;
        this.nameFr = nameFr;
        this.game = game;
        this.element = element;
        this.category = category;
        this.confidence = confidence;
        this.wiki = wiki;
    }

    public ToyKey getKey() { return key; }
    public String getNameEn() { return nameEn; }
    public String getNameFr() { return nameFr; }
    public String getGame() { return game; }
    public String getElement() { return element; }
    public String getCategory() { return category; }
    public String getConfidence() { return confidence; }
    public String getWiki() { return wiki; }

    public void update(String nameEn, String nameFr, String game,
                       String element, String category, String confidence, String wiki) {
        this.nameEn = nameEn;
        this.nameFr = nameFr;
        this.game = game;
        this.element = element;
        this.category = category;
        this.confidence = confidence;
        this.wiki = wiki;
    }
}
