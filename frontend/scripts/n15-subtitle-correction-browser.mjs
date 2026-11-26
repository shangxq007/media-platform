import assert from 'node:assert/strict'
import { execFileSync } from 'node:child_process'
import { writeFileSync } from 'node:fs'
import { join } from 'node:path'
import { exportRevision } from './formal-export.mjs'

export async function runSubtitleCorrection(ctx, reviewedParent) {
  const { page, outputRoot, url, endpoint, post, cp, waitRevision, addClip, upload } = ctx
  const ffmpeg = args => execFileSync('/usr/bin/ffmpeg',['-v','error',...args])
  const saved = (name, data) => writeFileSync(join(outputRoot,name),JSON.stringify(data,null,2)+'\n')
  const paths = name => join(outputRoot,name)
  const section = page.getByRole('region',{name:'Subtitle edit',exact:true})
  const button = name => section.getByRole('button',{name,exact:true})
  const input = name => section.getByLabel(name,{exact:true})
  const seek = page.getByLabel('Timeline preview seek')
  const canvas = page.getByLabel('Timeline preview canvas')
  const previewRegion = page.getByRole('region',{name:'Timeline media preview',exact:true})
  const report = { mode: reviewedParent ? 'reviewed-parent' : 'candidate', browser: page.context().browser().version(),
    draft: {}, exports: {} }
  const save = () => saved('correction-result.json', report)
  const editorRequests = []
  page.on('request', request => {
    if (request.url() !== endpoint+'/preview' && request.url() !== endpoint+'/apply') return
    editorRequests.push({ method: request.method(), path: new URL(request.url()).pathname,
      body: request.postData(), ifMatch: request.headers()['if-match'] ?? null })
  })
  const duration = async max => page.waitForFunction(value =>
    Number(document.querySelector('[aria-label="Timeline preview seek"]')?.max) === value, max)
  async function at(seconds) {
    await seek.evaluate((input, at) => {
      Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set.call(input,String(at))
      input.dispatchEvent(new Event('input',{ bubbles:true }))
      input.dispatchEvent(new Event('change',{ bubbles:true }))
    },seconds)
  }
  async function state(label, limit, time, visible, cueId) {
    const data = await previewRegion.evaluate(node => ({
      seekLimit: Number(node.querySelector('input[type=range]').max),
      nativeCursor: Number(node.querySelector('input[type=range]').value),
      actualCursor: Number(node.dataset.previewTime),
      cueIds: (node.dataset.activeSubtitles || '').split(',').filter(Boolean), status: node.dataset.previewStatus,
      revision: node.dataset.previewRevision,
    }))
    report.draft[label] = data
    await canvas.screenshot({ path: paths(label+'.png') })
    save()
    assert.equal(data.seekLimit,limit,label)
    assert.equal(data.actualCursor,time,label)
    assert.equal(data.nativeCursor,time,label)
    assert.equal(data.cueIds.includes(cueId),visible,label)
    return data
  }
  async function makeVideo(file, seconds) {
    ffmpeg(['-y','-f','lavfi','-i','color=0x142850:s=320x240:r=24','-t',String(seconds),
      '-c:v','libopenh264','-pix_fmt','yuv420p',paths(file)])
    await upload(file)
  }
  const profile = 'noto-sc-color-emoji-v1'
  const authored = (cueId, text, start, end, keys = []) => ({ cueId,text,start,end,keys,fontProfile:profile,width:200,fontSize:24 })
  async function commitCue(parameters, tag) {
    const base = await cp(tag+'-base')
    const head = await ctx.syncHead()
    assert.equal(head.revisionId,base.head)
    const body={batchId:ctx.project+'-'+tag,baseRevisionId:head.revisionId,baseContentHash:head.contentHash,
      operations:[{ definitionId:'timeline.subtitle.cue.set',parameters }]}
    const preview=await post('preview',body)
    assert.equal(preview.status(),200,await preview.text())
    const applied=await post('apply',body,(await preview.json()).batchDigest)
    assert.equal(applied.status(),201,await applied.text())
    const receipt=await applied.json()
    const checkpoint=await cp(tag+'-applied')
    assert.equal(checkpoint.head,receipt.newRevisionId)
    assert.equal(checkpoint.rows,base.rows+1)
    return checkpoint
  }
  async function stageDraft(start,end,text) {
    await input('Subtitle text').fill(text)
    await input('Subtitle start (timeline seconds)').fill(start)
    await input('Subtitle end (timeline seconds)').fill(end)
    const response = page.waitForResponse(r=>r.url()===endpoint+'/preview')
    await button('Preview subtitle').click()
    const r=await response
    assert.equal(r.status(),200,await r.text())
    await button('Confirm subtitle').waitFor()
    return r.request().postDataJSON().operations[0].parameters.cueId
  }

  await makeVideo('short-correction.mp4',4)
  await addClip('short-correction.mp4','track-1','0','4','4')
  const short = await cp('short')
  await page.goto(url); await waitRevision(short.head); await duration(4)
  const newId = await stageDraft('5','7','New subtitle at six')
  assert.deepEqual(await cp('new-preview'),short)
  const draftMax = reviewedParent ? 4 : 7
  await duration(draftMax)
  await at(6)
  const visible = async cueId => page.waitForFunction(id =>
    (document.querySelector('[data-preview-revision]')?.dataset.activeSubtitles || '').split(',').includes(id),cueId)
  if (!reviewedParent) await visible(newId)
  await state('new-seek',draftMax,reviewedParent ? 4 : 6,!reviewedParent,newId)
  if (!reviewedParent) {
    await page.getByRole('button',{name:'Play Timeline',exact:true}).click()
    await page.waitForFunction(() => {
      const host = document.querySelector('[data-preview-revision]')
      const t = Number(host?.dataset.previewTime)
      return t > 6.05 && t < 7 && host?.dataset.previewStatus === 'PLAYING' && !!host.dataset.activeSubtitles
    })
    report.draft.playedBeyondSavedEnd = await previewRegion.getAttribute('data-preview-time')
    await page.waitForFunction(() => {
      const host = document.querySelector('[data-preview-revision]')
      return Number(host?.dataset.previewTime) === 7 && host?.dataset.previewStatus === 'PAUSED'
    })
    report.draft.playbackEnd = 7
    await at(6)
    await visible(newId)
  }
  await button('Cancel subtitle').click()
  await duration(4)
  await state('new-cancel',4,4,false,newId)
  assert.deepEqual(await cp('new-cancel'),short)
  assert.equal(editorRequests.filter(r=>r.path.endsWith('/apply')).length,0)

  const initial = await commitCue(authored('existing-correction-cue','Saved subtitle','1/1','3/1'),'existing')
  await page.reload(); await waitRevision(initial.head); await duration(4)
  await input('Subtitle cue').selectOption('existing-correction-cue')
  const extended = await stageDraft('1','7','Extended subtitle stays editable')
  assert.equal(extended,'existing-correction-cue')
  assert.deepEqual(await cp('extend-preview'),initial)
  await duration(draftMax)
  await at(6)
  await visible(extended)
  await state('extend-seek',draftMax,reviewedParent ? 4 : 6,true,extended)
  await button('Cancel subtitle').click()
  await duration(4)
  await state('extend-cancel',4,4,false,extended)
  assert.deepEqual(await cp('extend-cancel'),initial)
  assert.equal(editorRequests.filter(r=>r.path.endsWith('/apply')).length,0)

  await stageDraft('1','7','Extended subtitle stays editable')
  const receiptResponse = page.waitForResponse(r=>r.url()===endpoint+'/apply')
  await button('Confirm subtitle').click()
  const applied=await receiptResponse
  assert.equal(applied.status(),201,await applied.text())
  const receipt=await applied.json()
  await waitRevision(receipt.newRevisionId)
  const extendedSaved=await cp('extend-applied')
  assert.equal(extendedSaved.rows,initial.rows+1)
  assert.equal(extendedSaved.commands,initial.commands+1)
  assert.equal(extendedSaved.head,receipt.newRevisionId)
  assert.equal(receipt.parentRevisionId,initial.head)
  await page.reload();await waitRevision(extendedSaved.head); await duration(7)
  await input('Subtitle cue').selectOption(extended)
  assert.equal(await input('Subtitle text').inputValue(),'Extended subtitle stays editable')
  await at(6);await visible(extended)
  await state('extend-reopened',7,6,true,extended)
  assert.deepEqual(await cp('extend-reopened'),extendedSaved)
  assert.deepEqual(editorRequests.map(r=>r.path.split('/').at(-1)),['preview','preview','preview','apply'])
  report.draft.requests = [...editorRequests]
  report.draft.receipt = receipt
  save()

  await makeVideo('long-correction.mp4',56)
  // addClip's cursor in the DB fixture precedes the subtitle writes, so authorize against
  // the freshly read HEAD through the canonical batch instead of guessing a base.
  await ctx.syncHead()
  await addClip('long-correction.mp4','track-1','4','60','56')
  const text = 'Long fade witness'
  const cue = authored('long-fade-correction-cue',text,'57/1','60/1',[
    { time:'1/1',xMicros:100000,yMicros:400000,opacityMicros:1000000 },
    { time:'71/24',xMicros:100000,yMicros:400000,opacityMicros:100000 },
  ])
  const long = await commitCue(cue,'long')
  await page.reload();await waitRevision(long.head); await duration(60)
  const rates=[[24,1],[24000,1001]]
  const alphaAt = t => Math.max(.1,1-.9*Math.max(0,t-58)/(47/24))
  const cases = rates.map(([num,den]) => {
    const frameCount = Math.ceil(60*num/den)
    const indices = [58,59,59.5].map(seconds=>Math.ceil(seconds*num/den)).concat(frameCount-1)
    return {num,den,frameCount,points:indices.map((frame,i)=>({ label:['onset','midpoint','late','last'][i],
      frame,timestamp:frame*den+'/'+num,seconds:frame*den/num,expectedAlpha:alphaAt(frame*den/num) }))}
  })
  saved('correction-expected.json',{text,cue,extent:['0/1','60/1'],cases,tolerance:{chrome:.04,mp4:.075},control:[10,10],anchor:[32,96]})

  const referenceResponse=await page.request.post(ctx.base+ctx.scope+'/timeline-operations/subtitle-layout',{
    headers:{...ctx.headers,Accept:'image/png'},data:{text,width:200,fontSize:24,fontProfile:profile},
  })
  assert.equal(referenceResponse.status(),200,await referenceResponse.text())
  const reference=await referenceResponse.body()
  writeFileSync(paths('fade-reference.png'),reference)
  const rgba=ffmpeg(['-i',paths('fade-reference.png'),'-frames:v','1','-pix_fmt','rgba','-f','rawvideo','-'])
  const mask=[]
  for(let n=0;n<rgba.length/4;n++)
    if(rgba.subarray(4*n,4*n+4).every(c=>c===255))mask.push([n%200+32,Math.floor(n/200)+96])
  assert(mask.length>=40)
  function measure(rgb) {
    assert.equal(rgb.length,320*240*3)
    const control=[0,1,2].map(c=>rgb[(10*320+10)*3+c])
    assert(control.every((v,c)=>Math.abs(v-[20,40,80][c])<=12),JSON.stringify(control))
    const samples=mask.map(([x,y])=>(rgb[(y*320+x)*3]-control[0])/(255-control[0])).sort((a,b)=>a-b)
    return {control,maskPixels:mask.length,alpha:samples[Math.floor(samples.length/2)]}
  }
  for(const item of cases) {
    const label = 'n15-f1-'+item.num+'-'+item.den
    const {path,...artifact}=await exportRevision(ctx,long.head,0,60000,label,320,240,ctx.base,item.num,item.den)
    const probe=JSON.parse(execFileSync('/usr/bin/ffprobe',['-v','error','-select_streams','v:0','-count_frames',
      '-show_entries','stream=avg_frame_rate,r_frame_rate,nb_read_frames,time_base','-of','json',path]).toString()).streams[0]
    const frameTimes=JSON.parse(execFileSync('/usr/bin/ffprobe',['-v','error','-select_streams','v:0','-show_frames',
      '-show_entries','frame=pts,pts_time','-of','json',path]).toString()).frames
    const entry={...artifact,probe,frames:[],expectedFrameCount:item.frameCount}
    if (!reviewedParent) {
      assert.equal(frameTimes.length,item.frameCount)
      assert.equal(probe.avg_frame_rate,item.num+'/'+item.den)
    }
    report.exports[label]=entry
    for(const expectedPoint of item.points) {
      const frame = Math.min(expectedPoint.frame, frameTimes.length-1)
      const point = frame === expectedPoint.frame ? expectedPoint : { ...expectedPoint, frame,
        timestamp:frame*item.den+'/'+item.num, seconds:frame*item.den/item.num,
        expectedAlpha:alphaAt(frame*item.den/item.num) }
      await at(point.seconds)
      await visible(cue.cueId)
      const chromePath = paths(label+'-chrome-'+point.label+'.png')
      const decodedPath = paths(label+'-mp4-'+point.label+'.png')
      const image = await page.waitForFunction(({time,cueId}) => {
        const host = document.querySelector('[data-preview-revision]')
        if (Math.abs(Number(host?.dataset.previewTime)-time) > .001
            || !(host?.dataset.activeSubtitles || '').split(',').includes(cueId)) return false
        const canvas = host.querySelector('canvas')
        const pixel = canvas.getContext('2d').getImageData(10,10,1,1).data
        return [20,40,80].every((v,i) => Math.abs(v-pixel[i])<=12) && canvas.toDataURL()
      }, {time:point.seconds,cueId:cue.cueId})
      writeFileSync(chromePath,Buffer.from((await image.jsonValue()).split(',')[1],'base64'))
      const select='select=eq(n\\,'+point.frame+')'
      ffmpeg(['-y','-i',path,'-vf',select,'-frames:v','1',decodedPath])
      const raw = f=>ffmpeg(['-i',f,'-frames:v','1','-pix_fmt','rgb24','-f','rawvideo','-'])
      const chrome=measure(raw(chromePath)), mp4=measure(raw(decodedPath))
      const decodedTimestamp=frameTimes[point.frame]
      assert(Math.abs(Number(decodedTimestamp.pts_time)-point.seconds)<.000002,
        JSON.stringify({decodedTimestamp,point}))
      entry.frames.push({...point,decodedTimestamp,chrome,mp4,chromeCursor:Number(await previewRegion.getAttribute('data-preview-time'))})
      save()
      assert(Math.abs(chrome.alpha-point.expectedAlpha)<.04,JSON.stringify(entry.frames.at(-1)))
      if (!reviewedParent) assert(Math.abs(mp4.alpha-point.expectedAlpha)<.075,JSON.stringify(entry.frames.at(-1)))
    }
  }
  report.status = reviewedParent ? 'OBSERVED' : 'PASS'
  save()
}
