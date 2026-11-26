import assert from 'node:assert/strict'
import { execFileSync } from 'node:child_process'
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
  save('Same-ref stale edit and independent concurrent writes')

  await select('main')
  await waitView('main', mainDone.newRevisionId)
  const deleteClip = page.getByRole('button', { name: 'Delete selected clip' })
  const f5 = {}
  let before = independent
  for (const resolution of ['replay', 'discard']) {
    await drawTrim(20)
    const trace = []
    await page.route('**' + batch + '/apply', async route => {
      const req = route.request()
      const res = await route.fetch()
      trace.push({ body: req.postData(), ifMatch: req.headers()['if-match'], status: res.status(), response: await res.json() })
      if (trace.length === 1) await route.abort('failed')
      else await route.fulfill({ response: res })
    })
    await page.getByRole('button', { name: 'Apply trim', exact: true }).click()
    await retryApply.waitFor()
    const committed = await checkpoint('f5-' + resolution + '-committed')
    const accepted = trace[0].response.newRevisionId
    assert.equal(trace[0].status, 201)
    assert.equal(committed.refs.main, accepted)
    assert.equal(committed.rows, before.rows + 1)
    assert.equal(JSON.parse(trace[0].body).baseRevisionId, before.refs.main)
    assert.equal(JSON.parse(trace[0].body).refId ?? 'main', 'main')
    assert.match(trace[0].ifMatch, /^"[0-9a-f]{64}"$/)
    const reloadHead = page.getByRole('button', { name: 'Reload HEAD', exact: true })
    const picker = page.getByRole('combobox', { name: 'Editing branch' })
    const history = page.locator('.ff-revision-list button').filter({ hasText: before.refs.main })
    assert.equal(await deleteClip.isDisabled(), true)
    assert.equal(await reloadHead.isDisabled(), true)
    assert.equal(await picker.isDisabled(), true)
    assert.equal(await history.isDisabled(), true)
    await deleteClip.evaluate(button => button.click())
    await page.getByRole('region', { name: 'Edit existing clip' }).press('Delete')
    const blocked = await checkpoint('f5-' + resolution + '-blocked')
    assert.deepEqual(blocked, committed)
    assert.equal(trace.length, 1)
    assert.equal(await retryApply.isEnabled(), true)
    if (resolution === 'replay') {
      await retryApply.click()
      await page.getByText(`Trim applied at revision ${accepted}.`, { exact: true }).waitFor()
      assert.deepEqual(trace.map(e => e.status), [201, 200])
      assert.equal(trace[1].body, trace[0].body)
      assert.equal(trace[1].ifMatch, trace[0].ifMatch)
      assert.equal(trace[1].response.newRevisionId, accepted)
      assert.equal(trace[1].response.replayed, true)
    } else {
      await page.getByRole('button', { name: 'Cancel trim' }).click()
      await page.getByText(/Trim retry discarded/).waitFor()
      assert.equal(trace.length, 1)
    }
    await page.unroute('**' + batch + '/apply')
    await waitView('main', accepted)
    assert.equal(await retryApply.count(), 0)
    const resolved = await checkpoint('f5-' + resolution + '-resolved')
    assert.deepEqual(resolved, committed)
    const readback = page.waitForResponse(res => res.url().endsWith(branchPath + '/main') && res.request().method() === 'GET')
    await reloadHead.click()
    assert.equal((await readback).status(), 200)
    await history.click()
    await waitView('main', before.refs.main)
    await page.getByRole('button', { name: 'Return to editing head' }).click()
    await waitView('main', accepted)
    await select('alternate')
    await waitView('alternate', independent.refs.alternate)
    await select('main')
    await waitView('main', accepted)
    const navigation = await checkpoint('f5-' + resolution + '-navigation')
    assert.deepEqual(navigation, resolved)
    f5[resolution] = { before, committed, blocked, resolved, navigation, trace, accepted }
    before = navigation
  }
  assert.equal(await deleteClip.isEnabled(), true)
  const deleteResponse = page.waitForResponse(res => res.url().endsWith(batch + '/apply') && res.request().method() === 'POST')
  await deleteClip.click()
  const deleted = await deleteResponse
  assert.equal(deleted.status(), 201)
  const deleteBody = deleted.request().postDataJSON()
  const deleteReceipt = await deleted.json()
  assert.equal(deleteBody.baseRevisionId, before.refs.main)
  assert.equal(deleteBody.operations[0].definitionId, 'timeline.delete')
  await page.getByText(`Clip clip-red deleted at revision ${deleteReceipt.newRevisionId}. The source media is retained.`).waitFor()
  const deletion = await checkpoint('f5-deletion')
  assert.equal(deletion.rows, before.rows + 1)
  assert.equal(deletion.refs.main, deleteReceipt.newRevisionId)
  result.http.defaultTrimOwnership = { ...f5, deletion: { before, after: deletion, status: 201, revision: deleteReceipt.newRevisionId } }
  save('Default-branch trim retry and discard block deletion, release navigation, and permit a later delete')

  // Seed a fresh editor with the original two-colour fixture through the canonical operation.
  const removedHead = await branch()
  const reset = { batchId: 'n03-seed', baseRevisionId: removedHead.headRevisionId,
    baseContentHash: removedHead.contentHash, operations: [{ definitionId: 'timeline.revision.restore', version: '1.0',
      parameters: { sourceRevisionId: a } }] }
  const seed = await apply(reset, (await preview(reset)).batchDigest)
  await page.reload()
  await waitView('main', seed.newRevisionId)
  const n03 = { A: seed.newRevisionId, steps: [], conflicts: [] }
  const controls = page.getByRole('region', { name: 'Confirmed trim history' })
  const undo = controls.getByRole('button', { name: 'Undo trim', exact: true })
  const redo = controls.getByRole('button', { name: 'Redo trim', exact: true })
  const reloadHead = page.getByRole('button', { name: 'Reload HEAD', exact: true })
  async function uiTrim(label, pixels = 20) {
    const response = page.waitForResponse(r => r.url().endsWith(batch + '/apply') && r.request().method() === 'POST')
    await drawTrim(pixels)
    await page.getByRole('button', { name: 'Apply trim', exact: true }).click()
    const accepted = await response
    assert.equal(accepted.status(), 201)
    const id = (await accepted.json()).newRevisionId
    await waitView('main', id)
    await page.getByText(`Trim applied at revision ${id}.`, { exact: true }).waitFor()
    return { id, state: await checkpoint('n03-' + label) }
  }
  async function navigateHistory(direction, label, source, lost = false) {
    const trace = []
    await page.route('**' + batch + '/apply', async route => {
      const req = route.request()
      const res = await route.fetch()
      trace.push({ body: req.postData(), ifMatch: req.headers()['if-match'], status: res.status(), receipt: await res.json() })
      if (lost && trace.length === 1) await route.abort('failed')
      else await route.fulfill({ response: res })
    })
    try {
      await (direction === 'Undo' ? undo : redo).click()
      if (lost) {
        const retry = controls.getByRole('button', { name: 'Retry ' + direction.toLowerCase() + ' trim' })
        await retry.waitFor()
        assert.equal(await deleteClip.isDisabled(), true)
        assert.equal(await page.getByRole('combobox', { name: 'Editing branch' }).isDisabled(), true)
        await deleteClip.evaluate(button => button.click())
        const committed = await checkpoint('n03-' + label + '-lost')
        assert.equal(trace.length, 1)
        assert.equal(committed.refs.main, trace[0].receipt.newRevisionId)
        await retry.click()
      }
      await controls.getByText(new RegExp('^' + direction + ' applied at revision ')).waitFor()
      const accepted = trace[0].receipt.newRevisionId
      await waitView('main', accepted)
      assert.deepEqual(trace.map(x => x.status), lost ? [201, 200] : [201])
      assert.match(trace[0].ifMatch, /^"[0-9a-f]{64}"$/)
      assert.equal(JSON.parse(trace[0].body).operations[0].parameters.sourceRevisionId, source)
      if (lost) {
        assert.equal(trace[0].body, trace[1].body)
        assert.equal(trace[0].ifMatch, trace[1].ifMatch)
        assert.equal(trace[1].receipt.newRevisionId, accepted)
        assert.equal(trace[1].receipt.replayed, true)
        assert.equal(await deleteClip.isEnabled(), true)
        assert.equal(await page.getByRole('combobox', { name: 'Editing branch' }).isEnabled(), true)
      }
      const state = await checkpoint('n03-' + label)
      n03.steps.push({ label, accepted, source, trace, state })
      return accepted
    } finally {
      await page.unroute('**' + batch + '/apply')
    }
  }
  const nA = await checkpoint('n03-a')
  await picture('N03-A', 0, { '0.15': 'red', '0.4': 'red' })
  n03.B = (await uiTrim('b')).id
  await picture('N03-B', 20, { '0.15': 'black', '0.4': 'red' })
  n03.C = (await uiTrim('c')).id
  await picture('N03-C', 40, { '0.15': 'black', '0.4': 'black', '0.6': 'red' })
  await navigateHistory('Undo', 'undo1', n03.B, true)
  await picture('N03-Undo1', 20, { '0.4': 'red' })
  await navigateHistory('Undo', 'undo2', n03.A)
  await picture('N03-Undo2', 0, { '0.15': 'red' })
  await navigateHistory('Redo', 'redo1', n03.B, true)
  await picture('N03-Redo1', 20, { '0.15': 'black', '0.4': 'red' })
  const redone = await navigateHistory('Redo', 'redo2', n03.C)
  await page.reload()
  await waitView('main', redone)
  await picture('N03-Reopened', 40, { '0.4': 'black', '0.6': 'red' })
  await checkpoint('n03-reopened')
  const first = await uiTrim('new-base', 10)
  await navigateHistory('Undo', 'new-undo', redone)
  assert.equal(await redo.isEnabled(), true)
  await uiTrim('new-trim', 20)
  assert.equal(await redo.isDisabled(), true)
  await page.locator('.ff-revision-list button').filter({ hasText: first.id }).click()
  await waitView('main', first.id)
  assert.equal(await controls.count(), 0)
  const browsed = await checkpoint('n03-browse')
  await page.getByRole('button', { name: 'Return to editing head' }).click()
  await waitView('main', browsed.refs.main)

  async function staleHistory(direction, label) {
    const current = await branch()
    const competitor = trim('n03-competitor-' + label, 'main', current.headRevisionId, current.contentHash, '1/8')
    const plan = await api(batch + '/preview', 200, { body: competitor, client: second.request })
    const accepted = await api(batch + '/apply', 201, { body: competitor, digest: plan.batchDigest, client: second.request })
    const state = await checkpoint('n03-' + label + '-before')
    const refused = page.waitForResponse(response => response.url().includes(batch + '/') && response.status() === 409)
    await (direction === 'Undo' ? undo : redo).click()
    const failure = await refused
    const body = failure.request().postDataJSON()
    const payload = await failure.json()
    assert.equal(body.baseRevisionId, current.headRevisionId)
    await controls.getByText(new RegExp('^' + direction + ' conflict')).waitFor()
    assert.equal(await controls.getByRole('button', { name: /Retry/ }).count(), 0)
    const after = await checkpoint('n03-' + label + '-after')
    assert.deepEqual(after, state)
    await reloadHead.click()
    await waitView('main', accepted.newRevisionId)
    n03.conflicts.push({ direction, status: 409, code: payload.errorCode, body, state, after })
    return accepted.newRevisionId
  }

  const headAfterConflict = await staleHistory('Undo', 'stale-undo')
  await uiTrim('pre-redo-conflict', 10)
  await navigateHistory('Undo', 'prep-redo', headAfterConflict)
  await staleHistory('Redo', 'stale-redo')
  await select('alternate')
  await waitView('alternate', independent.refs.alternate)
  assert.equal(await controls.count(), 0)
  await select('main')
  assert.equal(await undo.isDisabled(), true)
  await checkpoint('n03-branch-return')
  result.http.n03 = { ...n03, initial: nA, reopenedRevision: redone }
  save('N03 default-branch undo, redo, replay, conflicts and isolated navigation')

  // Recreate the playable two-source fixture, placing blue one second after red.
  const prePaste = await branch()
  const restoreForPaste = { ...reset, batchId: 'n04-seed', baseRevisionId: prePaste.headRevisionId, baseContentHash: prePaste.contentHash }
  const restoredForPaste = await apply(restoreForPaste, (await preview(restoreForPaste)).batchDigest)
  const gapEdit = { batchId: 'n04-fixture-gap', baseRevisionId: restoredForPaste.newRevisionId,
    baseContentHash: restoredForPaste.newContentHash, operations: [{ definitionId: 'timeline.move',
      target: { clipId: 'clip-blue' }, parameters: { absolute: true, delta: '3/1' } }] }
  const beforeFx = await apply(gapEdit, (await preview(gapEdit)).batchDigest)
  const effectEdit = { batchId: 'n04-inline-effect', baseRevisionId: beforeFx.newRevisionId,
    baseContentHash: beforeFx.newContentHash, operations: [{ definitionId: 'timeline.clip.effect.set', version: '2.0',
      target: { clipId: 'clip-red' }, parameters: { brightness: 0.1, contrast: 1, saturation: 1, grayscale: false,
        fadeInSeconds: 0, fadeOutSeconds: 0, enabled: true } }] }
  const gapHead = await apply(effectEdit, (await preview(effectEdit)).batchDigest)
  await page.reload()
  await waitView('main', gapHead.newRevisionId)

  const clipboard = page.getByRole('region', { name: 'Clip clipboard' })
  const pastePreview = clipboard.getByRole('button', { name: 'Preview paste at playhead', exact: true })
  const pasteConfirm = clipboard.getByRole('button', { name: 'Confirm paste', exact: true })
  const pasteRetry = clipboard.getByRole('button', { name: 'Retry confirmed paste', exact: true })
  const snapshot = async id => JSON.parse((await api(`/api/render/projects/${project}/timeline/revisions/${id}/snapshot`, 200)).canonicalTimelineJson)
  const n04 = { base: gapHead.newRevisionId }
  async function seek(at) {
    await page.getByLabel('Timeline preview seek').evaluate((input, seconds) => {
      Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set.call(input, seconds)
      input.dispatchEvent(new Event('input', { bubbles: true }))
      input.dispatchEvent(new Event('change', { bubbles: true }))
    }, at)
  }
  async function copyPair() {
    // Reverse the checkbox click order to prove authored chronology wins.
    await clipboard.getByRole('checkbox', { name: 'Copy clip clip-blue', exact: true }).check()
    await clipboard.getByRole('checkbox', { name: 'Copy clip clip-red', exact: true }).check()
    await clipboard.getByRole('button', { name: 'Copy selected clips' }).click()
    await clipboard.getByText('2 clips copied.', { exact: true }).waitFor()
  }
  async function previewPasteAt(at) {
    await seek(at)
    const planned = page.waitForResponse(res => res.url().endsWith(batch + '/preview') && res.request().method() === 'POST')
    await pastePreview.click()
    const response = await planned
    assert.equal(response.status(), 200)
    await pasteConfirm.waitFor()
    return { body: response.request().postDataJSON(), digest: (await response.json()).batchDigest }
  }

  const beforePaste = await checkpoint('n04-before')
  const sourceSnapshot = await snapshot(n04.base)
  const track = sourceSnapshot.tracks.find(t => t.trackId === 'track-1')
  const sources = [track.clips.find(c => c.clipId === 'clip-red'), track.clips.find(c => c.clipId === 'clip-blue')]
  const t = stamp => { const [ticks, scale = '1'] = stamp.split('/'); return Number(ticks) / Number(scale) }
  assert.deepEqual(sources.map(c => [t(c.startTime), t(c.endTime)]), [[0, 2], [3, 3.5]])
  await copyPair()
  assert.deepEqual(await checkpoint('n04-copied'), beforePaste)
  const planned = await previewPasteAt(3.5)
  const params = planned.body.operations[0].parameters
  assert.equal(planned.body.operations[0].definitionId, 'timeline.clip.paste')
  assert.deepEqual(params.sourceClipIds, ['clip-red', 'clip-blue'])
  assert.equal(t(params.timelineStart), 3.5)
  assert.equal(new Set(params.newClipIds).size, 2)
  assert.deepEqual(await checkpoint('n04-preview'), beforePaste)
  const invalid = structuredClone(planned.body)
  invalid.batchId = 'n04-invalid-second'
  invalid.operations[0].parameters.newClipIds[1] = 'clip-blue'
  const invalidPreview = await preview(invalid, 422)
  const invalidApply = await apply(invalid, planned.digest, 422)
  assert.match(invalidApply.detail, /clip identity already exists: clip-blue/)
  assert.deepEqual(await checkpoint('n04-invalid'), beforePaste)
  const denied = await apply(planned.body, planned.digest, 403, reader)
  assert.deepEqual(await checkpoint('n04-reader'), beforePaste)
  const pasteWire = []
  await page.route('**' + batch + '/apply', async route => {
    const req = route.request(), response = await route.fetch()
    pasteWire.push({ body: req.postData(), ifMatch: req.headers()['if-match'], status: response.status(), receipt: await response.json() })
    if (pasteWire.length === 1) await route.abort('failed')
    else await route.fulfill({ response })
  })
  await pasteConfirm.click()
  await pasteRetry.waitFor()
  const committedPaste = await checkpoint('n04-committed')
  const pasteRevision = pasteWire[0].receipt.newRevisionId
  assert.equal(committedPaste.refs.main, pasteRevision)
  assert.equal(committedPaste.rows, beforePaste.rows + 1)
  assert.equal(await page.getByRole('combobox', { name: 'Editing branch' }).isDisabled(), true)
  assert.equal(await deleteClip.isDisabled(), true)
  await pasteRetry.click()
  await clipboard.getByText('Paste accepted at revision ' + pasteRevision + '.', { exact: true }).waitFor()
  await page.unroute('**' + batch + '/apply')
  assert.deepEqual(pasteWire.map(entry => entry.status), [201, 200])
  assert.equal(pasteWire[0].body, pasteWire[1].body)
  assert.equal(pasteWire[0].ifMatch, '"' + planned.digest + '"')
  assert.equal(pasteWire[0].ifMatch, pasteWire[1].ifMatch)
  assert.equal(pasteWire[1].receipt.newRevisionId, pasteRevision)
  assert.equal(pasteWire[1].receipt.replayed, true)
  assert.deepEqual(await checkpoint('n04-replay'), committedPaste)

  await page.reload()
  await waitView('main', pasteRevision)
  const saved = (await snapshot(pasteRevision)).tracks.find(t => t.trackId === 'track-1')
  assert.equal(saved.clips.length, track.clips.length + 2)
  for (const original of track.clips) assert.deepEqual(saved.clips.find(c => c.clipId === original.clipId), original)
  const copies = params.newClipIds.map(id => saved.clips.find(c => c.clipId === id))
  assert.deepEqual(copies.map(c => [t(c.startTime), t(c.endTime)]), [[3.5, 5.5], [6.5, 7]])
  for (let ix = 0; ix < copies.length; ix++) {
    const { clipId: _i, startTime: _s, endTime: _e, ...semantic } = copies[ix]
    const { clipId: _oi, startTime: _os, endTime: _oe, ...sourceSemantic } = sources[ix]
    const withoutEffectIds = ({ effects, ...rest }) => ({
      ...rest, effects: (effects ?? []).map(({ id: _id, ...effect }) => effect),
    })
    assert.deepEqual(withoutEffectIds(semantic), withoutEffectIds(sourceSemantic))
    if (ix === 0) {
      assert.equal(sourceSemantic.effects.length, 1)
      assert.notEqual(semantic.effects[0].id, sourceSemantic.effects[0].id)
    }
  }
  assert.deepEqual(await checkpoint('n04-reopened'), committedPaste)
  const canvas = page.getByLabel('Timeline preview canvas', { exact: true })
  n04.frames = {}
  for (const [at, channel] of [[3.75, 0], [6, -1], [6.7, 2]]) {
    await seek(at)
    await page.waitForFunction(wanted => {
      const elt = document.querySelector('canvas[aria-label="Timeline preview canvas"]')
      const rgb = Array.from(elt.getContext('2d').getImageData(elt.width / 2, elt.height / 2, 1, 1).data).slice(0, 3)
      return wanted < 0 ? Math.max(...rgb) < 25 : rgb[wanted] > 80 && rgb[wanted] > 2 * Math.max(...rgb.filter((_, i) => i !== wanted))
    }, channel)
    n04.frames[at] = await canvas.evaluate(elt =>
      Array.from(elt.getContext('2d').getImageData(elt.width / 2, elt.height / 2, 1, 1).data).slice(0, 3))
    await canvas.screenshot({ path: join(out, `N04-${at}-picture.png`) })
  }
  await page.getByRole('group', { name: 'Timeline trim' }).screenshot({ path: join(out, 'N04-reopened-timeline.png') })

  await copyPair()
  const stalePlan = await previewPasteAt(7)
  const competingPasteEdit = trim('n04-competing-trim', 'main', pasteRevision, pasteWire[0].receipt.newContentHash, '1/8')
  const competingPastePlan = await api(batch + '/preview', 200, { body: competingPasteEdit, client: second.request })
  const competingPasteReceipt = await api(batch + '/apply', 201, { body: competingPasteEdit,
    digest: competingPastePlan.batchDigest, client: second.request })
  const beforeStalePaste = await checkpoint('n04-stale-before')
  const stalePasteResponse = page.waitForResponse(res => res.url().endsWith(batch + '/apply') && res.request().method() === 'POST')
  await pasteConfirm.click()
  const refusal = await stalePasteResponse
  assert.equal(refusal.status(), 409)
  assert.deepEqual(refusal.request().postDataJSON(), stalePlan.body)
  const stalePasteError = await refusal.json()
  assert.equal(stalePasteError.errorCode, 'STALE_BASE_REVISION')
  await clipboard.getByText(/Paste conflict/).waitFor()
  assert.deepEqual(await checkpoint('n04-stale-after'), beforeStalePaste)
  await reloadHead.click()
  await waitView('main', competingPasteReceipt.newRevisionId)
  await select('alternate')
  await waitView('alternate', independent.refs.alternate)
  assert.equal(await clipboard.count(), 0)
  await select('main')
  await waitView('main', competingPasteReceipt.newRevisionId)
  assert.equal(await pastePreview.isDisabled(), true)
  assert.deepEqual(await checkpoint('n04-branch-return'), beforeStalePaste)
  result.http.n04 = { ...n04, pasteRevision, sources, copies, pasteWire,
    invalidPreview: invalidPreview.errorCode, invalidApply: invalidApply.errorCode,
    denied: denied.errorCode, stale: stalePasteError.errorCode, competitor: competingPasteReceipt.newRevisionId }
  save('N04 atomic clipboard, reopening, exact retry, invalid second source, authorization and stale confirmation')

  const exactEndpoints = []
  for (const [label, blueStart, exactEnd] of [['centisecond', '151/100', '201/100'], ['fractional', '23/15', '61/30']]) {
    const previous = await branch()
    const seedBody = { ...reset, batchId: 'n04-f1-reset-' + label,
      baseRevisionId: previous.headRevisionId, baseContentHash: previous.contentHash }
    const seedHead = await apply(seedBody, (await preview(seedBody)).batchDigest)
    const fixture = { batchId: 'n04-f1-fixture-' + label, baseRevisionId: seedHead.newRevisionId,
      baseContentHash: seedHead.newContentHash, operations: [
        { definitionId: 'timeline.trim', target: { clipId: 'clip-red' }, parameters: { edge: 'END', delta: '1/2' } },
        { definitionId: 'timeline.move', target: { clipId: 'clip-blue' }, parameters: { absolute: true, delta: blueStart } },
      ] }
    const baseHead = await apply(fixture, (await preview(fixture)).batchDigest)
    await page.reload()
    await waitView('main', baseHead.newRevisionId)
    const realOriginals = (await snapshot(baseHead.newRevisionId)).tracks.find(t => t.trackId === 'track-1').clips
    assert.equal(realOriginals.find(c => c.clipId === 'clip-blue').endTime, exactEnd)
    const cp = stage => checkpoint('n04-f1-' + label + '-' + stage)
    const initial = await cp('before')
    await copyPair()

    await seek(t(exactEnd) - 0.0000003)
    const overlaps = page.waitForResponse(res => res.url().endsWith(batch + '/preview') && res.request().method() === 'POST')
    await pastePreview.click()
    const overlap = await overlaps
    assert.equal(overlap.status(), 422)
    const overlapBody = overlap.request().postDataJSON()
    const earlier = overlapBody.operations[0].parameters.timelineStart
    assert.ok(t(earlier) < t(exactEnd), 'Do not snap earlier requests to the endpoint')
    assert.match((await overlap.json()).detail, /would overlap/)
    const rejectedApply = await apply(overlapBody, '0'.repeat(64), 422)
    assert.match(rejectedApply.detail, /would overlap/)
    await clipboard.getByText(/Paste preview rejected/).waitFor()
    assert.equal(await pasteConfirm.count(), 0)
    assert.deepEqual(await cp('overlap'), initial)

    // Exercise the native slider's End key rather than supplying the exact fraction to the client.
    await page.getByLabel('Timeline preview seek').press('End')
    const endpointRequest = page.waitForResponse(res => res.url().endsWith(batch + '/preview') && res.request().method() === 'POST')
    await pastePreview.click()
    const atEnd = await endpointRequest
    assert.equal(atEnd.status(), 200)
    const endpointBody = atEnd.request().postDataJSON()
    assert.equal(endpointBody.operations[0].parameters.timelineStart, exactEnd)
    const ids = endpointBody.operations[0].parameters.newClipIds
    assert.equal(ids.length, 2)
    await pasteConfirm.waitFor()
    assert.deepEqual(await cp('preview'), initial)
    const writeResponse = page.waitForResponse(res => res.url().endsWith(batch + '/apply') && res.request().method() === 'POST')
    await pasteConfirm.click()
    const write = await writeResponse
    assert.equal(write.status(), 201)
    assert.deepEqual(write.request().postDataJSON(), endpointBody)
    const { batchDigest } = await atEnd.json()
    assert.equal(write.request().headers()['if-match'], '"' + batchDigest + '"')
    const { newRevisionId } = await write.json()
    await clipboard.getByText('Paste accepted at revision ' + newRevisionId + '.', { exact: true }).waitFor()
    const actual = await cp('commit')
    assert.equal(actual.rows, initial.rows + 1)
    assert.equal(actual.refs.main, newRevisionId)

    await page.reload()
    await waitView('main', newRevisionId)
    const reopenedTrack = (await snapshot(newRevisionId)).tracks.find(t => t.trackId === 'track-1')
    const copiesAtEnd = ids.map(id => reopenedTrack.clips.find(clip => clip.clipId === id))
    assert.equal(copiesAtEnd[0].startTime, exactEnd)
    assert.equal(reopenedTrack.clips.length, realOriginals.length + 2)
    assert.deepEqual(await cp('reopened'), actual)
    exactEndpoints.push({ label, exactEnd, earlier, base: baseHead.newRevisionId, accepted: newRevisionId,
      endpointBody, overlapBody, copies: copiesAtEnd, overlapPreview: overlap.status(),
      overlapApply: 422, apply: write.status() })
  }
  result.http.n04.endpointRegression = exactEndpoints
  save('N04 F1 exact timeline endpoints and no-write genuine overlap')

  const currentN05 = await branch()
  const seedN05 = { ...reset, batchId: 'n05-seed', baseRevisionId: currentN05.headRevisionId, baseContentHash: currentN05.contentHash }
  const seededN05 = await apply(seedN05, (await preview(seedN05)).batchDigest)
  const clipsOn = document => document.tracks.find(t => t.trackId === 'track-1').clips
  const originalsN05 = clipsOn(await snapshot(seededN05.newRevisionId))
  const addN05 = (id, source, start, end) => ({ definitionId: 'timeline.media-clip.add', parameters: {
    trackId: 'track-1', clipId: id, mediaAssetId: source.artifactId, mediaStreamId: source.mediaStreamId,
    artifactId: source.artifactId, contentDigest: source.contentDigest, sourceStart: '0', sourceEnd: '1',
    timelineStart: start, timelineEnd: end, rateNumerator: 1, rateDenominator: 1, direction: 'FORWARD' } })
  const makeMove = (target, delta, absolute = false) => ({
    definitionId: 'timeline.move', target, parameters: { delta, absolute } })
  const editsN05 = { batchId: 'n05-three-clips', baseRevisionId: seededN05.newRevisionId,
    baseContentHash: seededN05.newContentHash, operations: [
      { definitionId: 'timeline.delete', target: { clipId: 'clip-blue' } },
      { definitionId: 'timeline.trim', target: { clipId: 'clip-red' }, parameters: { edge: 'END', delta: '1' } },
      addN05('clip-blue', originalsN05.find(c => c.clipId === 'clip-blue'), '1', '2'),
      addN05('clip-gray', originalsN05.find(c => c.clipId === 'clip-red'), '4', '5'),
      { definitionId: 'timeline.clip.effect.set', version: '2.0', target: { clipId: 'clip-gray' },
        parameters: { brightness: 0, contrast: 1, saturation: 1, grayscale: true,
          fadeInSeconds: 0, fadeOutSeconds: 0, enabled: true } },
    ] }
  const n05Base = await apply(editsN05, (await preview(editsN05)).batchDigest)
  await page.reload()
  await waitView('main', n05Base.newRevisionId)
  const moveTools = page.getByRole('region', { name: 'Move selected clips' })
  const movePreview = moveTools.getByRole('button', { name: 'Preview selected move' })
  const moveConfirm = moveTools.getByRole('button', { name: 'Confirm selected move' })
  const moveRetry = moveTools.getByRole('button', { name: 'Retry confirmed move' })
  const moveSelect = id => moveTools.getByRole('checkbox', { name: 'Select for move ' + id })
  const cp5 = name => checkpoint('n05-' + name)
  const wire5 = suffix => page.waitForResponse(res => res.url().endsWith(batch + suffix) && res.request().method() === 'POST')
  async function pair5() {
    await moveSelect('clip-red').check()
    await moveSelect('clip-blue').check()
    await moveTools.getByText('2 selected', { exact: true }).waitFor()
  }
  async function plan5(delta, expected = 200) {
    await moveTools.getByLabel('Move offset (seconds)').fill(delta)
    const awaited = wire5('/preview')
    await movePreview.click()
    const res = await awaited
    const payload = await res.json()
    assert.equal(res.status(), expected, JSON.stringify(payload))
    if (expected === 200) await moveConfirm.waitFor()
    return { body: res.request().postDataJSON(), payload }
  }
  const before5 = await cp5('before')
  await pair5()
  await moveTools.getByRole('button', { name: 'Clear move selection' }).click()
  await moveTools.getByText('0 selected', { exact: true }).waitFor()
  await pair5()
  assert.deepEqual(await cp5('selection'), before5)
  const collision5 = await plan5('3', 422)
  assert.match(collision5.payload.detail, /clip-blue would overlap clip-gray/)
  assert.match((await apply(collision5.body, '0'.repeat(64), 422)).detail, /would overlap/)
  assert.deepEqual(await cp5('collision'), before5)
  const negativeRejection = await plan5('-1', 422)
  assert.match(negativeRejection.payload.detail, /negative start/)
  assert.match((await apply(negativeRejection.body, '0'.repeat(64), 422)).detail, /negative start/)
  assert.deepEqual(await cp5('negative-reject'), before5)
  const positive5 = await plan5('1')
  const denied5 = await apply(positive5.body, positive5.payload.batchDigest, 403, reader)
  const isolated5 = await api(batch.replace(project, 'project-peer') + '/apply', 404,
    { body: positive5.body, digest: positive5.payload.batchDigest })
  assert.deepEqual(await cp5('preview'), before5)
  const moveWire = []
  await page.route('**' + batch + '/apply', async route => {
    const req = route.request(), res = await route.fetch()
    moveWire.push({ target: req.url(), body: req.postData(), ifMatch: req.headers()['if-match'],
      status: res.status(), receipt: await res.json() })
    if (moveWire.length === 1) await route.abort('failed')
    else await route.fulfill({ response: res })
  })
  await moveConfirm.click()
  await moveRetry.waitFor()
  const committed5 = await cp5('committed')
  const moveRevision = moveWire[0].receipt.newRevisionId
  assert.equal(committed5.refs.main, moveRevision)
  assert.equal(committed5.rows, before5.rows + 1)
  assert.equal(committed5.commands, before5.commands + 1)
  assert.equal(await deleteClip.isDisabled(), true)
  assert.equal(await page.getByRole('combobox', { name: 'Editing branch' }).isDisabled(), true)
  await moveRetry.click()
  await moveTools.getByText('Move accepted at revision ' + moveRevision + '.', { exact: true }).waitFor()
  await page.unroute('**' + batch + '/apply')
  assert.deepEqual(moveWire.map(x => x.status), [201, 200])
  for (const key of ['target', 'body', 'ifMatch']) assert.equal(moveWire[0][key], moveWire[1][key])
  assert.equal(moveWire[0].ifMatch, '"' + positive5.payload.batchDigest + '"')
  assert.equal(moveWire[1].receipt.newRevisionId, moveRevision)
  assert.equal(moveWire[1].receipt.replayed, true)
  assert.deepEqual(await cp5('replay'), committed5)
  await page.reload()
  await waitView('main', moveRevision)
  const mediaSamples = {}
  const canvas5 = page.getByLabel('Timeline preview canvas', { exact: true })
  for (const [at, colour] of [['1.4', 'red'], ['2.4', 'blue'], ['4.4', 'gray']]) {
    await seek(at)
    await page.waitForFunction(colour => {
      const c = document.querySelector('canvas[aria-label="Timeline preview canvas"]')
      const [r, g, b] = c.getContext('2d').getImageData(c.width / 2, c.height / 2, 1, 1).data
      return colour === 'red' ? r > 80 && r > 2 * Math.max(g, b) : colour === 'blue' ? b > 80 && b > 2 * Math.max(r, g)
        : Math.min(r, g, b) > 30 && Math.max(r, g, b) - Math.min(r, g, b) < 6
    }, colour)
    mediaSamples[at] = await canvas5.evaluate(c =>
      Array.from(c.getContext('2d').getImageData(c.width / 2, c.height / 2, 1, 1).data).slice(0, 3))
    await canvas5.screenshot({ path: join(out, 'n05-' + colour + '-picture.png') })
  }
  await page.getByRole('group', { name: 'Timeline trim' }).screenshot({ path: join(out, 'n05-reopened-timeline.png') })
  assert.deepEqual(await cp5('reopened'), committed5)
  await pair5()
  const zero5 = await plan5('0')
  assert.equal(zero5.payload.noOp, true)
  const zeroWire = wire5('/apply')
  await moveConfirm.click()
  const zeroResponse = await zeroWire
  assert.equal(zeroResponse.status(), 200)
  assert.equal((await zeroResponse.json()).status, 'NO_OP')
  await moveTools.getByText(/No revision was added/).waitFor()
  const checkpointZero = await cp5('zero')
  assert.equal(checkpointZero.rows, committed5.rows)
  assert.deepEqual(checkpointZero.refs, committed5.refs)

  const addGap5 = { batchId: 'n05-rational-gap', baseRevisionId: moveRevision,
    baseContentHash: moveWire[0].receipt.newContentHash, operations: [makeMove({ clipId: 'clip-blue' }, '61/30', true)] }
  const gapReceipt5 = await apply(addGap5, (await preview(addGap5)).batchDigest)
  const gap5 = await cp5('gap-base')
  await page.reload()
  await waitView('main', gapReceipt5.newRevisionId)
  await pair5()
  const backwards5 = await plan5('-1/30')
  assert.equal(backwards5.body.operations[0].parameters.delta, '-1/30')
  const negativeWire = wire5('/apply')
  await moveConfirm.click()
  const negativeResponse = await negativeWire
  assert.equal(negativeResponse.status(), 201)
  const negativeReceipt5 = await negativeResponse.json()
  await waitView('main', negativeReceipt5.newRevisionId)
  assert.equal((await cp5('negative')).rows, gap5.rows + 1)

  await pair5()
  await plan5('1/30')
  const competitor5 = trim('n05-other-session', 'main', negativeReceipt5.newRevisionId, negativeReceipt5.newContentHash, '1/8')
  const other5 = await api(batch + '/apply', 201, { body: competitor5, digest: (await preview(competitor5)).batchDigest, client: second.request })
  const staleBase5 = await cp5('stale-before')
  const staleWire = wire5('/apply')
  await moveConfirm.click()
  const staleResponse = await staleWire
  assert.equal(staleResponse.status(), 409)
  assert.equal((await staleResponse.json()).errorCode, 'STALE_BASE_REVISION')
  await moveTools.getByText(/Move conflict/).waitFor()
  assert.deepEqual(await cp5('stale-after'), staleBase5)
  await reloadHead.click()
  await waitView('main', other5.newRevisionId)
  await select('alternate')
  await waitView('alternate', independent.refs.alternate)
  assert.equal(await moveTools.count(), 0)
  await select('main')
  await waitView('main', other5.newRevisionId)
  await moveTools.getByText('0 selected', { exact: true }).waitFor()
  result.http.n05 = { base: n05Base.newRevisionId, moved: moveRevision, gapBase: gapReceipt5.newRevisionId,
    reversed: negativeReceipt5.newRevisionId, competitor: other5.newRevisionId, mediaSamples, moveWire,
    collision: { preview: collision5.payload.errorCode, apply: 422 }, negativeStart: negativeRejection.payload.errorCode,
    authorization: { reader: denied5.errorCode, foreign: isolated5.errorCode }, zero: zeroResponse.status(), stale: staleResponse.status() }
  save('N05 atomic common offset, overlap rollback, exact replay, reopen, signed rational gap, zero and stale conflict')

  const evidence6 = { moves: [], deletions: [], offsets: [], invalid: [] }
  const snap = page.getByRole('button', { name: 'Snap', exact: true })
  const clipEditor6 = page.getByRole('region', { name: 'Edit existing clip' })
  const feedback6 = clipEditor6.locator('[data-snap-feedback]')
  const c6 = name => checkpoint('n06-' + name)
  const clip6 = name => page.getByLabel('Timeline clip ' + name, { exact: true })
  async function setSnap6(enabled) {
    if ((await snap.getAttribute('aria-pressed')) !== String(enabled)) await snap.press('Space')
    await page.waitForFunction(on => document.querySelector('button[aria-pressed=' + on + ']') &&
      [...document.querySelectorAll('button')].some(b => b.textContent.trim() === 'Snap' && b.getAttribute('aria-pressed') === on),
    String(enabled))
  }
  async function revise6(label, operations, expected = 201) {
    const current = await branch()
    const body = { batchId: 'n06-' + label, baseRevisionId: current.headRevisionId,
      baseContentHash: current.contentHash, operations }
    return apply(body, (await preview(body)).batchDigest, expected)
  }
  async function restore6(label, source = n05Base.newRevisionId) {
    return revise6(label, [{ definitionId: 'timeline.revision.restore', parameters: { sourceRevisionId: source } }])
  }
  async function view6(revision) {
    await page.reload()
    await waitView('main', revision)
  }
  async function drag6(locator, px, inspect = async () => {}) {
    await locator.scrollIntoViewIfNeeded()
    const box = await locator.boundingBox()
    assert.ok(box, 'drag source visible')
    const x = box.x + box.width / 2, y = box.y + box.height / 2
    await page.mouse.move(x, y)
    await page.mouse.down()
    await page.mouse.move(x + px, y, { steps: 8 })
    await inspect()
    await page.mouse.up()
  }
  const wire6 = suffix => {
    const response = wire5(suffix)
    // An inspector can fail before pointer-up. Keep that error visible if its response waiter times out too.
    response.catch(() => {})
    return response
  }
  const trace6 = async res => ({ target: res.url(), body: res.request().postDataJSON(),
    raw: res.request().postData(), ifMatch: res.request().headers()['if-match'], status: res.status(), payload: await res.json() })
  function confirmed6(planned, applied) {
    assert.equal(planned.status, 200)
    assert.equal(applied.status, 201)
    assert.equal(applied.raw, planned.raw)
    assert.equal(applied.ifMatch, '"' + planned.payload.batchDigest + '"')
    assert.equal(applied.payload.parentRevisionId, applied.body.baseRevisionId)
  }
  const localRequests6 = []
  page.on('request', req => {
    if (req.method() === 'POST' && req.url().includes(batch + '/')) localRequests6.push(req.url())
  })

  // A stationary clip ends at 61/30. Run the same drag from the same authored state twice.
  await restore6('single-seed')
  const stationary6 = await revise6('single-track', [
    { definitionId: 'timeline.delete', target: { clipId: 'clip-gray' } },
    makeMove({ clipId: 'clip-red' }, '4', true),
    makeMove({ clipId: 'clip-blue' }, '31/30', true),
  ])
  for (const [label, enabled, expected] of [['free', false, '21/10'], ['edge', true, '61/30']]) {
    const current = enabled ? await restore6('single-restore', stationary6.newRevisionId) : stationary6
    await view6(current.newRevisionId)
    await setSnap6(enabled)
    const before = await c6(label + '-before')
    if (enabled) {
      const calls = localRequests6.length
      await drag6(clip6('clip-red'), -152, async () => {
        assert.match(await feedback6.innerText(), /clip-red start to clip-blue end at 61\/30s/)
        assert.equal(localRequests6.length, calls)
        await page.keyboard.press('Escape')
        assert.equal(await feedback6.count(), 0)
      })
      assert.equal(localRequests6.length, calls)
      assert.deepEqual(await c6('cancel'), before)
    }
    const planned = wire6('/preview')
    const written = wire6('/apply')
    let guide = null
    await drag6(clip6('clip-red'), -152, async () => {
      await clipEditor6.getByText(/Moving clip-red to/).waitFor({ timeout: 5000 })
      if (enabled) {
        guide = await feedback6.innerText()
        assert.match(guide, /clip-red start to clip-blue end at 61\/30s/)

      } else assert.equal(await feedback6.count(), 0)
    })
    const p = await trace6(await planned), a = await trace6(await written)
    confirmed6(p, a)
    assert.equal(p.body.operations[0].parameters.delta, expected)
    await view6(a.payload.newRevisionId)
    assert.equal((await c6(label + '-after')).rows, before.rows + 1)
    evidence6.moves.push({ label, expected, guide, preview: p, apply: a, revision: a.payload.newRevisionId })
    console.log('N06_BROWSER_CASE', label, 'PASS')
  }

  // The playhead at the exact canonical endpoint wins an equal-distance tie with the blue clip end.
  await restore6('endpoint-seed', stationary6.newRevisionId)
  const endpoint6 = await revise6('endpoint-track', [makeMove({ clipId: 'clip-red' }, '0', true)])
  await view6(endpoint6.newRevisionId)
  await setSnap6(true)
  await page.getByLabel('Timeline preview seek').press('End')
  const playBefore = await c6('playhead-before')
  const playPreview = wire6('/preview'), playWrite = wire6('/apply')
  let playGuide
  await drag6(clip6('clip-red'), 166, async () => {
    playGuide = await feedback6.innerText()
    assert.match(playGuide, /clip-red start to playhead at 61\/30s/)
  })
  const pp = await trace6(await playPreview), pa = await trace6(await playWrite)
  confirmed6(pp, pa)
  assert.equal(pp.body.operations[0].parameters.delta, '61/30')
  await view6(pa.payload.newRevisionId)
  assert.equal((await c6('playhead-after')).rows, playBefore.rows + 1)
  evidence6.moves.push({ label: 'playhead', expected: '61/30', guide: playGuide, preview: pp, apply: pa, revision: pa.payload.newRevisionId })

  console.log('N06_BROWSER_CASE playhead PASS')
  const trimBase6 = await restore6('trim-seed', endpoint6.newRevisionId)
  await view6(trimBase6.newRevisionId)
  await setSnap6(true)
  await seek('0.5')
  const trimBefore6 = await c6('trim-before')
  const trimPreview6 = wire6('/preview')
  let trimGuide6
  await drag6(page.getByLabel('Trim end edge clip-red', { exact: true }), -37, async () => {
    trimGuide6 = await feedback6.innerText()
    assert.match(trimGuide6, /clip-red end to playhead at 1\/2s/)
  })
  const tp = await trace6(await trimPreview6)
  assert.equal(tp.status, 200)
  assert.deepEqual(tp.body.operations[0].parameters, { edge: 'END', delta: '1/2' })
  assert.deepEqual(await c6('trim-preview'), trimBefore6)
  const trimCommit6 = wire6('/apply')
  await page.getByRole('button', { name: 'Apply trim', exact: true }).click()
  const ta = await trace6(await trimCommit6)
  confirmed6(tp, ta)
  await view6(ta.payload.newRevisionId)
  assert.equal((await c6('trim-after')).rows, trimBefore6.rows + 1)
  evidence6.moves.push({ label: 'trim', expected: '1/2', guide: trimGuide6, preview: tp, apply: ta, revision: ta.payload.newRevisionId })

  console.log('N06_BROWSER_CASE trim PASS')
  await restore6('pair-seed')
  const pairBase6 = await revise6('pair-track', [
    makeMove({ clipId: 'clip-blue' }, '2', true), makeMove({ clipId: 'clip-red' }, '1/2', true),
  ])
  await view6(pairBase6.newRevisionId)
  await setSnap6(true)
  await pair5()
  const pairBefore6 = await c6('pair-before')
  for (const [form, expected] of [['+2/60','1/30'], ['-2/60','-1/30'], ['0','0/1']]) {
    const prepared = await plan5(form)
    assert.equal(prepared.body.operations[0].parameters.delta, expected)
    evidence6.offsets.push({ form, expected, observed: prepared.body.operations[0].parameters.delta })
  }
  for (const form of ['+-2/60', '-+2/60', '++1', '--1']) {
    const noRequests = localRequests6.length
    await moveTools.getByLabel('Move offset (seconds)').fill(form)
    await moveTools.getByRole('alert').filter({ hasText: 'at most one sign' }).waitFor()
    assert.equal(await moveConfirm.count(), 0)
    await movePreview.click()
    assert.equal(localRequests6.length, noRequests)
    evidence6.invalid.push({ form, requests: localRequests6.length - noRequests })
  }
  assert.deepEqual(await c6('invalid-offsets'), pairBefore6)

  const illegalPreview6 = wire6('/preview')
  await drag6(clip6('clip-red'), 283, async () => {
    assert.match(await feedback6.innerText(), /clip-red start to clip-gray start at 4\/1s/)
  })
  const illegal6 = await trace6(await illegalPreview6)
  assert.equal(illegal6.status, 422)
  assert.match(illegal6.payload.detail, /overlap/)
  await moveTools.getByRole('alert').filter({ hasText: 'rejected' }).waitFor()
  assert.equal(await moveConfirm.count(), 0)
  const illegalApply6 = await apply(illegal6.body, '0'.repeat(64), 422)
  assert.match(illegalApply6.detail, /overlap/)
  assert.deepEqual(await c6('illegal'), pairBefore6)
  evidence6.illegal = { preview: illegal6, applyStatus: 422, applyError: illegalApply6.errorCode }

  const legalPreview6 = wire6('/preview')
  let pairGuide6
  await drag6(clip6('clip-red'), 77, async () => {
    pairGuide6 = await feedback6.innerText()
    assert.match(pairGuide6, /clip-blue end to clip-gray start at 4\/1s/)

  })
  const gp = await trace6(await legalPreview6)
  assert.equal(gp.body.operations[0].parameters.delta, '1/1')
  await moveConfirm.waitFor()
  assert.equal(await moveTools.getByText('clip-red · 3/2 to 5/2', { exact: true }).count(), 1)
  assert.equal(await moveTools.getByText('clip-blue · 3/1 to 4/1', { exact: true }).count(), 1)
  assert.deepEqual(await c6('pair-preview'), pairBefore6)
  const pairWire6 = []
  await page.route('**' + batch + '/apply', async route => {
    const res = await route.fetch()
    const req = route.request()
    pairWire6.push({ target: req.url(), body: req.postDataJSON(), raw: req.postData(),
      ifMatch: req.headers()['if-match'], status: res.status(), payload: await res.json() })
    if (pairWire6.length === 1) await route.abort('failed')
    else await route.fulfill({ response: res })
  })
  await moveConfirm.click()
  await moveRetry.waitFor()
  const pairAfter6 = await c6('pair-committed')
  assert.equal(pairAfter6.rows, pairBefore6.rows + 1)
  assert.equal(await snap.isDisabled(), true)
  assert.equal(await deleteClip.isDisabled(), true)
  const lockedRequests6 = localRequests6.length
  await clipEditor6.press('Delete')
  // A disabled native toggle is inert even if a click is dispatched to it.
  await snap.dispatchEvent('click')
  assert.equal(await snap.getAttribute('aria-pressed'), 'true')
  assert.equal(localRequests6.length, lockedRequests6)
  assert.deepEqual(await c6('pair-locked'), pairAfter6)
  await moveRetry.click()
  await moveTools.getByText('Move accepted at revision ' + pairWire6[0].payload.newRevisionId + '.', {exact:true}).waitFor()
  await page.unroute('**' + batch + '/apply')
  confirmed6(gp, pairWire6[0])
  assert.deepEqual(pairWire6.map(r => r.status), [201, 200])
  for (const field of ['target', 'raw', 'ifMatch']) assert.equal(pairWire6[0][field],pairWire6[1][field])
  assert.equal(pairWire6[1].payload.newRevisionId,pairWire6[0].payload.newRevisionId)
  assert.equal(pairWire6[1].payload.replayed,true)
  assert.equal(await snap.isDisabled(), false)
  await view6(pairWire6[0].payload.newRevisionId)
  assert.deepEqual(await c6('pair-replay'), pairAfter6)
  evidence6.moves.push({ label: 'pair', expected: '1/1', guide: pairGuide6, preview: gp,
    apply: pairWire6[0], replay: pairWire6[1], revision: pairWire6[0].payload.newRevisionId })

  console.log('N06_BROWSER_CASE pair PASS')
  await restore6('delete-seed')
  const redPin6 = originalsN05.find(c => c.clipId === 'clip-red')
  const bluePin6 = originalsN05.find(c => c.clipId === 'clip-blue')
  const add6 = (id, media, start, end, sourceEnd, rateDenominator = 1) => {
    const op = addN05(id, media, start, end)
    return { ...op, parameters: { ...op.parameters, sourceEnd, rateDenominator } }
  }
  const deleteBase6 = await revise6('delete-track', [
    ...['clip-red', 'clip-blue', 'clip-gray'].map(id => ({ definitionId: 'timeline.delete', target: { clipId: id } })),
    add6('clip-red', redPin6, '0', '2', '2'), add6('clip-blue', bluePin6, '2', '5', '1', 3),
    add6('clip-gray', redPin6, '5', '7', '2'), add6('clip-later', bluePin6, '8', '9', '1'),
    { definitionId: 'timeline.clip.effect.set', version: '2.0', target: { clipId: 'clip-gray' },
      parameters: { brightness: 0, contrast: 1, saturation: 1, grayscale: true, fadeInSeconds: 0, fadeOutSeconds: 0, enabled: true } },
  ])
  async function samples6(prefix, points) {
    const canvas = page.getByLabel('Timeline preview canvas', { exact: true })
    const samples = {}
    for (const [at, colour] of points) {
      await seek(at)
      await page.waitForFunction(colour => {
        const c = document.querySelector('canvas[aria-label="Timeline preview canvas"]')
        const [r,g,b] = c.getContext('2d').getImageData(c.width/2,c.height/2,1,1).data
        if (colour === 'red') return r > 80 && r > 2 * Math.max(g,b)
        if (colour === 'blue') return b > 80 && b > 2 * Math.max(r,g)
        if (colour === 'black') return Math.max(r,g,b) < 10
        return Math.min(r,g,b) > 30 && Math.max(r,g,b) - Math.min(r,g,b) < 6
      }, colour)
      samples[at] = await canvas.evaluate(c => Array.from(c.getContext('2d').getImageData(c.width/2,c.height/2,1,1).data).slice(0,3))
      await canvas.screenshot({ path: join(out, prefix + '-' + colour + '.png') })
    }
    return samples
  }

  for (const entry of ['button','keyboard']) {
    const initial = entry === 'button' ? deleteBase6 : await restore6('delete-equivalent', deleteBase6.newRevisionId)
    await view6(initial.newRevisionId)
    const before = await c6(entry + '-before')
    const picturesBefore = await samples6('n06-delete-' + entry + '-before', [['3','blue']])
    await clip6('clip-blue').click()
    const dpWire = wire6('/preview'), daWire = wire6('/apply')
    if (entry === 'button') await deleteClip.click()
    else await clip6('clip-blue').press('Delete')
    const dp = await trace6(await dpWire), da = await trace6(await daWire)
    confirmed6(dp, da)
    assert.deepEqual(dp.body.operations[0], { definitionId: 'timeline.delete', target: { clipId: 'clip-blue' } })
    await view6(da.payload.newRevisionId)
    assert.equal(await clip6('clip-blue').count(), 0)
    const picturesAfter = await samples6('n06-delete-' + entry + '-after', [['1','red'],['3','black'],['5.5','gray']])
    await seek('2.65')
    await page.getByRole('button', { name: 'Play Timeline', exact: true }).click()
    await page.waitForFunction(() => {
      const preview = document.querySelector('section[aria-label="Timeline media preview"]')
      const seconds = Number(preview.dataset.previewTime)
      const canvas = preview.querySelector('canvas')
      const [r,g,b] = canvas.getContext('2d').getImageData(canvas.width / 2, canvas.height / 2, 1, 1).data
      return preview.dataset.previewStatus === 'PLAYING' && seconds >= 3 && seconds < 4 && r === 0 && g === 0 && b === 0
    })
    const playingGap = await page.getByRole('region', { name: 'Timeline media preview' }).getAttribute('data-preview-time')
    await page.getByRole('button', { name: 'Pause Timeline', exact: true }).click()
    const after = await c6(entry + '-after')
    assert.equal(after.rows, before.rows + 1)
    assert.equal(after.commands, before.commands + 1)
    evidence6.deletions.push({ entry, before: initial.newRevisionId, revision: da.payload.newRevisionId,
      picturesBefore, picturesAfter, playingGap, preview: dp, apply: da })
  }
  result.http.n06 = evidence6
  save('N06 exact clip and playhead snaps, trim, atomic pair, input validation, retry and ordinary delete by both entry points')

  const c7 = name => checkpoint('n07-' + name)
  const ripplePreview = clipEditor6.getByRole('button', { name: 'Preview ripple delete', exact: true })
  const rippleConfirm = clipEditor6.getByRole('button', { name: 'Confirm ripple delete', exact: true })
  const rippleCancel = clipEditor6.getByRole('button', { name: 'Cancel ripple delete', exact: true })
  const rippleRetry = clipEditor6.getByRole('button', { name: 'Retry Ripple Delete with the same command ID', exact: true })
  const rippleStage = async () => {
    await clip6('clip-blue').click()
    const pending = wire6('/preview')
    await ripplePreview.click()
    return trace6(await pending)
  }
  const base7 = (await restore6('ripple-equivalent', deleteBase6.newRevisionId)).newRevisionId
  await view6(base7)
  const before7 = await c7('before')
  const cancelled7 = await rippleStage()
  assert.equal(cancelled7.status, 200)
  await clipEditor6.getByText('Remove clip-blue. Shift clip-gray, clip-later left by 3/1s on this track.', { exact: true }).waitFor()
  await rippleCancel.click()
  assert.equal(await rippleConfirm.count(), 0)
  assert.deepEqual(await c7('cancel'), before7)
  const planned7 = await rippleStage()
  await rippleConfirm.waitFor()
  assert.deepEqual(planned7.body.operations, [
    { definitionId: 'timeline.clip.ripple-delete', version: '1.0', target: { clipId: 'clip-blue' } },
  ])
  const denied7 = await apply(planned7.body, (await preview(planned7.body, 200, reader)).batchDigest, 403, reader)
  assert.deepEqual(await c7('reader'), before7)
  const exact7 = []
  await page.route('**' + batch + '/apply', async route => {
    const req = route.request(), res = await route.fetch()
    exact7.push({ target: req.url(), body: req.postDataJSON(), raw: req.postData(), ifMatch: req.headers()['if-match'],
      status: res.status(), payload: await res.json() })
    if (exact7.length === 1) await route.abort('failed')
    else await route.fulfill({ response: res })
  })
  try {
    await rippleConfirm.click()
    await rippleRetry.waitFor()
    const locked = await c7('committed')
    assert.equal(locked.rows, before7.rows + 1)
    assert.equal(locked.commands, before7.commands + 1)
    assert.equal(locked.refs.main, exact7[0].payload.newRevisionId)
    assert.equal(await deleteClip.isDisabled(), true)
    assert.equal(await ripplePreview.isDisabled(), true)
    assert.equal(await page.getByRole('combobox', { name: 'Editing branch' }).isDisabled(), true)
    await clipEditor6.press('Delete')
    assert.equal(exact7.length, 1)
    await rippleRetry.click()
    await clipEditor6.getByText('Clip clip-blue ripple deleted at revision ' + locked.refs.main + '.', { exact: true }).waitFor()
    assert.deepEqual(await c7('replay'), locked)
  } finally { await page.unroute('**' + batch + '/apply') }
  confirmed6(planned7, exact7[0])
  assert.deepEqual(exact7.map(x => x.status), [201, 200])
  for (const field of ['target', 'raw', 'ifMatch']) assert.equal(exact7[0][field], exact7[1][field])
  const ripple7 = exact7[0].payload.newRevisionId
  assert.equal(exact7[1].payload.newRevisionId, ripple7)
  await page.getByRole('button', { name: 'Undo ripple delete', exact: true }).waitFor()
  const u7 = wire6('/apply')
  await page.getByRole('button', { name: 'Undo ripple delete', exact: true }).click()
  const undo7 = await trace6(await u7)
  const undoId7 = undo7.payload.newRevisionId
  await waitView('main', undoId7)
  await page.getByRole('button', { name: 'Redo ripple delete', exact: true }).waitFor()
  await c7('undo')
  const r7 = wire6('/apply')
  await page.getByRole('button', { name: 'Redo ripple delete', exact: true }).click()
  const redo7 = await trace6(await r7)
  const redoId7 = redo7.payload.newRevisionId
  await waitView('main', redoId7)
  await c7('redo')
  await view6(redoId7)
  assert.equal(await clip6('clip-blue').count(), 0)
  const pictures7 = await samples6('n07-ripple-reopened', [['1','red'],['2.5','gray'],['4.5','black'],['5.5','blue']])
  await seek('1.75')
  await page.getByRole('button', { name: 'Play Timeline', exact: true }).click()
  await page.waitForFunction(() => {
    const preview = document.querySelector('section[aria-label="Timeline media preview"]')
    const canvas = preview.querySelector('canvas')
    const [r,g,b] = canvas.getContext('2d').getImageData(canvas.width/2,canvas.height/2,1,1).data
    return preview.dataset.previewStatus === 'PLAYING' && +preview.dataset.previewTime >= 2
      && +preview.dataset.previewTime < 3 && Math.min(r,g,b) > 30 && Math.max(r,g,b) - Math.min(r,g,b) < 6
  })
  const playing7 = await page.getByRole('region', { name: 'Timeline media preview' }).getAttribute('data-preview-time')
  await page.getByRole('button', { name: 'Pause Timeline', exact: true }).click()
  assert.deepEqual(await c7('reopen'), await c7('redo'))
  save('N07 ripple cancel, comparison with ordinary Delete, reader rejection, exact replay, Undo/Redo, reopened playback')

  const staleBase7 = (await restore6('ripple-stale', base7)).newRevisionId
  await view6(staleBase7)
  const stalePlan7 = await rippleStage()
  const competing7 = await revise6('ripple-concurrent', [makeMove({ clipId: 'clip-later' }, '9', true)])
  const competitorState7 = await c7('stale-before')
  const staleApply7 = wire6('/apply')
  await rippleConfirm.click()
  const stale7 = await trace6(await staleApply7)
  assert.deepEqual(stale7.body, stalePlan7.body)
  assert.equal(stale7.status, 409)
  assert.equal(stale7.payload.errorCode, 'STALE_BASE_REVISION')
  await waitView('main', competing7.newRevisionId)
  assert.deepEqual(await c7('stale-after'), competitorState7)

  const linked7 = await revise6('ripple-relationship', [{ definitionId: 'timeline.sync.create',
    target: { syncIdentityKey: 'SYNC(clip-later,clip-red)' },
    parameters: { endpointA: 'clip-red', localAnchorA: '1', endpointB: 'clip-later', localAnchorB: '1/2' } }])
  await view6(linked7.newRevisionId)
  const linkedBefore7 = await c7('linked-before')
  const linkedPlan7 = await rippleStage()
  assert.equal(linkedPlan7.status, 422)
  assert.match(linkedPlan7.payload.detail, /cannot preserve relationship.*clip-later/)
  await clipEditor6.getByRole('alert').filter({ hasText: /clip-later/ }).waitFor()
  assert.equal(await rippleConfirm.count(), 0)
  const linkedApply7 = await apply(linkedPlan7.body, '0'.repeat(64), 422)
  assert.match(linkedApply7.detail, /cannot preserve relationship.*clip-later/)
  assert.deepEqual(await c7('linked-after'), linkedBefore7)

  await restore6('ripple-fraction-seed', base7)
  const fractionalClip7 = add6('clip-blue', bluePin6, '2', '121/30', '1', 61)
  fractionalClip7.parameters.rateNumerator = 30
  const fractional7 = await revise6('ripple-fractional', [
    ...['clip-blue','clip-gray','clip-later'].map(clipId => ({ definitionId: 'timeline.delete', target: { clipId } })),
    fractionalClip7, add6('clip-gray', redPin6, '121/30', '181/30', '2'),
    add6('clip-later', bluePin6, '211/30', '241/30', '1'),
  ])
  await view6(fractional7.newRevisionId)
  const fractionalBefore7 = await c7('fraction-before')
  const fractionalPlan7 = await rippleStage()
  assert.equal(fractionalPlan7.status, 200)
  await clipEditor6.getByText('Remove clip-blue. Shift clip-gray, clip-later left by 61/30s on this track.', { exact: true }).waitFor()
  const readRoute7 = '**' + branchPath + '/main'
  const recoveryCounts7 = { headReads: 0, writes: 0 }
  await page.route(readRoute7, async route => {
    recoveryCounts7.headReads++
    if (recoveryCounts7.headReads === 1) await route.abort('failed')
    else await route.continue()
  })
  const count7 = req => {
    if (req.method() === 'POST' && req.url().endsWith(batch + '/apply')) recoveryCounts7.writes++
  }
  page.on('request', count7)
  let fractionalApply7
  try {
    const pending = wire6('/apply')
    await rippleConfirm.click()
    fractionalApply7 = await trace6(await pending)
    confirmed6(fractionalPlan7, fractionalApply7)
    const accepted = fractionalApply7.payload.newRevisionId
    await clipEditor6.getByText('Ripple Delete accepted at revision ' + accepted + '. Reload HEAD to refresh the editor.', { exact: true }).waitFor()
    assert.equal(await rippleRetry.count(), 0)
    assert.equal(await ripplePreview.isDisabled(), true)
    const recovery = clipEditor6.getByRole('button', { name: 'Reload HEAD after accepted edit', exact: true })
    await recovery.click()
    await waitView('main', accepted)
    await clipEditor6.getByText('Clip clip-blue ripple deleted at revision ' + accepted + '.', { exact: true }).waitFor()
    assert.deepEqual(recoveryCounts7, { headReads: 2, writes: 1 })
  } finally {
    await page.unroute(readRoute7)
    page.off('request', count7)
  }
  const fractionalAfter7 = await c7('fraction-after')
  assert.equal(fractionalAfter7.rows, fractionalBefore7.rows + 1)
  assert.equal(fractionalAfter7.commands, fractionalBefore7.commands + 1)
  await view6(fractionalApply7.payload.newRevisionId)
  assert.deepEqual(await c7('fraction-reopen'), fractionalAfter7)
  result.http.n07 = { base: base7, ripple: ripple7, undo: undo7, redo: redo7, plan: planned7, wire: exact7,
    denied: { status: 403, code: denied7.errorCode }, pictures: pictures7, playing: playing7,
    stale: { status: stale7.status, code: stale7.payload.errorCode, head: competing7.newRevisionId },
    relationship: { preview: linkedPlan7, apply: linkedApply7, status: 422 },
    fractional: { before: fractional7.newRevisionId, plan: fractionalPlan7, apply: fractionalApply7, recoveryCounts: recoveryCounts7 } }
  save('N07 atomic single-track ripple, fractional duration, stale and affected relationship rejection, read-only recovery')

  const c8 = stage => checkpoint('n08-' + stage)
  const insertTime = clipboard.getByLabel('Ripple insertion time (seconds)')
  const previewInsert = clipboard.getByRole('button', { name: 'Preview ripple insert', exact: true })
  const confirmInsert = clipboard.getByRole('button', { name: 'Confirm ripple insert', exact: true })
  const retryInsert = clipboard.getByRole('button', { name: 'Retry confirmed ripple insert', exact: true })
  async function copyForInsert(id) {
    await clipboard.getByRole('checkbox', { name: 'Copy clip ' + id, exact: true }).check()
    await clipboard.getByRole('button', { name: 'Copy selected clips' }).click()
  }
  async function prepareInsert(position) {
    await insertTime.fill(position)
    const response = wire6('/preview')
    await previewInsert.click()
    return trace6(await response)
  }
  const start8 = (await restore6('insert-seed', base7)).newRevisionId
  await view6(start8)
  await copyForInsert('clip-red')
  const before8 = await c8('before')
  const invalid8 = await prepareInsert('3')
  assert.equal(invalid8.status, 422)
  assert.match(invalid8.payload.detail, /inside clip clip-blue/)
  await apply(invalid8.body, '0'.repeat(64), 422)
  assert.equal(await confirmInsert.count(), 0)
  await prepareInsert('5')
  await clipboard.getByText(/Shift right by 2\/1s. Shifted clips: clip-gray, clip-later/).waitFor()
  await clipboard.getByRole('button', { name: 'Cancel ripple insert' }).click()
  const planned8 = await prepareInsert('5')
  const denied8 = await apply(planned8.body, planned8.payload.batchDigest, 403, reader)
  const alternate8 = await branch('alternate')
  const unsupportedRef8 = { ...planned8.body, batchId: 'insert-alternate', refId: 'alternate',
    baseRevisionId: alternate8.headRevisionId, baseContentHash: alternate8.contentHash }
  assert.equal((await preview(unsupportedRef8, 409)).errorCode, 'REF_NOT_SUPPORTED')
  await apply(unsupportedRef8, planned8.payload.batchDigest, 409)
  const invalidRequests8 = []
  for (const [label, overrides, status] of [
    ['cross-track', { trackId: 'other-track' }, 422],
    ['multiple', { sourceClipIds: ['clip-red', 'clip-gray'], newClipIds: ['copy-red', 'copy-gray'] }, 400],
  ]) {
    const step = planned8.body.operations[0]
    const body = { ...planned8.body, batchId: 'insert-' + label,
      operations: [{ ...step, parameters: { ...step.parameters, ...overrides } }] }
    const rejected = await preview(body, status)
    await apply(body, planned8.payload.batchDigest, status)
    invalidRequests8.push({ label, status, detail: rejected.detail })
  }
  assert.deepEqual(await c8('readonly'), before8)
  const applied8 = []
  const applyRoute8 = '**' + batch + '/apply'
  await page.route(applyRoute8, async route => {
    const req = route.request(), res = await route.fetch()
    applied8.push({ target: req.url(), raw: req.postData(), body: req.postDataJSON(),
      ifMatch: req.headers()['if-match'], status: res.status(), payload: await res.json() })
    if (applied8.length === 1) await route.abort('failed')
    else await route.fulfill({ response: res })
  })
  let accepted8
  try {
    await confirmInsert.click()
    await retryInsert.waitFor()
    const written = await c8('committed')
    accepted8 = written.refs.main
    assert.equal(written.rows, before8.rows + 1)
    assert.equal(written.commands, before8.commands + 1)
    assert.equal(await deleteClip.isDisabled(), true)
    await page.route(readRoute7, route => route.abort('failed'), { times: 1 })
    await retryInsert.click()
    await clipboard.getByText('Ripple Insert accepted at revision ' + accepted8 + '. Reload HEAD to refresh the editor.').waitFor()
    assert.equal(await retryInsert.count(), 0)
    assert.equal(await previewInsert.isDisabled(), true)
    await page.getByRole('button', { name: 'Reload HEAD', exact: true }).click()
    await waitView('main', accepted8)
    assert.deepEqual(await c8('recovered'), written)
  } finally { await page.unroute(applyRoute8) }
  confirmed6(planned8, applied8[0])
  assert.deepEqual(applied8.map(w => [w.status, w.payload.newRevisionId]), [[201, accepted8], [200, accepted8]])
  for (const key of ['target', 'raw', 'ifMatch']) assert.equal(applied8[1][key], applied8[0][key])
  for (const direction of ['Undo', 'Redo']) {
    const pending = wire6('/apply')
    await page.getByRole('button', { name: direction + ' ripple insert', exact: true }).click()
    const revision = (await trace6(await pending)).payload.newRevisionId
    await waitView('main', revision)
    await c8(direction.toLowerCase())
  }
  await view6((await branch()).headRevisionId)
  const pictures8 = await samples6('n08-reopened', [['5.5','red'], ['7.5','gray'], ['9.5','black'], ['10.5','blue']])
  await c8('reopened')

  const edgeCases8 = []
  for (const [label, position, shifted] of [['gap', '221/30', 'clip-later'], ['end', '9', 'none']]) {
    const base = (await restore6('insert-' + label, base7)).newRevisionId
    await view6(base)
    await copyForInsert('clip-red')
    const planned = await prepareInsert(position)
    await clipboard.getByText(new RegExp('Shifted clips: ' + shifted)).waitFor()
    const response = wire6('/apply')
    await confirmInsert.click()
    const accepted = await trace6(await response)
    confirmed6(planned, accepted)
    await waitView('main', accepted.payload.newRevisionId)
    edgeCases8.push({ label, base, position, plan: planned, accepted })
    await c8(label)
  }
  const staleBase8 = (await restore6('insert-stale', base7)).newRevisionId
  await view6(staleBase8)
  await copyForInsert('clip-red')
  const stalePlan8 = await prepareInsert('5')
  await revise6('insert-competitor', [makeMove({ clipId: 'clip-later' }, '9', true)])
  const competitor8 = await c8('competitor')
  const staleResponse8 = wire6('/apply')
  await confirmInsert.click()
  const stale8 = await trace6(await staleResponse8)
  assert.deepEqual(stale8.body, stalePlan8.body)
  assert.equal(stale8.status, 409)
  assert.equal(stale8.payload.errorCode, 'STALE_BASE_REVISION')
  assert.deepEqual(await c8('stale'), competitor8)
  const relationBase8 = (await restore6('insert-relationship', linked7.newRevisionId)).newRevisionId
  await view6(relationBase8)
  await copyForInsert('clip-gray')
  const relationBefore8 = await c8('bound')
  const relation8 = await prepareInsert('5')
  assert.equal(relation8.status, 422)
  assert.match(relation8.payload.detail, /relationship.*clip-later/)
  await apply(relation8.body, '0'.repeat(64), 422)
  assert.deepEqual(await c8('rejected'), relationBefore8)
  result.http.n08 = { base: start8, accepted: accepted8, newId: planned8.body.operations[0].parameters.newClipIds[0],
    plan: planned8, attempts: applied8, pictures: pictures8, edgeCases: edgeCases8,
    unsupported: invalidRequests8, alternate: 'REF_NOT_SUPPORTED', inside: invalid8.payload.detail, relationship: relation8.payload.detail, reader: denied8.errorCode, stale: stale8.payload.errorCode }
  save('N08 single-track ripple insert, read-only recovery, exact replay, whole edit Undo/Redo and reopened playback')
  const c9 = stage => checkpoint('n09-' + stage)
  async function rollMedia(colour, seconds, fixture = 'n09') {
    const name = fixture + '-' + colour + '.mp4'
    const path = join(out, name)
    execFileSync('/usr/bin/ffmpeg', ['-hide_banner', '-loglevel', 'error', '-nostdin', '-y', '-f',
      'lavfi', '-i', 'color=c=' + colour + ':s=320x240:r=24', '-t', String(seconds), '-c:v', 'libopenh264',
      '-pix_fmt', 'yuv420p', '-movflags', '+faststart', path])
    return uploadFixture(name, path)
  }
  async function uploadFixture(name, path) {
    const uploaded = await context.request.post(base + '/api/tenants/tenant-fr-e2e/projects/' + project + '/source-media', {
      headers: { Authorization: 'Bearer ' + token, 'Idempotency-Key': name },
      multipart: { file: { name, mimeType: 'video/mp4', buffer: readFileSync(path) } },
    })
    assert.equal(uploaded.status(), 201)
    const media = await uploaded.json()
    return { ...media, mediaStreamId: media.streams.find(s => s.kind === 'VIDEO').streamId }
  }
  const red9 = await rollMedia('red', 5), blue9 = await rollMedia('blue', 6)
  const rollSource = (id, pin, start, end, from, to) => {
    const op = addN05(id, pin, start, end)
    return { ...op, parameters: { ...op.parameters, sourceStart: from, sourceEnd: to } }
  }
  await restore6('roll-seed', base7)
  const seed9 = await revise6('roll-fixture', [
    ...['clip-red', 'clip-blue', 'clip-gray', 'clip-later'].map(clipId => ({ definitionId: 'timeline.delete', target: { clipId } })),
    rollSource('clip-red', red9, '2', '5', '1', '4'), rollSource('clip-blue', blue9, '5', '9', '2', '6'),
    rollSource('clip-later', blue9, '10', '11', '0', '1'),
  ])
  await view6(seed9.newRevisionId)
  const roll = page.getByRole('region', { name: 'Roll edit', exact: true })
  const prepare9 = async time => {
    await roll.getByLabel('Shared cut').selectOption(JSON.stringify(['track-1', 'clip-red', 'clip-blue']))
    await roll.getByLabel('New cut time (seconds)').fill(time)
    const next = wire6('/preview')
    await roll.getByRole('button', { name: 'Preview roll' }).click()
    return trace6(await next)
  }
  const confirm9 = roll.getByRole('button', { name: 'Confirm roll', exact: true })
  const before9 = await c9('before')
  const frame9 = async (label, colour) => ({
    cut: await samples6('n09-' + label + '-cut', [['5.5', colour]]),
    control: await samples6('n09-' + label + '-control', [['10.5', 'blue']]),
  })
  const frames9 = { before: await frame9('before', 'blue') }
  await prepare9('6')
  await roll.getByRole('button', { name: 'Cancel roll' }).click()
  const planned9 = await prepare9('6')
  await roll.getByText('clip-red timeline [2/1, 6/1), source [1/1, 5/1)').waitFor()
  await roll.getByText('clip-blue timeline [6/1, 9/1), source [3/1, 6/1)').waitFor()
  const rejects9 = []
  const step9 = planned9.body.operations[0]
  for (const [label, overrides] of [['left-exhausted', { cutTime: '181/30' }],
    ['right-exhausted', { cutTime: '89/30' }], ['empty', { cutTime: '2' }],
    ['non-neighbor', { rightClipId: 'clip-later' }], ['gap', { leftClipId: 'clip-blue', rightClipId: 'clip-later' }],
    ['cross-track', { trackId: 'other-track' }]]) {
    const body = { ...planned9.body, batchId: 'roll-' + label,
      operations: [{ ...step9, parameters: { ...step9.parameters, ...overrides } }] }
    const res = await preview(body, 422)
    await apply(body, '0'.repeat(64), 422)
    rejects9.push({ label, status: 422, detail: res.detail })
  }
  await apply(planned9.body, planned9.payload.batchDigest, 403, reader)
  const alt9 = await branch('alternate')
  const alternateBody9 = { ...planned9.body, refId: 'alternate', baseRevisionId: alt9.headRevisionId, baseContentHash: alt9.contentHash }
  assert.equal((await preview(alternateBody9, 409)).errorCode, 'REF_NOT_SUPPORTED')
  await apply(alternateBody9, planned9.payload.batchDigest, 409)
  assert.deepEqual(await c9('readonly'), before9)
  const attempts9 = []
  await page.route(applyRoute8, async route => {
    const req = route.request(), res = await route.fetch()
    attempts9.push({ target: req.url(), raw: req.postData(), body: req.postDataJSON(),
      ifMatch: req.headers()['if-match'], status: res.status(), payload: await res.json() })
    if (attempts9.length === 1) await route.abort('failed')
    else await route.fulfill({ response: res })
  })
  try {
    await confirm9.click()
    const retry = roll.getByRole('button', { name: 'Retry confirmed roll' })
    await retry.waitFor()
    const committed = await c9('committed')
    assert.equal(committed.rows, before9.rows + 1)
    assert.equal(await deleteClip.isDisabled(), true)
    await page.route(readRoute7, route => route.abort('failed'), { times: 1 })
    await retry.click()
    await roll.getByText('Roll accepted at revision ' + committed.refs.main + '. Reload HEAD to refresh the editor.').waitFor()
    await page.getByRole('button', { name: 'Reload HEAD', exact: true }).click()
    await waitView('main', committed.refs.main)
    assert.deepEqual(await c9('recovered'), committed)
  } finally { await page.unroute(applyRoute8) }
  confirmed6(planned9, attempts9[0])
  for (const field of ['target', 'raw', 'ifMatch']) assert.equal(attempts9[0][field], attempts9[1][field])
  assert.equal(attempts9[1].status, 200)
  assert.equal(attempts9[0].payload.newRevisionId, attempts9[1].payload.newRevisionId)
  frames9.after = await frame9('after', 'red')
  for (const direction of ['Undo', 'Redo']) {
    const response = wire6('/apply')
    await page.getByRole('button', { name: direction + ' roll', exact: true }).click()
    await waitView('main', (await trace6(await response)).payload.newRevisionId)
    await c9(direction.toLowerCase())
  }
  await view6((await branch()).headRevisionId)
  frames9.reopened = await frame9('reopened', 'red')
  await c9('reopened')
  const zero9 = await prepare9('6')
  assert.equal(zero9.payload.noOp, true)
  const zeroReceipt9 = wire6('/apply')
  await confirm9.click()
  assert.equal((await trace6(await zeroReceipt9)).payload.status, 'NO_OP')
  await roll.getByText(/Roll created no revision/).waitFor()
  await c9('zero')
  for (const cut of ['3', '151/30']) {
    const plan = await prepare9(cut)
    const response = wire6('/apply')
    await confirm9.click()
    const receipt = await trace6(await response)
    confirmed6(plan, receipt)
    await waitView('main', receipt.payload.newRevisionId)
    await c9(cut === '3' ? 'left' : 'fraction')
  }
  const stalePlan9 = await prepare9('5')
  await revise6('roll-competitor', [makeMove({ clipId: 'clip-later' }, '11', true)])
  const competitor9 = await c9('competitor')
  const staleResponse9 = wire6('/apply')
  await confirm9.click()
  const stale9 = await trace6(await staleResponse9)
  assert.deepEqual(stale9.body, stalePlan9.body)
  assert.equal(stale9.status, 409)
  assert.equal(stale9.payload.errorCode, 'STALE_BASE_REVISION')
  assert.deepEqual(await c9('stale'), competitor9)
  result.http.n09 = { base: seed9.newRevisionId, plan: planned9, attempts: attempts9, rejects: rejects9,
    frames: frames9, zero: zero9, stale: stale9 }
  save('N09 shared-cut roll, exact handles, fractional placement, atomic replay, recovery, undo/redo and reopened playback')
  const c10 = stage => checkpoint('n10-' + stage)
  const file10 = 'n10-two-sections.mp4'
  const path10 = join(out, file10)
  execFileSync('/usr/bin/ffmpeg', ['-hide_banner', '-loglevel', 'error', '-nostdin', '-y',
    '-f', 'lavfi', '-i', 'color=c=red:s=320x240:r=24:d=3',
    '-f', 'lavfi', '-i', 'color=c=blue:s=320x240:r=24:d=5',
    '-f', 'lavfi', '-i', 'sine=frequency=440:sample_rate=48000:duration=8',
    '-filter_complex', '[0:v][1:v]concat=n=2:v=1:a=0[v]', '-map', '[v]', '-map', '2:a',
    '-c:v', 'libopenh264', '-pix_fmt', 'yuv420p', '-c:a', 'aac', '-movflags', '+faststart', path10])
  const probe10 = JSON.parse(execFileSync('/usr/bin/ffprobe', ['-v','error','-show_entries',
    'stream=codec_type,duration:format=duration','-of','json',path10], { encoding: 'utf8' }))
  assert.equal(Number(probe10.format.duration), 8)
  assert.deepEqual(probe10.streams.map(s => [s.codec_type, Number(s.duration)]), [['video',8], ['audio',8]])
  const mixed10 = await uploadFixture(file10, path10)
  await restore6('slip-seed', seed9.newRevisionId)
  const seed10 = await revise6('slip-fixture', [
    ...['clip-red','clip-blue','clip-later'].map(clipId => ({ definitionId: 'timeline.delete', target: { clipId } })),
    rollSource('clip-red', red9, '2','4','0','2'), rollSource('clip-blue', mixed10, '5','8','2','5'),
    rollSource('clip-later', blue9, '10','11','0','1'),
    { definitionId: 'audio.gain.set', target: { audioTrackId: 'track-1', clipId: 'clip-blue' }, parameters: { linear: 0.5 } },
  ])
  await view6(seed10.newRevisionId)
  const slip = page.getByRole('region', { name: 'Slip edit', exact: true })
  const prepare10 = async offset => {
    await slip.getByLabel('Slip clip').selectOption('clip-blue')
    await slip.getByLabel('Source offset (seconds)').fill(offset)
    const next = wire6('/preview')
    await slip.getByRole('button', { name: 'Preview slip' }).click()
    return trace6(await next)
  }
  const confirm10 = slip.getByRole('button', { name: 'Confirm slip', exact: true })
  const frame10 = async (stage, colour) => ({
    selected: await samples6('n10-' + stage + '-selected', [['5.5',colour]]),
    neighbor: await samples6('n10-' + stage + '-neighbor', [['3','red']]),
  })
  const frames10 = { before: await frame10('before', 'red') }
  const zeroBefore10 = await c10('zero-before')
  const zero10 = await prepare10('0')
  assert.equal(zero10.payload.noOp, true)
  const zeroWrite10 = wire6('/apply')
  await confirm10.click()
  assert.equal((await trace6(await zeroWrite10)).payload.status, 'NO_OP')
  await slip.getByText(/Slip created no revision/).waitFor()
  assert.equal(await page.getByRole('button', { name: 'Undo slip' }).count(), 0)
  const before10 = await c10('before')
  assert.deepEqual(before10.refs, zeroBefore10.refs)
  assert.equal(before10.rows, zeroBefore10.rows)
  await prepare10('+1')
  await slip.getByRole('button', { name: 'Cancel slip' }).click()
  const rejected10 = []
  for (const offset of ['-61/30','91/30']) {
    const rejected = await prepare10(offset)
    assert.equal(rejected.status, 422)
    await apply(rejected.body, '0'.repeat(64), 422)
    rejected10.push({ offset, preview: rejected.payload.detail, applyStatus: 422 })
  }
  const planned10 = await prepare10('+1')
  await slip.getByText('clip-blue timeline [5/1, 8/1)').waitFor()
  await slip.getByText('Source [2/1, 5/1) → [3/1, 6/1)').waitFor()
  await slip.getByText('Source offset +1/1s').waitFor()
  await apply(planned10.body, planned10.payload.batchDigest, 403, reader)
  const alt10 = await branch('alternate')
  const alternate10 = { ...planned10.body, refId: 'alternate', baseRevisionId: alt10.headRevisionId, baseContentHash: alt10.contentHash,
    operations: [{ ...planned10.body.operations[0], target: { clipId: 'clip-red' } }] }
  assert.equal((await preview(alternate10, 409)).errorCode, 'REF_NOT_SUPPORTED')
  await apply(alternate10, planned10.payload.batchDigest, 409)
  assert.deepEqual(await c10('readonly'), before10)
  const attempts10 = []
  await page.route(applyRoute8, async route => {
    const req = route.request(), res = await route.fetch()
    attempts10.push({ target: req.url(), raw: req.postData(), body: req.postDataJSON(),
      ifMatch: req.headers()['if-match'], status: res.status(), payload: await res.json() })
    if (attempts10.length === 1) await route.abort('failed')
    else await route.fulfill({ response: res })
  })
  try {
    await confirm10.click()
    const retry = slip.getByRole('button', { name: 'Retry confirmed slip' })
    await retry.waitFor()
    const committed = await c10('committed')
    assert.equal(committed.rows, before10.rows + 1)
    assert.equal(await deleteClip.isDisabled(), true)
    await page.route(readRoute7, route => route.abort('failed'), { times: 1 })
    await retry.click()
    await slip.getByText('Slip accepted at revision ' + committed.refs.main + '. Reload HEAD to refresh the editor.').waitFor()
    await page.getByRole('button', { name: 'Reload HEAD', exact: true }).click()
    await waitView('main', committed.refs.main)
    assert.deepEqual(await c10('recovered'), committed)
  } finally { await page.unroute(applyRoute8) }
  confirmed6(planned10, attempts10[0])
  for (const field of ['target', 'raw', 'ifMatch']) assert.equal(attempts10[0][field], attempts10[1][field])
  assert.equal(attempts10[1].status, 200)
  assert.equal(attempts10[0].payload.newRevisionId, attempts10[1].payload.newRevisionId)
  frames10.after = await frame10('after', 'blue')
  for (const direction of ['Undo', 'Redo']) {
    const response = wire6('/apply')
    await page.getByRole('button', { name: direction + ' slip', exact: true }).click()
    await waitView('main', (await trace6(await response)).payload.newRevisionId)
    await c10(direction.toLowerCase())
  }
  await view6((await branch()).headRevisionId)
  frames10.reopened = await frame10('reopened', 'blue')
  await c10('reopened')
  const edges10 = []
  for (const [stage, offset] of [['left','-1'], ['fraction','1/30'], ['source-start','-2'], ['source-end','+3']]) {
    const base = (await restore6('slip-' + stage, seed10.newRevisionId)).newRevisionId
    await view6(base)
    const plan = await prepare10(offset)
    const pending = wire6('/apply')
    await confirm10.click()
    const accepted = await trace6(await pending)
    confirmed6(plan, accepted)
    await waitView('main', accepted.payload.newRevisionId)
    edges10.push({ stage, offset, base, accepted: accepted.payload.newRevisionId })
    await c10(stage)
  }
  const staleBase10 = (await restore6('slip-stale', seed10.newRevisionId)).newRevisionId
  await view6(staleBase10)
  const stalePlan10 = await prepare10('+1')
  await revise6('slip-competitor', [makeMove({ clipId: 'clip-later' }, '12', true)])
  const competitor10 = await c10('competitor')
  const staleResponse10 = wire6('/apply')
  await confirm10.click()
  const stale10 = await trace6(await staleResponse10)
  assert.deepEqual(stale10.body, stalePlan10.body)
  assert.equal(stale10.status, 409)
  assert.equal(stale10.payload.errorCode, 'STALE_BASE_REVISION')
  assert.deepEqual(await c10('stale'), competitor10)
  const linked10 = await revise6('slip-sync', [{ definitionId: 'timeline.sync.create',
    target: { syncIdentityKey: 'SYNC(clip-blue,clip-later)' },
    parameters: { endpointA: 'clip-blue', localAnchorA: '1', endpointB: 'clip-later', localAnchorB: '1/2' } }])
  await view6(linked10.newRevisionId)
  const bound10 = await c10('bound')
  const relation10 = await prepare10('+1')
  assert.equal(relation10.status, 422)
  assert.match(relation10.payload.detail, /relationship.*clip-blue/)
  await apply(relation10.body, '0'.repeat(64), 422)
  assert.deepEqual(await c10('rejected'), bound10)
  result.http.n10 = { probe: probe10, base: seed10.newRevisionId, plan: planned10, attempts: attempts10, frames: frames10,
    edges: edges10, rejected: rejected10, readOnly: 403, alternate: 409, stale: stale10.payload, sync: relation10.payload.detail }
  save('N10 single-clip slip, fixed timeline picture, routed audio, exact media handles, replay, readback and undo/redo')
  const c11 = stage => checkpoint('n11-' + stage)
  const blue11 = await rollMedia('blue', 5, 'n11'), red11 = await rollMedia('red', 6, 'n11')
  await restore6('slide-seed', seed10.newRevisionId)
  const seed11 = await revise6('slide-fixture', [
    ...['clip-red','clip-blue','clip-later'].map(clipId => ({ definitionId: 'timeline.delete', target: { clipId } })),
    rollSource('clip-left', blue11, '2','5','1','4'), rollSource('clip-middle', mixed10, '5','8','2','5'),
    rollSource('clip-right', red11, '8','12','2','6'), rollSource('clip-red', red9, '13','14','0','1'),
    { definitionId: 'audio.gain.set', target: { audioTrackId: 'track-1', clipId: 'clip-middle' }, parameters: { linear: 0.5 } },
  ])
  await view6(seed11.newRevisionId)
  const slide = page.getByRole('region', { name: 'Slide edit', exact: true })
  const prepare11 = async offset => {
    await slide.getByLabel('Middle clip').selectOption({ label: 'clip-middle between clip-left and clip-right' })
    await slide.getByLabel('Timeline offset (seconds)').fill(offset)
    const next = wire6('/preview')
    await slide.getByRole('button', { name: 'Preview slide' }).click()
    return trace6(await next)
  }
  const confirm11 = slide.getByRole('button', { name: 'Confirm slide', exact: true })
  const frame11 = async (stage, shifted) => {
    const samples = {}
    for (const [point, time, colour] of [
      ['left-cut','5.5',shifted ? 'blue' : 'red'], ['right-cut','8.5',shifted ? 'blue' : 'red'],
      ['middle-early',shifted ? '6.5' : '5.5','red'], ['middle-late',shifted ? '7.5' : '6.5','blue'],
      ['control','13.5','red'],
    ]) samples[point] = await samples6('n11-' + stage + '-' + point, [[time,colour]])
    return samples
  }
  const frames11 = { before: await frame11('before', false) }
  const zeroBefore11 = await c11('zero-before')
  const zero11 = await prepare11('0')
  assert.equal(zero11.payload.noOp, true)
  const zeroWrite11 = wire6('/apply')
  await confirm11.click()
  assert.equal((await trace6(await zeroWrite11)).payload.status, 'NO_OP')
  await slide.getByText(/Slide created no revision/).waitFor()
  assert.equal(await page.getByRole('button', { name: 'Undo slide' }).count(), 0)
  const before11 = await c11('before')
  assert.deepEqual(before11.refs, zeroBefore11.refs)
  assert.equal(before11.rows, zeroBefore11.rows)
  await prepare11('+1')
  await slide.getByRole('button', { name: 'Cancel slide' }).click()
  const rejected11 = []
  for (const [offset, reason, checkpoint] of [
    ['-49/24', /source handle before clip-right/, 'right-overrun'],
    ['25/24', /source clip-left is invalid.*duration/i, 'left-overrun'],
    ['-3', /positive duration on clip-left/],
    ['4', /positive duration on clip-right/],
  ]) {
    const rejected = await prepare11(offset)
    assert.equal(rejected.status, 422)
    await apply(rejected.body, '0'.repeat(64), 422)
    assert.match(rejected.payload.detail, reason)
    if (checkpoint) assert.deepEqual(await c11(checkpoint), before11)
    rejected11.push({ offset, preview: rejected.payload.detail, applyStatus: 422 })
  }
  const planned11 = await prepare11('+1')
  for (const line of ['Cuts 5/1s → 6/1s and 8/1s → 9/1s', 'clip-left timeline [2/1, 6/1), source [1/1, 5/1)',
    'clip-middle timeline [6/1, 9/1), source [2/1, 5/1)', 'clip-right timeline [9/1, 12/1), source [3/1, 6/1)']) {
    await slide.getByText(line, { exact: true }).waitFor()
  }
  await apply(planned11.body, planned11.payload.batchDigest, 403, reader)
  const alt11 = await branch('alternate')
  const alternate11 = { ...planned11.body, refId: 'alternate', baseRevisionId: alt11.headRevisionId, baseContentHash: alt11.contentHash,
    operations: planned11.body.operations }
  assert.equal((await preview(alternate11, 409)).errorCode, 'REF_NOT_SUPPORTED')
  await apply(alternate11, planned11.payload.batchDigest, 409)
  assert.deepEqual(await c11('readonly'), before11)
  const attempts11 = []
  await page.route(applyRoute8, async route => {
    const req = route.request(), res = await route.fetch()
    attempts11.push({ target: req.url(), raw: req.postData(), body: req.postDataJSON(),
      ifMatch: req.headers()['if-match'], status: res.status(), payload: await res.json() })
    if (attempts11.length === 1) await route.abort('failed')
    else await route.fulfill({ response: res })
  })
  try {
    await confirm11.click()
    const retry = slide.getByRole('button', { name: 'Retry confirmed slide' })
    await retry.waitFor()
    const committed = await c11('committed')
    assert.equal(committed.rows, before11.rows + 1)
    assert.equal(await deleteClip.isDisabled(), true)
    await page.route(readRoute7, route => route.abort('failed'), { times: 1 })
    await retry.click()
    await slide.getByText('Slide accepted at revision ' + committed.refs.main + '. Reload HEAD to refresh the editor.').waitFor()
    await page.getByRole('button', { name: 'Reload HEAD', exact: true }).click()
    await waitView('main', committed.refs.main)
    assert.deepEqual(await c11('recovered'), committed)
  } finally { await page.unroute(applyRoute8) }
  confirmed6(planned11, attempts11[0])
  for (const field of ['target', 'raw', 'ifMatch']) assert.equal(attempts11[0][field], attempts11[1][field])
  assert.equal(attempts11[1].status, 200)
  assert.equal(attempts11[0].payload.newRevisionId, attempts11[1].payload.newRevisionId)
  frames11.after = await frame11('after', true)
  for (const direction of ['Undo', 'Redo']) {
    const response = wire6('/apply')
    await page.getByRole('button', { name: direction + ' slide', exact: true }).click()
    await waitView('main', (await trace6(await response)).payload.newRevisionId)
    await c11(direction.toLowerCase())
  }
  await view6((await branch()).headRevisionId)
  frames11.reopened = await frame11('reopened', true)
  await c11('reopened')
  const edges11 = []
  for (const [stage, offset] of [['left','-1'], ['fraction','1/30'], ['source-start','-2'], ['source-end','+1']]) {
    const base = (await restore6('slide-' + stage, seed11.newRevisionId)).newRevisionId
    await view6(base)
    const plan = await prepare11(offset)
    const pending = wire6('/apply')
    await confirm11.click()
    const accepted = await trace6(await pending)
    confirmed6(plan, accepted)
    await waitView('main', accepted.payload.newRevisionId)
    edges11.push({ stage, offset, base, accepted: accepted.payload.newRevisionId })
    await c11(stage)
  }
  const staleBase11 = (await restore6('slide-stale', seed11.newRevisionId)).newRevisionId
  await view6(staleBase11)
  const stalePlan11 = await prepare11('+1')
  await revise6('slide-competitor', [makeMove({ clipId: 'clip-red' }, '14', true)])
  const competitor11 = await c11('competitor')
  const staleResponse11 = wire6('/apply')
  await confirm11.click()
  const stale11 = await trace6(await staleResponse11)
  assert.deepEqual(stale11.body, stalePlan11.body)
  assert.equal(stale11.status, 409)
  assert.equal(stale11.payload.errorCode, 'STALE_BASE_REVISION')
  assert.deepEqual(await c11('stale'), competitor11)
  const linked11 = await revise6('slide-sync', [{ definitionId: 'timeline.sync.create',
    target: { syncIdentityKey: 'SYNC(clip-red,clip-right)' },
    parameters: { endpointA: 'clip-red', localAnchorA: '1', endpointB: 'clip-right', localAnchorB: '1/2' } }])
  await view6(linked11.newRevisionId)
  const bound11 = await c11('bound')
  const relation11 = await prepare11('+1')
  assert.equal(relation11.status, 422)
  assert.match(relation11.payload.detail, /relationship.*clip-right/)
  await apply(relation11.body, '0'.repeat(64), 422)
  assert.deepEqual(await c11('rejected'), bound11)
  result.http.n11 = { base: seed11.newRevisionId, plan: planned11, attempts: attempts11, frames: frames11,
    edges: edges11, rejected: rejected11, readOnly: 403, alternate: 409, stale: stale11.payload, sync: relation11.payload.detail }
  const readerHead = await branch()
  const opacityBody = { batchId: 'n12-reader', baseRevisionId: readerHead.headRevisionId, baseContentHash: readerHead.contentHash,
    operations: [{ definitionId: 'timeline.clip.opacity.keyframes.set', target: { clipId: 'clip-middle' },
      parameters: { firstTime: '5', firstValue: 0, lastTime: '7', lastValue: 1 } }] }
  const opacityDigest = (await preview(opacityBody, 200, reader)).batchDigest
  const deniedOpacity = await apply(opacityBody, opacityDigest, 403, reader)
  result.http.n12Reader = { status: 403, errorCode: deniedOpacity.errorCode, revision: readerHead.headRevisionId }
  result.status = 'PASS'
  save('N11 single-track slide, two shifted cuts, clip-relative picture and fixed control, exact handles, recovery and undo/redo')
} finally {
  await browser.close()
}
