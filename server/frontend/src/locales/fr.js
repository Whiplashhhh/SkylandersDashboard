export default {
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
  views: { table: 'Classement', grid: 'Grille', traps: 'Pièges' },
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
  detail: {
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
    gamesWithoutParser: 'Aucun parseur de sauvegarde pour {games} : leur taux de complétion est '
      + 'indéterminé, pas nul.',
    levelNotShown: 'Le niveau n’est pas affiché : la courbe XP → niveau n’a pas été mesurée.'
  },
  units: { hoursMinutes: '{h} h {m}', minutes: '{m} min' }
}
