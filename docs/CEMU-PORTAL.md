# Portail Cemu : utilisation et protocole v1

## État de la livraison

Implémentation Linux (dont Omarchy), 13 septembre 2026. Le navigateur commande
le serveur ; un connecteur Python sur le PC de jeu interroge le serveur toutes
les 500 ms puis appelle Cemu par une socket Unix privée. Aucune fenêtre USB
n'est nécessaire. Le lancement du pont active le portail émulé pour cette session.

Bases compatibles, **avec les modifications de cette livraison appliquées** :

- Cemu : `3310f3b8b184d64a62b89fd59088c799432badf5`.
- Dashboard : `01bb5beca14ec3d7fd53b9a6221deee4e08c51de`, avec le travail local
  préexistant sur le portail, le catalogue et les vilains conservé.

Ces commits seuls ne contiennent pas le pont. Aucun commit ni push n'a été créé.
Le patch Cemu doit être compilé ; le binaire Cemu officiel n'expose pas ce protocole.
Windows/macOS : pont non activé, non validé. Les autres fonctions de Cemu restent
compilables ; le code socket est limité à Linux.

## Installation

### Serveur dashboard

Configurer deux secrets distincts, indépendants du secret d'ingestion :

```sh
export PORTAL_CONNECTOR_TOKEN="<secret pour le connecteur>"
export PORTAL_CONTROL_TOKEN="<secret pour les navigateurs autorisés>"
```

Générer chaque secret avec `python3 -c 'import secrets; print(secrets.token_urlsafe(32))'`.
Les transmettre dans la configuration privée du serveur, pas dans Git.
Reconstruire et redémarrer le serveur avec ces variables. En développement,
utiliser le Maven installé (`mvn verify`, puis `mvn spring-boot:run` dans `server/`).
Le dépôt ne fournit actuellement pas de wrapper `mvnw`.
Pour Docker, déclarer ces deux variables dans le service serveur de la configuration
de déploiement. Une valeur vide désactive l'accès correspondant (401).

### PC Omarchy

Compiler Cemu suivant son `BUILD.md`. La configuration de build présente a été
compilée avant modification puis après patch avec :

```sh
cd Cemu
cmake --build build -j 2
```

Elle produit ici `Cemu/bin/Cemu_debug`. Un build Release séparé est préférable
pour jouer régulièrement ; suivre `Cemu/BUILD.md`, sans réutiliser à l'aveugle
les options d'un autre OS.

Copier `connector/connector.json.example` vers une configuration privée située
**hors de la racine des figurines**, par exemple
`~/.config/skylanders/connector.json`, mode `0600`, et renseigner :

- `skylandersRoot` : `/home/whiplashhh/Games/Cemu/skylanders` ;
- `socket` : `/run/user/<UID>/cemu-portal/control.sock` (`id -u` donne l'UID) ;
- `serverUrl` : URL du dashboard, idéalement HTTPS sur le tailnet ;
- `token` : même valeur que `PORTAL_CONNECTOR_TOKEN`, pas la clé navigateur.

Depuis la racine de l'espace de travail :

```sh
python3 SkylandersDashboard/connector/start_session.py \
  --config ~/.config/skylanders/connector.json \
  --cemu Cemu/bin/Cemu_debug \
  --game "$HOME/Games/Cemu/Skylanders - Trap Team (Europe) (En,Fr,De,Es,It,Nl,Sv,No,Da,Fi).wux"
```

Le lanceur crée le répertoire de socket privé, lance Cemu et le connecteur,
puis arrête le connecteur à la fermeture de Cemu. Il peut récupérer une socket
abandonnée après crash, uniquement si elle appartient à l'utilisateur et refuse
les connexions. Il refuse une instance déjà active. Ne lancer qu'un connecteur
pour ce serveur mono-utilisateur.

Pour un Cemu lancé séparément, définir `CEMU_PORTAL_SOCKET` et
`CEMU_PORTAL_ROOT` avant son démarrage, avec un répertoire parent de socket
appartenant à l'utilisateur et de mode `0700`, puis lancer :

```sh
python3 SkylandersDashboard/connector/skylanders_connector.py \
  --config ~/.config/skylanders/connector.json
```

### Navigateur / téléphone

Ouvrir le portail du dashboard (`/portail` ou « Afficher portail »), entrer la
valeur de `PORTAL_CONTROL_TOKEN`, puis connecter. Cette connexion n'est nécessaire
qu'une fois pour la session du navigateur : la fenêtre principale et le portail
détaché partagent la clé via un BroadcastChannel de même origine. « Déconnecter »
l'efface dans les fenêtres ouvertes. Le navigateur n'appelle jamais le PC de jeu directement.

La grille **9 figurines + 1 piège est le portail en direct** :

- Glisser une figurine de la collection vers une case la pose dans Cemu.
- La recherche au-dessus du portail affiche les six premiers résultats, en deux
  rangées de trois images. Cliquer pose dans la première case libre ; un piège va
  automatiquement dans la serrure. Les boutons + des listes utilisent le même canal.
- Le × retire immédiatement la figurine. « Tout vider » retire tous les occupants,
  sans seconde confirmation, y compris ceux ajoutés depuis la fenêtre native.
- Glisser une figurine déjà présente vers une case libre change sa position graphique
  sans la recharger dans le jeu. Vers une case occupée, l'occupant est retiré d'abord.
- Remplacer une figurine enchaîne automatiquement retrait confirmé puis chargement.
  Ce remplacement n'est pas atomique : si le chargement échoue, la case reste vide,
  conformément à l'état réel, et l'erreur apparaît. Aucun réessai automatique.

Le serveur rapproche les chemins ingérés de l'inventaire du connecteur pour retrouver
le **fichier exact**, sans deviner depuis son nom. Pour un modèle ayant plusieurs copies,
un choix illustré ponctuel permet de choisir la sauvegarde ; ce choix est retenu pendant
la session. Sans fichier à la fois ingéré et disponible, la pose est refusée : vérifier
que l'agent et le connecteur utilisent la même racine.

Une animation d'attente accompagne la commande ; les images dans les cases ne changent
qu'après observation de Cemu. Les actions natives et les autres fenêtres sont relues en
continu. Les emplacements Cemu supplémentaires restent visibles hors grille et retirables.
L'ancienne disposition enregistrée par `/api/portal` n'est ni affichée comme état Cemu,
ni rejouée lors d'une connexion. Poser une figurine ne change jamais son déblocage.

Cette interface exige le serveur et les assets web de la même livraison. Le protocole
Cemu/connecteur reste en v1 : aucune recompilation de Cemu ni modification de sa configuration
n'est nécessaire pour cette évolution de l'interface. Reconstruire le frontend puis le JAR,
relancer le serveur et recharger les fenêtres du navigateur. Le connecteur se reconnecte.

## Garanties et limites

- Le connecteur n'ouvre aucun dump en écriture et ne parse aucun octet. Il publie
  un inventaire de chemins relatifs et vérifie taille/racine avant une commande.
  L'agent d'ingestion reste indépendant, en lecture seule. Le serveur ne monte
  ni ne lit le disque du PC. Seul Cemu effectue ses écritures normales de jeu.
- La racine autorisée est vérifiée après résolution des liens dans le connecteur
  et dans Cemu. Pas de chemin fourni par le navigateur. Les fichiers sont validés
  par Cemu : 1024 octets et CRC d'en-tête selon son algorithme existant.
- Le `fileId` opaque est le SHA-256 du chemin relatif UTF-8 exact, stable lorsque
  le jeu modifie les octets. Il désigne une entrée de fichier, pas un modèle ni
  une preuve de déblocage. Deux chemins distincts ont deux identifiants.
- Les indices affichés proviennent des **16 indices internes Cemu**, présentés
  de 1 à 16 dans l'UI. Ils ne correspondent pas aux cases de la grille préparée.
  La capacité de stockage du portail n'est pas un nombre de joueurs simultanés.
- Les commandes et les actions de la fenêtre native passent par le même service
  `SkylanderUSB`. Les accès au moteur sont verrouillés ; les commandes réseau sont
  exécutées sur le thread GUI par un timer, sans boîte de dialogue. La fenêtre USB
  relit les emplacements réels toutes les 200 ms.
- Une pose ne débloque jamais une figurine dans la collection. L'ingestion et
  `first_played_at` demeurent la source du déblocage et de la progression.
- Le navigateur attend la confirmation. Un timeout après livraison donne
  `UNKNOWN`, jamais une réussite inventée. L'état observé est relu au prochain
  échange. Après coupure, aucun chargement périmé n'est rejoué.
- Une seule commande à la fois ; pas de remplacement atomique. Retirer puis
  charger est volontairement explicite. Les commandes ne survivent pas au
  redémarrage du serveur. Inventaire rafraîchi toutes les 10 secondes.
- Pièges, Swap Force et véhicules ne sont pas annoncés comme validés en jeu.
  Le transport peut charger leurs dumps, mais aucune règle de compatibilité de
  jeu, de combinaison ou de placement physique n'est déduite de leur présence.
- L'activation du portail est faite au démarrage du pont. Le contrôle distant
  n'ajoute pas une API générale de démarrage/arrêt du jeu ou de configuration Cemu.

## Contrat local Cemu v1

Socket Unix `0600`, dans un répertoire `0700`. Aucun port réseau. Un JSON UTF-8
terminé par `\n`, une réponse par connexion. Requête maximum 8192 octets,
connexion maximum 2 secondes. Le client utilise un timeout de 3 secondes.
La lecture ne consomme pas les transitions USB du jeu.

Lecture : `{"version":1,"command":"getState"}`.
`getCapabilities` retourne le même état avec `capacity:16`.

Mutation :

```json
{
  "version": 1,
  "command": "loadFigure",
  "commandId": "UUID unique",
  "epoch": "epoch renvoyé par getState",
  "expectedRevision": 4,
  "expiresAt": 1789280000000,
  "path": "/racine/autorisee/figurine.sky"
}
```

`expiresAt` est un instant Unix en millisecondes (maximum 5 secondes dans le
futur ; le serveur émet un délai de 2 secondes). Synchroniser les horloges PC et
serveur. `removeFigure` remplace `path` par `slot` (0–15).
`clearAll` n'a pas d'argument supplémentaire.

Réponse : `version`, `ok`, `error`, `assignedSlot` (-1 si non applicable), `state`.
L'état porte `epoch` (nouveau à chaque Cemu), `revision` (pose/retrait), `enabled`,
`capacity`, et `slots:[{index,toyId,variantId,path}]` occupés. Les chemins absolus
restent locaux : le connecteur les remplace par `fileId` avant l'envoi au serveur.
Un fichier ouvert via l'UI native hors inventaire garde ses IDs et un `fileId:null`.

Erreurs : `INVALID_REQUEST`, `INCOMPATIBLE_VERSION`, `INVALID_COMMAND_ID`,
`UNKNOWN_COMMAND`, `STALE_SESSION`, `STALE_STATE`, `EXPIRED`, `PORTAL_DISABLED`,
`INVALID_SLOT`, `PATH_FORBIDDEN`, `FILE_UNAVAILABLE`, `INVALID_DUMP`,
`ALREADY_LOADED`, `PORTAL_FULL`. Les 512 derniers identifiants de mutation sont
mémorisés par Cemu ; un retry renvoie sa réponse initiale sans réexécution.
Les préconditions et l'expiration restent obligatoires. Ne pas réutiliser un ID.

Client de diagnostic :

```sh
python3 connector/portal_client.py --socket /chemin/control.sock getState
python3 connector/portal_client.py --socket /chemin/control.sock loadFigure --path /copie-test/figure.sky
python3 connector/portal_client.py --socket /chemin/control.sock removeFigure --slot 0
```

## Contrat serveur v1

Tous les endpoints `/api/bridge` exigent `Authorization: Bearer …` et retournent
`Cache-Control: no-store`. Le token connecteur est accepté uniquement pour
`POST /api/bridge/exchange`. Le token navigateur est utilisé pour :

- `GET /api/bridge/portal` : état enrichi du portail direct, grille 9 + piège,
  `layoutRevision`, `pendingSlot`, résultat et disponibilité ;
- `POST /api/bridge/portal` : `commandId` (UUID), `command` (`place`, `remove`, `clear`),
  `epoch`, `expectedRevision`, `layoutRevision`, et `index` pour poser/retirer.
  Une pose ajoute `toyId`, `variantId` et `fileId` (facultatif uniquement si la copie
  disponible est unique). `index` désigne une case graphique, jamais un indice Cemu.
  Le serveur maintient la correspondance à partir des observations. Les changements de
  disposition invalident les requêtes périmées, même sans changement dans l'émulateur.
  Une action composite expire après 5 secondes ; chaque étape garde le délai de 2 secondes
  du canal existant. Un changement de session ou une action native entre les étapes annule
  la suite. Les refus de cette API directe sont des HTTP 409 avec un code `error` ;
- `GET /api/bridge` : disponibilité, dernier état, fichiers, résultat et `busy` ;
- `POST /api/bridge/commands` : `commandId`, `command`, `epoch`,
  `expectedRevision`, plus `fileId` pour charger ou `slot` pour retirer.

L'échange sortant contient `version:1`, `session` (UUID du connecteur),
`availability`, `state`, `filesDigest`, éventuellement `files:[{id,relativePath}]`
et `result:{commandId,ok,error}`. La réponse contient `command:null` ou une commande
avec `session`, `epoch`, `expectedRevision`, `expiresAt` et les arguments, plus
`needFiles`. Une commande délivrée ne l'est jamais une deuxième fois. Le connecteur
change de session après une erreur HTTP et abandonne les actions en attente.

**Publication de l'inventaire (évolution du 23 septembre 2026).** `files` ne
circule que lorsque la liste change ou que le serveur la réclame ; les autres
échanges ne portent que `filesDigest`, l'empreinte SHA-256 des identifiants triés,
un par ligne. Le serveur recalcule cette empreinte sur ce qu'il a réellement reçu :
l'empreinte annoncée sert à comparer, jamais à décrire un contenu qu'il n'a pas vu.
Empreinte inconnue — reconnexion, redémarrage du serveur, session changée — il vide
son inventaire et répond `needFiles:true` plutôt que de commander sur une liste
périmée ; le connecteur republie alors la liste complète.

Raison : 702 dumps pèsent 104 Ko et le connecteur sonde deux fois par seconde, soit
207 Ko/s en continu. Sur une socket locale c'était gratuit ; vers un serveur distant,
chaque sondage prenait 0,4 s pour 0,1 s utile et dépassait le délai d'attente dès que
le lien était partagé avec l'ingestion. Mesuré après changement : 1 échange sur 12
porte la liste, le reste fait 400 octets.

Le champ est facultatif dans les deux sens, donc un connecteur qui envoie toujours
`files` reste accepté. L'inverse n'est pas vrai : un connecteur qui omet `files`
face à un serveur antérieur reçoit `400 INVALID_EXCHANGE`. Mettre à jour le serveur
avant le connecteur, ou les deux ensemble.

Disponibilités : `READY`, `PORTAL_DISABLED`, `CEMU_UNAVAILABLE`,
`CONNECTOR_UNAVAILABLE` après 5 secondes sans échange. Le navigateur ajoute
`SERVER_UNAVAILABLE` lorsqu'il ne peut plus lire le serveur.
Résultats : `PENDING`, `APPLIED`, `REJECTED`, `EXPIRED`, `UNKNOWN`.
`APPLIED` confirme le chargement/retrait dans le moteur, pas une reconnaissance
par un jeu donné. HTTP 401 pour un secret absent/invalide, 409 pour concurrence
ou état périmé, 503 lorsque Cemu n'est pas prêt.

Toute évolution incompatible de ces champs ou de leur sémantique impose une
nouvelle version et une mise à jour coordonnée Cemu/connecteur/serveur.
L'absence de pont ou une version inconnue ne doit jamais basculer en simulation.

## Validation

Les résultats reproductibles et les essais en jeu sont consignés dans
[CEMU-PORTAL-VALIDATION.md](CEMU-PORTAL-VALIDATION.md).
