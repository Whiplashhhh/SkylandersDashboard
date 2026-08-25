// Internationalisation, sans dépendance.
//
// vue-i18n ferait très bien l'affaire, mais le CLAUDE.md demande de consulter avant d'ajouter
// une dépendance, et deux langues avec interpolation et un pluriel tiennent en quelques lignes.
// Si le besoin grandit — genres, ordinaux, dates localisées complexes — c'est le moment de
// basculer sur la bibliothèque plutôt que d'étoffer ceci.

import { computed, ref } from 'vue'
import fr from './locales/fr.js'
import en from './locales/en.js'

const MESSAGES = { fr, en }
const STORAGE_KEY = 'skylanders-locale'
const FALLBACK = 'fr'

export const LOCALES = [
  { id: 'system', labelKey: 'locales.system', hintKey: 'locales.systemHint' },
  { id: 'fr', label: 'Français' },
  { id: 'en', label: 'English' }
]

function detect () {
  const tag = (navigator.language || FALLBACK).slice(0, 2).toLowerCase()
  return MESSAGES[tag] ? tag : FALLBACK
}

function stored () {
  try {
    const value = localStorage.getItem(STORAGE_KEY)
    return LOCALES.some(l => l.id === value) ? value : 'system'
  } catch {
    return 'system'
  }
}

/** Préférence brute, telle que choisie : « system », « fr » ou « en ». */
export const preference = ref(stored())

/** Langue réellement appliquée, « system » résolu. */
export const locale = computed(() => preference.value === 'system' ? detect() : preference.value)

export function setLocale (id) {
  preference.value = id
  try {
    localStorage.setItem(STORAGE_KEY, id)
  } catch {
    // Stockage refusé : la langue s'applique quand même, elle ne survivra pas au rechargement.
  }
  applyDocumentLocale()
}

/** Ce que Vue ne rend pas : l'attribut lang et le titre de l'onglet. */
export function applyDocumentLocale () {
  document.documentElement.lang = locale.value
  document.title = t('app.title')
}

function lookup (dictionary, path) {
  return path.split('.').reduce((node, key) => (node == null ? undefined : node[key]), dictionary)
}

/**
 * Traduit une clé.
 *
 * Interpolation : `{nom}`. Pluriel : « singulier | pluriel », choisi sur `n`, ce qui couvre
 * exactement le français et l'anglais — une règle de plus large serait du code mort.
 *
 * Une clé absente est renvoyée telle quelle : un libellé manquant se voit à l'écran plutôt que
 * de disparaître silencieusement.
 */
export function t (key, params = {}) {
  let text = lookup(MESSAGES[locale.value], key)
  if (typeof text !== 'string') {
    text = lookup(MESSAGES[FALLBACK], key)
  }
  if (typeof text !== 'string') {
    return key
  }
  if (text.includes('|')) {
    const forms = text.split('|').map(form => form.trim())
    text = Math.abs(Number(params.n)) <= 1 ? forms[0] : forms[1]
  }
  return text.replace(/\{(\w+)\}/g, (whole, name) => {
    const value = params[name]
    if (value === undefined) return whole
    // Une liste s'écrit avec des séparateurs lisibles, pas avec la virgule brute de String().
    return Array.isArray(value) ? value.join(', ') : String(value)
  })
}

/** Formatage des nombres et dates dans la langue active. */
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
