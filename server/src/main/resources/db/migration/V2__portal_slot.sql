-- Disposition du portail : quelle figurine est posee sur quel emplacement.
-- Reference : plan « Panneau Portail » §2.1.
--
-- Table volontairement betasse : elle memorise QUI est pose OU, et rien d'autre. Aucune
-- logique de collection ne se lit ici. En particulier — invariant 5 de CLAUDE.md — poser
-- une figurine sur le portail *de l'ecran* ne la debloque pas : le deblocage reste
-- toy.first_played_at, alimente par le contenu des sauvegardes et par rien d'autre.

CREATE TABLE portal_slot
(
    slot_index integer PRIMARY KEY CHECK (slot_index >= 0),

    -- Identite canonique (invariant 3 de CLAUDE.md) : le couple (toy ID, variant ID), et
    -- NON toy.id. Une meme figurine physique porte jusqu'a 10 lignes `toy` — une par chemin
    -- de fichier, donc une par dossier de jeu — et l'interface ne manipule que ce couple.
    --
    -- Volontairement sans clef etrangere : une identite recue mais absente de catalog.json
    -- (cas NEW, SPEC.md §7.2) est une vraie figurine, posable, qui n'a pourtant de ligne
    -- dans aucune table d'identites. La verification d'existence vit dans PortalService,
    -- qui sait interroger les deux sources.
    toy_id     integer     NOT NULL,
    variant_id integer     NOT NULL,

    placed_at  timestamptz NOT NULL
);

-- Une ligne n'existe que tant que l'emplacement est occupe : un emplacement vide est une
-- absence de ligne. Consequence assumee : pas de colonne `updated_at` a cote de `placed_at`,
-- les deux vaudraient toujours la meme chose et laisseraient croire a une distinction qui
-- n'existe pas.

-- Une figurine physique n'existe qu'en un exemplaire : elle ne peut pas occuper deux
-- emplacements a la fois. Reposer une figurine deja posee la deplace (PortalService.place).
CREATE UNIQUE INDEX portal_slot_occupant_idx ON portal_slot (toy_id, variant_id);

COMMENT ON TABLE portal_slot IS
    'Disposition courante du portail. Ne dit rien du deblocage : cf. toy.first_played_at.';
