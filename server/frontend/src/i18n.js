// Internationalisation, sur vue-i18n.
//
// L'API exposée ici — `t`, `format`, `locale`, `preference`, `setLocale` — est volontairement
// la même que celle de l'implémentation maison qu'elle remplace : les composants n'ont pas
// bougé. Le wrapper `t` fait une seule chose que vue-i18n ne fait pas seul : il déduit le
// nombre pour le pluriel du paramètre `n`, pour que les appels restent `t('clé', { n })`.

import { createI18n } from 'vue-i18n'
import { computed, ref } from 'vue'
import fr from './locales/fr.js'
import en from './locales/en.js'

const STORAGE_KEY = 'skylanders-locale'
const FALLBACK = 'fr'
const AVAILABLE = ['fr', 'en']

export const LOCALES = [
  { id: 'system', labelKey: 'locales.system', hintKey: 'locales.systemHint' },
  { id: 'fr', label: 'Français' },
  { id: 'en', label: 'English' }
]

function detect () {
  const tag = (navigator.language || FALLBACK).slice(0, 2).toLowerCase()
  return AVAILABLE.includes(tag) ? tag : FALLBACK
}

function stored () {
  try {
    const value = localStorage.getItem(STORAGE_KEY)
    return LOCALES.some(entry => entry.id === value) ? value : 'system'
  } catch {
    return 'system'
  }
}

/** Préférence brute, telle que choisie : « system », « fr » ou « en ». */
export const preference = ref(stored())

/** Langue réellement appliquée, « system » résolu. */
export const locale = computed(() =>
  preference.value === 'system' ? detect() : preference.value)

export const i18n = createI18n({
  legacy: false,
  globalInjection: true,
  locale: locale.value,
  fallbackLocale: FALLBACK,
  messages: { fr, en },
  // Une clé absente est renvoyée telle quelle et signalée en console : un libellé manquant
  // se voit à l'écran plutôt que de disparaître en silence.
  missingWarn: true,
  fallbackWarn: false
})

/**
 * Traduit une clé.
 *
 * Un paramètre `n` numérique sert aussi de sélecteur de pluriel, ce qui permet aux appels de
 * rester `t('clé', { n })` sans répéter le nombre.
 *
 * Les listes sont jointes avant interpolation : `String(tableau)` collerait les valeurs.
 */
export function t (key, params = {}) {
  const named = {}
  for (const [name, value] of Object.entries(params)) {
    named[name] = Array.isArray(value) ? value.join(', ') : value
  }
  return typeof params.n === 'number'
    ? i18n.global.t(key, named, params.n)
    : i18n.global.t(key, named)
}

export function setLocale (id) {
  preference.value = id
  try {
    localStorage.setItem(STORAGE_KEY, id)
  } catch {
    // Stockage refusé : la langue s'applique quand même, elle ne survivra pas au rechargement.
  }
  i18n.global.locale.value = locale.value
  applyDocumentLocale()
}

/** Ce que Vue ne rend pas : l'attribut lang et le titre de l'onglet. */
export function applyDocumentLocale () {
  i18n.global.locale.value = locale.value
  document.documentElement.lang = locale.value
  document.title = t('app.title')
}

/** Formatage des nombres, dates et durées dans la langue active. */
export const format = {
  number: value => value == null ? null : Number(value).toLocaleString(locale.value),
  date: iso => iso == null ? null
    : new Date(iso).toLocaleDateString(locale.value,
      { day: '2-digit', month: 'short', year: 'numeric' }),
  dateTime: iso => iso == null ? null
    : new Date(iso).toLocaleString(locale.value, { dateStyle: 'medium', timeStyle: 'short' }),
  duration: seconds => {
    if (seconds == null) return null
    const h = Math.floor(seconds / 3600)
    const m = Math.floor((seconds % 3600) / 60)
    return h > 0
      ? t('units.hoursMinutes', { h, m: String(m).padStart(2, '0') })
      : t('units.minutes', { m })
  }
}
