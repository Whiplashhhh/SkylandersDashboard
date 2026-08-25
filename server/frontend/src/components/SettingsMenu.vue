<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { THEMES } from '../theme.js'
import { LOCALES, t } from '../i18n.js'

defineProps({
  theme: { type: String, required: true },
  locale: { type: String, required: true }
})
defineEmits(['update:theme', 'update:locale'])

const open = ref(false)
const root = ref(null)

function close (event) {
  if (root.value && !root.value.contains(event.target)) open.value = false
}
function onKey (event) {
  if (event.key === 'Escape') open.value = false
}
onMounted(() => {
  document.addEventListener('click', close)
  document.addEventListener('keydown', onKey)
})
onBeforeUnmount(() => {
  document.removeEventListener('click', close)
  document.removeEventListener('keydown', onKey)
})

const label = entry => entry.label || t(entry.labelKey)
const hint = entry => (entry.hintKey ? ` — ${t(entry.hintKey)}` : '')
</script>

<template>
  <div ref="root" class="settings">
    <button
      class="gear" :title="t('settings.open')" :aria-label="t('settings.open')"
      :aria-expanded="open" @click="open = !open"
    >
      <!-- Roue crantée en SVG plutôt qu'un emoji : elle suit la couleur du texte et donc
           le thème, et rend pareil sur toutes les plateformes. -->
      <svg viewBox="0 0 24 24" width="20" height="20" aria-hidden="true">
        <path fill="currentColor" d="M19.14 12.94a7.6 7.6 0 0 0 .05-.94 7.6 7.6 0 0 0-.05-.94l2.03-1.58a.5.5
          0 0 0 .12-.62l-1.92-3.32a.5.5 0 0 0-.6-.22l-2.39.96a7.03 7.03 0 0 0-1.62-.94l-.36-2.54a.5.5 0 0
          0-.5-.42h-3.84a.5.5 0 0 0-.5.42l-.36 2.54c-.58.24-1.13.55-1.62.94l-2.39-.96a.5.5 0 0 0-.6.22L2.67
          8.86a.5.5 0 0 0 .12.62l2.03 1.58a7.6 7.6 0 0 0 0 1.88l-2.03 1.58a.5.5 0 0 0-.12.62l1.92 3.32c.13.22.39.3.6.22l2.39-.96c.5.39
          1.04.7 1.62.94l.36 2.54c.04.24.25.42.5.42h3.84c.25 0 .46-.18.5-.42l.36-2.54c.58-.24 1.13-.55
          1.62-.94l2.39.96c.22.08.47 0 .6-.22l1.92-3.32a.5.5 0 0 0-.12-.62ZM12 15.6A3.6 3.6 0 1 1 15.6 12
          3.6 3.6 0 0 1 12 15.6Z"/>
      </svg>
    </button>

    <div v-if="open" class="panel" role="dialog" :aria-label="t('settings.title')">
      <label>
        <span>{{ t('settings.theme') }}</span>
        <select :value="theme" @change="$emit('update:theme', $event.target.value)">
          <option v-for="entry in THEMES" :key="entry.id" :value="entry.id">
            {{ label(entry) }}{{ hint(entry) }}
          </option>
        </select>
      </label>

      <label>
        <span>{{ t('settings.language') }}</span>
        <select :value="locale" @change="$emit('update:locale', $event.target.value)">
          <option v-for="entry in LOCALES" :key="entry.id" :value="entry.id">
            {{ label(entry) }}{{ hint(entry) }}
          </option>
        </select>
      </label>
    </div>
  </div>
</template>

<style scoped>
.settings { position: relative; }
.gear {
  display: grid; place-items: center;
  background: none; border: 1px solid transparent; border-radius: 9px;
  padding: 6px; cursor: pointer; color: var(--muted);
}
.gear:hover { color: var(--text); background: var(--panel-2); }
.gear[aria-expanded='true'] { color: var(--text); border-color: var(--line); background: var(--panel-2); }

.panel {
  position: absolute; top: calc(100% + 6px); left: 0; z-index: 30;
  background: var(--panel); border: 1px solid var(--line); border-radius: 10px;
  padding: 12px; min-width: 260px;
  display: flex; flex-direction: column; gap: 10px;
  box-shadow: 0 10px 30px #0006;
}
.panel label { display: flex; flex-direction: column; gap: 4px; font-size: 12px; color: var(--muted); }
.panel select {
  background: var(--panel-2); border: 1px solid var(--line);
  border-radius: 8px; padding: 6px 10px; color: var(--text);
}
</style>
