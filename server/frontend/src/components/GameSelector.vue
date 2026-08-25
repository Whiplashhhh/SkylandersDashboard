<script setup>
import { ref } from 'vue'

defineProps({
  games: { type: Array, default: () => [] },
  modelValue: { type: String, default: null }
})
defineEmits(['update:modelValue'])

// Un jeu sans logo fourni affiche son nom, dans les couleurs du thème. Un badge d'image
// généré porterait des couleurs figées et resterait sombre en thème clair.
const missing = ref(new Set())
const fail = g => { missing.value = new Set(missing.value).add(g) }

const LABELS = {
  SPYROS_ADVENTURE: "Spyro's Adventure",
  GIANTS: 'Giants',
  SWAP_FORCE: 'Swap Force',
  TRAP_TEAM: 'Trap Team',
  SUPERCHARGERS: 'SuperChargers',
  IMAGINATORS: 'Imaginators'
}
const label = g => LABELS[g] || g.replaceAll('_', ' ')
</script>

<template>
  <nav class="games" aria-label="Choix du jeu">
    <button class="game all" :class="{ on: !modelValue }" @click="$emit('update:modelValue', null)">
      <span class="txt">Tous les jeux</span>
    </button>
    <button
      v-for="g in games" :key="g"
      class="game" :class="{ on: modelValue === g }"
      @click="$emit('update:modelValue', g)"
    >
      <img v-if="!missing.has(g)" :src="`/api/images/game/${g}`" :alt="label(g)" @error="fail(g)" />
      <span v-else class="txt">{{ label(g) }}</span>
    </button>
  </nav>
</template>

<style scoped>
.games {
  display: flex; gap: 10px; padding: 12px 16px; overflow-x: auto;
  border-bottom: 1px solid var(--line); background: var(--panel);
  /* Centré tant que ça tient ; `safe` évite qu'un débordement rende le premier
     bouton inatteignable au défilement sur petit écran. */
  justify-content: safe center;
  flex: 0 0 auto;
}
.game {
  flex: 0 0 auto;
  background: var(--panel-2); border: 2px solid transparent;
  border-radius: 12px; padding: 6px 8px; cursor: pointer;
  display: grid; place-items: center; min-height: 60px;
  transition: border-color .12s, transform .12s;
}
.game:hover { transform: translateY(-2px); }
.game.on { border-color: var(--accent); }
.game img { height: 46px; display: block; border-radius: 6px; }
.game .txt { white-space: nowrap; }
.all { min-width: 130px; }
.txt { font-size: 14px; font-weight: 600; }
</style>
