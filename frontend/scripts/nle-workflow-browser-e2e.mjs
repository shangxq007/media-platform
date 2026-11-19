import { chromium } from 'playwright'
import { readFileSync, writeFileSync, mkdirSync, copyFileSync } from 'node:fs'
import { join } from 'node:path'
import { execFileSync } from 'node:child_process'
import { createHash } from 'node:crypto'

const [appRoot, outRoot] = process.argv.slice(2)
if (!appRoot || !outRoot) throw new Error('Owned app and output paths required')
mkdirSync(outRoot, { recursive: true })
const token = readFileSync(join(appRoot, 'token'), 'utf8').trim()
const setup = JSON.parse(readFileSync(join(appRoot, 'workflow-setup.json'), 'utf8'))
const payload = JSON.parse(Buffer.from(token.split('.')[1], 'base64url').toString())
const base = 'http://127.0.0.1:18080'
const project = setup.project
const projectRoot = `/api/tenants/${setup.tenant}/projects/${project}`
const productRoot = `/api/timeline-git/products/${project}`
const authorized = { Authorization: `Bearer ${token}` }
const browser = await chromium.launch({ executablePath: '/usr/bin/google-chrome', headless: true,
  args: ['--no-sandbox', '--disable-dev-shm-usage'] })
const context = await browser.newContext({ acceptDownloads: true, viewport: { width: 1440, height: 1000 } })
await context.addInitScript(({ access, profile }) => {
  const user = { access_token: access, token_type: 'Bearer', profile,
    expires_at: profile.exp, scope: 'openid profile email', session_state: profile.sid }
  for (const authority of ['http://127.0.0.1:18081', 'http://127.0.0.1:18081/'])
    sessionStorage.setItem(`oidc.user:${authority}:f8-local`, JSON.stringify(user))
}, { access: token, profile: payload })
const result = { status: null, browser: browser.version(), base, stages: [], edits: [], revisions: [],
  requests: [], conflict: null, media: null, error: null }
const stage = name => { result.stages.push(name); writeFileSync(join(outRoot, 'workflow-progress.json'), JSON.stringify(result, null, 2)) }
const page = await context.newPage()
page.on('response', response => {
  const url = new URL(response.url())
  if (url.pathname.startsWith('/api/') && !url.pathname.includes('/auth/'))
    result.requests.push({ method: response.request().method(), path: url.pathname, status: response.status(), at: Date.now() })
})
const currentHead = async () => {
  const response = await page.request.get(`${base}${productRoot}/revisions/current`, { headers: authorized })
  if (response.status() !== 200) throw new Error(`HEAD read failed: ${response.status()}`)
  return response.json()
}
const snapshotOf = async revision => {
  const response = await page.request.get(`${base}/api/render/projects/${project}/timeline/revisions/${revision}/snapshot`,
    { headers: authorized })
  if (response.status() !== 200) throw new Error(`Snapshot read failed: ${response.status()}`)
  return JSON.parse((await response.json()).canonicalTimelineJson)
}
const clipsOf = document => document.tracks.flatMap(track => track.clips ?? [])
const seconds = value => {
  const [n, d] = String(value).split('/')
  return Number(n) / (d === undefined ? 1 : Number(d))
}
const assertClip = (clip, field, expected) => {
  if (Math.abs(seconds(clip[field]) - expected) > 1e-9)
    throw new Error(`${clip.clipId}.${field}: ${clip[field]} != ${expected}`)
}
const waitForHeadChange = async (previous, timeout = 60000) => {
  const deadline = Date.now() + timeout
  while (Date.now() < deadline) {
    const head = await currentHead()
    if (head.revisionId !== previous) return head
    await new Promise(resolve => setTimeout(resolve, 250))
  }
  throw new Error('authoritative HEAD did not change')
}
const sourceButton = name => page.getByRole('region', { name: 'Source media' })
  .getByRole('button', { name: new RegExp(name.replace('.', '\\.')) })
const addSource = async (sourceName, expectedStart) => {
  const previous = await currentHead()
  await sourceButton(sourceName).click()
  await page.waitForFunction(start => {
    const label = [...document.querySelectorAll('label')].find(item => item.textContent?.includes('Timeline start'))
    const input = label?.querySelector('input')
    return input && input.value === start
  }, `${expectedStart}/1`)
  await page.getByRole('button', { name: 'Preview operation' }).click()
  await page.getByLabel(/I confirm this exact frozen draft/i).waitFor({ timeout: 30000 })
  await page.getByLabel(/I confirm this exact frozen draft/i).check()
  await page.getByRole('button', { name: 'Confirm and apply' }).click()
  const head = await waitForHeadChange(previous.revisionId)
  const document = await snapshotOf(head.revisionId)
  result.revisions.push({ step: `ADD_${sourceName}`, revisionId: head.revisionId })
  return { head, document, clip: clipsOf(document).find(clip => clip.artifactId === setup.sources[sourceName === setup.sources.A.name ? 'A' : 'B'].artifactId) }
}
const selectClip = async clipId => {
  await page.getByLabel(`Timeline clip ${clipId}`).click()
}
const dragBody = async (clipId, pixels) => {
  const body = page.getByLabel(`Timeline clip ${clipId}`)
  await body.scrollIntoViewIfNeeded()
  const box = await body.boundingBox()
  if (!box) throw new Error(`clip ${clipId} has no box`)
  const x = box.x + box.width / 2, y = box.y + box.height / 2
  await page.mouse.move(x, y); await page.mouse.down()
  await page.mouse.move(x + pixels, y, { steps: 8 }); await page.mouse.up()
}
const dragEdge = async (clipId, edge, pixels) => {
  const handle = page.getByLabel(`Trim ${edge} edge ${clipId}`)
  await handle.scrollIntoViewIfNeeded()
  const box = await handle.boundingBox()
  if (!box) throw new Error(`${edge} handle of ${clipId} has no box`)
  const x = box.x + box.width / 2, y = box.y + box.height / 2
  await page.mouse.move(x, y); await page.mouse.down()
  await page.mouse.move(x + pixels, y, { steps: 8 }); await page.mouse.up()
}
const waitPreview = async revision => page.waitForFunction(rev => {
  const host = document.querySelector('[aria-label="Timeline media preview"]')
  return host?.getAttribute('data-preview-status') === 'READY'
    && host.getAttribute('data-preview-revision') === rev
}, revision, { timeout: 120000 })
const previewPixel = async at => {
  await page.getByLabel('Timeline preview seek').evaluate((element, value) => {
    const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set
    setter.call(element, String(value))
    element.dispatchEvent(new Event('input', { bubbles: true }))
    element.dispatchEvent(new Event('change', { bubbles: true }))
  }, at)
  await page.waitForTimeout(400)
  return page.evaluate(() => {
    const canvas = document.querySelector('canvas[aria-label="Timeline preview canvas"]')
    const data = canvas.getContext('2d').getImageData(160, 120, 1, 1).data
    return [data[0], data[1], data[2]]
  })
}
const colourOf = ([r, g, b]) => (r < 30 && g < 30 && b < 30) ? 'black'
  : r > 2 * Math.max(g, b) ? 'red' : g > 2 * Math.max(r, b) ? 'green' : b > 2 * Math.max(r, g) ? 'blue' : 'mixed'
try {
  await page.goto(`${base}/w/workspace-fr-e2e/projects/${project}/edit`, { waitUntil: 'domcontentloaded' })
  await page.getByRole('heading', { name: 'Timeline editor' }).waitFor({ timeout: 30000 })
  await page.getByRole('group', { name: 'Timeline trim' }).waitFor({ timeout: 30000 })
  stage('NLE_PAGE_OPEN')
  const pixelsPerSecond = Number(await page.getByRole('group', { name: 'Timeline trim' }).getAttribute('data-pixels-per-second'))
  if (!pixelsPerSecond) throw new Error('Clip strip scale unavailable')

  // Add A at 0 (2 s red), then B at 2 (2 s green).
  const addedA = await addSource('workflow-red.mp4', '0')
  const clipA = addedA.clip
  if (!clipA) throw new Error('added A clip missing from snapshot')
  assertClip(clipA, 'startTime', 0); assertClip(clipA, 'endTime', 2)
  result.edits.push({ step: 'ADD_A', clipId: clipA.clipId })
  stage('ADD_A_UPDATED')
  const addedB = await addSource('workflow-green.mp4', '2')
  const clipB = addedB.clip
  if (!clipB) throw new Error('added B clip missing from snapshot')
  assertClip(clipB, 'startTime', 2); assertClip(clipB, 'endTime', 4)
  result.edits.push({ step: 'ADD_B', clipId: clipB.clipId })
  stage('ADD_B_UPDATED')

  // Real Timeline preview: canvas frames + audio from the canonical source artifacts.
  const previewHead = await currentHead()
  await waitPreview(previewHead.revisionId)
  const previewBefore = { 0.5: colourOf(await previewPixel(0.5)), 2.5: colourOf(await previewPixel(2.5)) }
  if (previewBefore[0.5] !== 'red' || previewBefore[2.5] !== 'green')
    throw new Error(`preview frames do not match the Timeline: ${JSON.stringify(previewBefore)}`)
  await page.getByRole('button', { name: 'Play Timeline' }).click()
  await page.waitForTimeout(1300)
  const audioRms = Number(await page.getByLabel('Timeline media preview').getAttribute('data-audio-rms'))
  const playedTime = Number(await page.getByLabel('Timeline media preview').getAttribute('data-preview-time'))
  await page.getByRole('button', { name: 'Pause Timeline' }).click()
  if (!(audioRms > 0.001)) throw new Error(`preview audio RMS too low: ${audioRms}`)
  if (!(playedTime > 0.4)) throw new Error(`preview did not advance: ${playedTime}`)
  result.preview = { revision: previewHead.revisionId, samples: previewBefore, audioRms, playedTime }
  stage('TIMELINE_PREVIEW_PLAYED_WITH_AUDIO')

  // Move B to 4.
  let previous = await currentHead()
  await selectClip(clipB.clipId)
  await dragBody(clipB.clipId, 2 * pixelsPerSecond)
  await page.getByText(/Clip moved to 4\.00s/).waitFor({ timeout: 60000 })
  let movedHead = await waitForHeadChange(previous.revisionId)
  let document = await snapshotOf(movedHead.revisionId)
  assertClip(clipsOf(document).find(clip => clip.clipId === clipB.clipId), 'startTime', 4)
  result.edits.push({ step: 'MOVE_B', clipId: clipB.clipId, revisionId: movedHead.revisionId })
  stage('MOVE_B_UPDATED')

  // Trim A's end by 1 s.
  previous = await currentHead()
  await selectClip(clipA.clipId)
  await dragEdge(clipA.clipId, 'end', -pixelsPerSecond)
  await page.getByText(/Preview valid:/).waitFor({ timeout: 30000 })
  await page.getByRole('button', { name: 'Apply trim' }).click()
  await page.getByText(/Trim applied at revision/).waitFor({ timeout: 60000 })
  const trimmedHead = await waitForHeadChange(previous.revisionId)
  document = await snapshotOf(trimmedHead.revisionId)
  assertClip(clipsOf(document).find(clip => clip.clipId === clipA.clipId), 'endTime', 1)
  result.edits.push({ step: 'TRIM_A', clipId: clipA.clipId, revisionId: trimmedHead.revisionId })
  stage('TRIM_A_UPDATED')

  // The preview must rebind to the new revision without a page reload.
  const afterTrimHead = await currentHead()
  await waitPreview(afterTrimHead.revisionId)
  const previewAfter = { 0.5: colourOf(await previewPixel(0.5)), 2.5: colourOf(await previewPixel(2.5)) }
  if (previewAfter[0.5] !== 'red' || previewAfter[2.5] !== 'black')
    throw new Error(`preview did not refresh after trim: ${JSON.stringify(previewAfter)}`)
  result.previewAfterEdit = { revision: afterTrimHead.revisionId, samples: previewAfter }
  stage('PREVIEW_REFRESHED_AFTER_EDIT')

  // Split B at timeline 5.
  previous = await currentHead()
  await selectClip(clipB.clipId)
  await page.getByLabel('Clip operation').selectOption('SPLIT_CLIP')
  await page.getByLabel('Split time').fill('5')
  await page.getByLabel('Split selected clip').click()
  await page.getByText(/Clip split at 5/).waitFor({ timeout: 60000 })
  const splitHead = await waitForHeadChange(previous.revisionId)
  document = await snapshotOf(splitHead.revisionId)
  const splitClips = clipsOf(document)
  const left = splitClips.find(clip => clip.clipId === clipB.clipId)
  const right = splitClips.find(clip => clip.mediaAssetId === clipB.mediaAssetId && clip.clipId !== clipB.clipId)
  if (!left || !right) throw new Error('split halves missing from snapshot')
  assertClip(left, 'endTime', 5); assertClip(right, 'startTime', 5)
  result.edits.push({ step: 'SPLIT_B', left: left.clipId, right: right.clipId, revisionId: splitHead.revisionId })
  stage('SPLIT_B_UPDATED')

  // Move B-right to 6 and delete B-left.
  previous = await currentHead()
  await selectClip(right.clipId)
  await dragBody(right.clipId, pixelsPerSecond)
  await page.getByText(/Clip moved to 6\.00s/).waitFor({ timeout: 60000 })
  const rightMoved = await waitForHeadChange(previous.revisionId)
  document = await snapshotOf(rightMoved.revisionId)
  assertClip(clipsOf(document).find(clip => clip.clipId === right.clipId), 'startTime', 6)
  result.edits.push({ step: 'MOVE_B_RIGHT', clipId: right.clipId, revisionId: rightMoved.revisionId })
  stage('MOVE_B_RIGHT_UPDATED')

  previous = await currentHead()
  await selectClip(left.clipId)
  await page.getByRole('button', { name: 'Delete selected clip' }).click()
  await page.getByText(/deleted at revision/).waitFor({ timeout: 60000 })
  const deletedHead = await waitForHeadChange(previous.revisionId)
  document = await snapshotOf(deletedHead.revisionId)
  if (clipsOf(document).some(clip => clip.clipId === left.clipId)) throw new Error('deleted B-left still present')
  result.edits.push({ step: 'DELETE_B_LEFT', clipId: left.clipId, revisionId: deletedHead.revisionId })
  stage('DELETE_B_LEFT_UPDATED')

  // Page recovery: reload and verify authoritative state.
  await page.reload({ waitUntil: 'domcontentloaded' })
  await page.getByRole('heading', { name: 'Timeline editor' }).waitFor({ timeout: 30000 })
  await page.getByLabel(`Timeline clip ${clipA.clipId}`).waitFor({ timeout: 30000 })
  document = await snapshotOf((await currentHead()).revisionId)
  if (clipsOf(document).length !== 2) throw new Error('reload did not restore exactly two clips')
  result.edits.push({ step: 'RELOAD', clips: clipsOf(document).map(clip => clip.clipId) })
  stage('RELOAD_RESTORED')

  const reloadPreviewHead = await currentHead()
  await waitPreview(reloadPreviewHead.revisionId)
  const previewReload = { 0.5: colourOf(await previewPixel(0.5)), 6.5: colourOf(await previewPixel(6.5)) }
  if (previewReload[0.5] !== 'red' || previewReload[6.5] !== 'green')
    throw new Error(`reloaded preview does not match the Timeline: ${JSON.stringify(previewReload)}`)
  result.previewAfterReload = { revision: reloadPreviewHead.revisionId, samples: previewReload }
  stage('PREVIEW_RESTORED_AFTER_RELOAD')

  // Conflict: a concurrent canonical API edit advances HEAD; this page's loaded draft becomes stale.
  const beforeConflict = await currentHead()
  const moveResponse = await page.request.post(`${base}${projectRoot}/timeline-operations/clips/apply`, {
    headers: authorized,
    data: { type: 'MOVE_CLIP', baseRevisionId: beforeConflict.revisionId,
      baseContentHash: beforeConflict.revision.semanticContext.timelineContentDigest,
      clipId: clipA.clipId, delta: '1/1', absolute: true, commandId: crypto.randomUUID() },
  })
  if (moveResponse.status() !== 200 && moveResponse.status() !== 201)
    throw new Error(`concurrent move failed: ${moveResponse.status()} ${await moveResponse.text()}`)
  const pageBHead = await waitForHeadChange(beforeConflict.revisionId)
  result.conflict = { before: beforeConflict.revisionId, pageBRevision: pageBHead.revisionId }
  const staleTrim = page.getByLabel(`Trim end edge ${clipA.clipId}`)
  await staleTrim.scrollIntoViewIfNeeded()
  const trimBox = await staleTrim.boundingBox()
  if (!trimBox) throw new Error('stale trim handle missing')
  await page.mouse.move(trimBox.x + trimBox.width / 2, trimBox.y + trimBox.height / 2)
  await page.mouse.down()
  await page.mouse.move(trimBox.x + trimBox.width / 2 - 40, trimBox.y + trimBox.height / 2, { steps: 6 })
  await page.mouse.up()
  await page.getByText(/Preview valid:/).waitFor({ timeout: 30000 })
  await page.getByRole('button', { name: 'Apply trim' }).click()
  await page.getByRole('alert').waitFor({ timeout: 30000 })
  const conflictText = await page.getByRole('alert').textContent()
  if (!/HEAD has changed|timeline changed/i.test(conflictText ?? '')) throw new Error(`unexpected conflict text: ${conflictText}`)
  const afterConflict = await currentHead()
  if (afterConflict.revisionId !== pageBHead.revisionId) throw new Error('page A changed HEAD during conflict')
  const conflictDocument = await snapshotOf(afterConflict.revisionId)
  assertClip(clipsOf(conflictDocument).find(clip => clip.clipId === clipA.clipId), 'startTime', 1)
  result.conflict.message = conflictText
  result.conflict.headAfter = afterConflict.revisionId
  result.conflict.preserved = true
  stage('CONFLICT_REJECTED_AND_RECOVERED')

  // Reload to the latest HEAD, then render the final timeline (A 1-2, B-right 6-7).
  await page.reload({ waitUntil: 'domcontentloaded' })
  await page.getByRole('heading', { name: 'Timeline editor' }).waitFor({ timeout: 30000 })
  const finalHead = await currentHead()
  const finalDocument = await snapshotOf(finalHead.revisionId)
  const finalClips = clipsOf(finalDocument)
  if (finalClips.length !== 2) throw new Error('unexpected final clip count')
  assertClip(finalClips.find(clip => clip.clipId === clipA.clipId), 'startTime', 1)
  assertClip(finalClips.find(clip => clip.clipId === right.clipId), 'startTime', 6)
  result.edits.push({ step: 'FINAL', revisionId: finalHead.revisionId })
  await waitPreview(finalHead.revisionId)
  const finalPreview = {}
  for (const at of [0.5, 1.5, 3.5, 5.5, 6.5]) finalPreview[at] = colourOf(await previewPixel(at))
  const expectedFinal = { 0.5: 'black', 1.5: 'red', 3.5: 'black', 5.5: 'black', 6.5: 'green' }
  for (const [at, expected] of Object.entries(expectedFinal))
    if (finalPreview[at] !== expected) throw new Error(`final preview ${at}: expected ${expected}, got ${JSON.stringify(finalPreview[at])}`)
  result.previewFinal = finalPreview
  await page.getByLabel('Output start (ms)').fill('0')
  await page.getByLabel('Output end (ms)').fill('7000')
  await page.getByRole('button', { name: 'Submit RenderJob' }).click()
  await page.getByText(/RenderJob .* accepted for revision/).waitFor({ timeout: 30000 })
  const jobId = await page.getByLabel('Current RenderJob').locator('code').first().textContent()
  await page.getByLabel('Current RenderJob').getByText('COMPLETED', { exact: true }).waitFor({ timeout: 240000 })
  const render = await page.request.get(`${base}/api/tenants/${setup.tenant}/projects/${project}/render-jobs/${jobId}/result`, { headers: authorized })
  const job = await render.json()
  if (!job.finalArtifactId) throw new Error('completed render has no final artifact')
  await page.getByRole('button', { name: 'Create delivery' }).click()
  await page.getByText(/Delivery /).first().waitFor({ timeout: 30000 })
  const downloadPromise = page.waitForEvent('download')
  await page.getByRole('button', { name: 'Download output video' }).click()
  const download = await downloadPromise
  const output = join(outRoot, 'workflow-output.mp4')
  await download.saveAs(output)
  copyFileSync(output, join(appRoot, 'downloaded-final.mp4'))
  const probe = JSON.parse(execFileSync('/usr/bin/ffprobe', ['-v', 'error', '-count_frames', '-show_entries',
    'format=duration:stream=codec_name,width,height,avg_frame_rate,nb_read_frames', '-of', 'json', output]).toString())
  const video = probe.streams[0]
  const sample = at => [...execFileSync('/usr/bin/ffmpeg', ['-v', 'error', '-ss', String(at), '-i', output,
    '-frames:v', '1', '-vf', 'scale=1:1', '-f', 'rawvideo', '-pix_fmt', 'rgb24', 'pipe:1'])]
  const dominant = ([r, g, b]) => (r < 30 && g < 30 && b < 30) ? 'black'
    : r > 2 * Math.max(g, b) ? 'red' : g > 2 * Math.max(r, b) ? 'green' : b > 2 * Math.max(r, g) ? 'blue' : 'mixed'
  const samples = { 0.5: 'black', 1.5: 'red', 3.5: 'black', 5.5: 'black', 6.5: 'green' }
  const observed = {}
  for (const [at, expected] of Object.entries(samples)) {
    const rgb = sample(Number(at)); observed[at] = { rgb, colour: dominant(rgb) }
    if (dominant(rgb) !== expected) throw new Error(`sample ${at}: expected ${expected}, got ${JSON.stringify(observed[at])}`)
  }
  result.media = { jobId, artifactId: job.finalArtifactId, durationSeconds: Number(probe.format.duration),
    frames: Number(video.nb_read_frames), codec: video.codec_name, width: video.width, height: video.height,
    sha256: createHash('sha256').update(readFileSync(output)).digest('hex'), samples: observed }
  if (Math.abs(result.media.durationSeconds - 7) > 0.01 || result.media.frames !== 168)
    throw new Error(`unexpected media output ${JSON.stringify(result.media)}`)
  const parity = {}
  for (const at of Object.keys(samples)) {
    const expected = observed[at].colour
    if (result.previewFinal[at] !== expected)
      throw new Error(`preview/render parity ${at}: preview ${result.previewFinal[at]} vs render ${expected}`)
    parity[at] = { preview: result.previewFinal[at], render: expected }
  }
  result.previewRenderParity = parity
  const sourceContentRequests = result.requests.filter(request =>
    request.path.includes('/source-artifacts/') && request.path.endsWith('/content'))
  const snapshotRequests = result.requests.filter(request =>
    request.path.includes('/timeline/revisions/') && request.path.endsWith('/snapshot'))
  const snapshotsByRevision = {}
  for (const request of snapshotRequests) snapshotsByRevision[request.path] = (snapshotsByRevision[request.path] ?? 0) + 1
  const maxSnapshotsPerRevision = Math.max(0, ...Object.values(snapshotsByRevision))
  const requestsBySecond = {}
  for (const request of result.requests) {
    const second = Math.floor((request.at ?? 0) / 1000)
    requestsBySecond[second] = (requestsBySecond[second] ?? 0) + 1
  }
  const maxRequestsPerSecond = Math.max(0, ...Object.values(requestsBySecond))
  result.requestBounds = { sourceContentRequests: sourceContentRequests.length,
    snapshotRequests: snapshotRequests.length, maxSnapshotsPerRevision, maxRequestsPerSecond,
    totalRequests: result.requests.length }
  if (sourceContentRequests.length > 8)
    throw new Error(`source content request storm: ${JSON.stringify(result.requestBounds)}`)
  if (maxSnapshotsPerRevision > 12)
    throw new Error(`snapshot request storm for one revision: ${JSON.stringify(result.requestBounds)}`)
  if (maxRequestsPerSecond > 50)
    throw new Error(`request rate too high: ${JSON.stringify(result.requestBounds)}`)
  stage('RENDER_AND_DELIVERY_VERIFIED')
  result.status = 'PASS'
} catch (error) {
  result.status = 'FAIL'; result.error = String(error)
  result.failurePage = { url: page.url(), bodyPreview: await page.locator('body').innerText().then(v => v.slice(0, 1000)).catch(() => '') }
  await page.screenshot({ path: join(outRoot, 'workflow-failure.png'), fullPage: true }).catch(() => {})
  throw error
} finally {
  writeFileSync(join(outRoot, 'workflow-browser-result.json'), JSON.stringify(result, null, 2))
  writeFileSync(join(appRoot, 'workflow-e2e.json'), JSON.stringify({ result: result.status, ...result }, null, 2))
  await browser.close()
}
