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
  deliveryId: null, media: null, negative: [], responses: [], trims: [] }
const stage = name => { result.stages.push(name); writeFileSync(join(outRoot, 'progress.json'), JSON.stringify(result, null, 2)) }
const projectRoot = '/api/tenants/tenant-fr-e2e/projects/project-fr-e2e'
const authorized = { Authorization: `Bearer ${token}` }
function negative(name, status, accepted) {
  if (!accepted.includes(status)) throw new Error(`${name} returned ${status}; expected ${accepted.join('/')}`)
  result.negative.push({ name, status })
}
// Three exact colour segments so a trim boundary can be proven from the rendered pixels:
// 0-2 s red, 2-4 s green, 4-6 s blue. Synthetic task-owned fixture, never a product feature.
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
const snapshotClips = async (revision) => {
  const response = await page.request.get(`${base}/api/render/projects/project-fr-e2e/timeline/revisions/${revision}/snapshot`,
    { headers: authorized })
  if (response.status() !== 200) throw new Error(`Snapshot read failed: ${response.status()}`)
  const body = await response.json()
  const document = JSON.parse(body.canonicalTimelineJson)
  return document.tracks.flatMap(track => track.clips ?? [])
}
try {
  const navigation = await page.goto(`${base}/w/workspace-fr-e2e/projects/project-fr-e2e/edit`, { waitUntil: 'domcontentloaded' })
  result.navigation = { status: navigation?.status() ?? null, initialUrl: page.url() }
  await page.getByRole('heading', { name: 'Timeline editor' }).waitFor({ timeout: 30000 })
  await page.getByText('Source media', { exact: true }).waitFor({ timeout: 30000 })
  await page.getByText('Workspace and Project scope verified by authenticated server reads.').waitFor({ timeout: 30000 })
  stage('NLE_PAGE_OPEN')
  const sourceArea = page.getByRole('region', { name: 'Source media' })
  await sourceArea.getByLabel('Upload video or audio (64 MiB maximum)').setInputFiles({ name: 'invalid.txt', mimeType: 'text/plain', buffer: Buffer.from('invalid media') })
  await sourceArea.getByRole('button', { name: 'Upload source' }).click()
  await sourceArea.getByRole('alert').waitFor({ timeout: 30000 })
  result.negative.push({ name: 'INVALID_UPLOAD', message: await sourceArea.getByRole('alert').textContent() })
  const anonymousSource = await page.request.get(`${base}${projectRoot}/source-media`)
  negative('SOURCE_AUTHORIZATION_DENIED', anonymousSource.status(), [401, 403])
  const staleRevision = await page.locator('.ff-head-identity code').nth(0).textContent()
  const staleHash = await page.locator('.ff-head-identity code').nth(1).textContent()
  await page.getByLabel('Upload video or audio (64 MiB maximum)').setInputFiles(sourcePath)
  await page.getByRole('button', { name: 'Upload source' }).click()
  await page.getByText(/Accepted gradient-6s\.mp4 as Source Artifact/).waitFor({ timeout: 60000 })
  stage('SOURCE_UPLOAD_ACCEPTED')
  await page.screenshot({ path: join(outRoot, 'source-upload.png'), fullPage: true })
  // Fixture setup: the operation-editing fixture leaves its own clips on the track. Remove them
  // through the formal Operation API so the trim scenario renders exactly one clip. The trims
  // themselves are performed as user interaction below, never by constructing a document.
  for (const stale of await (async () => {
    const headResponse = await page.request.get(`${base}/api/timeline-git/products/project-fr-e2e/revisions/current`, { headers: authorized })
    const current = await headResponse.json()
    return snapshotClips(current.revisionId)
  })()) {
    const headResponse = await page.request.get(`${base}/api/timeline-git/products/project-fr-e2e/revisions/current`, { headers: authorized })
    const current = await headResponse.json()
    const removal = await page.request.post(`${base}${projectRoot}/timeline-operations/clips/apply`, {
      headers: authorized, data: { type: 'DELETE_CLIP', baseRevisionId: current.revisionId,
        baseContentHash: current.revision.semanticContext.timelineContentDigest,
        clipId: stale.clipId, commandId: crypto.randomUUID() },
    })
    if (removal.status() !== 201 && removal.status() !== 200)
      throw new Error(`Fixture clip cleanup failed for ${stale.clipId}: ${removal.status()}`)
  }
  await page.reload({ waitUntil: 'domcontentloaded' })
  await page.getByRole('heading', { name: 'Timeline editor' }).waitFor({ timeout: 30000 })
  // The reload drops the in-memory source pin; re-select the uploaded source from the browser.
  await page.getByRole('button', { name: /gradient-6s\.mp4/ }).first().click()
  result.responses.push({ path: 'fixture-clip-cleanup', result: 'REMOVED_PREEXISTING_CLIPS' })
  await page.getByLabel('Track ID').fill('track-1')
  const clipId = await page.getByLabel('Clip ID').inputValue()
  await page.getByLabel('Source start (exact rational)').fill('0')
  await page.getByLabel('Source end (exact rational)').fill('6')
  await page.getByLabel('Timeline start (exact rational)').fill('0')
  await page.getByLabel('Timeline end (exact rational)').fill('6')
  await page.getByRole('button', { name: 'Preview operation' }).click()
  await page.getByText('Server preview', { exact: true }).waitFor({ timeout: 30000 })
  await page.getByLabel(/I confirm this exact frozen draft/).check()
  await page.getByRole('button', { name: 'Confirm and apply' }).click()
  await page.getByText(/Server accepted revision/).waitFor({ timeout: 30000 })
  stage('ADD_OPERATION_REVISION')
  const staleEdit = await page.request.post(`${base}${projectRoot}/timeline-operations/clips/apply`, {
    headers: authorized, data: { type: 'MOVE_CLIP', baseRevisionId: staleRevision, baseContentHash: staleHash,
      clipId, delta: '1', absolute: false, commandId: `stale-browser-${Date.now()}` },
  })
  negative('REVISION_CAS_CONFLICT', staleEdit.status(), [404, 409])
  const sourceResponse = await page.request.get(`${base}${projectRoot}/source-media`, { headers: authorized })
  if (sourceResponse.status() !== 200) throw new Error(`Source catalog read failed: ${sourceResponse.status()}`)
  const source = (await sourceResponse.json()).find(row => row.name === 'gradient-6s.mp4')
  if (!source) throw new Error('Uploaded source absent from authoritative catalog')
  result.responses.push({ path: `${projectRoot}/source-media`, status: 200, artifactId: source.artifactId,
    contentDigest: source.contentDigest, duration: source.duration, streams: source.streams })
  const currentRevision = await page.locator('.ff-head-identity code').nth(0).textContent()
  const currentHash = await page.locator('.ff-head-identity code').nth(1).textContent()
  const missingSource = await page.request.post(`${base}${projectRoot}/timeline-operations/add-media-clip/preview`, {
    headers: authorized, data: { baseRevisionId: currentRevision, baseContentHash: currentHash,
      trackId: 'track-1', clipId: `missing-browser-${Date.now()}`, mediaAssetId: source.artifactId,
      mediaStreamId: source.streams.find(stream => stream.kind === 'VIDEO').streamId,
      artifactId: 'missing-artifact', contentDigest: source.contentDigest, sourceStart: '0', sourceEnd: '1',
      timelineStart: '4', timelineEnd: '5', rateNumerator: 1, rateDenominator: 1, direction: 'FORWARD' },
  })
  negative('NONEXISTENT_SOURCE', missingSource.status(), [404, 422])

  // Real product interaction: drag a clip edge in the timeline, preview through the canonical
  // Operation authority, then apply. Two trims: head (remove the red 0-2 s) and tail (remove 5-6 s).
  await page.getByRole('button', { name: 'Refresh clips' }).click()
  const strip = page.getByRole('group', { name: 'Timeline trim' })
  await strip.waitFor({ timeout: 30000 })
  const pixelsPerSecond = Number(await strip.getAttribute('data-pixels-per-second'))
  if (!pixelsPerSecond) throw new Error('Trim strip scale unavailable')
  const dragEdge = async (edge, pixels) => {
    const handle = page.getByLabel(`Trim ${edge} edge ${clipId}`)
    await handle.waitFor({ timeout: 30000 })
    // The strip scrolls horizontally when a clip is wider than the panel, exactly like a user
    // scrolling to reach the far edge; the drag must start from the visible handle.
    await handle.scrollIntoViewIfNeeded()
    const box = await handle.boundingBox()
    if (!box) throw new Error(`${edge} trim handle has no box`)
    const x = box.x + box.width / 2
    const y = box.y + box.height / 2
    await page.mouse.move(x, y)
    await page.mouse.down()
    await page.mouse.move(x + pixels, y, { steps: 8 })
    await page.mouse.up()
  }
  await dragEdge('start', 2 * pixelsPerSecond)
  await page.getByText(/Preview valid: source 2\/1s/).waitFor({ timeout: 30000 })
  stage('HEAD_TRIM_PREVIEWED')
  await page.screenshot({ path: join(outRoot, 'trim-head-preview.png'), fullPage: true })
  await page.getByRole('button', { name: 'Apply trim' }).click()
  await page.getByText(/Trim applied at revision/).waitFor({ timeout: 30000 })
  const headRevision = await page.locator('.ff-head-identity code').nth(0).textContent()
  const headClips = await snapshotClips(headRevision)
  const headClip = headClips.find(clip => clip.clipId === clipId)
  if (!headClip) throw new Error('Trimmed clip missing from canonical snapshot')
  result.trims.push({ stage: 'HEAD', revision: headRevision, clip: headClip })
  if (headClip.trimStart !== '2/1' || headClip.trimEnd !== '6/1'
      || headClip.startTime !== '2/1' || headClip.endTime !== '6/1') {
    throw new Error(`Head trim snapshot mismatch: ${JSON.stringify(headClip)}`)
  }
  stage('HEAD_TRIM_APPLIED')
  await dragEdge('end', -1 * pixelsPerSecond)
  await page.getByText(/Preview valid: source 2\/1s → 5\/1s/).waitFor({ timeout: 30000 })
  stage('TAIL_TRIM_PREVIEWED')
  await page.getByRole('button', { name: 'Apply trim' }).click()
  await page.getByText(/Trim applied at revision/).waitFor({ timeout: 30000 })
  const revision = await page.locator('.ff-head-identity code').nth(0).textContent()
  const clips = await snapshotClips(revision)
  const clip = clips.find(item => item.clipId === clipId)
  if (!clip) throw new Error('Tail-trimmed clip missing from canonical snapshot')
  result.trims.push({ stage: 'TAIL', revision, clip })
  if (clip.trimStart !== '2/1' || clip.trimEnd !== '5/1'
      || clip.startTime !== '2/1' || clip.endTime !== '5/1') {
    throw new Error(`Tail trim snapshot mismatch: ${JSON.stringify(clip)}`)
  }
  stage('TAIL_TRIM_APPLIED')

  await page.reload({ waitUntil: 'domcontentloaded' })
  await page.getByRole('heading', { name: 'Timeline editor' }).waitFor({ timeout: 30000 })
  await page.getByText(revision, { exact: true }).first().waitFor({ timeout: 30000 })
  await page.getByRole('group', { name: 'Timeline trim' }).waitFor({ timeout: 30000 })
  const restored = await snapshotClips(revision)
  if (JSON.stringify(restored.find(item => item.clipId === clipId)) !== JSON.stringify(clip)) {
    throw new Error('Reloaded canonical snapshot differs from the applied trim')
  }
  stage('REFRESH_AUTHORITATIVE_RESTORE')
  await page.screenshot({ path: join(outRoot, 'timeline-restored.png'), fullPage: true })

  await page.getByLabel('Output start (ms)').fill('2000')
  await page.getByLabel('Output end (ms)').fill('5000')
  await page.getByRole('button', { name: 'Submit RenderJob' }).click()
  await page.getByText(/RenderJob .* accepted for revision/).waitFor({ timeout: 30000 })
  await page.getByLabel('Current RenderJob').waitFor({ timeout: 30000 })
  const jobId = await page.getByLabel('Current RenderJob').locator('code').first().textContent()
  result.jobId = jobId
  stage('FORMAL_RENDERJOB_ACCEPTED')
  await page.getByLabel('Current RenderJob').getByText('COMPLETED', { exact: true }).waitFor({ timeout: 180000 })
  stage('WORKER_COMPLETED')
  const response = await page.request.get(`${base}/api/tenants/tenant-fr-e2e/projects/project-fr-e2e/render-jobs/${jobId}/result`,
    { headers: { Authorization: `Bearer ${token}` } })
  if (response.status() !== 200) throw new Error(`Result read failed: ${response.status()}`)
  const job = await response.json(); result.artifactId = job.finalArtifactId
  result.responses.push({ path: `${projectRoot}/render-jobs/${jobId}/result`, status: response.status(),
    renderJobId: job.renderJobId, jobStatus: job.status, finalArtifactId: job.finalArtifactId })
  if (!result.artifactId) throw new Error('Completed job has no final Artifact')
  await page.getByRole('button', { name: 'Create delivery' }).click()
  await page.getByText(/Delivery /).first().waitFor({ timeout: 30000 })
  const deliveryId = await page.locator('[aria-label="Render and delivery"] code').last().textContent()
  result.deliveryId = deliveryId
  result.responses.push({ path: `/api/projects/project-fr-e2e/deliveries`, result: 'UI_ACCEPTED', deliveryId })
  const downloadPromise = page.waitForEvent('download')
  await page.getByRole('button', { name: 'Download output video' }).click()
  const download = await downloadPromise
  const output = join(outRoot, 'render-output.mp4')
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
  // Content proof: red (0-2 s) was trimmed away, green (2-4 s) opens the output, blue (4-5 s) closes it.
  const samples = { open: 0.2, middle: 1.0, tail: 2.6 }
  const observed = {}
  for (const [label, at] of Object.entries(samples)) {
    const rgb = samplePixel(output, at)
    observed[label] = { at, rgb, colour: dominant(rgb) }
  }
  if (observed.open.colour !== 'green' || observed.middle.colour !== 'green' || observed.tail.colour !== 'blue')
    throw new Error(`Trimmed content does not match the source window: ${JSON.stringify(observed)}`)
  result.media.samples = observed
  const anonymousDownload = await page.request.get(`${base}/api/projects/project-fr-e2e/deliveries/${deliveryId}/result`)
  negative('DELIVERY_DOWNLOAD_AUTHORIZATION_DENIED', anonymousDownload.status(), [401, 403])
  stage('DELIVERY_DOWNLOAD_MEDIA_VERIFIED')
  await page.screenshot({ path: join(outRoot, 'nle-final.png'), fullPage: true })
  result.status = 'PASS'
} catch (error) {
  result.status = 'FAIL'; result.error = String(error)
  result.failurePage = { url: page.url(), title: await page.title().catch(() => ''),
    bodyPreview: await page.locator('body').innerText().then(value => value.slice(0, 800)).catch(() => '') }
  await page.screenshot({ path: join(outRoot, 'nle-failure.png'), fullPage: true }).catch(() => {})
  throw error
} finally {
  writeFileSync(join(outRoot, 'browser-result.json'), JSON.stringify(result, null, 2))
  await browser.close()
}
