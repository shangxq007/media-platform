import { exportRevision } from './formal-export.mjs'
import assert from 'node:assert/strict'
import { execFileSync } from 'node:child_process'
import { createHash } from 'node:crypto'
import { existsSync, readFileSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import { chromium } from 'playwright'

const [appRoot, outputRoot, mode] = process.argv.slice(2)
const dissolve = mode === '--dissolve'
assert(appRoot && outputRoot)
const base = 'http://127.0.0.1:18080'
const tenant = 'tenant-fr-e2e'
const workspace = 'workspace-fr-e2e'
const token = readFileSync(join(appRoot, 'token'), 'utf8').trim()
const headers = { Authorization: `Bearer ${token}` }
const profile = JSON.parse(Buffer.from(token.split('.')[1], 'base64url').toString())
const browser = await chromium.launch({ executablePath: '/usr/bin/google-chrome', headless: true,
  args: ['--no-sandbox', '--disable-dev-shm-usage'] })
const context = await browser.newContext({ acceptDownloads: true, viewport: { width: 1600, height: 1100 } })
const page = await context.newPage()
page.setDefaultTimeout(30000)
const report = { browser: browser.version(), status: 'RUNNING', frames: {}, requests: [] }
await context.addInitScript(({ access, profile }) => {
  const user = { access_token: access, token_type: 'Bearer', profile, expires_at: profile.exp,
    scope: 'openid profile email', session_state: profile.sid }
  for (const issuer of ['http://127.0.0.1:18081', 'http://127.0.0.1:18081/']) {
    sessionStorage.setItem(`oidc.user:${issuer}:f8-local`, JSON.stringify(user))
  }
}, { access: token, profile })

async function run() {
  const created = await page.request.post(`${base}/api/identity/tenants/${tenant}/projects`, {
    headers, data: { name: dissolve ? 'N13 shared cut' : 'N12 opacity keys', workspaceId: workspace },
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
  const upload = async file => {
    const response = await page.request.post(base + scope + '/source-media', {
      headers: { ...headers, 'Idempotency-Key': `audio-source-${file}` }, multipart: { file: { name: file, mimeType: file.endsWith('.wav') ? 'audio/wav' : 'video/mp4',
        buffer: readFileSync(join(outputRoot, file)) } },
    })
    assert.equal(response.status(), 201, await response.text())
    return response.json()
  }
  const sources = {}
  const addClip = async (file, trackId, start, end, sourceEnd = null) => {
    const source = sources[file]
    const stream = source.streams.find(item => item.kind === (file.endsWith('.wav') ? 'AUDIO' : 'VIDEO'))
    const clipId = file.split('.')[0]
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
  writeFileSync(join(outputRoot, 'project-id'), project)
  const cp = async name => {
    writeFileSync(join(outputRoot,'checkpoint-'+name),'')
    for (let i=0;i<300;i++) {
      const path=join(outputRoot,'database-'+name+'.json')
      if(existsSync(path)) return JSON.parse(readFileSync(path))
      await new Promise(resolve=>setTimeout(resolve,100))
    }
    throw Error('Missing PostgreSQL checkpoint '+name)
  }
  const waitRevision = async id => page.waitForFunction(id => {
    const view=document.querySelector('[data-preview-revision]')
    return view?.dataset.previewRevision===id && ['READY','PAUSED'].includes(view.dataset.previewStatus)
  },id)
  const url=base+'/w/'+workspace+'/projects/'+project+'/edit'
  const endpoint = base + scope + '/timeline-operations/batch/v2'
  const post = async (method, body, digest) => page.request.post(endpoint+'/'+method,{
    headers: digest ? { ...headers, 'If-Match': '"'+digest+'"' } : headers, data: body })
  if (dissolve) {
    const { runDissolve } = await import('./n13-cross-dissolve-browser.mjs')
    const context = { page, base, scope, project, headers, outputRoot, url, endpoint, post, cp, waitRevision, addTrack, addClip,
      head: () => current, upload: async file => { sources[file] = await upload(file) } }
    await runDissolve(context)
    return
  }
  await addTrack('VIDEO','top','n12-top')
  for (const [color, duration] of [['blue',7],['red',4]]) {
    const file = color + '.mp4'
    execFileSync('/usr/bin/ffmpeg', ['-v','error','-y','-f','lavfi','-i','color=c='+color+':s=320x240:r=24','-t',String(duration),
      '-c:v','libopenh264','-pix_fmt','yuv420p',join(outputRoot,file)])
    sources[file] = await upload(file)
  }
  await addClip('blue.mp4','track-1','0','7','7')
  const clipId = await addClip('red.mp4','top','2','6','4')
  report.foreground = clipId
  const setup = { batchId:'n12-static', baseRevisionId:current.revisionId, baseContentHash:current.contentHash,operations:[
    { definitionId:'timeline.clip.transform.set',target:{clipId},parameters:{format:'composition-transform-micro-v1',
      positionXMicros:0,positionYMicros:0,scaleXMicros:1000000,scaleYMicros:1000000,rotationMicroDegrees:0,
      cropLeftMicros:0,cropRightMicros:1000000,cropTopMicros:0,cropBottomMicros:1000000,opacityMicros:250000}},
    { definitionId:'timeline.clip.effect.set',target:{clipId},parameters:{brightness:0,contrast:1,saturation:1.1,grayscale:false,
      fadeInSeconds:0,fadeOutSeconds:0,enabled:true}}]}
  const plan = await post('preview',setup)
  assert.equal(plan.status(),200,await plan.text())
  const receipt = await post('apply',setup,(await plan.json()).batchDigest)
  assert.equal(receipt.status(),201,await receipt.text())
  const root = (await receipt.json()).newRevisionId
  const before = await cp('base')
  await page.goto(url)
  await waitRevision(root)
  const section=page.getByRole('region',{name:'Opacity edit',exact:true})
  const choose=() => section.getByLabel('Opacity clip').selectOption(clipId)
  const inputs=['First key time (clip seconds)','First opacity (%)','Second key time (clip seconds)','Second opacity (%)']
  const fill=async (vals) => { await choose(); for(let i=0;i<4;i++) await section.getByLabel(inputs[i],{exact:true}).fill(vals[i]) }
  const btn=s=>section.getByRole('button',{name:s,exact:true})
  const preview=async () => {
    const next=page.waitForResponse(r=>r.url()===endpoint+'/preview')
    await btn('Preview opacity').click()
    const r=await next
    assert.equal(r.status(),200,await r.text())
    await btn('Confirm opacity').waitFor()
    return { body:r.request().postDataJSON(),digest:(await r.json()).batchDigest }
  }
  const checkpointFrame=async(stage,at,wanted) => {
    await page.getByLabel('Timeline preview seek').evaluate((input,at)=>{
      Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set.call(input,String(at))
      input.dispatchEvent(new Event('input',{bubbles:true}));input.dispatchEvent(new Event('change',{bubbles:true}))
    },at)
    const rgb=await page.waitForFunction(({at,wanted})=>{
      const view=document.querySelector('[data-preview-revision]')
      if(Number(view.dataset.previewTime)!==at) return false
      const c=view.querySelector('canvas'), pixels=c.getContext('2d').getImageData(160,120,2,2).data
      const rgb=[0,1,2].map(k=>(pixels[k]+pixels[k+4]+pixels[k+8]+pixels[k+12])/4)
      return rgb.every((v,i)=>Math.abs(v-wanted[i])<=8) && rgb
    },{at,wanted})
    const actual=await rgb.jsonValue()
    await page.getByLabel('Timeline preview canvas').screenshot({path:join(outputRoot,stage+'-'+at+'.png')})
    return {at,expected:wanted,observed:actual}
  }
  const points=[[2.5,[0,0,255]],[3,[0,0,255]],[4,[128,0,128]],[5,[255,0,0]],[5.5,[255,0,0]],[6.5,[0,0,255]]]
  const sample=async stage=>{
    const rows=[]
    for(const [at,wanted] of points)rows.push(await checkpointFrame(stage,at,wanted))
    report.frames[stage]=rows
  }
  report.frames.static=await checkpointFrame('static',4,[64,0,191])
  await fill(['1','0','3','100'])
  await preview()
  await sample('draft')
  await btn('Cancel opacity').click()
  await checkpointFrame('cancel',4,[64,0,191])
  assert.deepEqual(await cp('draft'),before)
  const planned=await preview()
  for (const parameters of [{firstValue:-0.1},{lastValue:1.1},{lastTime:'3'},{lastTime:'6'}]) {
    const body=structuredClone(planned.body)
    Object.assign(body.operations[0].parameters,parameters)
    const invalid=await post('preview',body)
    assert([400,422].includes(invalid.status()),await invalid.text())
  }
  const attempts=[]
  await page.route('**'+new URL(endpoint).pathname+'/apply',async route=>{
    const req=route.request(),response=await route.fetch()
    attempts.push({body:req.postData(),target:new URL(req.url()).pathname,ifMatch:req.headers()['if-match'],status:response.status(),receipt:await response.json()})
    if(attempts.length===1)await route.abort('failed')
    else await route.fulfill({response})
  })
  await btn('Confirm opacity').click()
  const retry=btn('Retry confirmed opacity');await retry.waitFor()
  const committed=await cp('accepted')
  await page.route('**/timeline/branches/main',route=>route.abort('failed'),{times:1})
  await retry.click()
  await section.getByText('Opacity accepted at revision '+committed.head+'. Reload HEAD to refresh the editor.').waitFor()
  await page.getByRole('button',{name:'Reload HEAD',exact:true}).click()
  await waitRevision(committed.head)
  assert.deepEqual(await cp('recovery'),committed)
  await page.unroute('**'+new URL(endpoint).pathname+'/apply')
  assert.deepEqual(attempts.map(a=>a.status),[201,200])
  for(const k of ['body','ifMatch','target'])assert.equal(attempts[0][k],attempts[1][k])
  assert.deepEqual(JSON.parse(attempts[0].body),planned.body)
  assert.equal(attempts[0].ifMatch,'"'+planned.digest+'"')
  assert(attempts.every(a=>a.receipt.newRevisionId===committed.head))
  report.replay=attempts
  await sample('accepted')
  await page.reload()
  await waitRevision(committed.head);await choose()
  assert.deepEqual(await Promise.all(inputs.map(k=>section.getByLabel(k,{exact:true}).inputValue())),['1/1','0','3/1','100'])
  await sample('reopened')
  const delivery=await exportRevision({page,base,scope,project,headers,outputRoot},committed.head,0,7000,'n12-opacity')
  const {path,...artifact}=delivery
  report.export=artifact
  report.frames.export=[]
  for(const [at,expected] of points) {
    const bytes=execFileSync('/usr/bin/ffmpeg',['-v','error','-i',path,'-ss',String(at),'-frames:v','1','-vf','crop=2:2:160:120,format=rgb24','-f','rawvideo','-'])
    assert.equal(bytes.length,12)
    const observed=[0,1,2].map(i=>(bytes[i]+bytes[i+3]+bytes[i+6]+bytes[i+9])/4)
    report.frames.export.push({at,expected,observed})
    writeFileSync(join(outputRoot,'opacity-result.json'),JSON.stringify(report,null,2))
    assert(observed.every((v,i)=>Math.abs(v-expected[i])<=12),JSON.stringify({at,expected,observed}))
  }
  await fill(['1','25','3','75'])
  const old=await preview(), pending=page.waitForResponse(r=>r.url()===endpoint+'/apply')
  await btn('Confirm opacity').click()
  const edited=(await (await pending).json()).newRevisionId
  await waitRevision(edited);await cp('edited')
  for(const direction of ['Undo','Redo']) {
    const next=page.waitForResponse(r=>r.url()===endpoint+'/apply')
    await page.getByRole('button',{name:direction+' opacity',exact:true}).click()
    const r=await next;assert.equal(r.status(),201,await r.text())
    await waitRevision((await r.json()).newRevisionId);await cp(direction.toLowerCase())
  }
  const stale={...old.body,batchId:'n12-stale'}
  const staleResult=await post('apply',stale,old.digest)
  assert.equal(staleResult.status(),409,await staleResult.text())
  report.stale=await staleResult.json();assert.equal(report.stale.errorCode,'STALE_BASE_REVISION')
  await cp('stale')
  await choose();await preview()
  const zero=page.waitForResponse(r=>r.url()===endpoint+'/apply')
  await btn('Confirm opacity').click()
  assert.equal((await (await zero).json()).status,'NO_OP')
  await cp('noop')
  report.status='PASS'
  writeFileSync(join(outputRoot,'opacity-result.json'),JSON.stringify(report,null,2))
}
try { await run() } finally { await browser.close() }
