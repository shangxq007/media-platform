import assert from 'node:assert/strict'
import { existsSync, readFileSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import { chromium } from 'playwright'

const [app, out] = process.argv.slice(2)
const token = readFileSync(join(app, 'token'), 'utf8').trim()
const reader = readFileSync(join(app, 'reader-token'), 'utf8').trim()
const profile = JSON.parse(Buffer.from(token.split('.')[1], 'base64url').toString())
const base = 'http://127.0.0.1:18080'
const project = 'project-fr-e2e'
const branchPath = `/api/render/projects/${project}/timeline/branches`
const batch = `/api/tenants/tenant-fr-e2e/projects/${project}/timeline-operations/batch/v2`
const editor = base + `/w/workspace-fr-e2e/projects/${project}/edit`
const browser = await chromium.launch({ executablePath: '/usr/bin/google-chrome', headless: true,
  args: ['--no-sandbox', '--disable-dev-shm-usage'] })
async function session() {
  const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } })
  await context.addInitScript(({ access, claims }) => {
    const user = { access_token: access, token_type: 'Bearer', profile: claims,
      expires_at: claims.exp, scope: 'openid profile email', session_state: claims.sid }
    for (const authority of ['http://127.0.0.1:18081', 'http://127.0.0.1:18081/'])
      sessionStorage.setItem(`oidc.user:${authority}:f8-local`, JSON.stringify(user))
  }, { access: token, claims: profile })
  return context
}

const context = await session()
const page = await context.newPage()
const result = { status: 'RUNNING', browser: browser.version(), stages: [], pictures: {}, http: {} }
const save = stage => {
  result.stages.push(stage)
  writeFileSync(join(out, 'branches-result.json'), JSON.stringify(result, null, 2))
}

async function api(path, expected, { body, digest, actor = token, client = context.request } = {}) {
  const request = await client.fetch(base + path, {
    method: body === undefined ? 'GET' : 'POST', data: body,
    headers: { Authorization: `Bearer ${actor}`, ...(digest ? { 'If-Match': `"${digest}"` } : {}) },
  })
  const text = await request.text()
  const response = text ? JSON.parse(text) : null
  assert.equal(request.status(), expected, `${path} ${response?.errorCode ?? ''}`)
  return response
}

async function checkpoint(name) {
  const path = join(out, 'database-' + name + '.json')
  writeFileSync(join(out, 'checkpoint-' + name), '')
  for (let attempt = 0; attempt < 300; attempt++) {
    if (existsSync(path)) return JSON.parse(readFileSync(path, 'utf8'))
    await new Promise(resolve => setTimeout(resolve, 100))
  }
  throw new Error('Missing PostgreSQL checkpoint ' + name)
}

const branch = (ref = 'main', expected = 200, actor = token) => api(branchPath + '/' + ref, expected, { actor })
const trim = (id, ref, revision, contentHash, delta = '1/4') => ({
  batchId: id, refId: ref, baseRevisionId: revision, baseContentHash: contentHash,
  operations: [{ definitionId: 'timeline.trim', target: { clipId: 'clip-red' }, parameters: { edge: 'START', delta } }],
})
const preview = (body, code = 200, actor = token) => api(batch + '/preview', code, { body, actor })
const apply = (body, digest, code = 201, actor = token) => api(batch + '/apply', code, { body, digest, actor })
const select = (ref, tab = page) => tab.getByRole('combobox', { name: 'Editing branch', exact: true }).selectOption(ref)

async function waitView(ref, revision, tab = page) {
  await tab.waitForFunction(({ ref, revision }) => {
    const media = document.querySelector('[data-preview-revision]')
    const timeline = document.querySelector('section[data-viewed-revision]')
    return document.querySelector('select[aria-label="Editing branch"]')?.value === ref
      && media?.getAttribute('data-preview-revision') === revision
      && media.getAttribute('data-preview-status') === 'READY'
      && timeline?.getAttribute('data-viewed-revision') === revision
      && !!timeline.querySelector('[data-clip-id="clip-red"]')
  }, { ref, revision }, { timeout: 45000 })
}

async function picture(label, left, colours) {
  const red = page.getByLabel('Timeline clip clip-red', { exact: true })
  assert.equal(await red.evaluate(el => parseFloat(el.style.left)), left)
  const canvas = page.getByLabel('Timeline preview canvas', { exact: true })
  result.pictures[label] = { left, samples: {} }
  for (const [seconds, colour] of Object.entries(colours)) {
    await page.getByLabel('Timeline preview seek').evaluate((input, at) => {
      Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set.call(input, at)
      input.dispatchEvent(new Event('input', { bubbles: true }))
      input.dispatchEvent(new Event('change', { bubbles: true }))
    }, seconds)
    await page.waitForFunction(wanted => {
      const c = document.querySelector('canvas[aria-label="Timeline preview canvas"]')
      const [r, g, b] = c.getContext('2d').getImageData(c.width / 2, c.height / 2, 1, 1).data
      return wanted === 'red' ? r > 80 && r > 2 * Math.max(g, b) : Math.max(r, g, b) < 30
    }, colour)
    result.pictures[label].samples[seconds] = await canvas.evaluate(c =>
      Array.from(c.getContext('2d').getImageData(c.width / 2, c.height / 2, 1, 1).data).slice(0, 3))
    await canvas.screenshot({ path: join(out, `${label}-${seconds}-picture.png`) })
  }
  await page.getByRole('group', { name: 'Timeline trim' }).screenshot({ path: join(out, label + '-timeline.png') })
}

try {
  const main = await branch()
  const { headRevisionId: b, contentHash: bHash } = main
  const a = main.history[0].parentRevisionId
  const aHash = (await api(`/api/timeline-git/products/${project}/revisions/${a}`, 200)).timelineContentHash
  const initial = await checkpoint('initial')
  assert.deepEqual(initial.refs, { main: b })
  await page.goto(editor)
  await waitView('main', b)
  await picture('B', 400, { '0.4': 'black', '1.4': 'black' })
  await page.locator('.ff-revision-list button').filter({ hasText: a }).click()
  await waitView('main', a)
  await picture('A', 0, { '0.4': 'red', '1.4': 'red' })

  const createTrace = []
  await page.route('**' + branchPath, async route => {
    if (route.request().method() !== 'POST') return route.continue()
    const response = await route.fetch()
    createTrace.push({ body: route.request().postData(), status: response.status(), response: await response.json() })
    if (createTrace.length === 1) await route.abort('failed')
    else await route.fulfill({ response })
  })
  await page.getByLabel('Branch name').fill('alternate')
  await page.getByRole('button', { name: 'Create branch from viewed revision' }).click()
  const retryCreate = page.getByRole('button', { name: 'Retry branch creation' })
  await retryCreate.waitFor()
  assert.equal(await page.getByRole('combobox', { name: 'Editing branch' }).isDisabled(), true)
  assert.equal(await page.getByRole('button', { name: 'Preview restore' }).isDisabled(), true)
  const fork = await checkpoint('fork')
  assert.equal(fork.refs.alternate, a)
  assert.equal(fork.rows, initial.rows)
  assert.equal(fork.snapshots, initial.snapshots)
  await retryCreate.click()
  await waitView('alternate', a)
  assert.deepEqual(createTrace.map(e => e.status), [201, 200])
  assert.equal(createTrace[0].body, createTrace[1].body)
  assert.equal(createTrace[1].response.replayed, true)
  await page.unroute('**' + branchPath)
  const collision = await api(branchPath, 409, { body: { refId: 'alternate', sourceRevisionId: b, commandId: 'n02-occupied' } })
  assert.deepEqual(await checkpoint('duplicate'), fork)
  result.http.create = { trace: createTrace, occupied: { status: 409, code: collision.errorCode } }
  save('Fork, lost response, replay and occupied name')

  assert.equal((await branch('alternate', 200, reader)).headRevisionId, a)
  await api(branchPath, 403, { actor: reader, body: { refId: 'reader-denied', sourceRevisionId: a, commandId: 'n02-reader-create' } })
  const readerEdit = trim('n02-reader-edit', 'alternate', a, aHash)
  await apply(readerEdit, (await preview(readerEdit, 200, reader)).batchDigest, 403, reader)
  const peer = branchPath.replace(project, 'project-peer')
  const foreignRevision = (await api(peer + '/main', 200)).headRevisionId
  await api(branchPath, 404, { body: { refId: 'foreign-revision', sourceRevisionId: foreignRevision, commandId: 'n02-foreign-create' } })
  await api(peer, 201, { body: { refId: 'peer-only', sourceRevisionId: foreignRevision, commandId: 'n02-peer' } })
  await branch('peer-only', 404)
  await apply(trim('n02-foreign-target', 'peer-only', a, aHash), '0'.repeat(64), 404)
  assert.deepEqual(await checkpoint('foreign'), fork)
  result.http.access = { readerListAndRead: 200, readerCreate: 403, readerEdit: 403, foreignSource: 404, foreignTarget: 404 }

  await api(branchPath, 201, { body: { refId: 'mirror', sourceRevisionId: a, commandId: 'n02-mirror' } })
  const twoRefs = trim('n02-cross-confirmation', 'alternate', a, aHash)
  const mirror = { ...twoRefs, refId: 'mirror' }
  const altDigest = (await preview(twoRefs)).batchDigest
  const mirrorDigest = (await preview(mirror)).batchDigest
  assert.notEqual(altDigest, mirrorDigest)
  const mismatch = await apply(mirror, altDigest, 409)
  const confirmed = await checkpoint('confirmation')
  assert.deepEqual(confirmed.refs, { main: b, alternate: a, mirror: a })
  assert.equal(confirmed.rows, fork.rows)
  result.http.changedRef = { alternate: altDigest, mirror: mirrorDigest, status: 409, code: mismatch.errorCode }

  const second = await session()
  const otherEditor = await second.newPage()
  await otherEditor.goto(editor)
  await waitView('main', b, otherEditor)
  assert.equal(await page.getByRole('button', { name: 'Delete selected clip' }).count(), 0)
  // The initial left handle straddles the visible edge of the clip.
  const drawTrim = async (pixels = 80) => {
    const handle = page.getByLabel('Trim start edge clip-red')
    await handle.scrollIntoViewIfNeeded()
    const box = await handle.boundingBox()
    assert.ok(box)
    const x = box.x + box.width / 2, y = box.y + box.height / 2
    await page.mouse.move(x + 2, y)
    await page.mouse.down()
    await page.mouse.move(x + pixels + 2, y, { steps: 8 })
    await page.mouse.up()
    await page.getByText(/Preview valid:/).waitFor()
  }
  await drawTrim()
  await select('main')
  await page.getByRole('button', { name: 'Stay on this branch' }).click()
  await page.getByText(/Preview valid:/).waitFor()
  result.unsavedDecision = 'stay preserves confirmed draft'

  const applyTrace = []
  let delayedRelease, observeApply
  const observedApply = new Promise(resolve => { observeApply = resolve })
  const delayedApply = new Promise(resolve => { delayedRelease = resolve })
  await page.route('**' + batch + '/apply', async route => {
    const req = route.request()
    const response = await route.fetch()
    applyTrace.push({ body: req.postData(), ifMatch: req.headers()['if-match'], status: response.status(), response: await response.json() })
    if (applyTrace.length === 1) {
      observeApply()
      await delayedApply
      await route.abort('failed')
    } else await route.fulfill({ response })
  })
  await page.getByRole('button', { name: 'Apply trim', exact: true }).click()
  await observedApply
  assert.equal(await page.getByRole('combobox', { name: 'Editing branch' }).isDisabled(), true)
  const applied = await checkpoint('trim')
  const c = applied.refs.alternate
  assert.equal(applied.refs.main, b)
  assert.equal(applied.rows, confirmed.rows + 1)
  delayedRelease()
  const retryApply = page.getByRole('button', { name: /Retry Trim/ })
  await retryApply.waitFor()
  assert.equal(await page.getByRole('combobox', { name: 'Editing branch' }).isDisabled(), true)
  await retryApply.click()
  await waitView('alternate', c)
  await page.getByText(/Trim applied at revision/).waitFor()
  assert.deepEqual(applyTrace.map(e => e.status), [201, 200])
  assert.equal(applyTrace[0].body, applyTrace[1].body)
  assert.equal(applyTrace[0].ifMatch, applyTrace[1].ifMatch)
  assert.equal(JSON.parse(applyTrace[0].body).refId, 'alternate')
  assert.equal(applyTrace[1].response.replayed, true)
  const replay = await checkpoint('replay')
  assert.deepEqual(replay, applied)
  await page.unroute('**' + batch + '/apply')
  result.http.trim = { trace: applyTrace, before: confirmed, after: applied, replay }
  result.revisions = { A: a, B: b, C: c }
  await picture('C', 80, { '0.4': 'black', '1.4': 'red' })
  await waitView('main', b, otherEditor)
  await select('main')
  await waitView('main', b)

  let release, seen
  const pending = new Promise(resolve => { seen = resolve })
  const blocked = new Promise(resolve => { release = resolve })
  const delayedRoute = '**' + branchPath + '/alternate'
  await page.route(delayedRoute, async route => {
    const response = await route.fetch()
    seen()
    await blocked
    await route.fulfill({ response }).catch(() => {})
  })
  await select('alternate')
  await pending
  await select('main')
  await waitView('main', b)
  release()
  await page.unroute(delayedRoute)
  await waitView('main', b)
  await select('alternate')
  await waitView('alternate', c)
  await page.reload()
  await waitView('alternate', c)
  await otherEditor.reload()
  await waitView('main', b, otherEditor)
  assert.deepEqual(await checkpoint('switch'), applied)
  save('Exact trim retry, branch switch, delayed read, reload and second editor')

  const current = await branch('alternate')
  const right = trim('n02-same-ref-loser', 'alternate', c, current.contentHash, '1/2')
  const rightPlan = await preview(right)
  await drawTrim(20)
  const laterTrace = []
  let signalCommit
  const commitObserved = new Promise(resolve => { signalCommit = resolve })
  await page.route('**' + batch + '/apply', async route => {
    const req = route.request()
    const response = await route.fetch()
    laterTrace.push({ body: req.postData(), ifMatch: req.headers()['if-match'], status: response.status(), response: await response.json() })
    if (laterTrace.length === 1) {
      await route.abort('failed')
      signalCommit()
    } else await route.fulfill({ response })
  })
  await page.getByRole('button', { name: 'Apply trim', exact: true }).click()
  await commitObserved
  await retryApply.waitFor()
  const laterCommitted = await checkpoint('later-committed')
  const acceptedId = laterCommitted.refs.alternate
  assert.equal(laterCommitted.rows, applied.rows + 1)
  assert.equal(laterTrace[0].response.newRevisionId, acceptedId)
  const accepted = await branch('alternate')
  const laterEdit = trim('n02-after-lost-response', 'alternate', acceptedId, accepted.contentHash)
  const laterPlan = await api(batch + '/preview', 200, { body: laterEdit, client: second.request })
  const advanced = await api(batch + '/apply', 201, { body: laterEdit, digest: laterPlan.batchDigest, client: second.request })
  const laterAdvanced = await checkpoint('later-advanced')
  assert.equal(laterAdvanced.rows, laterCommitted.rows + 1)
  assert.equal(laterAdvanced.refs.alternate, advanced.newRevisionId)
  await retryApply.click()
  await waitView('alternate', advanced.newRevisionId)
  const acceptedMessage = `Trim applied at revision ${acceptedId}. The branch is now at revision ${advanced.newRevisionId}.`
  await page.getByText(acceptedMessage, { exact: true }).waitFor()
  assert.equal(await retryApply.count(), 0)
  assert.deepEqual(laterTrace.map(e => e.status), [201, 200])
  assert.equal(laterTrace[0].body, laterTrace[1].body)
  assert.equal(laterTrace[0].ifMatch, laterTrace[1].ifMatch)
  assert.equal(laterTrace[1].response.newRevisionId, acceptedId)
  assert.equal(laterTrace[1].response.replayed, true)
  const laterReplay = await checkpoint('later-replay')
  assert.deepEqual(laterReplay, laterAdvanced)
  await page.unroute('**' + batch + '/apply')
  result.http.replayAfterAdvance = { trace: laterTrace, before: applied, committed: laterCommitted,
    advanced: laterAdvanced, replay: laterReplay, acceptedMessage }
  const stale = await apply(right, rightPlan.batchDigest, 409)
  const sameRef = await checkpoint('stale')
  assert.deepEqual(sameRef, laterReplay)
  result.http.sameRef = { loser: 409, code: stale.errorCode, before: laterReplay, after: sameRef }

  const mainEdit = trim('n02-independent-main', 'main', b, bHash)
  const mirrorEdit = trim('n02-independent-mirror', 'mirror', a, aHash)
  const [mainPlan, mirrorPlan] = await Promise.all([preview(mainEdit), preview(mirrorEdit)])
  const [mainDone, mirrorDone] = await Promise.all([
    apply(mainEdit, mainPlan.batchDigest), apply(mirrorEdit, mirrorPlan.batchDigest),
  ])
  const independent = await checkpoint('independent')
  assert.equal(independent.refs.main, mainDone.newRevisionId)
  assert.equal(independent.refs.mirror, mirrorDone.newRevisionId)
  assert.equal(independent.refs.alternate, sameRef.refs.alternate)
  assert.equal(independent.rows, sameRef.rows + 2)
  result.http.independent = { main: 201, mirror: 201, before: sameRef, after: independent }
  result.status = 'PASS'
  save('Same-ref stale edit and independent concurrent writes')
} finally {
  await browser.close()
}
