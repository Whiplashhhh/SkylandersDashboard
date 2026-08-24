-- Schema initial. Reference : SPEC.md §7.1, amende par les mesures de la
-- phase 0 consignees dans FORMAT.md §7.3 (decisions actees le 2026-08-22).

-- ---------------------------------------------------------------------------
-- catalog_toy : roster complet d'un jeu, possede ou non.
-- Alimente par catalog.json au demarrage (FORMAT.md §7.3).
-- Ne dit JAMAIS si l'utilisateur a joue avec le jouet : cf. toy.first_played_at.
-- ---------------------------------------------------------------------------
CREATE TABLE catalog_toy
(
    toy_id     integer NOT NULL,
    variant_id integer NOT NULL,
    name_en    text    NOT NULL,
    name_fr    text    NOT NULL,
    game       text    NOT NULL,
    element    text    NOT NULL,
    category   text    NOT NULL,
    confidence text    NOT NULL,
    PRIMARY KEY (toy_id, variant_id)
);

COMMENT ON TABLE catalog_toy IS
    'Roster complet. Une entree sans ligne toy correspondante = jamais recue.';
COMMENT ON COLUMN catalog_toy.toy_id IS
    'Pour un piege, ce champ encode l''ELEMENT et non le modele (FORMAT.md §6.2).';

-- ---------------------------------------------------------------------------
-- toy : figurines pour lesquelles un fichier a ete recu.
-- ---------------------------------------------------------------------------
CREATE TABLE toy
(
    id              bigserial PRIMARY KEY,

    -- Cle d'identite fonctionnelle (FORMAT.md §7.1) : l'UID ne peut pas servir,
    -- 609 UID distincts seulement pour 702 fichiers, jusqu'a 10 fichiers par UID.
    file_path       text        NOT NULL UNIQUE,

    -- Stocke et affiche a titre informatif, JAMAIS comme identifiant.
    -- Volontairement sans contrainte d'unicite (FORMAT.md §7.1, §7.3).
    uid             bytea       NOT NULL,

    toy_id          integer     NOT NULL,
    variant_id      integer     NOT NULL,
    game_folder     text        NOT NULL,
    element_folder  text        NOT NULL,
    category_folder text        NOT NULL,

    first_seen_at   timestamptz NOT NULL,
    last_seen_at    timestamptz NOT NULL,
    is_present      boolean     NOT NULL DEFAULT true,

    -- = moment reel du deblocage. Alimente par l'horodatage de premiere
    -- ecriture porte par le tag lui-meme (FORMAT.md §5.1, offset logique +0x50),
    -- et non par la date d'ingestion : une figurine jouee il y a des annees est
    -- datee correctement des le premier scan. Monotone, jamais remis a NULL.
    first_played_at timestamptz,

    -- Horodatage de derniere sauvegarde lu sur le tag (FORMAT.md §5.1, +0x40).
    last_saved_at   timestamptz,

    -- Les 1024 octets du PREMIER fichier recu pour ce chemin. Sert de reference
    -- aux deltas de toy_snapshot (FORMAT.md §7.3). Immuable une fois pose.
    baseline        bytea       NOT NULL,

    CONSTRAINT toy_baseline_size CHECK (octet_length(baseline) = 1024)
);

CREATE INDEX toy_identity_idx ON toy (toy_id, variant_id);
CREATE INDEX toy_first_played_idx ON toy (first_played_at);

COMMENT ON COLUMN toy.first_seen_at IS
    'Premier fichier recu. N''est PAS le deblocage : le pack contient les 702 '
        'fichiers des le depart, cette date sera quasi identique partout.';

-- ---------------------------------------------------------------------------
-- toy_snapshot : historique de progression.
-- ---------------------------------------------------------------------------
CREATE TABLE toy_snapshot
(
    id                bigserial PRIMARY KEY,
    toy_pk            bigint      NOT NULL REFERENCES toy (id) ON DELETE CASCADE,
    captured_at       timestamptz NOT NULL,
    content_hash      bytea       NOT NULL,

    -- Blocs de 16 octets differant de toy.baseline, encodes en une suite de
    -- [index de bloc u8][16 octets]. Mesure du 2026-08-23 : 35 % du cout d'un
    -- stockage integral, contre 98 % pour un delta octet par octet (le format
    -- ecrit par blocs entiers). Calcule contre la reference et NON contre le
    -- snapshot precedent : SPEC.md §8.2 impose de rejeter un snapshot douteux,
    -- donc des trous dans une chaine seraient normaux et la rendraient
    -- irreconstructible. Ici chaque snapshot se reconstruit seul.
    delta             bytea       NOT NULL,

    xp                integer,
    level             integer,
    gold              integer,
    upgrades_bitfield integer,
    nickname          text,
    playtime_seconds  integer,
    saved_at          timestamptz,

    parse_status      text        NOT NULL,
    has_play_evidence boolean     NOT NULL,

    CONSTRAINT snapshot_delta_shape CHECK (octet_length(delta) % 17 = 0),
    CONSTRAINT snapshot_hash_size CHECK (octet_length(content_hash) = 32)
);

CREATE INDEX snapshot_toy_time_idx ON toy_snapshot (toy_pk, captured_at DESC);
CREATE UNIQUE INDEX snapshot_toy_hash_idx ON toy_snapshot (toy_pk, content_hash);

-- Colonnes volontairement ABSENTES par rapport a SPEC.md §7.1 :
--   hat_id      -- prouve non stocke sur le tag (FORMAT.md §5.2). Une colonne
--                  toujours NULL laisserait croire a une implementation
--                  manquante alors que l'information n'existe pas.
--   hero_points -- champ jamais localise. Sera ajoute si la phase 0 le trouve.

-- ---------------------------------------------------------------------------
-- trap_content : quel vilain se trouve dans quel piege, dans le temps.
-- ---------------------------------------------------------------------------
CREATE TABLE trap_content
(
    id             bigserial PRIMARY KEY,
    toy_pk         bigint      NOT NULL REFERENCES toy (id) ON DELETE CASCADE,
    captured_at    timestamptz NOT NULL,
    villain_raw_id integer     NOT NULL,
    is_empty       boolean     NOT NULL
);

CREATE INDEX trap_content_toy_time_idx ON trap_content (toy_pk, captured_at DESC);

-- ---------------------------------------------------------------------------
-- villain : referentiel auto-alimente (SPEC.md §7.2). Aucune source externe
-- ne donne la correspondance raw_id -> nom ; l'utilisateur nomme, l'app persiste.
-- ---------------------------------------------------------------------------
CREATE TABLE villain
(
    raw_id           integer PRIMARY KEY,
    name             text,
    element          text,
    named_by_user_at timestamptz
);

-- Identifiants deja observes en phase 0 (FORMAT.md §5.3), noms confirmes par
-- les noms de fichiers du pack.
INSERT INTO villain (raw_id, name, element)
VALUES (9, 'Gulper', 'Eau'),
       (12, 'Buzzer Beak', 'Air'),
       (20, 'Sheep Creep', 'Vie'),
       (32, 'Tussle Sprout', 'Terre'),
       (35, 'Slobber Trap', 'Eau'),
       -- Observes dans des pieges remplis en session, noms non releves.
       (1, NULL, 'Vie'),
       (21, NULL, 'Tech'),
       (30, NULL, 'Tech');

-- ---------------------------------------------------------------------------
-- scan_run : journal des ingestions.
-- ---------------------------------------------------------------------------
CREATE TABLE scan_run
(
    id            bigserial PRIMARY KEY,
    started_at    timestamptz NOT NULL,
    finished_at   timestamptz,
    files_scanned integer     NOT NULL DEFAULT 0,
    files_changed integer     NOT NULL DEFAULT 0,
    files_failed  integer     NOT NULL DEFAULT 0,
    trigger       text        NOT NULL
);
