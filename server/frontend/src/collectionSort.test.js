import { test } from 'node:test'
import assert from 'node:assert/strict'
import { sortCollection } from './collectionSort.js'

const toys = [
  { toyId: 1, variantId: 0, nameFr: 'Éclair', unlocked: true, lastSavedAt: '2026-09-10T12:00:00Z', originGame: 'GIANTS', element: 'Feu', category: 'GIANT' },
  { toyId: 2, variantId: 0, nameFr: 'Alpha', unlocked: false, lastSavedAt: '2026-09-12T12:00:00Z', originGame: 'TRAP_TEAM', element: 'Eau', category: 'TRAP' },
  { toyId: 3, variantId: 0, nameFr: 'Zèbre', unlocked: true, lastSavedAt: '2026-09-11T12:00:00Z', originGame: 'SPYROS_ADVENTURE', element: 'Air', category: 'CHARACTER' }
]
const ids = (key, direction) => sortCollection(toys, key, direction, 'fr', key => key).map(t => t.toyId)
test('recent play ignores unplayed file dates, keeps absent dates last in both directions', () => {
  assert.deepEqual(ids('recent', 'desc'), [3, 1, 2])
  assert.deepEqual(ids('recent', 'asc'), [1, 3, 2])
  assert.deepEqual(toys.map(t => t.toyId), [1, 2, 3])
})
test('alphabetical, element, category and chronological game sorts reverse correctly', () => {
  for (const [key, expected] of [['name', [2, 1, 3]], ['element', [3, 2, 1]], ['category', [3, 1, 2]], ['game', [3, 1, 2]]]) {
    assert.deepEqual(ids(key, 'asc'), expected)
    assert.deepEqual(ids(key, 'desc'), [...expected].reverse())
  }
})
