<script setup>
defineProps({
  modelValue: { type: Object, required: true },
  games: { type: Array, default: () => [] },
  elements: { type: Array, default: () => [] },
  categories: { type: Array, default: () => [] }
})
const emit = defineEmits(['update:modelValue', 'reset'])
</script>

<template>
  <div class="bar">
    <input
      class="search"
      type="search"
      placeholder="Rechercher un nom…"
      :value="modelValue.search"
      @input="emit('update:modelValue', { ...modelValue, search: $event.target.value })"
    />

    <select :value="modelValue.game"
            @change="emit('update:modelValue', { ...modelValue, game: $event.target.value || null })">
      <option value="">Tous les jeux</option>
      <option v-for="g in games" :key="g" :value="g">{{ g.replaceAll('_', ' ') }}</option>
    </select>

    <select :value="modelValue.element"
            @change="emit('update:modelValue', { ...modelValue, element: $event.target.value || null })">
      <option value="">Tous les éléments</option>
      <option v-for="e in elements" :key="e" :value="e">{{ e }}</option>
    </select>

    <select :value="modelValue.category"
            @change="emit('update:modelValue', { ...modelValue, category: $event.target.value || null })">
      <option value="">Toutes les catégories</option>
      <option v-for="c in categories" :key="c" :value="c">{{ c }}</option>
    </select>

    <select :value="modelValue.state"
            @change="emit('update:modelValue', { ...modelValue, state: $event.target.value || null })">
      <option value="">Débloquées et non débloquées</option>
      <option value="unlocked">Débloquées seulement</option>
      <option value="locked">Non débloquées seulement</option>
    </select>

    <button class="reset" @click="emit('reset')">Réinitialiser</button>
  </div>
</template>

<style scoped>
.bar {
  display: flex; flex-wrap: wrap; gap: 8px;
  padding: 12px 16px;
  background: var(--panel);
  border-bottom: 1px solid var(--line);
  position: sticky; top: 0; z-index: 5;
}
input, select {
  background: var(--panel-2);
  border: 1px solid var(--line);
  border-radius: 8px;
  padding: 7px 10px;
}
.search { flex: 1 1 220px; min-width: 180px; }
.reset {
  background: transparent; border: 1px solid var(--line);
  border-radius: 8px; padding: 7px 12px; cursor: pointer; color: var(--muted);
}
.reset:hover { color: var(--text); border-color: var(--accent); }
</style>
