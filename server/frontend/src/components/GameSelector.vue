<script setup>
import { ref } from 'vue'
import { t } from '../i18n.js'

defineProps({
  games: { type: Array, default: () => [] },
  modelValue: { type: String, default: null }
})
defineEmits(['update:modelValue'])

// Un jeu sans logo fourni affiche son nom, dans les couleurs du thème. Un badge d'image
// généré porterait des couleurs figées et resterait sombre en thème clair.
const missing = ref(new Set())
const fail = g => { missing.value = new Set(missing.value).add(g) }

// Les noms de jeux ne se traduisent pas : ce sont des titres commerciaux, identiques
// dans les deux langues.
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
  <nav class="games" :aria-label="t('games.pick')">
    <button class="game all" :class="{ on: !modelValue }" :aria-pressed="!modelValue" @click="$emit('update:modelValue', null)">
      <span class="txt">{{ t('games.all') }}</span>
    </button>
    <button
      v-for="g in games" :key="g"
      class="game" :class="{ on: modelValue === g }" :aria-pressed="modelValue === g"
      @click="$emit('update:modelValue', g)"
    >
      <img v-if="!missing.has(g)" :src="`/api/images/game/${g}`" :alt="label(g)" @error="fail(g)" />
      <span v-else class="txt">{{ label(g) }}</span>
    </button>
  </nav>
</template>

<style scoped>
.games {
  display: flex; gap: 8px; padding: 0 0 4px; overflow-x: auto;
  background: transparent;
  /* Centré tant que ça tient ; `safe` évite qu'un débordement rende le premier
     bouton inatteignable au défilement sur petit écran. */
  justify-content: flex-start;
  flex: 0 0 auto;
}
.game {
  flex: 0 0 auto;
  background: var(--panel); border: 1px solid var(--line);
  border-radius: 8px; padding: 8px 18px; cursor: pointer;
  display: grid; place-items: center; min-height: 54px;
  transition: border-color .12s, transform .12s;
}
.game:hover { transform: translateY(-2px); }
.game.on { border-color: var(--accent); background: color-mix(in srgb, var(--accent) 8%, var(--panel)); color: var(--accent); }
.game img { height: 34px; max-width: 112px; object-fit: contain; display: block; border-radius: 6px; }
.game .txt { white-space: nowrap; }
.all { min-width: 110px; }
.txt { font-size: 12px; font-weight: 600; }
</style>
