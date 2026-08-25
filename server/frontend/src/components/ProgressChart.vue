<script setup>
import { computed, ref } from 'vue'
import { format, t } from '../i18n.js'

const props = defineProps({
  history: { type: Array, required: true }
})

// Une seule mesure à la fois, jamais deux échelles sur un même graphe : XP et or n'ont ni la
// même unité ni le même ordre de grandeur, et les superposer laisserait croire à une corrélation
// que le choix des échelles aurait fabriquée.
const MEASURES = [
  { key: 'xp', labelKey: 'columns.xp', format: v => format.number(v) },
  { key: 'gold', labelKey: 'columns.gold', format: v => format.number(v) },
  { key: 'playtimeSeconds', labelKey: 'columns.playtime', format: v => format.duration(v) }
]

const measure = ref(MEASURES[0])
const hover = ref(null)

// Deux façons de poser l'axe horizontal, et aucune n'est « la bonne » :
//   date   — honnête sur le temps, mais une session de jeu écrase tous ses relevés
//            sur quelques pixels quand la session précédente date de deux jours ;
//   relevé — chaque sauvegarde occupe la même largeur, la forme redevient lisible,
//            mais l'espacement ne dit plus rien du temps écoulé.
// L'utilisateur choisit, et le libellé de l'axe dit laquelle est active.
const axis = ref('time')

const W = 400
const H = 150
const PAD = { top: 12, right: 12, bottom: 22, left: 46 }

// `at` est le moment où la sauvegarde a réellement eu lieu selon le tag lui-même, pas la date
// à laquelle l'agent l'a envoyée. Tracer sur la date d'ingestion ferait reculer la courbe dès
// qu'une capture est rejouée ou qu'un scan complet arrive après coup.
const points = computed(() =>
  props.history
    .filter(h => h[measure.value.key] != null && (h.at || h.capturedAt))
    .map(h => ({ t: new Date(h.at || h.capturedAt).getTime(), v: h[measure.value.key], raw: h }))
    .sort((a, b) => a.t - b.t))

const scale = computed(() => {
  const p = points.value
  if (p.length < 2) return null
  const byTime = axis.value === 'time'
  const pos = d => (byTime ? d.t : p.indexOf(d))
  const t0 = byTime ? p[0].t : 0
  const t1 = byTime ? p[p.length - 1].t : p.length - 1
  const values = p.map(d => d.v)
  const max = Math.max(...values)
  const min = Math.min(...values)
  // Une série plate garde une bande visible plutôt qu'une division par zéro.
  const lo = min === max ? Math.max(0, min - 1) : min
  const hi = min === max ? max + 1 : max
  const spanT = t1 - t0 || 1
  return {
    at: pos,
    x: t => PAD.left + ((t - t0) / spanT) * (W - PAD.left - PAD.right),
    y: v => H - PAD.bottom - ((v - lo) / (hi - lo)) * (H - PAD.top - PAD.bottom),
    lo,
    hi
  }
})

const path = computed(() => {
  const s = scale.value
  if (!s) return ''
  return points.value
    .map((d, i) => `${i ? 'L' : 'M'}${s.x(s.at(d)).toFixed(1)} ${s.y(d.v).toFixed(1)}`)
    .join(' ')
})

const ticks = computed(() => {
  const s = scale.value
  if (!s) return []
  return [s.lo, (s.lo + s.hi) / 2, s.hi].map(v => ({ v, y: s.y(v) }))
})

const last = computed(() => points.value[points.value.length - 1] || null)

function onMove (event) {
  const s = scale.value
  if (!s) return
  const box = event.currentTarget.getBoundingClientRect()
  const x = ((event.clientX - box.left) / box.width) * W
  let best = null
  for (const d of points.value) {
    const distance = Math.abs(s.x(s.at(d)) - x)
    if (!best || distance < best.distance) best = { d, distance }
  }
  hover.value = best ? best.d : null
}

const day = iso => format.date(iso)
const moment = point => format.dateTime(point.raw.at || point.raw.capturedAt)
</script>

<template>
  <section class="chart">
    <header>
      <h3>{{ t('chart.title') }}</h3>
      <div class="switch">
        <button v-for="m in MEASURES" :key="m.key"
                :class="{ on: measure.key === m.key }" @click="measure = m">
          {{ t(m.labelKey) }}
        </button>
      </div>
    </header>

    <div v-if="points.length > 1" class="axis-switch">
      <button :class="{ on: axis === 'time' }" @click="axis = 'time'"
              :title="t('chart.byDateTitle')">{{ t('chart.byDate') }}</button>
      <button :class="{ on: axis === 'index' }" @click="axis = 'index'"
              :title="t('chart.byReadingTitle')">{{ t('chart.byReading') }}</button>
    </div>

    <p v-if="points.length < 2" class="note">
      {{ points.length === 0 ? t('chart.none') : t('chart.single') }}
    </p>

    <template v-else>
      <div class="plot">
        <svg :viewBox="`0 0 ${W} ${H}`" @mousemove="onMove" @mouseleave="hover = null">
          <!-- Grille et axes volontairement discrets : ils situent, ils ne se regardent pas. -->
          <g class="grid">
            <line v-for="tick in ticks" :key="tick.v" :x1="PAD.left" :x2="W - PAD.right"
                  :y1="tick.y" :y2="tick.y" />
          </g>
          <g class="axis">
            <text v-for="tick in ticks" :key="tick.v" :x="PAD.left - 6" :y="tick.y + 3.5" text-anchor="end">
              {{ format.number(Math.round(tick.v)) }}
            </text>
          </g>

          <path class="line" :d="path" />

          <circle v-for="d in points" :key="d.t" class="dot"
                  :cx="scale.x(scale.at(d))" :cy="scale.y(d.v)" r="2.5" />

          <g v-if="hover">
            <line class="crosshair" :x1="scale.x(scale.at(hover))" :x2="scale.x(scale.at(hover))"
                  :y1="PAD.top" :y2="H - PAD.bottom" />
            <circle class="marker" :cx="scale.x(scale.at(hover))" :cy="scale.y(hover.v)" r="4.5" />
          </g>

          <text class="edge" :x="PAD.left" :y="H - 6" text-anchor="start">
            {{ axis === 'time' ? day(points[0].raw.at || points[0].raw.capturedAt)
              : t('chart.reading', { n: 1 }) }}
          </text>
          <text class="edge" :x="W - PAD.right" :y="H - 6" text-anchor="end">
            {{ axis === 'time' ? day(last.raw.at || last.raw.capturedAt)
              : t('chart.reading', { n: points.length }) }}
          </text>
        </svg>

        <div v-if="hover" class="tip">
          <strong>{{ measure.format(hover.v) }}</strong>
          <span>{{ moment(hover) }}</span>
        </div>
      </div>

      <!-- Un seul libellé direct : la valeur courante. Étiqueter chaque point saturerait
           un graphe de 400 px de large. -->
      <p class="current">
        <strong>{{ t('chart.current', { value: measure.format(last.v) }) }}</strong>
        <span class="dim">· {{ t('chart.readings', { n: points.length }) }}</span>
      </p>
    </template>
  </section>
</template>

<style scoped>
.chart { display: flex; flex-direction: column; gap: 8px; }
header { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
h3 { margin: 0; font-size: 13px; text-transform: uppercase; color: var(--muted); letter-spacing: .04em; }

.switch { display: flex; border: 1px solid var(--line); border-radius: 7px; overflow: hidden; }
.switch button {
  background: var(--panel-2); border: none; padding: 3px 9px;
  font-size: 11.5px; color: var(--muted); cursor: pointer;
}
.switch button.on { background: var(--line); color: var(--text); }

.axis-switch { display: flex; gap: 6px; }
.axis-switch button {
  background: none; border: none; padding: 0; cursor: pointer;
  font-size: 11px; color: var(--muted); text-decoration: underline dotted;
}
.axis-switch button.on { color: var(--text); text-decoration: none; font-weight: 600; }

.plot { position: relative; }
svg { width: 100%; height: auto; display: block; background: var(--panel); border-radius: 8px; }

.grid line { stroke: var(--line); stroke-width: 1; }
.axis text { fill: var(--muted); font-size: 8px; font-family: system-ui, sans-serif; }
.edge { fill: var(--muted); font-size: 8px; font-family: system-ui, sans-serif; }

/* Couleur de série définie par le thème (voir style.css) : chaque thème a la sienne,
   validée contre SA surface — bande de clarté, plancher de chroma, contraste ≥ 3:1. */
.line { fill: none; stroke: var(--series-1); stroke-width: 2; stroke-linejoin: round; stroke-linecap: round; }
.dot { fill: var(--series-1); }
.marker { fill: var(--series-1); stroke: var(--panel); stroke-width: 2; }
.crosshair { stroke: var(--muted); stroke-width: 1; stroke-dasharray: 3 3; }

.tip {
  position: absolute; top: 6px; right: 8px;
  background: var(--bg); border: 1px solid var(--line); border-radius: 7px;
  padding: 4px 8px; display: flex; flex-direction: column; pointer-events: none;
}
.tip strong { font-size: 13px; }
.tip span { font-size: 10.5px; color: var(--muted); }

.current { margin: 0; font-size: 12.5px; }
.note { margin: 0; color: var(--muted); font-size: 12px; }
.dim { color: var(--muted); }
</style>
