export default {
  bridge: {
    "toySearch": "Search for a Skylander…",
    "noResults": "No Skylanders found.",
    "chooseCopy": "Which copy would you like to use?",
    "copyHint": "This model has several saves. Click the one to place; this choice is remembered for this session.",
    "directHint": "Drop a figure onto a slot or click a search result to place it directly in the game. Use × to remove it.",
    "directSync": "Slots reflect the Cemu portal and stay in sync across your windows.",

    "live": "Emulated portal",
    "title": "Cemu live",
    "token": "Dashboard control key",
    "connect": "Connect",
    "disconnect": "Disconnect",
    "setup": "Start Cemu with the bridge and connector on the gaming PC. The control key is configured on the server.",
    "choose": "Figure to load",
    "search": "Search the PC files…",
    "select": "Select a specific file",
    "load": "Load into Cemu",
    "pending": "Waiting for Cemu…",
    "remove": "Remove",
    "clear": "Clear Cemu",
    "confirmClear": "Confirm removing all",
    "cancel": "Cancel",
    "figure": "Figure",
    "position": "Cemu slot {n}",
    "observed": "{n} figure(s) confirmed in Cemu",
    "lastObserved": "Last known state — connection unavailable",
    "prepared": "Prepared layout",
    "preparedHint": "This grid prepares your selection. To play, load files into Cemu using the controls above.",
    "status": {
        "LOCKED": "Not connected",
        "READY": "Connected to Cemu",
        "CONNECTOR_UNAVAILABLE": "Connector offline",
        "CEMU_UNAVAILABLE": "Cemu unavailable",
        "PORTAL_DISABLED": "Portal disabled",
        "SERVER_UNAVAILABLE": "Server unavailable"
    },
    "outcome": {
        "PENDING": "Command pending",
        "APPLIED": "Confirmed by Cemu",
        "REJECTED": "Command rejected",
        "EXPIRED": "Command expired",
        "UNKNOWN": "Unconfirmed result. Portal state refreshed."
    },
    "errors": {
        "WRONG_SLOT_TYPE": "Place traps in the keyhole and figures in the nine slots.",
        "CHOOSE_COPY": "Choose the copy of this figure to use.",

        "PORTAL_AUTH_REQUIRED": "Missing or incorrect control key.",
        "NETWORK_ERROR": "Connection interrupted. Check the latest state before retrying.",
        "FILE_UNAVAILABLE": "File missing or inaccessible on the PC.",
        "PATH_FORBIDDEN": "File is outside the allowed folder.",
        "INVALID_DUMP": "Invalid dump (size or header).",
        "ALREADY_LOADED": "This file is already loaded.",
        "PORTAL_FULL": "The portal is full.",
        "STALE_STATE": "The portal changed. State refreshed; try again.",
        "STALE_SESSION": "Cemu restarted. Try again using its new state.",
        "SESSION_CHANGED": "The connector restarted.",
        "EXPIRED": "The deadline expired; old commands will not be replayed.",
        "CEMU_UNAVAILABLE": "Cemu is unavailable.",
        "PORTAL_DISABLED": "Enable the emulated portal or restart Cemu with the bridge.",
        "INVALID_SLOT": "Invalid slot.",
        "UNKNOWN_COMMAND": "Unknown command.",
        "COMMAND_IN_PROGRESS": "A command is already awaiting confirmation.",
        "INCOMPATIBLE_VERSION": "Incompatible bridge versions.",
        "INVALID_COMMAND_ID": "Invalid command ID.",
        "INVALID_REQUEST": "Invalid request."
    }
},
  collectionSort: { label: 'Sort collection', direction: 'Sort direction', recent: 'Recently played', element: 'Type (element)', name: 'Alphabetical', category: 'Category', game: 'Game', asc: 'Ascending', desc: 'Descending' },
  dashboard: {
    noCaptured: 'No villains match this selection.',
    "companion": "The Portal Master’s hideout",
    "skip": "Skip to collection",
    "updated": "Data refreshed",
    "unavailable": "Refresh unavailable",
    "refresh": "Refresh data",
    "retry": "Try again",
    "navigation": "Collection views",
    "overview": "Collection overview",
    "eyebrow": "Skylands journal",
    "welcome": "One collection.",
    "welcomeAccent": "Endless adventures.",
    "intro": "Rediscover your heroes, their achievements, and the adventures still to come.",
    "lastPlayed": "Latest save",
    "unlocked": "Unlocked figures",
    "confirmed": "Confirmed progress",
    "received": "Received figures",
    "inLibrary": "Present in your library",
    "elements": "Elements played",
    "elementsHint": "With at least one unlocked figure",
    "games": "Games represented",
    "gamesHint": "Across your collection",
    "coverage": "Unlocks can only be confirmed for games with a supported save parser.",
    "title_grid": "Your collection",
    "subtitle_grid": "Every hero has a story. Explore yours.",
    "title_table": "The leaderboard",
    "subtitle_table": "Your heroes, ranked by their achievements.",
    "title_traps": "The trap chamber",
    "subtitle_traps": "Keep an eye on your portal’s prisoners.",
    "title_villains": "Villains of the Skylands",
    "subtitle_villains": "Discover the villains currently held in your traps.",
    "unlockedFirst": "Unlocked figures first",
    "empty": "Your adventure starts here",
    "emptyHint": "Start the local agent to receive your figure collection.",
    "emptyFiltered": "No heroes on the horizon",
    "notPlayed": "Not unlocked",
    "openToy": "View {name}",
    "localPortal": "Local layout",
    "portalHint": "This panel prepares your selection. It does not control Cemu’s portal yet."
},
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
  views: { table: 'Ranking', grid: 'Collection', traps: 'Traps', villains: 'Villains' },
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
  portal: {
    title: 'Portal',
    show: 'Show portal', hide: 'Hide portal', close: 'Close the portal',
    reattach: 'Reattach portal',
    windowTitle: 'Portal — Skylanders Collection',
    detach: 'Detach', detachTitle: 'Open the portal in a separate window',
    clearAll: 'Clear all', confirmClear: 'Confirm?',
    count: '{n} placed of {total} slots | {n} placed of {total} slots',
    emptySlot: 'free', trapSlot: 'trap',
    place: 'Put on the portal', alreadyPlaced: 'Already on the portal',
    remove: 'Take off the portal',
    placedAt: 'Placed on {date}',
    full: 'Portal full: take one off before putting another on.',
    beyond: 'off grid',
    beyondTitle: 'Placed past the configured grid. Take it off to free the slot, which will '
      + 'then disappear.',
    note: 'Drag a figurine onto a slot, or use the + button in the lists — drag and drop does '
      + 'not exist on touch screens, the button works everywhere.',
    syncNote: 'The layout is stored on the server. Windows of this browser sync live; other '
      + 'devices will read it again on their next load.'
  },
  villains: {
    doomRaider: 'Doom Raider',
    captured: 'Locked in a trap', notCaptured: 'Never captured',
    rawId: 'Tag identifier', capturedAt: 'Read on',
    summary: '{n} villain captured of {total}. | {n} villains captured of {total}.',
    note: 'A villain counts as captured while a trap holds it. The tags carry no tally of past '
      + 'captures: that lives in the console save.',
    onlyCaptured: 'Only show captured villains',
    openStory: 'Open {name}’s page',
    noStory: 'No summary for this villain.',
    source: 'Summary from the Skylanders wiki (Fandom), CC BY-SA.'
  },
  detail: {
    wiki: 'Wiki page', wikiTitle: 'Open the Skylanders wiki page (new tab)',
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
    trapGoesInTrapSlot: 'A trap goes in the trap keyhole, below the grid: the portal has '
      + 'only one.',
    onlyTrapsInTrapSlot: 'The trap keyhole takes traps only.',
    gamesWithoutParser: 'No save parser for {games}: their completion rate is undetermined, '
      + 'not zero.',
    levelNotShown: 'Level is not shown: the XP → level curve has not been measured.'
  },
  units: { hoursMinutes: '{h}h {m}', minutes: '{m} min' }
}
