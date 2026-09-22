<script setup>
import { ref } from 'vue'
import { connect, disconnect, portal, ready } from '../portal.js'
import { t } from '../i18n.js'
const token = ref('')
async function authenticate() { await connect(token.value); if (portal.authenticated) token.value = '' }
</script>

<template>
  <div class="connection" :class="{ ready }">
    <span role="status"><i aria-hidden="true" />{{ t(`bridge.status.${portal.availability}`) }}</span>
    <button v-if="portal.authenticated" class="disconnect" @click="disconnect">{{ t('bridge.disconnect') }}</button>
    <form v-else @submit.prevent="authenticate">
      <label for="cemu-token">{{ t('bridge.token') }}</label>
      <div><input id="cemu-token" v-model="token" type="password" autocomplete="off" required>
        <button type="submit">{{ t('bridge.connect') }}</button></div>
    </form>
  </div>
</template>

<style scoped>
.connection { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 8px; font-size: 11px; color: var(--muted); }
.connection span { display: flex; align-items: center; gap: 7px; }
i { width: 6px; height: 6px; border-radius: 50%; background: currentColor; }
.ready span { color: var(--accent); }
form { width: 100%; }
label { display: block; margin: 4px 0 6px; }
form > div { display: flex; gap: 6px; }
input, button { font: inherit; border: 1px solid var(--line); border-radius: 7px; padding: 7px; color: var(--text); background: var(--panel); min-width: 0; }
input { flex: 1; }
button { cursor: pointer; }
button.disconnect { padding: 0; border: 0; background: transparent; color: var(--muted); font-size: 10px; }
</style>
