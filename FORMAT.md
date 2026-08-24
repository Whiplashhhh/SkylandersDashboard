# FORMAT.md — format `.sky`, offsets et niveaux de preuve

> **Source de vérité technique du projet.** Tout offset utilisé dans le code doit référencer une entrée de ce fichier
> avec son niveau de preuve (CLAUDE.md, invariant 2). Un offset sans référence est un bug.

**Niveaux de preuve** (SPEC.md §4.6) :

| Niveau       | Signification                                                       |
|--------------|---------------------------------------------------------------------|
| `VÉRIFIÉ`    | Validé par diff reproductible sur ≥ 2 figurines                     |
| `PROBABLE`   | Cohérent avec une source externe ou une mesure indirecte, non testé |
| `HYPOTHÈSE`  | Supposé, non validé                                                 |

Aucun offset `HYPOTHÈSE` ne doit être utilisé en production sans avertissement dans l'UI (SPEC.md §10.4).

**Outils de mesure** : `tools/skydump.py`, `tools/skydiff.py`, `tools/skycrypto.py`.
**Corpus** : le pack local (702 fichiers), plus 11 captures de la session de jeu du 2026-08-18/19 rangées dans
`~/Games/Cemu/skydumps/`.
**Sauvegarde préalable** (SPEC.md §4.1) : `~/Games/Cemu/skylanders.backup.2026-08-17`, vérifiée par `diff -rq` récursif
et SHA-256 agrégé. Sert de point T0 pour toutes les figurines.

---

## 1. Structure physique — `VÉRIFIÉ`

Dump brut d'un tag Mifare Classic 1K : 1024 octets exactement, 16 secteurs × 4 blocs × 16 octets = 64 blocs.
Bloc `N` → offset `N * 16`. Les 702 fichiers du pack font exactement 1024 octets, zéro exception.

---

## 2. Chiffrement

### 2.1 Constante de dérivation de clé — `VÉRIFIÉ`

```
" Copyright (C) 2010 Activision. All Rights Reserved. "
```

**Espace avant ET espace après. 53 octets ASCII, pas de terminateur nul.**

```
20 43 6F 70 79 72 69 67 68 74 20 28 43 29 20 32 30 31 30 20 41 63 74 69 76 69 73 69 6F 6E 2E
20 41 6C 6C 20 52 69 67 68 74 73 20 52 65 73 65 72 76 65 64 2E 20
```

**Preuve** : 12 variantes plausibles mises en concurrence ; pour chacune, mesure du taux d'octets nuls et de l'entropie
de Shannon sur les seuls blocs porteurs de données. Séparation catégorique : la bonne variante donne 37–94 % de zéros
et 0,6–5,6 bits/octet, les 11 autres < 1,1 % et ≈ 7,7 bits/octet. Sur les **119 fichiers du pack portant ≥ 20 blocs de
données, elle l'emporte 119 fois sur 119**. Confirmée depuis sur les 11 captures de jeu (68–75 % de zéros).

Reproduction : `python3 tools/skydump.py "<un .sky riche en données>"` affiche le classement complet.

### 2.2 Dérivation de clé par bloc — `VÉRIFIÉ`

```
key(N) = MD5( dump[0x00 .. 0x20] || byte(N) || CONSTANTE )
```

Chiffrement **AES-128-ECB**, une clé distincte par bloc. `MessageDigest` (MD5) + `Cipher` (`AES/ECB/NoPadding`) du JDK
suffisent — aucune dépendance externe.

### 2.3 Répartition clair / chiffré — `PROBABLE`

| Zone                  | État                                              |
|-----------------------|---------------------------------------------------|
| Bloc 0                | Clair — UID + données constructeur                |
| Bloc 1                | Clair — identité du jouet                         |
| Blocs où `N % 4 == 3` | Clair — trailers de secteur (clés d'accès Mifare) |
| Tous les autres       | AES-128-ECB, clé distincte par bloc               |

Cohérent avec toutes les mesures, mais non validé par un diff dédié. Repris de SPEC.md §3.2.

---

## 3. Signature « jamais joué » — `VÉRIFIÉ`

**Énoncé** : un bloc de données que le jeu n'a jamais écrit est stocké comme **16 octets nuls bruts, non chiffrés** —
et non comme le chiffré de seize zéros. Une figurine dont **la totalité** des blocs de données est à zéro n'a jamais
été posée sur le portail.

**Preuve — test de bascule (protocole SPEC.md §4.3bis), reproduit sur 7 figurines :**

| Figurine    | Avant        | Après     | Remarque                                                        |
|-------------|--------------|-----------|------------------------------------------------------------------|
| Food Fight  | 0 bloc écrit | 12 blocs  | capture dédiée après ~30 s : `0x06`, `0x24`, `0x25`, `0x29`, `0x2A`, `0x2C`, `0x2D` |
| Snap Shot   | 0 bloc écrit | 12 blocs  | capture dédiée après ~30 s                                       |
| Wildfire    | 0 bloc écrit | 12 blocs  | capture dédiée après ~30 s : `0x06`, `0x24`–`0x26`, `0x28`–`0x2A`, `0x2C`–`0x2E`, `0x30`, `0x31` |
| Enigma      | 0 bloc écrit | 23 blocs  | jouée sans capture dédiée, comparée après coup au T0              |
| Short Cut   | 0 bloc écrit | 23 blocs  | idem                                                              |
| Ka Boom     | 0 bloc écrit | 12 blocs  | idem                                                              |
| Gearshift   | 0 bloc écrit | 12 blocs  | idem                                                              |

Aucune de ces sept figurines n'avait le moindre bloc écrit avant d'être posée sur le portail, et toutes en ont après.
Les quatre dernières n'étaient pas prévues au protocole : jouées au fil des sessions, elles ont été comparées après
coup au T0 de la sauvegarde et valent comme reproductions indépendantes.

Sur les 702 fichiers du pack, **432 ont une zone de données entièrement à zéro** — ce sont les figurines jamais posées
sur le portail. Contrôle complémentaire : les 54 pièges Trap Team vierges lisent tous `(0, 0)` sur les champs
« vilain » et « nombre de captures » (§5.3), sans une seule exception.

**Conséquence pour l'implémentation** : c'est le critère de `toy.first_played_at` (SPEC.md §7.1). Un seul point de
vérité, très supérieur au faisceau XP/or/temps de jeu envisagé en SPEC.md §3.5bis — inutile de croiser plusieurs champs
qui pourraient diverger.

> **Attention** : le critère est « **tous** les blocs de données à zéro ». Ne jamais tester un bloc isolé.

---

## 4. Géométrie de la zone de sauvegarde — `VÉRIFIÉ`

### 4.1 Deux zones miroir

| Zone   | Blocs                              | Premier octet |
|--------|------------------------------------|---------------|
| Zone 0 | `0x08`–`0x22` hors trailers        | `0x080`       |
| Zone 1 | `0x24`–`0x3E` hors trailers        | `0x240`       |

Chaque zone contient **21 blocs de données de 16 octets = 336 octets utiles**, disposés à l'identique.

### 4.2 Espace logique — la clé de lecture du format

**Les trailers de secteur coupent physiquement les champs.** Il faut concaténer les blocs de données en sautant les
blocs où `N % 4 == 3`, puis raisonner dans cet espace logique.

Preuve directe : le surnom `Foo Fighter` (22 octets UTF-16LE) s'écrit à cheval sur les blocs `0x0A` et `0x0C`, en
sautant le trailer `0x0B` — il est contigu en logique, fragmenté en physique.

Correspondance logique → physique :

| Logique       | Zone 0 (bloc / offset) | Zone 1 (bloc / offset) |
|---------------|------------------------|------------------------|
| `+0x00`–`0x0F`| `0x08` / `0x080`       | `0x24` / `0x240`       |
| `+0x10`–`0x1F`| `0x09` / `0x090`       | `0x25` / `0x250`       |
| `+0x20`–`0x2F`| `0x0A` / `0x0A0`       | `0x26` / `0x260`       |
| `+0x30`–`0x3F`| `0x0C` / `0x0C0`       | `0x28` / `0x280`       |
| `+0x40`–`0x4F`| `0x0D` / `0x0D0`       | `0x29` / `0x290`       |
| `+0x50`–`0x5F`| `0x0E` / `0x0E0`       | `0x2A` / `0x2A0`       |

Les deux zones sont séparées de `0x1C0` octets en physique sur leurs premiers blocs.

### 4.3 Compteur de séquence — `VÉRIFIÉ` — logique `+0x09`, `u8`

**C'est le champ qui désigne la zone à lire : celle dont le compteur est le plus élevé.**

Vérifié sur les 7 captures successives de Food Fight, plus Snap Shot et les 3 captures de piège. À chaque sauvegarde le
jeu écrit dans la zone la moins récente et incrémente son compteur de 1, en alternance :

| Capture             | cpt zone 0 | cpt zone 1 | Zone courante | Or lu (vérité terrain) |
|---------------------|-----------:|-----------:|---------------|------------------------|
| C1 (30 s)           | 0          | **1**      | Zone 1        | 0                      |
| C4 chapeau          | **30**     | 29         | Zone 0        | 419                    |
| C4 surnom           | **32**     | 31         | Zone 0        | 419                    |
| C2 niveau 2         | 72         | **73**     | Zone 1        | **1691** ✓             |
| C2 niveau 3         | **80**     | 79         | Zone 0        | **1741** ✓             |
| C3 1 amélioration   | **82**     | 81         | Zone 0        | **1261** ✓             |
| C3 2 améliorations  | 82         | **83**     | Zone 1        | **589** ✓              |

Le compteur prédit correctement la zone porteuse de la bonne valeur d'or **sur 7 captures sur 7**. C'est la preuve
conjointe du compteur et de l'offset de l'or.

> C'est exactement le piège annoncé dans CLAUDE.md (« l'XP ne monte qu'une fois sur deux »). Lire la mauvaise zone
> donne la valeur de la sauvegarde **précédente**, pas une valeur absurde — donc aucune validation de plausibilité ne
> rattrapera l'erreur. Le compteur est le seul garde-fou.

**Le compteur est un `u8` et boucle.** À 255 il repasse à 0 : comparer avec `(a - b) mod 256 < 128` plutôt que `>`.
Non observé en pratique (valeurs vues : 0 à 83) — précaution, pas mesure.

---

## 5. Carte des champs (offsets **logiques** dans la zone courante)

### 5.1 Personnages — Trap Team

Cinq champs sont passés `VÉRIFIÉ` grâce à la seconde session : ils ont été reproduits sur **Wildfire** après l'avoir
été sur **Food Fight**, ce qui satisfait la définition de SPEC.md §4.6 (≥ 2 figurines).

| Logique | Taille | Champ | Niveau | Preuve |
|---------|--------|-------|--------|--------|
| `+0x00` | `u16` LE | **XP cumulé** | `VÉRIFIÉ` | Croît uniquement au combat, **plateau exact** pendant les phases d'achat, sur les deux figurines. Ne se réinitialise pas à la montée de niveau (Food Fight : 1450 au niv. 2 → 2549 au niv. 3) : le champ est donc **cumulatif**, pas « XP dans le niveau courant ». Recoupement inter-figurines : Food Fight lit 2549 en venant d'atteindre le niveau 3, Wildfire lit 3753 en fin de jauge du niveau 3 — l'ordre concorde avec l'état constaté en jeu. `+0x02` est resté à `0x00` sur toutes les mesures. **Plafond** : `Bat Spin test.sky` lit exactement **33 000**, valeur ronde et unique dans le pack, qui correspond au plafond annoncé par SPEC.md §3.5 pour le champ d'XP historique. Ce champ est donc probablement saturé au-delà — cf. §8.7. **Réserve** : aucune valeur d'XP absolue n'a jamais été lue à l'écran, le champ est identifié par son comportement et ce recoupement, pas par un ancrage numérique direct comme l'or. |
| `+0x03` | `u16` LE | **Or** | `VÉRIFIÉ` | **7 valeurs exactes** notées en jeu et retrouvées à l'octet près. Food Fight : 1691, 1741, 1261, 589. Wildfire : **2612, 2142, 1296** — trois sur trois. Inclut quatre deltas d'achat connus (−480, −672, −470, −846). Retrouvé indépendamment dans les deux zones miroir. |
| `+0x05` | `u16` LE | **Temps de jeu (secondes)** | `VÉRIFIÉ` | Croissance monotone sur les deux figurines (Food Fight 7 → 3199 s, Wildfire 8 → 1712 s). Avance même quand la partie ne fait que traverser des menus, jamais en arrière. |
| `+0x09` | `u8` | **Compteur de séquence** | `VÉRIFIÉ` | Cf. §4.3 |
| `+0x0A`–`+0x0F` | 6 o | Condensat / CRC | `HYPOTHÈSE` | Change intégralement à chaque écriture, valeurs pseudo-aléatoires, sans corrélation avec les champs métier. Ne pas tenter de l'interpréter, ne pas le recalculer (lecture seule). |
| `+0x10` | `u8`+ | **Améliorations (bitfield)** | `VÉRIFIÉ` | **Le bit dépend de l'amélioration achetée, pas du nombre d'achats** — c'est ce que la seconde figurine a révélé. Food Fight : `0x00` → `0x04` → `0x0C` → `0x1C` (bits 2, 3, 4). Wildfire : `0x00` → `0x04` → `0x14` (bits 2 **puis 4**, en sautant le 3). Un vrai bitfield, donc, et non un compteur déguisé. Les bits ne s'effacent jamais. Bits 0 et 1 jamais observés à 1 ; `+0x11` est resté à `0x00`, donc largeur ≥ 8 bits, réelle inconnue. |
| `+0x20` | UTF-16LE | **Surnom** | `VÉRIFIÉ` | `Foo Fighter` sur Food Fight, `Will` sur Wildfire, lus exactement, apparus à la capture où ils ont été saisis et à aucune avant. S'étend sur les blocs `0x0A` et `0x0C` (cf. §4.2). |
| `+0x40` | 6 o | **Horodatage de dernière sauvegarde** | `VÉRIFIÉ` | Structure `[minute u8, heure u8, jour u8, mois u8, année u16 LE]`. Concorde à la minute près avec la date de modification du fichier sur l'ensemble des captures des deux sessions. |
| `+0x50` | 6 o | **Horodatage de première écriture** | `VÉRIFIÉ` | Même structure. Figé sur l'instant exact de la première pose : `2026-08-18 23:21` pour Food Fight, `2026-08-19 23:59` pour Wildfire, inchangé sur toutes les captures ultérieures. |
| `+0x62` | `u16` LE | Compteur secondaire, inconnu | `HYPOTHÈSE` | Vaut exactement `0x8001` à la première sauvegarde des **deux** figurines, puis croît de façon monotone : le bit 15 semble être un drapeau et les 15 bits bas un compteur. Ces bits bas suivent le temps de jeu à un rapport remarquablement stable de **~1 unité pour 6,6–6,8 s** (5 mesures sur 2 figurines). Ni l'or, ni l'XP, ni le compteur de séquence ne l'expliquent. Candidats : seconde base de temps, points de héros, compteur d'activité. |

> **`+0x50` est un cadeau pour le modèle de données** : il donne `toy.first_played_at` (SPEC.md §7.1) **avec sa date
> réelle**, pas la date d'ingestion. Une figurine jouée il y a des années sera correctement datée dès le premier scan.
>
> Confirmation indépendante : `Feu/Torch.sky`, jouée avant ce projet, porte `+0x50 = 2026-08-16 11:02`.

### 5.2 Le chapeau **n'est pas stocké sur la figurine** — `VÉRIFIÉ` (résultat négatif)

**Protocole** : trois captures successives de Food Fight ne différant **que** par le chapeau équipé — aucun combat,
aucun achat, aucun ramassage entre elles.

| Capture | Chapeau      | XP   | Or  | Améliorations |
|---------|--------------|------|-----|---------------|
| B1      | *aucun*      | 7139 | 771 | `0x1C`        |
| B2      | Girouette    | 7139 | 771 | `0x1C`        |
| B3      | Melon        | 7139 | 771 | `0x1C`        |

**Résultat** : sur les 1024 octets déchiffrés, seuls 29 octets diffèrent entre ces trois états, et **tous** sont
expliqués par des champs déjà identifiés — temps de jeu `+0x05`, compteur `+0x09`, condensat `+0x0A`–`+0x0F`,
horodatage `+0x40`, compteur secondaire `+0x62` — plus les constantes propres à chaque zone physique (§8.3). **Zéro
octet hors des deux zones miroir n'a bougé** : les blocs `0x02`, `0x04`, `0x05` sont restés vierges et le bloc `0x06`
rigoureusement identique.

**Conclusion** : trois états de chapeau distincts, dont l'absence de chapeau, ne laissent aucune trace. Le chapeau
équipé ne réside pas sur le tag — il vit dans la sauvegarde de la console, hors périmètre (SPEC.md §14).

**Conséquence pour le schéma** : la colonne `hat_id` de `toy_snapshot` (SPEC.md §7.1) restera toujours `NULL` pour
Trap Team. À supprimer, ou à documenter comme définitivement non renseignable pour ce jeu. Ne pas la laisser suggérer
qu'une valeur manque par défaut d'implémentation.

**Champs demandés mais toujours non localisés** : chemin d'amélioration choisi, défis héroïques. Non testés — la
seconde session n'a pas atteint le stade où le jeu propose le choix de branche (cf. §8.4).

### 5.2bis Les statistiques de personnage ne sont pas sur le tag — `VÉRIFIÉ` (résultat négatif)

Vie, vitesse, armure, chance : **aucune n'est écrite sur la figurine**. Ce sont des attributs du *personnage*,
identiques pour toutes ses copies, que le jeu tient dans ses propres données. Le tag ne porte que ce qui est propre à
un exemplaire : sa progression.

C'est cohérent avec la carte des champs — les 336 octets de la zone logique sont occupés par XP, or, temps de jeu,
améliorations, surnom, horodatages et condensat, et rien dans la région explorée ne varie d'un personnage à l'autre
sans varier aussi d'une partie à l'autre.

**Conséquence pour l'UI** : le classement (§10.1) ne trie que sur des colonnes issues du tag. Trier par « vie »
supposerait d'importer une table externe non mesurée — écarté le 2026-08-24, pour ne pas mêler des valeurs `PROBABLE`
venues d'ailleurs à des colonnes `VÉRIFIÉ` mesurées sur les fichiers de l'utilisateur.

### 5.3 Pièges — Trap Team

| Logique | Taille | Champ | Niveau | Preuve |
|---------|--------|-------|--------|--------|
| `+0x01` | `u8` | Nombre de vilains capturés depuis toujours | `PROBABLE` | 0 (vide) → 1 (Slobber Trap) → 2 (Gulper) sur le même piège. Les 5 pièges du pack sont tous à 1. |
| `+0x10` | `u8` | **Vilain actuellement enfermé** | `VÉRIFIÉ` | Cf. tableau ci-dessous. `0x00` = piège vide. |

**Un piège ne porte aucun champ de progression de personnage** — `VÉRIFIÉ`. Mesuré sur deux pièges
indépendants : les deux horodatages `+0x40` et `+0x50` restent à zéro, l'or et le surnom sont vides, et `+0x00` lit un
`256` (`0x0100`) constant. Appliquer la disposition « personnage » à un piège afficherait donc « 256 XP », une valeur
entièrement fabriquée. Le parser refuse de rapporter ces champs pour un piège.

> **Conséquence** : `toy.first_played_at` d'un piège ne peut pas venir du tag. Le serveur retombe sur la date
> d'ingestion — honnête faute de mieux, mais à ne pas confondre avec la date réelle que porte un personnage.

**Validation croisée du champ vilain** — c'est la mesure la plus solide de la session, parce qu'elle confronte mes
captures à des fichiers du pack que je n'ai jamais touchés et dont le nom porte la vérité :

| Vilain        | ID   | Ma capture (Fiole du Déluge) | Fichier indépendant du pack                       |
|---------------|------|------------------------------|---------------------------------------------------|
| Gulper        | `0x09` | ✓                          | `Gulper - Fiole du Déluge Légendaire.sky` ✓        |
| Buzzer Beak   | `0x0C` | —                          | `Buzzer Beak - Avis de Tempête.sky` ✓ (cas SPEC.md §4.4) |
| Sheep Creep   | `0x14` | —                          | `Sheep Creep - Aigle de Chêne.sky` ✓              |
| Tussle Sprout | `0x20` | —                          | `Tussle Sprout - Faucon de Roche.sky` ✓           |
| Slobber Trap  | `0x23` | ✓                          | `Slobber Trap - Bâton Trempé.sky` ✓               |

Deux identifiants supplémentaires ont été relevés sur des pièges remplis pendant la session, **dont le nom du vilain
n'a pas été noté** — ils illustrent exactement le mécanisme d'auto-alimentation de SPEC.md §7.2 :

| ID     | Piège porteur                        | Nom du vilain |
|--------|--------------------------------------|---------------|
| `0x01` | `Pièges/Vie/Arbuste Hurleur.sky`     | **à nommer**  |
| `0x15` | `Pièges/Tech/Fleur d'Usine.sky`      | **à nommer**  |
| `0x1E` | `Pièges/Tech/Ange Automatique.sky`   | **à nommer**  |

**Contrôle négatif renforcé** : `Pièges/Eau/Hache Aquatique.sky` a été posé sur le portail — 42 blocs écrits, donc une
zone de sauvegarde bien initialisée — mais sans jamais servir à capturer. Il lit `+0x10 = 0x00` et `+0x01 = 0`. Le
champ vilain vaut donc `0x00` pour un piège vide **même après écriture**, et pas seulement pour un piège vierge : la
valeur `0` signifie bien « vide », elle n'est pas un artefact de non-initialisation.

Deux vilains lus à la même valeur dans un fichier que j'ai produit et dans un fichier du pack fabriqué ailleurs : le
champ ne peut pas être un artefact. Contrôle négatif : les **54 pièges vierges lisent tous `0x00`**.

Ces 5 lignes amorcent la table `villain` de SPEC.md §7.2, qui se remplira ensuite à la main au fil des parties.

---

## 6. Identité du jouet — `VÉRIFIÉ`

| Offset | Taille | Champ               | Niveau      |
|--------|--------|---------------------|-------------|
| `0x00` | 4      | UID du tag          | `PROBABLE`  |
| `0x10` | 2      | **Toy ID** (u16 LE) | `VÉRIFIÉ`   |
| `0x1C` | 2      | **Variant ID** (u16 LE) | `VÉRIFIÉ` |

### 6.1 Méthode — cohérence interne, sans source externe

Le pack étant complet (SPEC.md §6.6), aucune table communautaire n'est nécessaire : si les offsets sont bons, les
702 fichiers doivent s'organiser sans contradiction. `tools/catalog_bootstrap.py` applique cinq tests. Un offset faux
produirait des valeurs dispersées et ferait exploser les trois premiers.

| # | Test                                                        | Résultat                    |
|---|-------------------------------------------------------------|-----------------------------|
| 1 | Pièges : le toy ID partitionne-t-il par élément ?            | 11 toy ID / 11 éléments, **0 mélange**, 0 doublon |
| 2 | Une identité (toy ID, variant ID) = un seul modèle ?         | 436 identités, **0 conflit de nom**, 0 UID incohérent |
| 3 | Un modèle = un seul toy ID ?                                 | 332 noms, **0 conflit inexpliqué** |
| 4 | Le variant ID code-t-il le type de variante ?                | codes récurrents et stables (§6.3) |
| 5 | Reverse-mapping FR → EN hors personnages                     | **131/131, 100 %**          |

**Aucune divergence.** Le canari de SPEC.md §6.3 — « une divergence massive >10 % signifie que l'offset du toy ID est
faux » — ne chante pas.

Reproduction :

```bash
python3 tools/catalog_bootstrap.py --no-write            # verdict seul
python3 tools/catalog_bootstrap.py --no-write --verbose  # détail de chaque écart
python3 tools/catalog_bootstrap.py --out catalog.json    # + génération du catalogue
```

Sortie 0 si cohérent, 1 sinon. À relancer après toute modification du parsing d'identité.

### 6.2 Ce que les écarts ont appris

Aucun écart n'était du bruit : chacun a livré une règle du format. C'est la raison pour laquelle les tests sont passés
`VÉRIFIÉ` plutôt qu'assouplis jusqu'à ce qu'ils passent.

**Les pièges n'ont pas de toy ID individuel.** Le toy ID d'un piège encode son **élément**, et le variant ID sa
**forme**. Les six pièges Magie partagent le toy ID 210 et se distinguent par leur variant ID.

| Toy ID | Élément | | Toy ID | Élément | | Toy ID | Élément |
|--------|---------|-|--------|---------|-|--------|---------|
| 210    | Magie   | | 214    | Tech    | | 218    | Ténèbres |
| 211    | Eau     | | 215    | Feu     | | 219    | Lumière |
| 212    | Air     | | 216    | Terre   | | 220    | Kaos    |
| 213    | Mort-Vivant | | 217 | Vie     | |        |         |

> **Conséquence pour `catalog_toy`** : la clé (toy ID, variant ID) reste valide pour un piège, mais `toy_id` seul n'y
> désigne pas un modèle — il désigne un élément. Ne jamais afficher « toy ID 210 » comme identifiant de piège.

**Les codes de variante sont stables.** Le variant ID n'est pas arbitraire, il encode le type de variante :

| Variante        | Code(s) observé(s)       | Fichiers |
|-----------------|--------------------------|----------|
| Série 2         | `6145` — **24 sur 24**   | 24       |
| Élite d'Eon     | `14352`, `18448`         | 14       |
| LightCore       | `4614`, `8710`           | 16       |
| Légendaire      | `13315`, `7171`, `0`     | 30       |
| Sombre / Nitro  | `17410`, `17666`, `13314`| 22       |
| Aucune          | `0` majoritaire          | 538      |

**Les moitiés Swap Force sont deux tags distincts.** Le haut porte `2000 + n`, le bas `1000 + n`, sur les 25 paires
sans exception. Ce ne sont pas des variantes l'une de l'autre mais deux objets à part entière.

**Une variante à variant ID nul reçoit son propre toy ID.** Sur Spyro's Adventure, quatre variantes ont un variant ID
de `0` — ce champ ne peut donc pas les distinguer, et le jeu leur alloue un toy ID dédié : `Bash Légendaire` 404 pour
un Bash à 4, `Spyro Légendaire` 416, `Trigger Happy Légendaire` 419, `Chop Chop Légendaire` 430 (convention
« base + 400 »), et `Spyro Sombre` 28. Les jeux suivants encodent la variante dans le variant ID et abandonnent cette
échappatoire.

**Neuf identités sont portées par plusieurs fichiers**, toutes avec le même UID : ce sont des copies du même jouet
rangées à plusieurs endroits (les huit Acolytes présents à la fois dans `Giants/Acolytes`, `Trap Team/Acolytes` et
`Trap Team/Minis`, plus `VVind-Up.sky` doublé par `vvindup_1.sky`, resté non renommé). Doublons de fichiers, pas
collisions d'identité.

**Coquilles du pack absorbées** (SPEC.md §6.4) : le matching tolérant du bootstrap — accents ignorés, ponctuation
retirée, distance de Levenshtein ≤ 2, et `vv` lu comme `w` — réconcilie `Cobra Candabra`/`Cobra Cadabra`,
`Tri-Tip`/`Tritip`, `Flashwing`/`Flash Wing`, `Bad_Juju`/`Badjuju`, `Lob Star`/`Lob-Star`, `VVind-Up`/`Wind Up`.
**Bootstrap uniquement — jamais en runtime**, conformément à SPEC.md §6.4.

### 6.3 Livrable

`catalog.json` à la racine : **524 identités**, dont **498 `VALIDATED` (95,0 %)** et 26 `REVIEW`, zéro `NEW`. Le seuil
de sortie de la phase 1 (SPEC.md §12, « >95 % de noms validés ») est franchi dès le bootstrap. Les 26 `REVIEW` sont les
identités portant plusieurs noms — à trancher à la main, comme le prévoit SPEC.md §6.5.

## 7. Décisions actées

### 7.1 Clé d'identité de la table `toy` = chemin relatif — acté le 2026-08-18

Le risque « collisions d'UID » de SPEC.md §7.3 est **confirmé et clos**.

| Indicateur                             | Valeur        |
|----------------------------------------|---------------|
| UID distincts                          | **609 / 702** |
| UID partagés par ≥ 2 fichiers          | 51            |
| Fichiers impliqués dans une collision  | **144**       |
| Fichiers pour le pire UID (`D8300000`) | **10**        |

Distribution : 31 UID à 2 fichiers, 13 à 3, 2 à 4, 2 à 5, 1 à 6, 1 à 9, 1 à 10.

**Décision** : `toy.uid` **ne porte aucune contrainte d'unicité**. La clé d'identité fonctionnelle de `toy` est le
**chemin relatif** reçu de l'agent. L'UID reste stocké et affiché à titre informatif, jamais comme identifiant.

**Le couple (toy ID, variant ID) ne peut pas non plus servir de clé** : 524 couples distincts pour 702 fichiers,
jusqu'à 55 fichiers pour un même couple — normal, les consommables Imaginators sont plusieurs exemplaires du même
modèle. Cela confirme le chemin relatif comme seule clé viable.

> Ne remet pas en cause l'invariant 3 de CLAUDE.md : le *chemin* sert de clé de ligne en base, pas de source d'identité
> métier. L'identité métier reste (toy ID, variant ID), résolue via `catalog_toy`.

### 7.2 Lecture de la sauvegarde : toujours passer par le compteur

Toute lecture d'un champ de progression **doit** commencer par comparer les compteurs `+0x09` des deux zones et
sélectionner la plus élevée. Un accès direct à un offset physique sans cette étape est un bug (cf. §4.3).

---

### 7.3 Décisions de stack — actées le 2026-08-22

Prises en concertation, en complément de ce que SPEC.md §11.2 laissait ouvert.

| Sujet | Décision | Raison |
|---|---|---|
| **Agent laptop** | **Python** (SPEC.md §11.2 laissait le choix) | L'agent ne fait que scanner, hasher et POSTer. Pas de JVM à installer, lancement immédiat, et Python 3.12 est déjà présent avec `tools/`. |
| **Frontend** | **Buildé dans le JAR Spring Boot** | Un seul conteneur, un seul port, pas de CORS ni de reverse-proxy. Adapté à un homelab mono-utilisateur. |
| **`hat_id`** | **Supprimé** de `toy_snapshot` | Prouvé non stocké sur le tag (§5.2). |
| **`toy.uid`** | **Sans contrainte d'unicité** | 609 UID pour 702 fichiers (§7.1). |
| **`first_played_at`** | Alimenté par l'horodatage `+0x50` du tag | Date historique réelle, pas la date d'ingestion (§5.1). |
| **`last_saved_at`** | **Ajouté**, alimenté par `+0x40` | Permet « dernière partie le… » sans dépendre de l'ingestion. |

Ces écarts au schéma de SPEC.md §7.1 sont **volontaires et motivés par les mesures**. Ils sont à reporter dans la
spec lors de sa prochaine révision.

---

## 8. Questions ouvertes

### 8.1 Bloc `0x06` — blob immuable écrit à la première pose

Le bloc `0x06` est le **seul bloc de données hors des deux zones miroir** (les blocs `0x02`, `0x04` et `0x05` restent
vierges sur tous les fichiers observés). Il est écrit lors de la toute première pose sur le portail, en même temps que
la première zone de sauvegarde, puis **ne change plus jamais**.

| Figurine   | Contenu déchiffré du bloc `0x06`   | Stable sur                     |
|------------|------------------------------------|--------------------------------|
| Food Fight | `DD8F7F28756B28E077D49148AB7BDE8F` | 10 captures, 4 jours de jeu    |
| Wildfire   | `714DA54C0776FA61296037CF351AE472` | 4 captures                     |

Contenu de haute entropie, distinct d'une figurine à l'autre. Probablement une empreinte ou un jeton d'identité écrit
à l'initialisation. Sans usage connu pour le projet.

> **Correction** : une version antérieure de ce document affirmait que ce bloc « change intégralement à chaque
> sauvegarde ». C'était faux — l'inférence venait de sa haute entropie, pas d'une mesure. Les captures B1/B2/B3 le
> montrent rigoureusement identique. Le champ qui change à chaque écriture est le condensat `+0x0A`–`+0x0F`, à
> l'intérieur des zones.

### 8.2 Objets Imaginators à empreinte de 4 blocs — schéma différent, gelé

Caractérisation resserrée le 2026-08-23, en branchant le test de non-régression sur tout le pack.

**Ce qui est concerné** : les fichiers écrivant **exactement 4 blocs** — `0x02`, `0x04`, `0x22`, `0x3E`, soit 64 octets.
Ils sont 141 : 110 `Coffres/`, 29 `Cristaux de Création/` et **2 `Packs Aventure/`**. Aucun ne se déchiffre de façon
plausible, avec aucune des 12 variantes de constante.

**Ce qui ne l'est pas, contrairement à ce que je supposais** :

- ~~la catégorie~~ — 40 `Cristaux de Création` et 2 `Packs Aventure` se déchiffrent parfaitement. Ce n'est donc pas
  « les consommables » ;
- ~~l'écriture hors des deux zones~~ — les blocs `0x02` et `0x04` sont écrits par **tous** les jouets Imaginators, y
  compris les 40 personnages qui se déchiffrent sans problème ;
- ~~le volume insuffisant~~ — écarté : `Cristaux de Création/Lanterne Air 1.sky` porte 5 blocs (80 octets), assez pour
  mesurer, et reste à 2,5 % de zéros pour 6,0 bits/octet.

**Le discriminant est le nombre de blocs écrits, et la séparation est sans recouvrement** :

| Blocs écrits | Fichiers | Déchiffrement |
|--------------|----------|---------------|
| 4 ou 5       | **142**  | bruit, sans exception |
| 6 à 10       | **0**    | — l'intervalle est vide |
| 11 et plus   | **140**  | propre, sans exception |

Aucun fichier ne tombe entre les deux populations. Ces petits objets utilisent donc une autre dérivation de clé, ou ne
sont pas chiffrés du tout. L'intervalle vide rend le seuil du test de non-régression insensible à son réglage exact.

**Statut : ouvert, gelé.** À reprendre en phase 7+ (Imaginators). Sans impact sur la v1, dont le périmètre de parsing
est Trap Team. Le test de non-régression les exclut explicitement par leur nombre de blocs, pas en abaissant son seuil.

### 8.3 Zone logique au-delà de `+0x60`

Les octets `+0x70`–`+0x72` et `+0x7C` prennent des valeurs différentes selon la zone physique et **ne varient pas dans
le temps** — ils diffèrent entre zone 0 et zone 1 sans rapport avec la progression. Le modèle « deux zones strictement
identiques » ne tient donc peut-être pas au-delà de `+0x60`. À creuser avant d'exploiter un champ de cette région.

---

### 8.4 Chemin d'amélioration : le choix de branche n'a pas encore été atteint

SPEC.md §3.5 attend un champ « chemin d'amélioration choisi ». Aucune des deux figurines testées n'a atteint le stade
où le jeu propose ce choix : le bitfield `+0x10` plafonne à 3 bits sur Food Fight (`0x1C`) et 2 sur Wildfire (`0x14`).
Dans la structure d'amélioration habituelle de la série, le choix entre deux branches n'apparaît qu'**après l'achat des
améliorations de base**, ce qui explique qu'une seule liste soit visible en jeu pour l'instant. Cohérent avec les
mesures, mais non vérifié — à reprendre quand une figurine aura acheté assez d'améliorations.

### 8.5 Objets équipables donnés aux Skylanders

Question soulevée après la seconde session, non testée. Le résultat négatif du chapeau (§5.2) constitue un a priori
fort : si l'équipement le plus visible d'une figurine ne laisse aucune trace sur le tag, un objet équipé n'en laisse
probablement aucune non plus. Mais ce n'est pas une preuve — c'est une extrapolation, et elle est explicitement
marquée comme telle.

**Test qui trancherait**, identique en forme à celui du chapeau : trois captures d'une même figurine, ne différant que
par l'objet équipé (aucun / objet A / objet B), sans combat ni achat entre elles.

---

### 8.7 Le champ d'XP sature à 33 000 — le champ supplémentaire de Trap Team n'est pas localisé

SPEC.md §3.5 prévenait : « Trap Team utilise des champs XP supplémentaires : le champ d'origine plafonnait vers
33 000, insuffisant pour le niveau max de TT. » La mesure le confirme : sur les 35 figurines Trap Team jouées, une
seule dépasse 7 139 et elle lit **exactement 33 000** — valeur ronde, unique, et conforme au plafond annoncé.

**Ce que ça implique** : `+0x00` est le champ historique. Pour une figurine de haut niveau, la valeur lue est
tronquée, et l'entrée §5.1 reste `VÉRIFIÉ` pour *ce champ-là*, pas pour « l'XP réelle du personnage ».

**Non localisé** : le second champ. `+0x02` est resté nul partout, donc ce n'est pas une simple extension en `u24`.
Le candidat `+0x62` est écarté : la figurine à 33 000 y lit `0x8001`, sa valeur initiale.

**Test qui trancherait** : monter une figurine au-delà du niveau correspondant à 33 000 XP et comparer deux captures
successives. En attendant, l'API et l'UI signalent explicitement toute valeur d'XP ≥ 33 000 comme possiblement saturée.

---

### 8.6 Une identité peut appartenir à plusieurs jeux — limite de `catalog_toy`

Révélé en branchant l'API sur les données réelles. Huit Acolytes (`Barkley`, `Gill Runt`, `Terrabite`…) sont livrés à
la fois sous `Giants/Acolytes` et sous `Trap Team/Acolytes` et `Trap Team/Minis`, avec **la même identité
(toy ID, variant ID) et le même UID** — c'est bien le même jouet physique, utilisable dans les deux jeux.

Or `catalog_toy` a pour clé primaire `(toy_id, variant_id)` et **une seule** colonne `game` (SPEC.md §7.1). Le
bootstrap y met donc le premier jeu rencontré, et `Barkley` s'affiche sous Giants alors qu'il a été joué dans
Trap Team. Ce n'est pas faux, c'est incomplet.

Trois issues possibles, **non tranchées** :

1. accepter la limite et documenter `game` comme « premier jeu où ce jouet apparaît » ;
2. ajouter `game` à la clé primaire — le roster gagne des doublons volontaires, un par jeu ;
3. sortir la relation dans une table `catalog_toy_game` (n–n).

Sans impact sur le déblocage, qui passe par `toy.first_played_at` et ignore `catalog_toy`. À trancher avant l'écran
Collection, puisque c'est lui qui filtre par jeu.

---

## 9. Phase 0 — **close**

SPEC.md §12 exige des offsets `VÉRIFIÉ` pour **toy ID, variant ID et XP**.

| Champ      | État      | Preuve |
|------------|-----------|--------|
| Toy ID     | `VÉRIFIÉ` | §6.1 — 5 tests de cohérence interne sur 702 fichiers, 0 conflit |
| Variant ID | `VÉRIFIÉ` | §6.1, §6.2 — codes de variante stables, partition des pièges exacte |
| XP         | `VÉRIFIÉ` | §5.1 — reproduit sur Food Fight et Wildfire |

**Les trois critères sont atteints. La phase 0 est terminée** et le développement applicatif peut commencer
(CLAUDE.md, « Ordre de travail »).

### Bilan

| Établi `VÉRIFIÉ`                                              | Reste ouvert                                  |
|----------------------------------------------------------------|-----------------------------------------------|
| Constante et dérivation de clé (§2.1, §2.2)                    | Répartition clair/chiffré, `PROBABLE` (§2.3)  |
| Signature « jamais joué », 7 figurines (§3)                    | Champ `+0x62` (§5.1)                          |
| Géométrie des deux zones + espace logique (§4.1, §4.2)         | Chemin d'amélioration, défis héroïques (§8.4) |
| Compteur de séquence (§4.3)                                    | Objets équipables (§8.5)                      |
| XP, or, temps de jeu, améliorations, surnom (§5.1)             | Consommables Imaginators, gelé (§8.2)         |
| Les deux horodatages (§5.1)                                    | Structure au-delà de `+0x60` (§8.3)           |
| Vilain enfermé dans un piège (§5.3)                            | 26 entrées `REVIEW` de `catalog.json` (§6.3)  |
| Toy ID et variant ID (§6)                                      |                                               |
| Chapeau **non stocké sur le tag** — résultat négatif (§5.2)    |                                               |

Aucun point ouvert ne bloque la v1. Tous les champs décrits en SPEC.md §1 sont couverts, à l'exception du chapeau,
dont on sait désormais qu'il est hors d'atteinte par construction.
