package net.vanbaelinghem.skylanders.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One villain of the game, captured or not.
 *
 * <p>Says what exists; never whether the user holds it. That is read from {@code trap_content},
 * through the name the user gave the raw id — the same distinction as {@code catalog_toy} versus
 * {@code toy.first_played_at} (CLAUDE.md invariant 5).
 *
 * <p>Keyed by name, not by raw id, on purpose: no external source maps a raw id to a villain
 * (SPEC.md §7.2), so the name is the only thing this reference and the tags can share.
 */
@Entity
@Table(name = "catalog_villain")
public class CatalogVillain {

    @Id
    private String name;

    @Column(nullable = false)
    private String element;

    /** Boss of its element. Magic has none, and Kaos is one — both confirmed on the wiki. */
    @Column(name = "doom_raider", nullable = false)
    private boolean doomRaider;

    private String wiki;

    /** Short extract of the Fandom page, CC BY-SA; the wiki link carries the attribution. */
    @Column(columnDefinition = "text")
    private String summary;

    protected CatalogVillain() {}

    public CatalogVillain(String name, String element, boolean doomRaider,
                          String wiki, String summary) {
        this.name = name;
        this.element = element;
        this.doomRaider = doomRaider;
        this.wiki = wiki;
        this.summary = summary;
    }

    public String getName() { return name; }
    public String getElement() { return element; }
    public boolean isDoomRaider() { return doomRaider; }
    public String getWiki() { return wiki; }
    public String getSummary() { return summary; }

    public void update(String element, boolean doomRaider, String wiki, String summary) {
        this.element = element;
        this.doomRaider = doomRaider;
        this.wiki = wiki;
        this.summary = summary;
    }
}
