import assert from 'node:assert/strict'
import { execFileSync } from 'node:child_process'
import { createHash } from 'node:crypto'
import { readFileSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import { exportRevision } from './formal-export.mjs'

export async function runSubtitle(ctx) {
  const { page, outputRoot, url, endpoint, post, cp, waitRevision, addClip, upload } = ctx
  const file = 'backdrop.mp4'
  execFileSync('/usr/bin/ffmpeg',['-v','error','-y','-f','lavfi','-i','color=0x142850:s=320x240:r=24',
    '-t','5','-c:v','libopenh264','-pix_fmt','yuv420p',join(outputRoot,file)])
  await upload(file)
  await addClip(file,'track-1','0','5','5')
  const text = '春天来了 🌻\uFE0F\nSpring is here'
  const wrapText = text.replace('\n',' ')
  const expected = {
    text, explicitLines: text.split('\n'), wrapText, wrappedLines: text.split('\n'),
    expectedMotion: [
      {frame:23,visible:false}, {frame:24,visible:true,x:32,y:96,alpha:1},
      {frame:48,visible:true,x:56,y:114,alpha:0.8}, {frame:84,visible:true,x:80,y:132,alpha:0.6},
      {frame:96,visible:false},
    ],
    wrapped: [{frame:48,visible:true,x:32,y:132,alpha:1}],
    rate:24, cueStart:'1/1', cueEnd:'4/1', points:'Half-open interval and two cue-local position/opacity keys',
    referenceWidth:320, cueWidths:[200,160], fontSize:24, control:[10,10],
    glyphs:['春','天','来','了','🌻'],
  }
  writeFileSync(join(outputRoot,'expected.json'),JSON.stringify(expected,null,2))
  const base = await cp('base')
  await page.goto(url); await waitRevision(base.head)
  const region=page.getByRole('region',{name:'Subtitle edit',exact:true})
  const button=name=>region.getByRole('button',{name,exact:true})
  const input=name=>region.getByLabel(name,{exact:true})
  const wire=method=>page.waitForResponse(r=>r.url()===endpoint+'/'+method)
  const report={browser:page.context().browser().version(),frames:{},receipts:{},references:{}}
  const save=()=>writeFileSync(join(outputRoot,'subtitle-result.json'),JSON.stringify(report,null,2))
  async function fill({content,width,animated}){
    await input('Subtitle text').fill(content)
    await input('Subtitle start (timeline seconds)').fill('1')
    await input('Subtitle end (timeline seconds)').fill('4')
    await input('Subtitle width (at 320 px)').fill(String(width))
    await input('Subtitle font size (at 320 px)').fill('24')
    if(animated){
      await input('Animate whole subtitle').check()
      await input('Subtitle key 1 Y (%)').fill('40')
      await input('Subtitle key 2 time (cue seconds)').fill('2')
      await input('Subtitle key 2 Y (%)').fill('55')
    } else await input('Animate whole subtitle').uncheck()
  }
  async function preview(){
    const next=wire('preview');await button('Preview subtitle').click()
    const r=await next;assert.equal(r.status(),200,await r.text());await button('Confirm subtitle').waitFor()
    return {body:r.request().postDataJSON(),digest:(await r.json()).batchDigest}
  }
  async function confirm(stage,direction){
    const next=wire('apply')
    await (direction?page.getByRole('button',{name:direction+' subtitle',exact:true}):button('Confirm subtitle')).click()
    const r=await next;assert.equal(r.status(),201,await r.text())
    const result=await r.json();report.receipts[stage]=result
    await waitRevision(result.newRevisionId)
    return cp(stage)
  }

  const raw=(file,frame,pixelFormat)=>execFileSync('/usr/bin/ffmpeg',['-v','error','-i',file,
    '-vf','select=eq(n\\,'+frame+'),format='+pixelFormat,'-frames:v','1','-f','rawvideo','-'])
  async function reference(content,width,label){
    const r=await page.request.post(ctx.base+ctx.scope+'/timeline-operations/subtitle-layout',{
      headers:{...ctx.headers,Accept:'image/png'},data:{text:content,width,fontSize:24,fontProfile:'noto-sc-color-emoji-v1'},
    })
    assert.equal(r.status(),200,await r.text())
    const png=await r.body()
    const output=join(outputRoot,label+'-reference.png')
    writeFileSync(output,png)
    const height=png.readUInt32BE(20)
    const rgba=raw(output,0,'rgba')
    assert.equal(rgba.length,width*height*4)
    const rowRuns=[]
    for(let y=0;y<height;y++){
      let ink=false
      for(let x=0;x<width;x++) if(rgba[4*(y*width+x)+3]>140){ink=true;break}
      if(ink && (!rowRuns.length || rowRuns.at(-1)[1]!==y-1))rowRuns.push([y,y])
      else if(ink)rowRuns.at(-1)[1]=y
    }
    // Adjacent strokes may have single-row holes. The two line bands have a much larger gap.
    const groups=[]
    for(const run of rowRuns){
      if(groups.length && run[0]-groups.at(-1)[1]<=4)groups.at(-1)[1]=run[1]
      else groups.push(run)
    }
    assert.equal(groups.length,2,JSON.stringify(groups))
    const glyphs=[]
    for(const [i,name] of expected.glyphs.entries()){
      const from=i<4?2+i*24:100, to=i<4?2+(i+1)*24:134
      let samples=0, amber=0
      for(let y=groups[0][0];y<=groups[0][1];y++)for(let x=from;x<to;x++){
        const p=4*(y*width+x)
        if(rgba[p+3]>=160){ samples++; if(rgba[p]>130&&rgba[p+1]>80&&rgba[p+2]<125)amber++ }
      }
      assert(samples>24,JSON.stringify({name,samples,amber}))
      if(i===4)assert(amber>20,'The emoji must include the sunflower’s yellow and orange colors')
      glyphs.push({name,from,to,samples,amber})
    }
    report.references[label]={width,height,groups,glyphs,sha256:createHash('sha256').update(png).digest('hex')}
    return {width,height,rgba,groups,glyphs}
  }
  async function browserFrame(stage,point){
    const at=Math.round(point.frame/24*1000)/1000
    await page.getByLabel('Timeline preview seek').evaluate((input,value)=>{
      Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set.call(input,String(value))
      input.dispatchEvent(new Event('input',{bubbles:true})); input.dispatchEvent(new Event('change',{bubbles:true}))
    },at)
    const image=await page.waitForFunction(({at,visible})=>{
      const view=document.querySelector('[data-preview-revision]')
      if(!view || Number(view.dataset.previewTime)!==at || !!view.dataset.activeSubtitles!==visible) return false
      const canvas=view.querySelector('canvas')
      const c=canvas.getContext('2d').getImageData(10,10,1,1).data
      if(![20,40,80].every((v,i)=>Math.abs(v-c[i])<=12))return false
      return canvas.toDataURL()
    },{at,visible:point.visible})
    const path=join(outputRoot,stage+'-'+point.frame+'.png')
    writeFileSync(path,Buffer.from((await image.jsonValue()).split(',')[1],'base64'))
    return raw(path,0,'rgb24')
  }
  function measure(frame,point,block,exported){
    assert.equal(frame.length,320*240*3)
    const control=Array.from(frame.subarray((10*320+10)*3,(10*320+10)*3+3))
    assert(control.every((c,i)=>Math.abs(c-[20,40,80][i])<=12),JSON.stringify(control))
    const idx=(x,y)=>(y*320+x)*3
    const error=(x,y)=>Math.max(...control.map((v,i)=>Math.abs(v-frame[idx(x,y)+i])))
    if(!point.visible){
      for(let y=80;y<224;y++)for(let x=20;x<280;x++)assert(error(x,y)<=9,'The cue must be absent outside [start,end)')
      return {control,ink:0}
    }
    const {x,y,alpha}=point
    const q=(u,v,i)=>frame[idx(x+u,y+v)+i]
    const threshold=exported?35:10
    const opacities=[]
    for(let v=0;v<block.height;v++)for(let u=0;u<block.width;u++){
      const p=4*(v*block.width+u)
      if(block.rgba[p+3]===255 && block.rgba[p]===255 && block.rgba[p+1]===255 && block.rgba[p+2]===255){
        opacities.push((q(u,v,0)-control[0])/(255-control[0]))
      }
    }
    opacities.sort((a,b)=>a-b)
    const measuredOpacity=opacities[Math.floor(opacities.length/2)]
    assert(Math.abs(measuredOpacity-alpha)<(exported?0.075:0.04), JSON.stringify({measuredOpacity,alpha}))
    const check=(from,to,top,bottom)=>{
      let expectedInk=0, overlap=0, union=0, channelError=0, channels=0, yellow=0
      for(let v=top;v<=bottom;v++)for(let u=from;u<to;u++){
        const p=4*(v*block.width+u), ref=block.rgba
        const a=alpha*ref[p+3]/255
        const rgb=control.map((k,i)=>Math.round(k*(1-a)+ref[p+i]*a))
        const distance=Math.max(...rgb.map((k,i)=>Math.abs(control[i]-k)))
        const observed=error(x+u,y+v)>55, wanted=distance>55
        if(wanted)expectedInk++
        if(wanted&&observed)overlap++
        if(wanted||observed)union++
        if(wanted){
          for(let i=0;i<3;i++){ channelError+=Math.abs(q(u,v,i)-rgb[i]);channels++ }
          if(q(u,v,0)>110&&q(u,v,1)>75&&q(u,v,2)<125)yellow++
        }
      }
      const iou=overlap/union, meanChannelError=channelError/channels
      assert(expectedInk>15&&iou>(exported?0.63:0.93)&&meanChannelError<threshold,
        JSON.stringify({from,to,top,bottom,expectedInk,iou,meanChannelError}))
      return {iou,meanChannelError,yellow}
    }
    const glyphs=block.glyphs.map(g=>({name:g.name,...check(g.from,g.to,...block.groups[0])}))
    assert(glyphs.at(-1).yellow>15,'The decoded sunflower must retain its colored center and petals')
    const lines=block.groups.map(([top,bottom])=>check(0,block.width,top,bottom))
    return {control,expectedX:x,expectedY:y,expectedAlpha:alpha,measuredOpacity,glyphs,lines}
  }
  async function samples(stage,points,block,mp4=null){
    const result=[]
    for(const point of points){
      const bytes=mp4?raw(mp4,point.frame,'rgb24'):await browserFrame(stage,point)
      result.push({frame:point.frame,seconds:point.frame/24,expected:point,observed:measure(bytes,point,block,!!mp4)})
      report.frames[stage]=result;save()
      if(mp4)execFileSync('/usr/bin/ffmpeg',['-v','error','-y','-i',mp4,'-vf','select=eq(n\\,'+point.frame+')',
        '-frames:v','1',join(outputRoot,stage+'-'+point.frame+'.png')])
    }
  }
  const block=await reference(text,200,'explicit')
  const wrapped=await reference(wrapText,160,'wrapped')
  await fill({content:text,width:200,animated:true})
  const draft=await preview()
  await samples('draft',expected.expectedMotion,block)
  await button('Cancel subtitle').click();assert.deepEqual(await cp('draft'),base)
  for(const [id,mutate] of [['time',p=>p.end='1/1'],['key',p=>p.keys[1].time='3/1'],['value',p=>p.keys[0].opacityMicros=1000001]]){
    const body=structuredClone(draft.body);body.batchId=ctx.project+'-bad-cue-'+id;mutate(body.operations[0].parameters)
    for(const method of ['preview','apply']){
      const r=await post(method,body,method==='apply'?draft.digest:null)
      assert([400,422].includes(r.status()),await r.text());report[id+'-'+method]={status:r.status(),problem:await r.json()}
    }
  }
  assert.deepEqual(await cp('invalid'),base)
  const frozen=await preview(),attempts=[]
  const applyRoute='**'+new URL(endpoint).pathname+'/apply'
  await page.route(applyRoute,async route=>{
    const req=route.request(),res=await route.fetch()
    attempts.push({path:new URL(req.url()).pathname,body:req.postData(),ifMatch:req.headers()['if-match'],status:res.status(),receipt:await res.json()})
    if(attempts.length===1)await route.abort('failed')
    else await route.fulfill({response:res})
  })
  await button('Confirm subtitle').click();await button('Retry confirmed subtitle').waitFor()
  const accepted=await cp('accepted')
  await page.route('**/timeline/branches/main',r=>r.abort('failed'),{times:1})
  await button('Retry confirmed subtitle').click()
  await region.getByText('Subtitle accepted at revision '+accepted.head+'. Reload HEAD to refresh the editor.').waitFor()
  await page.getByRole('button',{name:'Reload HEAD',exact:true}).click();await waitRevision(accepted.head)
  assert.deepEqual(await cp('recovery'),accepted);await page.unroute(applyRoute)
  assert.deepEqual(attempts.map(a=>a.status),[201,200])
  for(const field of ['path','body','ifMatch'])assert.equal(attempts[0][field],attempts[1][field])
  assert.equal(attempts[0].ifMatch,'"'+frozen.digest+'"')
  assert.deepEqual(JSON.parse(attempts[0].body),frozen.body)
  assert(attempts.every(a=>a.receipt.newRevisionId===accepted.head));report.replay=attempts
  const cueId=frozen.body.operations[0].parameters.cueId
  report.cueId=cueId
  await page.reload();await waitRevision(accepted.head);await input('Subtitle cue').selectOption(cueId)
  assert.equal(await input('Subtitle text').inputValue(),text)
  await samples('reopened',expected.expectedMotion,block)
  const {path,...record}=await exportRevision(ctx,accepted.head,0,5000,'n15-subtitle')
  report['n15-subtitle']=record;await samples('n15-subtitle',expected.expectedMotion,block,path)
  await fill({content:wrapText,width:160,animated:false});const stale=await preview()
  const edited=await confirm('edited')
  await confirm('undo','Undo');await confirm('redo','Redo')
  const rejected=await post('apply',{...stale.body,batchId:ctx.project+'-stale-cue'},stale.digest)
  assert.equal(rejected.status(),409,await rejected.text());report.stale=await rejected.json();await cp('stale')
  await page.reload(); await waitRevision((await cp('reopened-wrap')).head)
  await input('Subtitle cue').selectOption(cueId)
  assert.equal(await input('Subtitle text').inputValue(),wrapText)
  await samples('wrap-reopened',expected.wrapped,wrapped)
  const {path:wrapPath,...wrapRecord}=await exportRevision(ctx,edited.head,0,5000,'n15-wrapped-static')
  report['n15-wrapped-static']=wrapRecord;await samples('n15-wrapped-static',expected.wrapped,wrapped,wrapPath)
  await input('Remove saved subtitle').check();await preview();const removed=await confirm('removed')
  await samples('removed',[{frame:48,visible:false}],wrapped)
  await confirm('undo-remove','Undo');await confirm('redo-remove','Redo')
  await page.reload();await waitRevision((await cp('removed-reopen')).head)
  assert.equal(await input('Subtitle cue').locator('option').count(),1)
  assert.equal(removed.rows,edited.rows+3)
  report.status='PASS';save()
}
