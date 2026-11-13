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
  deliveryId: null, media: null, negative: [], responses: [] }
const stage = name => { result.stages.push(name); writeFileSync(join(outRoot, 'progress.json'), JSON.stringify(result, null, 2)) }
const projectRoot = '/api/tenants/tenant-fr-e2e/projects/project-fr-e2e'
const authorized = { Authorization: `Bearer ${token}` }
function negative(name, status, accepted) {
  if (!accepted.includes(status)) throw new Error(`${name} returned ${status}; expected ${accepted.join('/')}`)
  result.negative.push({ name, status })
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
  await page.getByLabel('Upload video or audio (64 MiB maximum)').setInputFiles(join(appRoot, 'red.mp4'))
  await page.getByRole('button', { name: 'Upload source' }).click()
  await page.getByText(/Accepted red\.mp4 as Source Artifact/).waitFor({ timeout: 60000 })
  stage('SOURCE_UPLOAD_ACCEPTED')
  await page.screenshot({ path: join(outRoot, 'source-upload.png'), fullPage: true })
  await page.getByLabel('Track ID').fill('track-1')
  const clipId = await page.getByLabel('Clip ID').inputValue()
  await page.getByLabel('Timeline start (exact rational)').fill('2')
  await page.getByLabel('Timeline end (exact rational)').fill('3')
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
  const source = (await sourceResponse.json()).find(row => row.name === 'red.mp4')
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
  await page.getByRole('button', { name: 'Refresh clips' }).click()
  await page.getByLabel('Clip', { exact: true }).selectOption(clipId)
  await page.getByLabel('Clip operation').selectOption('MOVE_CLIP')
  await page.getByLabel('Exact time').fill('5/2')
  await page.getByLabel('Absolute position').check()
  await page.getByRole('button', { name: 'Apply operation' }).click()
  await page.getByText(/Operation accepted at revision/).waitFor({ timeout: 30000 })
  stage('MOVE_OPERATION_REVISION')
  await page.getByLabel('Clip operation').selectOption('TRIM_CLIP')
  await page.getByLabel('Exact time').fill('1/2')
  await page.getByRole('button', { name: 'Apply operation' }).click()
  await page.getByText(/Operation accepted at revision/).waitFor({ timeout: 30000 })
  stage('TRIM_OPERATION_REVISION')
  await page.getByLabel('Clip operation').selectOption('DELETE_CLIP')
  await page.getByLabel('Clip', { exact: true }).selectOption('clip-blue')
  await page.getByRole('button', { name: 'Apply operation' }).click()
  await page.getByText(/Operation accepted at revision/).waitFor({ timeout: 30000 })
  stage('DELETE_OPERATION_REVISION')
  const revision = await page.locator('.ff-head-identity code').first().textContent()
  await page.reload({ waitUntil: 'domcontentloaded' })
  await page.getByRole('heading', { name: 'Timeline editor' }).waitFor({ timeout: 30000 })
  await page.getByText(revision, { exact: true }).first().waitFor({ timeout: 30000 })
  await page.getByText(/red\.mp4/).first().waitFor({ timeout: 30000 })
  stage('REFRESH_AUTHORITATIVE_RESTORE')
  await page.screenshot({ path: join(outRoot, 'timeline-restored.png'), fullPage: true })
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
  const tailPixel = execFileSync('/usr/bin/ffmpeg', ['-v','error','-ss','2.75','-i',output,'-frames:v','1','-vf','scale=1:1',
    '-f','rawvideo','-pix_fmt','rgb24','pipe:1'])
  if (tailPixel.length !== 3 || tailPixel[0] <= tailPixel[1] || tailPixel[0] <= tailPixel[2])
    throw new Error(`Edited tail is not the selected red source: ${[...tailPixel]}`)
  result.media.tailPixelRgb = [...tailPixel]
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
