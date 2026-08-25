<script setup>
import { t } from '../i18n.js'

defineProps({
  elements: { type: Array, default: () => [] },
  modelValue: { type: String, default: null }
})
defineEmits(['update:modelValue'])

// Les symboles viennent du pack lui-même (SPEC.md §5.5), déposés par
// tools/import_images.py. Sans eux, le serveur renvoie une pastille colorée.
const CODES = {
  Feu: 'FEU', Eau: 'EAU', Vie: 'VIE', Magie: 'MAGIE', Tech: 'TECH', Terre: 'TERRE',
  Air: 'AIR', 'Mort-Vivant': 'MORT_VIVANT', 'Lumière': 'LUMIERE', 'Ténèbres': 'TENEBRES',
  Kaos: 'KAOS', Inconnu: 'UNKNOWN'
}
const src = e => `/api/images/element/${CODES[e] ? e : 'Inconnu'}`
// Lie plutot qu'ecrit en dur : un src litteral serait resolu par Vite comme un
// asset local a l'assemblage, et le build echouerait.
const allSrc = '/api/images/element/all'
</script>

<template>
  <aside class="rail" :aria-label="t('elements.filter')">
    <button class="el" :class="{ on: !modelValue }" :title="t('elements.allTitle')"
            @click="$emit('update:modelValue', null)">
      <img :src="allSrc" :alt="t('elements.allTitle')" />
      <span>{{ t('elements.all') }}</span>
    </button>
    <button
      v-for="e in elements" :key="e"
      class="el" :class="{ on: modelValue === e }" :title="t(`elements.${e}`)"
      @click="$emit('update:modelValue', e)"
    >
      <img :src="src(e)" :alt="t(`elements.${e}`)" />
      <span>{{ t(`elements.${e}`) }}</span>
    </button>
  </aside>
</template>

<style scoped>
/* Défilement propre à la colonne : parcourir la liste des éléments ne doit pas
   déplacer le tableau, et inversement. */
.rail {
  flex: 0 0 92px;
  display: flex; flex-direction: column; gap: 4px;
  padding: 12px 8px; border-right: 1px solid var(--line);
  background: var(--panel);
  overflow-y: auto; overscroll-behavior: contain;
}
.el {
  background: none; border: 2px solid transparent; border-radius: 10px;
  padding: 6px 2px; cursor: pointer;
  display: flex; flex-direction: column; align-items: center; gap: 3px;
}
.el:hover { background: var(--panel-2); }
.el.on { border-color: var(--accent); background: var(--panel-2); }
.el img { width: 40px; height: 40px; object-fit: contain; }
.el span { font-size: 10px; color: var(--muted); text-align: center; line-height: 1.15; }
.el.on span { color: var(--text); }
</style>
