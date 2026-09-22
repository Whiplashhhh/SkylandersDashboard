<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { api } from '../api.js'
import { toyArtwork } from '../toyArtwork.js'
import { t } from '../i18n.js'
import { place, slotOf, startDrag } from '../portal.js'

const props = defineProps({
  /** Le portail est ouvert : les pièges peuvent être posés dessus. */
  placeable: { type: Boolean, default: false },
  refreshKey: { type: Number, default: 0 }
})
watch(() => props.refreshKey, () => load(true))

const emit = defineEmits(['open'])

const traps = ref([])
const known = ref([])
const loading = ref(true)
const error = ref(null)
const editing = ref(null)
const draft = ref('')
const saving = ref(false)

const ELEMENT_ORDER = ['Feu', 'Eau', 'Vie', 'Magie', 'Tech', 'Terre', 'Air',
  'Mort-Vivant', 'Lumière', 'Ténèbres', 'Kaos', 'Inconnu']

async function load (quiet = false) {
  if (!quiet) loading.value = true
  error.value = null
  try {
    traps.value = await api.traps()
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}
onMounted(async () => {
  await load()
  try {
    // Le roster des vilains sert de liste de choix : taper « Buzzer Beack » une fois créait un
    // vilain que plus rien ne pouvait rapprocher du référentiel.
    known.value = await api.villainNames()
  } catch {
    known.value = []          // liste indisponible : on retombe sur la saisie libre
  }
})

const onPortal = trap => slotOf(trap) !== null

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
  return toyArtwork(trap, trap)
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
                   class="trap" :class="{ empty: trap.empty }"
                   :draggable="placeable" @dragstart="startDrag($event, trap)">
            <!-- Un piège occupé montre son prisonnier : c'est l'information utile, le piège
                 vide se reconnaît déjà à son propre visuel. -->
            <!-- Un piège occupé s'ouvre sur la fiche de son prisonnier : histoire, élément,
                 lien vers le wiki. Vide, il n'y a rien à raconter. -->
            <component :is="trap.villain ? 'button' : 'div'" class="artbox"
                       :title="trap.villain ? t('villains.openStory', { name: trap.villain.name }) : ''"
                       @click="trap.villain && emit('open', trap.villain)">
              <img class="art" :src="artFor(trap)"
                   :alt="trap.empty ? trap.trapName : (trap.villainName || t('traps.unknownVillain'))"
                   loading="lazy" draggable="false" />
            </component>
            <div class="info">
              <span class="tname">
                {{ trap.trapName }}
                <button v-if="placeable" class="place" :class="{ on: onPortal(trap) }"
                        :title="onPortal(trap) ? t('portal.alreadyPlaced') : t('portal.place')"
                        :aria-label="onPortal(trap) ? t('portal.alreadyPlaced') : t('portal.place')"
                        @click.stop="place(trap)">{{ onPortal(trap) ? '✓' : '+' }}</button>
              </span>

              <span v-if="trap.empty" class="villain dim">{{ t('traps.empty') }}</span>

              <template v-else-if="editing === trap.villainRawId">
                <form class="edit" @submit.prevent="save(trap)">
                  <input v-model="draft" :disabled="saving" autofocus
                         :list="known.length ? 'villain-names' : undefined"
                         :placeholder="t('traps.placeholder', { id: trap.villainRawId })" />
                  <datalist v-if="known.length" id="villain-names">
                    <option v-for="n in known" :key="n" :value="n" />
                  </datalist>
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
.traps { padding: 22px 0 30px; }
.summary { margin: 0 0 4px; font-size: 14px; }
.summary strong { font-size: 17px; }
.note { margin: 0 0 18px; color: var(--muted); font-size: 12px; }

.group { margin-bottom: 32px; }
h2 {
  display: flex; align-items: center; gap: 8px;
  font-size: 18px; margin: 0 0 16px; font-weight: 600;
}
h2 img { width: 26px; height: 26px; object-fit: contain; }
.count { color: var(--muted); font-weight: 400; font-size: 12px; }

.cards { display: grid; grid-template-columns: repeat(auto-fill, minmax(min(260px, 100%), 1fr)); gap: 16px; }
.trap {
  display: flex; gap: 10px; align-items: center;
  background: var(--panel); border: 1px solid var(--line);
  border-radius: 12px; padding: 16px;
}
.trap.empty { opacity: 1; }
.artbox { background: none; border: none; padding: 0; display: block; }
button.artbox { cursor: pointer; }
button.artbox:hover .art { outline: 2px solid var(--accent); outline-offset: 1px; }
.art { width: 60px; height: 60px; object-fit: contain; border-radius: 8px; background: var(--panel-2); }
.info { display: flex; flex-direction: column; gap: 3px; min-width: 0; flex: 1; }
.tname {
  font-weight: 600; font-size: 13px;
  display: flex; align-items: center; gap: 6px; min-width: 0;
}
.place {
  width: 20px; height: 20px; line-height: 1; flex: 0 0 auto;
  background: var(--panel-2); border: 1px solid var(--line); border-radius: 6px;
  color: var(--muted); font-size: 12px; cursor: pointer;
}
.place:hover, .place.on {
  color: var(--on-accent); background: var(--accent); border-color: var(--accent);
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
