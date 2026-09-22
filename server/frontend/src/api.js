async function get (path, params = {}) {
  const query = new URLSearchParams(
    Object.entries(params).filter(([, v]) => v !== null && v !== '' && v !== undefined)
  )
  const suffix = query.toString() ? `?${query}` : ''
  const response = await fetch(`/api${path}${suffix}`)
  if (!response.ok) {
    throw new Error(`${response.status} sur ${path}`)
  }
  return response.json()
}

async function send (method, path, body) {
  const response = await fetch(`/api${path}`, {
    method,
    headers: body === undefined ? {} : { 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body)
  })
  if (!response.ok) {
    // Un refus métier arrive avec un Notice : { code, params }. Le serveur nomme la situation,
    // l'interface — seule à savoir dans quelle langue elle est lue — en fait une phrase.
    const notice = await response.json().catch(() => null)
    const error = new Error(`${response.status} sur ${path}`)
    error.status = response.status
    if (notice?.code) error.notice = notice
    throw error
  }
  return response.json()
}

// Artwork was refreshed from the verified wiki pages on 2026-09-12.  Keep this
// revision in the URL so a browser that displayed the old, lower-resolution
// artwork does not retain it for the image endpoint's six-hour cache lifetime.
const ARTWORK_REVISION = '20260912-wiki-villains'
const artworkUrl = path => `/api/images/${path}?v=${ARTWORK_REVISION}`

export const api = {
  toys: filters => get('/toys', filters),
  leaderboard: params => get('/leaderboard', params),
  toy: (toyId, variantId) => get(`/toys/${toyId}/${variantId}`),
  history: (toyId, variantId) => get(`/toys/${toyId}/${variantId}/history`),
  stats: () => get('/stats'),
  traps: params => get('/traps', params),
  villains: params => get('/villains', params),
  villain: name => get(`/villains/${encodeURIComponent(name)}`),
  villainNames: element => get('/villains/names', { element }),
  nameVillain: (rawId, name, element) =>
    send('PUT', `/villains/raw/${rawId}`, { name, element }),
  villainImageUrl: name => artworkUrl(`villain/name/${encodeURIComponent(name)}`),
  villainImageByRawIdUrl: rawId => artworkUrl(`villain/${rawId}`),
  imageUrl: (toyId, variantId) => artworkUrl(`${toyId}/${variantId}`),

  // Portail. Chaque mutation renvoie la disposition entiere : poser une figurine deja posee
  // la deplace, donc une seule requete peut changer deux emplacements.
  portal: () => get('/portal'),
  placeOnPortal: (index, toyId, variantId) =>
    send('PUT', `/portal/slots/${index}`, { toyId, variantId }),
  clearPortalSlot: index => send('DELETE', `/portal/slots/${index}`),
  clearPortal: () => send('POST', '/portal/clear')
}
