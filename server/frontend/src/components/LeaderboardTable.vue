<script setup>
import { computed } from 'vue'

const props = defineProps({
  page: { type: Object, default: null },
  columns: { type: Array, required: true }
})
const emit = defineEmits(['sort', 'open', 'page'])

const GAME_LABELS = {
  SPYROS_ADVENTURE: 'Spyro', GIANTS: 'Giants', SWAP_FORCE: 'Swap Force',
  TRAP_TEAM: 'Trap Team', SUPERCHARGERS: 'SuperChargers', IMAGINATORS: 'Imaginators'
}
const CATEGORY_LABELS = {
  CHARACTER: 'Personnage', GIANT: 'Géant', TRAP: 'Piège', VEHICLE: 'Véhicule',
  SIDEKICK: 'Acolyte', MINI: 'Mini', ITEM: 'Objet', ADVENTURE_PACK: 'Pack Aventure',
  CHEST: 'Coffre', CREATION_CRYSTAL: 'Cristal', UNKNOWN: 'Autre'
}

function duration (s) {
  if (s == null) return '—'
  const h = Math.floor(s / 3600); const m = Math.floor((s % 3600) / 60)
  return h > 0 ? `${h} h ${String(m).padStart(2, '0')}` : `${m} min`
}
function day (iso) {
  return iso ? new Date(iso).toLocaleDateString('fr-FR',
    { day: '2-digit', month: 'short', year: 'numeric' }) : '—'
}
function num (v) { return v == null ? '—' : v.toLocaleString('fr-FR') }

const showsProgress = computed(() => props.columns.some(c => c.kind === 'xp'))

// Sur une ligne de piège occupé, l'icône montre le vilain enfermé plutôt que le piège :
// c'est ce que la colonne voisine nomme, les deux se répondent.
function artFor (row) {
  return row.trapEmpty === false && row.villainRawId
    ? `/api/images/villain/${row.villainRawId}`
    : `/api/images/${row.toyId}/${row.variantId}`
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
            :title="`Trier par ${c.label}`"
            @click="emit('sort', c.key)"
          >
            {{ c.label }}
            <span v-if="page.sort === c.key" class="arrow">{{ page.direction === 'asc' ? '▲' : '▼' }}</span>
          </th>
        </tr>
      </thead>
      <tbody>
        <tr
          v-for="r in page.rows" :key="`${r.toyId}/${r.variantId}`"
          :class="{ locked: !r.unlocked }" @click="emit('open', r)"
        >
          <td class="rank">{{ r.rank }}</td>
          <td class="art">
            <img :src="artFor(r)" :alt="r.nameFr" loading="lazy" />
          </td>
          <td v-for="c in columns" :key="c.key" :class="c.align">
            <template v-if="c.key === 'name'">
              <span class="name">{{ r.nameFr }}</span>
              <span v-if="r.nickname" class="nick">« {{ r.nickname }} »</span>
              <span v-if="!r.unlocked" class="lock" title="Jamais posée sur le portail">🔒</span>
            </template>

            <span v-else-if="c.kind === 'villain'">
              <template v-if="r.trapEmpty === true"><span class="dim">vide</span></template>
              <template v-else-if="r.villainRawId == null"><span class="dim">—</span></template>
              <template v-else-if="r.villainName">{{ r.villainName }}</template>
              <!-- Aucun référentiel externe ne donne le nom d'un vilain : il se nomme à la main
                   depuis l'écran Pièges, et le référentiel se remplit tout seul (SPEC.md §7.2). -->
              <span v-else class="unnamed" :title="`Identifiant ${r.villainRawId} — à nommer depuis l'écran Pièges`">
                Vilain #{{ r.villainRawId }}
              </span>
            </span>

            <span v-else-if="c.kind === 'xp'">
              <template v-if="r.parsable">
                {{ num(r.xp) }}<span v-if="r.xpCapped" class="cap"
                  title="Champ saturé à 33 000, la valeur réelle est peut-être plus haute">*</span>
              </template>
              <span v-else class="dim" title="Aucun parseur de sauvegarde pour ce jeu">n/d</span>
            </span>

            <span v-else-if="c.kind === 'number'">{{ r.parsable ? num(r[c.field]) : '—' }}</span>
            <span v-else-if="c.kind === 'duration'">{{ r.parsable ? duration(r[c.field]) : '—' }}</span>
            <span v-else-if="c.kind === 'date'" class="dim">{{ day(r[c.field]) }}</span>
            <span v-else-if="c.kind === 'category'" class="dim">{{ CATEGORY_LABELS[r.category] || r.category }}</span>
            <span v-else-if="c.key === 'element'" class="dim">{{ r.element }}</span>
            <span v-else-if="c.key === 'game'" class="dim">
              {{ r.games.map(g => GAME_LABELS[g] || g).join(', ') }}
            </span>
          </td>
        </tr>
      </tbody>
    </table>

    <p v-if="page && !page.rows.length" class="empty">Aucune figurine ne correspond à ces filtres.</p>

    <p v-if="page && page.rows.length && !showsProgress" class="hint">
      Ces entrées ne portent pas de progression : les colonnes affichées sont celles qui ont un sens
      pour elles.
    </p>

    <nav v-if="page && page.pageCount > 1" class="pager">
      <button :disabled="page.page <= 1" @click="emit('page', page.page - 1)">Précédent</button>
      <span>Page {{ page.page }} / {{ page.pageCount }} — {{ page.total }} entrées</span>
      <button :disabled="page.page >= page.pageCount" @click="emit('page', page.page + 1)">Suivant</button>
    </nav>
  </div>
</template>

<style scoped>
.wrap { overflow-x: auto; }
table { width: 100%; border-collapse: collapse; font-size: 13.5px; }
th, td { padding: 7px 10px; border-bottom: 1px solid var(--line); white-space: nowrap; }
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
  width: 34px; height: 34px; object-fit: contain;
  border-radius: 6px; background: var(--panel-2); display: block;
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
</style>
