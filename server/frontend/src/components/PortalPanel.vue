<script setup>
import { onMounted } from 'vue'
import { api } from '../api.js'
import PortalSearch from './PortalSearch.vue'
import PortalSlot from './PortalSlot.vue'
import CemuPortal from './CemuPortal.vue'
import { clearAll, clearSlot, figurineSlots, load, occupiedCount, place, portal,
  trapSlot, ready, dismissCopyChoice } from '../portal.js'
import { t } from '../i18n.js'

const props = defineProps({
  /** Rendu plein écran dans la fenêtre détachée : ni bouton fermer, ni bouton détacher. */
  detached: { type: Boolean, default: false }
})
const emit = defineEmits(['close', 'detach', 'openVillain'])

/** Taille de la fenêtre détachée. Calibrée sur la grille : 3 colonnes + la serrure. */
const WINDOW = { width: 440, height: 780 }


onMounted(() => {
  if (!portal.loaded) load()
})

function detach () {
  // Même origine, donc la fenêtre ouverte partage le BroadcastChannel et se synchronise
  // instantanément avec celle-ci.
  // Taille fixe : la disposition du portail a une forme voulue — trois rangées de trois plus
  // la serrure — et la laisser étirer ne rendrait pas la grille plus lisible. `resizable=no`
  // est respecté par les fenêtres ouvertes par script ; la mise en page tient de toute façon
  // si un navigateur l'ignore.
  const opened = window.open('/portail', 'skylanders-portail',
    `width=${WINDOW.width},height=${WINDOW.height},resizable=no,scrollbars=yes,`
    + 'menubar=no,toolbar=no,location=no,status=no')
  // Le panneau se retire de la fenêtre principale : le portail est maintenant à côté, le
  // garder ici en double mangerait la largeur de la grille pour rien. Les boutons « poser »
  // des listes, eux, restent — c'est de là qu'on alimente la fenêtre détachée.
  if (opened) emit('detach')
}

function onSlotDrop ({ index, toy }) {
  place(toy, index)
}
</script>

<template>
  <section class="portal" :class="{ detached: props.detached }">
    <header>
      <div class="titles">
        <h2>{{ t('portal.title') }}</h2>
        <span class="count">
          {{ t('portal.count', { n: occupiedCount, total: portal.slotCount }) }}
        </span>
      </div>
      <div class="actions">
        <button v-if="!props.detached" class="ghost" :title="t('portal.detachTitle')"
                @click="detach">{{ t('portal.detach') }}</button>
        <button class="ghost" :disabled="!ready || portal.busy || !portal.slots.some(s => s.toy)" @click="clearAll">
          {{ t('portal.clearAll') }}
        </button>
        <button v-if="!props.detached" class="close" :aria-label="t('portal.close')"
                :title="t('portal.close')" @click="$emit('close')">✕</button>
      </div>
    </header>

    <CemuPortal />
    <PortalSearch />
    <div v-if="portal.copyChoice" class="copy-choice">
      <strong>{{ t('bridge.chooseCopy') }}</strong>
      <p>{{ t('bridge.copyHint') }}</p>
      <button v-for="file in portal.copyChoice.files" :key="file.id" :disabled="!ready || portal.busy"
              @click="place(portal.copyChoice.toy, portal.copyChoice.index, file.id, portal.copyChoice.preconditions)">
        <img :src="api.imageUrl(portal.copyChoice.toy.toyId, portal.copyChoice.toy.variantId)" alt="">
        <span>{{ file.relativePath }}</span>
      </button>
      <button @click="dismissCopyChoice">{{ t('bridge.cancel') }}</button>
    </div>
    <p v-if="portal.busy" class="state" role="status">{{ t('bridge.pending') }}</p>
    <p v-else-if="portal.authenticated && !ready" class="state" role="status">{{ t('bridge.lastObserved') }}</p>

    <p v-if="portal.error" class="err" role="alert">{{ t(portal.error.key, portal.error.params) }}</p>
    <p v-else-if="!portal.loaded" class="state">{{ t('common.loading') }}</p>

    <template v-if="portal.loaded">
      <!-- Trois par rangée : la grille du portail se lit d'un coup d'œil et garde la même
           forme quelle que soit la largeur du panneau. -->
      <div class="slots" :class="{ offline: !ready }">
        <PortalSlot
          v-for="slot in figurineSlots" :key="slot.index" :slot="slot"
          @place="onSlotDrop" @clear="clearSlot" @open="emit('openVillain', $event)"
        />
      </div>

      <!-- La serrure à piège, seule et centrée sous la grille, comme sur le vrai portail. -->
      <div v-if="trapSlot" class="keyhole">
        <PortalSlot
          :slot="trapSlot" @place="onSlotDrop" @clear="clearSlot"
          @open="emit('openVillain', $event)"
        />
      </div>
    </template>

    <!-- Le glisser-déposer HTML5 n'existe pratiquement pas sur tactile : le bouton des listes
         reste le chemin garanti, sur tous les appareils (plan §4). -->
    <p class="note">{{ t('bridge.directHint') }}</p>
    <!-- BroadcastChannel relie les fenêtres d'un même navigateur, pas les appareils entre eux. -->
    <p class="note dim">{{ t('bridge.directSync') }}</p>
  </section>
</template>

<style scoped>
.portal {
  display: flex; flex-direction: column; gap: 10px;
  width: min(340px, 100vw);
  flex: 0 0 auto;
  border-left: 1px solid var(--line);
  background: var(--panel);
  padding: 12px;
  overflow-y: auto;
}
/* Détachée, la fenêtre n'a que ça à montrer : elle prend toute la place. */
.portal.detached {
  width: 100%; height: 100vh; border-left: none; background: var(--bg);
}

header { display: flex; align-items: flex-start; justify-content: space-between; gap: 8px; }
.titles { display: flex; flex-direction: column; gap: 2px; min-width: 0; }
h2 { margin: 0; font-size: 15px; }
.count { font-size: 11.5px; color: var(--muted); }
.actions { display: flex; align-items: center; gap: 6px; }

.ghost {
  background: transparent; border: 1px solid var(--line); color: var(--muted);
  border-radius: 8px; padding: 4px 9px; cursor: pointer; font-size: 12px;
}
.ghost:hover:not(:disabled) { color: var(--text); border-color: var(--accent); }
.ghost:disabled { opacity: .4; cursor: default; }
.close { background: none; border: none; color: var(--muted); font-size: 15px; cursor: pointer; }
.close:hover { color: var(--text); }

.slots { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; }
/* Un seul emplacement, centré sur la largeur d'une colonne de la grille au-dessus. */
.keyhole {
  display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px;
  margin-top: 2px;
}
.keyhole > * { grid-column: 2; }

.state { color: var(--muted); font-size: 13px; margin: 0; }
.err { color: var(--err); font-size: 12.5px; margin: 0; }
.note { color: var(--muted); font-size: 11px; margin: 0; }
.note.dim { opacity: .75; }
.slots.offline { opacity: .6; }
.copy-choice { border: 1px solid var(--accent); border-radius: 10px; padding: 10px; font-size: 12px; }
.copy-choice p { color: var(--muted); font-size: 11px; }
.copy-choice button { display: flex; align-items: center; gap: 8px; width: 100%; margin-top: 6px; padding: 6px; border: 1px solid var(--line); border-radius: 6px; color: var(--text); background: var(--panel); text-align: left; cursor: pointer; }
.copy-choice img { width: 32px; height: 36px; object-fit: contain; }
.copy-choice span { overflow-wrap: anywhere; font-size: 10px; }
@media (max-width: 900px) { .portal:not(.detached) { position: fixed; right: 0; top: 0; bottom: 0; z-index: 10; box-shadow: -12px 0 40px #0004; width: min(360px, 100vw); } }
</style>
