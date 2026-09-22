-- catalog_villain : roster des vilains de Trap Team, capture ou non.
--
-- Pendant de catalog_toy pour les vilains, alimente par villains.json au demarrage. Ne dit
-- JAMAIS si l'utilisateur a capture le vilain : ca se lit sur trap_content, via le nom que
-- l'utilisateur a donne a l'identifiant brut (SPEC.md §7.2). Meme raisonnement que
-- l'invariant 5 de CLAUDE.md : la presence d'une ligne ici n'est pas un deblocage.
--
-- La table `villain` existante garde son role et n'est pas touchee : elle relie un
-- identifiant BRUT lu sur un tag a un nom, ce qu'aucune source externe ne sait faire. Celle-ci
-- dit seulement ce qui existe dans le jeu.
CREATE TABLE catalog_villain
(
    name         text PRIMARY KEY,
    element      text    NOT NULL,

    -- Boss de son element. Le wiki est formel : la Magie n'a pas de Doom Raider, et Kaos en
    -- est un malgre son statut a part.
    doom_raider  boolean NOT NULL DEFAULT false,

    -- Titre de page verifie sur le wiki Fandom, comme catalog_toy.wiki.
    wiki         text,

    -- Extrait court de la page, sous licence CC BY-SA ; le lien wiki porte l'attribution.
    summary      text
);

COMMENT ON TABLE catalog_villain IS
    'Ce qui existe dans le jeu. La capture se lit sur trap_content, jamais ici.';
