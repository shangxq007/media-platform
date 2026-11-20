import assert from 'node:assert/strict'
import { execFileSync } from 'node:child_process'
import { createHash } from 'node:crypto'
import { readFileSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import { chromium } from 'playwright'

const [appRoot, outputRoot] = process.argv.slice(2)
assert(appRoot && outputRoot)
const base = 'http://127.0.0.1:18080'
const tenant = 'tenant-fr-e2e'
const workspace = 'workspace-fr-e2e'
const token = readFileSync(join(appRoot, 'token'), 'utf8').trim()
const headers = { Authorization: `Bearer ${token}` }
const profile = JSON.parse(Buffer.from(token.split('.')[1], 'base64url').toString())
const report = { status: 'RUNNING', stages: [], video: {}, audio: {}, requests: [] }
const record = (stage) => {
  report.stages.push(stage)
  writeFileSync(join(outputRoot, 'multitrack-result.json'), JSON.stringify(report, null, 2))
}

const browser = await chromium.launch({ executablePath: '/usr/bin/google-chrome', headless: true,
  args: ['--no-sandbox', '--disable-dev-shm-usage'] })
const context = await browser.newContext({ acceptDownloads: true, viewport: { width: 1440, height: 1050 } })
const page = await context.newPage()
page.setDefaultTimeout(30000)
report.browser = browser.version()
await context.addInitScript(({ access, profile }) => {
  const user = { access_token: access, token_type: 'Bearer', profile, expires_at: profile.exp,
    scope: 'openid profile email', session_state: profile.sid }
  for (const issuer of ['http://127.0.0.1:18081', 'http://127.0.0.1:18081/']) {
    sessionStorage.setItem(`oidc.user:${issuer}:f8-local`, JSON.stringify(user))
  }
  const create = AudioContext.prototype.createAnalyser
  AudioContext.prototype.createAnalyser = function () {
    const analyser = create.call(this)
    window.nleAnalyser = { analyser, rate: this.sampleRate }
    return analyser
  }
}, { access: token, profile })

async function get(path) {
  const response = await page.request.get(base + path, { headers })
  assert.equal(response.status(), 200, path)
  return response.json()
}

function tonePeaks(samples, rate) {
  return Object.fromEntries([440, 880].map(frequency => {
    let re = 0
    let im = 0
    let weight = 0
    for (let index = 0; index < samples.length; index++) {
      const phase = 2 * Math.PI * frequency * index / rate
      const window = 0.5 - 0.5 * Math.cos(2 * Math.PI * index / (samples.length - 1))
      re += window * samples[index] * Math.cos(phase)
      im += window * samples[index] * Math.sin(phase)
      weight += window
    }
    return [frequency, 2 * Math.hypot(re, im) / weight]
  }))
}

try {
  const created = await page.request.post(`${base}/api/identity/tenants/${tenant}/projects`, {
    headers, data: { name: 'Multitrack NLE R1', workspaceId: workspace },
  })
  assert.equal(created.status(), 200, `project creation ${await created.text()}`)
  const { id: project } = await created.json()
  assert(project)
  const scope = `/api/tenants/${tenant}/projects/${project}`
  const head = () => get(`/api/timeline-git/products/${project}/revisions/current`)
  const snapshot = async revision => JSON.parse((await get(
    `/api/render/projects/${project}/timeline/revisions/${revision}/snapshot`)).canonicalTimelineJson)
  page.on('response', response => {
    const { pathname } = new URL(response.url())
    if (pathname.startsWith(scope)) report.requests.push([response.request().method(), pathname, response.status()])
  })
  const preview = page.getByRole('region', { name: 'Timeline media preview' })
  const waitRevision = async previous => {
    const deadline = Date.now() + 30000
    while (Date.now() < deadline) {
      const current = await head()
      if (current.revisionId !== previous) {
        await page.waitForFunction(revision => document.querySelector('[data-preview-revision]')?.dataset.previewRevision === revision,
          current.revisionId)
        return current
      }
      await page.waitForTimeout(150)
    }
    throw new Error('Timeline HEAD did not advance')
  }
  const edit = page.getByRole('region', { name: 'Edit existing clip' })
  const track = label => edit.getByRole('button', { name: new RegExp(`^${label} .* track$`) })
  const sourceRegion = page.getByRole('region', { name: 'Source media' })
  const field = label => page.getByLabel(label, { exact: true })
  await page.goto(`${base}/w/${workspace}/projects/${project}/edit`)
  await page.getByRole('heading', { name: 'Timeline editor' }).waitFor()
  const initialized = page.waitForResponse(r => r.url().endsWith('/timeline-operations/initialize'))
  await page.getByRole('button', { name: 'Initialize Timeline' }).click()
  const initialization = await initialized
  assert.equal(initialization.status(), 201, await initialization.text())
  let current = await waitRevision(null)
  for (const [kind, label] of [['video', 'V2'], ['audio', 'A1'], ['audio', 'A2']]) {
    await edit.getByRole('button', { name: `Add ${kind} track` }).click()
    current = await waitRevision(current.revisionId)
    await track(label).waitFor()
  }
  record('FOUR_TRACKS_SAVED_THROUGH_OPERATIONS')

  for (const file of ['video-a.mp4', 'video-b.mp4', 'audio-c.wav', 'audio-d.wav']) {
    await sourceRegion.locator('input[type=file]').setInputFiles(join(outputRoot, file))
    const posted = page.waitForResponse(r => r.url().endsWith('/source-media') && r.request().method() === 'POST')
    await sourceRegion.getByRole('button', { name: 'Upload source' }).click()
    const response = await posted
    assert.equal(response.status(), 201, await response.text())
    await sourceRegion.getByRole('button', { name: new RegExp(file) }).waitFor()
  }
  record('FOUR_SOURCES_UPLOADED_IN_WORKBENCH')

  const add = async (file, lane, start, end, sourceStart, sourceEnd) => {
    await track(lane).click()
    await sourceRegion.getByRole('button', { name: new RegExp(file) }).click()
    await field('Timeline start (exact rational)').fill(start)
    await field('Timeline end (exact rational)').fill(end)
    if (sourceStart) await field('Source start (exact rational)').fill(sourceStart)
    if (sourceEnd) await field('Source end (exact rational)').fill(sourceEnd)
    const clipId = await field('Clip ID').inputValue()
    await page.getByRole('button', { name: 'Preview operation' }).click()
    await page.getByLabel(/I confirm this exact frozen draft/i).check()
    await page.getByRole('button', { name: 'Confirm and apply' }).click()
    current = await waitRevision(current.revisionId)
    await edit.getByLabel(`Timeline clip ${clipId}`, { exact: true }).waitFor()
    return clipId
  }
  const videoA = await add('video-a.mp4', 'V1', '0', '4')
  const videoB = await add('video-b.mp4', 'V2', '1', '4')
  await add('audio-c.wav', 'A1', '0', '3')
  await add('audio-d.wav', 'A2', '1', '4')
  const moved = await add('video-a.mp4', 'V1', '4', '5', '2', '3')
  record('FOUR_TRACKS_POPULATED')

  await field('Clip').selectOption(moved)
  await field('Clip operation').selectOption('MOVE_CLIP')
  await field('Absolute position').check()
  await field('Exact time').fill('5')
  await field('Destination track').selectOption({ label: 'V2' })
  const appliedMove = page.waitForResponse(r => r.url().endsWith('/timeline-operations/clips/apply'))
  await edit.getByRole('button', { name: 'Apply operation' }).click()
  const moveResponse = await appliedMove
  assert.equal(moveResponse.status(), 200, await moveResponse.text())
  const moveRequest = moveResponse.request().postDataJSON()
  current = await waitRevision(current.revisionId)
  const replay = await page.request.post(base + scope + '/timeline-operations/clips/apply', { headers, data: moveRequest })
  assert.equal(replay.status(), 200, await replay.text())
  assert.equal((await replay.json()).newRevisionId, current.revisionId)
  const stale = await page.request.post(base + scope + '/timeline-operations/clips/apply', {
    headers, data: { ...moveRequest, delta: '6', commandId: 'nle-r1-stale-attempt' },
  })
  assert.equal(stale.status(), 409, await stale.text())
  assert.equal((await head()).revisionId, current.revisionId)
  record('CROSS_TRACK_MOVE_REPLAY_AND_CAS')

  await field('Clip').selectOption(videoB)
  await field('Clip operation').selectOption('TRIM_CLIP')
  await field('Trim edge').selectOption('END')
  await field('Exact time').fill('3/2')
  await edit.getByRole('button', { name: 'Preview trim' }).click()
  await edit.getByRole('button', { name: 'Apply trim', exact: true }).click()
  current = await waitRevision(current.revisionId)

  await field('Clip').selectOption(videoA)
  await field('Clip operation').selectOption('SPLIT_CLIP')
  await field('Split time').fill('13/4')
  await edit.getByRole('button', { name: 'Split selected clip' }).click()
  current = await waitRevision(current.revisionId)
  const splitDocument = await snapshot(current.revisionId)
  const right = splitDocument.tracks.flatMap(item => item.clips)
    .find(clip => clip.startTime === '13/4' && clip.clipId !== videoA)
  assert(right, 'split clip')
  await field('Clip').selectOption(right.clipId)
  await edit.getByRole('button', { name: 'Delete selected clip' }).click()
  current = await waitRevision(current.revisionId)
  report.revisionId = current.revisionId
  report.projectId = project
  report.snapshot = await snapshot(current.revisionId)
  assert(report.snapshot.tracks.find(t => t.name === 'V2').clips.some(c => c.clipId === moved))
  assert(!report.snapshot.tracks.flatMap(t => t.clips).some(c => c.clipId === right.clipId))
  await page.reload()
  await page.waitForFunction(revision => {
    const region = document.querySelector('[data-preview-revision]')
    return region?.dataset.previewRevision === revision && region?.dataset.previewStatus === 'READY'
  }, current.revisionId, { timeout: 90000 })
  await edit.getByRole('group', { name: 'Timeline trim' }).screenshot({ path: join(outputRoot, 'edited-tracks.png') })
  record('MOVE_TRIM_SPLIT_DELETE_REPLAY_CAS_AND_RELOAD')

  const artifactId = report.snapshot.tracks[0].clips[0].artifactId
  const readPath = scope + `/source-artifacts/${artifactId}/content`
  const anonymous = await page.request.get(base + readPath)
  const foreign = await page.request.get(base + readPath.replace(tenant, 'foreign-tenant'), { headers })
  assert([401, 403].includes(anonymous.status()))
  assert([403, 404].includes(foreign.status()))
  report.sourceReadProtection = { anonymous: anonymous.status(), foreignTenant: foreign.status() }
  const readsBeforePlayback = report.requests.filter(([method, path]) => method === 'GET' && path.endsWith('/content')).length

  async function seek(seconds) {
    await field('Timeline preview seek').evaluate((element, value) => {
      Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set.call(element, String(value))
      element.dispatchEvent(new Event('input', { bubbles: true }))
      element.dispatchEvent(new Event('change', { bubbles: true }))
    }, seconds)
    await page.waitForFunction(value => Number(document.querySelector('[data-preview-time]')?.dataset.previewTime) === value, seconds)
  }
  const dominant = ([r, g, b]) => Math.max(r, g, b) < 30 ? 'black'
    : r > 2 * Math.max(g, b) ? 'red' : g > 2 * Math.max(r, b) ? 'green' : b > 2 * Math.max(r, g) ? 'blue' : 'unknown'
  const canvas = page.getByLabel('Timeline preview canvas')
  for (const [at, expected] of [[0.5, 'red'], [1.5, 'green'], [2.75, 'blue'], [3.20, 'blue'], [3.30, 'black'], [5.50, 'blue']]) {
    await seek(at)
    const pixel = () => canvas.evaluate(element => [...element.getContext('2d').getImageData(160, 120, 1, 1).data].slice(0, 3))
    let rgb = await pixel()
    for (let attempt = 0; dominant(rgb) !== expected && attempt < 40; attempt++) {
      await page.waitForTimeout(50)
      rgb = await pixel()
    }
    assert.equal(dominant(rgb), expected, `Preview frame at ${at}s`)
    report.video[at] = { expected, previewRgb: rgb }
    await canvas.screenshot({ path: join(outputRoot, `preview-${at}.png`) })
  }
  const audioCases = [[0.50, [440]], [1.50, [440, 880]], [3.35, [880]], [4.30, []], [1.75, [440, 880]]]
  function assertPeaks(peaks, present, label) {
    for (const frequency of [440, 880]) {
      assert(present.includes(frequency) ? peaks[frequency] > 0.035 && peaks[frequency] < 0.10 : peaks[frequency] < 0.012,
        `${label} ${frequency}Hz: ${peaks[frequency]}`)
    }
  }
  for (const [at, present] of audioCases) {
    await seek(at)
    await preview.getByRole('button', { name: 'Play Timeline' }).click()
    await page.waitForFunction(value => Number(document.querySelector('[data-preview-time]')?.dataset.previewTime) > value,
      at + 0.16)
    const { samples, rate } = await page.evaluate(() => {
      const { analyser, rate } = window.nleAnalyser
      const samples = new Float32Array(analyser.fftSize)
      analyser.getFloatTimeDomainData(samples)
      return { samples: [...samples], rate }
    })
    const peaks = tonePeaks(samples, rate)
    assertPeaks(peaks, present, `Preview at ${at}s`)
    report.audio[at] = { expected: present, preview: peaks }
    await preview.getByRole('button', { name: 'Pause Timeline' }).click()
  }
  assert.equal(report.requests.filter(([method, path]) => method === 'GET' && path.endsWith('/content')).length, readsBeforePlayback)
  record('BROWSER_PIXELS_AUDIO_SIGNAL_AND_BOUNDED_SOURCE_READS')

  await field('Output start (ms)').fill('0')
  await field('Output end (ms)').fill('6000')
  await field('Frame rate').fill('24')
  await page.getByRole('button', { name: 'Submit RenderJob' }).click()
  const jobCard = page.getByLabel('Current RenderJob')
  await jobCard.locator('code').first().waitFor()
  const jobId = await jobCard.locator('code').first().textContent()
  report.render = { jobId }
  record('FORMAL_RENDER_SUBMITTED')
  const terminal = jobCard.getByText(/^(COMPLETED|FAILED|CANCELLED)$/)
  await terminal.waitFor({ timeout: 240000 })
  assert.equal(await terminal.textContent(), 'COMPLETED', `RenderJob ${jobId}`)
  const job = await get(scope + `/render-jobs/${jobId}/result`)
  assert(job.finalArtifactId)
  report.render.artifactId = job.finalArtifactId
  await page.getByRole('button', { name: 'Create delivery' }).click()
  const downloaded = page.waitForEvent('download')
  await page.getByRole('button', { name: 'Download output video' }).click()
  const output = join(outputRoot, 'multitrack-output.mp4')
  await (await downloaded).saveAs(output)
  const bytes = readFileSync(output)
  report.render.sha256 = createHash('sha256').update(bytes).digest('hex')
  report.render.bytes = bytes.length
  const probe = JSON.parse(execFileSync('/usr/bin/ffprobe', ['-v', 'error', '-count_frames', '-show_entries',
    'format=duration:stream=codec_type,codec_name,nb_read_frames,duration', '-of', 'json', output]).toString())
  report.render.probe = probe
  assert(Math.abs(Number(probe.format.duration) - 6) <= 0.05)
  assert.equal(Number(probe.streams.find(s => s.codec_type === 'video').nb_read_frames), 144)
  assert.equal(probe.streams.find(s => s.codec_type === 'audio').codec_name, 'aac')
  for (const [at, sample] of Object.entries(report.video)) {
    sample.renderRgb = [...execFileSync('/usr/bin/ffmpeg', ['-v', 'error', '-ss', at, '-i', output,
      '-frames:v', '1', '-vf', 'scale=1:1', '-pix_fmt', 'rgb24', '-f', 'rawvideo', '-'])]
    assert.equal(dominant(sample.renderRgb), sample.expected, `Rendered frame at ${at}s`)
  }
  for (const [at, sample] of Object.entries(report.audio)) {
    const raw = execFileSync('/usr/bin/ffmpeg', ['-v', 'error', '-ss', at, '-i', output,
      '-t', '0.085333333', '-ac', '1', '-ar', '48000', '-f', 'f32le', '-'])
    const data = Array.from({ length: raw.length / 4 }, (_, index) => raw.readFloatLE(index * 4))
    sample.render = tonePeaks(data, 48000)
    assertPeaks(sample.render, sample.expected, `Rendered audio at ${at}s`)
    for (const frequency of sample.expected) {
      assert(Math.abs(sample.render[frequency] - sample.preview[frequency]) < 0.025, `Gain parity at ${at}s`)
    }
  }
  report.status = 'PASS'
  record('DELIVERY_VIDEO_AND_AUDIO_PARITY')
} catch (error) {
  report.status = 'FAIL'
  report.error = String(error)
  record('FAILED')
  await page.screenshot({ path: join(outputRoot, 'failure.png'), fullPage: true }).catch(() => undefined)
  throw error
} finally {
  await browser.close()
}
