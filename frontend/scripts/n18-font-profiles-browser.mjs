import assert from 'node:assert/strict'
import { execFileSync } from 'node:child_process'
import { createHash } from 'node:crypto'
import { writeFileSync } from 'node:fs'
import { join } from 'node:path'
import { exportRevision } from './formal-export.mjs'

const sans = 'noto-sc-color-emoji-v1'
const serif = 'noto-serif-sc-color-emoji-v1'
const digest = bytes => createHash('sha256').update(bytes).digest('hex')
const ff = args => execFileSync('/usr/bin/ffmpeg', ['-v', 'error', ...args], { maxBuffer: 32 * 1024 * 1024 })

export async function runFontProfiles(ctx) {
  const { page, outputRoot, url, endpoint, post, cp, waitRevision, addClip, upload } = ctx
  const path = name => join(outputRoot, name)
  const report = { status: 'RUNNING', browser: page.context().browser().version(), frames: {}, requests: {}, blocks: {}, exports: {} }
  const save = () => writeFileSync(path('font-result.json'), JSON.stringify(report, null, 2) + '\n')
  const bg = [24, 36, 64]
  const inline = ctx.inline === true
  const text = inline ? '人月🌻\uFE0F\nMiddleword\n水永' : '人月㐀永 🌻\uFE0F Small Latin Aa'
  const width = 114
  const fontSize = 16
  const word = 'Middleword'
  const start = Array.from(text.slice(0, text.indexOf(word))).length
  const runs = [{ start, end: start + word.length, fontProfile: serif }]
  if (inline) report.inlineRuns = runs
  const layoutEndpoint = ctx.base + ctx.scope + '/timeline-operations/subtitle-layout'
  const requestLayout = async (label, content, w, size, fontProfile, destinationWidth, fontRuns = []) => {
    const r = await page.request.post(layoutEndpoint, {
      headers: { ...ctx.headers, Accept: 'image/png' },
      data: { text: content, width: w, fontSize: size, ...(fontProfile ? { fontProfile } : {}), fontRuns, destinationWidth },
    })
    assert.equal(r.status(), 200, await r.text())
    const bytes = await r.body(), file = path(label + '.png')
    writeFileSync(file, bytes)
    const block = { width: bytes.readUInt32BE(16), height: bytes.readUInt32BE(20), bytes: bytes.length, sha256: digest(bytes) }
    report.blocks[label] = block
    const rgba = ff(['-i', file, '-frames:v', '1', '-f', 'rawvideo', '-pix_fmt', 'rgba', '-'])
    assert.equal(rgba.length, block.width * block.height * 4)
    return { ...block, rgba }
  }
  // Cross-revision parity against the frozen N17 320-pixel layout of the original mixed-script fixture.
  const legacyRef = await requestLayout('legacy-n17-reference', '人月水永 🌻\uFE0F\nSmall Latin 12 px', 140, 12, null, 320)
  assert.equal(legacyRef.sha256, 'fe8a58e1a1fb47802c61aef7eb1099fe43f4ac197d407e9bf0090292d719a596')
  const blocks = {}
  for (const target of [320, 1600]) {
    blocks[target] = [
      await requestLayout('sans-' + target, text, width, fontSize, sans, target),
      await requestLayout('serif-' + target, text, width, fontSize, inline ? sans : serif, target, inline ? runs : []),
    ]
    assert.notEqual(blocks[target][0].sha256, blocks[target][1].sha256)
    const lineHeight = Math.ceil(fontSize * 1.45)
    // Narrow enough to expose a real metric-driven wrap difference with the same text.
    assert.deepEqual(blocks[target].map(block => (block.height * 320 / target - 4) / lineHeight), inline ? [3, 3] : [2, 3])
    if (inline) {
      const [a, b] = blocks[target], stride = a.width * 4
      const first = (2 + lineHeight) * target / 320 * stride, last = (2 + 2 * lineHeight) * target / 320 * stride
      assert(a.rgba.subarray(0, first).equals(b.rgba.subarray(0, first)))
      const reference = await requestLayout('whole-serif-reference-' + target, text, width, fontSize, serif, target)
      assert(b.rgba.subarray(first, last).equals(reference.rgba.subarray(first, last)))
      assert(!a.rgba.subarray(first, last).equals(b.rgba.subarray(first, last)))
      assert(a.rgba.subarray(last).equals(b.rgba.subarray(last)))
    }
  }
  for (const [label, fallback] of [['han-fallback', '㐀'], ['color-emoji', '🌻']]) {
    const a = await requestLayout(label + '-sans', fallback, width, fontSize, sans, 1600)
    const b = await requestLayout(label + '-serif', fallback, width, fontSize, serif, 1600)
    assert.equal(a.sha256, b.sha256, label + ' uses the same pinned raster under both profiles')
  }
  ff(['-y', '-f', 'lavfi', '-i', 'color=0x182440:s=320x240:r=24', '-t', '4',
    '-c:v', 'libopenh264', '-pix_fmt', 'yuv420p', path('background.mp4')])
  await upload('background.mp4')
  await addClip('background.mp4', 'track-1', '0', '4', '4')
  const base = await cp('base')
  const params = { text, width, fontSize, keys: [] }
  async function seedCue(cue) {
    const current = await ctx.syncHead()
    const body = {
      batchId: ctx.project + '-' + cue.cueId,
      baseRevisionId: current.revisionId, baseContentHash: current.contentHash,
      operations: [{ definitionId: 'timeline.subtitle.cue.set', parameters: { ...params, ...cue } }],
    }
    const preview = await post('preview', body)
    assert.equal(preview.status(), 200, await preview.text())
    const applied = await post('apply', body, (await preview.json()).batchDigest)
    assert.equal(applied.status(), 201, await applied.text())
  }
  await seedCue({ cueId: 'legacy-cue', start: '0', end: '2/1' })
  const first = await cp('first')
  assert.equal(first.rows, base.rows + 1)
  await seedCue({ cueId: 'second-cue', start: '2/1', end: '4/1', fontProfile: sans })
  const defaults = await cp('legacy')
  assert.equal(defaults.rows, first.rows + 1)
  await page.goto(url)
  await waitRevision(defaults.head)

  const edit = page.getByRole('region', { name: 'Subtitle edit', exact: true })
  const button = name => edit.getByRole('button', { name, exact: true })
  const input = name => edit.getByLabel(name, { exact: true })
  const response = method => page.waitForResponse(r => r.url() === endpoint + '/' + method)
  const seek = page.getByLabel('Timeline preview seek')
  let writeRequests = 0
  page.on('request', r => { if (r.url() === endpoint + '/apply') writeRequests++ })

  function imageScores(rgb, target, intended) {
    const originX = target / 10, originY = target * 33 / 80
    const control = [...rgb.subarray(0, 3)]
    assert(control.every((c, i) => Math.abs(c - bg[i]) <= 12), JSON.stringify(control))
    return blocks[target].map((block, profile) => {
      let overlap = 0, union = 0, yellow = 0, ink = 0
      for (let y = 0; y < block.height; y++) for (let x = 0; x < block.width; x++) {
        const argb = (y * block.width + x) * 4
        const p = ((originY + y) * target + originX + x) * 3
        const alpha = block.rgba[argb + 3] / 255
        const wanted = control.some((c, k) => Math.abs((block.rgba[argb + k] - c) * alpha) > 65)
        const visible = control.some((c, k) => Math.abs(rgb[p + k] - c) > 65)
        if (wanted && visible) overlap++
        if (wanted || visible) union++
        if (wanted) ink++
        if (visible && block.rgba[argb] > 145 && block.rgba[argb + 1] > 90 && block.rgba[argb + 2] < 120 &&
            rgb[p] > 130 && rgb[p + 1] > 75 && rgb[p + 2] < 140) yellow++
      }
      return { profile: profile ? serif : sans, iou: overlap / union, ink, yellow, intended: profile === intended }
    })
  }
  function measure(label, rgb, target, intended, tolerance) {
    assert.equal(rgb.length, target * (target * 3 / 4) * 3)
    const scores = imageScores(rgb, target, intended)
    assert(scores[intended].yellow > 10 && scores[intended].ink > 100, JSON.stringify(scores))
    assert(scores[intended].iou >= tolerance && scores[intended].iou > scores[1 - intended].iou + 0.08, JSON.stringify(scores))
    report.frames[label] = { target, tolerance, scores }
    save()
  }
  async function frame(label, time, id, variant) {
    await seek.evaluate((el, t) => {
      Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set.call(el, String(t))
      el.dispatchEvent(new Event('input', { bubbles: true }))
      el.dispatchEvent(new Event('change', { bubbles: true }))
    }, time)
    const { rgba, width: w, height: h } = blocks[320][variant]
    const other = blocks[320][1 - variant].rgba
    const points = []
    for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
      const i = (y * w + x) * 4
      if (rgba[i + 3] > 240 && (other[i + 3] ?? 0) < 15) points.push([32 + x, 132 + y])
    }
    assert(points.length > 12)
    const canvas = await (await page.waitForFunction(({ time, id, points }) => {
      const root = document.querySelector('[data-preview-revision]')
      if (!root || Math.abs(Number(root.dataset.previewTime) - time) > .001 || root.dataset.activeSubtitles !== id) return false
      const canvas = root.querySelector('canvas')
      if (canvas.width !== 320 || canvas.height !== 240) return false
      const c = canvas.getContext('2d'), data = c.getImageData(0, 0, canvas.width, canvas.height).data
      const index = (x, y) => (y * canvas.width + x) * 4
      if (Math.abs(data[0] - 24) > 12) return false
      if (points.filter(([x, y]) => data[index(x, y)] > 130).length < points.length * .90) return false
      return canvas.toDataURL()
    }, { time, id, points })).jsonValue()
    const file = path(label + '.png')
    writeFileSync(file, Buffer.from(canvas.split(',')[1], 'base64'))
    measure(label, ff(['-i', file, '-frames:v', '1', '-f', 'rawvideo', '-pix_fmt', 'rgb24', '-']), 320, variant, .95)
  }
  report.fixture = { text, width, fontSize, timeA: 1, timeB: 3, intervals: ['[0,2)', '[2,4)'], source: 'background.mp4' }
  await input('Subtitle cue').selectOption('second-cue')
  assert.equal(await input('Subtitle font').inputValue(), sans)
  await frame('draft-sans-before', 3, 'second-cue', 0)
  async function preview() {
    const next = response('preview')
    await button('Preview subtitle').click()
    const result = await next
    assert.equal(result.status(), 200, await result.text())
    await button('Confirm subtitle').waitFor()
    return { body: result.request().postDataJSON(), digest: (await result.json()).batchDigest }
  }

  async function chooseRange(value, selectedText = word) {
    const area = input('Subtitle text')
    await area.focus()
    await area.evaluate((element, word) => {
      const start = element.value.indexOf(word)
      element.setSelectionRange(start, start + word.length)
      element.dispatchEvent(new Event('select', { bubbles: true }))
      document.dispatchEvent(new Event('selectionchange'))
    }, selectedText)
    await input('Font for selected text').selectOption(value)
  }
  if (inline) {
    await chooseRange(serif)
    const area = input('Subtitle text')
    const prefix = 'e\u0301👩‍🚀'
    await area.press('Control+Home')
    await page.keyboard.insertText(prefix)
    // The pinned Serif face covers the decomposed mark, while Sans does not.
    await chooseRange(serif, 'e\u0301')
    const edited = (await preview()).body.operations[0].parameters.fontRuns
    assert.deepEqual(edited, [{ start: 0, end: 2, fontProfile: serif },
      { start: runs[0].start + Array.from(prefix).length, end: runs[0].end + Array.from(prefix).length, fontProfile: serif }])
    await button('Cancel subtitle').click()
    await area.fill(text)
  } else await input('Subtitle font').selectOption(serif)
  await preview()
  await frame('draft-serif', 3, 'second-cue', 1)
  await button('Cancel subtitle').click()
  await frame('draft-sans-after-cancel', 3, 'second-cue', 0)
  assert.equal(writeRequests, 0)
  assert.deepEqual(await cp('cancel'), defaults)

  const frozen = await preview()
  const alt = frozen.body.operations[0].parameters
  assert.equal(alt.cueId, 'second-cue')
  if (inline) assert.deepEqual(alt.fontRuns, runs)
  else assert.equal(alt.fontProfile, serif)
  report.serifCueId = alt.cueId
  save()
  const unauth = await page.request.post(endpoint + '/apply', {
    data: frozen.body, headers: { 'If-Match': '"' + frozen.digest + '"' },
  })
  report.requests.denied = unauth.status()
  assert.equal(unauth.status(), 401, await unauth.text())
  assert.deepEqual(await cp('denied'), defaults)
  const invalid = structuredClone(frozen.body)
  invalid.batchId += '-invalid'
  invalid.operations[0].parameters.fontProfile = 'unknown-font-profile-v1'
  for (const method of ['preview', 'apply']) {
    const rejected = await post(method, invalid, method === 'apply' ? frozen.digest : null)
    assert.equal(rejected.status(), 400, await rejected.text())
    report.requests['invalid-' + method] = { status: rejected.status(), problem: await rejected.json() }
  }
  if (inline) {
    for (const [label, sample, start, end] of [['combining-split', 'e\u0301', 0, 1],
        ['emoji-split', '🌻️', 0, 1], ['unsupported-cluster', '🌻‍🌻', 0, 3]]) {
      const bad = structuredClone(frozen.body)
      bad.batchId += '-' + label
      Object.assign(bad.operations[0].parameters, { text: sample, fontRuns: [{ start, end, fontProfile: serif }] })
      for (const method of ['preview', 'apply']) {
        const r = await post(method, bad, method === 'apply' ? frozen.digest : null)
        assert([400, 422].includes(r.status()), await r.text())
        report.requests[label + '-' + method] = { status: r.status(), problem: await r.json() }
      }
    }
  }
  assert.deepEqual(await cp('invalid'), defaults)

  const routeUrl = '**' + new URL(endpoint).pathname + '/apply', attempts = []
  await page.route(routeUrl, async route => {
    const req = route.request(), r = await route.fetch()
    attempts.push({ path: new URL(req.url()).pathname, body: req.postData(), ifMatch: req.headers()['if-match'],
      status: r.status(), receipt: await r.json() })
    if (attempts.length === 1) await route.abort('failed')
    else await route.fulfill({ response: r })
  })
  await button('Confirm subtitle').click()
  await button('Retry confirmed subtitle').waitFor()
  const accepted = await cp('lost')
  assert.equal(accepted.rows, defaults.rows + 1)
  await page.route('**/timeline/branches/main', request => request.abort('failed'), { times: 1 })
  await button('Retry confirmed subtitle').click()
  await edit.getByText('Subtitle accepted at revision ' + accepted.head + '. Reload HEAD to refresh the editor.').waitFor()
  await page.getByRole('button', { name: 'Reload HEAD', exact: true }).click()
  await waitRevision(accepted.head)
  assert.deepEqual(await cp('recovered'), accepted)
  await page.unroute(routeUrl)
  assert.equal(writeRequests, 2)
  assert.deepEqual(attempts.map(a => a.status), [201, 200])
  for (const field of ['path', 'body', 'ifMatch']) assert.equal(attempts[0][field], attempts[1][field])
  assert.equal(attempts[0].ifMatch, '"' + frozen.digest + '"')
  assert.deepEqual(JSON.parse(attempts[0].body), frozen.body)
  assert(attempts.every(a => a.receipt.newRevisionId === accepted.head))
  report.requests.exactReplay = attempts

  const currentFont = async name => {
    await input('Subtitle cue').selectOption('second-cue')
    assert.equal(await input('Subtitle font').inputValue(), inline ? sans : name)
    if (inline && name === serif) await edit.getByRole('list', { name: 'Selected text fonts' }).getByText(/Middleword/).waitFor()
  }
  await currentFont(serif)
  await frame('accepted-serif', 3, 'second-cue', 1)
  for (const [direction, profile, variant] of [['Undo', sans, 0], ['Redo', serif, 1]]) {
    const pending = response('apply')
    await page.getByRole('button', { name: direction + ' subtitle', exact: true }).click()
    const r = await pending
    assert.equal(r.status(), 201, await r.text())
    const receipt = await r.json()
    report.requests[direction] = receipt
    await waitRevision(receipt.newRevisionId)
    assert.equal((await cp(direction.toLowerCase())).head, receipt.newRevisionId)
    await currentFont(profile)
    await frame(direction.toLowerCase(), 3, 'second-cue', variant)
  }
  assert.equal(writeRequests, 4)
  const stale = await post('apply', { ...frozen.body, batchId: ctx.project + '-stale-font' }, frozen.digest)
  assert.equal(stale.status(), 409, await stale.text())
  report.requests.stale = await stale.json()
  assert.equal(report.requests.stale.errorCode, 'STALE_BASE_REVISION')
  const final = await cp('stale')
  await page.reload()
  await waitRevision(final.head)
  await currentFont(serif)
  await frame('reopened-sans', 1, 'legacy-cue', 0)
  await frame('reopened-serif', 3, 'second-cue', 1)
  assert.deepEqual(await cp('reopened'), final)

  for (const target of [320, 1600]) {
    const height = target * 3 / 4, t0 = performance.now()
    const { path: mp4, ...receipt } = await exportRevision(ctx, final.head, 0, 4000, 'bmf-' + target, target, height)
    const wallMs = performance.now() - t0
    const stream = JSON.parse(execFileSync('/usr/bin/ffprobe', ['-v', 'error', '-show_streams', '-of', 'json', mp4])).streams[0]
    assert.equal(stream.nb_frames, '96')
    assert.equal(stream.width, target)
    assert.equal(stream.height, height)
    assert.equal(stream.avg_frame_rate, '24/1')
    report.exports[target] = { ...receipt, wallMs, stream }
    for (const [frame, variant] of [[24, 0], [72, 1]]) {
      const label = 'bmf-' + target + '-frame-' + frame
      ff(['-y', '-i', mp4, '-vf', 'select=eq(n\\,' + frame + ')', '-frames:v', '1', path(label + '.png')])
      measure(label, ff(['-i', path(label + '.png'), '-frames:v', '1', '-f', 'rawvideo', '-pix_fmt', 'rgb24', '-']),
        target, variant, target === 320 ? .72 : .85)
    }
  }
  if (inline) {
    await chooseRange('inherit')
    const cleared = await preview()
    assert.deepEqual(cleared.body.operations[0].parameters.fontRuns, [])
    const accepted = response('apply')
    await button('Confirm subtitle').click()
    const r = await accepted
    assert.equal(r.status(), 201, await r.text())
    await waitRevision((await r.json()).newRevisionId)
    await page.reload()
    await waitRevision((await cp('cleared')).head)
    await frame('cleared-inherited', 3, 'second-cue', 0)
  }
  report.status = 'PASS'
  save()
}
