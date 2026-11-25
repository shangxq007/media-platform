import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'
import { existsSync, readFileSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import { chromium } from 'playwright'

const [app, out] = process.argv.slice(2)
const token = readFileSync(join(app, 'token'), 'utf8').trim()
const reader = readFileSync(join(app, 'reader-token'), 'utf8').trim()
const profile = JSON.parse(Buffer.from(token.split('.')[1], 'base64url').toString())
const base = 'http://127.0.0.1:18080'
const project = 'project-fr-e2e'
const projectApi = `/api/tenants/tenant-fr-e2e/projects/${project}`
const batch = projectApi + '/timeline-operations/batch/v2'
const revisions = `/api/render/projects/${project}/timeline/revisions`
const editor = base + `/w/workspace-fr-e2e/projects/${project}/edit`
const browser = await chromium.launch({ executablePath: '/usr/bin/google-chrome', headless: true,
  args: ['--no-sandbox', '--disable-dev-shm-usage'] })
const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } })
await context.addInitScript(({ access, profile: claims }) => {
  const user = { access_token: access, token_type: 'Bearer', profile: claims,
    expires_at: claims.exp, scope: 'openid profile email', session_state: claims.sid }
  for (const authority of ['http://127.0.0.1:18081', 'http://127.0.0.1:18081/'])
    sessionStorage.setItem(`oidc.user:${authority}:f8-local`, JSON.stringify(user))
}, { access: token, profile })
const page = await context.newPage()
const result = { status: 'RUNNING', browser: browser.version(), timecodeSeconds: 0.4, stages: [], cases: {}, pictures: {} }
const record = name => {
  result.stages.push(name)
  writeFileSync(join(out, 'history-result.json'), JSON.stringify(result, null, 2))
}
async function checkpoint(name, http) {
  const response = join(out, 'database-' + name + '.json')
  writeFileSync(join(out, 'checkpoint-' + name), '')
  for (let attempt = 0; attempt < 300; attempt++) {
    if (existsSync(response)) {
      const fact = JSON.parse(readFileSync(response, 'utf8'))
      assert.deepEqual({ head: fact.head, rows: fact.rows }, http)
      return
    }
    await new Promise(resolve => setTimeout(resolve, 100))
  }
  throw new Error('PostgreSQL checkpoint did not respond: ' + name)
}

const api = async (path, expected, { body, digest, actor = token } = {}) => {
  const headers = { Authorization: `Bearer ${actor}`, ...(digest ? { 'If-Match': `"${digest}"` } : {}) }
  const response = await context.request.fetch(base + path, {
    method: body === undefined ? 'GET' : 'POST', headers, data: body,
  })
  const data = await response.json()
  assert.equal(response.status(), expected, `${path}: ${response.status()} ${data.errorCode ?? ''}`)
  return data
}
const history = () => api(revisions + '?limit=100', 200)
const head = async () => {
  const [summary, current] = await Promise.all([api(revisions + '/head', 200),
    api(`/api/timeline-git/products/${project}/revisions/current`, 200)])
  assert.equal(summary.id, current.revisionId)
  return { ...summary, contentHash: current.revision.semanticContext.timelineContentDigest }
}
const counts = async () => ({ head: (await head()).id, rows: (await history()).length })
const snap = id => api(revisions + '/' + id + '/snapshot', 200)
const intent = (key, revision, operation) => ({
  batchId: key, baseRevisionId: revision.id, baseContentHash: revision.contentHash, operations: [operation],
})
const restore = (key, revision, sourceRevisionId) => intent(key, revision, {
  definitionId: 'timeline.revision.restore', version: '1.0', parameters: { sourceRevisionId },
})
const move = (key, revision, time) => intent(key, revision, {
  definitionId: 'timeline.move', target: { clipId: 'clip-red' }, parameters: { absolute: true, delta: time },
})
const preview = body => api(batch + '/preview', 200, { body })
const commit = (body, digest, expected = 201) => api(batch + '/apply', expected, { body, digest })
const waitView = id => page.waitForFunction(wanted => {
  const media = document.querySelector('[data-preview-revision]')
  const timeline = document.querySelector('section[data-viewed-revision]')
  return media?.getAttribute('data-preview-revision') === wanted
    && media.getAttribute('data-preview-status') === 'READY'
    && timeline?.getAttribute('data-viewed-revision') === wanted
    && !!timeline.querySelector('[data-clip-id="clip-blue"]')
}, id, { timeout: 45000 })
const pick = id => page.locator('.ff-revision-list button').filter({ hasText: id }).click()
const returnToHead = id => page.getByRole('button', { name: 'Return to editing head' }).click().then(() => waitView(id))

async function picture(name, expectedLeft, dominant) {
  const red = page.getByLabel('Timeline clip clip-red', { exact: true })
  await red.waitFor()
  const left = await red.evaluate(el => parseFloat(el.style.left))
  assert.equal(left, expectedLeft, name)
  await page.getByLabel('Timeline preview seek').evaluate((input, time) => {
    Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set.call(input, String(time))
    input.dispatchEvent(new Event('input', { bubbles: true }))
    input.dispatchEvent(new Event('change', { bubbles: true }))
  }, result.timecodeSeconds)
  await page.waitForFunction(wanted => {
    const c = document.querySelector('canvas[aria-label="Timeline preview canvas"]')
    const [r, g, b] = c.getContext('2d').getImageData(c.width / 2, c.height / 2, 1, 1).data
    return wanted === 'red' ? r > 80 && r > 2 * Math.max(g, b) : Math.max(r, g, b) < 30
  }, dominant)
  const canvas = page.getByLabel('Timeline preview canvas', { exact: true })
  const rgb = await canvas.evaluate(c => Array.from(c.getContext('2d').getImageData(c.width / 2, c.height / 2, 1, 1).data).slice(0, 3))
  const strip = page.getByRole('group', { name: 'Timeline trim' })
  await canvas.screenshot({ path: join(out, name + '-picture.png') })
  await strip.screenshot({ path: join(out, name + '-timeline.png') })
  result.pictures[name] = { redClipStartPixels: left, centerRGB: rgb, dominant }
}

try {
  const definition = await api(projectApi + '/semantic/operations/timeline.revision.restore/1.0', 200)
  assert.equal(definition.target, 'TIMELINE')
  assert.deepEqual(definition.invocationModes, ['PREVIEW', 'APPLY'])
  assert.deepEqual(definition.parameters, { sourceRevisionId: { type: 'revision-id', required: true } })
  result.discovery = definition
  const a = await head()
  const aSnapshot = await snap(a.id)
  const bIntent = move('n01-fixture-b', a, '3/1')
  await commit(bIntent, (await preview(bIntent)).batchDigest)
  const b = await head()
  assert.notEqual(a.contentHash, b.contentHash)
  const before = await counts()
  await checkpoint('before-browse', before)
  result.revisions = { A: a.id, B: b.id, AHash: a.contentHash, BHash: b.contentHash }
  await page.goto(editor, { waitUntil: 'domcontentloaded' })
  await waitView(b.id)
  await picture('B', 240, 'black')
  record('B editing head loaded')

  const aSnapshotRoute = `**${revisions}/${a.id}/snapshot`
  let observed, release
  const requested = new Promise(resolve => { observed = resolve })
  const hold = new Promise(resolve => { release = resolve })
  const delay = async route => {
    observed()
    await hold
    await route.continue().catch(() => {})
  }
  await page.route(aSnapshotRoute, delay)
  await pick(a.id)
  await requested
  await returnToHead(b.id)
  release()
  await page.unroute(aSnapshotRoute, delay, { behavior: 'wait' })
  await waitView(b.id)
  assert.equal(await page.getByRole('region', { name: 'Historical timeline' }).count(), 0)
  record('A snapshot delayed while editor returned to B')

  await pick(a.id)
  await waitView(a.id)
  assert.equal(await page.getByRole('button', { name: 'Delete selected clip' }).count(), 0)
  assert.equal(await page.getByRole('button', { name: 'Preview operation' }).count(), 0)
  await picture('A', 0, 'red')
  await returnToHead(b.id)
  assert.deepEqual(await counts(), before)
  await checkpoint('browse', before)
  result.cases.browse = { before, after: await counts(), delayedSnapshot: true }
  record('A viewed read-only, returned to B, no history write')

  const deny = restore('n01-reader', b, a.id)
  const readerPreview = await api(batch + '/preview', 200, { body: deny, actor: reader })
  const denied = await api(batch + '/apply', 403, { body: deny, digest: readerPreview.batchDigest, actor: reader })
  assert.deepEqual(await counts(), before)
  await checkpoint('reader', before)
  result.cases.reader = { preview: 200, apply: 403, code: denied.errorCode, after: await counts() }
  const foreignId = (await api(revisions.replace(project, 'project-peer') + '/head', 200)).id
  const foreign = restore('n01-foreign', b, foreignId)
  const missing = await api(batch + '/preview', 404, { body: foreign })
  await api(batch + '/apply', 404, { body: foreign, digest: '0'.repeat(64) })
  assert.deepEqual(await counts(), before)
  await checkpoint('foreign', before)
  result.cases.foreignProject = { preview: 404, apply: 404, code: missing.errorCode, after: await counts() }
  await api(revisions + '/' + a.id + '/restore', 410, { body: {} })
  await api(`/api/timeline-git/products/${project}/revisions/${a.id}/restore`, 410, { body: {} })
  assert.deepEqual(await counts(), before)
  await checkpoint('retired', before)
  result.cases.retired = { statuses: [410, 410], after: await counts() }
  record('reader, foreign source and retired direct endpoints rejected')

  const applyTrace = []
  let previews = 0
  page.on('request', req => {
    if (new URL(req.url()).pathname === batch + '/preview' && req.postData()?.includes('timeline.revision.restore')) previews++
  })
  await page.route('**' + batch + '/apply', async route => {
    const req = route.request()
    const response = await route.fetch()
    applyTrace.push({ body: req.postData(), ifMatch: req.headers()['if-match'],
      status: response.status(), result: await response.json() })
    if (applyTrace.length === 1) await route.abort('failed')
    else await route.fulfill({ response })
  })
  await pick(a.id)
  await waitView(a.id)
  await page.getByRole('button', { name: 'Preview restore', exact: true }).click()
  await page.getByRole('button', { name: 'Confirm restore', exact: true }).waitFor()
  assert.deepEqual(await counts(), before)
  await page.getByRole('button', { name: 'Confirm restore', exact: true }).click()
  const retry = page.getByRole('button', { name: 'Retry confirmed restore' })
  await retry.waitFor()
  const afterLost = await counts()
  await checkpoint('committed-response-lost', afterLost)
  assert.equal(afterLost.rows, before.rows + 1)
  await retry.click()
  await page.getByText(`Restore accepted as revision ${afterLost.head}.`, { exact: true }).waitFor()
  await waitView(afterLost.head)
  assert.equal(previews, 1)
  assert.equal(applyTrace.length, 2)
  assert.equal(applyTrace[0].body, applyTrace[1].body)
  assert.equal(applyTrace[0].ifMatch, applyTrace[1].ifMatch)
  assert.equal(applyTrace[0].status, 201)
  assert.equal(applyTrace[1].status, 200)
  assert.equal(applyTrace[1].result.replayed, true)
  assert.equal(applyTrace[1].result.newRevisionId, afterLost.head)
  assert.deepEqual(await counts(), afterLost)
  await checkpoint('replay', afterLost)
  await page.unroute('**' + batch + '/apply')
  const c = await head()
  const cSnapshot = await snap(c.id)
  assert.equal(c.parentRevisionId, b.id)
  assert.equal(c.contentHash, a.contentHash)
  assert.equal(cSnapshot.canonicalTimelineJson, aSnapshot.canonicalTimelineJson)
  result.revisions = { ...result.revisions, C: c.id, CHash: c.contentHash, parentOfC: c.parentRevisionId,
    equalCanonicalPayloadSHA256: createHash('sha256').update(cSnapshot.canonicalTimelineJson).digest('hex') }
  result.cases.replay = { before, afterLost, afterRetry: await counts(), applyTrace, previews }
  await page.reload({ waitUntil: 'domcontentloaded' })
  await waitView(c.id)
  await picture('C', 0, 'red')
  assert.deepEqual(result.pictures.C, result.pictures.A)
  await pick(b.id)
  await waitView(b.id)
  assert.equal(await page.getByLabel('Timeline clip clip-red').evaluate(el => parseFloat(el.style.left)), 240)
  await pick(a.id)
  await waitView(a.id)
  await returnToHead(c.id)
  record('C reloaded, A and B still accessible')

  const noop = restore('n01-same-content', c, a.id)
  const noopPlan = await preview(noop)
  const noopResult = await commit(noop, noopPlan.batchDigest, 200)
  assert.equal(noopPlan.noOp, true)
  assert.equal(noopResult.status, 'NO_OP')
  assert.deepEqual(await counts(), afterLost)
  await checkpoint('no-op', afterLost)
  result.cases.sameContent = { http: 200, outcome: noopResult.status, before: afterLost, after: await counts() }

  // Preview in the editor, then advance the real head with a competing edit.
  await pick(b.id)
  await waitView(b.id)
  await page.getByRole('button', { name: 'Preview restore', exact: true }).click()
  const confirm = page.getByRole('button', { name: 'Confirm restore', exact: true })
  await confirm.waitFor()
  const competition = move('n01-competing-edit', c, '5/1')
  await commit(competition, (await preview(competition)).batchDigest)
  const advanced = await counts()
  await checkpoint('competing-edit', advanced)
  assert.equal(advanced.rows, afterLost.rows + 1)
  const conflictResponse = page.waitForResponse(r => new URL(r.url()).pathname === batch+'/apply')
  await confirm.click()
  const conflict = await conflictResponse
  assert.equal(conflict.status(), 409)
  const conflictData = await conflict.json()
  await page.getByText(/restore confirmation is stale/).waitFor()
  await waitView(advanced.head)
  assert.equal(await confirm.count(), 0)
  assert.deepEqual(await counts(), advanced)
  await pick(b.id)
  await waitView(b.id)
  assert.equal(await confirm.count(), 0)
  await page.getByRole('button', { name: 'Preview restore', exact: true }).click()
  await confirm.waitFor()
  await page.getByRole('button', { name: 'Discard restore' }).click()
  await checkpoint('stale', advanced)
  result.cases.conflict = { http: 409, code: conflictData.errorCode, beforeApply: advanced, after: await counts(), newPreviewRequired: true }
  result.status = 'PASS'
  record('stale confirmation cleared; fresh preview required')
} finally {
  await browser.close()
}
