# Skylanders Collection Dashboard — Spécification

> **Statut** : v0.1 — spec de démarrage
> **Contrainte absolue** : l'application est **strictement en lecture seule** sur les fichiers `.sky`.

---

## 1. Objectif

Application web auto-hébergée qui lit les dumps de figurines Skylanders (`.sky`) utilisés
par le portail émulé de Cemu, et expose :

- un **inventaire complet** de la collection (702 fichiers, 6 jeux) ;
- le **détail de progression** des figurines (niveau, XP, or, chapeau, améliorations,
  surnom, temps de jeu) — Trap Team en priorité ;
- le **contenu actuel des pièges** (quel vilain est enfermé dans quel piège) ;
- un **historique** de progression dans le temps (SGBD).

L'interface est en **français** : les noms de fichiers sont déjà francisés.

---

## 2. Contexte d'exécution

| Élément | Emplacement |
|---|---|
| Cemu + fichiers `.sky` sources | Laptop `pop-os` — `~/Games/Cemu/skylanders` |
| Application + base de données | Serveur homelab (Docker) |
| Accès utilisateur | Tailscale |

### 2.1 Transport des fichiers (décision d'architecture)

Les `.sky` vivent sur le laptop, l'app (API + base + UI) tourne sur le serveur.
**Solution retenue : agent local à lancement manuel, qui pousse les deltas vers le serveur.**

```
Laptop                                    Serveur (homelab)
┌──────────────────────┐                 ┌──────────────────────┐
│ Agent local           │                 │  API ingestion        │
│ - config: chemin en   │  HTTP/Tailscale │  → parsing            │
│   dur dans un fichier │ ──────────────► │  → PostgreSQL         │
│ - scan + hash         │  (fichiers      │                       │
│ - watch tant qu'ouvert│   changés       │  Web UI (Vue)         │
│ - envoie le delta     │   seulement)    │  ← consultable         │
│   seulement           │                 │    à tout moment       │
└──────────────────────┘                 │    (y compris          │
                                          │    téléphone)          │
                                          └──────────────────────┘
```

**Répartition du travail (décision clé) : l'agent ne parse rien.**
Il lit les octets bruts, les hash, détecte ce qui a changé, et pousse le contenu brut.
Tout le déchiffrement, la classification et le parsing vivent **uniquement côté serveur**.

Raison : pendant la phase 0 et tant que les offsets ne sont pas stabilisés (`FORMAT.md`),
la logique de parsing va être corrigée fréquemment. Avec un agent « bête », un correctif
ne redéploie que le serveur. Si l'agent parsait aussi, chaque ajustement d'offset
demanderait de redéployer le laptop — de la friction au moment où l'itération est la plus
rapide. Le fait que les octets bruts transitent sur le réseau n'est pas un problème :
c'est le propre Tailscale de l'utilisateur, entre son propre laptop et son propre serveur.

**Cycle de vie de l'agent :**

- Lancement **manuel**, quand l'utilisateur veut jouer. Pas de service auto-démarré au
  boot — l'usage n'est pas quotidien.
- Au lancement : scan complet du dossier configuré, envoi de tout fichier dont le hash
  diffère du dernier envoi connu (cache local, cf. §2.2).
- Pendant que l'agent tourne : `WatchService` local sur le dossier, envoi des deltas au
  fil de l'eau (avec debounce, cf. §8.2 — Cemu écrit pendant que tu joues).
- À la fermeture de l'agent : rien de spécial, l'API reste consultable avec les dernières
  données reçues.

**Configuration de l'agent :** un fichier local, chemin en dur, pas de sélecteur de
dossier graphique en v1 :

```yaml
# agent.yaml
skylandersRoot: /home/willem/Games/Cemu/skylanders
serverUrl: https://<hote-tailscale>:8443
```

Modifiable à la main si le dossier change. Un vrai file picker pourra être ajouté plus
tard si le besoin se fait sentir — inutile pour un utilisateur unique qui connaît son
propre chemin.

**Ce que le serveur ne fait plus :** il ne monte, ne lit, ni ne surveille aucun dossier
du laptop. Il n'existe aucun accès filesystem entre le serveur et les fichiers sources —
seul le contenu explicitement poussé par l'agent transite. C'est une garantie de lecture
seule **plus forte** qu'un montage réseau : le serveur n'a physiquement aucun moyen
d'écrire quoi que ce soit sur le laptop, puisqu'il n'y a pas de canal serveur→laptop du tout.

### 2.2 Volumétrie

702 fichiers × 1024 octets ≈ **702 Ko**. Un scan complet côté agent (lecture + hash)
comme un parsing complet côté serveur (déchiffrement + classification) coûtent chacun
quelques centaines de millisecondes.

**Conséquence de conception :** aucun cache côté serveur, aucune optimisation de scan.
La base de données existe **uniquement pour l'historique**, jamais pour la performance.

**Cache côté agent (le seul cache du système) :** une table locale légère
(`chemin relatif → SHA-256 → dernier envoi réussi`), typiquement un fichier JSON ou
SQLite embarqué. Son unique rôle est d'éviter de renvoyer 702 fichiers à chaque
lancement de l'agent. Au premier lancement (ou cache absent/corrompu), l'agent envoie
tout — 702 Ko, quelques secondes sur Tailscale. Aux lancements suivants, seuls les
fichiers modifiés depuis la dernière session de jeu sont envoyés, typiquement 1 à 5
fichiers.

| Jeu | Fichiers |
|---|---|
| Spyro's Adventure | 50 |
| Giants | 74 |
| Swap Force | 111 |
| Trap Team | 156 |
| SuperChargers | 75 |
| Imaginators | 236 |
| **Total** | **702** |

---

## 3. Format `.sky` — état des connaissances

> ⚠️ **Les offsets ci-dessous sont des HYPOTHÈSES.** Ils doivent être validés
> empiriquement (§4) avant toute implémentation. Ne jamais considérer un offset comme
> acquis sans test sur un fichier réel.

### 3.1 Structure physique

Dump brut d'un tag **Mifare Classic 1K** :

- 1024 octets exactement
- 16 secteurs × 4 blocs × 16 octets = 64 blocs
- Bloc `N` → offset `N * 16`

### 3.2 Chiffrement

| Zone | État |
|---|---|
| Bloc 0 | **Clair** — UID + données constructeur |
| Bloc 1 | **Clair** — identité du jouet |
| Blocs où `N % 4 == 3` | **Clair** — trailers de secteur (clés d'accès Mifare) |
| Tous les autres | **AES-128-ECB**, clé distincte par bloc |

**Dérivation de clé** (par bloc `N`) :

```
key(N) = MD5( dump[0x00 .. 0x20]  ||  byte(N)  ||  CONSTANTE )
```

- `dump[0x00..0x20]` = les 32 premiers octets du dump (blocs 0 et 1, en clair)
- `CONSTANTE` = chaîne ASCII de copyright Activision 2010
  → **la forme exacte (espace final ? octet nul ?) est à déterminer par essai** :
  tester les variantes jusqu'à ce que le déchiffrement produise des valeurs plausibles.

Java standard suffit — aucune dépendance :
`MessageDigest.getInstance("MD5")` + `Cipher.getInstance("AES/ECB/NoPadding")`.

### 3.3 Bloc 0 — Identité physique

| Offset | Taille | Champ | Confiance |
|---|---|---|---|
| `0x00` | 4 | **UID du tag** | Élevée |
| `0x04`–`0x0F` | 12 | Données constructeur | — |

L'UID est la **clé d'identité canonique** d'une figurine.

### 3.4 Bloc 1 — Identité du jouet

| Offset | Taille | Champ | Confiance |
|---|---|---|---|
| `0x10` | 2 | **Toy ID** (uint16 LE) | Moyenne |
| `0x1C` | 2 | **Variant ID** (uint16 LE) | **Faible — à valider** |

Le variant ID distingue Series 1 / Series 2 / LightCore / Legendary / Eon's Elite /
Golden / Dark / etc.

**Ces deux champs sont identiques sur les 6 jeux.** C'est ce qui permet de lister la
collection entière dès la v1 sans parser aucune sauvegarde.

### 3.5 Zone de sauvegarde (chiffrée)

Le jouet possède **deux zones de sauvegarde miroir** avec un compteur de séquence.
C'est le mécanisme d'écriture atomique du tag.

> **Règle impérative** : lire les deux zones, comparer les compteurs, retenir **celle
> dont le compteur est le plus élevé**. Ignorer cette règle produit des données périmées
> de façon intermittente et très difficile à diagnostiquer.

Champs attendus (offsets **tous à déterminer**) :

- XP (Trap Team utilise des champs XP supplémentaires : le champ d'origine plafonnait
  vers 33 000, insuffisant pour le niveau max de TT)
- Or
- Chapeau équipé (id)
- Améliorations achetées (bitfield)
- Chemin d'amélioration choisi
- Surnom (UTF-16LE)
- Temps de jeu
- Défis héroïques

**Structure spécifique au jeu.** Un `.sky` de Trap Team et un de SuperChargers n'ont pas
la même disposition de sauvegarde.

### 3.6 Pièges (Trap Team)

Un piège est un tag distinct qui contient :

- un **type de piège** (forme + élément)
- un champ identifiant **le vilain actuellement enfermé** (offset à déterminer)

**Limite fonctionnelle importante** : un piège ne contient **qu'un seul vilain à la fois**.
La *checklist* de tous les vilains capturés dans la partie n'est **pas** dans les `.sky` —
elle réside dans la sauvegarde Wii U du jeu.

→ Périmètre v1 : « quel piège contient quoi **en ce moment** ».
→ Hors périmètre : « j'ai capturé 43/46 vilains ».

---

## 4. Phase 0 — Rétro-ingénierie guidée (à faire AVANT le développement)

Cette phase est **obligatoire** et précède tout code applicatif.

### 4.1 Sauvegarde préalable

```bash
cp -r ~/Games/Cemu/skylanders ~/Games/Cemu/skylanders.backup.$(date +%F)
```

À faire **avant toute chose**. Non négociable.

### 4.2 Outil de diff

Écrire un script (Python recommandé pour l'itération rapide : `hashlib` +
`pycryptodome`) qui :

1. déchiffre deux dumps du même personnage ;
2. affiche les octets ayant changé, avec leur offset, en clair et en chiffré ;
3. interprète les candidats en uint16/uint32 LE.

### 4.3 Protocole expérimental

Pour chaque champ à localiser :

1. dumper le fichier (copie) ;
2. réaliser **une seule action ciblée** en jeu (gagner un niveau, acheter une
   amélioration précise, équiper un chapeau, ramasser une somme d'or connue) ;
3. re-dumper ;
4. differ.

Les octets qui bougent donnent la carte des offsets.

### 4.4 Cas de test déjà disponibles

| Fichier | Vérité attendue |
|---|---|
| `Pièges/Air/Buzzer Beak - Avis de Tempête.sky` | Le champ vilain doit valoir **Buzzer Beak** |

> **Ne pas renommer ce fichier** tant que l'offset du vilain n'est pas trouvé et validé.

### 4.5 Sources de référence

Le code de **Dolphin** (éditeur Skylanders intégré) et de **Cemu** sont la meilleure
documentation vivante du format. À croiser systématiquement avec les mesures empiriques.

### 4.6 Livrable de la phase 0

Un document `FORMAT.md` listant chaque offset **avec son niveau de preuve** :

- `VÉRIFIÉ` — validé par diff reproductible sur ≥2 figurines
- `PROBABLE` — cohérent avec une source externe, non testé
- `HYPOTHÈSE` — supposé, non validé

Aucun offset `HYPOTHÈSE` ne doit être utilisé en production sans avertissement dans l'UI.

---

## 5. Classification par arborescence

L'arborescence porte des métadonnées **absentes des fichiers** : jeu, élément, catégorie.

### 5.1 Structure observée

```
<Jeu>/<Élément>/*.sky
<Jeu>/<Élément>/Variantes/*.sky
<Jeu>/<Élément>/Véhicules/*.sky          (SuperChargers)
<Jeu>/Pièges/<Élément>/*.sky             (Trap Team)
<Jeu>/Pièges/<Élément>/Variantes/*.sky
<Jeu>/<Catégorie>/*.sky                  (Minis, Acolytes, Objets, Packs Aventure…)
```

### 5.2 Règles de classification

Appliquer dans cet ordre, première règle gagnante :

| # | Motif de chemin | Jeu | Catégorie | Élément |
|---|---|---|---|---|
| 1 | `<J>/Pièges/<E>/Variantes/f.sky` | `<J>` | `TRAP` | `<E>` |
| 2 | `<J>/Pièges/<E>/f.sky` | `<J>` | `TRAP` | `<E>` |
| 3 | `<J>/<E>/Véhicules/f.sky` | `<J>` | `VEHICLE` | `<E>` |
| 4 | `<J>/<E>/Variantes/f.sky` | `<J>` | `CHARACTER` | `<E>` |
| 5 | `<J>/<Cat>/f.sky` où `<Cat>` ∈ catégories connues | `<J>` | `<Cat>` | issu du fichier / `UNKNOWN` |
| 6 | `<J>/<E>/f.sky` où `<E>` ∈ éléments connus | `<J>` | `CHARACTER` | `<E>` |
| 7 | *défaut* | `<J>` | `UNKNOWN` | `UNKNOWN` |

**Éléments connus** : Air, Eau, Feu, Lumière, Magie, Mort-Vivant, Ténèbres, Terre, Tech, Vie

**Catégories connues** (niveau 2) : Acolytes, Minis, Objets, Packs Aventure, Pièges,
Coffres, Cristaux de Création, Géants

### 5.3 Anomalie à absorber telle quelle

Les variantes de **personnages** sont dans un sous-dossier `Variantes/`, mais les
variantes de **véhicules** sont **à plat** dans `Véhicules/` :

```
Véhicules/Bolide Ardent.sky
Véhicules/Bolide Ardent Doré.sky      ← variante, pas de sous-dossier
Véhicules/Bolide Ardent E3.sky        ← variante
Véhicules/Bolide Ardent Sombre.sky    ← variante
```

→ Le statut « variante » se déduit **du nom** (suffixe reconnu), pas seulement du dossier.
→ **Ne pas réorganiser les fichiers sources.** L'app s'adapte, jamais l'inverse.

### 5.4 Filtrage

- Ne scanner que `*.sky`
- **Rejeter** tout fichier dont la taille ≠ 1024 octets (journaliser en `WARN`)
- Ignorer : `desktop.ini`, `*.txt`, `*.md`, `*.png`, `*.jpg`

### 5.5 Bonus gratuit

Chaque dossier d'élément contient déjà son icône : `<Élément>SymbolSkylanders.png`
(ex. `MagicSymbolSkylanders.png`). L'UI dispose de ses symboles d'éléments sans effort.

---

## 6. Résolution des noms : FR ↔ EN ↔ Toy ID

Le pack a été **renommé automatiquement** de l'anglais vers le français
(cf. `Correspondances FR.md`). Le renommage est donc **réversible**, ce qui permet un
bootstrap validé du catalogue.

### 6.1 Règle fondamentale

> **Le nom de fichier n'est JAMAIS un identifiant.**
> Il est renommable, annotable, et ne distingue pas toujours les variantes.
> L'identité canonique est **UID** (physique) et **toy ID + variant ID** (modèle).

Le nom de fichier sert de **source d'amorçage** et de **vérification croisée**, rien de plus.

### 6.2 Pipeline de reverse-mapping (FR → EN)

Pour chaque fichier :

1. **Normaliser Unicode** (NFC) et uniformiser les apostrophes
   → `'` (U+2019) et `'` (U+0027) doivent être traités comme équivalents.
   *Le fichier de correspondances contient lui-même les deux formes
   (`Dragon's Peak` / `Dragon's Peak`).*
2. **Retirer l'annotation utilisateur** : tout ce qui suit ` - `
   → `Buzzer Beak - Avis de Tempête` ⇒ `Buzzer Beak` + annotation `Avis de Tempête`
   ⚠️ Attention : la partie **avant** le tiret peut être le vilain et la partie
   **après** le piège. Traiter les deux segments comme candidats.
3. **Détecter et détacher un suffixe de variante** (FR) :
   `Élite d'Eon`, `Légendaire`, `Série 2`, `LightCore`, `Power Blue`, `Doré`,
   `Platine`, `Sombre`, `Nitro`, `(Haut)`, `(Bas)`
4. **Traduire le nom de base** via les tables inversées de `Correspondances FR.md`
   (objets, pièges, véhicules, packs aventure, cristaux, coffres).
   Les **noms de personnages ne sont pas traduits** — ils passent tels quels.
5. **Réappliquer la variante en préfixe** (anglais) :
   `Spyro` + `Élite d'Eon` ⇒ `Eon's Elite Spyro`
   `Bolide Ardent` + `Doré` ⇒ `Golden Hot Streak`

### 6.3 Validation croisée

```
nom fichier ──(reverse-mapping)──► nom EN candidat ──┐
                                                     ├──► CONCORDANCE ?
toy ID (bloc 1) ──(table communauté)──► nom EN ──────┘
```

| Résultat | Action |
|---|---|
| Concordance | Entrée `VALIDÉE` — les deux chaînes se confirment |
| Divergence | Entrée `À REVOIR` — signale soit un offset faux, soit un cas tordu |
| Toy ID inconnu | Entrée `NOUVELLE` — pré-remplie avec le nom déduit, à confirmer |

**Une divergence massive (>10 %) signifie que l'offset du toy ID est faux.**
C'est le canari de la phase 0.

### 6.4 Pièges connus dans les noms

Le renommage automatique a introduit des défauts :

| Observé | Attendu | Nature |
|---|---|---|
| `Cobra Candabra` | `Cobra Cadabra` | Coquille dans le pack |
| `Pyrmid` | `Pyramid` | Coquille — **déjà documentée** dans le fichier de correspondances |
| `Deja Vu` | `Déjà Vu` | Accents absents |

→ Prévoir un **matching tolérant** (distance de Levenshtein ≤ 2, accents ignorés)
   pour l'étape de bootstrap **uniquement**, avec revue humaine obligatoire.
→ Ne **jamais** utiliser le matching flou en runtime.

### 6.5 Livrable

Un fichier `catalog.json` versionné :

```json
{
  "toys": [
    {
      "toyId": 100,
      "variantId": 0,
      "nameEn": "Spyro",
      "nameFr": "Spyro",
      "game": "SPYROS_ADVENTURE",
      "element": "MAGIE",
      "category": "CHARACTER",
      "confidence": "VALIDATED"
    }
  ]
}
```

Généré par un **script de bootstrap**, puis **corrigé et versionné à la main**.
Il devient ensuite la source de vérité, indépendante des noms de fichiers.

---

## 7. Modèle de données (PostgreSQL)

### 7.1 Tables

**`catalog_toy`** — référentiel des modèles (alimenté par `catalog.json`)

| Colonne | Type | Note |
|---|---|---|
| `toy_id` | `int` | PK composite |
| `variant_id` | `int` | PK composite |
| `name_en` | `text` | |
| `name_fr` | `text` | |
| `game` | `text` | enum |
| `element` | `text` | enum |
| `category` | `text` | enum |
| `confidence` | `text` | `VALIDATED` / `REVIEW` / `NEW` |

**`toy`** — figurines physiques détectées

| Colonne | Type | Note |
|---|---|---|
| `id` | `bigserial` | PK |
| `uid` | `bytea` | UID du tag, **unique si possible** (cf. §7.3) |
| `file_path` | `text` | chemin relatif à `SKYLANDERS_ROOT` |
| `toy_id` | `int` | → `catalog_toy` |
| `variant_id` | `int` | → `catalog_toy` |
| `game_folder` | `text` | déduit du chemin |
| `element_folder` | `text` | déduit du chemin |
| `category_folder` | `text` | déduit du chemin |
| `first_seen_at` | `timestamptz` | |
| `last_seen_at` | `timestamptz` | |
| `is_present` | `boolean` | passe à `false` si le fichier disparaît |

**`toy_snapshot`** — historique de progression

| Colonne | Type | Note |
|---|---|---|
| `id` | `bigserial` | PK |
| `toy_pk` | `bigint` | → `toy.id` |
| `captured_at` | `timestamptz` | |
| `content_hash` | `bytea` | SHA-256 du dump brut |
| `xp` | `int` | nullable |
| `level` | `int` | dérivé de l'XP |
| `gold` | `int` | nullable |
| `hat_id` | `int` | nullable |
| `upgrades_bitfield` | `int` | nullable |
| `nickname` | `text` | nullable |
| `playtime_seconds` | `int` | nullable |
| `hero_points` | `int` | nullable |
| `parse_status` | `text` | `OK` / `PARTIAL` / `UNSUPPORTED_GAME` |

> **Règle d'insertion** : n'insérer un snapshot **que si `content_hash` diffère** du
> dernier snapshot de cette figurine. Sinon la table explose sans apporter d'information.

**`trap_content`** — historique du contenu des pièges

| Colonne | Type |
|---|---|
| `id` | `bigserial` |
| `toy_pk` | `bigint` → `toy.id` |
| `captured_at` | `timestamptz` |
| `villain_raw_id` | `int` |
| `is_empty` | `boolean` |

**`villain`** — référentiel des vilains, **auto-alimenté**

| Colonne | Type | Note |
|---|---|---|
| `raw_id` | `int` | PK — valeur lue dans le piège |
| `name` | `text` | nullable tant qu'inconnu |
| `element` | `text` | nullable |
| `named_by_user_at` | `timestamptz` | nullable |

**`scan_run`** — journal des scans

| Colonne | Type |
|---|---|
| `id` | `bigserial` |
| `started_at` / `finished_at` | `timestamptz` |
| `files_scanned` / `files_changed` / `files_failed` | `int` |
| `trigger` | `text` — `STARTUP` / `WATCH` / `MANUAL` / `SCHEDULED` |

### 7.2 Vilains : stratégie d'auto-réparation

Le référentiel des vilains **ne peut pas être bootstrappé** — aucune source externe ne
donne la correspondance `raw_id` → nom.

**Mécanisme retenu** : quand l'app lit un `villain_raw_id` absent de la table, elle
affiche « **Vilain inconnu (0x3A)** » avec un champ de saisie. L'utilisateur nomme, l'app
persiste. Le référentiel se remplit au fil des parties, sans session de RE dédiée.

Le même mécanisme s'applique aux **toy IDs inconnus**.

### 7.3 Risque : collisions d'UID

Le pack est redistribué et généré. Il est **possible** que plusieurs fichiers partagent
le même UID de tag.

→ Le scanner doit **détecter les collisions dès le premier scan** et les journaliser.
→ Si des collisions existent, basculer la clé d'identité sur le **chemin relatif**.
→ **À vérifier empiriquement avant de figer le schéma.**

---

## 8. Pipeline côté agent (laptop)

```
1. Charger agent.yaml (skylandersRoot, serverUrl)
2. Charger le cache local (chemin → dernier SHA-256 envoyé)
3. Scan initial complet :
   a. Parcourir skylandersRoot, filtrer *.sky de 1024 octets
   b. Pour chaque fichier : calculer SHA-256
   c. Si hash == cache → SKIP
   d. Sinon → POST /api/ingest {cheminRelatif, contenuBase64, hash}
   e. Si envoi réussi → mettre à jour le cache
4. Démarrer un WatchService sur skylandersRoot
5. Pour chaque événement de modification (avec debounce 2 s, cf. §8.2) :
   → répéter b-e pour le fichier concerné
6. Tourner jusqu'à fermeture manuelle de l'agent
```

### 8.1 Pipeline côté serveur (déclenché par réception HTTP)

```
POST /api/ingest reçoit {cheminRelatif, contenuBraut, hash}
1. Vérifier taille == 1024 octets, sinon rejeter (400) et journaliser en WARN
2. Recalculer le SHA-256 côté serveur, comparer à celui annoncé (défense en profondeur)
3. Si hash == dernier snapshot connu pour ce chemin → 200 OK, ne rien insérer (idempotent)
4. Parser bloc 0  → UID
5. Parser bloc 1  → toy ID, variant ID
6. Classifier le chemin reçu → jeu, élément, catégorie (§5)
7. Résoudre le nom via catalog_toy (§6)
8. Si jeu == TRAP_TEAM :
     - déchiffrer les deux zones de sauvegarde
     - retenir celle au compteur le plus élevé
     - parser les champs de progression
     - si catégorie == TRAP → parser le vilain
   Sinon : parse_status = UNSUPPORTED_GAME
9. Si les valeurs semblent aberrantes (§8.2) → rejeter (422), conserver le snapshot
   précédent, journaliser en WARN
10. Insérer toy_snapshot (+ trap_content si piège)
11. Répondre 200/201
```

Il n'existe **aucun scan planifié côté serveur** : le serveur ne connaît que ce qui lui
est explicitement envoyé. `is_present = false` (figurine retirée du dossier) ne peut être
déduit que si l'agent envoie un signalement explicite de suppression — **hors périmètre
v1**, puisque le pack ne perd normalement pas de fichiers.

### 8.2 Écritures partielles

Cemu écrit dans les `.sky` **pendant** que tu joues. Le `WatchService` de l'agent peut
donc observer un fichier en cours d'écriture, et l'envoyer dans un état incohérent.

Garde-fous obligatoires :

- **Côté agent** : debounce de 2 s après le dernier événement sur un fichier avant de le
  lire et l'envoyer
- **Côté serveur**, à réception :
  - rejet si taille ≠ 1024 octets
  - rejet si le déchiffrement produit des valeurs aberrantes (XP négatif, niveau > 100,
    compteur de séquence identique sur les deux zones alors que le contenu diffère…)
- en cas de rejet : répondre une erreur explicite à l'agent, journaliser en `WARN` côté
  serveur, **conserver le snapshot précédent** en base. **Ne jamais écrire un snapshot
  douteux.** L'agent pourra retenter à la prochaine modification détectée.

---

## 9. API REST

```
POST /api/ingest                   réception depuis l'agent — {cheminRelatif, contenuBase64, hash}
GET  /api/toys                     liste, filtres: game, element, category, search
GET  /api/toys/{id}                détail + dernier snapshot
GET  /api/toys/{id}/history        série temporelle des snapshots
GET  /api/traps                    tous les pièges + vilain actuel
GET  /api/villains                 référentiel des vilains
PUT  /api/villains/{rawId}         nommer un vilain inconnu
GET  /api/stats                    agrégats (par jeu, par élément, complétion)
GET  /api/scans                    historique des ingestions reçues
GET  /api/images/{toyId}           image ou placeholder généré
```

**Aucun endpoint ne touche un système de fichiers côté laptop.** Le serveur ne monte,
ne lit ni n'écrit jamais dans le dossier source — le seul canal est `/api/ingest`,
alimenté volontairement par l'agent.

**Sécurité de `/api/ingest` :** l'accès réseau est déjà restreint par Tailscale (seuls
les appareils du tailnet peuvent atteindre le serveur). En défense en profondeur, prévoir
un token partagé simple (en-tête `Authorization`) entre l'agent et le serveur, stocké
dans `agent.yaml` et dans la config serveur. Pas besoin d'un système d'auth complet pour
un usage mono-utilisateur.

---

## 10. Interface

### 10.1 Écrans v1

| Écran | Contenu |
|---|---|
| **Collection** | Grille de cartes, filtres par jeu / élément / catégorie, recherche |
| **Détail figurine** | Identité, progression, améliorations, graphe XP dans le temps |
| **Pièges** | Grille groupée par élément, vilain actuel, saisie si inconnu |
| **Statistiques** | Complétion par jeu, répartition par élément, top XP |

### 10.2 Filtre par jeu

Déduit directement du dossier de niveau 1. Les six jeux sont disponibles dès la v1,
puisque l'identité (blocs 0 et 1) est lisible sur tous.

### 10.3 Images

Les visuels sont sous copyright Activision.

→ **Aucun scraper intégré.**
→ Dossier local `images/` alimenté manuellement, fichiers nommés par toy ID :
  `3001.png`, `3001_variant2.png`
→ **Repli gracieux obligatoire** : si l'image est absente, générer un badge avec la
  couleur de l'élément et les initiales du nom.

L'application doit être pleinement utilisable avec **zéro image**.

### 10.4 Indication de fiabilité

Tout champ issu d'un offset non `VÉRIFIÉ` (cf. §4.6) doit être visuellement marqué
comme incertain dans l'UI. Ne jamais présenter une valeur devinée comme un fait.

---

## 11. Architecture logicielle

### 11.1 Séparation clé

```
ToyIdentityParser          ← universel, 6 jeux, blocs 0 et 1
   │
GameSaveParser (interface) ← une implémentation par jeu
   ├── TrapTeamSaveParser  ← seule implémentation en v1
   ├── SuperChargersSaveParser   (plus tard)
   └── …
```

Un jeu sans implémentation renvoie `parse_status = UNSUPPORTED_GAME`. La figurine reste
listée avec son identité, sans détail de progression. **Ajouter un jeu ne doit rien casser.**

### 11.2 Stack

| Composant | Choix |
|---|---|
| Backend (serveur) | Spring Boot (Java) |
| Frontend (serveur) | Vue 3 |
| Base | PostgreSQL |
| Déploiement serveur | Docker Compose sur le homelab |
| **Agent (laptop)** | **Petite appli locale — voir ci-dessous** |
| Accès | Tailscale |
| Prototypage RE | Python (`hashlib` + `pycryptodome`) — phase 0 uniquement |

Le déchiffrement n'exige **aucune dépendance** côté Java : JCE standard suffit.
Il ne s'exécute **que côté serveur** (§2.1) — l'agent ne déchiffre rien, il hash et envoie.

**Choix technique de l'agent :** vu que l'utilisateur le lance manuellement avant de
jouer (pas de service système, pas d'auto-démarrage), un **JAR Java exécutable** est
cohérent avec le reste de la stack — même écosystème, mêmes bibliothèques de hash/HTTP
que le serveur, un seul `./mvnw package` à connaître. Alternative viable : un script
Python autonome, plus léger à lancer, si l'utilisateur préfère ne pas dépendre d'une JVM
sur le laptop pour une tâche aussi simple. Décision à confirmer, sans impact sur le reste
de l'architecture — l'agent ne parle au serveur qu'en HTTP.

---

## 12. Roadmap

| Phase | Contenu | Critère de sortie |
|---|---|---|
| **0** | RE guidée, outil de diff, `FORMAT.md` | Offsets `VÉRIFIÉ` pour toy ID, variant ID, XP |
| **1** | Scanner + classification + `catalog.json` | 702 fichiers listés, >95 % de noms validés |
| **2** | Persistance + API + UI Collection | Inventaire consultable via Tailscale |
| **3** | Parsing Trap Team + snapshots | XP/or/niveau affichés et historisés |
| **4** | Pièges + référentiel vilains auto-alimenté | Contenu des pièges affiché et nommable |
| **5** | Graphes d'historique, statistiques | Courbe d'XP dans le temps |
| **6+** | Autres jeux (SuperChargers, Imaginators…) | — |

---

## 13. Registre des risques

| Risque | Impact | Mitigation |
|---|---|---|
| Offsets erronés (hallucinés ou obsolètes) | **Élevé** | Phase 0 obligatoire, validation croisée §6.3, marquage de confiance |
| Zone de sauvegarde périmée (compteur ignoré) | Élevé | Règle §3.5, test avec deux dumps successifs |
| Écriture partielle par Cemu | Moyen | Debounce + validation + conservation du snapshot précédent |
| Collisions d'UID dans le pack | Moyen | Détection au premier scan, repli sur le chemin |
| Coquilles dans les noms du pack | Faible | Matching tolérant au bootstrap + revue humaine |
| Corruption d'un `.sky` | **Critique** | Lecture seule stricte + Syncthing `Receive Only` + sauvegarde préalable |

---

## 14. Hors périmètre v1

- Écriture / édition de figurines
- Checklist complète des vilains capturés (réside dans la sauvegarde du jeu, pas dans les `.sky`)
- Parsing des sauvegardes Wii U de Cemu
- Support des jeux autres que Trap Team pour le détail de progression
- Téléchargement automatique d'images
- Multi-utilisateur / authentification (accès Tailscale suffisant)
