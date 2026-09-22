// Shared live portal controller for collection buttons, drag/drop and the detached window.
// Slot contents come exclusively from Cemu observations; placement never changes unlocked.
import { computed, reactive } from 'vue'
import { api } from './api.js'

export const DRAG_TYPE = 'application/x-skylander'
export const portal = reactive({
  slotCount: 9, trapSlotIndex: 9, slots: [], loaded: false, busy: false, error: null,
  availability: 'LOCKED', authenticated: false, state: null, files: [], layoutRevision: 0,
  pendingSlot: null, outcome: null, copyChoice: null
})
export const occupiedCount = computed(() => portal.slots.filter(s => s.toy && !s.trapSlot).length)
export const figurineSlots = computed(() => portal.slots.filter(s => !s.trapSlot))
export const trapSlot = computed(() => portal.slots.find(s => s.trapSlot) ?? null)
export const ready = computed(() => portal.authenticated && portal.availability === 'READY')
export const firstFreeIndex = computed(() => portal.slots.find(s => !s.toy && !s.beyondGrid && !s.trapSlot)?.index ?? null)
export function targetSlotFor(toy) { return toy?.category === 'TRAP' ? 9 : firstFreeIndex.value }
export function slotOf(toy) {
  return portal.slots.find(s => s.toy?.toyId === toy?.toyId && s.toy?.variantId === toy?.variantId)?.index ?? null
}

let token = sessionStorage.getItem('cemu-control-token') ?? ''
let started = false, generation = 0, sending = false, observedOutcome = null
let choices = {}
try { choices = JSON.parse(sessionStorage.getItem('cemu-copy-choices') ?? '{}') } catch { /* Discard obsolete preferences. */ }
const channel = typeof BroadcastChannel === 'function' ? new BroadcastChannel('portal-sync') : null
channel?.addEventListener('message', event => {
  if (event.data?.type === 'auth-request' && token) channel.postMessage({ type: 'auth', token })
  if (event.data?.type === 'auth' && typeof event.data.token === 'string' && event.data.token !== token) {
    token = event.data.token
    generation++
    if (token) sessionStorage.setItem('cemu-control-token', token)
    else { sessionStorage.removeItem('cemu-control-token'); portal.authenticated = false; portal.availability = 'LOCKED' }
    load()
  }
  if (event.data?.type === 'copy-selected') {
    choices[event.data.key] = event.data.fileId
    sessionStorage.setItem('cemu-copy-choices', JSON.stringify(choices))
  }
  if (event.data?.type === 'copy-choice') portal.copyChoice = event.data.choice
  // Only invalidate observations. Receiving a broadcast never issues a command.
  if (event.data?.type === 'portal-changed' && !sending) refresh()
})
channel?.postMessage({ type: 'auth-request' })

async function call(body) {
  const response = await fetch('/api/bridge/portal', {
    method: body ? 'POST' : 'GET', signal: AbortSignal.timeout(4000),
    headers: { Authorization: `Bearer ${token}`, ...(body ? { 'Content-Type': 'application/json' } : {}) },
    body: body ? JSON.stringify(body) : undefined
  })
  const data = await response.json()
  if (!response.ok) throw new Error(data.error ?? 'NETWORK_ERROR')
  return data
}
function apply(view) {
  Object.assign(portal, view, { loaded: true, authenticated: true })
  const nextOutcome = view.outcome ? `${view.outcome.commandId}/${view.outcome.status}` : null
  if (nextOutcome !== observedOutcome) {
    observedOutcome = nextOutcome
    portal.error = view.outcome && ['REJECTED', 'UNKNOWN', 'EXPIRED'].includes(view.outcome.status)
      ? { key: `bridge.errors.${view.outcome.error || 'STALE_STATE'}` } : null
  }
}
function fail(error) {
  const code = /^[A-Z_]+$/.test(error.message) ? error.message : 'NETWORK_ERROR'
  portal.error = { key: `bridge.errors.${code}` }
}
async function refresh() {
  if (!token || sending) return
  const current = ++generation
  try {
    const view = await call()
    if (current !== generation) return
    apply(view)
  } catch (error) {
    if (current !== generation) return
    portal.availability = error.message === 'PORTAL_AUTH_REQUIRED' ? 'LOCKED' : 'SERVER_UNAVAILABLE'
    if (portal.availability === 'LOCKED') portal.authenticated = false
    fail(new Error(error.message === 'PORTAL_AUTH_REQUIRED' ? error.message : 'NETWORK_ERROR'))
  }
}
async function poll() {
  await refresh()
  setTimeout(poll, 400)
}
export async function load() {
  if (!portal.loaded) {
    portal.slots = Array.from({ length: 10 }, (_, index) => ({ index, toy: null, trapSlot: index === 9, beyondGrid: false }))
    portal.loaded = true
  }
  if (!started) { started = true; poll() }
  else await refresh()
}
export async function connect(value) {
  token = value.trim(); generation++; portal.error = null
  await load()
  await refresh()
  if (portal.authenticated) {
    sessionStorage.setItem('cemu-control-token', token)
    channel?.postMessage({ type: 'auth', token })
  }
}
export function disconnect() {
  generation++; token = ''; portal.authenticated = false; portal.availability = 'LOCKED'
  portal.copyChoice = null
  sessionStorage.removeItem('cemu-control-token')
  channel?.postMessage({ type: 'auth', token: '' })
}
async function mutate(command, fields = {}, preconditions = {}) {
  if (!ready.value) { fail(new Error('CEMU_UNAVAILABLE')); return false }
  if (sending || portal.busy) { fail(new Error('COMMAND_IN_PROGRESS')); return false }
  sending = true; portal.busy = true; portal.pendingSlot = fields.index ?? null; portal.error = null; generation++
  const current = generation
  try {
    const view = await call({ commandId: crypto.randomUUID(), command, epoch: portal.state.epoch,
      expectedRevision: portal.state.revision, layoutRevision: portal.layoutRevision, ...preconditions, ...fields })
    if (current !== generation) return false
    apply(view)
    channel?.postMessage({ type: 'portal-changed' })
    return !['REJECTED', 'UNKNOWN', 'EXPIRED'].includes(view.outcome?.status)
  } catch (error) {
    if (current === generation) fail(error)
    return false
  } finally {
    sending = false
    if (current === generation) await refresh()
  }
}

export async function place(toy, index = null, fileId = null, preconditions = null) {
  if (!ready.value) { fail(new Error('CEMU_UNAVAILABLE')); return false }
  if (portal.busy) { fail(new Error('COMMAND_IN_PROGRESS')); return false }
  preconditions ??= { epoch: portal.state.epoch, expectedRevision: portal.state.revision, layoutRevision: portal.layoutRevision }
  try {
    // Metadata and exact paths originate from server-side ingestion, never filename guesses.
    const detail = await api.toy(toy.toyId, toy.variantId)
    const target = index ?? (slotOf(detail.toy) ?? targetSlotFor(detail.toy))
    if (target === null) { portal.error = { key: 'portal.full' }; return false }
    if ((target === 9) !== (detail.toy.category === 'TRAP')) { fail(new Error('WRONG_SLOT_TYPE')); return false }
    const candidates = portal.files.filter(file => detail.files.includes(file.relativePath))
    const source = Number.isInteger(toy.fromIndex) ? portal.slots.find(s => s.index === toy.fromIndex) : null
    if (source && (source.toy?.toyId !== toy.toyId || source.toy?.variantId !== toy.variantId)) {
      fail(new Error('STALE_STATE')); return false
    }
    const key = `${toy.toyId}/${toy.variantId}`
    const selected = fileId ?? source?.fileId ?? (candidates.length === 1 ? candidates[0].id : choices[key])
    if (!candidates.length) { fail(new Error('FILE_UNAVAILABLE')); return false }
    if (!selected || !candidates.some(file => file.id === selected)) {
      portal.copyChoice = { toy: detail.toy, index: target, files: candidates, preconditions }
      channel?.postMessage({ type: 'copy-choice', choice: JSON.parse(JSON.stringify(portal.copyChoice)) })
      return false
    }
    choices[key] = selected
    sessionStorage.setItem('cemu-copy-choices', JSON.stringify(choices))
    channel?.postMessage({ type: 'copy-selected', key, fileId: selected })
    dismissCopyChoice()
    return mutate('place', { index: target, toyId: toy.toyId, variantId: toy.variantId, fileId: selected }, preconditions)
  } catch { fail(new Error('NETWORK_ERROR')); return false }
}
export function clearSlot(index) { return mutate('remove', { index }) }
export function clearAll() { return mutate('clear') }
export function dismissCopyChoice() { portal.copyChoice = null; channel?.postMessage({ type: 'copy-choice', choice: null }) }
export function dismissError() { portal.error = null }
export function startDrag(event, toy, fromIndex = null) {
  event.dataTransfer.setData(DRAG_TYPE, JSON.stringify({ toyId: toy.toyId, variantId: toy.variantId, fromIndex }))
  event.dataTransfer.setData('text/plain', toy.nameFr ?? toy.trapName ?? '')
  event.dataTransfer.effectAllowed = 'move'
}
export function readDrag(event) {
  try {
    const value = JSON.parse(event.dataTransfer?.getData(DRAG_TYPE) ?? '')
    return Number.isInteger(value?.toyId) && Number.isInteger(value?.variantId) ? value : null
  } catch { return null }
}
