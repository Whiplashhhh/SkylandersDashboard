<script setup>
import { THEMES } from '../theme.js'
defineProps({ modelValue: { type: String, required: true } })
defineEmits(['update:modelValue'])
</script>

<template>
  <label class="theme">
    <span class="sr">Thème</span>
    <select :value="modelValue" @change="$emit('update:modelValue', $event.target.value)">
      <option v-for="t in THEMES" :key="t.id" :value="t.id">
        {{ t.label }}{{ t.hint ? ` — ${t.hint}` : '' }}
      </option>
    </select>
  </label>
</template>

<style scoped>
.theme select {
  background: var(--panel-2); border: 1px solid var(--line);
  border-radius: 8px; padding: 6px 10px;
}
.sr {
  position: absolute; width: 1px; height: 1px;
  overflow: hidden; clip-path: inset(50%); white-space: nowrap;
}
</style>
