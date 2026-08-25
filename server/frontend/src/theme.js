// Thèmes de l'interface.
//
// `system` n'est pas un thème mais une délégation : il suit la préférence du navigateur et
// bascule tout seul si elle change en cours de route. Les autres sont des choix explicites.
//
// Chaque thème définit sa propre couleur de série pour les graphes, validée contre SA surface
// (bande de clarté, plancher de chroma, contraste ≥ 3:1) — une couleur lisible sur fond sombre
// ne l'est pas forcément sur fond clair.

// Les libellés sont des clés : ils passent par i18n, comme le reste de l'interface.
export const THEMES = [
  { id: 'system', labelKey: 'themes.system', hintKey: 'themes.systemHint' },
  { id: 'light', labelKey: 'themes.light' },
  { id: 'dark', labelKey: 'themes.dark' },
  { id: 'forge', labelKey: 'themes.forge', hintKey: 'themes.forgeHint' },
  { id: 'crypte', labelKey: 'themes.crypte', hintKey: 'themes.crypteHint' },
  { id: 'arcane', labelKey: 'themes.arcane', hintKey: 'themes.arcaneHint' }
]

const STORAGE_KEY = 'skylanders-theme'
const media = window.matchMedia('(prefers-color-scheme: light)')

function resolve (id) {
  return id === 'system' ? (media.matches ? 'light' : 'dark') : id
}

export function applyTheme (id) {
  document.documentElement.dataset.theme = resolve(id)
  try {
    localStorage.setItem(STORAGE_KEY, id)
  } catch {
    // Navigation privée ou stockage refusé : le thème s'applique quand même, il ne
    // survivra simplement pas au rechargement.
  }
}

export function loadTheme () {
  let stored = null
  try {
    stored = localStorage.getItem(STORAGE_KEY)
  } catch {
    stored = null
  }
  return THEMES.some(t => t.id === stored) ? stored : 'system'
}

/** Rebascule quand la préférence du navigateur change, mais seulement en mode « système ». */
export function watchSystem (currentId) {
  media.addEventListener('change', () => {
    if (currentId() === 'system') {
      document.documentElement.dataset.theme = resolve('system')
    }
  })
}
