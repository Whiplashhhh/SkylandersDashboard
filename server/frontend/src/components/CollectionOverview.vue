<script setup>
import { computed, ref, watch } from 'vue'
import { api } from '../api.js'
import { format, t } from '../i18n.js'

const props = defineProps({ roster: { type: Array, required: true }, stats: { type: Object, default: null } })
defineEmits(['open'])
const recent = computed(() => props.roster.filter(toy => toy.unlocked && toy.lastSavedAt)
  .sort((a, b) => new Date(b.lastSavedAt) - new Date(a.lastSavedAt))[0])
const artFailed = ref(false)
watch(recent, () => { artFailed.value = false })
const playedElements = computed(() => new Set(props.roster.filter(toy => toy.unlocked && toy.element !== 'Inconnu').map(toy => toy.element)).size)
const gameCount = computed(() => new Set(props.roster.flatMap(toy => toy.games)).size)
const figures = computed(() => props.stats?.totals)
</script>

<template>
  <section class="overview" :aria-label="t('dashboard.overview')">
    <div class="welcome">
      <div class="welcome-copy">
        <span class="eyebrow">{{ t('dashboard.eyebrow') }}</span>
        <h2>{{ t('dashboard.welcome') }}<br><span>{{ t('dashboard.welcomeAccent') }}</span></h2>
        <p>{{ t('dashboard.intro') }}</p>
      </div>
      <button v-if="recent" class="recent" @click="$emit('open', recent)">
        <div class="recent-art" aria-hidden="true">
          <img v-if="!artFailed" :src="api.imageUrl(recent.toyId, recent.variantId)" alt="" @error="artFailed = true" />
          <span v-else class="art-fallback">{{ recent.nameFr.slice(0, 2) }}</span>
        </div>
        <div class="recent-copy"><span class="recent-label">{{ t('dashboard.lastPlayed') }}</span>
          <strong>{{ recent.nameFr }}</strong>
          <span>{{ format.date(recent.lastSavedAt) }} <span aria-hidden="true">↗</span></span>
        </div>
      </button>
      <div v-else class="portal-art" aria-hidden="true"><div class="orbit"><div class="orbit-core">✦</div></div></div>
    </div>
    <div class="metrics">
      <div class="metric"><span class="metric-label">{{ t('dashboard.unlocked') }}</span>
        <div class="metric-value">{{ figures ? format.number(figures.unlocked) : '—' }}<span v-if="figures"> / {{ format.number(figures.rosterSize) }}</span></div>
        <span class="metric-caption">{{ t('dashboard.confirmed') }}</span>
      </div>
      <div class="metric"><span class="metric-label">{{ t('dashboard.received') }}</span>
        <div class="metric-value">{{ figures ? format.number(figures.received) : '—' }}</div>
        <span class="metric-caption">{{ t('dashboard.inLibrary') }}</span>
      </div>
      <div class="metric"><span class="metric-label">{{ t('dashboard.elements') }}</span>
        <div class="metric-value">{{ figures ? playedElements : '—' }}</div>
        <span class="metric-caption">{{ t('dashboard.elementsHint') }}</span>
      </div>
      <div class="metric"><span class="metric-label">{{ t('dashboard.games') }}</span>
        <div class="metric-value">{{ figures ? gameCount : '—' }}</div>
        <span class="metric-caption">{{ t('dashboard.gamesHint') }}</span>
      </div>
    </div>
    <p v-if="stats?.byGame?.some(g => !g.parserAvailable)" class="coverage">{{ t('dashboard.coverage') }}</p>
  </section>
</template>

<style scoped>
.overview { border: 1px solid var(--line); border-radius: 16px; overflow: hidden; background: var(--panel); }
.welcome { position: relative; display: flex; align-items: center; justify-content: space-between; gap: 24px; min-height: 172px; padding: 22px 30px; overflow: hidden;
  background: radial-gradient(ellipse at 82% 60%, color-mix(in srgb, var(--accent) 12%, transparent), transparent 55%); }
.welcome-copy { z-index: 1; max-width: 440px; }
.eyebrow { color: var(--accent); font-size: 10px; font-weight: 650; letter-spacing: .18em; text-transform: uppercase; }
h2 { margin: 12px 0; font-size: clamp(28px, 2.7vw, 36px); line-height: 1.1; letter-spacing: -.045em; font-weight: 750; }
h2 span { color: var(--accent); }
.welcome-copy p { color: var(--muted); font-size: 12px; line-height: 1.7; margin: 0; max-width: 340px; }
.recent { display: flex; align-items: center; gap: 20px; max-width: 45%; padding: 0; border: 0; background: none; cursor: pointer; text-align: left; }
.recent-art { width: clamp(100px, 13vw, 180px); aspect-ratio: 1; flex-shrink: 0; border-radius: 50%; background: radial-gradient(circle, color-mix(in srgb, var(--accent) 15%, transparent), transparent 70%); }
.recent-art img { width: 100%; height: 100%; object-fit: contain; filter: drop-shadow(0 12px 16px #0005); transition: transform .25s ease; }
.recent:hover img { transform: translateY(-5px) rotate(-3deg); }
.recent-copy { display: flex; flex-direction: column; gap: 8px; min-width: 0; }
.recent-copy strong { font-size: 19px; letter-spacing: -.03em; }
.recent-copy > span { font-size: 11px; color: var(--muted); }
.recent-copy .recent-label { color: var(--accent); }
.art-fallback { display: grid; place-items: center; height: 100%; font-size: 44px; color: var(--accent); }
.portal-art { width: 220px; height: 140px; display: grid; place-items: center; flex-shrink: 0; }
.orbit { display: grid; place-items: center; width: 128px; height: 128px; border: 1px solid color-mix(in srgb, var(--accent) 40%, transparent); border-radius: 50%; outline: 1px solid color-mix(in srgb, var(--accent) 12%, transparent); outline-offset: 18px; transform: rotate(-20deg); }
.orbit-core { display: grid; place-items: center; width: 90px; height: 90px; border: 1px solid var(--accent); border-radius: 50%; color: var(--accent); font-size: 48px; box-shadow: 0 0 45px color-mix(in srgb, var(--accent) 12%, transparent), inset 0 0 25px color-mix(in srgb, var(--accent) 12%, transparent); }
.metrics { display: grid; grid-template-columns: repeat(4, 1fr); padding: 20px 0; border-top: 1px solid var(--line); }
.metric { padding: 0 26px; border-right: 1px solid var(--line); }
.metric:last-child { border: 0; }
.metric-label, .metric-caption { display: block; font-size: 11px; color: var(--muted); }
.metric-value { font-size: 28px; font-weight: 650; font-variant-numeric: tabular-nums; letter-spacing: -.04em; margin: 7px 0; }
.metric:first-child .metric-value { color: var(--accent); }
.metric-value span { font-size: 14px; color: var(--muted); font-weight: 400; letter-spacing: 0; }
.metric-caption { font-size: 10px; }
.coverage { margin: 0; padding: 0 26px 14px; color: var(--muted); font-size: 10px; }
@media (max-width: 1100px) { .recent { flex-direction: column; gap: 0; min-width: 130px; } .recent-art { width: 110px; } .recent-copy { text-align: center; gap: 3px; } .recent-copy strong { font-size: 15px; } .metric { padding: 0 16px; } }
@media (max-width: 700px) { .welcome { padding: 24px; gap: 12px; } h2 { font-size: 29px; } .portal-art { width: 120px; opacity: .6; } .orbit { width: 100px; height: 100px; } .orbit-core { width: 76px; height: 76px; font-size: 40px; } .metrics { grid-template-columns: repeat(2, 1fr); gap: 20px 0; } .metric:nth-child(2) { border: 0; } }
@media (max-width: 440px) { .welcome { align-items: flex-start; } .welcome-copy p { font-size: 11px; } .recent { min-width: 85px; max-width: 100px; margin-top: 22px; } .recent-art { width: 85px; } .recent-copy strong { font-size: 12px; } .recent-copy > span { font-size: 9px; } .portal-art { position: absolute; right: -40px; bottom: -25px; opacity: .25; } h2 { font-size: 26px; } }
</style>
