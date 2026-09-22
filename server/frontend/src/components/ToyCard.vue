<script setup>
import { computed, ref, watch } from 'vue'
import { toyArtwork } from '../toyArtwork.js'
import { slotOf, startDrag } from '../portal.js'
import { t } from '../i18n.js'

const props = defineProps({
  toy: { type: Object, required: true },
  trap: { type: Object, default: null },
  /** Affiche le bouton « Mettre sur le portail » et rend la carte glissable. */
  placeable: { type: Boolean, default: false }
})
defineEmits(['open', 'place'])

const failed = ref(false)
const colors = { Feu: '#ef9471', Eau: '#70bce0', Vie: '#9bc975', Magie: '#b49adf', Tech: '#dac278', Terre: '#c7a47d', Air: '#a4d8e4', 'Mort-Vivant': '#a7b8b3', 'Lumière': '#e0d9b1', 'Ténèbres': '#a49bd4' }
const elementColor = computed(() => colors[props.toy.element] || 'var(--accent)')
const imageUrl = computed(() => toyArtwork(props.toy, props.trap))
watch(imageUrl, () => { failed.value = false })
const artworkName = computed(() => props.trap?.empty === false
  ? props.trap.villain?.name || t('traps.unknownVillain') : props.toy.nameFr)

// SPEC.md §10.1bis : le cadenas dit « pas encore joué », pas « à découvrir ». On montre donc
// la vraie image, simplement grisée — jamais une silhouette mystère.
const locked = computed(() => !props.toy.unlocked)

// Déjà sur le portail : le bouton le dit plutôt que de reposer la figurine là où elle est.
const onPortal = computed(() => props.placeable && slotOf(props.toy) !== null)
</script>

<template>
  <!-- Le bouton « poser » est un frère de la carte, pas un enfant : un bouton dans un bouton
       n'est pas du HTML valide et les navigateurs en font ce qu'ils veulent. -->
  <div class="holder" :style="{ '--element-color': elementColor }" :draggable="placeable" @dragstart="startDrag($event, toy)">
    <button class="card" :class="{ locked }" :aria-label="`${t('dashboard.openToy', { name: toy.nameFr })} — ${t(toy.unlocked ? 'detail.unlocked' : 'dashboard.notPlayed')}`" @click="$emit('open', toy)">
      <div class="card-top"><span>{{ t(`elements.${toy.element}`) }}</span><span class="state-symbol" :title="toy.unlocked ? t('detail.unlocked') : t('dashboard.notPlayed')">{{ toy.unlocked ? '✓' : '◇' }}</span></div>
      <div class="art">
        <img v-if="!failed" @error="failed = true" :src="imageUrl" :alt="artworkName" loading="lazy" draggable="false" />
        <span v-if="failed" class="image-fallback">{{ toy.nameFr.slice(0, 2) }}</span>
      </div>
      <div class="label">
        <span class="name">{{ toy.nameFr }}</span>
        <span class="meta">{{ t(`categoriesOne.${toy.category}`) }}<span class="card-arrow" aria-hidden="true">↗</span></span>
      </div>
    </button>

    <button
      v-if="placeable" class="place" :class="{ on: onPortal }"
      :title="onPortal ? t('portal.alreadyPlaced') : t('portal.place')"
      :aria-label="onPortal ? t('portal.alreadyPlaced') : t('portal.place')"
      @click.stop="$emit('place', toy)"
    >{{ onPortal ? '✓' : '+' }}</button>
  </div>
</template>

<style scoped>
.holder { position: relative; display: flex; min-width: 0; }
.card { flex: 1; min-width: 0; padding: 14px; display: flex; flex-direction: column; cursor: pointer; text-align: left; background: var(--panel); border: 1px solid var(--line); border-radius: 12px; overflow: hidden; transition: border-color .2s, transform .2s, box-shadow .2s; }
.card:hover { border-color: var(--element-color); transform: translateY(-4px); box-shadow: var(--shadow); }
.card-top { display: flex; justify-content: space-between; align-items: center; gap: 8px; font-size: 10px; color: var(--muted); }
.card-top > span:first-child::before { content: ''; display: inline-block; width: 5px; height: 5px; background: var(--element-color); border-radius: 50%; margin-right: 6px; }
.state-symbol { color: var(--accent); }
.locked .state-symbol { color: var(--muted); }
.art { position: relative; width: 100%; aspect-ratio: 1; flex-shrink: 0; overflow: hidden; margin: 10px 0; background: radial-gradient(ellipse at 50% 65%, color-mix(in srgb, var(--element-color) 14%, transparent), transparent 70%); }
.art img { position: absolute; inset: 0; display: block; width: 100%; height: 100%; object-fit: contain; filter: drop-shadow(0 9px 8px #0003); transition: transform .25s; }
.card:hover .art img { transform: scale(1.06); }
.locked .art img { filter: grayscale(1) opacity(.52); }
.locked .art { background: none; }
.image-fallback { display: grid; place-items: center; height: 100%; color: var(--element-color); font-size: 42px; font-weight: 700; }
.label { display: flex; flex-direction: column; gap: 6px; min-width: 0; border-top: 1px solid var(--line); padding-top: 12px; }
.name { font-weight: 650; font-size: 14px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.locked .name { color: var(--muted); }
.meta { display: flex; align-items: center; justify-content: space-between; font-size: 10px; color: var(--muted); }
.card-arrow { font-size: 15px; }
.place { position: absolute; right: 10px; top: 38px; width: 32px; height: 32px; z-index: 1; background: var(--panel-2); border: 1px solid var(--line); border-radius: 8px; color: var(--text); font-size: 20px; cursor: pointer; }
.place:hover, .place.on { color: var(--on-accent); background: var(--accent); border-color: var(--accent); }
@media (max-width: 600px) { .card { padding: 12px; } .name { font-size: 12px; } }
</style>
