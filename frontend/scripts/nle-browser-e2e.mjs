import { chromium } from 'playwright'
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs'
import { join } from 'node:path'
import { execFileSync } from 'node:child_process'
import { createHash } from 'node:crypto'

const [appRoot, outRoot] = process.argv.slice(2)
if (!appRoot || !outRoot) throw new Error('Owned app and output paths required')
mkdirSync(outRoot, { recursive: true })
const token = readFileSync(join(appRoot, 'token'), 'utf8').trim()
const payload = JSON.parse(Buffer.from(token.split('.')[1], 'base64url').toString())
const base = 'http://127.0.0.1:18080'
const browser = await chromium.launch({ executablePath: '/usr/bin/google-chrome', headless: true,
  args: ['--no-sandbox', '--disable-dev-shm-usage'] })
const context = await browser.newContext({ acceptDownloads: true, viewport: { width: 1440, height: 1000 } })
await context.addInitScript(({ access, profile }) => {
  const user = { access_token: access, token_type: 'Bearer', profile,
    expires_at: profile.exp, scope: 'openid profile email', session_state: profile.sid }
  for (const authority of ['http://127.0.0.1:18081', 'http://127.0.0.1:18081/']) {
    sessionStorage.setItem(`oidc.user:${authority}:f8-local`, JSON.stringify(user))
  }
}, { access: token, profile: payload })
const page = await context.newPage()
const requests = []
page.on('response', response => {
  const url = new URL(response.url())
  if (url.pathname.startsWith('/api/') && !url.pathname.includes('/auth/'))
    requests.push({ method: response.request().method(), path: url.pathname, status: response.status() })
})
const result = { browser: browser.version(), base, requests, stages: [], jobId: null, artifactId: null,
  deliveryId: null, media: null, negative: [], responses: [], edits: [] }
const stage = name => { result.stages.push(name); writeFileSync(join(outRoot, 'progress.json'), JSON.stringify(result, null, 2)) }
const projectRoot = '/api/tenants/tenant-fr-e2e/projects/project-fr-e2e'
const productRoot = '/api/timeline-git/products/project-fr-e2e'
const authorized = { Authorization: `Bearer ${token}` }
function negative(name, status, accepted) {
  if (!accepted.includes(status)) throw new Error(`${name} returned ${status}; expected ${accepted.join('/')}`)
  result.negative.push({ name, status })
}
const sourcePath = join(outRoot, 'gradient-6s.mp4')
execFileSync('/usr/bin/ffmpeg', ['-hide_banner', '-loglevel', 'error', '-nostdin', '-y',
  '-f', 'lavfi', '-i', 'color=c=red:s=320x240:r=30:d=2',
  '-f', 'lavfi', '-i', 'color=c=green:s=320x240:r=30:d=2',
  '-f', 'lavfi', '-i', 'color=c=blue:s=320x240:r=30:d=2',
  '-filter_complex', '[0:v][1:v][2:v]concat=n=3:v=1:a=0[v]', '-map', '[v]',
  '-c:v', 'libopenh264', '-pix_fmt', 'yuv420p', sourcePath])
result.source = { path: sourcePath, sha256: createHash('sha256').update(readFileSync(sourcePath)).digest('hex') }
const samplePixel = (file, seconds) => [...execFileSync('/usr/bin/ffmpeg', ['-v', 'error', '-ss', String(seconds),
  '-i', file, '-frames:v', '1', '-vf', 'scale=1:1', '-f', 'rawvideo', '-pix_fmt', 'rgb24', 'pipe:1'])]
const dominant = ([r, g, b]) => r > 80 && r > 2 * Math.max(g, b) ? 'red'
  : g > 80 && g > 2 * Math.max(r, b) ? 'green'
    : b > 80 && b > 2 * Math.max(r, g) ? 'blue' : 'mixed'
const currentHead = async () => {
  const response = await page.request.get(`${base}${productRoot}/revisions/current`, { headers: authorized })
  if (response.status() !== 200) throw new Error(`HEAD read failed: ${response.status()}`)
  return response.json()
}
const snapshotOf = async revision => {
  const response = await page.request.get(`${base}/api/render/projects/project-fr-e2e/timeline/revisions/${revision}/snapshot`,
    { headers: authorized })
  if (response.status() !== 200) throw new Error(`Snapshot read failed: ${response.status()}`)
  const body = await response.json()
  result.responses.push({ snapshotRevision: revision, digest: createHash('sha256')
    .update(body.canonicalTimelineJson).digest('hex') })
  return JSON.parse(body.canonicalTimelineJson)
}
const clipsOf = document => document.tracks.flatMap(track => track.clips ?? [])
const requireClip = (document, clipId) => {
  const clip = clipsOf(document).find(row => row.clipId === clipId)
  if (!clip) throw new Error(`clip ${clipId} missing from the canonical snapshot`)
  return clip
}
const parseTimelineTime = value => {
  const [numerator, denominator] = String(value).split('/')
  return Number(numerator) / (denominator === undefined ? 1 : Number(denominator))
}
const assertClipTime = (clip, field, expected) => {
  if (Math.abs(parseTimelineTime(clip[field]) - expected) > 1e-9)
    throw new Error(`clip ${clip.clipId} ${field}: ${clip[field]} != ${expected}`)
}
try {
  const navigation = await page.goto(`${base}/w/workspace-fr-e2e/projects/project-fr-e2e/edit`, { waitUntil: 'domcontentloaded' })
  result.navigation = { status: navigation?.status() ?? null, initialUrl: page.url() }
  await page.getByRole('heading', { name: 'Timeline editor' }).waitFor({ timeout: 30000 })
  await page.getByText('Workspace and Project scope verified by authenticated server reads.').waitFor({ timeout: 30000 })
  stage('NLE_PAGE_OPEN')
  const sourceArea = page.getByRole('region', { name: 'Source media' })
  await sourceArea.getByLabel('Upload video or audio (64 MiB maximum)').setInputFiles({ name: 'invalid.txt', mimeType: 'text/plain', buffer: Buffer.from('invalid media') })
  await sourceArea.getByRole('button', { name: 'Upload source' }).click()
  await sourceArea.getByRole('alert').waitFor({ timeout: 30000 })
  result.negative.push({ name: 'INVALID_UPLOAD', message: await sourceArea.getByRole('alert').textContent() })
  const anonymousSource = await page.request.get(`${base}${projectRoot}/source-media`)
  negative('SOURCE_AUTHORIZATION_DENIED', anonymousSource.status(), [401, 403])
  const headBefore = await currentHead()
  await page.getByLabel('Upload video or audio (64 MiB maximum)').setInputFiles(sourcePath)
  await page.getByRole('button', { name: 'Upload source' }).click()
  await page.getByText(/Accepted gradient-6s\.mp4 as Source Artifact/).waitFor({ timeout: 60000 })
  stage('SOURCE_UPLOAD_ACCEPTED')
  const sourceResponse = await page.request.get(`${base}${projectRoot}/source-media`, { headers: authorized })
  if (sourceResponse.status() !== 200) throw new Error(`Source catalog read failed: ${sourceResponse.status()}`)
  const source = (await sourceResponse.json()).find(row => row.name === 'gradient-6s.mp4')
  if (!source) throw new Error('Uploaded source absent from authoritative catalog')
  const videoStream = source.streams.find(stream => stream.kind === 'VIDEO')
  const addClip = async (clipId, sourceStart, sourceEnd, timelineStart, timelineEnd) => {
    const head = await currentHead()
    const body = {
      baseRevisionId: head.revisionId,
      baseContentHash: head.revision.semanticContext.timelineContentDigest,
      trackId: 'track-1', clipId, mediaAssetId: source.artifactId, mediaStreamId: videoStream.streamId,
      artifactId: source.artifactId, contentDigest: source.contentDigest,
      sourceStart, sourceEnd, timelineStart, timelineEnd,
      rateNumerator: 1, rateDenominator: 1, direction: 'FORWARD',
    }
    const preview = await page.request.post(`${base}${projectRoot}/timeline-operations/add-media-clip/preview`,
      { headers: authorized, data: body })
    if (preview.status() !== 200)
      throw new Error(`add clip preview ${clipId} failed: ${preview.status()} ${await preview.text()}`)
    const planned = await preview.json()
    const response = await page.request.post(`${base}${projectRoot}/timeline-operations/add-media-clip/apply`,
      { headers: authorized, data: { request: body, expectedPlanDigest: planned.planDigest,
        applyCommandId: crypto.randomUUID() } })
    if (response.status() !== 200 && response.status() !== 201)
      throw new Error(`add clip ${clipId} failed: ${response.status()} ${await response.text()}`)
    return response.json()
  }
  // The harness seeds project-fr-e2e with its own operation-editing clips. Clear them through the
  // formal DELETE operation so this scenario starts from a known canonical timeline.
  for (let guard = 0; guard < 20; guard++) {
    const head = await currentHead()
    const document = await snapshotOf(head.revisionId)
    const existing = clipsOf(document)
    if (existing.length === 0) break
    const response = await page.request.post(`${base}${projectRoot}/timeline-operations/clips/apply`, {
      headers: authorized, data: { type: 'DELETE_CLIP', baseRevisionId: head.revisionId,
        baseContentHash: head.revision.semanticContext.timelineContentDigest,
        clipId: existing[0].clipId, commandId: crypto.randomUUID() },
    })
    if (response.status() !== 200 && response.status() !== 201)
      throw new Error(`clear clip ${existing[0].clipId} failed: ${response.status()} ${await response.text()}`)
  }
  // Setup through the formal Operation API: three adjacent clips referencing the one 6 s source.
  // All user-visible Move/Delete/Trim edits below happen through the real editor UI.
  const added = []
  added.push(await addClip('clip-a', '0', '2', '0', '2'))
  added.push(await addClip('clip-b', '2', '4', '2', '4'))
  added.push(await addClip('clip-c', '4', '6', '4', '6'))
  result.initial = { head: headBefore.revisionId, clips: ['clip-a', 'clip-b', 'clip-c'], revisions: added.map(r => r.revisionId) }
  stage('MULTI_CLIP_TIMELINE_CREATED')
  await page.reload({ waitUntil: 'domcontentloaded' })
  await page.getByRole('heading', { name: 'Timeline editor' }).waitFor({ timeout: 30000 })
  const strip = page.getByRole('group', { name: 'Timeline trim' })
  await strip.waitFor({ timeout: 30000 })
  await page.getByLabel('Timeline clip clip-b').waitFor({ timeout: 30000 })
  const pixelsPerSecond = Number(await strip.getAttribute('data-pixels-per-second'))
  if (!pixelsPerSecond) throw new Error('Clip strip scale unavailable')
  const dragEdge = async (clipId, edge, pixels) => {
    const handle = page.getByLabel(`Trim ${edge} edge ${clipId}`)
    await handle.waitFor({ timeout: 30000 })
    await handle.scrollIntoViewIfNeeded()
    const box = await handle.boundingBox()
    if (!box) throw new Error(`${edge} trim handle of ${clipId} has no box`)
    const x = box.x + box.width / 2, y = box.y + box.height / 2
    await page.mouse.move(x, y); await page.mouse.down()
    await page.mouse.move(x + pixels, y, { steps: 8 }); await page.mouse.up()
  }
  const dragBody = async (clipId, pixels) => {
    const body = page.getByLabel(`Timeline clip ${clipId}`)
    await body.waitFor({ timeout: 30000 })
    await body.scrollIntoViewIfNeeded()
    const box = await body.boundingBox()
    if (!box) throw new Error(`clip ${clipId} has no box`)
    const x = box.x + box.width / 2, y = box.y + box.height / 2
    await page.mouse.move(x, y); await page.mouse.down()
    await page.mouse.move(x + pixels, y, { steps: 8 }); await page.mouse.up()
  }
  // 1) TRIM clip C's right edge by 1 s (source 4-6 -> 4-5, timeline 4-5).
  await dragEdge('clip-c', 'end', -1 * pixelsPerSecond)
  await page.getByText(/Preview valid: source 4\/1s → 5\/1s/).waitFor({ timeout: 30000 })
  await page.getByRole('button', { name: 'Apply trim' }).click()
  await page.getByText(/Trim applied at revision/).waitFor({ timeout: 30000 })
  const afterTrimHead = await currentHead()
  const afterTrim = await snapshotOf(afterTrimHead.revisionId)
  result.edits.push({ stage: 'TRIM', revision: afterTrimHead.revisionId, clip: requireClip(afterTrim, 'clip-c') })
  assertClipTime(requireClip(afterTrim, 'clip-c'), 'trimEnd', 5)
  stage('TRIM_APPLIED')
  // 2) DELETE clip B through the explicit editor delete entry.
  await page.getByLabel('Timeline clip clip-b').click()
  await page.getByRole('button', { name: 'Delete selected clip' }).click()
  await page.getByText(/deleted at revision/).waitFor({ timeout: 30000 })
  const afterDeleteHead = await currentHead()
  const afterDelete = await snapshotOf(afterDeleteHead.revisionId)
  if (clipsOf(afterDelete).some(clip => clip.clipId === 'clip-b')) throw new Error('deleted clip still present')
  const deletedStaleMove = await page.request.post(`${base}${projectRoot}/timeline-operations/clips/apply`, {
    headers: authorized, data: { type: 'MOVE_CLIP', baseRevisionId: headBefore.revisionId,
      baseContentHash: headBefore.revision.semanticContext.timelineContentDigest,
      clipId: 'clip-b', delta: '0', absolute: true, commandId: crypto.randomUUID() },
  })
  negative('DELETED_CLIP_EDIT_REJECTED', deletedStaleMove.status(), [400, 404, 409, 422])
  result.edits.push({ stage: 'DELETE', revision: afterDeleteHead.revisionId, clips: clipsOf(afterDelete).map(clip => clip.clipId) })
  stage('DELETE_APPLIED')
  // 3) MOVE clip C to the timeline start 2 s (absolute, non-ripple): 4-5 -> 2-3.
  await dragBody('clip-c', -2 * pixelsPerSecond)
  await page.getByText(/Clip moved to 2\.00s → 3\.00s/).waitFor({ timeout: 30000 })
  const afterMoveHead = await currentHead()
  const afterMove = await snapshotOf(afterMoveHead.revisionId)
  const moved = requireClip(afterMove, 'clip-c')
  result.edits.push({ stage: 'MOVE', revision: afterMoveHead.revisionId, clip: moved })
  if (moved.clipId !== 'clip-c') throw new Error('move changed the clip identity')
  assertClipTime(moved, 'startTime', 2)
  assertClipTime(moved, 'endTime', 3)
  assertClipTime(moved, 'trimStart', 4)
  assertClipTime(moved, 'trimEnd', 5)
  const a = requireClip(afterMove, 'clip-a')
  assertClipTime(a, 'startTime', 0)
  assertClipTime(a, 'endTime', 2)
  if (clipsOf(afterMove).length !== 2) throw new Error('expected exactly two clips after delete')
  stage('MOVE_APPLIED')
  // 4) Refresh: the editor must restore the authoritative revision.
  await page.reload({ waitUntil: 'domcontentloaded' })
  await page.getByRole('heading', { name: 'Timeline editor' }).waitFor({ timeout: 30000 })
  await page.getByText(afterMoveHead.revisionId, { exact: true }).first().waitFor({ timeout: 30000 })
  const restored = await snapshotOf(afterMoveHead.revisionId)
  if (JSON.stringify(clipsOf(restored)) !== JSON.stringify(clipsOf(afterMove)))
    throw new Error('reloaded snapshot differs from the applied edits')
  stage('REFRESH_AUTHORITATIVE_RESTORE')
  await page.screenshot({ path: join(outRoot, 'multi-clip-edited.png'), fullPage: true })
  // 5) Render the edited timeline through the formal RenderJob and verify the media.
  await page.getByLabel('Output start (ms)').fill('0')
  await page.getByLabel('Output end (ms)').fill('3000')
  await page.getByRole('button', { name: 'Submit RenderJob' }).click()
  await page.getByText(/RenderJob .* accepted for revision/).waitFor({ timeout: 30000 })
  const jobId = await page.getByLabel('Current RenderJob').locator('code').first().textContent()
  result.jobId = jobId
  stage('FORMAL_RENDERJOB_ACCEPTED')
  await page.getByLabel('Current RenderJob').getByText('COMPLETED', { exact: true }).waitFor({ timeout: 180000 })
  stage('WORKER_COMPLETED')
  const response = await page.request.get(`${base}/api/tenants/tenant-fr-e2e/projects/project-fr-e2e/render-jobs/${jobId}/result`,
    { headers: authorized })
  if (response.status() !== 200) throw new Error(`Result read failed: ${response.status()}`)
  const job = await response.json(); result.artifactId = job.finalArtifactId
  result.responses.push({ path: `render-jobs/${jobId}/result`, status: response.status(),
    renderJobId: job.renderJobId, jobStatus: job.status, finalArtifactId: job.finalArtifactId })
  if (!result.artifactId) throw new Error('Completed job has no final Artifact')
  await page.getByRole('button', { name: 'Create delivery' }).click()
  await page.getByText(/Delivery /).first().waitFor({ timeout: 30000 })
  const deliveryId = await page.locator('[aria-label="Render and delivery"] code').last().textContent()
  result.deliveryId = deliveryId
  const downloadPromise = page.waitForEvent('download')
  await page.getByRole('button', { name: 'Download output video' }).click()
  const download = await downloadPromise
  const output = join(outRoot, 'move-delete-output.mp4')
  await download.saveAs(output)
  const bytes = readFileSync(output)
  const probe = JSON.parse(execFileSync('/usr/bin/ffprobe', ['-v','error','-show_format','-show_streams','-of','json',output]).toString())
  execFileSync('/usr/bin/ffmpeg', ['-v','error','-i',output,'-f','null','-'])
  result.media = { sha256: createHash('sha256').update(bytes).digest('hex'), bytes: bytes.length,
    format: probe.format.format_name, durationSeconds: Number(probe.format.duration),
    streams: probe.streams.map(stream => ({ codec: stream.codec_name, type: stream.codec_type,
      width: stream.width, height: stream.height, frameRate: stream.avg_frame_rate })) }
  if (!result.media.bytes || !result.media.streams.some(stream => stream.type === 'video')) throw new Error('Downloaded media is not decoded video')
  if (result.media.durationSeconds < 2.9 || result.media.durationSeconds > 3.2) throw new Error(`Unexpected edited duration ${result.media.durationSeconds}`)
  // Content proof: clip A (red 0-2) then moved/trimmed clip C (blue, source 4-5 at timeline 2-3).
  // The deleted green clip must be absent.
  const samples = { aStart: 0.4, aEnd: 1.6, cStart: 2.2, cEnd: 2.8 }
  const observed = {}
  for (const [label, at] of Object.entries(samples)) {
    const rgb = samplePixel(output, at)
    observed[label] = { at, rgb, colour: dominant(rgb) }
  }
  if (observed.aStart.colour !== 'red' || observed.aEnd.colour !== 'red'
      || observed.cStart.colour !== 'blue' || observed.cEnd.colour !== 'blue')
    throw new Error(`Move/Delete output does not match the edited timeline: ${JSON.stringify(observed)}`)
  const greenProbe = samplePixel(output, 1.0)
  if (dominant(greenProbe) === 'green') throw new Error('deleted green clip leaked into the output')
  result.media.samples = observed
  result.media.deletedClipProbe = { at: 1.0, rgb: greenProbe, colour: dominant(greenProbe) }
  const anonymousDownload = await page.request.get(`${base}/api/projects/project-fr-e2e/deliveries/${deliveryId}/result`)
  negative('DELIVERY_DOWNLOAD_AUTHORIZATION_DENIED', anonymousDownload.status(), [401, 403])
  stage('DELIVERY_DOWNLOAD_MEDIA_VERIFIED')
  await page.screenshot({ path: join(outRoot, 'move-delete-final.png'), fullPage: true })
  result.status = 'PASS'
} catch (error) {
  result.status = 'FAIL'; result.error = String(error)
  result.failurePage = { url: page.url(), title: await page.title().catch(() => ''),
    bodyPreview: await page.locator('body').innerText().then(value => value.slice(0, 800)).catch(() => '') }
  await page.screenshot({ path: join(outRoot, 'move-delete-failure.png'), fullPage: true }).catch(() => {})
  throw error
} finally {
  writeFileSync(join(outRoot, 'browser-result.json'), JSON.stringify(result, null, 2))
  await browser.close()
}
