<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { api } from '../api.js'
import { t } from '../i18n.js'

const props = defineProps({ refreshKey: { type: Number, default: 0 } })
watch(() => props.refreshKey, () => load(true))

const emit = defineEmits(['open'])

const villains = ref([])
const loading = ref(true)
const error = ref(null)
const onlyCaptured = ref(false)

// Ordre canonique des éléments, celui des jeux. Kaos ferme la marche : ce n'est pas un
// élément du jeu mais il occupe la même place dans l'arborescence (cf. Element.KAOS).
const ELEMENT_ORDER = ['Feu', 'Eau', 'Vie', 'Magie', 'Tech', 'Terre', 'Air',
  'Mort-Vivant', 'Lumière', 'Ténèbres', 'Kaos']

async function load (quiet = false) {
  if (!quiet) loading.value = true
  error.value = null
  try {
    villains.value = await api.villains()
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}
onMounted(load)

const shown = computed(() =>
  onlyCaptured.value ? villains.value.filter(v => v.captured) : villains.value)

const grouped = computed(() => {
  const byElement = new Map()
  for (const villain of shown.value) {
    if (!byElement.has(villain.element)) byElement.set(villain.element, [])
    byElement.get(villain.element).push(villain)
  }
  return ELEMENT_ORDER
    .filter(e => byElement.has(e))
    .map(e => ({ element: e, villains: byElement.get(e) }))
})

const captured = computed(() => villains.value.filter(v => v.captured).length)
</script>

<template>
  <section class="villains">
    <p v-if="error" class="state err">{{ t('common.error', { message: error }) }}</p>
    <p v-else-if="loading" class="state">{{ t('common.loading') }}</p>

    <template v-else>
      <p class="summary">
        {{ t('villains.summary', { n: captured, captured, total: villains.length }) }}
      </p>
      <!-- Un vilain « capturé » est un vilain qu'un piège contient MAINTENANT. Les tags ne
           portent pas de tableau de chasse : celui-ci vit dans la sauvegarde de la console,
           hors périmètre (SPEC.md §3.6). -->
      <p class="note">{{ t('villains.note') }}</p>

      <label class="filter">
        <input type="checkbox" v-model="onlyCaptured" />
        {{ t('villains.onlyCaptured') }}
      </label>

      <p v-if="!shown.length" class="state">{{ t('dashboard.noCaptured') }}</p>

      <div v-for="group in grouped" :key="group.element" class="group">
        <h2>
          <img :src="`/api/images/element/${group.element}`" :alt="group.element" />
          {{ t(`elements.${group.element}`) }}
          <span class="count">
            {{ group.villains.filter(v => v.captured).length }} / {{ group.villains.length }}
          </span>
        </h2>
        <div class="cards">
          <button
            v-for="villain in group.villains" :key="villain.name"
            class="villain" :class="{ locked: !villain.captured, boss: villain.doomRaider }"
            @click="emit('open', villain)"
          >
            <div class="art">
              <!-- Jamais capturé : l'image reste la vraie, grisée, et un « ? » se pose
                   dessus. Même traitement que le cadenas des figurines (SPEC.md §10.1bis). -->
              <img :src="api.villainImageUrl(villain.name)" :alt="villain.name" loading="lazy" />
              <span v-if="!villain.captured" class="mark" aria-hidden="true">?</span>
            </div>
            <span class="name">{{ villain.captured ? villain.name : '?' }}</span>
            <span v-if="villain.doomRaider" class="tag">{{ t('villains.doomRaider') }}</span>
          </button>
        </div>
      </div>
    </template>
  </section>
</template>

<style scoped>
.villains { padding: 22px 0 30px; }
.summary { margin: 0 0 4px; font-size: 14px; }
.note { margin: 0 0 12px; color: var(--muted); font-size: 12px; }
.filter { display: inline-flex; align-items: center; gap: 6px; font-size: 12.5px;
  color: var(--muted); margin-bottom: 16px; cursor: pointer; }

.group { margin-bottom: 32px; }
h2 { display: flex; align-items: center; gap: 8px; font-size: 18px; margin: 0 0 16px;
  font-weight: 600; }
h2 img { width: 26px; height: 26px; object-fit: contain; }
.count { color: var(--muted); font-weight: 400; font-size: 12px; }

.cards { display: grid; grid-template-columns: repeat(auto-fill, minmax(150px, 1fr)); gap: 16px; }
.villain {
  position: relative;
  display: flex; flex-direction: column; gap: 6px; align-items: center;
  background: var(--panel); border: 1px solid var(--line); border-radius: 10px;
  padding: 14px; cursor: pointer; text-align: center; color: inherit; font: inherit;
}
.villain:hover { border-color: var(--accent); }
.villain.boss { border-color: var(--warn); }
.art { position: relative; width: 100%; aspect-ratio: 1; }
.art img { width: 100%; height: 100%; object-fit: contain; border-radius: 8px;
  background: var(--panel-2); }
.locked .art img { filter: grayscale(1) brightness(.5); }
.mark {
  position: absolute; inset: 0; display: grid; place-items: center;
  font-size: 34px; font-weight: 700; color: var(--muted); text-shadow: 0 2px 6px #000;
}
.name { font-size: 12.5px; font-weight: 600; max-width: 100%;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.locked .name { color: var(--muted); }
.tag { font-size: 9.5px; color: var(--warn); border: 1px solid var(--warn);
  border-radius: 5px; padding: 1px 5px; }
.state { color: var(--muted); padding: 26px 0; }
.err { color: var(--err); }
@media (max-width: 600px) { .cards { grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; } }
</style>
