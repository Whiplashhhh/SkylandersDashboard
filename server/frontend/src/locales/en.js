export default {
  app: {
    title: 'Skylanders Collection',
    counters: '{unlocked} unlocked / {total} in roster'
  },
  settings: { open: 'Settings', title: 'Settings', theme: 'Theme', language: 'Language' },
  themes: {
    system: 'System', systemHint: 'Follows the browser setting',
    light: 'Light', dark: 'Dark',
    forge: 'Burning Forge', forgeHint: 'Fire',
    crypte: 'Crypt', crypteHint: 'Undead',
    arcane: 'Arcane', arcaneHint: 'Magic'
  },
  locales: { system: 'System', systemHint: 'Follows the browser language' },
  filters: {
    search: 'Search a name…',
    allCategories: 'All categories',
    allStates: 'All', unlocked: 'Unlocked', locked: 'Locked',
    perPage: '{n} per page', reset: 'Reset'
  },
  views: { table: 'Ranking', grid: 'Grid', traps: 'Traps' },
  common: {
    loading: 'Loading…', error: 'Error: {message}',
    figurines: '{n} figurine | {n} figurines'
  },
  games: { all: 'All games', pick: 'Pick a game' },
  elements: {
    all: 'All', allTitle: 'All elements', filter: 'Filter by element',
    Feu: 'Fire', Eau: 'Water', Vie: 'Life', Magie: 'Magic', Tech: 'Tech', Terre: 'Earth',
    Air: 'Air', 'Mort-Vivant': 'Undead', 'Lumière': 'Light', 'Ténèbres': 'Dark',
    Kaos: 'Kaos', Inconnu: 'Unknown'
  },
  categories: {
    CHARACTER: 'Characters', GIANT: 'Giants', TRAP: 'Traps', VEHICLE: 'Vehicles',
    SIDEKICK: 'Sidekicks', MINI: 'Minis', ITEM: 'Items', ADVENTURE_PACK: 'Adventure Packs',
    CHEST: 'Chests', CREATION_CRYSTAL: 'Creation Crystals', UNKNOWN: 'Other'
  },
  categoriesOne: {
    CHARACTER: 'Character', GIANT: 'Giant', TRAP: 'Trap', VEHICLE: 'Vehicle',
    SIDEKICK: 'Sidekick', MINI: 'Mini', ITEM: 'Item', ADVENTURE_PACK: 'Adventure Pack',
    CHEST: 'Chest', CREATION_CRYSTAL: 'Crystal', UNKNOWN: 'Other'
  },
  columns: {
    name: 'Name', trapName: 'Trap', element: 'Element', game: 'Game', category: 'Category',
    xp: 'XP', gold: 'Gold', upgrades: 'Upgrades', playtime: 'Playtime',
    lastSaved: 'Last played', lastCapture: 'Last capture',
    firstPlayed: 'First placed', firstUsed: 'First used',
    villain: 'Trapped villain'
  },
  table: {
    sortBy: 'Sort by {label}', noResults: 'No figurine matches these filters.',
    noProgress: 'These entries carry no progression: the columns shown are the ones that mean '
      + 'something for them.',
    previous: 'Previous', next: 'Next',
    pageInfo: 'Page {page} / {pages} — {total} entries',
    lockedTitle: 'Never placed on the portal',
    cappedTitle: 'Field saturates at {ceiling}, the real value may be higher',
    unsupportedTitle: 'No save parser for this game', na: 'n/a',
    emptyTrap: 'empty', unnamedVillain: 'Villain #{id}',
    unnamedTitle: 'Raw id {id} — name it from the Traps screen'
  },
  traps: {
    summary: '{occupied} trap occupied out of {total}. | {occupied} traps occupied out of {total}.',
    unnamed: '{n} unnamed villain — click “Villain #…” to name it.'
      + ' | {n} unnamed villains — click “Villain #…” to name them.',
    note: 'A trap holds one villain at a time. What you see here is their current content.',
    empty: 'empty', placeholder: 'Name of villain #{id}', save: 'OK', cancel: 'Cancel',
    renameTitle: 'Raw id {id} — click to rename', unknownVillain: 'Unknown villain'
  },
  detail: {
    close: 'Close', identity: 'Identity', identityValue: 'toy {toyId} · variant {variantId}',
    games: 'Games', status: 'Status',
    unlocked: 'Unlocked', neverPlayed: 'Never placed on the portal',
    notReceived: 'No file received',
    firstPlayed: 'First played', lastSaved: 'Last save',
    lastReading: 'Latest reading', nickname: 'Nickname',
    levelNote: 'Level is not shown: the XP → level mapping has not been measured on real '
      + 'sessions yet.',
    history: 'Detailed history · {n} readings',
    files: '{n} file | {n} files',
    loadFailed: 'Could not load: {message}',
    tableSaved: 'Saved', tableBlocks: 'Blocks'
  },
  chart: {
    title: 'Progression', byDate: 'By date', byReading: 'By reading',
    byDateTitle: 'Spacing proportional to elapsed time',
    byReadingTitle: 'Every save takes the same width',
    current: 'Current: {value}', readings: '{n} readings', reading: 'reading {n}',
    none: 'No reading for this measure.',
    single: 'A single reading: two saves are needed to draw a change.'
  },
  notices: {
    unsupportedGame: 'No save parser for this game: identity only, progression and unlock '
      + 'undetermined.',
    nameNeedsReview: 'Name needs review: several labels point at this identity.',
    xpCapped: 'XP at {value}: the field read saturates at {ceiling}, the real value may be higher.',
    neverPlayed: 'File received but no trace of play: this figurine was never placed on a portal.',
    gamesWithoutParser: 'No save parser for {games}: their completion rate is undetermined, '
      + 'not zero.',
    levelNotShown: 'Level is not shown: the XP → level curve has not been measured.'
  },
  units: { hoursMinutes: '{h}h {m}', minutes: '{m} min' }
}
