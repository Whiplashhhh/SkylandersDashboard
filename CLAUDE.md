# CLAUDE.md

Instructions pour Claude Code sur ce dépôt. La spécification complète est dans `SPEC.md` — la lire avant toute tâche non
triviale.

---

## Le projet en une phrase

Dashboard web auto-hébergé qui lit **en lecture seule** les dumps de figurines Skylanders (`.sky`) utilisés par le
portail émulé de Cemu, et historise leur progression dans PostgreSQL.

## Architecture en deux composants

- **Agent** (laptop, lancement manuel) : scanne le dossier configuré, hash les fichiers, envoie les deltas au serveur en
  HTTP. **Ne parse et ne déchiffre rien.**
- **Serveur** (homelab, Docker) : reçoit les octets bruts via `/api/ingest`, fait tout le travail de
  déchiffrement/classification/persistance, expose l'API et l'UI Vue consultables depuis n'importe quel appareil du
  tailnet (y compris le téléphone), à tout moment — pas seulement quand l'agent tourne.

**Ne jamais faire migrer de la logique de parsing vers l'agent.** Toute la valeur de cette séparation est de pouvoir
corriger un offset (fréquent pendant la phase 0) en ne redéployant que le serveur.

---

## ⛔ Invariants — ne jamais transgresser

### 1. Lecture seule absolue sur les `.sky`

Aucun code de ce dépôt n'ouvre un `.sky` en écriture, ne le renomme, ne le déplace, ne le supprime. Pas d'exception, pas
de « mode avancé », pas de flag de configuration.

Concrètement :

- **le serveur n'a aucun accès au système de fichiers du laptop, point final** — pas de montage, pas de chemin réseau,
  pas d'accès direct. Le seul canal est `/api/ingest`, et il ne circule que dans un sens (agent → serveur).
- côté agent : ouvrir les `.sky` uniquement en lecture (`StandardOpenOption.READ`) ; ne jamais appeler `Files.write`,
  `Files.move`,
  `Files.delete`, `File.renameTo` sur un chemin sous `skylandersRoot`
- l'API REST du serveur n'expose aucun endpoint qui écrirait vers un chemin laptop

Si une tâche semble exiger une écriture vers le dossier source, **s'arrêter et demander**.

### 2. Ne jamais inventer un offset

Le format `.sky` est rétro-conçu. Les offsets présents dans `SPEC.md` §3 sont des **hypothèses explicitement marquées
comme telles**.

Règle : tout offset utilisé dans le code doit référencer une entrée de `FORMAT.md`
avec son niveau de preuve (`VÉRIFIÉ` / `PROBABLE` / `HYPOTHÈSE`).

```java
// FORMAT.md#toy-id — VÉRIFIÉ (diff sur 12 figurines, 2026-08-16)
private static final int OFFSET_TOY_ID = 0x10;
```

Un offset sans référence est un bug. Si la preuve n'existe pas, **écrire le test qui la produit** avant d'écrire le
parser.

### 3. Le nom de fichier n'est pas un identifiant

L'identité canonique est l'**UID** (bloc 0) et le couple **toy ID + variant ID** (bloc 1). Le nom de fichier sert au
bootstrap et à la vérification croisée, jamais à l'identification en runtime.

### 4. Ne pas réorganiser l'arborescence source

L'arborescence a des incohérences connues (variantes de véhicules à plat, `Pièges/` avec l'élément un cran plus bas).
L'application s'adapte. On ne « corrige » jamais les fichiers.

### 5. Ne jamais confondre « fichier reçu » et « figurine débloquée »

**Le piège le plus probable de ce projet.** Le dossier source contient déjà un fichier pour **tout** Skylanders possible
et chacune de ses variantes — pas seulement ceux réellement joués. Les 702 fichiers seront donc tous ingérés dès le
premier lancement de l'agent, joués ou non.

Conséquence stricte :

- l'existence d'une ligne `toy`, ou d'un `toy_snapshot`, **ne signifie jamais** « débloqué »
- le seul signal valide est `toy.first_played_at IS NOT NULL`, alimenté uniquement quand le contenu de la sauvegarde
  montre une preuve de jeu réelle (cf. `SPEC.md` §3.5bis, §7.1)
- ce signal n'existe que pour un jeu dont le `GameSaveParser` est implémenté ; pour les autres, toutes les figurines
  restent non débloquées par défaut — c'est un état honnête, pas un bug à corriger

`catalog_toy` est le roster complet d'un jeu (débloqué ou non). `toy` ne contient que les figurines dont un fichier a
été reçu — ce qui, avec un pack complet, sera vite l'intégralité du roster, débloquées ou pas. Ne jamais ajouter de
champ `is_owned`/`is_unlocked` sur
`catalog_toy` : le statut se calcule à la lecture par jointure sur `toy.first_played_at`.

---

## Stack

| Couche      | Techno                                    |
|-------------|-------------------------------------------|
| Backend     | Spring Boot, Java 21                      |
| Frontend    | Vue 3 (Composition API)                   |
| Base        | PostgreSQL                                |
| Migrations  | Flyway                                    |
| Build       | Maven                                     |
| Déploiement | Docker Compose (homelab), accès Tailscale |

**Le déchiffrement n'a besoin d'aucune dépendance** : `MessageDigest` (MD5) et `Cipher`
(`AES/ECB/NoPadding`) du JDK suffisent. Ne pas ajouter BouncyCastle.

---

## Commandes

```bash
# Serveur — backend
cd server && ./mvnw spring-boot:run
cd server && ./mvnw test
cd server && ./mvnw verify

# Serveur — frontend
cd server/frontend && npm run dev
cd server/frontend && npm run build

# Base de données locale
docker compose up -d postgres

# Déploiement serveur (Debian) — voir docs/DEPLOIEMENT.md
docker compose -f docker-compose.yml -f docker-compose.server.yml up -d --build

# Raccourci de session sur le PC de jeu (launcher Omarchy)
./tools/install-launcher.sh

# Agent — lancement manuel avant une session de jeu
cd agent && ./mvnw package
java -jar agent/target/agent.jar --config agent.yaml

# Outils de rétro-ingénierie (phase 0)
python tools/skydiff.py <fichier_avant.sky> <fichier_apres.sky>
python tools/skydump.py <fichier.sky>
```

---

## Organisation

```
tools/                       scripts Python de RE (phase 0) — hors build

agent/                       AGENT — laptop, lancement manuel
  src/main/java/.../
    AgentConfig.java           lecture de agent.yaml (chemin en dur, url serveur, token)
    LocalCache.java            chemin → dernier SHA-256 envoyé
    FolderScanner.java         scan initial + WatchService
    IngestClient.java          POST /api/ingest
  agent.yaml.example

server/                      SERVEUR — homelab, Docker
  src/main/java/.../
    ingest/                    IngestController — réception des deltas
    format/                    parsing bas niveau des .sky (déchiffrement inclus)
      SkyCrypto.java             dérivation de clé + déchiffrement
      ToyIdentityParser.java     blocs 0 et 1 — universel 6 jeux
      save/
        GameSaveParser.java        interface
        TrapTeamSaveParser.java    seule implémentation v1
    catalog/                   résolution des noms, reverse-mapping FR→EN
    classification/            classification du chemin reçu (jeu/élément/catégorie)
    domain/                    entités JPA
    api/                       contrôleurs REST (lecture, pour l'UI)
  frontend/                    Vue 3

Dockerfile                   image du serveur (frontend Vue + JAR), visuels exclus
docker-compose.server.yml    surcouche de déploiement homelab (service server + durcissement)
.env.example                 secrets du déploiement (jetons, mot de passe base)
tools/skylanders-session.sh  session de jeu complète — agent + connecteur + Cemu patché
tools/install-launcher.sh    installe le raccourci dans le launcher d'Omarchy

FORMAT.md                    offsets + niveaux de preuve  ← source de vérité technique
SPEC.md                      spécification fonctionnelle
catalog.json                 référentiel toy ID → noms (généré puis corrigé à la main)
```

**Tout le déchiffrement et le parsing vivent sous `server/`.** Le dossier `agent/` ne doit jamais importer `SkyCrypto`
ni aucune classe de `format/`.

---

## Tests

### Fixtures obligatoires

Les tests de parsing s'appuient sur de **vrais dumps** placés dans
`src/test/resources/fixtures/`, avec la vérité attendue en JSON à côté.

**Ne jamais écrire un test de parsing sur des octets inventés.** Un tel test valide l'implémentation contre elle-même et
ne prouve rien.

### Cas de test connus

| Fixture                         | Vérité                                      |
|---------------------------------|---------------------------------------------|
| `buzzer_beak_storm_warning.sky` | piège `Storm Warning`, vilain `Buzzer Beak` |

### Test de non-régression global

Un test parcourt les 702 fichiers et vérifie que le taux de résolution de noms validés reste au-dessus du seuil. Une
chute brutale signale un offset cassé.

---

## Conventions

- Code, noms de classes, commentaires techniques : **anglais**
- Libellés d'interface, données métier : **français** (les noms du pack sont francisés)
- Encodage : UTF-8 partout ; normaliser en NFC toute chaîne issue d'un nom de fichier
- Apostrophes : traiter `'` (U+2019) et `'` (U+0027) comme équivalentes lors des comparaisons de noms
- Logs : `WARN` pour tout fichier rejeté, avec chemin + raison. Ne jamais échouer silencieusement sur un fichier.

---

## Pièges récurrents

**Confondre présence du fichier et déblocage.** Voir invariant 5 ci-dessus. Rappel ici parce que c'est le bug le plus
facile à écrire sans y penser : un `SELECT COUNT(*) FROM toy`
ou un simple `LEFT JOIN` sur l'existence de la ligne donnera 702/702 « débloqués » dès le premier scan — ce qui semble
marcher en test rapide, mais qui est faux. Toujours passer par `first_played_at`.

**Logique de parsing qui migre vers l'agent.** Piège de conception le plus probable :
un jour, par souci d'« optimisation », quelqu'un (humain ou IA) propose de faire calculer le niveau ou l'XP côté agent
pour « alléger le serveur ». Ne jamais faire ça — voir « Architecture en deux composants » en tête de ce fichier.
L'agent hash et envoie, rien d'autre.

**Zone de sauvegarde double.** Le tag a deux zones miroir avec un compteur de séquence. Toujours retenir celle au
compteur le plus élevé. L'oubli produit des données périmées de manière intermittente — symptôme typique : « l'XP ne
monte qu'une fois sur deux ».

**Écritures partielles.** Cemu écrit pendant le jeu, Syncthing propage. Debounce 2 s, rejet si taille ≠ 1024, rejet si
valeurs aberrantes. En cas de doute, **conserver le snapshot précédent** plutôt qu'en écrire un mauvais.

**Snapshots redondants.** N'insérer un `toy_snapshot` que si le SHA-256 du dump diffère du dernier connu. Sans ça la
table grossit sans porter d'information.

**Coquilles dans les noms du pack** (`Cobra Candabra`, `Pyrmid`, `Deja Vu`). Matching tolérant autorisé **au bootstrap
uniquement**, jamais en runtime.

**Fichiers parasites.** Filtrer `desktop.ini`, `*.txt`, `*.md`, `*.png`, `*.jpg`. Ne scanner que `*.sky` de exactement
1024 octets.

---

## Ordre de travail

Respecter les phases de `SPEC.md` §12. En particulier : **la phase 0 (rétro-ingénierie)
précède tout code applicatif.** Écrire un parser avant d'avoir validé les offsets produit du code qu'il faudra jeter.

---

## En cas de doute

Demander plutôt que supposer, en particulier sur :

- un offset non documenté dans `FORMAT.md`
- toute opération qui toucherait au système de fichiers source
- l'ajout d'une dépendance
- un changement de schéma de base

Ce projet manipule des données de jeu accumulées sur des années et non reproductibles. La prudence prime sur la vitesse.