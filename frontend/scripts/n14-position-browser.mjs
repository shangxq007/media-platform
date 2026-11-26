import assert from 'node:assert/strict'
import { execFileSync } from 'node:child_process'
import { writeFileSync } from 'node:fs'
import { join } from 'node:path'
import { exportRevision } from './formal-export.mjs'

export async function runPosition(ctx) {
  const { page, outputRoot, url, endpoint, post, cp, waitRevision, addTrack, addClip, upload } = ctx
  for (const [file, duration, filters] of [
    ['red.mp4', 5, 'null'],
    ['blue.mp4', 5, "drawbox=x=30:y=34:w=20:h=12:color=white:t=fill:enable='gte(t,0.75)'"],
  ]) {
    execFileSync('/usr/bin/ffmpeg', ['-v','error','-y','-f','lavfi','-i','color='+file.split('.')[0]+':s=320x240:r=24',
      '-t',String(duration),'-vf',filters,'-c:v','libopenh264','-pix_fmt','yuv420p',join(outputRoot,file)])
    await upload(file)
  }
  await addClip('red.mp4','track-1','0','3','3')
  await addClip('blue.mp4','track-1','3','7','5','1')
  await addTrack('AUDIO','sound','n14-audio')
  execFileSync('/usr/bin/ffmpeg',['-v','error','-y','-f','lavfi','-i','sine=frequency=330:duration=7',join(outputRoot,'tone.wav')])
  await upload('tone.wav'); await addClip('tone.wav','sound','0','7','7')
  const clipId='blue'
  const step=(definitionId,parameters)=>({definitionId,target:{clipId},parameters})
  const readHead=async()=>{
    const r=await page.request.get(ctx.base+'/api/timeline-git/products/'+ctx.project+'/revisions/current',{headers:ctx.headers})
    assert.equal(r.status(),200)
    const head=await r.json()
    return {baseRevisionId:head.revisionId,baseContentHash:head.contentHash??head.revision.semanticContext.timelineContentDigest}
  }
  async function write(id,operations){
    const body={batchId:ctx.project+'-'+id,...await readHead(),operations}
    const p=await post('preview',body);assert.equal(p.status(),200,await p.text())
    const r=await post('apply',body,(await p.json()).batchDigest);assert.equal(r.status(),201,await r.text())
    return r.json()
  }
  await write('n14-static',[
    step('timeline.clip.transform.set',{format:'composition-transform-micro-v1',positionXMicros:100000,positionYMicros:50000,
      scaleXMicros:1000000,scaleYMicros:1000000,rotationMicroDegrees:0,cropLeftMicros:0,cropRightMicros:1000000,
      cropTopMicros:0,cropBottomMicros:1000000,opacityMicros:1000000}),
    step('timeline.clip.effect.set',{brightness:0,contrast:1,saturation:1.05,grayscale:false,fadeInSeconds:0,fadeOutSeconds:0,enabled:true}),
    {definitionId:'audio.gain.set',target:{audioTrackId:'sound',clipId:'tone'},parameters:{linear:0.7}},
  ])
  const base=await cp('base'); await page.goto(url); await waitRevision(base.head)
  const region=page.getByRole('region',{name:'Position edit',exact:true})
  const select=()=>region.getByLabel('Position clip').selectOption(clipId)
  const button=name=>region.getByRole('button',{name,exact:true})
  const keyField=(n,field)=>region.getByLabel('Key '+n+' '+field,{exact:true})
  const fill=async(n,values)=>{
    for(const [i,field] of ['time (clip seconds)','X (%)','Y (%)'].entries()) await keyField(n,field).fill(values[i])
  }
  const wire=kind=>page.waitForResponse(r=>r.url()===endpoint+'/'+kind)
  const report={browser:page.context().browser().version(),frames:{},receipts:{}}
  const save=()=>writeFileSync(join(outputRoot,'position-result.json'),JSON.stringify(report,null,2))
  // The unscaled white marker has edge-bound center (40,40); output is 320×240 at 24 fps.
  // Key times relative to clip start are 1/2 and 5/2. Source begins at 1 s, so the marker is already visible at t=3.25.
  const points=[[78,104,64],[84,104,64],[108,136,82],[132,168,100],[144,168,100]]
  const fixed=points.map(([f])=>[f,72,52])
  const combined=[[96,120,73]] // t=4, one-quarter of the placement interval and halfway through the 2 s dissolve.
  const expected={animated:points,static:fixed,composition:combined,control:{frame:60,x:10,y:10,rgb:[255,0,0]}}
  writeFileSync(join(outputRoot,'expected-frames.json'),JSON.stringify(expected,null,2))
  async function seek(at){
    await page.getByLabel('Timeline preview seek').evaluate((input,time)=>{
      Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set.call(input,String(time))
      input.dispatchEvent(new Event('input',{bubbles:true}));input.dispatchEvent(new Event('change',{bubbles:true}))
    },at)
  }
  function measure(bytes,width,height,stride,combo) {
    let xmin=width,xmax=-1,ymin=height,ymax=-1,count=0
    for(let y=0;y<height;y++)for(let x=0;x<width;x++){
      const i=(y*width+x)*stride
      const [r,g,b]=[bytes[i],bytes[i+1],bytes[i+2]]
      const marker=combo?r>220&&g>50&&b>50&&Math.abs(g-b)<28:r>210&&g>210&&b>210
      if(!marker)continue
      xmin=Math.min(xmin,x);xmax=Math.max(xmax,x);ymin=Math.min(ymin,y);ymax=Math.max(ymax,y);count++
    }
    if(count<120)return null
    const pixel=(x,y)=>Array.from(bytes.slice((y*width+x)*stride,(y*width+x)*stride+3))
    return {x:(xmin+xmax+1)/2,y:(ymin+ymax+1)/2,count,
      marker:pixel(Math.floor((xmin+xmax+1)/2),Math.floor((ymin+ymax+1)/2)),
      control:pixel(10,10),inside:pixel(200,170)}
  }
  async function sample(stage,positions,artifact=null,combo=false){
    report.frames[stage]=[]
    for(const [frame,x,y] of positions){
      const at=frame/24
      let observed
      if(artifact){
        const bytes=execFileSync('/usr/bin/ffmpeg',['-v','error','-i',artifact,'-vf','select=eq(n\\,'+frame+'),format=rgb24',
          '-frames:v','1','-f','rawvideo','-'])
        assert.equal(bytes.length,320*240*3)
        observed=measure(bytes,320,240,3,combo)
      }else{
        await seek(at)
        const handle=await page.waitForFunction(({at,x,y,combo,source})=>{
          const view=document.querySelector('[data-preview-revision]')
          if(Number(view.dataset.previewTime)!==at)return false
          const c=view.querySelector('canvas')
          if(c.width!==320||c.height!==240)return false
          const metrics=(new Function('return ('+source+')')())(c.getContext('2d').getImageData(0,0,c.width,c.height).data,c.width,c.height,4,combo)
          return metrics&&Math.abs(metrics.x-x)<=1&&Math.abs(metrics.y-y)<=1&&metrics
        },{at,x,y,combo,source:measure.toString()})
        observed=await handle.jsonValue()
        await page.getByLabel('Timeline preview canvas').screenshot({path:join(outputRoot,stage+'-'+frame+'.png')})
      }
      const row={at,frame,expected:{x,y},observed};report.frames[stage].push(row);save()
      const tolerance=artifact?2:1
      assert(observed&&Math.abs(observed.x-x)<=tolerance&&Math.abs(observed.y-y)<=tolerance,JSON.stringify(row))
      if(combo){
        for(const [name,rgb] of [['marker',[255,96,96]],['control',[255,0,0]],['inside',[159,0,96]]])
          assert(observed[name].every((channel,i)=>Math.abs(channel-rgb[i])<=12),JSON.stringify(row))
      }
    }
  }
  async function preview(){
    const next=wire('preview');await button('Preview position').click()
    const r=await next;assert.equal(r.status(),200,await r.text());await button('Confirm position').waitFor()
    return {body:r.request().postDataJSON(),digest:(await r.json()).batchDigest}
  }
  async function confirm(name,buttonName='Confirm position'){
    const next=wire('apply')
    await (buttonName==='Confirm position'?button(buttonName):page.getByRole('button',{name:buttonName,exact:true})).click()
    const res=await next;assert.equal(res.status(),201,await res.text())
    const receipt=await res.json();report.receipts[name]=receipt
    await waitRevision(receipt.newRevisionId);return cp(name)
  }
  async function deliver(name,revision,positions,combo=false){
    const {path,...record}=await exportRevision(ctx,revision,0,7000,name)
    report[name]=record
    await sample(name,positions,path,combo)
    if(name==='n14-position'){
      const bytes=execFileSync('/usr/bin/ffmpeg',['-v','error','-i',path,'-vf','select=eq(n\\,60),crop=2:2:10:10,format=rgb24',
        '-frames:v','1','-f','rawvideo','-'])
      const exported=Array.from(bytes.slice(0,3))
      await seek(2.5)
      const chrome=await page.waitForFunction(()=>{
        const view=document.querySelector('[data-preview-revision]')
        if(Number(view.dataset.previewTime)!==2.5)return false
        const data=view.querySelector('canvas').getContext('2d').getImageData(10,10,1,1).data
        return data[0]>240&&data[1]<8&&data[2]<8&&Array.from(data.slice(0,3))
      })
      report.control={...expected.control,chrome:await chrome.jsonValue(),exported}
      assert(exported[0]>240&&exported[1]<12&&exported[2]<12)
    }
  }
  await sample('static',fixed)
  await select(); await fill(1,['1/2','20','10']);await fill(2,['5/2','40','25'])
  const draft=await preview(); await sample('draft',points);await button('Cancel position').click()
  await sample('cancel',fixed.slice(2,3));assert.deepEqual(await cp('draft'),base)
  for(const [name,change] of [
    ['time',keys=>{keys[1].time='7/1'}],['value',keys=>{keys[0].xMicros=16000001}],
    ['fraction',keys=>{keys[0].yMicros=0.5}],['duplicate',keys=>{keys[1].time=keys[0].time}],
  ]){
    const body=structuredClone(draft.body);body.batchId=ctx.project+'-bad-'+name;change(body.operations[0].parameters.keys)
    for(const method of ['preview','apply']){
      const r=await post(method,body,method==='apply'?draft.digest:null)
      assert([400,422].includes(r.status()),await r.text());report[name+'-'+method]={status:r.status(),error:await r.json()}
    }
  }
  assert.deepEqual(await cp('invalid'),base)
  const plan=await preview(),attempts=[]
  const routePath='**'+new URL(endpoint).pathname+'/apply'
  await page.route(routePath,async route=>{
    const req=route.request(),res=await route.fetch()
    attempts.push({target:new URL(req.url()).pathname,body:req.postData(),ifMatch:req.headers()['if-match'],status:res.status(),receipt:await res.json()})
    if(attempts.length===1)await route.abort('failed');else await route.fulfill({response:res})
  })
  await button('Confirm position').click();await button('Retry confirmed position').waitFor()
  const accepted=await cp('accepted')
  await page.route('**/timeline/branches/main',r=>r.abort('failed'),{times:1})
  await button('Retry confirmed position').click()
  await region.getByText('Position accepted at revision '+accepted.head+'. Reload HEAD to refresh the editor.').waitFor()
  await page.getByRole('button',{name:'Reload HEAD',exact:true}).click();await waitRevision(accepted.head)
  assert.deepEqual(await cp('recovery'),accepted);await page.unroute(routePath)
  assert.deepEqual(attempts.map(a=>a.status),[201,200])
  for(const field of ['target','body','ifMatch'])assert.equal(attempts[0][field],attempts[1][field])
  assert.deepEqual(JSON.parse(attempts[0].body),plan.body);assert.equal(attempts[0].ifMatch,'"'+plan.digest+'"')
  assert(attempts.every(a=>a.receipt.newRevisionId===accepted.head));report.replay=attempts
  await page.reload();await waitRevision(accepted.head);await select()
  assert.equal(await keyField(1,'time (clip seconds)').inputValue(),'1/2');assert.equal(await keyField(2,'Y (%)').inputValue(),'25')
  await sample('reopened',points);await deliver('n14-position',accepted.head,points)
  await fill(2,['5/2','45','27.5'])
  const obsolete=await preview();await confirm('changed')
  await confirm('undo','Undo position');await confirm('redo','Redo position')
  const stale=await post('apply',{...obsolete.body,batchId:ctx.project+'-n14-stale'},obsolete.digest)
  assert.equal(stale.status(),409,await stale.text());report.stale=await stale.json();await cp('stale')
  await region.getByLabel('Clear position keys').check();await preview();await sample('clear-draft',fixed.slice(2,3))
  const cleared=await confirm('cleared')
  await page.reload();await waitRevision(cleared.head);await sample('cleared-reopened',fixed)
  await deliver('n14-cleared',cleared.head,fixed)
  await select();await fill(1,['1/2','20','10']);await fill(2,['5/2','40','25']);await preview();await confirm('recreated')
  const mix=await write('n14-composition',[
    {definitionId:'timeline.transition.cross-dissolve.create',target:{clipId:'red'},
      parameters:{transitionId:'n14-cut',incomingClipId:clipId,duration:'2/1'}},
    step('timeline.clip.opacity.keyframes.set',{firstTime:'3',lastTime:'5',firstValue:0.75,lastValue:0.75}),
  ])
  await cp('combo');await page.reload();await waitRevision(mix.newRevisionId)
  await sample('combo-reopened',combined,null,true);await deliver('n14-composition',mix.newRevisionId,combined,true)
  report.status='PASS';save()
}
