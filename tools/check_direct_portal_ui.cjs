/** Isolated browser check: real built Vue UI, controlled HTTP observations, no Cemu or saves. */
const assert = require('node:assert/strict')
const fs = require('node:fs/promises')
const http = require('node:http')
const path = require('node:path')
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright')
const root = path.resolve(__dirname, '../server/src/main/resources/static')
const models = Array.from({ length: 7 }, (_, i) => ({ toyId: i + 1, variantId: 0,
  nameFr: `Skylander ${i + 1}`, nameEn: `Skylander ${i + 1}`, category: i === 6 ? 'TRAP' : 'FIGURE',
  element: 'FIRE', unlocked: false, games: [], received: true }))
const files = models.map((toy, i) => ({ id: String(i + 1).repeat(64), relativePath: `Test/${toy.nameFr}.sky` }))
files.push({ id: 'a'.repeat(64), relativePath: 'Other/Skylander 6.sky' })
const slots = () => Array.from({ length: 10 }, (_, index) => ({ index, toy: null, trapSlot: index === 9, beyondGrid: false }))
const view = { availability: 'READY', state: { epoch: 'test', revision: 0, capacity: 16, enabled: true, slots: [] },
  files, slotCount: 9, trapSlotIndex: 9, slots: slots(), layoutRevision: 0, busy: false, pendingSlot: null, outcome: null }
const commands = [], errors = []
let pending
function confirm() {
  assert(pending)
  const cmd = pending; pending = null
  if (cmd.command === 'place') {
    view.slots[cmd.index] = { ...view.slots[cmd.index], toy: models.find(t => t.toyId === cmd.toyId), fileId: cmd.fileId, cemuIndex: 13 }
  } else if (cmd.command === 'remove') {
    view.slots[cmd.index] = { ...view.slots[cmd.index], toy: null, fileId: null, cemuIndex: null }
  } else view.slots = slots()
  view.state.revision++; view.layoutRevision++; view.busy = false; view.pendingSlot = null
  view.outcome = { commandId: cmd.commandId, status: 'APPLIED', error: null }
}
const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, 'http://localhost')
  const json = value => { res.setHeader('Content-Type', 'application/json'); res.end(JSON.stringify(value)) }
  if (url.pathname === '/api/bridge/portal') {
    if (req.headers.authorization !== 'Bearer ui-test-only') { res.statusCode = 401; return json({ error: 'PORTAL_AUTH_REQUIRED' }) }
    if (req.method === 'POST') {
      let raw = ''; for await (const chunk of req) raw += chunk
      const cmd = JSON.parse(raw)
      assert.equal(view.busy, false); assert.equal(cmd.expectedRevision, view.state.revision)
      assert.equal(cmd.layoutRevision, view.layoutRevision)
      commands.push(cmd); pending = cmd; view.busy = true; view.pendingSlot = cmd.index
      view.outcome = { commandId: cmd.commandId, status: 'PENDING', error: null }
    }
    return json(view)
  }
  if (url.pathname === '/api/toys') {
    const query = url.searchParams.get('search') || ''
    return json(models.filter(t => t.nameFr.toLowerCase().includes(query.toLowerCase())))
  }
  const detail = url.pathname.match(/^\/api\/toys\/(\d+)\/0$/)
  if (detail) {
    const toy = models.find(t => t.toyId === Number(detail[1]))
    return json({ toy, files: [files[toy.toyId - 1].relativePath, ...(toy.toyId === 6 ? [files[7].relativePath] : [])] })
  }
  if (url.pathname.startsWith('/api/images/')) {
    res.setHeader('Content-Type', 'image/svg+xml')
    return res.end('<svg xmlns="http://www.w3.org/2000/svg" width="80" height="80"><circle cx="40" cy="40" r="30" fill="#44bea5"/></svg>')
  }
  if (url.pathname.startsWith('/api/')) {
    errors.push(`Unexpected API ${req.method} ${url.pathname}`); res.statusCode = 404; return json({})
  }
  try {
    const filename = url.pathname === '/portail' || url.pathname === '/' ? 'index.html' : url.pathname.slice(1)
    const content = await fs.readFile(path.join(root, filename))
    res.setHeader('Content-Type', filename.endsWith('.js') ? 'text/javascript' : filename.endsWith('.css') ? 'text/css' : 'text/html')
    res.end(content)
  } catch { res.statusCode = 404; res.end() }
})
const waitFor = async predicate => {
  for (let i = 0; i < 100; i++) { if (await predicate()) return; await new Promise(r => setTimeout(r, 50)) }
  throw new Error('Timed out waiting for expected observation')
}
;(async () => {
  await new Promise(r => server.listen(0, '127.0.0.1', r))
  const url = `http://127.0.0.1:${server.address().port}/portail`
  const browser = await chromium.launch({ executablePath: process.env.CHROMIUM_PATH || '/bin/chromium', headless: true })
  try {
    const context = await browser.newContext({ locale: 'fr-FR', viewport: { width: 440, height: 900 }, reducedMotion: 'reduce', serviceWorkers: 'block' })
    const page = await context.newPage(); page.on('pageerror', error => errors.push(error.message))
    await page.goto(url)
    await page.locator('#cemu-token').fill('ui-test-only')
    await page.getByRole('button', { name: 'Connecter', exact: true }).click()
    await page.getByText('Connecté à Cemu', { exact: true }).waitFor()
    const other = await context.newPage(); other.on('pageerror', error => errors.push(error.message)); await other.goto(url)
    await other.getByText('Connecté à Cemu', { exact: true }).waitFor() // BroadcastChannel auth sharing.
    assert.equal(await page.getByText('Charger dans Cemu', { exact: true }).count(), 0)
    assert.equal(await page.locator('select').count(), 0)
    await page.locator('#portal-search').fill('Skylander')
    await waitFor(async () => await page.locator('.results button').count() === 6)
    const boxes = await page.locator('.results button').evaluateAll(nodes => nodes.map(n => ({ x: n.offsetLeft, y: n.offsetTop })))
    assert.equal(new Set(boxes.map(b => b.y)).size, 2)
    assert.equal(new Set(boxes.map(b => b.x)).size, 3)
    await page.locator('.results button').first().click()
    await waitFor(() => commands.length === 1)
    assert.equal(commands[0].command, 'place'); assert.equal(commands[0].index, 0)
    assert.equal(await page.locator('[data-slot-index="0"] .art').count(), 0) // No optimistic occupied slot.
    confirm()
    await page.locator('[data-slot-index="0"] .art').waitFor()
    await other.locator('[data-slot-index="0"] .art').waitFor()
    // Drop a collection payload on graphical slot 7, independently of Cemu's internal slot.
    const transfer = await page.evaluateHandle(() => {
      const dt = new DataTransfer(); dt.setData('application/x-skylander', JSON.stringify({ toyId: 2, variantId: 0, fromIndex: null })); return dt
    })
    await page.locator('[data-slot-index="7"]').dispatchEvent('drop', { dataTransfer: transfer })
    await waitFor(() => commands.length === 2); assert.equal(commands[1].index, 7); confirm()
    await page.locator('[data-slot-index="7"] .art').waitFor()
    await other.locator('[data-slot-index="7"] .art').waitFor()
    await other.locator('[data-slot-index="7"] .remove').click()
    await waitFor(() => commands.length === 3); assert.equal(commands[2].command, 'remove'); confirm()
    await waitFor(async () => await page.locator('[data-slot-index="7"] .art').count() === 0)
    // Ambiguous copies get one image choice; subsequent placements reuse that explicit choice.
    await page.locator('.results button').nth(5).click()
    await page.locator('.copy-choice').waitFor(); await other.locator('.copy-choice').waitFor()
    assert.equal(commands.length, 3)
    await other.locator('.copy-choice button').filter({ hasText: 'Other/Skylander 6.sky' }).click()
    await waitFor(() => commands.length === 4); assert.equal(commands[3].fileId, 'a'.repeat(64)); confirm()
    await other.locator('[data-slot-index="1"] .art').waitFor()
    // Trap search click targets the keyhole automatically.
    await page.locator('#portal-search').fill('Skylander 7')
    await waitFor(async () => await page.locator('.results button').count() === 1)
    await page.locator('.results button').click()
    await waitFor(() => commands.length === 5); assert.equal(commands[4].index, 9); confirm()
    await page.locator('[data-slot-index="9"] .art').waitFor()
    await page.getByRole('button', { name: 'Tout vider', exact: true }).click()
    await waitFor(() => commands.length === 6); assert.equal(commands[5].command, 'clear'); confirm()
    await waitFor(async () => await page.locator('.slot .art').count() === 0)
    view.availability = 'CEMU_UNAVAILABLE'
    await page.getByText('Cemu indisponible', { exact: true }).waitFor()
    assert.equal(await page.locator('.results button').isDisabled(), true)
    view.availability = 'READY'
    await page.getByText('Connecté à Cemu', { exact: true }).waitFor()
    assert.equal(commands.length, 6) // Reconnection only reads; no old placement replay.
    assert.deepEqual(errors, [])
    await page.screenshot({ path: '/tmp/direct-portal-ui.png', fullPage: true })
    console.log('PASS: six-image search, click, drag/drop, confirmed state, removal, shared windows/auth, exact copy, trap, clear, reconnect; 6 explicit commands.')
  } finally { await browser.close(); await new Promise(r => server.close(r)) }
})().catch(error => { console.error(error); server.close(); process.exitCode = 1 })
