import { nextTick, onUnmounted, watch } from 'vue'

// Keep keyboard navigation inside the open detail panel and restore the opener.
export function useDrawerFocus (isOpen, drawer) {
  let opener
  const focusable = () => [...(drawer.value?.querySelectorAll(
    'button:not(:disabled), a[href], input:not(:disabled), select:not(:disabled), summary, [tabindex="0"]'
  ) || [])].filter(node => node.getClientRects().length)
  function trap (event) {
    if (event.key !== 'Tab' || !drawer.value) return
    const items = focusable()
    if (!items.length) { event.preventDefault(); drawer.value.focus(); return }
    const first = items[0], last = items[items.length - 1]
    if (event.shiftKey && (document.activeElement === first || !drawer.value.contains(document.activeElement))) {
      event.preventDefault(); last.focus()
    } else if (!event.shiftKey && (document.activeElement === last || !drawer.value.contains(document.activeElement))) {
      event.preventDefault(); first.focus()
    }
  }
  watch(isOpen, async open => {
    if (open) {
      opener = document.activeElement
      await nextTick()
      if (!isOpen()) return
      focusable()[0]?.focus()
      document.addEventListener('keydown', trap)
    } else {
      document.removeEventListener('keydown', trap)
      if (opener?.isConnected) opener.focus({ preventScroll: true })
    }
  })
  onUnmounted(() => document.removeEventListener('keydown', trap))
}
