import { api } from './api.js'

export function toyArtwork(toy, trap) {
  if (toy.category === 'TRAP' && trap?.empty === false) {
    return trap.villain?.name
      ? api.villainImageUrl(trap.villain.name)
      : api.villainImageByRawIdUrl(trap.villainRawId)
  }
  return api.imageUrl(toy.toyId, toy.variantId)
}
