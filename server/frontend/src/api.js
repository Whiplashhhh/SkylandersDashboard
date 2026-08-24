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

async function put (path, body) {
  const response = await fetch(`/api${path}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body)
  })
  if (!response.ok) {
    throw new Error(`${response.status} sur ${path}`)
  }
  return response.json()
}

export const api = {
  toys: filters => get('/toys', filters),
  leaderboard: params => get('/leaderboard', params),
  toy: (toyId, variantId) => get(`/toys/${toyId}/${variantId}`),
  history: (toyId, variantId) => get(`/toys/${toyId}/${variantId}/history`),
  stats: () => get('/stats'),
  traps: params => get('/traps', params),
  villains: () => get('/villains'),
  nameVillain: (rawId, name, element) => put(`/villains/${rawId}`, { name, element }),
  imageUrl: (toyId, variantId) => `/api/images/${toyId}/${variantId}`
}
