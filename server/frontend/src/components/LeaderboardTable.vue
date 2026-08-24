<script setup>
defineProps({
  page: { type: Object, default: null },
  loading: { type: Boolean, default: false }
})
const emit = defineEmits(['sort', 'open', 'page'])

// Les colonnes ne portent que des données lues sur le tag. Vie, vitesse, armure et chance sont
// des attributs du personnage, identiques pour toutes les copies, et ne sont écrits nulle part
// sur la figurine — les proposer au tri afficherait une donnée qu'on n'a pas.
const COLUMNS = [
  { key: 'name', label: 'Nom', align: 'left' },
  { key: 'element', label: 'Élément', align: 'left' },
  { key: 'game', label: 'Jeu', align: 'left' },
  { key: 'xp', label: 'XP', align: 'right' },
  { key: 'gold', label: 'Or', align: 'right' },
  { key: 'upgrades', label: 'Améliorations', align: 'right' },
  { key: 'playtime', label: 'Temps de jeu', align: 'right' },
  { key: 'lastSaved', label: 'Dernière partie', align: 'right' }
]

const LABELS = {
  SPYROS_ADVENTURE: 'Spyro', GIANTS: 'Giants', SWAP_FORCE: 'Swap Force',
  TRAP_TEAM: 'Trap Team', SUPERCHARGERS: 'SuperChargers', IMAGINATORS: 'Imaginators'
}

function duration (s) {
  if (s == null) return '—'
  const h = Math.floor(s / 3600); const m = Math.floor((s % 3600) / 60)
  return h > 0 ? `${h} h ${String(m).padStart(2, '0')}` : `${m} min`
}
function day (iso) {
  return iso ? new Date(iso).toLocaleDateString('fr-FR', { day: '2-digit', month: 'short', year: 'numeric' }) : '—'
}
function num (v) { return v == null ? '—' : v.toLocaleString('fr-FR') }
</script>

<template>
  <div class="wrap">
    <table v-if="page">
      <thead>
        <tr>
          <th class="rank">#</th>
          <th class="art"></th>
          <th
            v-for="c in COLUMNS" :key="c.key"
            :class="[c.align, { active: page.sort === c.key }]"
            @click="emit('sort', c.key)"
            :title="`Trier par ${c.label}`"
          >
            {{ c.label }}
            <span class="arrow" v-if="page.sort === c.key">{{ page.direction === 'asc' ? '▲' : '▼' }}</span>
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
            <img :src="`/api/images/${r.toyId}/${r.variantId}`" :alt="r.nameFr" loading="lazy" />
          </td>
          <td class="left name">
            {{ r.nameFr }}
            <span v-if="r.nickname" class="nick">« {{ r.nickname }} »</span>
            <span v-if="!r.unlocked" class="lock" title="Jamais posée sur le portail">🔒</span>
          </td>
          <td class="left dim">{{ r.element }}</td>
          <td class="left dim">{{ r.games.map(g => LABELS[g] || g).join(', ') }}</td>
          <td class="right">
            <template v-if="r.parsable">
              {{ num(r.xp) }}
              <!-- FORMAT.md §8.7 : le champ sature ; on le dit plutôt que de présenter
                   la valeur comme un fait. -->
              <span v-if="r.xpCapped" class="cap" title="Champ saturé, valeur réelle peut-être plus haute">*</span>
            </template>
            <span v-else class="dim" title="Aucun parseur de sauvegarde pour ce jeu">n/d</span>
          </td>
          <td class="right">{{ r.parsable ? num(r.gold) : '—' }}</td>
          <td class="right">{{ r.parsable ? num(r.upgradesCount) : '—' }}</td>
          <td class="right">{{ r.parsable ? duration(r.playtimeSeconds) : '—' }}</td>
          <td class="right dim">{{ day(r.lastSavedAt) }}</td>
        </tr>
      </tbody>
    </table>

    <p v-if="page && !page.rows.length" class="empty">Aucune figurine ne correspond à ces filtres.</p>

    <nav v-if="page && page.pageCount > 1" class="pager">
      <button :disabled="page.page <= 1" @click="emit('page', page.page - 1)">Précédent</button>
      <span>Page {{ page.page }} / {{ page.pageCount }} — {{ page.total }} figurines</span>
      <button :disabled="page.page >= page.pageCount" @click="emit('page', page.page + 1)">Suivant</button>
    </nav>
  </div>
</template>

<style scoped>
.wrap { overflow-x: auto; }
table { width: 100%; border-collapse: collapse; font-size: 13.5px; }
th, td { padding: 7px 10px; border-bottom: 1px solid var(--line); white-space: nowrap; }
th {
  position: sticky; top: 0; background: var(--panel);
  color: var(--muted); font-weight: 500; cursor: pointer; user-select: none;
}
th:hover { color: var(--text); }
th.active { color: var(--accent); }
.arrow { font-size: 9px; }
.left, td.left { text-align: left; }
.right, td.right { text-align: right; font-variant-numeric: tabular-nums; }
.rank { width: 46px; text-align: right; color: var(--muted); font-variant-numeric: tabular-nums; }
.art { width: 44px; }
.art img { width: 34px; height: 34px; object-fit: contain; border-radius: 6px; background: var(--panel-2); display: block; }
tbody tr { cursor: pointer; }
tbody tr:hover { background: var(--panel-2); }
.locked .art img { filter: grayscale(1) brightness(.6); }
.locked .name, .locked td { color: var(--muted); }
.name { font-weight: 600; }
.nick { font-weight: 400; color: var(--muted); font-size: 12px; margin-left: 5px; }
.lock { margin-left: 6px; font-size: 11px; }
.dim { color: var(--muted); }
.cap { color: #f0c674; }
.empty { color: var(--muted); padding: 28px 12px; }
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
