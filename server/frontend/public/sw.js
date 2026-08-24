/*
 * Service worker — coquille applicative uniquement.
 *
 * Règle centrale : **rien sous /api n'est mis en cache.** Un tableau de bord qui
 * afficherait une XP périmée sans le dire serait pire qu'un tableau de bord qui
 * affiche une erreur : la donnée aurait l'air fraîche et serait fausse. Hors
 * ligne, la coquille s'ouvre et les données échouent visiblement.
 *
 * Le reste (HTML, JS, CSS, icônes) est servi depuis le cache quand c'est possible,
 * ce qui rend le démarrage instantané une fois l'application installée.
 */
const SHELL = 'skylanders-shell-v1'
const RUNTIME = 'skylanders-runtime-v1'

self.addEventListener('install', event => {
  event.waitUntil(
    caches.open(SHELL).then(cache => cache.addAll(['/', '/manifest.webmanifest']))
      .then(() => self.skipWaiting())
  )
})

self.addEventListener('activate', event => {
  event.waitUntil(
    caches.keys()
      .then(names => Promise.all(
        names.filter(n => n !== SHELL && n !== RUNTIME).map(n => caches.delete(n))))
      .then(() => self.clients.claim())
  )
})

self.addEventListener('fetch', event => {
  const request = event.request
  if (request.method !== 'GET') return

  const url = new URL(request.url)
  if (url.origin !== self.location.origin) return

  // Données : toujours le réseau, jamais le cache.
  if (url.pathname.startsWith('/api/')) return

  // Navigation : réseau d'abord, pour qu'un redéploiement ne laisse pas une
  // coquille périmée en place ; le cache ne sert que de filet hors ligne.
  if (request.mode === 'navigate') {
    event.respondWith(
      fetch(request)
        .then(response => {
          const copy = response.clone()
          caches.open(SHELL).then(cache => cache.put('/', copy))
          return response
        })
        .catch(() => caches.match('/'))
    )
    return
  }

  // Assets : le cache répond tout de suite, le réseau rafraîchit en arrière-plan.
  // Les noms sont hachés par Vite, donc une nouvelle version n'écrase jamais
  // l'ancienne entrée — elle en crée une autre.
  event.respondWith(
    caches.match(request).then(cached => {
      const network = fetch(request)
        .then(response => {
          if (response.ok) {
            const copy = response.clone()
            caches.open(RUNTIME).then(cache => cache.put(request, copy))
          }
          return response
        })
        .catch(() => cached)
      return cached || network
    })
  )
})
