<script setup>
/*
 * Racine de la fenêtre détachée (/portail).
 *
 * Pas de routeur : `main.js` choisit ce composant sur le chemin de la page. Ajouter
 * vue-router pour une seule route de plus serait une dépendance pour rien.
 */
import { onMounted, ref } from 'vue'
import PortalPanel from './components/PortalPanel.vue'
import { applyTheme, loadTheme, watchSystem } from './theme.js'
import { t } from './i18n.js'

const theme = ref(loadTheme())

onMounted(() => {
  // Le thème est déjà posé par le script en tête de index.html ; ce qui manque ici, c'est le
  // suivi des changements de préférence système en cours de route.
  applyTheme(theme.value)
  watchSystem(() => theme.value)
  document.title = t('portal.windowTitle')
})
</script>

<template>
  <PortalPanel detached />
</template>
