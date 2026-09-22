export const rankingExcluded = new Set(['TRAP', 'VEHICLE', 'ITEM', 'ADVENTURE_PACK', 'CHEST'])
const games = ['SPYROS_ADVENTURE', 'GIANTS', 'SWAP_FORCE', 'TRAP_TEAM', 'SUPERCHARGERS', 'IMAGINATORS']

export function sortCollection(cards, key, direction, locale, translate) {
  const collator = new Intl.Collator(locale, { sensitivity: 'base', numeric: true })
  const name = toy => (locale === 'en' ? toy.nameEn : toy.nameFr) || toy.nameEn || ''
  const value = toy => {
    if (key === 'recent') {
      const date = toy.unlocked && toy.lastSavedAt ? Date.parse(toy.lastSavedAt) : NaN
      return Number.isFinite(date) ? date : null
    }
    if (key === 'name') return name(toy)
    if (key === 'element') return toy.element ? translate(`elements.${toy.element}`) : null
    if (key === 'category') return toy.category ? translate(`categoriesOne.${toy.category}`) : null
    const index = games.indexOf(toy.originGame)
    return index < 0 ? null : index
  }
  return [...cards].sort((a, b) => {
    const av = value(a), bv = value(b)
    if (av == null && bv != null) return 1
    if (bv == null && av != null) return -1
    const order = av == null ? 0 : typeof av === 'number' ? av - bv : collator.compare(av, bv)
    return order * (direction === 'asc' ? 1 : -1) || collator.compare(name(a), name(b)) || a.toyId - b.toyId || a.variantId - b.variantId
  })
}
