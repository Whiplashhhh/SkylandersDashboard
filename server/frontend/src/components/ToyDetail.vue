<script setup>
import { computed, ref, watch } from 'vue'
import { useDrawerFocus } from '../useDrawerFocus.js'
import { api } from '../api.js'
import { toyArtwork } from '../toyArtwork.js'
import ProgressChart from './ProgressChart.vue'
import { format, t } from '../i18n.js'
import { slotOf } from '../portal.js'

const props = defineProps({
  toy: { type: Object, default: null },
  trap: { type: Object, default: null },
  placeable: { type: Boolean, default: false }
})
const drawer = ref(null)
useDrawerFocus(() => Boolean(props.toy), drawer)

defineEmits(['close', 'place'])

const onPortal = computed(() => props.toy && slotOf(props.toy) !== null)

const detail = ref(null)
const history = ref([])
const error = ref(null)

watch(() => props.toy, async toy => {
  detail.value = null
  history.value = []
  error.value = null
  if (!toy) return
  try {
    const [d, h] = await Promise.all([
      api.toy(toy.toyId, toy.variantId),
      api.history(toy.toyId, toy.variantId)
    ])
    detail.value = d
    history.value = h
  } catch (e) {
    error.value = e.message
  }
}, { immediate: true })

const DASH = '—'
const duration = seconds => format.duration(seconds) ?? DASH
const moment = iso => format.dateTime(iso) ?? DASH
</script>

<template>
  <aside v-if="toy" class="drawer" ref="drawer" role="dialog" aria-modal="true" :aria-label="toy.nameFr" tabindex="-1">
    <header>
      <div>
        <h2>{{ toy.nameFr }}</h2>
        <p class="sub">{{ toy.nameEn }} · {{ t(`elements.${toy.element}`) }} · {{ t(`categoriesOne.${toy.category}`) }}</p>
      </div>
      <button class="close" @click="$emit('close')" :aria-label="t('detail.close')">✕</button>
    </header>

    <img class="art" :src="toyArtwork(toy, trap)" :alt="trap?.empty === false ? (trap.villain?.name || t('traps.unknownVillain')) : toy.nameFr"
         :class="{ locked: !toy.unlocked }" />

    <div class="actions">
      <button v-if="placeable" class="to-portal" :class="{ on: onPortal }"
              @click="$emit('place', toy)">
        {{ onPortal ? t('portal.alreadyPlaced') : t('portal.place') }}
      </button>

      <!-- Pas de bouton quand aucune page n'a été vérifiée pour cette identité : pièges,
           coffres et cristaux n'en ont pas sur le wiki. Mieux vaut rien qu'un lien mort
           (tools/wiki_links.py). -->
      <a v-if="toy.wikiUrl" class="wiki" :href="toy.wikiUrl" target="_blank"
         rel="noopener noreferrer" :title="t('detail.wikiTitle')">
        {{ t('detail.wiki') }} ↗
      </a>
    </div>

    <p v-if="error" class="warn">{{ t('detail.loadFailed', { message: error }) }}</p>

    <ul v-if="detail?.warnings?.length" class="warnings">
      <li v-for="w in detail.warnings" :key="w.code">{{ t(`notices.${w.code}`, w.params) }}</li>
    </ul>

    <dl class="facts">
      <dt>{{ t('detail.identity') }}</dt>
      <dd>{{ t('detail.identityValue', { toyId: toy.toyId, variantId: toy.variantId }) }}</dd>
      <dt>{{ t('detail.games') }}</dt>
      <dd>{{ toy.games.map(g => g.replaceAll('_', ' ')).join(', ') }}</dd>
      <dt>{{ t('detail.status') }}</dt>
      <dd>{{ toy.unlocked ? t('detail.unlocked')
        : (toy.received ? t('detail.neverPlayed') : t('detail.notReceived')) }}</dd>
      <dt v-if="toy.firstPlayedAt">{{ t('detail.firstPlayed') }}</dt>
      <dd v-if="toy.firstPlayedAt">{{ moment(toy.firstPlayedAt) }}</dd>
      <dt v-if="toy.lastSavedAt">{{ t('detail.lastSaved') }}</dt>
      <dd v-if="toy.lastSavedAt">{{ moment(toy.lastSavedAt) }}</dd>
    </dl>

    <ProgressChart v-if="history.length" :history="history" />

    <section v-if="detail?.latest" class="progress">
      <h3>{{ t('detail.lastReading') }}</h3>
      <div class="grid">
        <div><span class="k">{{ t('columns.xp') }}</span><span class="v">{{ detail.latest.xp ?? DASH }}</span></div>
        <div><span class="k">{{ t('columns.gold') }}</span><span class="v">{{ detail.latest.gold ?? DASH }}</span></div>
        <div><span class="k">{{ t('columns.playtime') }}</span><span class="v">{{ duration(detail.latest.playtimeSeconds) }}</span></div>
        <div><span class="k">{{ t('columns.upgrades') }}</span><span class="v">{{ detail.latest.upgradesCount ?? DASH }}</span></div>
        <div v-if="detail.latest.nickname"><span class="k">{{ t('detail.nickname') }}</span><span class="v">{{ detail.latest.nickname }}</span></div>
      </div>
      <!-- Le niveau n'est volontairement pas affiché : la courbe XP→niveau n'est pas mesurée
           (FORMAT.md §5.1). Afficher une valeur devinée contreviendrait à SPEC.md §10.4. -->
      <p class="note">{{ t('detail.levelNote') }}</p>
    </section>

    <!-- La vue tableau reste disponible à côté du graphe : une courbe se lit d'un coup d'œil,
         un chiffre exact se lit dans un tableau. -->
    <details v-if="history.length > 1" class="history">
      <summary>{{ t('detail.history', { n: history.length }) }}</summary>
      <table>
        <thead><tr><th>{{ t('detail.tableSaved') }}</th><th>{{ t('columns.xp') }}</th><th>{{ t('columns.gold') }}</th><th>{{ t('detail.tableBlocks') }}</th></tr></thead>
        <tbody>
          <tr v-for="(h, i) in history.slice().reverse()" :key="i">
            <td>{{ moment(h.at || h.capturedAt) }}</td>
            <td>{{ h.xp ?? DASH }}</td>
            <td>{{ h.gold ?? DASH }}</td>
            <td>{{ h.changedBlocks }}</td>
          </tr>
        </tbody>
      </table>
    </details>

    <details v-if="detail?.files?.length" class="files">
      <summary>{{ t('detail.files', { n: detail.files.length }) }}</summary>
      <ul><li v-for="f in detail.files" :key="f">{{ f }}</li></ul>
    </details>
  </aside>
</template>

<style scoped>
.drawer {
  position: fixed; top: 0; right: 0; bottom: 0;
  width: min(430px, 100vw);
  background: var(--panel);
  border-left: 1px solid var(--line);
  padding: 28px; overflow-y: auto; z-index: 20;
  display: flex; flex-direction: column; gap: 20px;
}
header { display: flex; justify-content: space-between; align-items: flex-start; gap: 10px; }
h2 { margin: 0; font-size: 26px; letter-spacing: -.04em; }
h3 { margin: 0 0 8px; font-size: 13px; text-transform: uppercase; color: var(--muted); letter-spacing: .04em; }
.sub { margin: 2px 0 0; color: var(--muted); font-size: 12px; }
.close { background: none; border: none; color: var(--muted); font-size: 18px; cursor: pointer; min-width: 36px; min-height: 36px; }

.art { width: 200px; max-width: 100%; align-self: center; border-radius: 10px; background: var(--panel-2); }
.art.locked { filter: grayscale(1) brightness(.62); }

.actions { display: flex; gap: 8px; justify-content: center; flex-wrap: wrap; }
.to-portal {
  background: var(--panel-2); border: 1px solid var(--line); border-radius: 8px;
  padding: 6px 14px; cursor: pointer; font-size: 13px; color: var(--text);
}
.to-portal:hover { border-color: var(--accent); }
.to-portal.on { background: var(--accent); color: var(--on-accent); border-color: var(--accent); }

.wiki {
  background: var(--panel-2); border: 1px solid var(--line); border-radius: 8px;
  padding: 6px 14px; font-size: 13px; color: var(--text); text-decoration: none;
}
.wiki:hover { border-color: var(--accent); color: var(--accent); }

.warnings { margin: 0; padding-left: 18px; color: var(--warn); font-size: 12.5px; }
.warn { color: var(--err); }

.facts { display: grid; grid-template-columns: auto 1fr; gap: 10px 14px; margin: 0; font-size: 13px; }
.facts dt { color: var(--muted); }
.facts dd { margin: 0; }

.grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 8px; }
.grid div { background: var(--panel-2); border-radius: 8px; padding: 8px 10px; }
.k { display: block; font-size: 11px; color: var(--muted); }
.v { font-size: 17px; font-weight: 600; }
.note { font-size: 11.5px; color: var(--muted); margin: 8px 0 0; }

table { width: 100%; border-collapse: collapse; font-size: 12px; }
th, td { text-align: left; padding: 4px 6px; border-bottom: 1px solid var(--line); }
th { color: var(--muted); font-weight: 500; }

.files { font-size: 12px; color: var(--muted); }
.files ul { margin: 6px 0 0; padding-left: 16px; word-break: break-all; }
</style>
