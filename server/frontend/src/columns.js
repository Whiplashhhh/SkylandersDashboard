// Jeux de colonnes du classement, selon la catégorie affichée.
//
// Un piège ne porte aucun champ de progression : XP, or, améliorations et temps de jeu y sont
// tous vides (FORMAT.md §5.3). Afficher ces colonnes sur une liste de pièges, c'est afficher
// huit tirets. La colonne utile est le vilain enfermé.

export const PROGRESS = [
  { key: 'name', labelKey: 'columns.name', align: 'left' },
  { key: 'element', labelKey: 'columns.element', align: 'left' },
  { key: 'game', labelKey: 'columns.game', align: 'left' },
  { key: 'xp', labelKey: 'columns.xp', align: 'right', kind: 'xp' },
  { key: 'gold', labelKey: 'columns.gold', align: 'right', kind: 'number', field: 'gold' },
  { key: 'upgrades', labelKey: 'columns.upgrades', align: 'right', kind: 'number', field: 'upgradesCount' },
  { key: 'playtime', labelKey: 'columns.playtime', align: 'right', kind: 'duration', field: 'playtimeSeconds' },
  { key: 'lastSaved', labelKey: 'columns.lastSaved', align: 'right', kind: 'date', field: 'lastSavedAt' }
]

export const TRAP = [
  { key: 'name', labelKey: 'columns.trapName', align: 'left' },
  { key: 'element', labelKey: 'columns.element', align: 'left' },
  { key: 'villain', labelKey: 'columns.villain', align: 'left', kind: 'villain' },
  { key: 'lastSaved', labelKey: 'columns.lastCapture', align: 'right', kind: 'date', field: 'lastSavedAt' },
  { key: 'firstPlayed', labelKey: 'columns.firstPlayed', align: 'right', kind: 'date', field: 'firstPlayedAt' }
]

// Coffres, cristaux, objets, packs aventure : aucun parseur ne les lit, et ils n'ont de toute
// façon pas de progression. On s'en tient à l'identité.
export const IDENTITY = [
  { key: 'name', labelKey: 'columns.name', align: 'left' },
  { key: 'element', labelKey: 'columns.element', align: 'left' },
  { key: 'game', labelKey: 'columns.game', align: 'left' },
  { key: 'category', labelKey: 'columns.category', align: 'left', kind: 'category' },
  { key: 'firstPlayed', labelKey: 'columns.firstUsed', align: 'right', kind: 'date', field: 'firstPlayedAt' }
]

const IDENTITY_ONLY = ['CHEST', 'CREATION_CRYSTAL', 'ITEM', 'ADVENTURE_PACK']

export function columnsFor (category) {
  if (category === 'TRAP') return TRAP
  if (IDENTITY_ONLY.includes(category)) return IDENTITY
  return PROGRESS
}

/** Colonne de tri par défaut d'un jeu de colonnes : la plus parlante, pas la première. */
export function defaultSortFor (category) {
  if (category === 'TRAP') return { sort: 'villain', direction: 'asc' }
  if (IDENTITY_ONLY.includes(category)) return { sort: 'name', direction: 'asc' }
  return { sort: 'xp', direction: 'desc' }
}
