<script setup>
import { computed, ref } from 'vue'
import { api } from '../api.js'
import { DRAG_TYPE, readDrag, startDrag, portal, ready } from '../portal.js'
import { format, t } from '../i18n.js'

const props = defineProps({ slot: { type: Object, required: true } })
const emit = defineEmits(['place', 'clear', 'open'])

const over = ref(false)
const toy = computed(() => props.slot.toy)
const villain = computed(() => props.slot.villain)

// Un piège occupé montre son prisonnier : c'est l'information utile, le piège vide se
// reconnaît déjà à son propre visuel. Même règle que l'écran Pièges.
const art = computed(() => villain.value
  ? api.villainImageUrl(villain.value.name)
  : api.imageUrl(toy.value.toyId, toy.value.variantId))
const label = computed(() => villain.value?.name ?? toy.value?.nameFr)

// Pendant un survol, le contenu du presse-papier de glisser est masqué par le navigateur :
// seuls les TYPES sont lisibles. C'est donc sur eux qu'on décide d'accepter la cible.
const accepts = event => Array.from(event.dataTransfer?.types ?? []).includes(DRAG_TYPE)

function onDragOver (event) {
  if (!ready.value || portal.busy || !accepts(event)) return
  event.preventDefault()
  event.dataTransfer.dropEffect = 'move'
  over.value = true
}

function onDrop (event) {
  over.value = false
  const payload = readDrag(event)
  if (!ready.value || portal.busy || !payload) return
  event.preventDefault()
  if (payload.fromIndex === props.slot.index) return
  emit('place', { index: props.slot.index, toy: payload })
}
</script>

<template>
  <div
    class="slot"
    :class="{ filled: !!toy, over, beyond: slot.beyondGrid, trap: slot.trapSlot, pending: portal.busy && (portal.pendingSlot === slot.index || portal.pendingSlot == null) }"
    :data-slot-index="slot.index"
    :aria-busy="portal.busy && portal.pendingSlot === slot.index"
    :draggable="!!toy && ready && !portal.busy"
    @dragstart="toy && startDrag($event, toy, slot.index)"
    @dragover="onDragOver"
    @dragleave="over = false"
    @drop="onDrop"
  >
    <template v-if="toy">
      <!-- Cliquable seulement quand il y a quelque chose à raconter : un piège occupé ouvre la
           fiche de son prisonnier. -->
      <component :is="villain ? 'button' : 'div'" class="face"
                 :title="villain ? t('villains.openStory', { name: villain.name }) : label"
                 @click="villain && emit('open', villain)">
        <img class="art" :src="art" :alt="label"
             loading="lazy" draggable="false" />
        <span class="name">{{ label }}</span>
        <span class="meta"
              :title="slot.placedAt ? t('portal.placedAt', { date: format.dateTime(slot.placedAt) }) : ''">
          <template v-if="villain">{{ toy.nameFr }}</template>
          <template v-else>{{ t(`elements.${toy.element}`) }}</template>
        </span>
      </component>
      <button class="remove" :disabled="!ready || portal.busy" :title="t('portal.remove')" :aria-label="t('portal.remove')"
              @click.stop="emit('clear', slot.index)">✕</button>
    </template>

    <template v-else>
      <span class="empty" aria-hidden="true">
        {{ slot.trapSlot ? '🔒' : slot.index + 1 }}
      </span>
      <span class="hint">{{ slot.trapSlot ? t('portal.trapSlot') : t('portal.emptySlot') }}</span>
    </template>

    <!-- Un emplacement au-delà de la grille configurée n'est pas une erreur à cacher : la
         figurine y est réellement posée, et l'y laisser invisible la rendrait impossible à
         reposer ailleurs. On le signale et on permet de le libérer. -->
    <span v-if="slot.beyondGrid" class="badge" :title="t('portal.beyondTitle')">
      {{ t('portal.beyond') }}
    </span>
  </div>
</template>

<style scoped>
.slot {
  position: relative;
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  gap: 4px;
  aspect-ratio: 1;
  padding: 8px;
  border: 1px dashed var(--line);
  border-radius: 12px;
  background: var(--panel-2);
  text-align: center;
  min-width: 0;
}
.slot.pending { animation: portal-wait .7s ease-in-out infinite alternate; }
@keyframes portal-wait { to { border-color: var(--accent); box-shadow: inset 0 0 18px color-mix(in srgb, var(--accent) 15%, transparent); } }
@media (prefers-reduced-motion: reduce) { .slot.pending { animation: none; border-color: var(--accent); } }
.remove:disabled { opacity: .4; cursor: default; }
.slot.filled { border-style: solid; background: var(--panel); cursor: grab; }
.slot.filled:active { cursor: grabbing; }
.slot.over { border-color: var(--accent); background: var(--panel); }
.slot.beyond { border-color: var(--warn); }
/* La serrure à piège se distingue à l'œil : elle n'accepte pas la même chose que les autres. */
.slot.trap { border-style: solid; border-color: var(--accent); }
.slot.trap:not(.filled) { background: color-mix(in srgb, var(--accent) 10%, var(--panel-2)); }

.face {
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  gap: 4px; width: 100%; height: 100%; min-height: 0; min-width: 0;
  background: none; border: none; padding: 0; color: inherit; font: inherit;
}
button.face { cursor: pointer; }
button.face:hover .name { color: var(--accent); }

.art { width: 100%; flex: 1; min-height: 0; object-fit: contain; }
.art.locked { filter: grayscale(1) brightness(.62); }

.name {
  font-size: 12px; font-weight: 600; max-width: 100%;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.meta { font-size: 10.5px; color: var(--muted); }

.empty { font-size: 20px; color: var(--line); font-weight: 700; }
.hint { font-size: 10.5px; color: var(--muted); }

.remove {
  position: absolute; top: 4px; right: 4px;
  background: var(--panel-2); border: 1px solid var(--line); border-radius: 6px;
  color: var(--muted); font-size: 11px; line-height: 1;
  padding: 3px 5px; cursor: pointer;
}
.remove:hover { color: var(--err); border-color: var(--err); }

.badge {
  position: absolute; bottom: 4px; left: 4px;
  font-size: 9.5px; color: var(--warn);
  border: 1px solid var(--warn); border-radius: 5px; padding: 1px 4px;
}
</style>
