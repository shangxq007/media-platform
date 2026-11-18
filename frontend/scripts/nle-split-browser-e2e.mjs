import { chromium } from 'playwright'
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs'
import { join } from 'node:path'

const [appRoot, outRoot] = process.argv.slice(2)
if (!appRoot || !outRoot) throw new Error('Owned app and output paths required')
mkdirSync(outRoot, { recursive: true })
const token = readFileSync(join(appRoot, 'token'), 'utf8').trim()
const setup = JSON.parse(readFileSync(join(appRoot, 'browser-setup.json'), 'utf8'))
const payload = JSON.parse(Buffer.from(token.split('.')[1], 'base64url').toString())
const base = 'http://127.0.0.1:18080'
const browser = await chromium.launch({ executablePath: '/usr/bin/google-chrome', headless: true,
  args: ['--no-sandbox', '--disable-dev-shm-usage'] })
const context = await browser.newContext({ acceptDownloads: true, viewport: { width: 1440, height: 1000 } })
await context.addInitScript(({ access, profile }) => {
  const user = { access_token: access, token_type: 'Bearer', profile,
    expires_at: profile.exp, scope: 'openid profile email', session_state: profile.sid }
  for (const authority of ['http://127.0.0.1:18081', 'http://127.0.0.1:18081/'])
    sessionStorage.setItem(`oidc.user:${authority}:f8-local`, JSON.stringify(user))
}, { access: token, profile: payload })
const page = await context.newPage()
const requests = []
page.on('response', response => {
  const url = new URL(response.url())
  if (url.pathname.startsWith('/api/') && !url.pathname.includes('/auth/'))
    requests.push({ method: response.request().method(), path: url.pathname, status: response.status() })
})
const result = { browser: browser.version(), base, requests, stages: [], clips: null,
  splitRevision: null, movedRevision: null, ui: {} }
const stage = name => { result.stages.push(name); writeFileSync(join(outRoot, 'split-browser-progress.json'), JSON.stringify(result, null, 2)) }
const projectRoot = `/api/tenants/tenant-fr-e2e/projects/${setup.project}`
const productRoot = `/api/timeline-git/products/${setup.project}`
const authorized = { Authorization: `Bearer ${token}` }
const currentHead = async () => {
  const response = await page.request.get(`${base}${productRoot}/revisions/current`, { headers: authorized })
  if (response.status() !== 200) throw new Error(`HEAD read failed: ${response.status()}`)
  return response.json()
}
const snapshotOf = async revision => {
  const response = await page.request.get(`${base}/api/render/projects/${setup.project}/timeline/revisions/${revision}/snapshot`,
    { headers: authorized })
  if (response.status() !== 200) throw new Error(`Snapshot read failed: ${response.status()}`)
  return JSON.parse((await response.json()).canonicalTimelineJson)
}
const clipsOf = document => document.tracks.flatMap(track => track.clips ?? [])
const seconds = value => {
  const [numerator, denominator] = String(value).split('/')
  return Number(numerator) / (denominator === undefined ? 1 : Number(denominator))
}
const assertTime = (clip, field, expected) => {
  if (Math.abs(seconds(clip[field]) - expected) > 1e-9)
    throw new Error(`${clip.clipId}.${field}: ${clip[field]} != ${expected}`)
}
try {
  await page.goto(`${base}/w/workspace-fr-e2e/projects/${setup.project}/edit`, { waitUntil: 'domcontentloaded' })
  await page.getByRole('heading', { name: 'Timeline editor' }).waitFor({ timeout: 30000 })
  await page.getByText('Workspace and Project scope verified by authenticated server reads.').waitFor({ timeout: 30000 })
  const strip = page.getByRole('group', { name: 'Timeline trim' })
  await strip.waitFor({ timeout: 30000 })
  await page.getByLabel(`Timeline clip ${setup.clipId}`).waitFor({ timeout: 30000 })
  stage('NLE_PAGE_OPEN')
  const pixelsPerSecond = Number(await strip.getAttribute('data-pixels-per-second'))
  if (!pixelsPerSecond) throw new Error('Clip strip scale unavailable')

  // Split through the real editor UI at the exact timeline time 3 s.
  await page.getByLabel(`Timeline clip ${setup.clipId}`).click()
  await page.getByLabel('Clip operation').selectOption('SPLIT_CLIP')
  await page.getByLabel('Split time').fill('0')
  await page.getByLabel('Split selected clip').click()
  await page.getByText(/strictly inside/).waitFor({ timeout: 30000 })
  result.ui.boundaryRejected = true
  await page.getByLabel('Split time').fill('3')
  await page.getByLabel('Split selected clip').click()
  await page.getByText(/Clip split at 3/).waitFor({ timeout: 60000 })
  const splitHead = await currentHead()
  const splitDocument = await snapshotOf(splitHead.revisionId)
  const splitClips = clipsOf(splitDocument)
  if (splitClips.length !== 2) throw new Error(`expected two clips after split, got ${splitClips.length}`)
  const left = splitClips.find(clip => clip.clipId === setup.clipId)
  const right = splitClips.find(clip => clip.clipId !== setup.clipId)
  if (!left || !right) throw new Error('split did not preserve the left identity and create a right clip')
  if (right.clipId === setup.clipId) throw new Error('right clip reused the left identity')
  assertTime(left, 'startTime', 0); assertTime(left, 'endTime', 3)
  assertTime(left, 'trimStart', 0); assertTime(left, 'trimEnd', 3)
  assertTime(right, 'startTime', 3); assertTime(right, 'endTime', 6)
  assertTime(right, 'trimStart', 3); assertTime(right, 'trimEnd', 6)
  if (left.mediaAssetId !== right.mediaAssetId || left.artifactId !== right.artifactId)
    throw new Error('split did not preserve the source media references')
  result.splitRevision = splitHead.revisionId
  result.clips = splitClips
  result.ui.rightClipId = right.clipId
  stage('SPLIT_APPLIED_THROUGH_UI')

  // The two clips are independent: reload, both are selectable, and the right clip can be moved.
  await page.reload({ waitUntil: 'domcontentloaded' })
  await page.getByRole('heading', { name: 'Timeline editor' }).waitFor({ timeout: 30000 })
  await page.getByLabel(`Timeline clip ${setup.clipId}`).waitFor({ timeout: 30000 })
  await page.getByLabel(`Timeline clip ${right.clipId}`).waitFor({ timeout: 30000 })
  result.ui.bothClipsSelectable = true
  await page.getByLabel(`Timeline clip ${right.clipId}`).click()
  const body = page.getByLabel(`Timeline clip ${right.clipId}`)
  const box = await body.boundingBox()
  if (!box) throw new Error('split right clip has no box')
  const x = box.x + box.width / 2, y = box.y + box.height / 2
  await page.mouse.move(x, y); await page.mouse.down()
  await page.mouse.move(x + pixelsPerSecond, y, { steps: 8 }); await page.mouse.up()
  await page.getByText(/Clip moved to 4\.00s/).waitFor({ timeout: 60000 })
  const movedHead = await currentHead()
  const movedDocument = await snapshotOf(movedHead.revisionId)
  const moved = clipsOf(movedDocument).find(clip => clip.clipId === right.clipId)
  if (!moved) throw new Error('moved right clip missing from snapshot')
  assertTime(moved, 'startTime', 4); assertTime(moved, 'endTime', 7)
  assertTime(moved, 'trimStart', 3); assertTime(moved, 'trimEnd', 6)
  result.movedRevision = movedHead.revisionId
  result.movedClip = moved
  stage('RIGHT_CLIP_MOVED_THROUGH_UI')
  await page.screenshot({ path: join(outRoot, 'split-browser-final.png'), fullPage: true })
  result.status = 'PASS'
} catch (error) {
  result.status = 'FAIL'; result.error = String(error)
  result.failurePage = { url: page.url(), bodyPreview:
    await page.locator('body').innerText().then(value => value.slice(0, 800)).catch(() => '') }
  await page.screenshot({ path: join(outRoot, 'split-browser-failure.png'), fullPage: true }).catch(() => {})
  throw error
} finally {
  writeFileSync(join(outRoot, 'split-browser-result.json'), JSON.stringify(result, null, 2))
  await browser.close()
}
