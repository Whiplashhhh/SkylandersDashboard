<script setup>
import { computed, onMounted, ref } from 'vue'
import { api } from '../api.js'
import { t } from '../i18n.js'

const traps = ref([])
const loading = ref(true)
const error = ref(null)
const editing = ref(null)
const draft = ref('')
const saving = ref(false)

const ELEMENT_ORDER = ['Feu', 'Eau', 'Vie', 'Magie', 'Tech', 'Terre', 'Air',
  'Mort-Vivant', 'Lumière', 'Ténèbres', 'Kaos', 'Inconnu']

async function load () {
  loading.value = true
  error.value = null
  try {
    traps.value = await api.traps()
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}
onMounted(load)

const grouped = computed(() => {
  const byElement = new Map()
  for (const trap of traps.value) {
    if (!byElement.has(trap.element)) byElement.set(trap.element, [])
    byElement.get(trap.element).push(trap)
  }
  return ELEMENT_ORDER
    .filter(e => byElement.has(e))
    .map(e => ({ element: e, traps: byElement.get(e) }))
})

const occupied = computed(() => traps.value.filter(one => !one.empty).length)
const unnamed = computed(() =>
  new Set(traps.value.filter(one => !one.empty && !one.villainName)
    .map(one => one.villainRawId)).size)

function artFor (trap) {
  return trap.empty
    ? `/api/images/${trap.toyId}/${trap.variantId}`
    : `/api/images/villain/${trap.villainRawId}`
}

function startEdit (trap) {
  editing.value = trap.villainRawId
  draft.value = trap.villainName || ''
}

async function save (trap) {
  const name = draft.value.trim()
  if (!name) return
  saving.value = true
  try {
    // Nommer un identifiant le nomme partout : c'est le même vilain dans tous les pièges
    // qui le contiennent (SPEC.md §7.2).
    await api.nameVillain(trap.villainRawId, name, trap.element)
    editing.value = null
    await load()
  } catch (e) {
    error.value = e.message
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <section class="traps">
    <p v-if="error" class="state err">{{ t('common.error', { message: error }) }}</p>
    <p v-else-if="loading" class="state">{{ t('common.loading') }}</p>

    <template v-else>
      <p class="summary">
        {{ t('traps.summary', { n: occupied, occupied, total: traps.length }) }}
        <span v-if="unnamed" class="dim">{{ t('traps.unnamed', { n: unnamed }) }}</span>
      </p>
      <!-- La liste des vilains capturés au cours de la partie n'est pas sur les tags : elle vit
           dans la sauvegarde de la console. On montre donc ce que chaque piège contient
           MAINTENANT, pas un tableau de chasse (SPEC.md §3.6). -->
      <p class="note">{{ t('traps.note') }}</p>

      <div v-for="group in grouped" :key="group.element" class="group">
        <h2>
          <img :src="`/api/images/element/${group.element}`" :alt="group.element" />
          {{ t(`elements.${group.element}`) }}
          <span class="count">{{ group.traps.filter(one => !one.empty).length }} / {{ group.traps.length }}</span>
        </h2>
        <div class="cards">
          <article v-for="trap in group.traps" :key="`${trap.toyId}/${trap.variantId}`"
                   class="trap" :class="{ empty: trap.empty }">
            <!-- Un piège occupé montre son prisonnier : c'est l'information utile, le piège
                 vide se reconnaît déjà à son propre visuel. -->
            <img class="art" :src="artFor(trap)" :alt="trap.empty ? trap.trapName : (trap.villainName || t('traps.unknownVillain'))"
                 loading="lazy" />
            <div class="info">
              <span class="tname">{{ trap.trapName }}</span>

              <span v-if="trap.empty" class="villain dim">{{ t('traps.empty') }}</span>

              <template v-else-if="editing === trap.villainRawId">
                <form class="edit" @submit.prevent="save(trap)">
                  <input v-model="draft" :disabled="saving" autofocus
                         :placeholder="t('traps.placeholder', { id: trap.villainRawId })" />
                  <button type="submit" :disabled="saving || !draft.trim()">{{ t('traps.save') }}</button>
                  <button type="button" class="cancel" :title="t('traps.cancel')"
                          @click="editing = null">✕</button>
                </form>
              </template>

              <button v-else class="villain named" @click.stop="startEdit(trap)"
                      :class="{ unnamed: !trap.villainName }"
                      :title="t('traps.renameTitle', { id: trap.villainRawId })">
                {{ trap.villainName || t('table.unnamedVillain', { id: trap.villainRawId }) }}
              </button>
            </div>
          </article>
        </div>
      </div>
    </template>
  </section>
</template>

<style scoped>
.traps { padding: 14px 16px 30px; }
.summary { margin: 0 0 4px; font-size: 14px; }
.summary strong { font-size: 17px; }
.note { margin: 0 0 18px; color: var(--muted); font-size: 12px; }

.group { margin-bottom: 22px; }
h2 {
  display: flex; align-items: center; gap: 8px;
  font-size: 14px; margin: 0 0 10px; font-weight: 600;
}
h2 img { width: 26px; height: 26px; object-fit: contain; }
.count { color: var(--muted); font-weight: 400; font-size: 12px; }

.cards { display: grid; grid-template-columns: repeat(auto-fill, minmax(230px, 1fr)); gap: 10px; }
.trap {
  display: flex; gap: 10px; align-items: center;
  background: var(--panel); border: 1px solid var(--line);
  border-radius: 10px; padding: 8px;
}
.trap.empty { opacity: .55; }
.art { width: 44px; height: 44px; object-fit: contain; border-radius: 8px; background: var(--panel-2); }
.info { display: flex; flex-direction: column; gap: 3px; min-width: 0; flex: 1; }
.tname {
  font-weight: 600; font-size: 13px;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.villain { font-size: 12.5px; text-align: left; }
button.villain {
  background: none; border: none; padding: 0; cursor: pointer; color: var(--accent);
}
button.villain:hover { text-decoration: underline; }
button.villain.unnamed { color: var(--warn); font-style: italic; }
.dim { color: var(--muted); }
.summary .dim { display: block; margin-top: 2px; font-size: 12.5px; }

.edit { display: flex; gap: 4px; }
.edit input {
  flex: 1; min-width: 0; background: var(--panel-2);
  border: 1px solid var(--accent); border-radius: 6px; padding: 3px 6px; font-size: 12px;
}
.edit button {
  background: var(--panel-2); border: 1px solid var(--line);
  border-radius: 6px; padding: 3px 8px; cursor: pointer; font-size: 12px;
}
.edit button:disabled { opacity: .4; cursor: default; }
.cancel { color: var(--muted); }
.state { color: var(--muted); padding: 26px 0; }
.err { color: var(--err); }
</style>
