<script setup>
import { computed } from 'vue'
import { api } from '../api.js'

const props = defineProps({ toy: { type: Object, required: true } })
defineEmits(['open'])

const imageUrl = computed(() => api.imageUrl(props.toy.toyId, props.toy.variantId))

// SPEC.md §10.1bis : le cadenas dit « pas encore joué », pas « à découvrir ». On montre donc
// la vraie image, simplement grisée — jamais une silhouette mystère.
const locked = computed(() => !props.toy.unlocked)
</script>

<template>
  <button class="card" :class="{ locked }" @click="$emit('open', toy)">
    <div class="art">
      <img :src="imageUrl" :alt="toy.nameFr" loading="lazy" />
      <span v-if="locked" class="lock" aria-hidden="true">🔒</span>
    </div>
    <div class="label">
      <span class="name">{{ toy.nameFr }}</span>
      <span class="meta">{{ toy.element }}</span>
    </div>
  </button>
</template>

<style scoped>
.card {
  background: var(--panel);
  border: 1px solid var(--line);
  border-radius: 12px;
  padding: 8px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  cursor: pointer;
  text-align: left;
  transition: border-color .12s, transform .12s;
}
.card:hover { border-color: var(--accent); transform: translateY(-2px); }
.card:focus-visible { outline: 2px solid var(--accent); outline-offset: 2px; }

.art { position: relative; aspect-ratio: 1; }
.art img {
  width: 100%; height: 100%;
  object-fit: contain;
  border-radius: 8px;
  background: var(--panel-2);
}
/* Traitement purement visuel, sur la même image que si elle était débloquée (SPEC.md §10.3). */
.locked .art img { filter: grayscale(1) brightness(.62); }
.lock {
  position: absolute; inset: 0;
  display: grid; place-items: center;
  font-size: 30px;
  text-shadow: 0 2px 6px #000;
}

.label { display: flex; flex-direction: column; gap: 2px; min-width: 0; }
.name {
  font-weight: 600; font-size: 13px;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.locked .name { color: var(--muted); }
.meta { font-size: 11px; color: var(--muted); }
</style>
