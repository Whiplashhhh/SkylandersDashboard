<script setup>
import { onUnmounted, ref, watch } from 'vue'
import { api } from '../api.js'
import { place, portal, ready, startDrag } from '../portal.js'
import { t } from '../i18n.js'
const search = ref(''), results = ref([]), loading = ref(false), failed = ref(false)
let timer, generation = 0
watch(search, value => {
  clearTimeout(timer)
  const current = ++generation
  results.value = []; failed.value = false
  loading.value = !!value.trim()
  if (!value.trim()) return
  timer = setTimeout(async () => {
    try {
      const toys = await api.toys({ search: value.trim() })
      if (current === generation) results.value = toys.slice(0, 6)
    } catch { if (current === generation) failed.value = true }
    finally { if (current === generation) loading.value = false }
  }, 180)
})
onUnmounted(() => { generation++; clearTimeout(timer) })
</script>

<template>
  <div class="portal-search">
    <label class="sr-only" for="portal-search">{{ t('bridge.toySearch') }}</label>
    <input id="portal-search" v-model="search" type="search" :placeholder="t('bridge.toySearch')" autocomplete="off">
    <p v-if="loading" role="status">{{ t('common.loading') }}</p>
    <p v-else-if="failed" role="alert">{{ t('bridge.errors.NETWORK_ERROR') }}</p>
    <p v-else-if="search.trim() && !results.length">{{ t('bridge.noResults') }}</p>
    <div v-if="results.length" class="results">
      <button v-for="toy in results" :key="`${toy.toyId}/${toy.variantId}`"
              :disabled="!ready || portal.busy" :title="toy.nameFr" :aria-label="`${t('portal.place')} : ${toy.nameFr}`"
              draggable="true" @dragstart="startDrag($event, toy)" @click="place(toy)">
        <img :src="api.imageUrl(toy.toyId, toy.variantId)" alt="" draggable="false">
        <span>{{ toy.nameFr }}</span>
      </button>
    </div>
  </div>
</template>

<style scoped>
.sr-only { position: absolute; width: 1px; height: 1px; padding: 0; margin: -1px; overflow: hidden; clip: rect(0, 0, 0, 0); white-space: nowrap; border: 0; }
input { width: 100%; padding: 10px 12px; font: inherit; font-size: 12px; color: var(--text); background: var(--panel-2); border: 1px solid var(--line); border-radius: 9px; }
p { color: var(--muted); font-size: 11px; margin: 8px 0; }
.results { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 6px; margin-top: 8px; }
button { display: flex; flex-direction: column; align-items: center; gap: 4px; min-width: 0; border: 1px solid var(--line); border-radius: 8px; padding: 7px 4px; background: var(--panel-2); color: var(--text); cursor: pointer; }
button:hover:not(:disabled) { border-color: var(--accent); background: var(--panel); }
button:disabled { opacity: .5; cursor: default; }
img { width: 100%; height: 54px; object-fit: contain; }
span { max-width: 100%; font-size: 10px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
</style>
