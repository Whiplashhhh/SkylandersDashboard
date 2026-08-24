// Jeux de colonnes du classement, selon la catégorie affichée.
//
// Un piège ne porte aucun champ de progression : XP, or, améliorations et temps de jeu y sont
// tous vides (FORMAT.md §5.3). Afficher ces colonnes sur une liste de pièges, c'est afficher
// huit tirets. La colonne utile est le vilain enfermé.

export const PROGRESS = [
  { key: 'name', label: 'Nom', align: 'left' },
  { key: 'element', label: 'Élément', align: 'left' },
  { key: 'game', label: 'Jeu', align: 'left' },
  { key: 'xp', label: 'XP', align: 'right', kind: 'xp' },
  { key: 'gold', label: 'Or', align: 'right', kind: 'number', field: 'gold' },
  { key: 'upgrades', label: 'Améliorations', align: 'right', kind: 'number', field: 'upgradesCount' },
  { key: 'playtime', label: 'Temps de jeu', align: 'right', kind: 'duration', field: 'playtimeSeconds' },
  { key: 'lastSaved', label: 'Dernière partie', align: 'right', kind: 'date', field: 'lastSavedAt' }
]

export const TRAP = [
  { key: 'name', label: 'Piège', align: 'left' },
  { key: 'element', label: 'Élément', align: 'left' },
  { key: 'villain', label: 'Vilain enfermé', align: 'left', kind: 'villain' },
  { key: 'lastSaved', label: 'Dernière capture', align: 'right', kind: 'date', field: 'lastSavedAt' },
  { key: 'firstPlayed', label: 'Première pose', align: 'right', kind: 'date', field: 'firstPlayedAt' }
]

// Coffres, cristaux, objets, packs aventure : aucun parseur ne les lit, et ils n'ont de toute
// façon pas de progression. On s'en tient à l'identité.
export const IDENTITY = [
  { key: 'name', label: 'Nom', align: 'left' },
  { key: 'element', label: 'Élément', align: 'left' },
  { key: 'game', label: 'Jeu', align: 'left' },
  { key: 'category', label: 'Catégorie', align: 'left', kind: 'category' },
  { key: 'firstPlayed', label: 'Première utilisation', align: 'right', kind: 'date', field: 'firstPlayedAt' }
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
