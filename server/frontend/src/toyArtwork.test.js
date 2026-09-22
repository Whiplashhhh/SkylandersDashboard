import { test } from 'node:test'
import assert from 'node:assert/strict'
import { toyArtwork } from './toyArtwork.js'
import { api } from './api.js'

test('capture, replacement and emptying switch artwork without changing trap identity', () => {
  const trap = { category: 'TRAP', toyId: 211, variantId: 12289 }
  assert.equal(toyArtwork(trap, { empty: true }), api.imageUrl(211, 12289))
  assert.equal(toyArtwork(trap, { empty: false, villain: { name: 'Dreamcatcher' } }), api.villainImageUrl('Dreamcatcher'))
  assert.equal(toyArtwork(trap, { empty: false, villain: { name: 'Bomb Shell' } }), api.villainImageUrl('Bomb Shell'))
  assert.equal(toyArtwork(trap, { empty: false, villainRawId: 999 }), api.villainImageByRawIdUrl(999))
  assert.equal(toyArtwork(trap, { empty: true, villain: { name: 'Dreamcatcher' } }), api.imageUrl(211, 12289))
  assert.equal(toyArtwork({ ...trap, category: 'CHARACTER' }, { empty: false, villainRawId: 999 }), api.imageUrl(211, 12289))
})
