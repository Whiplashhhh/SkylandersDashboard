export default {
  bridge: {
    "toySearch": "Rechercher un Skylander…",
    "noResults": "Aucun Skylander trouvé.",
    "chooseCopy": "Quelle copie veux-tu utiliser ?",
    "copyHint": "Ce modèle a plusieurs sauvegardes. Clique sur celle à poser ; ce choix sera retenu pendant cette session.",
    "directHint": "Glisse une figurine sur une case ou clique sur un résultat : elle apparaît directement dans le jeu. Le × la retire.",
    "directSync": "Les cases reflètent le portail de Cemu et se synchronisent entre tes fenêtres.",

    "live": "Portail émulé",
    "title": "Cemu en direct",
    "token": "Clé de contrôle du dashboard",
    "connect": "Connecter",
    "disconnect": "Déconnecter",
    "setup": "Lance Cemu avec le pont et le connecteur sur le PC de jeu. La clé de contrôle est configurée sur le serveur.",
    "choose": "Figurine à charger",
    "search": "Rechercher dans les fichiers du PC…",
    "select": "Sélectionner un fichier précis",
    "load": "Charger dans Cemu",
    "pending": "Confirmation de Cemu…",
    "remove": "Retirer",
    "clear": "Vider Cemu",
    "confirmClear": "Confirmer le retrait de tout",
    "cancel": "Annuler",
    "figure": "Figurine",
    "position": "Emplacement Cemu {n}",
    "observed": "{n} figurine(s) confirmée(s) dans Cemu",
    "lastObserved": "Dernier état connu — connexion indisponible",
    "prepared": "Disposition préparée",
    "preparedHint": "Cette grille prépare ta sélection. Pour jouer, charge les fichiers dans Cemu avec les commandes ci-dessus.",
    "status": {
        "LOCKED": "À connecter",
        "READY": "Connecté à Cemu",
        "CONNECTOR_UNAVAILABLE": "Connecteur absent",
        "CEMU_UNAVAILABLE": "Cemu indisponible",
        "PORTAL_DISABLED": "Portail désactivé",
        "SERVER_UNAVAILABLE": "Serveur indisponible"
    },
    "outcome": {
        "PENDING": "Commande en attente",
        "APPLIED": "Commande confirmée par Cemu",
        "REJECTED": "Commande refusée",
        "EXPIRED": "Commande expirée",
        "UNKNOWN": "Résultat non confirmé. État du portail relu."
    },
    "errors": {
        "WRONG_SLOT_TYPE": "Pose les pièges dans la serrure et les figurines dans les neuf cases.",
        "CHOOSE_COPY": "Choisis la copie de cette figurine à utiliser.",

        "PORTAL_AUTH_REQUIRED": "Clé de contrôle absente ou incorrecte.",
        "NETWORK_ERROR": "Connexion interrompue. Vérifie le dernier état avant de réessayer.",
        "FILE_UNAVAILABLE": "Fichier absent ou inaccessible sur le PC.",
        "PATH_FORBIDDEN": "Fichier situé hors du dossier autorisé.",
        "INVALID_DUMP": "Dump invalide (taille ou en-tête).",
        "ALREADY_LOADED": "Ce fichier est déjà chargé.",
        "PORTAL_FULL": "Le portail est plein.",
        "STALE_STATE": "Le portail a changé. Son état a été actualisé : réessaie.",
        "STALE_SESSION": "Cemu a redémarré. Réessaie depuis son nouvel état.",
        "SESSION_CHANGED": "Le connecteur a redémarré.",
        "EXPIRED": "Le délai est dépassé ; aucune ancienne commande ne sera rejouée.",
        "CEMU_UNAVAILABLE": "Cemu est indisponible.",
        "PORTAL_DISABLED": "Active le portail émulé ou relance Cemu avec le pont.",
        "INVALID_SLOT": "Emplacement invalide.",
        "UNKNOWN_COMMAND": "Commande non reconnue.",
        "COMMAND_IN_PROGRESS": "Une commande attend déjà sa confirmation.",
        "INCOMPATIBLE_VERSION": "Versions du pont incompatibles.",
        "INVALID_COMMAND_ID": "Identifiant de commande invalide.",
        "INVALID_REQUEST": "Requête invalide."
    }
},
  collectionSort: { label: 'Trier la collection', direction: 'Sens du tri', recent: 'Derniers joués', element: 'Type (élément)', name: 'Ordre alphabétique', category: 'Catégorie', game: 'Jeu', asc: 'Croissant', desc: 'Décroissant' },
  dashboard: {
    noCaptured: 'Aucun vilain ne correspond à cette sélection.',
    "companion": "Le repaire du Maître du Portail",
    "skip": "Aller à la collection",
    "updated": "Données actualisées",
    "unavailable": "Actualisation indisponible",
    "refresh": "Actualiser les données",
    "retry": "Réessayer",
    "navigation": "Vues de la collection",
    "overview": "Aperçu de la collection",
    "eyebrow": "Journal des Skylands",
    "welcome": "Une collection",
    "welcomeAccent": "Mille aventures",
    "intro": "Retrouve tes héros, leurs exploits et les aventures qu’il reste à écrire.",
    "lastPlayed": "Dernière sauvegarde",
    "unlocked": "Figurines débloquées",
    "confirmed": "Progression confirmée",
    "received": "Figurines reçues",
    "inLibrary": "Présentes dans la bibliothèque",
    "elements": "Éléments joués",
    "elementsHint": "Avec au moins une figurine débloquée",
    "games": "Jeux représentés",
    "gamesHint": "Dans ta collection",
    "coverage": "Le déblocage ne peut être confirmé que pour les jeux dont la sauvegarde est prise en charge.",
    "title_grid": "Ta collection",
    "subtitle_grid": "Chaque héros a son histoire. Explore la tienne.",
    "title_table": "Le classement",
    "subtitle_table": "Tes héros, classés selon leurs exploits.",
    "title_traps": "La chambre des pièges",
    "subtitle_traps": "Un œil sur les prisonniers de ton portail.",
    "title_villains": "Les vilains des Skylands",
    "subtitle_villains": "Retrouve les vilains actuellement enfermés dans tes pièges.",
    "unlockedFirst": "Figurines débloquées en premier",
    "empty": "Ton aventure commence ici",
    "emptyHint": "Lance l’agent local pour recevoir ta collection de figurines.",
    "emptyFiltered": "Aucun héros à l’horizon",
    "notPlayed": "Non débloquée",
    "openToy": "Voir la fiche de {name}",
    "localPortal": "Disposition locale",
    "portalHint": "Ce panneau prépare ta sélection. Il ne contrôle pas encore le portail de Cemu."
},
  app: {
    title: 'Collection Skylanders',
    counters: '{unlocked} débloquées / {total} au roster'
  },
  settings: { open: 'Paramètres', title: 'Paramètres', theme: 'Thème', language: 'Langue' },
  themes: {
    system: 'Système', systemHint: 'Suit le réglage du navigateur',
    light: 'Clair', dark: 'Sombre',
    forge: 'Forge Ardente', forgeHint: 'Feu',
    crypte: 'Crypte', crypteHint: 'Mort-Vivant',
    arcane: 'Arcane', arcaneHint: 'Magie'
  },
  locales: { system: 'Système', systemHint: 'Suit la langue du navigateur' },
  filters: {
    search: 'Rechercher un nom…',
    allCategories: 'Toutes les catégories',
    allStates: 'Toutes', unlocked: 'Débloquées', locked: 'Non débloquées',
    perPage: '{n} par page', reset: 'Réinitialiser'
  },
  views: { table: 'Classement', grid: 'Collection', traps: 'Pièges', villains: 'Vilains' },
  common: {
    loading: 'Chargement…', error: 'Erreur : {message}',
    figurines: '{n} figurine | {n} figurines'
  },
  games: { all: 'Tous les jeux', pick: 'Choix du jeu' },
  elements: {
    all: 'Tous', allTitle: 'Tous les éléments', filter: 'Filtrer par élément',
    Feu: 'Feu', Eau: 'Eau', Vie: 'Vie', Magie: 'Magie', Tech: 'Tech', Terre: 'Terre',
    Air: 'Air', 'Mort-Vivant': 'Mort-Vivant', 'Lumière': 'Lumière', 'Ténèbres': 'Ténèbres',
    Kaos: 'Kaos', Inconnu: 'Inconnu'
  },
  categories: {
    CHARACTER: 'Personnages', GIANT: 'Géants', TRAP: 'Pièges', VEHICLE: 'Véhicules',
    SIDEKICK: 'Acolytes', MINI: 'Minis', ITEM: 'Objets', ADVENTURE_PACK: 'Packs Aventure',
    CHEST: 'Coffres', CREATION_CRYSTAL: 'Cristaux de Création', UNKNOWN: 'Autres'
  },
  categoriesOne: {
    CHARACTER: 'Personnage', GIANT: 'Géant', TRAP: 'Piège', VEHICLE: 'Véhicule',
    SIDEKICK: 'Acolyte', MINI: 'Mini', ITEM: 'Objet', ADVENTURE_PACK: 'Pack Aventure',
    CHEST: 'Coffre', CREATION_CRYSTAL: 'Cristal', UNKNOWN: 'Autre'
  },
  columns: {
    name: 'Nom', trapName: 'Piège', element: 'Élément', game: 'Jeu', category: 'Catégorie',
    xp: 'XP', gold: 'Or', upgrades: 'Améliorations', playtime: 'Temps de jeu',
    lastSaved: 'Dernière partie', lastCapture: 'Dernière capture',
    firstPlayed: 'Première pose', firstUsed: 'Première utilisation',
    villain: 'Vilain enfermé'
  },
  table: {
    sortBy: 'Trier par {label}', noResults: 'Aucune figurine ne correspond à ces filtres.',
    noProgress: 'Ces entrées ne portent pas de progression : les colonnes affichées sont celles '
      + 'qui ont un sens pour elles.',
    previous: 'Précédent', next: 'Suivant',
    pageInfo: 'Page {page} / {pages} — {total} entrées',
    lockedTitle: 'Jamais posée sur le portail',
    cappedTitle: 'Champ saturé à {ceiling}, la valeur réelle est peut-être plus haute',
    unsupportedTitle: 'Aucun parseur de sauvegarde pour ce jeu', na: 'n/d',
    emptyTrap: 'vide', unnamedVillain: 'Vilain #{id}',
    unnamedTitle: 'Identifiant {id} — à nommer depuis l’écran Pièges'
  },
  traps: {
    summary: '{occupied} piège occupé sur {total}. | {occupied} pièges occupés sur {total}.',
    unnamed: '{n} vilain sans nom — clique sur « Vilain #… » pour le nommer.'
      + ' | {n} vilains sans nom — clique sur « Vilain #… » pour le nommer.',
    note: 'Un piège ne contient qu’un vilain à la fois. Ce que tu vois ici est leur contenu actuel.',
    empty: 'vide', placeholder: 'Nom du vilain #{id}', save: 'OK', cancel: 'Annuler',
    renameTitle: 'Identifiant {id} — cliquer pour renommer', unknownVillain: 'Vilain inconnu'
  },
  portal: {
    title: 'Portail',
    show: 'Afficher portail', hide: 'Masquer portail', close: 'Fermer le portail',
    reattach: 'Rattacher portail',
    windowTitle: 'Portail — Collection Skylanders',
    detach: 'Détacher', detachTitle: 'Ouvrir le portail dans une fenêtre à part',
    clearAll: 'Tout vider', confirmClear: 'Confirmer ?',
    count: '{n} posée sur {total} emplacements | {n} posées sur {total} emplacements',
    emptySlot: 'libre', trapSlot: 'piège',
    place: 'Mettre sur le portail', alreadyPlaced: 'Déjà sur le portail',
    remove: 'Retirer du portail',
    placedAt: 'Posée le {date}',
    full: 'Portail plein : retire une figurine avant d’en poser une autre.',
    beyond: 'hors grille',
    beyondTitle: 'Posée au-delà de la grille configurée. Retire-la pour libérer '
      + 'l’emplacement, il disparaîtra ensuite.',
    note: 'Glisse une figurine sur un emplacement, ou utilise le bouton + des listes — '
      + 'le glisser-déposer n’existe pas sur écran tactile, le bouton marche partout.',
    syncNote: 'La disposition est enregistrée sur le serveur. Les fenêtres de ce navigateur se '
      + 'synchronisent en direct ; les autres appareils la reliront à leur prochain chargement.'
  },
  villains: {
    doomRaider: 'Doom Raider',
    captured: 'Enfermé dans un piège', notCaptured: 'Jamais capturé',
    rawId: 'Identifiant du tag', capturedAt: 'Relevé le',
    summary: '{n} vilain capturé sur {total}. | {n} vilains capturés sur {total}.',
    note: 'Un vilain compte comme capturé quand un piège le contient en ce moment. Les tags ne '
      + 'portent pas de tableau de chasse : celui-ci vit dans la sauvegarde de la console.',
    onlyCaptured: 'Ne montrer que les vilains capturés',
    openStory: 'Voir la fiche de {name}',
    noStory: 'Aucun résumé pour ce vilain.',
    source: 'Résumé issu du wiki Skylanders (Fandom), sous licence CC BY-SA.'
  },
  detail: {
    wiki: 'Fiche du wiki', wikiTitle: 'Ouvrir la page du wiki Skylanders (nouvel onglet)',
    close: 'Fermer', identity: 'Identité', identityValue: 'toy {toyId} · variante {variantId}',
    games: 'Jeux', status: 'Statut',
    unlocked: 'Débloquée', neverPlayed: 'Jamais posée sur le portail',
    notReceived: 'Aucun fichier reçu',
    firstPlayed: 'Première partie', lastSaved: 'Dernière sauvegarde',
    lastReading: 'Dernier relevé', nickname: 'Surnom',
    levelNote: 'Le niveau n’est pas affiché : la correspondance XP → niveau n’a pas encore été '
      + 'mesurée sur de vraies parties.',
    history: 'Historique détaillé · {n} relevés',
    files: '{n} fichier | {n} fichiers',
    loadFailed: 'Chargement impossible : {message}',
    tableSaved: 'Sauvegardé', tableBlocks: 'Blocs'
  },
  chart: {
    title: 'Progression', byDate: 'Par date', byReading: 'Par relevé',
    byDateTitle: 'Espacement proportionnel au temps écoulé',
    byReadingTitle: 'Chaque sauvegarde occupe la même largeur',
    current: 'Actuel : {value}', readings: '{n} relevés', reading: 'relevé {n}',
    none: 'Aucun relevé pour cette mesure.',
    single: 'Un seul relevé : il faut au moins deux sauvegardes pour tracer une évolution.'
  },
  notices: {
    unsupportedGame: 'Aucun parseur de sauvegarde pour ce jeu : identité seule, progression et '
      + 'déblocage indéterminés.',
    nameNeedsReview: 'Nom à revoir : plusieurs libellés se rattachent à cette identité.',
    xpCapped: 'XP à {value} : le champ lu sature à {ceiling}, la valeur réelle est peut-être '
      + 'plus haute.',
    neverPlayed: 'Fichier reçu mais aucune trace de jeu : figurine jamais posée sur le portail.',
    trapGoesInTrapSlot: 'Un piège se pose dans la serrure à piège, sous la grille : le '
      + 'portail n’en a qu’une.',
    onlyTrapsInTrapSlot: 'La serrure à piège ne prend que des pièges.',
    gamesWithoutParser: 'Aucun parseur de sauvegarde pour {games} : leur taux de complétion est '
      + 'indéterminé, pas nul.',
    levelNotShown: 'Le niveau n’est pas affiché : la courbe XP → niveau n’a pas été mesurée.'
  },
  units: { hoursMinutes: '{h} h {m}', minutes: '{m} min' }
}
