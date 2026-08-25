import { createApp } from 'vue'
import App from './App.vue'
import './style.css'
import { applyDocumentLocale } from './i18n.js'

// Renseigne <html lang> et le titre de l'onglet des le demarrage : les lecteurs d'ecran et
// la cesure dependent du premier, l'onglet du second.
applyDocumentLocale()

createApp(App).mount('#app')

// Enregistré seulement en production : en développement, un service worker qui met en cache
// la coquille masque les rechargements à chaud et fait perdre du temps.
if (import.meta.env.PROD && 'serviceWorker' in navigator) {
  window.addEventListener('load', () => {
    navigator.serviceWorker.register('/sw.js').catch(error => {
      // L'application fonctionne parfaitement sans : l'installation est un confort.
      console.warn('Service worker non enregistré :', error)
    })
  })
}
