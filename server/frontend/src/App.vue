<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { api } from './api.js'
import CollectionOverview from './components/CollectionOverview.vue'
import GameSelector from './components/GameSelector.vue'
import ElementRail from './components/ElementRail.vue'
import LeaderboardTable from './components/LeaderboardTable.vue'
import ToyCard from './components/ToyCard.vue'
import ToyDetail from './components/ToyDetail.vue'
import ElementEffect from './components/ElementEffect.vue'
import TrapsView from './components/TrapsView.vue'
import VillainsView from './components/VillainsView.vue'
import VillainDetail from './components/VillainDetail.vue'
import { columnsFor, defaultSortFor } from './columns.js'
import SettingsMenu from './components/SettingsMenu.vue'
import PortalPanel from './components/PortalPanel.vue'
import { applyTheme, loadTheme, watchSystem } from './theme.js'
import { preference as localePreference, locale, setLocale, t } from './i18n.js'
import { sortCollection, rankingExcluded } from './collectionSort.js'
import { load as loadPortal, place as placeOnPortal, portal } from './portal.js'

// Ordre canonique des éléments : celui des jeux, pas l'ordre alphabétique.
const ELEMENT_ORDER = ['Feu', 'Eau', 'Vie', 'Magie', 'Tech', 'Terre', 'Air',
  'Mort-Vivant', 'Lumière', 'Ténèbres', 'Kaos', 'Inconnu']

// Ordre du menu, des personnages vers les accessoires. Les libellés passent par i18n.
const CATEGORY_ORDER = ['CHARACTER', 'GIANT', 'TRAP', 'VEHICLE', 'SIDEKICK', 'MINI',
  'ITEM', 'ADVENTURE_PACK', 'CHEST', 'CREATION_CRYSTAL', 'UNKNOWN']

const navIcons = {
  grid: 'M3 3h6v6H3z M15 3h6v6h-6z M3 15h6v6H3z M15 15h6v6h-6z',
  table: 'M4 20V12h4v8 M10 20V4h4v16 M16 20V8h4v12',
  traps: 'M12 2 21 12 12 22 3 12Z M8 12h8 M12 8v8',
  villains: 'M4 4 12 7 20 4v8c0 5-8 9-8 9s-8-4-8-9Z M8 11l2 2 M16 11l-2 2'
}
const view = ref('grid')
const game = ref(null)
const element = ref(null)
const search = ref('')
const state = ref(null)
const category = ref(null)
const sort = ref('xp')
const direction = ref('desc')
const page = ref(1)
const size = ref(15)

const collectionView = computed(() => ['table', 'grid'].includes(view.value))
const collectionSort = ref('recent')
const collectionDirection = ref('desc')
const displayedCards = computed(() => sortCollection(cards.value, collectionSort.value, collectionDirection.value, locale.value, t))
watch(collectionSort, key => { collectionDirection.value = key === 'recent' ? 'desc' : 'asc' })
const hasFilters = computed(() => game.value || element.value || category.value || search.value || state.value)
const columns = computed(() => columnsFor(category.value))
const board = ref(null)
const cards = ref([])
const trapContents = ref(new Map())
const trapFor = toy => toy ? trapContents.value.get(`${toy.toyId}/${toy.variantId}`) : null
const roster = ref([])
const stats = ref(null)
const selected = ref(null)
const selectedVillain = ref(null)
const loading = ref(true)
const error = ref(null)
const overviewError = ref(null)
const refreshing = ref(false)
const refreshedAt = ref(null)
const refreshKey = ref(0)
const theme = ref(loadTheme())
// Trois positions plutôt qu'un booléen : « détaché » est un état à part entière, où le
// portail est utilisé mais affiché dans une autre fenêtre. Les boutons « poser » des listes
// suivent l'usage du portail, pas la présence du panneau ici.
//   'off'      pas de portail, pas de boutons
//   'panel'    panneau à droite, boutons visibles
//   'detached' panneau dans sa propre fenêtre, boutons toujours visibles
const portalMode = ref('off')
const portalActive = computed(() => portalMode.value !== 'off')
const portalPanelHere = computed(() => portalMode.value === 'panel')

const games = computed(() => {
  const present = new Set(roster.value.flatMap(t => t.games))
  return ['SPYROS_ADVENTURE', 'GIANTS', 'SWAP_FORCE', 'TRAP_TEAM', 'SUPERCHARGERS', 'IMAGINATORS']
    .filter(g => present.has(g))
})
const elements = computed(() => {
  const scoped = roster.value.filter(t => !game.value || t.games.includes(game.value))
  const present = new Set(scoped.map(t => t.element))
  return ELEMENT_ORDER.filter(e => present.has(e))
})

// Les catégories dépendent du jeu : Giants n'a pas de pièges, seul SuperChargers a des
// véhicules. Proposer une catégorie absente donnerait un tableau vide sans raison visible.
const categories = computed(() => {
  const scoped = roster.value.filter(t => !game.value || t.games.includes(game.value))
  const present = new Set(scoped.map(t => t.category))
  return CATEGORY_ORDER.filter(c => present.has(c) && (view.value !== 'table' || !rankingExcluded.has(c)))
})

const filters = computed(() => ({
  game: game.value, element: element.value, category: category.value,
  search: search.value, state: state.value
}))

// Changer de jeu peut rendre le filtre courant sans objet : on le relâche plutôt que de
// laisser l'utilisateur devant une liste vide.
// Changer de catégorie change le jeu de colonnes : si le tri courant porte sur une colonne qui
// disparaît, on retombe sur le tri le plus parlant du nouveau jeu plutôt que sur rien.
watch(category, () => {
  if (!columns.value.some(c => c.key === sort.value)) {
    const fallback = defaultSortFor(category.value)
    sort.value = fallback.sort
    direction.value = fallback.direction
  }
})

watch([categories, elements], () => {
  if (category.value && !categories.value.includes(category.value)) category.value = null
  if (element.value && !elements.value.includes(element.value)) element.value = null
})

let debounce
watch([game, element, category, search, state, sort, direction, size, view], () => {
  page.value = 1
  clearTimeout(debounce)
  debounce = setTimeout(load, 160)
})
watch(page, () => load())

let loadId = 0
async function load (quiet = false) {
  const id = ++loadId
  if (!quiet) loading.value = true
  error.value = null
  try {
    if (view.value === 'traps' || view.value === 'villains') {
      // Ces deux écrans chargent leurs propres données.
    } else if (view.value === 'table') {
      const result = await api.leaderboard({
        ...filters.value, sort: sort.value, direction: direction.value,
        page: page.value, size: size.value
      })
      if (id === loadId) board.value = result
    } else {
      const [result, traps] = await Promise.all([api.toys(filters.value), api.traps()])
      if (id === loadId) {
        cards.value = result
        trapContents.value = new Map(traps.map(trap => [`${trap.toyId}/${trap.variantId}`, trap]))
      }
    }
  } catch (e) {
    if (id === loadId) error.value = e.message
  } finally {
    if (id === loadId) loading.value = false
  }
}

function onSort (key) {
  if (sort.value === key) {
    direction.value = direction.value === 'desc' ? 'asc' : 'desc'
  } else {
    sort.value = key
    // Un nom se lit de A à Z, un score du plus haut au plus bas.
    direction.value = ['name', 'element', 'game', 'category'].includes(key) ? 'asc' : 'desc'
  }
}

// Le portail ne se charge qu'a l'ouverture du panneau : tant qu'il est fermé, la disposition
// n'a rien à dire à personne et une requête de plus au démarrage se paierait sur mobile.
// Depuis « détaché », la bascule ramène le panneau ici plutôt que de tout éteindre : c'est le
// geste inverse de « Détacher », et fermer se fait avec la croix du panneau.
function togglePortal () {
  portalMode.value = portalMode.value === 'panel' ? 'off' : 'panel'
  if (portalActive.value && !portal.loaded) loadPortal()
}

const portalToggleLabel = computed(() => portalMode.value === 'panel'
  ? t('portal.hide')
  : (portalMode.value === 'detached' ? t('portal.reattach') : t('portal.show')))

function reset () {
  game.value = null; element.value = null; category.value = null
  search.value = ''; state.value = null
  sort.value = 'xp'; direction.value = 'desc'
  collectionSort.value = 'recent'; collectionDirection.value = 'desc'
}

watch(theme, applyTheme)

async function refresh () {
  if (refreshing.value) return
  refreshing.value = true
  overviewError.value = null
  try {
    const [all, s] = await Promise.all([api.toys({}), api.stats()])
    roster.value = all
    stats.value = s
    refreshedAt.value = new Date()
    refreshKey.value++
  } catch (e) {
    overviewError.value = e.message
  } finally {
    await load(true)
    refreshing.value = false
  }
}

let refreshTimer
let stopWatchingTheme
function onEscape (event) {
  if (event.key === 'Escape') { selected.value = null; selectedVillain.value = null }
}
onMounted(async () => {
  applyTheme(theme.value)
  stopWatchingTheme = watchSystem(() => theme.value)
  document.addEventListener('keydown', onEscape)
  await refresh()
  // Keep the collection current while the local agent sends new snapshots.
  refreshTimer = setInterval(() => {
    if (!document.hidden) refresh()
  }, 15000)
})
onUnmounted(() => {
  clearInterval(refreshTimer)
  clearTimeout(debounce)
  stopWatchingTheme?.()
  document.removeEventListener('keydown', onEscape)
})
</script>

<template>
  <div class="app">
  <a class="skip-link" href="#collection">{{ t('dashboard.skip') }}</a>
  <header class="top">
    <div class="brand">
      <span class="portal-emblem" aria-hidden="true"><span></span></span>
      <div><span class="brand-name">SKYLANDERS<span class="brand-dot">.</span></span>
        <span class="brand-caption">{{ t('dashboard.companion') }}</span></div>
    </div>
    <div class="header-actions">
      <span class="refresh-status" :class="{ failed: overviewError || error }">
        <span class="status-dot" aria-hidden="true"></span>
        {{ overviewError || error ? t('dashboard.unavailable') : refreshedAt ? t('dashboard.updated') : t('common.loading') }}
      </span>
      <button class="ghost refresh-button" :disabled="refreshing" @click="refresh"
              :aria-label="t('dashboard.refresh')" :title="t('dashboard.refresh')">↻</button>
      <SettingsMenu :theme="theme" :locale="localePreference"
        @update:theme="theme = $event" @update:locale="setLocale" />
    </div>
  </header>

  <div class="body">
    <ElementRail v-if="collectionView" v-model="element" :elements="elements" />
    <main id="collection" tabindex="-1">
      <CollectionOverview v-if="view === 'grid'" :roster="roster" :stats="stats" @open="selected = $event" />
      <p v-if="overviewError" class="overview-error" role="alert">
        {{ t('common.error', { message: overviewError }) }}
        <button class="ghost" @click="refresh">{{ t('dashboard.retry') }}</button>
      </p>
      <div class="section-heading">
        <div><h1>{{ t(`dashboard.title_${view}`) }}</h1><p>{{ t(`dashboard.subtitle_${view}`) }}</p></div>
        <button class="ghost portal-toggle" :class="{ on: portalActive }"
                :aria-pressed="portalActive" @click="togglePortal">◎ {{ portalToggleLabel }}</button>
      </div>
      <nav class="view-nav" :aria-label="t('dashboard.navigation')">
        <button v-for="tab in ['grid', 'table', 'traps', 'villains']" :key="tab"
                :class="{ on: view === tab }" :aria-pressed="view === tab" @click="view = tab">
          <svg class="nav-symbol" aria-hidden="true" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linejoin="round"><path :d="navIcons[tab]" /></svg>
          {{ t(`views.${tab}`) }}
        </button>
      </nav>
      <GameSelector v-if="collectionView" v-model="game" :games="games" />
      <div v-if="collectionView" class="toolbar">
        <input v-if="view === 'table' || view === 'grid'" v-model="search" type="search" class="search"
               :placeholder="t('filters.search')" :aria-label="t('filters.search')" />
        <select v-if="view === 'table' || view === 'grid'" v-model="category" :aria-label="t('filters.allCategories')">
          <option :value="null">{{ t('filters.allCategories') }}</option>
          <option v-for="c in categories" :key="c" :value="c">{{ t(`categories.${c}`) }}</option>
        </select>
        <select v-if="view === 'table' || view === 'grid'" v-model="state" :aria-label="t('filters.allStates')">
          <option :value="null">{{ t('filters.allStates') }}</option>
          <option value="unlocked">{{ t('filters.unlocked') }}</option>
          <option value="locked">{{ t('filters.locked') }}</option>
        </select>
        <select v-model.number="size" v-if="view === 'table'" :title="t('filters.perPage', { n: size })">
          <option v-for="n in [15, 30, 50, 100, 500]" :key="n" :value="n">
            {{ t('filters.perPage', { n }) }}
          </option>
        </select>
        <select v-if="view === 'grid'" v-model="collectionSort" :aria-label="t('collectionSort.label')">
          <option v-for="key in ['recent', 'element', 'name', 'category', 'game']" :key="key" :value="key">{{ t(`collectionSort.${key}`) }}</option>
        </select>
        <select v-if="view === 'grid'" v-model="collectionDirection" :aria-label="t('collectionSort.direction')">
          <option value="asc">{{ t('collectionSort.asc') }}</option>
          <option value="desc">{{ t('collectionSort.desc') }}</option>
        </select>
        <button v-if="view === 'table' || view === 'grid'" class="ghost"
                @click="reset">{{ t('filters.reset') }}</button>
      </div>

      <p v-if="error" class="state err" role="alert">{{ t('common.error', { message: error }) }}</p>
      <p v-else-if="loading" class="state" role="status">{{ t('common.loading') }}</p>

      <LeaderboardTable
        v-else-if="view === 'table'"
        :page="board" :columns="columns" :placeable="portalActive"
        @sort="onSort" @page="page = $event"
        @open="selected = $event" @place="placeOnPortal($event)"
      />

      <TrapsView v-else-if="view === 'traps'" :refresh-key="refreshKey" :placeable="portalActive"
                 @open="selectedVillain = $event" />

      <VillainsView v-else-if="view === 'villains'" :refresh-key="refreshKey" @open="selectedVillain = $event" />

      <template v-else-if="view === 'grid'">
        <p class="count">{{ t('common.figurines', { n: cards.length }) }}<span>{{ t(`collectionSort.${collectionSort}`) }} · {{ t(`collectionSort.${collectionDirection}`) }}</span></p>
        <div v-if="!cards.length" class="empty-collection">
          <span class="empty-symbol" aria-hidden="true">◇</span>
          <h2>{{ t(hasFilters ? 'dashboard.emptyFiltered' : 'dashboard.empty') }}</h2>
          <p>{{ t(hasFilters ? 'table.noResults' : 'dashboard.emptyHint') }}</p>
          <button v-if="hasFilters" class="ghost" @click="reset">{{ t('filters.reset') }}</button>
        </div>
        <div class="grid">
          <ToyCard v-for="t in displayedCards" :key="`${t.toyId}/${t.variantId}`" :toy="t"
                   :trap="trapFor(t)"
                   :placeable="portalActive"
                   @open="selected = $event" @place="placeOnPortal($event)" />
        </div>
      </template>
    </main>

    <!-- Colonne de droite plutôt qu'un calque : la grille et le classement restent visibles,
         c'est de là qu'on glisse. -->
    <PortalPanel v-if="portalPanelHere" @close="portalMode = 'off'"
                 @detach="portalMode = 'detached'"
                 @openVillain="selectedVillain = $event" />
  </div>

  <div v-if="selected || selectedVillain" class="scrim"
       @click="selected = null; selectedVillain = null"></div>
  <ElementEffect :subject="selectedVillain || selected" />
  <ToyDetail :toy="selected" :trap="trapFor(selected)" :placeable="portalActive" @close="selected = null"
             @place="placeOnPortal($event)" />
  <VillainDetail :villain="selectedVillain" @close="selectedVillain = null" />
  </div>
</template>

<style scoped>
.app { display: flex; flex-direction: column; height: 100dvh; }
.top { display: flex; justify-content: space-between; align-items: center; gap: 20px; padding: 18px 32px; border-bottom: 1px solid var(--line); background: var(--panel); }
.brand, .header-actions { display: flex; align-items: center; gap: 16px; }
.brand-name { display: block; font-size: 20px; font-weight: 850; letter-spacing: .09em; line-height: 1.2; }
.brand-dot { color: var(--accent); }
.brand-caption { display: block; color: var(--muted); font-size: 10px; letter-spacing: .22em; text-transform: uppercase; margin-top: 4px; }
.portal-emblem { display: grid; place-items: center; width: 40px; height: 40px; border: 1px solid var(--accent); border-radius: 50%; box-shadow: inset 0 0 12px color-mix(in srgb, var(--accent) 18%, transparent); }
.portal-emblem span { width: 21px; height: 21px; border: 3px double var(--accent); transform: rotate(45deg); }
.refresh-status { display: flex; align-items: center; gap: 8px; color: var(--muted); font-size: 12px; }
.status-dot { width: 6px; height: 6px; border-radius: 50%; background: var(--accent); }
.failed .status-dot { background: var(--err); }
.header-actions .refresh-button { font-size: 23px; padding: 2px 10px; }
.body { display: flex; align-items: stretch; flex: 1; min-height: 0; }
main { flex: 1; min-width: 0; overflow-y: auto; padding: 28px 32px 40px; }
.section-heading { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin: 30px 0 20px; }
h1 { margin: 0; font-size: clamp(24px, 2.4vw, 32px); letter-spacing: -.04em; font-weight: 750; }
.section-heading p { margin: 6px 0 0; font-size: 13px; color: var(--muted); }
.view-nav { display: flex; gap: 28px; border-bottom: 1px solid var(--line); margin-bottom: 18px; overflow-x: auto; }
.view-nav button { display: flex; align-items: center; gap: 9px; padding: 0 2px 14px; background: none; border: 0; border-bottom: 2px solid transparent; white-space: nowrap; cursor: pointer; color: var(--muted); font-size: 13px; }
.view-nav button.on { color: var(--accent); border-bottom-color: var(--accent); font-weight: 650; }
.nav-symbol { width: 17px; height: 17px; flex-shrink: 0; }
.toolbar { display: flex; flex-wrap: wrap; gap: 10px; align-items: center; padding: 18px 0; }
.search { flex: 1 1 200px; min-width: 150px; }
input, select { background: var(--panel); border: 1px solid var(--line); border-radius: 8px; padding: 10px 12px; font-size: 12px; min-height: 40px; }
.ghost { background: var(--panel); border: 1px solid var(--line); color: var(--muted); border-radius: 8px; padding: 10px 14px; cursor: pointer; font-size: 12px; }
.ghost:hover { color: var(--text); border-color: var(--accent); }
.ghost:disabled { opacity: .5; cursor: wait; }
.portal-toggle { color: var(--text); white-space: nowrap; }
.portal-toggle.on { color: var(--on-accent); background: var(--accent); border-color: var(--accent); }
.count { display: flex; justify-content: space-between; gap: 12px; color: var(--muted); font-size: 11px; margin: 0 0 16px; }
.grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(170px, 1fr)); gap: 16px; }
.state, .overview-error { color: var(--muted); padding: 26px 14px; }
.err, .overview-error { color: var(--err); }
.empty-collection { text-align: center; padding: 48px 20px; border: 1px dashed var(--line); border-radius: 12px; }
.empty-symbol { font-size: 42px; color: var(--accent); }
.empty-collection h2 { font-size: 20px; }
.empty-collection p { font-size: 13px; color: var(--muted); }
.scrim { position: fixed; inset: 0; background: #03090dc9; backdrop-filter: blur(5px); z-index: 15; }
@media (min-width: 1700px) { main { padding-inline: max(32px, calc((100vw - 1550px) / 2)); } }
@media (max-width: 900px) { main { padding: 22px 20px 30px; } .top { padding: 16px 20px; } .refresh-status { display: none; } }
@media (max-width: 600px) {
  .app { height: auto; min-height: 100dvh; } .body { flex-direction: column; } main { overflow: visible; padding: 20px 16px; }
  .top { padding: 14px 16px; } .brand-name { font-size: 16px; } .brand-caption { font-size: 8px; } .brand, .header-actions { gap: 10px; }
  .portal-emblem { width: 32px; height: 32px; } .section-heading { align-items: flex-start; } .section-heading p { max-width: 220px; line-height: 1.5; }
  .view-nav { gap: 20px; } .view-nav button { font-size: 12px; } .grid { grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
  .toolbar select { flex: 1; min-width: 0; } .search { flex-basis: 100%; } .count span { font-size: 10px; } .portal-toggle { max-width: 130px; white-space: normal; }
}
</style>
