<script setup>
import { computed } from 'vue'
import { api } from '../api.js'
import { format, t } from '../i18n.js'
import { slotOf, startDrag } from '../portal.js'

const props = defineProps({
  page: { type: Object, default: null },
  columns: { type: Array, required: true },
  /** Ajoute la colonne « poser sur le portail » et rend les lignes glissables. */
  placeable: { type: Boolean, default: false }
})
const emit = defineEmits(['sort', 'open', 'page', 'place'])

const onPortal = row => slotOf(row) !== null

const GAME_LABELS = {
  SPYROS_ADVENTURE: 'Spyro', GIANTS: 'Giants', SWAP_FORCE: 'Swap Force',
  TRAP_TEAM: 'Trap Team', SUPERCHARGERS: 'SuperChargers', IMAGINATORS: 'Imaginators'
}
// Les valeurs absentes s'affichent toutes pareil : un tiret, jamais un zéro qui ferait
// croire à une mesure.
const DASH = '—'
const duration = s => format.duration(s) ?? DASH
const day = iso => format.date(iso) ?? DASH
const num = v => format.number(v) ?? DASH

const showsProgress = computed(() => props.columns.some(c => c.kind === 'xp'))

// Sur une ligne de piège occupé, l'icône montre le vilain enfermé plutôt que le piège :
// c'est ce que la colonne voisine nomme, les deux se répondent.
function artFor (row) {
  return row.trapEmpty === false && row.villainRawId
    ? api.villainImageByRawIdUrl(row.villainRawId)
    : api.imageUrl(row.toyId, row.variantId)
}
</script>

<template>
  <div class="wrap">
    <table v-if="page">
      <thead>
        <tr>
          <th class="rank">#</th>
          <th class="art"></th>
          <th
            v-for="c in columns" :key="c.key"
            :class="[c.align, { active: page.sort === c.key }]"
            :title="t('table.sortBy', { label: t(c.labelKey) })"
            :aria-sort="page.sort === c.key ? (page.direction === 'asc' ? 'ascending' : 'descending') : 'none'"
            >
            <button class="sort-button" @click="emit('sort', c.key)">
            {{ t(c.labelKey) }}
            <span v-if="page.sort === c.key" class="arrow">{{ page.direction === 'asc' ? '▲' : '▼' }}</span>
            </button>
          </th>
          <!-- Colonne d'action présente seulement quand le portail est ouvert : sinon elle
               n'aurait nulle part où poser. -->
          <th v-if="placeable" class="place"></th>
        </tr>
      </thead>
      <tbody>
        <tr
          v-for="r in page.rows" :key="`${r.toyId}/${r.variantId}`"
          :class="{ locked: !r.unlocked }" :draggable="placeable"
          @dragstart="startDrag($event, r)" @click="emit('open', r)"
        >
          <td class="rank">{{ r.rank }}</td>
          <td class="art">
            <img :src="artFor(r)" :alt="r.nameFr" loading="lazy" />
          </td>
          <td v-for="c in columns" :key="c.key" :class="c.align">
            <template v-if="c.key === 'name'">
              <button class="name row-open" @click.stop="emit('open', r)">{{ r.nameFr }}</button>
              <span v-if="r.nickname" class="nick">« {{ r.nickname }} »</span>
              <span v-if="!r.unlocked" class="lock" :title="t('table.lockedTitle')">🔒</span>
            </template>

            <span v-else-if="c.kind === 'villain'">
              <template v-if="r.trapEmpty === true"><span class="dim">{{ t('table.emptyTrap') }}</span></template>
              <template v-else-if="r.villainRawId == null"><span class="dim">—</span></template>
              <template v-else-if="r.villainName">{{ r.villainName }}</template>
              <!-- Aucun référentiel externe ne donne le nom d'un vilain : il se nomme à la main
                   depuis l'écran Pièges, et le référentiel se remplit tout seul (SPEC.md §7.2). -->
              <span v-else class="unnamed" :title="t('table.unnamedTitle', { id: r.villainRawId })">
                {{ t('table.unnamedVillain', { id: r.villainRawId }) }}
              </span>
            </span>

            <span v-else-if="c.kind === 'xp'">
              <template v-if="r.parsable">
                {{ num(r.xp) }}<span v-if="r.xpCapped" class="cap"
                  :title="t('table.cappedTitle', { ceiling: num(33000) })">*</span>
              </template>
              <span v-else class="dim" :title="t('table.unsupportedTitle')">{{ t('table.na') }}</span>
            </span>

            <span v-else-if="c.kind === 'number'">{{ r.parsable ? num(r[c.field]) : '—' }}</span>
            <span v-else-if="c.kind === 'duration'">{{ r.parsable ? duration(r[c.field]) : '—' }}</span>
            <span v-else-if="c.kind === 'date'" class="dim">{{ day(r[c.field]) }}</span>
            <span v-else-if="c.kind === 'category'" class="dim">{{ t(`categoriesOne.${r.category}`) }}</span>
            <span v-else-if="c.key === 'element'" class="dim">{{ t(`elements.${r.element}`) }}</span>
            <span v-else-if="c.key === 'game'" class="dim">
              {{ r.games.map(g => GAME_LABELS[g] || g).join(', ') }}
            </span>
          </td>

          <td v-if="placeable" class="place">
            <button
              :class="{ on: onPortal(r) }"
              :title="onPortal(r) ? t('portal.alreadyPlaced') : t('portal.place')"
              :aria-label="onPortal(r) ? t('portal.alreadyPlaced') : t('portal.place')"
              @click.stop="emit('place', r)"
            >{{ onPortal(r) ? '✓' : '+' }}</button>
          </td>
        </tr>
      </tbody>
    </table>

    <p v-if="page && !page.rows.length" class="empty">{{ t('table.noResults') }}</p>

    <p v-if="page && page.rows.length && !showsProgress" class="hint">{{ t('table.noProgress') }}</p>

    <nav v-if="page && page.pageCount > 1" class="pager">
      <button :disabled="page.page <= 1" @click="emit('page', page.page - 1)">
        {{ t('table.previous') }}
      </button>
      <span>{{ t('table.pageInfo', { page: page.page, pages: page.pageCount, total: page.total }) }}</span>
      <button :disabled="page.page >= page.pageCount" @click="emit('page', page.page + 1)">
        {{ t('table.next') }}
      </button>
    </nav>
  </div>
</template>

<style scoped>
.wrap { overflow-x: auto; border: 1px solid var(--line); border-radius: 12px; background: var(--panel); }
table { width: 100%; border-collapse: collapse; font-size: 13.5px; }
th, td { padding: 12px 12px; border-bottom: 1px solid var(--line); white-space: nowrap; }
th {
  position: sticky; top: 0; background: var(--panel); z-index: 1;
  color: var(--muted); font-weight: 500; cursor: pointer; user-select: none;
}
th:hover { color: var(--text); }
th.active { color: var(--accent); }
.arrow { font-size: 9px; }
.left { text-align: left; }
.right { text-align: right; font-variant-numeric: tabular-nums; }
.rank { width: 46px; text-align: right; color: var(--muted); font-variant-numeric: tabular-nums; }
.art { width: 44px; }
.art img {
  width: 44px; height: 44px; object-fit: contain;
  border-radius: 6px; background: var(--panel-2); display: block;
}
th.place, td.place { width: 34px; padding-left: 0; padding-right: 8px; }
td.place button {
  width: 32px; height: 32px; line-height: 1;
  background: var(--panel-2); border: 1px solid var(--line); border-radius: 7px;
  color: var(--muted); font-size: 13px; cursor: pointer;
}
td.place button:hover,
td.place button.on {
  color: var(--on-accent); background: var(--accent); border-color: var(--accent);
}
tbody tr { cursor: pointer; }
tbody tr:hover { background: var(--panel-2); }
.locked .art img { filter: grayscale(1) brightness(.6); }
.locked td, .locked .name { color: var(--muted); }
.name { font-weight: 600; }
.nick { font-weight: 400; color: var(--muted); font-size: 12px; margin-left: 5px; }
.lock { margin-left: 6px; font-size: 11px; }
.dim { color: var(--muted); }
.cap { color: var(--warn); }
.unnamed { color: var(--warn); font-style: italic; }
.empty { color: var(--muted); padding: 28px 12px; }
.hint { color: var(--muted); font-size: 12px; padding: 10px 12px 0; }
.pager {
  display: flex; align-items: center; gap: 14px; justify-content: center;
  padding: 14px; color: var(--muted); font-size: 13px;
}
.pager button {
  background: var(--panel-2); border: 1px solid var(--line);
  border-radius: 8px; padding: 6px 14px; cursor: pointer;
}
.pager button:disabled { opacity: .4; cursor: default; }
.sort-button, .row-open { background: none; border: 0; padding: 4px 0; cursor: pointer; font: inherit; color: inherit; text-align: inherit; }
.row-open { font-weight: 650; }
tbody tr:nth-child(-n+3):not(.locked) .rank { color: var(--warn); font-weight: 750; }
</style>
