import assert from 'node:assert/strict'
import { execFileSync } from 'node:child_process'
import { writeFileSync } from 'node:fs'
import { join } from 'node:path'
import { exportRevision } from './formal-export.mjs'

const bg = [24,36,64]
const ff = args => execFileSync('/usr/bin/ffmpeg',['-v','error',...args],{maxBuffer:32*1024*1024})
const filename = (root,name) => join(root,name)
function capturePng(raw,width,height,path) {
  execFileSync('/usr/bin/ffmpeg',['-v','error','-y','-f','rawvideo','-pixel_format','rgb24',
    '-video_size',width+'x'+height,'-i','pipe:0','-frames:v','1',path],{ input:raw })
}

export async function runMotion(ctx, mode) {
  const {page,outputRoot,url,endpoint,cp,waitRevision,addTrack,addClip,upload}=ctx
  const hd=mode==='--n16-hd'
  const subtitle=mode==='--n16-subtitle'
  const file=label=>filename(outputRoot,label)
  const save=(name,out)=>writeFileSync(file(name),JSON.stringify(out,null,2)+'\n')
  const title='春天来了 🌻\uFE0F\nSpring is here'
  async function material(name,draw) {
    ff(['-y','-f','lavfi','-i','color=0x182440:s=320x240:r=24','-t','3.5',
      ...(draw?['-vf','drawbox=x=42:y=43:w=18:h=51:color=white:t=fill,drawbox=x=60:y=77:w=32:h=17:color=white:t=fill']:[]),
      '-c:v','libopenh264','-pix_fmt','yuv420p',file(name)])
    await upload(name)
  }
  await material('background.mp4',false)
  await addClip('background.mp4','track-1','0','7/2','7/2')
  if (!subtitle) {
    await addTrack('VIDEO','marker-track','n16-marker')
    await material('marker.mp4',true)
    await addClip('marker.mp4','marker-track','0','7/2','7/2')
  }
  const base=await cp('base')
  await page.goto(url);await waitRevision(base.head)
  const region=page.getByRole('region',{name:subtitle?'Subtitle edit':'Position edit',exact:true})
  const input=name=>region.getByLabel(name,{exact:true})
  const button=name=>region.getByRole('button',{name,exact:true})
  const edit=subtitle?'subtitle':'position'
  if (subtitle) {
    await input('Subtitle text').fill(title)
    await input('Subtitle start (timeline seconds)').fill('0')
    await input('Subtitle end (timeline seconds)').fill('7/2')
    await input('Subtitle width (at 320 px)').fill('200')
    await input('Animate whole subtitle').check()
    for (const n of [1,2]) await input('Subtitle key '+n+' opacity (%)').fill('100')
  } else {
    await region.getByLabel('Position clip').selectOption('marker')
  }
  const values=[[62500,100000],hd?[68750,104167]:[93750,120833]]
  for(let i=0;i<2;i++) {
    const prefix=subtitle?'Subtitle key '+(i+1):'Key '+(i+1)
    await input(prefix+(subtitle?' time (cue seconds)':' time (clip seconds)')).fill(String(i+1))
    await input(prefix+' X (%)').fill(String(values[i][0]/10000))
    await input(prefix+' Y (%)').fill(String(values[i][1]/10000))
  }
  let next=page.waitForResponse(r=>r.url()===endpoint+'/preview')
  await button('Preview '+edit).click()
  const preview=await next
  assert.equal(preview.status(),200,await preview.text())
  await button('Confirm '+edit).waitFor()
  assert.deepEqual(await cp('preview'),base)
  const request=preview.request().postDataJSON()
  const expected={mode,width:hd?1600:320,height:hd?1200:240,fps:24,subject:subtitle?'subtitle':'clip',
    values,keyTimes:['1/1','2/1'],text:subtitle?title:null,request,
    frames:[6,12,18,...Array.from({length:25},(_,i)=>24+i),54,60,66].map(frame=>{
      const t=Math.min(1,Math.max(0,(frame-24)/24))
      return {frame,time:frame/24,point:values[0].map((p,a)=>p+(values[1][a]-p)*t),
        group:frame<24?'initialHold':frame>48?'endHold':'trajectory'}
    })}
  save('authored.json',expected) // The unchanged authored trajectory precedes all sampling.
  next=page.waitForResponse(r=>r.url()===endpoint+'/apply')
  await button('Confirm '+edit).click()
  const response=await next
  assert.equal(response.status(),201,await response.text())
  const receipt=await response.json()
  await waitRevision(receipt.newRevisionId)
  assert.equal((await cp('accepted')).rows,base.rows+1)
  await page.reload()
  await waitRevision(receipt.newRevisionId)
  assert.equal((await cp('reopened')).head,receipt.newRevisionId)

  async function browserFrame(frame) {
    const t=frame.time
    await page.getByLabel('Timeline preview seek').evaluate((el,t)=>{
      Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set.call(el,String(t))
      el.dispatchEvent(new Event('input',{bubbles:true}));el.dispatchEvent(new Event('change',{bubbles:true}))
    },t)
    const image=await page.waitForFunction(({t,subtitle,bg})=>{
      const el=document.querySelector('[data-preview-revision]')
      if(Math.abs(Number(el?.dataset.previewTime)-t)>.001 || (subtitle && !el.dataset.activeSubtitles))return false
      const canvas=el.querySelector('canvas'), c=canvas.getContext('2d')
      const px=c.getImageData(10,10,1,1).data
      if(!bg.every((n,i)=>Math.abs(px[i]-n)<12))return false
      // The marker source is constant. Wait for a presented source pixel before the first capture.
      if(!subtitle && c.getImageData(76,76,1,1).data[0]<150)return false
      return {png:canvas.toDataURL(),time:Number(el.dataset.previewTime),width:canvas.width,height:canvas.height}
    },{t,subtitle,bg})
    const result=await image.jsonValue()
    if(result.width!==320||result.height!==240) throw Error('Unexpected Chrome preview raster')
    const {png,...timing}=result
    writeFileSync(file('browser-'+frame.frame+'.png'),Buffer.from(png.split(',')[1],'base64'))
    return timing
  }
  const browserRows=[]
  for(const f of expected.frames)browserRows.push({frame:f.frame,...await browserFrame(f)})
  // Observe actual playback separately from the seeked-frame spatial measurements.
  await page.getByLabel('Timeline preview seek').evaluate(el=>{
    Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set.call(el,'0.8')
    el.dispatchEvent(new Event('input',{bubbles:true}));el.dispatchEvent(new Event('change',{bubbles:true}))
  })
  await page.evaluate(()=>{
    const el=document.querySelector('[data-preview-revision]')
    const canvas=el.querySelector('canvas'), c=canvas.getContext('2d')
    window.n16Presentation=[]
    const ob=new MutationObserver(()=>{
      const t=Number(el.dataset.previewTime)
      if(el.dataset.previewStatus==='PLAYING'&&t>=.9&&t<=2.15) {
        const mark=c.getImageData(100,76,1,1).data
        window.n16Presentation.push([t,performance.now(),mark[0]])
      }
      if (t>2.15)ob.disconnect()
    })
    ob.observe(el,{attributes:true,attributeFilter:['data-preview-time']})
  })
  await page.getByRole('button',{name:'Play Timeline',exact:true}).click()
  await page.waitForFunction(()=>Number(document.querySelector('[data-preview-revision]')?.dataset.previewTime)>2.18)
  const presentation=await page.evaluate(()=>window.n16Presentation)
  await page.getByRole('button',{name:'Pause Timeline',exact:true}).click()
  const renderStart=performance.now()
  const {path,...artifact}=await exportRevision(ctx,receipt.newRevisionId,0,3500,'bmf-'+expected.subject+(hd?'-hd':''),expected.width,expected.height)
  const exportWallMs=performance.now()-renderStart
  const probes=JSON.parse(execFileSync('/usr/bin/ffprobe',['-v','error','-select_streams','v:0','-show_frames',
    '-show_entries','frame=pts,pts_time','-of','json',path])).frames
  assert.equal(probes.length,84)
  const selected=expected.frames.map(f=>'eq(n\\,'+f.frame+')').join('+')
  // Each raw frame is extracted directly from the formal MP4 before PNG conversion.
  const crop=subtitle?[14,16,210,106]:[48,60,84,75]
  const [x,y,w,h]=crop.map(v=>v*(hd?5:1))
  const raw=ff(['-i',path,'-vf','select='+selected+',format=rgb24,crop='+w+':'+h+':'+x+':'+y,
    '-fps_mode','passthrough','-f','rawvideo','-'])
  assert.equal(raw.length,w*h*3*expected.frames.length)
  const labels=[]
  for(const [i,f] of expected.frames.entries()) {
    capturePng(raw.subarray(i*w*h*3,(i+1)*w*h*3),w,h,file('bmf-crop-'+f.frame+'.png'))
    assert(Math.abs(Number(probes[f.frame].pts_time)-f.time)<.000002)
    labels.push({frame:f.frame,...probes[f.frame]})
  }
  save('motion-result.json',{status:'PASS',receipt,artifact,exportWallMs,browser:page.context().browser().version(),
    browserRows,presentation,exportFrames:labels,exportCrop:[x,y,w,h],bg})
}
