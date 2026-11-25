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
const FREQUENCIES = [440, 660, 880, 1320]
const tonePeaks = (samples, rate) => Object.fromEntries(FREQUENCIES.map(frequency => {
  let re = 0, im = 0, weight = 0
  for (let index = 0; index < samples.length; index++) {
    const phase = 2 * Math.PI * frequency * index / rate
    const window = 0.5 - 0.5 * Math.cos(2 * Math.PI * index / (samples.length - 1))
    re += window * samples[index] * Math.cos(phase)
    im += window * samples[index] * Math.sin(phase)
    weight += window
  }
  return [frequency, 2 * Math.hypot(re, im) / weight]
}))

const browser = await chromium.launch({ executablePath: '/usr/bin/google-chrome', headless: true,
  args: ['--no-sandbox', '--disable-dev-shm-usage'] })
const context = await browser.newContext({ acceptDownloads: true, viewport: { width: 1600, height: 1100 } })
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

try {
  const created = await page.request.post(`${base}/api/identity/tenants/${tenant}/projects`, {
    headers, data: { name: 'Audio editing and mixing R1', workspaceId: workspace },
  })
  assert.equal(created.status(), 200, await created.text())
  const { id: project } = await created.json()
  const scope = `/api/tenants/${tenant}/projects/${project}`
  const head = async () => {
    const raw = await (await page.request.get(base + `/api/timeline-git/products/${project}/revisions/current`, { headers })).json()
    const contentHash = raw.contentHash ?? raw.revision?.semanticContext?.timelineContentDigest
    assert(raw.revisionId && contentHash, 'canonical HEAD content hash is present')
    return { revisionId: raw.revisionId, contentHash }
  }
  const snapshot = async revision => JSON.parse((await (await page.request.get(
    base + `/api/render/projects/${project}/timeline/revisions/${revision}/snapshot`, { headers })).json()).canonicalTimelineJson)
  page.on('response', response => {
    const { pathname } = new URL(response.url())
    if (pathname.startsWith(scope)) report.requests.push([response.request().method(), pathname, response.status()])
  })
  let current
  const init = await page.request.post(base + scope + '/timeline-operations/initialize',
    { headers, data: { trackId: 'track-1', commandId: 'audio-init' } })
  assert.equal(init.status(), 201, await init.text())
  const initBody = await init.json()
  current = { revisionId: initBody.newRevisionId, contentHash: initBody.resultContentHash }
  const addTrack = async (trackType, trackId, commandId) => {
    const response = await page.request.post(base + scope + '/timeline-operations/tracks',
      { headers, data: { baseRevisionId: current.revisionId, baseContentHash: current.contentHash, trackType, trackId, commandId } })
    assert([200, 201].includes(response.status()), await response.text())
    const body = await response.json()
    current = { revisionId: body.newRevisionId ?? current.revisionId, contentHash: body.resultContentHash ?? body.unchangedContentHash ?? current.contentHash }
  }
  for (const [type, id] of [['VIDEO', 'track-v2'], ['VIDEO', 'track-v3'], ['VIDEO', 'track-v4'],
    ['AUDIO', 'track-a1'], ['AUDIO', 'track-a2'], ['AUDIO', 'track-a3'], ['AUDIO', 'track-a4']]) {
    await addTrack(type, id, `track-${id}`)
  }
  record('EIGHT_TRACKS_SAVED_THROUGH_OPERATIONS')

  const upload = async file => {
    const response = await page.request.post(base + scope + '/source-media', {
      headers: { ...headers, 'Idempotency-Key': `audio-source-${file}` }, multipart: { file: { name: file, mimeType: file.endsWith('.wav') ? 'audio/wav' : 'video/mp4',
        buffer: readFileSync(join(outputRoot, file)) } },
    })
    assert.equal(response.status(), 201, await response.text())
    return response.json()
  }
  const sources = {}
  for (const file of ['video-a.mp4', 'video-b.mp4', 'video-c.mp4', 'video-d.mp4',
    'audio-a.wav', 'audio-b.wav', 'audio-c.wav', 'audio-d.wav', 'video-embed.mp4', 'composition-pattern.mp4', 'composition-source-rotated.mp4']) sources[file] = await upload(file)
  record('SOURCES_UPLOADED_IN_WORKBENCH')

  const addClip = async (file, trackId, start, end, sourceEnd = null) => {
    const source = sources[file]
    const stream = source.streams.find(item => item.kind === (file.endsWith('.wav') ? 'AUDIO' : 'VIDEO'))
    const clipId = `clip-${file.replace('.', '-')}-${Math.random().toString(16).slice(2, 8)}`
    const command = { baseRevisionId: current.revisionId, baseContentHash: current.contentHash,
      trackId, clipId, mediaAssetId: source.mediaAssetId, mediaStreamId: stream.streamId,
      artifactId: source.artifactId, contentDigest: source.contentDigest,
      sourceStart: '0', sourceEnd: sourceEnd ?? stream.duration, timelineStart: start, timelineEnd: end,
      rateNumerator: 1, rateDenominator: 1, direction: 'FORWARD' }
    const previewResponse = await page.request.post(base + scope + '/timeline-operations/add-media-clip/preview', { headers, data: command })
    assert.equal(previewResponse.status(), 200, await previewResponse.text())
    const preview = await previewResponse.json()
    const response = await page.request.post(base + scope + '/timeline-operations/add-media-clip/apply', {
      headers, data: { request: command, expectedPlanDigest: preview.planDigest, applyCommandId: `apply-${clipId}` },
    })
    assert.equal(response.status(), 201, await response.text())
    const body = await response.json()
    current = { revisionId: body.newRevisionId, contentHash: body.newTimelineContentHash }
    return clipId
  }
  const videoA = await addClip('video-a.mp4', 'track-1', '0', '3')
  const videoB = await addClip('video-b.mp4', 'track-v2', '1', '4')
  const videoC = await addClip('video-c.mp4', 'track-v3', '2', '5')
  const videoD = await addClip('video-d.mp4', 'track-v4', '1', '4', '3')
  const audioA = await addClip('audio-a.wav', 'track-a1', '0', '5')
  const audioB = await addClip('audio-b.wav', 'track-a2', '1', '6')
  const audioC = await addClip('audio-c.wav', 'track-a3', '2', '7')
  const audioD = await addClip('audio-d.wav', 'track-a4', '3', '5', '2')
  record('FOUR_VIDEO_AND_FOUR_AUDIO_CLIPS_POPULATED')

  await page.goto(`${base}/w/${workspace}/projects/${project}/edit`)
  await page.getByRole('heading', { name: 'Timeline editor' }).waitFor()
  const edit = page.getByRole('region', { name: 'Edit existing clip' })
  const sourceRegion = page.getByRole('region', { name: 'Source media' })
  const preview = page.getByRole('region', { name: 'Timeline media preview' })
  const field = label => page.getByLabel(label, { exact: true })
  const waitRevision = async previous => {
    const deadline = Date.now() + 30000
    while (Date.now() < deadline) {
      const value = await head()
      if (value.revisionId !== previous) {
        await page.waitForFunction(revision => document.querySelector('[data-preview-revision]')?.dataset.previewRevision === revision,
          value.revisionId)
        return value
      }
      await page.waitForTimeout(150)
    }
    throw new Error('Timeline HEAD did not advance')
  }
  await page.waitForFunction(() => document.querySelector('[data-preview-status]')?.dataset.previewStatus === 'READY')
  await page.waitForTimeout(500)

  const applyAudio = async (clipId, gain, mute, pan) => {
    await field('Clip').selectOption(clipId)
    await field('Audio gain').fill(String(gain))
    const box = field('Mute clip')
    if ((await box.isChecked()) !== mute) await box.setChecked(mute)
    await page.waitForTimeout(40)
    await field('Audio pan').evaluate((element, value) => {
      Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set.call(element, String(value))
      element.dispatchEvent(new Event('input', { bubbles: true }))
      element.dispatchEvent(new Event('change', { bubbles: true }))
    }, pan)
    await page.waitForTimeout(40)
    await edit.getByRole('button', { name: 'Apply audio mix' }).click()
    current = await waitRevision(current.revisionId)
    await page.waitForFunction(() => {
      const status = document.querySelector('[data-preview-status]')?.dataset.previewStatus
      const max = Number(document.querySelector('input[aria-label="Timeline preview seek"]')?.getAttribute('max'))
      return ['READY', 'PAUSED', 'PLAYING'].includes(status) && max > 1
    })
    const route = (await snapshot(current.revisionId)).audioMix.routes.find(item => item.input.clipId === clipId)
    assert(route, `route ${clipId}`)
    assert.equal(route.gain.linear, gain)
    assert.equal(route.mute.muted, mute)
    assert.equal(route.balance.value, pan)
  }
  const transformFields = ['positionXMicros', 'positionYMicros', 'scaleXMicros', 'scaleYMicros',
    'rotationMicroDegrees', 'cropLeftMicros', 'cropRightMicros', 'cropTopMicros', 'cropBottomMicros', 'opacityMicros']
  const applyTransform = async (clipId, t) => {
    await field('Clip').selectOption(clipId)
    const values = {
      'Video position X': t.positionX, 'Video position Y': t.positionY,
      'Video scale X': t.scaleX, 'Video scale Y': t.scaleY,
      'Video rotation': t.rotationDegrees, 'Video opacity': t.opacity,
      'Video crop left': t.cropLeft, 'Video crop right': t.cropRight,
      'Video crop top': t.cropTop, 'Video crop bottom': t.cropBottom,
    }
    for (const [label, value] of Object.entries(values)) await field(label).fill(String(value))
    await page.waitForTimeout(60)
    await edit.getByRole('button', { name: 'Apply video transform' }).click()
    current = await waitRevision(current.revisionId)
    await page.waitForFunction(() => {
      const status = document.querySelector('[data-preview-status]')?.dataset.previewStatus
      const max = Number(document.querySelector('input[aria-label="Timeline preview seek"]')?.getAttribute('max'))
      return ['READY', 'PAUSED', 'PLAYING'].includes(status) && max > 1
    })
    const transformed = (await snapshot(current.revisionId)).tracks
      .flatMap(track => track.clips).find(clip => clip.clipId === clipId).transform
    for (const [key, value] of Object.entries(values)) {
      const property = { 'Video position X': 'positionXMicros', 'Video position Y': 'positionYMicros',
        'Video scale X': 'scaleXMicros', 'Video scale Y': 'scaleYMicros', 'Video rotation': 'rotationMicroDegrees',
        'Video opacity': 'opacityMicros', 'Video crop left': 'cropLeftMicros', 'Video crop right': 'cropRightMicros',
        'Video crop top': 'cropTopMicros', 'Video crop bottom': 'cropBottomMicros' }[key]
      assert.equal(transformed.format, 'composition-transform-micro-v1')
      assert.equal(transformed[property], Math.round(value * 1_000_000), clipId + ' ' + property)
    }
  }
  await applyTransform(videoB, { positionX: 0.6875, positionY: 0.666667, scaleX: 0.3, scaleY: 0.3,
    rotationDegrees: 0, cropLeft: 0, cropRight: 1, cropTop: 0, cropBottom: 1, opacity: 1 })
  await applyTransform(videoD, { positionX: 0.0625, positionY: 0.083333, scaleX: 0.5, scaleY: 0.5,
    rotationDegrees: 45, cropLeft: 0.2, cropRight: 0.8, cropTop: 0.2, cropBottom: 0.8, opacity: 0.5 })
  // Reset is a local editor action; applying identity afterwards is not necessary here.
  await field('Clip').selectOption(videoA)
  await edit.getByRole('button', { name: 'Reset transform' }).click()
  assert.equal(await field('Video position X').inputValue(), '0')
  assert.equal(await field('Video scale X').inputValue(), '1')
  assert.equal(await field('Video opacity').inputValue(), '1')
  record('VIDEO_TRANSFORMS_PERSISTED_THROUGH_BROWSER_CONTROLS')
  const applyEffect = async (clipId, e) => {
    await field('Clip').selectOption(clipId)
    await field('Video brightness').fill(String(e.brightness))
    await field('Video contrast').fill(String(e.contrast))
    await field('Video saturation').fill(String(e.saturation))
    await field('Video fade in').fill(String(e.fadeInSeconds))
    await field('Video fade out').fill(String(e.fadeOutSeconds))
    const gray = field('Video grayscale')
    if ((await gray.isChecked()) !== e.grayscale) await gray.setChecked(e.grayscale)
    const enabled = field('Video effect enabled')
    if ((await enabled.isChecked()) !== e.enabled) await enabled.setChecked(e.enabled)
    await edit.getByRole('button', { name: 'Apply video effect' }).click()
    current = await waitRevision(current.revisionId)
    await page.waitForFunction(() => {
      const status = document.querySelector('[data-preview-status]')?.dataset.previewStatus
      const max = Number(document.querySelector('input[aria-label="Timeline preview seek"]')?.getAttribute('max'))
      return ['READY', 'PAUSED', 'PLAYING'].includes(status) && max > 1
    })
    const effect = (await snapshot(current.revisionId)).tracks.flatMap(track => track.clips)
      .find(clip => clip.clipId === clipId).effects.find(item => item.effectKey === 'video.basic').parameters
    assert.equal(effect.brightness, e.brightness)
    assert.equal(effect.contrast, e.contrast)
    assert.equal(effect.saturation, e.saturation)
    assert.equal(effect.grayscale, e.grayscale)
    assert.equal(effect.fadeInSeconds, e.fadeInSeconds)
    assert.equal(effect.fadeOutSeconds, e.fadeOutSeconds)
    assert.equal(effect.enabled, e.enabled)
  }
  const applyTransition = async (outgoing, incoming, id) => {
    await field('Clip').selectOption(outgoing)
    await field('Transition id').fill(id)
    await field('Transition incoming clip').selectOption(incoming)
    await field('Transition duration').fill('1/1')
    await edit.getByRole('button', { name: 'Create cross dissolve' }).click()
    current = await waitRevision(current.revisionId)
    await page.waitForFunction(() => {
      const status = document.querySelector('[data-preview-status]')?.dataset.previewStatus
      const max = Number(document.querySelector('input[aria-label="Timeline preview seek"]')?.getAttribute('max'))
      return ['READY', 'PAUSED', 'PLAYING'].includes(status) && max > 1
    })
    const transition = (await snapshot(current.revisionId)).transitions.find(item => item.transitionId === id)
    assert(transition, 'canonical transition')
    assert.equal(transition.outgoingClipId, outgoing)
    assert.equal(transition.incomingClipId, incoming)
    assert.equal(transition.duration, '1/1')
  }
  await applyEffect(videoD, { brightness: 0.3, contrast: 1.2, saturation: 0.5,
    grayscale: false, fadeInSeconds: 0.5, fadeOutSeconds: 0.5, enabled: true })
  await applyTransition(videoB, videoC, 'transition-v2-v3')
  record('EFFECT_AND_CROSS_DISSOLVE_PERSISTED_THROUGH_BROWSER_CONTROLS')

  const rejectBatch = async (batchId, operations, diagnostic) => {
    const before = await head()
    const rejected = await page.request.post(base + scope + '/timeline-operations/batch/apply',
      { headers, data: { batchId, baseRevisionId: before.revisionId, baseContentHash: before.contentHash, operations } })
    const response = await rejected.text()
    assert.equal(rejected.ok(), false, response)
    assert(response.includes(diagnostic), response)
    assert.deepEqual(await head(), before, 'Invalid batch must preserve the canonical head')
    report.rejections ??= []
    report.rejections.push({ batchId, diagnostic, status: rejected.status(), revisionId: before.revisionId })
  }
  await rejectBatch('reject-competing-transition', [{ definitionId: 'timeline.transition.cross-dissolve.create',
    target: { clipId: videoA }, parameters: { transitionId: 'competing', incomingClipId: videoC, duration: '1/1' } }],
  'TIMELINE_TRANSITION_CONFLICT')
  await rejectBatch('reject-invalid-rate', [{ definitionId: 'timeline.temporal.set-rate',
    target: { clipId: videoB }, parameters: { numerator: 4, denominator: 1 } }],
  'TIMELINE_TRANSITION_INTERVAL_INVALID')
  await rejectBatch('reject-video-effect-on-audio', [
    { definitionId: 'audio.gain.set', target: { clipId: audioA, audioTrackId: 'track-a1' }, parameters: { linear: 0.4 } },
    { definitionId: 'timeline.clip.effect.set', target: { clipId: audioA },
      parameters: { brightness: 0.2, contrast: 1, saturation: 1, grayscale: false,
        fadeInSeconds: 0, fadeOutSeconds: 0, enabled: true } },
  ], 'TIMELINE_EFFECT_TARGET_UNSUPPORTED')
  record('CANONICAL_BATCH_REJECTION_PRESERVES_HEAD')

  // The first control also proves the browser UI writes through the canonical
  // gain/mute/pan batch; the remaining routes are applied through the same UI.
  await applyAudio(audioA, 1, false, -0.5)
  await applyAudio(audioB, 0.75, false, 0)
  await applyAudio(audioC, 1, false, 0.5)
  await applyAudio(audioD, 0.75, false, 0)
  record('GAIN_MUTE_PAN_PERSISTED_THROUGH_BROWSER_CONTROLS')

  // Cross-video move through the browser clip operation.
  await field('Clip').selectOption(videoD)
  await field('Clip operation').selectOption('MOVE_CLIP')
  await field('Absolute position').check()
  await field('Exact time').fill('4')
  await field('Destination track').selectOption({ label: 'V2' })
  await edit.getByRole('button', { name: 'Apply operation' }).click()
  current = await waitRevision(current.revisionId)
  // Move the transformed overlay back to V4 for the final four-layer render.
  await field('Clip').selectOption(videoD)
  await field('Clip operation').selectOption('MOVE_CLIP')
  await field('Absolute position').check()
  await field('Exact time').fill('1')
  await field('Destination track').selectOption({ label: 'V4' })
  await edit.getByRole('button', { name: 'Apply operation' }).click()
  current = await waitRevision(current.revisionId)
  // Cross-audio move away and back, proving route retargeting.
  for (const [time, destination] of [['5', 'A1'], ['3', 'A4']]) {
    await field('Clip').selectOption(audioD)
    await field('Clip operation').selectOption('MOVE_CLIP')
    await field('Absolute position').check()
    await field('Exact time').fill(time)
    await field('Destination track').selectOption({ label: destination })
    await edit.getByRole('button', { name: 'Apply operation' }).click()
    current = await waitRevision(current.revisionId)
  }
  record('CROSS_VIDEO_AND_AUDIO_TRACK_MOVES')

  const seek = async seconds => {
    const slider = field('Timeline preview seek')
    await page.waitForFunction(() => {
      const status = document.querySelector('[data-preview-status]')?.dataset.previewStatus
      const max = Number(document.querySelector('input[aria-label="Timeline preview seek"]')?.getAttribute('max'))
      return ['READY', 'PAUSED', 'PLAYING'].includes(status) && max > 1
    })
    const info = await slider.evaluate(element => ({ tag: element.tagName, type: element.type,
      label: element.getAttribute('aria-label'), value: element.value, max: element.max,
      disabled: element.disabled, readOnly: element.readOnly }))
    console.log('SEEK_INFO ' + JSON.stringify(info) + ' target=' + seconds)
    assert.equal(info.type, 'range', 'seek control is a range input')
    assert.equal(info.disabled, false, 'seek control is enabled')
    await slider.fill(String(seconds)).catch(() => undefined)
    await page.waitForTimeout(80)
    let observed = await slider.inputValue()
    if (Math.abs(Number(observed) - seconds) > 0.04) {
      await slider.evaluate((element, value) => {
        Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set.call(element, String(value))
        element.dispatchEvent(new Event('input', { bubbles: true }))
        element.dispatchEvent(new Event('change', { bubbles: true }))
      }, seconds)
      await page.waitForTimeout(80)
      observed = await slider.inputValue()
    }
    const published = await page.locator('[data-preview-time]').getAttribute('data-preview-time')
    console.log('SEEK_OBSERVED ' + observed + ' published=' + published + ' target=' + seconds)
    await page.waitForFunction(value => Math.abs(Number(document.querySelector('[data-preview-time]')?.dataset.previewTime) - value) < 0.04, seconds)
  }
  const dominant = ([r, g, b]) => Math.max(r, g, b) < 30 ? 'black'
    : r > 2 * Math.max(g, b) ? 'red' : g > 2 * Math.max(r, b) ? 'green' : b > 2 * Math.max(r, g) ? 'blue'
      : r > 100 && g > 100 && b < 100 ? 'yellow'
        : r > 200 && g < 80 && b > 200 ? 'magenta'
          : r > 60 && b > 60 && g < 50 && Math.abs(r - b) < 80 ? 'purple'
            : r < 40 && g > 50 && b > 80 ? 'teal' : 'unknown'
  const canvas = page.getByLabel('Timeline preview canvas')
  const videoSamples = [
    { at: 0.5, x: 160, y: 120, expected: 'red' },
    { at: 1.5, x: 270, y: 200, expected: 'green' },
    { at: 2.1, x: 250, y: 120, expected: 'red' },
    { at: 2.5, x: 250, y: 120, expected: 'purple' },
    { at: 2.9, x: 250, y: 120, expected: 'blue' },
    { at: 6.5, x: 160, y: 120, expected: 'black' },
  ]
  report.videoSamples = []
  for (const sample of videoSamples) {
    await seek(sample.at)
    await page.waitForTimeout(150)
    const rgb = await canvas.evaluate((element, point) =>
      [...element.getContext('2d').getImageData(point.x, point.y, 1, 1).data].slice(0, 3), sample)
    assert.equal(dominant(rgb), sample.expected,
      `Preview ${sample.expected} at ${sample.at}s (${sample.x},${sample.y})`)
    report.videoSamples.push({ ...sample, previewRgb: rgb })
  }
  const effectSamples = [
    { at: 1.35, x: 100, y: 100, phase: 'fade-in' },
    { at: 1.8, x: 100, y: 100, phase: 'effect-steady' },
    { at: 3.8, x: 100, y: 100, phase: 'fade-out' },
  ]
  report.effectSamples = []
  for (const sample of effectSamples) {
    await seek(sample.at)
    await page.waitForTimeout(800)
    const rgb = await canvas.evaluate((element, point) =>
      [...element.getContext('2d').getImageData(point.x, point.y, 1, 1).data].slice(0, 3), sample)
    console.log('EFFECT_PREVIEW ' + sample.phase + ' ' + sample.at + ' ' + rgb.join(','))
    report.effectSamples.push({ ...sample, previewRgb: rgb })
  }
  const sampleAudio = async at => {
    await seek(at)
    await preview.getByRole('button', { name: 'Play Timeline' }).click()
    await page.waitForFunction(value => Number(document.querySelector('[data-preview-time]')?.dataset.previewTime) > value, at + 0.16)
    const { samples, rate } = await page.evaluate(() => {
      const { analyser, rate } = window.nleAnalyser
      const data = new Float32Array(analyser.fftSize)
      analyser.getFloatTimeDomainData(data)
      return { samples: [...data], rate }
    })
    await preview.getByRole('button', { name: 'Pause Timeline' }).click()
    return tonePeaks(samples, rate)
  }
  const mixed = await sampleAudio(3.5)
  report.audio[3.5] = { expected: FREQUENCIES, preview: mixed }
  for (const frequency of FREQUENCIES) assert(mixed[frequency] > 0.015, `preview ${frequency}Hz=${mixed[frequency]}`)
  await seek(1.5)
  await preview.getByRole('button', { name: 'Play Timeline' }).click()
  await page.waitForTimeout(180)
  await preview.getByRole('button', { name: 'Pause Timeline' }).click()
  await preview.getByRole('button', { name: 'Play Timeline' }).click()
  await page.waitForFunction(() => Number(document.querySelector('[data-preview-time]')?.dataset.previewTime) > 1.8)
  await preview.getByRole('button', { name: 'Pause Timeline' }).click()
  record('FOUR_VIDEO_AND_FOUR_AUDIO_PREVIEW_SAMPLES')

  await applyAudio(audioB, 0.75, true, 0)
  const muted = await sampleAudio(3.5)
  report.audio.muted = { expected: [440, 880, 1320], preview: muted }
  assert(muted[660] < 0.01, `muted 660Hz=${muted[660]}`)
  assert(muted[440] > 0.015 && muted[880] > 0.015 && muted[1320] > 0.01, 'other audio tracks survive mute')
  await applyAudio(audioB, 0.75, false, 0)
  record('MUTE_AND_UNMUTE_THROUGH_BROWSER_CONTROLS')

  await page.reload()
  await page.waitForFunction(revision => {
    const region = document.querySelector('[data-preview-revision]')
    return region?.dataset.previewRevision === revision && region?.dataset.previewStatus === 'READY'
  }, current.revisionId, { timeout: 90000 })
  const reloaded = await snapshot(current.revisionId)
  for (const [clip, gain, mute, pan] of [[audioA, 1, false, -0.5], [audioB, 0.75, false, 0],
    [audioC, 1, false, 0.5], [audioD, 0.75, false, 0]]) {
    const route = reloaded.audioMix.routes.find(item => item.input.clipId === clip)
    assert.equal(route.gain.linear, gain)
    assert.equal(route.mute.muted, mute)
    assert.equal(route.balance.value, pan)
  }
  const reloadedClips = reloaded.tracks.flatMap(track => track.clips)
  for (const [clipId, expected] of [[videoB, [0.6875, 0.666667, 0.3, 0.3, 0, 0, 1, 0, 1, 1]],
    [videoC, [0, 0, 1, 1, 0, 0, 1, 0, 1, 1]],
    [videoD, [0.0625, 0.083333, 0.5, 0.5, 45, 0.2, 0.8, 0.2, 0.8, 0.5]]]) {
    const clip = reloadedClips.find(item => item.clipId === clipId)
    const t = clip.transform
    const actual = t === undefined ? [0, 0, 1, 1, 0, 0, 1, 0, 1, 1]
      : transformFields.map(property => t[property] / 1_000_000)
    assert.deepEqual(actual, expected, clipId + ' reloaded transform')
  }
  report.revisionId = current.revisionId
  report.projectId = project
  report.snapshot = reloaded
  record('REVISION_RELOAD_RESTORES_AUDIO_MIX_AND_VIDEO_TRANSFORMS')

  const artifactId = reloaded.tracks[0].clips[0].artifactId
  const readPath = scope + `/source-artifacts/${artifactId}/content`
  const anonymous = await page.request.get(base + readPath)
  const foreign = await page.request.get(base + readPath.replace(tenant, 'foreign-tenant'), { headers })
  assert([401, 403].includes(anonymous.status()))
  assert([403, 404].includes(foreign.status()))
  report.sourceReadProtection = { anonymous: anonymous.status(), foreignTenant: foreign.status() }
  record('SOURCE_ARTIFACT_SECURITY')

  await field('Output start (ms)').fill('0')
  await field('Output end (ms)').fill('7000')
  await field('Frame rate').fill('24')
  await page.getByRole('button', { name: 'Submit RenderJob' }).click()
  const jobCard = page.getByLabel('Current RenderJob')
  await jobCard.locator('code').first().waitFor()
  const jobId = await jobCard.locator('code').first().textContent()
  report.render = { jobId }
  record('FORMAL_RENDER_SUBMITTED')
  const terminal = jobCard.getByText(/^(COMPLETED|FAILED|CANCELLED)$/)
  await terminal.waitFor({ timeout: 300000 })
  assert.equal(await terminal.textContent(), 'COMPLETED', `RenderJob ${jobId}`)
  const job = await (await page.request.get(base + scope + `/render-jobs/${jobId}/result`, { headers })).json()
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
  assert(Math.abs(Number(probe.format.duration) - 7) <= 0.1)
  assert.equal(Number(probe.streams.find(s => s.codec_type === 'video').nb_read_frames), 168)
  assert.equal(probe.streams.find(s => s.codec_type === 'audio').codec_name, 'aac')
  for (const sample of report.videoSamples) {
    sample.renderRgb = [...execFileSync('/usr/bin/ffmpeg', ['-v', 'error', '-ss', String(sample.at), '-i', output,
      '-frames:v', '1', '-vf', `crop=2:2:${sample.x}:${sample.y},scale=1:1:flags=neighbor`, '-pix_fmt', 'rgb24', '-f', 'rawvideo', '-'])]
    assert.equal(dominant(sample.renderRgb), sample.expected,
      `Rendered ${sample.expected} at ${sample.at}s (${sample.x},${sample.y})`)
  }
  for (const sample of report.effectSamples) {
    sample.renderRgb = [...execFileSync('/usr/bin/ffmpeg', ['-v', 'error', '-ss', String(sample.at), '-i', output,
      '-frames:v', '1', '-vf', `crop=2:2:${sample.x}:${sample.y},scale=1:1:flags=neighbor`, '-pix_fmt', 'rgb24', '-f', 'rawvideo', '-'])]
    sample.maxChannelDelta = Math.max(...sample.previewRgb.map((value, index) => Math.abs(value - sample.renderRgb[index])))
    assert(sample.maxChannelDelta <= 24, `Effect parity ${sample.phase}: ${sample.maxChannelDelta}`)
    console.log('EFFECT_PARITY ' + sample.phase + ' ' + sample.at + ' preview=' + sample.previewRgb.join(',')
      + ' render=' + sample.renderRgb.join(',') + ' delta=' + sample.maxChannelDelta)
  }
  const raw = execFileSync('/usr/bin/ffmpeg', ['-v', 'error', '-ss', '3.5', '-i', output,
    '-t', '0.085333333', '-ac', '2', '-ar', '48000', '-f', 'f32le', '-'])
  const frames = raw.length / 4
  const left = Array.from({ length: frames / 2 }, (_, index) => raw.readFloatLE(index * 8))
  const right = Array.from({ length: frames / 2 }, (_, index) => raw.readFloatLE(index * 8 + 4))
  const leftPeaks = tonePeaks(left, 48000)
  const rightPeaks = tonePeaks(right, 48000)
  report.audio.render = { left: leftPeaks, right: rightPeaks }
  for (const frequency of FREQUENCIES) assert(Math.max(leftPeaks[frequency], rightPeaks[frequency]) > 0.008, `rendered ${frequency}Hz`)
  assert(leftPeaks[440] > rightPeaks[440] * 1.5, 'A1 pan left')
  assert(rightPeaks[880] > leftPeaks[880] * 1.5, 'A3 pan right')
  record('DELIVERY_VIDEO_AND_AUDIO_PARITY')

  const formalOutput = async (revisionId, startMillis, endMillis, label, width = 320, height = 240, submitOrigin = base, rate = 24) => {
    const submitted = await page.request.post(submitOrigin + '/api/render-jobs', {
      headers: { ...headers, 'Idempotency-Key': label },
      data: { revisionId, extent: { startMillis, endMillis, frameRateNumerator: rate, frameRateDenominator: 1, width, height },
        profile: 'default', outputFormat: 'MP4' },
    })
    assert.equal(submitted.status(), 201, await submitted.text())
    const { renderJobId } = await submitted.json()
    const deadline = Date.now() + 390000
    let finalArtifactId
    while (Date.now() < deadline && !finalArtifactId) {
      const result = await page.request.get(base + scope + `/render-jobs/${renderJobId}/result`, { headers })
      if (result.ok()) {
        const job = await result.json()
        assert(!['FAILED', 'CANCELLED'].includes(job.status), `${label}: ${JSON.stringify(job)}`)
        finalArtifactId = job.finalArtifactId
      }
      if (!finalArtifactId) await page.waitForTimeout(500)
    }
    assert(finalArtifactId, `${label} formal artifact`)
    const delivery = await page.request.post(base + `/api/projects/${project}/deliveries`, {
      headers: { ...headers, 'Idempotency-Key': `delivery-${label}` }, data: { sourceRenderJobId: renderJobId },
    })
    assert([200, 201].includes(delivery.status()), await delivery.text())
    const { deliveryId } = await delivery.json()
    const download = await page.request.get(base + `/api/projects/${project}/deliveries/${deliveryId}/result`, { headers })
    assert.equal(download.status(), 200)
    const bytes = await download.body()
    const path = join(outputRoot, `${label}.mp4`)
    writeFileSync(path, bytes)
    return { revisionId, renderJobId, finalArtifactId, deliveryId,
      sha256: createHash('sha256').update(bytes).digest('hex'), path }
  }
  const pixel = (path, at, x, y) => [...execFileSync('/usr/bin/ffmpeg', ['-v', 'error',
    '-ss', String(at), '-i', path, '-frames:v', '1', '-vf', `crop=2:2:${x}:${y},scale=1:1:flags=neighbor`,
    '-pix_fmt', 'rgb24', '-f', 'rawvideo', '-'])]
  const maxDelta = (a, b) => {
    assert.equal(a.length, 3)
    assert.equal(b.length, 3)
    return Math.max(...a.map((channel, i) => Math.abs(channel - b[i])))
  }
  report.partial = []
  for (const [start, end, label, times] of [
    [2250, 3500, 'partial-start-in-dissolve', [[2.5, 250, 120], [2.75, 250, 120]]],
    [1500, 2750, 'partial-end-in-dissolve', [[2.25, 250, 120], [2.5, 250, 120]]],
    [1250, 4250, 'partial-spanning-dissolve',
      [[1.375, 100, 100], [1.75, 250, 120], [2.25, 250, 120], [2.75, 250, 120], [3.25, 250, 120]]],
    [3500, 4250, 'partial-through-fade-out', [[3.75, 100, 100]]],
  ]) {
    const partial = await formalOutput(current.revisionId, start, end, label)
    const samples = times.map(([at, x, y]) => {
      const fullRgb = pixel(output, at, x, y)
      const partialRgb = pixel(partial.path, at - start / 1000, x, y)
      return { at, x, y, fullRgb, partialRgb, delta: maxDelta(fullRgb, partialRgb) }
    })
    report.partial.push({ ...partial, start, end, samples })
    record(`MEASURED_${label}`)
    for (const sample of samples) {
      assert(sample.delta <= 12, `${label} at ${sample.at} s: full ${sample.fullRgb}, partial ${sample.partialRgb}, delta ${sample.delta}`)
    }
  }
  record('FORMAL_PARTIAL_RENDER_PHASE')

  // Grayscale is authored through the Browser and checked again after a reload.
  // Compare matched deliveries with only the R1 grayscale parameter changed.
  const grayEffect = { brightness: 0, contrast: 1, saturation: 1, grayscale: false,
    fadeInSeconds: 0, fadeOutSeconds: 0, enabled: true }
  await applyEffect(videoB, grayEffect)
  const color = await formalOutput(current.revisionId, 1500, 2000, 'grayscale-control')
  await applyEffect(videoB, { ...grayEffect, grayscale: true })
  await page.reload()
  await page.waitForFunction(() => document.querySelector('[data-preview-status]')?.dataset.previewStatus === 'READY')
  const graySnapshot = await snapshot(current.revisionId)
  const grayClip = graySnapshot.tracks.flatMap(track => track.clips).find(clip => clip.clipId === videoB)
  assert.equal(grayClip.effects.find(effect => effect.effectKey === 'video.basic').parameters.grayscale, true)
  const gray = await formalOutput(current.revisionId, 1500, 2000, 'grayscale-authored')
  report.grayscale = { color, gray, samples: [] }
  for (const [at, x, y] of [[0.125, 270, 200], [0.25, 272, 202], [0.375, 274, 204]]) {
    const colorRgb = pixel(color.path, at, x, y)
    const grayRgb = pixel(gray.path, at, x, y)
    // BT.601 luma for the bounded 320×240 fixture, measured from its control delivery.
    const expectedLuma = Math.round(0.299 * colorRgb[0] + 0.587 * colorRgb[1] + 0.114 * colorRgb[2])
    report.grayscale.samples.push({ at, x, y, colorRgb, grayRgb, expectedLuma })
    record('MEASURED_FORMAL_GRAYSCALE')
    assert.equal(dominant(colorRgb), 'green')
    assert(Math.max(...grayRgb) - Math.min(...grayRgb) <= 4, `Grayscale chroma ${grayRgb}`)
    assert(grayRgb.every(channel => Math.abs(channel - expectedLuma) <= 6),
      `Grayscale luma ${grayRgb}, expected ${expectedLuma}`)
  }
  await applyEffect(videoB, grayEffect)
  record('FORMAL_GRAYSCALE_ARTIFACT_AND_RELOAD')

  // Embedded video audio: remove all independent audio clips, then explicitly route
  // the top video clip's embedded audio. The route is canonical; no count heuristic.
  const deleteClip = async clipId => {
    const response = await page.request.post(base + scope + '/timeline-operations/clips/apply', {
      headers, data: { type: 'DELETE_CLIP', baseRevisionId: current.revisionId,
        baseContentHash: current.contentHash, clipId, commandId: `embed-delete-${clipId}` },
    })
    assert([200, 201].includes(response.status()), await response.text())
    const body = await response.json()
    current = { revisionId: body.newRevisionId ?? body.baseRevisionId ?? current.revisionId,
      contentHash: body.resultContentHash ?? body.unchangedContentHash ?? current.contentHash }
  }
  for (const clipId of [audioA, audioB, audioC, audioD]) await deleteClip(clipId)
  const embeddedClip = await addClip('video-embed.mp4', 'track-v4', '4', '7')
  await page.reload()
  await page.waitForFunction(() => document.querySelector('[data-preview-status]')?.dataset.previewStatus === 'READY')
  await field('Clip').selectOption(embeddedClip)
  await applyAudio(embeddedClip, 1, false, 0)
  const embeddedAudio = await sampleAudio(5.5)
  assert(embeddedAudio[440] > 0.02, `embedded 440Hz=${embeddedAudio[440]}`)
  assert(embeddedAudio[660] < 0.01 && embeddedAudio[880] < 0.01, 'embedded route does not add other clips')
  await seek(5.5)
  const embeddedPixel = await canvas.evaluate(element => [...element.getContext('2d').getImageData(160, 120, 1, 1).data].slice(0, 3))
  assert(dominant(embeddedPixel) === 'magenta', `embedded video pixel ${embeddedPixel}`)
  report.embedded = { clipId: embeddedClip, revisionId: current.revisionId,
    audio: embeddedAudio, videoRgb: embeddedPixel }
  record('EMBEDDED_VIDEO_AUDIO_ROUTE_PREVIEW')

  const embeddedRender = page.waitForResponse(r => r.url().endsWith('/render-jobs') && r.request().method() === 'POST')
  await page.getByRole('button', { name: 'Submit RenderJob' }).click()
  const embeddedJobResponse = await embeddedRender
  assert.equal(embeddedJobResponse.status(), 201, await embeddedJobResponse.text())
  const embeddedJob = await embeddedJobResponse.json()
  const embeddedTerminal = page.getByLabel('Current RenderJob').getByText(/^(COMPLETED|FAILED|CANCELLED)$/)
  await embeddedTerminal.waitFor({ timeout: 300000 })
  assert.equal(await embeddedTerminal.textContent(), 'COMPLETED')
  const embeddedResult = await (await page.request.get(base + scope + `/render-jobs/${embeddedJob.renderJobId}/result`, { headers })).json()
  assert(embeddedResult.finalArtifactId)
  await page.getByRole('button', { name: 'Create delivery' }).click()
  const embeddedDownload = page.waitForEvent('download')
  await page.getByRole('button', { name: 'Download output video' }).click()
  const embeddedOutput = join(outputRoot, 'embedded-output.mp4')
  await (await embeddedDownload).saveAs(embeddedOutput)
  const embeddedRaw = execFileSync('/usr/bin/ffmpeg', ['-v', 'error', '-ss', '5.5', '-i', embeddedOutput,
    '-t', '0.085333333', '-ac', '1', '-ar', '48000', '-f', 'f32le', '-'])
  const embeddedSamples = Array.from({ length: embeddedRaw.length / 4 }, (_, index) => embeddedRaw.readFloatLE(index * 4))
  const embeddedRenderAudio = tonePeaks(embeddedSamples, 48000)
  assert(embeddedRenderAudio[440] > 0.02, `rendered embedded 440Hz=${embeddedRenderAudio[440]}`)
  report.embedded.render = { jobId: embeddedJob.renderJobId, artifactId: embeddedResult.finalArtifactId,
    audio: embeddedRenderAudio }
  record('EMBEDDED_VIDEO_AUDIO_FORMAL_RENDER')
  record('DELIVERY_VIDEO_AND_AUDIO_PARITY_WITH_EMBEDDED_AUDIO')

  const batch = async (batchId, operations) => {
    const response = await page.request.post(base + scope + '/timeline-operations/batch/apply', {
      headers, data: { batchId, baseRevisionId: current.revisionId, baseContentHash: current.contentHash, operations },
    })
    assert(response.ok(), await response.text())
    current = await head()
  }
  await batch('remove-dissolve-for-pattern', [{ definitionId: 'timeline.transition.cross-dissolve.delete',
    target: { clipId: videoB }, parameters: { transitionId: 'transition-v2-v3' } }])
  for (const clipId of [videoA, videoB, videoC, videoD, embeddedClip]) await deleteClip(clipId)
  const pattern = await addClip('composition-pattern.mp4', 'track-1', '0', '2', '2')
  await page.reload()
  await page.waitForFunction(() => document.querySelector('[data-preview-status]')?.dataset.previewStatus === 'READY')
  const transform = { positionX: 0.127123, positionY: 0.117321, scaleX: 0.5, scaleY: 0.5,
    rotationDegrees: 30, cropLeft: 0.125, cropRight: 0.875, cropTop: 0.125, cropBottom: 0.75, opacity: 0.8 }
  await applyTransform(pattern, transform)
  await applyEffect(pattern, { brightness: 0.14, contrast: 1.25, saturation: 0.65, grayscale: false,
    fadeInSeconds: 0, fadeOutSeconds: 0, enabled: true })
  await rejectBatch('reject-pixel-transform-format', [{ definitionId: 'timeline.clip.transform.set',
    target: { clipId: pattern }, parameters: { positionX: 40, positionY: 20 } }], 'Unsupported composition transform format')
  const invalidRaster = await page.request.post(base + '/api/render-jobs', {
    headers: { ...headers, 'Idempotency-Key': 'reject-odd-raster' },
    data: { revisionId: current.revisionId, extent: { startMillis: 0, endMillis: 2000,
      frameRateNumerator: 24, frameRateDenominator: 1, width: 641, height: 480 }, profile: 'default', outputFormat: 'MP4' },
  })
  assert.equal(invalidRaster.status(), 400, await invalidRaster.text())
  const rejectedAspect = await page.request.post(base + '/api/render-jobs', {
    headers: { ...headers, 'Idempotency-Key': 'reject-raster-aspect' },
    data: { revisionId: current.revisionId, extent: { startMillis: 0, endMillis: 2000,
      frameRateNumerator: 24, frameRateDenominator: 1, width: 240, height: 320 }, profile: 'default', outputFormat: 'MP4' },
  })
  assert.equal(rejectedAspect.status(), 400, await rejectedAspect.text())
  const aspectDiagnostic = (await rejectedAspect.json()).detail
  assert.equal(aspectDiagnostic, 'Output raster must match the authored composition aspect ratio')
  assert.equal((await head()).revisionId, current.revisionId)

  const composition = report.composition = { outputs: [], samples: [],
    rasterRejections: [invalidRaster.status(), rejectedAspect.status()], aspectDiagnostic }
  const aspect = (numerator, denominator) => ({ format: 'composition-aspect-rational-v1', numerator, denominator })
  const aspectOfRaster = (width, height) => {
    let a = width, b = height
    while (b !== 0) [a, b] = [b, a % b]
    return (width / a) + ':' + (height / a)
  }
  const render = async (label, end, width = 320, height = 240) => {
    const delivered = await formalOutput(current.revisionId, 0, end, label, width, height)
    const [video] = JSON.parse(execFileSync('/usr/bin/ffprobe', ['-v', 'error', '-select_streams', 'v:0',
      '-count_frames', '-show_entries', 'stream=width,height,sample_aspect_ratio,display_aspect_ratio,nb_read_frames,duration',
      '-of', 'json', delivered.path])).streams
    assert.equal(video.width, width)
    assert.equal(video.height, height)
    assert.equal(video.sample_aspect_ratio, '1:1')
    assert.equal(video.display_aspect_ratio, aspectOfRaster(width, height))
    assert.equal(Number(video.nb_read_frames), end * 24 / 1000)
    assert(Math.abs(Number(video.duration) - end / 1000) < 1 / 24)
    composition.outputs.push({ ...delivered, label, video })
    record('FORMAL_COMPOSITION_' + label)
    return delivered
  }
  const probes = [[5, 5], [42, 29], [112, 68], [133, 92], [148, 118], [163, 131]]
  const width320 = await render('single-transformed', 2000)
  const width640 = await render('landscape-double-resolution', 2000, 640, 480)
  assert.equal(width320.revisionId, width640.revisionId)
  await seek(0.5)
  const previewPixels = await canvas.evaluate((element, positions) => positions.map(([x,y]) =>
    [...element.getContext('2d').getImageData(x,y,1,1).data].slice(0,3)), probes)
  const singlePixels = probes.map(([x,y]) => pixel(width320.path, 0.5, x, y))
  const doublePixels = probes.map(([x,y]) => pixel(width640.path, 0.5, x*2, y*2))
  for (let index = 0; index < probes.length; index++) {
    const low = singlePixels[index], high = doublePixels[index], preview = previewPixels[index]
    composition.samples.push({ label: 'preview-and-resolution', position: probes[index], low, high, preview,
      previewDelta: maxDelta(low, preview), resolutionDelta: maxDelta(low, high) })
    assert(maxDelta(low, high) <= 24, 'resolution conformance ' + JSON.stringify(composition.samples.at(-1)))
    assert(maxDelta(low, preview) <= 24, 'pattern Preview/Render conformance ' + JSON.stringify(composition.samples.at(-1)))
  }
  assert(singlePixels.slice(0,2).every(rgb => Math.max(...rgb) <= 8), 'off-frame and rotated corner must be black')
  assert(new Set(singlePixels.slice(2).map(rgb => rgb.join(','))).size >= 3, 'nonuniform source must retain distinct interior samples')

  await addTrack('VIDEO', 'track-composition-empty', 'composition-empty-track')
  const empty = await render('empty-track-invariant', 2000)
  await page.reload()
  await page.waitForFunction(() => document.querySelector('[data-preview-status]')?.dataset.previewStatus === 'READY')
  await applyAudio(pattern, 1, true, 0)
  const mutedComposition = await render('muted-route-invariant', 2000)
  const compareGeometry = (name, a, b, time) => {
    const deltas = probes.map(([x,y]) => maxDelta(pixel(a.path, time, x, y), pixel(b.path, time, x, y)))
    composition.samples.push({ label: name, time, deltas })
    assert(Math.max(...deltas) <= 6, name + ' changed geometry: ' + deltas.join(','))
  }
  compareGeometry('empty-track', width320, empty, 0.5)
  compareGeometry('muted-route', width320, mutedComposition, 0.5)
  const second = await addClip('composition-pattern.mp4', 'track-1', '2', '4', '2')
  await page.reload()
  await page.waitForFunction(() => document.querySelector('[data-preview-status]')?.dataset.previewStatus === 'READY')
  await applyTransform(second, transform)
  const sameTrack = await render('multiple-clips-one-track', 4000)
  const relocate = await page.request.post(base + scope + '/timeline-operations/clips/apply', {
    headers, data: { type: 'MOVE_CLIP', baseRevisionId: current.revisionId, baseContentHash: current.contentHash,
      clipId: second, destinationTrackId: 'track-composition-empty', delta: '2/1', absolute: true,
      commandId: 'composition-equivalent-relocation' },
  })
  assert(relocate.ok(), await relocate.text())
  current = await head()
  const separateTracks = await render('equivalent-two-tracks', 4000)
  compareGeometry('equivalent-shapes-first-clip', sameTrack, separateTracks, 0.5)
  compareGeometry('equivalent-shapes-second-clip', sameTrack, separateTracks, 2.5)

  await page.reload()
  await page.waitForFunction(() => document.querySelector('[data-preview-status]')?.dataset.previewStatus === 'READY')
  await field('Composition aspect ratio').selectOption('3:4')
  current = await waitRevision(current.revisionId)
  assert.deepEqual((await snapshot(current.revisionId)).metadata.compositionAspectRatio, aspect(3, 4))
  await page.reload()
  await page.waitForFunction(() => document.querySelector('[data-preview-status]')?.dataset.previewStatus === 'READY')
  assert.equal(await field('Composition aspect ratio').inputValue(), '3:4')
  const portrait = await render('portrait-composition', 4000, 240, 320)
  const portraitPixels = [[5,5], [114,148], [135,162]].map(([x,y]) => pixel(portrait.path, 0.5, x, y))
  assert(Math.max(...portraitPixels[0]) <= 8)
  assert(portraitPixels.slice(1).some(rgb => Math.max(...rgb) > 32))
  composition.samples.push({ label: 'portrait', positions: [[5,5], [114,148], [135,162]], portraitPixels })
  const aspectOperation = ratio => ({ definitionId: 'timeline.composition.aspect.set', parameters: { aspectRatio: ratio } })
  const selectPreset = async (label, expected) => {
    await field('Composition aspect ratio').selectOption(label)
    current = await waitRevision(current.revisionId)
    assert.deepEqual((await snapshot(current.revisionId)).metadata.compositionAspectRatio, expected)
  }
  const waitReady = () => page.waitForFunction(() => document.querySelector('[data-preview-status]')?.dataset.previewStatus === 'READY')
  const setCustom = async (width, height) => {
    await field('Custom aspect width').fill(String(width))
    await field('Custom aspect height').fill(String(height))
    await edit.getByRole('button', { name: 'Apply custom aspect' }).click()
    current = await waitRevision(current.revisionId)
    const output = await snapshot(current.revisionId)
    assert.deepEqual(output.metadata.compositionAspectRatio, aspect(width, height))
    await page.reload()
    await waitReady()
    assert.equal(await field('Composition aspect ratio').inputValue(), width + ':' + height)
  }
  const generalized = composition.generalized = { samples: [], rejected: [] }
  const expectedGeometry = (await snapshot(current.revisionId)).tracks
    .flatMap(track => track.clips).find(clip => clip.clipId === pattern).transform
  const probePlacement = async (label, output, width, height) => {
    const angle = transform.rotationDegrees * Math.PI / 180
    const cos = Math.cos(angle), sin = Math.sin(angle)
    const w = width * transform.scaleX, h = height * transform.scaleY
    const left = width * transform.positionX, top = height * transform.positionY
    const centerX = left + (w * cos + h * sin) / 2
    const centerY = top + (w * sin + h * cos) / 2
    const interior = [[0.2, 0.58], [0.47, 0.61], [0.68, 0.6], [0.82, 0.57]].map(([u, v]) => {
      const x = (u - 0.5) * w, y = (v - 0.5) * h
      return [2 * Math.round((centerX + x * cos - y * sin) / 2),
        2 * Math.round((centerY + x * sin + y * cos) / 2)]
    })
    const points = [[4, 4], [2 * Math.ceil(left / 2) + 2, 2 * Math.ceil(top / 2) + 2], ...interior]
    const rgb = points.map(([x,y]) => pixel(output.path, 0.5, x, y))
    const measured = { label, points, rgb }
    generalized.samples.push(measured)
    record('MEASURED_LAYOUT_' + label)
    assert(rgb.slice(0, 2).every(value => Math.max(...value) <= 8), label + ' outside-crop or rotated corner')
    assert(rgb.slice(2).every(value => Math.max(...value) > 32), label + ' lost an interior sample')
    assert(new Set(rgb.slice(2).map(value => value.join(','))).size >= 3, label + ' lost chromatic detail')
    assert.deepEqual((await snapshot(current.revisionId)).tracks
      .flatMap(track => track.clips).find(clip => clip.clipId === pattern).transform, expectedGeometry)
    return measured
  }

  const matchedClip = await addClip('composition-source-rotated.mp4', 'track-v4', '4', '6', '2')
  await page.reload()
  await waitReady()
  await field('Clip').selectOption(matchedClip)
  const matchFile = sources['composition-source-rotated.mp4']
  const matchStream = matchFile.streams.find(stream => stream.kind === 'VIDEO')
  const matchPath = scope + '/source-media/' + matchFile.artifactId + '/display-aspect'
  const matchQuery = '?' + new URLSearchParams({ contentDigest: matchFile.contentDigest, streamId: matchStream.streamId })
  const deniedMatch = await page.request.get(base + matchPath.replace(tenant, 'foreign-tenant') + matchQuery, { headers })
  assert([403, 404].includes(deniedMatch.status()))
  const missingMatch = await page.request.get(base + matchPath + '?' + new URLSearchParams({
    contentDigest: matchFile.contentDigest, streamId: 'unknown-stream',
  }), { headers })
  assert.equal(missingMatch.status(), 422, await missingMatch.text())
  await edit.getByRole('button', { name: 'Match Source' }).click()
  current = await waitRevision(current.revisionId)
  const matchAspect = (await snapshot(current.revisionId)).metadata.compositionAspectRatio
  assert.deepEqual(matchAspect, aspect(9, 32))
  generalized.matchSource = { aspect: matchAspect, revisionId: current.revisionId,
    unavailable: missingMatch.status(), foreignDenied: deniedMatch.status() }
  await deleteClip(matchedClip)
  await page.reload()
  await waitReady()

  const presets = await field('Composition aspect ratio').locator('option').allTextContents()
  for (const expected of ['16:9', '9:16', '1:1', '4:3', '3:4', '4:5', '21:9', '2.39:1']) {
    assert(presets.includes(expected), 'Missing preset ' + expected)
  }
  await selectPreset('16:9', aspect(16, 9))
  const landscapeHead = await head()
  const normalized = async (width, height) => {
    const response = await page.request.post(base + scope + '/timeline-operations/batch/apply', {
      headers, data: { batchId: 'equivalent-ratio-replay', baseRevisionId: landscapeHead.revisionId,
        baseContentHash: landscapeHead.contentHash, operations: [aspectOperation(aspect(width, height))] },
    })
    assert(response.ok(), await response.text())
    return response.json()
  }
  const normalizedFirst = await normalized(1920, 1080)
  const normalizedAgain = await normalized(3840, 2160)
  assert.equal(normalizedFirst.status, 'NO_OP')
  assert.equal(normalizedAgain.status, 'NO_OP')
  assert.equal(normalizedAgain.newContentHash, landscapeHead.contentHash)
  assert.deepEqual(await head(), landscapeHead)
  generalized.normalization = { unchangedHead: landscapeHead, first: normalizedFirst.status, replay: normalizedAgain.status }
  const conflict = await page.request.post(base + scope + '/timeline-operations/batch/apply', {
    headers, data: { batchId: 'equivalent-ratio-replay', baseRevisionId: landscapeHead.revisionId,
      baseContentHash: landscapeHead.contentHash, operations: [aspectOperation(aspect(9, 16))] },
  })
  assert.equal(conflict.status(), 409, await conflict.text())
  assert.deepEqual(await head(), landscapeHead)
  generalized.normalization.conflictStatus = conflict.status()

  const ambiguousBody = JSON.stringify({ batchId: 'duplicate-aspect-member', baseRevisionId: landscapeHead.revisionId,
    baseContentHash: landscapeHead.contentHash, operations: [aspectOperation(aspect(13, 11))] })
    .replace('"numerator":13', '"numerator":0,"numerator":13')
  const ambiguous = await page.request.post(base + scope + '/timeline-operations/batch/apply', {
    headers: { ...headers, 'Content-Type': 'application/json' }, data: ambiguousBody,
  })
  assert.equal(ambiguous.status(), 400, await ambiguous.text())
  assert((await ambiguous.text()).includes('Invalid or ambiguous Timeline batch JSON'))
  assert.deepEqual(await head(), landscapeHead)
  generalized.duplicateJsonStatus = ambiguous.status()

  const wide = await render('generalized-16-9', 4000, 320, 180)
  const wideDouble = await render('generalized-16-9-double', 4000, 640, 360)
  assert.equal(wide.revisionId, wideDouble.revisionId)
  assert.deepEqual(await head(), landscapeHead)
  const wideSample = await probePlacement('16:9', wide, 320, 180)
  await waitReady()
  await seek(0.5)
  const widePreview = await canvas.evaluate((element, points) => ({
    width: element.width, height: element.height,
    rgb: points.map(([x,y]) => [...element.getContext('2d').getImageData(x,y,1,1).data].slice(0,3)),
  }), wideSample.points)
  assert.equal(widePreview.width, 320)
  assert.equal(widePreview.height, 180)
  const wideHigh = wideSample.points.map(([x,y]) => pixel(wideDouble.path, 0.5, 2*x, 2*y))
  generalized.resolution = { revisionId: landscapeHead.revisionId, points: wideSample.points,
    low: wideSample.rgb, high: wideHigh, preview: widePreview.rgb,
    resolutionDeltas: wideSample.rgb.map((value, index) => maxDelta(value, wideHigh[index])),
    previewDeltas: wideSample.rgb.map((value, index) => maxDelta(value, widePreview.rgb[index])) }
  record('MEASURED_GENERALIZED_RESOLUTION')
  assert(Math.max(...generalized.resolution.resolutionDeltas) <= 24, JSON.stringify(generalized.resolution))
  assert(Math.max(...generalized.resolution.previewDeltas) <= 24, JSON.stringify(generalized.resolution))

  for (const [label, n, d, width, height] of [['9:16', 9, 16, 180, 320], ['1:1', 1, 1, 320, 320]]) {
    await selectPreset(label, aspect(n, d))
    const output = await render('generalized-' + label.replace(':', '-'), 4000, width, height)
    await probePlacement(label, output, width, height)
  }
  await setCustom(13, 11)
  const custom = await render('generalized-custom-13-11', 4000, 312, 264)
  await probePlacement('13:11', custom, 312, 264)
  for (const [label, invalid, diagnostic] of [
    ['old-named-aspect', 'PORTRAIT_3_4', 'Unsupported composition aspect ratio format'],
    ['zero-aspect', aspect(0, 11), 'Composition aspect components'],
    ['fractional-aspect', aspect(1.5, 11), 'Composition aspect components'],
    ['overflow-aspect', aspect(2147483648, 11), 'Composition aspect components'],
  ]) {
    await rejectBatch(label, [aspectOperation(invalid)], diagnostic)
  }

  await setCustom(1000, 777)
  const unsupported = await page.request.post(base + '/api/render-jobs', {
    headers: { ...headers, 'Idempotency-Key': 'no-exact-aligned-raster' },
    data: { revisionId: current.revisionId, extent: { startMillis: 0, endMillis: 2000,
      frameRateNumerator: 24, frameRateDenominator: 1, width: 1000, height: 778 }, profile: 'default', outputFormat: 'MP4' },
  })
  assert.equal(unsupported.status(), 400, await unsupported.text())
  const unsupportedDiagnostic = (await unsupported.json()).detail
  assert.equal(unsupportedDiagnostic, 'No supported output raster exactly matches the authored composition aspect ratio')
  assert.equal(await page.getByRole('button', { name: 'Submit RenderJob' }).isDisabled(), true)
  assert.deepEqual(await head(), current)
  generalized.unsupported = { authored: aspect(1000, 777), revisionId: current.revisionId,
    status: unsupported.status(), detail: unsupportedDiagnostic }
  record('FORMAL_GENERALIZED_ASPECTS_AND_EXACT_REJECTIONS')

  for (const clipId of [pattern, second]) await deleteClip(clipId)
  await batch('restore-landscape-for-common-provider', [{ definitionId: 'timeline.composition.aspect.set',
    parameters: { aspectRatio: aspect(4, 3) } }])
  await addClip('video-a.mp4', 'track-1', '0', '2', '2')
  const commonRevision = await head()
  const commonBmf = await formalOutput(commonRevision.revisionId, 0, 2000, 'common-provider-bmf')
  const commonFfmpeg = await formalOutput(commonRevision.revisionId, 0, 2000, 'common-provider-ffmpeg',
    320, 240, 'http://127.0.0.1:18082')
  const preferred = await formalOutput(commonRevision.revisionId, 0, 2000, 'common-provider-preferred',
    320, 240, 'http://127.0.0.1:18083')
  for (const output of [commonBmf, commonFfmpeg, preferred]) {
    const [video] = JSON.parse(execFileSync('/usr/bin/ffprobe', ['-v', 'error', '-select_streams', 'v:0',
      '-count_frames', '-show_entries', 'stream=width,height,nb_read_frames,duration', '-of', 'json', output.path])).streams
    assert.equal(video.width, 320)
    assert.equal(video.height, 240)
    assert.equal(Number(video.nb_read_frames), 48)
  }
  const bmfCommonRgb = pixel(commonBmf.path, 0.5, 160, 120)
  const ffmpegCommonRgb = pixel(commonFfmpeg.path, 0.5, 160, 120)
  assert.equal(dominant(bmfCommonRgb), 'red')
  assert.equal(dominant(ffmpegCommonRgb), 'red')
  assert(maxDelta(bmfCommonRgb, ffmpegCommonRgb) <= 12)
  assert.deepEqual(await head(), commonRevision)
  const preferredRgb = pixel(preferred.path, 0.5, 160, 120)
  assert(maxDelta(bmfCommonRgb, preferredRgb) <= 12)
  composition.providerComparison = { preferredRgb, revision: commonRevision, bmfCommonRgb, ffmpegCommonRgb,
    outputs: [{ ...commonBmf, provider: 'bmf' }, { ...commonFfmpeg, provider: 'ffmpeg' },
      { ...preferred, provider: 'preferred' }] }
  const twelve = await formalOutput(commonRevision.revisionId, 0, 2000, 'common-source-12fps',
    320, 240, base, 12)
  const [rateWitness] = JSON.parse(execFileSync('/usr/bin/ffprobe', ['-v', 'error', '-select_streams', 'v:0',
    '-count_frames', '-show_entries', 'stream=nb_read_frames,avg_frame_rate,duration', '-of', 'json', twelve.path])).streams
  assert.equal(rateWitness.avg_frame_rate, '12/1')
  assert.equal(Number(rateWitness.nb_read_frames), 24)
  assert.equal(Number(rateWitness.duration), 2)
  composition.rateConversion = { ...twelve, ...rateWitness }
  const restrictedRaster = await page.request.post('http://127.0.0.1:18082/api/render-jobs', {
    headers: { ...headers, 'Idempotency-Key': 'explicit-ffmpeg-unsupported-raster' },
    data: { revisionId: commonRevision.revisionId, extent: { startMillis: 0, endMillis: 2000,
      frameRateNumerator: 24, frameRateDenominator: 1, width: 640, height: 480 }, profile: 'default', outputFormat: 'MP4' },
  })
  assert.equal(restrictedRaster.status(), 400, await restrictedRaster.text())

  // A one-track gap is legal on the FFmpeg compositor and outside the BMF concatenation envelope.
  await addClip('video-b.mp4', 'track-1', '3', '4', '1')
  const gapRevision = await head()
  const rejectedGap = await page.request.post(base + '/api/render-jobs', {
    headers: { ...headers, 'Idempotency-Key': 'explicit-bmf-gap' },
    data: { revisionId: gapRevision.revisionId, extent: { startMillis: 0, endMillis: 4000,
      frameRateNumerator: 24, frameRateDenominator: 1, width: 320, height: 240 }, profile: 'default', outputFormat: 'MP4' },
  })
  assert.equal(rejectedGap.status(), 400, await rejectedGap.text())
  const gap = await formalOutput(gapRevision.revisionId, 0, 4000, 'preferred-legal-ffmpeg',
    320, 240, 'http://127.0.0.1:18083')
  const gapRgb = pixel(gap.path, 2.5, 160, 120)
  assert(Math.max(...gapRgb) < 15)
  assert.equal(dominant(pixel(gap.path, 0.5, 160, 120)), 'red')
  composition.legalFfmpeg = { ...gap, provider: 'ffmpeg', policy: 'preferred', gapRgb,
    bmfGapStatus: rejectedGap.status(), ffmpegRasterStatus: restrictedRaster.status() }
  report.status = 'PASS'
  record('CANONICAL_COMPOSITION_CONFORMANCE')

} catch (error) {
  report.status = 'FAIL'
  report.error = String(error)
  report.previewStatus = await page.locator('[data-preview-status] [role=status]').first().textContent({ timeout: 2000 }).catch(() => null)
  record('FAILED')
  await page.screenshot({ path: join(outputRoot, 'failure.png'), fullPage: true }).catch(() => undefined)
  throw error
} finally {
  await browser.close()
}
