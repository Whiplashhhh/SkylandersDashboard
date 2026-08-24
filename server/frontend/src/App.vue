<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { api } from './api.js'
import GameSelector from './components/GameSelector.vue'
import ElementRail from './components/ElementRail.vue'
import LeaderboardTable from './components/LeaderboardTable.vue'
import ToyCard from './components/ToyCard.vue'
import ToyDetail from './components/ToyDetail.vue'
import TrapsView from './components/TrapsView.vue'
import { columnsFor, defaultSortFor } from './columns.js'

// Ordre canonique des éléments : celui des jeux, pas l'ordre alphabétique.
const ELEMENT_ORDER = ['Feu', 'Eau', 'Vie', 'Magie', 'Tech', 'Terre', 'Air',
  'Mort-Vivant', 'Lumière', 'Ténèbres', 'Kaos', 'Inconnu']

// Libellés métier en français (CLAUDE.md, « Conventions »). L'ordre est celui du menu,
// des personnages vers les accessoires.
const CATEGORY_LABELS = {
  CHARACTER: 'Personnages', GIANT: 'Géants', TRAP: 'Pièges', VEHICLE: 'Véhicules',
  SIDEKICK: 'Acolytes', MINI: 'Minis', ITEM: 'Objets', ADVENTURE_PACK: 'Packs Aventure',
  CHEST: 'Coffres', CREATION_CRYSTAL: 'Cristaux de Création', UNKNOWN: 'Autres'
}
const CATEGORY_ORDER = Object.keys(CATEGORY_LABELS)

const view = ref('table')
const game = ref(null)
const element = ref(null)
const search = ref('')
const state = ref(null)
const category = ref(null)
const sort = ref('xp')
const direction = ref('desc')
const page = ref(1)
const size = ref(20)

const columns = computed(() => columnsFor(category.value))
const board = ref(null)
const cards = ref([])
const roster = ref([])
const stats = ref(null)
const selected = ref(null)
const loading = ref(true)
const error = ref(null)

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
  return CATEGORY_ORDER.filter(c => present.has(c))
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
watch(page, load)

async function load () {
  loading.value = true
  error.value = null
  try {
    if (view.value === 'table') {
      board.value = await api.leaderboard({
        ...filters.value, sort: sort.value, direction: direction.value,
        page: page.value, size: size.value
      })
    } else {
      cards.value = await api.toys(filters.value)
    }
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
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

function reset () {
  game.value = null; element.value = null; category.value = null
  search.value = ''; state.value = null
  sort.value = 'xp'; direction.value = 'desc'
}

onMounted(async () => {
  try {
    const [all, s] = await Promise.all([api.toys({}), api.stats()])
    roster.value = all
    stats.value = s
  } catch (e) {
    error.value = e.message
  }
  await load()
})
</script>

<template>
  <header class="top">
    <h1>Collection Skylanders</h1>
    <div v-if="stats" class="counters">
      <strong>{{ stats.totals.unlocked }}</strong> débloquées / {{ stats.totals.rosterSize }} au roster
    </div>
  </header>

  <GameSelector v-model="game" :games="games" />

  <div class="body">
    <ElementRail v-model="element" :elements="elements" />

    <main>
      <div class="toolbar">
        <input v-if="view !== 'traps'" v-model="search" type="search" class="search" placeholder="Rechercher un nom…" />
        <select v-if="view !== 'traps'" v-model="category">
          <option :value="null">Toutes les catégories</option>
          <option v-for="c in categories" :key="c" :value="c">{{ CATEGORY_LABELS[c] || c }}</option>
        </select>
        <select v-if="view !== 'traps'" v-model="state">
          <option :value="null">Toutes</option>
          <option value="unlocked">Débloquées</option>
          <option value="locked">Non débloquées</option>
        </select>
        <select v-model.number="size" v-if="view === 'table'" title="Lignes par page">
          <option :value="20">20 par page</option>
          <option :value="50">50 par page</option>
          <option :value="100">100 par page</option>
          <option :value="500">500 par page</option>
        </select>
        <button v-if="view !== 'traps'" class="ghost" @click="reset">Réinitialiser</button>
        <div class="spacer"></div>
        <div class="toggle">
          <button :class="{ on: view === 'table' }" @click="view = 'table'">Classement</button>
          <button :class="{ on: view === 'grid' }" @click="view = 'grid'">Grille</button>
          <button :class="{ on: view === 'traps' }" @click="view = 'traps'">Pièges</button>
        </div>
      </div>

      <p v-if="error" class="state err">Erreur : {{ error }}</p>
      <p v-else-if="loading" class="state">Chargement…</p>

      <LeaderboardTable
        v-else-if="view === 'table'"
        :page="board" :columns="columns" @sort="onSort" @page="page = $event"
        @open="selected = $event"
      />

      <TrapsView v-else-if="view === 'traps'" />

      <template v-else-if="view === 'grid'">
        <p class="count">{{ cards.length }} figurine(s)</p>
        <div class="grid">
          <ToyCard v-for="t in cards" :key="`${t.toyId}/${t.variantId}`" :toy="t"
                   @open="selected = $event" />
        </div>
      </template>
    </main>
  </div>

  <div v-if="selected" class="scrim" @click="selected = null"></div>
  <ToyDetail :toy="selected" @close="selected = null" />
</template>

<style scoped>
.top {
  display: flex; align-items: baseline; justify-content: space-between;
  gap: 16px; padding: 14px 16px 10px;
}
h1 { margin: 0; font-size: 20px; }
.counters { color: var(--muted); font-size: 14px; }
.counters strong { color: var(--text); font-size: 17px; }

.body { display: flex; align-items: stretch; min-height: calc(100vh - 150px); }
main { flex: 1; min-width: 0; display: flex; flex-direction: column; }

.toolbar {
  display: flex; flex-wrap: wrap; gap: 8px; align-items: center;
  padding: 10px 14px; border-bottom: 1px solid var(--line);
}
.search { flex: 1 1 200px; min-width: 160px; }
input, select {
  background: var(--panel-2); border: 1px solid var(--line);
  border-radius: 8px; padding: 6px 10px;
}
.ghost {
  background: transparent; border: 1px solid var(--line); color: var(--muted);
  border-radius: 8px; padding: 6px 12px; cursor: pointer;
}
.ghost:hover { color: var(--text); border-color: var(--accent); }
.spacer { flex: 1; }
.toggle { display: flex; border: 1px solid var(--line); border-radius: 8px; overflow: hidden; }
.toggle button {
  background: var(--panel-2); border: none; padding: 6px 14px; cursor: pointer; color: var(--muted);
}
.toggle button.on { background: var(--accent); color: #10131a; font-weight: 600; }

.count { color: var(--muted); font-size: 12.5px; margin: 12px 14px 8px; }
.grid {
  display: grid; grid-template-columns: repeat(auto-fill, minmax(132px, 1fr));
  gap: 12px; padding: 0 14px 16px;
}
.state { color: var(--muted); padding: 26px 14px; }
.err { color: #e2726e; }
.scrim { position: fixed; inset: 0; background: #0009; z-index: 15; }
</style>
