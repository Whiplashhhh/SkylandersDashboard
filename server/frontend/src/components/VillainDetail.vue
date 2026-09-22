<script setup>
import { ref } from 'vue'
import { useDrawerFocus } from '../useDrawerFocus.js'
import { api } from '../api.js'
import { format, t } from '../i18n.js'

const props = defineProps({ villain: { type: Object, default: null } })
const drawer = ref(null)
useDrawerFocus(() => Boolean(props.villain), drawer)

defineEmits(['close'])
</script>

<template>
  <aside v-if="villain" class="drawer" ref="drawer" role="dialog" aria-modal="true" :aria-label="villain.name" tabindex="-1">
    <header>
      <div>
        <h2>{{ villain.name }}</h2>
        <p class="sub">
          {{ t(`elements.${villain.element}`) }}
          <span v-if="villain.doomRaider" class="boss">· {{ t('villains.doomRaider') }}</span>
        </p>
      </div>
      <button class="close" @click="$emit('close')" :aria-label="t('detail.close')">✕</button>
    </header>

    <img class="art" :src="api.villainImageUrl(villain.name)" :alt="villain.name"
         :class="{ locked: !villain.captured }" />

    <dl class="facts">
      <dt>{{ t('detail.status') }}</dt>
      <dd>{{ villain.captured ? t('villains.captured') : t('villains.notCaptured') }}</dd>
      <dt v-if="villain.rawId != null">{{ t('villains.rawId') }}</dt>
      <dd v-if="villain.rawId != null">{{ villain.rawId }}</dd>
      <dt v-if="villain.capturedAt">{{ t('villains.capturedAt') }}</dt>
      <dd v-if="villain.capturedAt">{{ format.dateTime(villain.capturedAt) }}</dd>
    </dl>

    <p v-if="villain.summary" class="story">{{ villain.summary }}</p>
    <p v-else class="story dim">{{ t('villains.noStory') }}</p>

    <a v-if="villain.wikiUrl" class="wiki" :href="villain.wikiUrl" target="_blank"
       rel="noopener noreferrer">{{ t('detail.wiki') }} ↗</a>
    <!-- Le texte vient du wiki Fandom, sous CC BY-SA : le lien ci-dessus porte l'attribution. -->
    <p v-if="villain.summary" class="credit">{{ t('villains.source') }}</p>
  </aside>
</template>

<style scoped>
.drawer {
  position: fixed; top: 0; right: 0; bottom: 0;
  width: min(400px, 100vw);
  background: var(--panel);
  border-left: 1px solid var(--line);
  padding: 28px; overflow-y: auto; z-index: 20;
  display: flex; flex-direction: column; gap: 20px;
}
header { display: flex; justify-content: space-between; align-items: flex-start; gap: 10px; }
h2 { margin: 0; font-size: 26px; letter-spacing: -.04em; }
.sub { margin: 2px 0 0; color: var(--muted); font-size: 12px; }
.boss { color: var(--warn); font-weight: 600; }
.close { background: none; border: none; color: var(--muted); font-size: 18px; cursor: pointer; min-width: 36px; min-height: 36px; }

.art { width: 200px; max-width: 100%; align-self: center; border-radius: 10px; background: var(--panel-2); }
/* Jamais capturé : même image, simplement grisée — jamais une silhouette mystère. */
.art.locked { filter: grayscale(1) brightness(.62); }

.facts { display: grid; grid-template-columns: auto 1fr; gap: 10px 14px; margin: 0; font-size: 13px; }
.facts dt { color: var(--muted); }
.facts dd { margin: 0; }

.story { margin: 0; font-size: 13.5px; line-height: 1.5; }
.story.dim { color: var(--muted); font-style: italic; }

.wiki {
  align-self: flex-start;
  background: var(--panel-2); border: 1px solid var(--line); border-radius: 8px;
  padding: 6px 14px; font-size: 13px; color: var(--text); text-decoration: none;
}
.wiki:hover { border-color: var(--accent); color: var(--accent); }
.credit { margin: 0; font-size: 11px; color: var(--muted); }
</style>
