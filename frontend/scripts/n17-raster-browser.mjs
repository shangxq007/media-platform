import assert from 'node:assert/strict'
import { execFileSync } from 'node:child_process'
import { writeFileSync } from 'node:fs'
import { join } from 'node:path'
import { exportRevision } from './formal-export.mjs'

export async function runRasterQuality(ctx, mode) {
  const {page,outputRoot,url,endpoint,cp,waitRevision,addClip,upload}=ctx
  const dense=mode==='--n17-dense'
  const path=name=>join(outputRoot,name)
  const save=(name,result)=>writeFileSync(path(name),JSON.stringify(result,null,2)+'\n')
  const script='人月水永 🌻\uFE0F\nSmall Latin 12 px'
  // Six full rows exercise the largest legal profile dimensions and the native
  // color strikes, while remaining valid under the N15 authoring budget.
  const denseRow='鬱永🌻🦋🦚🍉'
  const expectedText=dense?Array(6).fill(denseRow).join('\n'):script
  const ff=args=>execFileSync('/usr/bin/ffmpeg',['-v','error',...args],{maxBuffer:32*1024*1024})
  ff(['-y','-f','lavfi','-i','color=0x182440:s=320x240:r=24','-t','3.5','-c:v','libopenh264','-pix_fmt','yuv420p',path('background.mp4')])
  await upload('background.mp4')
  await addClip('background.mp4','track-1','0','7/2','7/2')
  const base=await cp('base')
  await page.goto(url);await waitRevision(base.head)
  const section=page.getByRole('region',{name:'Subtitle edit',exact:true})
  const input=name=>section.getByLabel(name,{exact:true})
  const button=name=>section.getByRole('button',{name,exact:true})
  const size=dense?44:12
  const width=dense?304:140
  await input('Subtitle text').fill(expectedText)
  await input('Subtitle start (timeline seconds)').fill('0')
  await input('Subtitle end (timeline seconds)').fill('7/2')
  await input('Subtitle width (at 320 px)').fill(String(width))
  await input('Subtitle font size (at 320 px)').fill(String(size))
  if(!dense){
    await input('Animate whole subtitle').check()
    // At 1600×1200 this is +10, +5.0004 px over one second.
    for(const [i,x,y] of [[1,'10','20'],[2,'10.625','20.4167']]){
      await input('Subtitle key '+i+' time (cue seconds)').fill(String(i))
      await input('Subtitle key '+i+' X (%)').fill(x)
      await input('Subtitle key '+i+' Y (%)').fill(y)
      await input('Subtitle key '+i+' opacity (%)').fill('100')
    }
  }
  let next=page.waitForResponse(r=>r.url()===endpoint+'/preview')
  await button('Preview subtitle').click()
  const p=await next
  assert.equal(p.status(),200,await p.text())
  await button('Confirm subtitle').waitFor()
  assert.deepEqual(await cp('preview'),base)
  const params=p.request().postDataJSON().operations[0].parameters
  assert.equal(params.text,expectedText)
  save('authored.json',{params,dense,rate:24,background:[24,36,64],baselineStage:base,
    samples:dense?[12]:[6,12,18,...Array.from({length:25},(_,i)=>24+i),54,60,66]})
  next=page.waitForResponse(r=>r.url()===endpoint+'/apply')
  await button('Confirm subtitle').click()
  const applied=await next
  assert.equal(applied.status(),201,await applied.text())
  const receipt=await applied.json()
  await waitRevision(receipt.newRevisionId)
  assert.equal((await cp('accepted')).rows,base.rows+1)
  await page.reload();await waitRevision(receipt.newRevisionId)
  assert.equal((await cp('reopened')).head,receipt.newRevisionId)
  await input('Subtitle cue').selectOption(params.cueId)
  assert.equal(await input('Subtitle text').inputValue(),expectedText)
  const controls=[24,36,64]
  const frames=dense?[12]:[6,12,18,...Array.from({length:25},(_,i)=>24+i),54,60,66]
  const chrome=[]
  for(const frame of frames){
    const t=frame/24
    await page.getByLabel('Timeline preview seek').evaluate((el,t)=>{
      Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set.call(el,String(t))
      el.dispatchEvent(new Event('input',{bubbles:true}));el.dispatchEvent(new Event('change',{bubbles:true}))
    },t)
    const result=await (await page.waitForFunction(({t,controls})=>{
      const el=document.querySelector('[data-preview-revision]')
      if(Math.abs(Number(el?.dataset.previewTime)-t)>.001 || !el.dataset.activeSubtitles)return false
      const canvas=el.querySelector('canvas'), pixels=canvas.getContext('2d').getImageData(5,5,1,1).data
      if(!controls.every((x,i)=>Math.abs(x-pixels[i])<=12))return false
      return {data:canvas.toDataURL(),time:Number(el.dataset.previewTime),width:canvas.width,height:canvas.height}
    },{t,controls})).jsonValue()
    const {data,...dimensions}=result
    assert.equal(dimensions.width,320); assert.equal(dimensions.height,240)
    writeFileSync(path('chrome-'+frame+'.png'),Buffer.from(data.split(',')[1],'base64'))
    chrome.push({frame,...dimensions})
  }
  const blocks={}
  for(const target of [320,1600,...(dense?[1920]:[])]){
    const r=await page.request.post(ctx.base+ctx.scope+'/timeline-operations/subtitle-layout',{
      headers:{...ctx.headers,Accept:'image/png'},
      data:{text:expectedText,width,fontSize:size,fontProfile:params.fontProfile,destinationWidth:target},
    })
    assert.equal(r.status(),200,await r.text())
    const bytes=await r.body()
    blocks[target]={bytes:bytes.length,width:bytes.readUInt32BE(16),height:bytes.readUInt32BE(20)}
    writeFileSync(path('block-'+target+'.png'),bytes)
  }
  const exports={}
  for(const [w,h] of (dense?[[1600,1200]]:[[320,240],[1600,1200]])){
    const started=performance.now()
    const {path:mp4,...artifact}=await exportRevision(ctx,receipt.newRevisionId,0,3500,'bmf-'+w,w,h)
    const wallMs=performance.now()-started
    const meta=JSON.parse(execFileSync('/usr/bin/ffprobe',['-v','error','-show_streams','-show_format','-of','json',mp4]))
    assert.equal(meta.streams[0].nb_frames,'84')
    assert.equal(meta.streams[0].avg_frame_rate,'24/1')
    assert.equal(meta.streams[0].width,w)
    assert.equal(meta.streams[0].height,h)
    const timing=JSON.parse(execFileSync('/usr/bin/ffprobe',['-v','error','-select_streams','v:0','-show_frames',
      '-show_entries','frame=pts,pts_time','-of','json',mp4])).frames
    for(const f of frames){
      assert(Math.abs(Number(timing[f].pts_time)-f/24)<.000002)
      const s=w/320
      // Visual-fixture crop holds the entire swept block including transparent margins.
      const crop=dense?null:[18,40,172,62].map(x=>x*s)
      const filters=['select=eq(n\\,'+f+')','format=rgb24']
      if(crop){const [x,y,cw,ch]=crop;filters.push('crop='+cw+':'+ch+':'+x+':'+y)}
      ff(['-y','-i',mp4,'-vf',filters.join(','),'-frames:v','1',path('bmf-'+w+'-frame-'+f+'.png')])
    }
    exports[w]={...artifact,wallMs,stream:{...meta.streams[0]},encoderTag:meta.format.tags?.encoder,
      pts:frames.map(frame=>({frame,...timing[frame]}))}
  }
  save('raster-result.json',{status:'PASS',receipt,blocks,chrome,exports})
}
