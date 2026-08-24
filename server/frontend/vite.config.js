import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// Le build atterrit directement dans les ressources statiques du serveur : un seul conteneur,
// un seul port, pas de CORS (decision du 2026-08-22, FORMAT.md §7.3).
export default defineConfig({
  plugins: [vue()],
  build: {
    outDir: '../src/main/resources/static',
    emptyOutDir: true
  },
  server: {
    // `npm run dev` parle au serveur Spring lance a cote.
    proxy: { '/api': 'http://localhost:8080' }
  }
})
