import assert from 'node:assert/strict'
import { execFileSync } from 'node:child_process'
import { writeFileSync } from 'node:fs'
import { join } from 'node:path'
import { exportRevision } from './formal-export.mjs'

export async function runDissolve(ctx) {
  const { page, outputRoot, url, endpoint, post, cp, waitRevision, addTrack, addClip, upload } = ctx
  for (const [color, duration, vf] of [
    ['red', 5, "drawbox=x=48:y=48:w=32:h=32:color=white:t=fill:enable='gte(t,3.25)'"],
    ['blue', 4, "drawbox=x=96:y=48:w=32:h=32:color=white:t=fill:enable='gte(t,0.75)'"],
  ]) {
    const file = color + '.mp4'
    execFileSync('/usr/bin/ffmpeg',['-v','error','-y','-f','lavfi','-i','color=c='+color+':s=320x240:r=24',
      '-t',String(duration),'-vf',vf,'-c:v','libopenh264','-pix_fmt','yuv420p',join(outputRoot,file)])
    await upload(file)
  }
  await addClip('red.mp4','track-1','0','3','3')
  await addClip('blue.mp4','track-1','3','7','4')
  await addTrack('AUDIO','sound','n13-sound')
  execFileSync('/usr/bin/ffmpeg',['-v','error','-y','-f','lavfi','-i','sine=frequency=220:duration=7',join(outputRoot,'tone.wav')])
  await upload('tone.wav'); await addClip('tone.wav','sound','0','7','7')
  const head = ctx.head()
  const setup = { batchId:'n13-control',baseRevisionId:head.revisionId,baseContentHash:head.contentHash,operations:[
    {definitionId:'audio.gain.set',target:{audioTrackId:'sound',clipId:'tone'},parameters:{linear:0.5}},
    {definitionId:'timeline.clip.effect.set',target:{clipId:'red'},parameters:{brightness:0,contrast:1,saturation:1.1,grayscale:false,
      fadeInSeconds:0,fadeOutSeconds:0,enabled:true}}] }
  async function write(body) {
    const p=await post('preview',body)
    assert.equal(p.status(),200,await p.text())
    const r=await post('apply',body,(await p.json()).batchDigest)
    assert.equal(r.status(),201,await r.text())
    return r.json()
  }
  const initial=await write(setup)
  const baseline=await cp('base')
  assert.equal(baseline.head,initial.newRevisionId)
  await page.goto(url);await waitRevision(baseline.head)
  const region=page.getByRole('region',{name:'Dissolve edit',exact:true})
  const select=()=>region.getByLabel('Shared video cut').selectOption({label:'red → blue at 3/1 s'})
  const duration=region.getByLabel('Dissolve duration (seconds)')
  const button=name=>region.getByRole('button',{name,exact:true})
  const wire=method=>page.waitForResponse(r=>r.url()===endpoint+'/'+method)
  const report={frames:{},receipts:{},browser:page.context().browser().version(),markers:[
    'outgoing red becomes white at (60,60) at source 3.25 s','incoming blue becomes white at (108,60) at source 0.75 s']}
  const save=()=>writeFileSync(join(outputRoot,'dissolve-result.json'),JSON.stringify(report,null,2))
  const points=[69,84,96,108,123]
  const expected=(frame,seconds,opacity=1)=>{
    const t=frame/24
    const incoming=t<3?0:seconds?Math.min((t-3)/seconds,1):1
    const a=incoming*opacity
    const outgoing=t<3+(seconds||0)
    const blend=(out,inc)=>out.map((v,i)=>Math.round((outgoing?v:0)*(1-a)+inc[i]*a))
    return [[160,120,blend([255,0,0],[0,0,255])],
      [60,60,blend(t>=3.25?[255,255,255]:[255,0,0],[0,0,255])],
      [108,60,blend([255,0,0],t>=3.75?[255,255,255]:[0,0,255])]]
  }
  async function sample(stage,seconds,frames=points,artifact=null,opacity=1) {
    const rows=[]
    for(const frame of frames) {
      const at=frame/24
      const samples=expected(frame,seconds,opacity)
      if(!artifact)await page.getByLabel('Timeline preview seek').evaluate((input,time)=>{
        Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set.call(input,String(time))
        input.dispatchEvent(new Event('input',{bubbles:true}));input.dispatchEvent(new Event('change',{bubbles:true}))
      },at)
      for(const [x,y,rgb] of samples) {
        let observed
        if(artifact){
          const bytes=execFileSync('/usr/bin/ffmpeg',['-v','error','-i',artifact,'-vf',
            'select=eq(n\\,'+frame+'),crop=2:2:'+x+':'+y+',format=rgb24','-frames:v','1','-f','rawvideo','-'])
          assert.equal(bytes.length,12)
          observed=[0,1,2].map(i=>(bytes[i]+bytes[i+3]+bytes[i+6]+bytes[i+9])/4)
        }else{
          const h=await page.waitForFunction(({at,x,y,rgb})=>{
            const view=document.querySelector('[data-preview-revision]')
            if(Number(view.dataset.previewTime)!==at)return false
            const p=view.querySelector('canvas').getContext('2d').getImageData(x,y,2,2).data
            const v=[0,1,2].map(i=>(p[i]+p[i+4]+p[i+8]+p[i+12])/4)
            return v.every((c,i)=>Math.abs(c-rgb[i])<=8)&&v
          },{at,x,y,rgb})
          observed=await h.jsonValue()
        }
        rows.push({frame,at,x,y,expected:rgb,observed})
        report.frames[stage]=rows;save()
        assert(observed.every((v,i)=>Math.abs(v-rgb[i])<=(artifact?12:8)),JSON.stringify(rows.at(-1)))
      }
      if(!artifact)await page.getByLabel('Timeline preview canvas').screenshot({path:join(outputRoot,stage+'-'+frame+'.png')})
    }
  }
  async function preview(){
    const response=wire('preview');await button('Preview dissolve').click()
    const r=await response;assert.equal(r.status(),200,await r.text())
    await button('Confirm dissolve').waitFor()
    return {body:r.request().postDataJSON(),digest:(await r.json()).batchDigest}
  }
  async function confirm(stage){
    const response=wire('apply');await button('Confirm dissolve').click()
    const r=await response;assert([200,201].includes(r.status()),await r.text())
    const receipt=await r.json();report.receipts[stage]=receipt
    await waitRevision(receipt.newRevisionId??r.request().postDataJSON().baseRevisionId)
    return receipt
  }
  async function history(direction,stage){
    const response=wire('apply')
    await page.getByRole('button',{name:direction+' dissolve',exact:true}).click()
    const r=await response;assert.equal(r.status(),201,await r.text());await waitRevision((await r.json()).newRevisionId);await cp(stage)
  }
  await sample('hard-cut',0);await select();await duration.fill('2')
  const draft=await preview()
  await region.getByText('Blend [3/1, 5/1) s · duration 2/1 s').waitFor()
  await sample('draft',2);await button('Cancel dissolve').click()
  await sample('cancel',0,[96]);assert.deepEqual(await cp('draft'),baseline)
  // The outgoing pin ends at 5 s. Fail both preview and apply one frame beyond it.
  for (const [name, mutate] of [['overrun',op=>{op.parameters.duration='49/24'}],
      ['invalid-pair',op=>{op.target.clipId='blue';op.parameters.incomingClipId='red'}]]) {
    const body=structuredClone(draft.body);body.batchId='n13-'+name;mutate(body.operations[0])
    for(const method of ['preview','apply']){
      const r=await post(method,body,method==='apply'?draft.digest:null)
      assert.equal(r.status(),422,await r.text());report[name+'-'+method]=await r.json()
    }
  }
  assert.deepEqual(await cp('invalid'),baseline)
  const planned=await preview(),attempts=[]
  const routePath='**'+new URL(endpoint).pathname+'/apply'
  await page.route(routePath,async route=>{
    const req=route.request(),resp=await route.fetch()
    attempts.push({target:new URL(req.url()).pathname,body:req.postData(),ifMatch:req.headers()['if-match'],status:resp.status(),receipt:await resp.json()})
    if(attempts.length===1)await route.abort('failed');else await route.fulfill({response:resp})
  })
  await button('Confirm dissolve').click();const retry=button('Retry confirmed dissolve');await retry.waitFor()
  const accepted=await cp('accepted')
  await page.route('**/timeline/branches/main',r=>r.abort('failed'),{times:1})
  await retry.click()
  await region.getByText('Dissolve accepted at revision '+accepted.head+'. Reload HEAD to refresh the editor.').waitFor()
  await page.getByRole('button',{name:'Reload HEAD',exact:true}).click();await waitRevision(accepted.head)
  assert.deepEqual(await cp('recovery'),accepted);await page.unroute(routePath)
  assert.deepEqual(attempts.map(a=>a.status),[201,200])
  for(const field of ['body','ifMatch','target'])assert.equal(attempts[0][field],attempts[1][field])
  assert.equal(attempts[0].ifMatch,'"'+planned.digest+'"');assert.deepEqual(JSON.parse(attempts[0].body),planned.body)
  assert(attempts.every(a=>a.receipt.newRevisionId===accepted.head));report.replay=attempts
  await page.reload();await waitRevision(accepted.head);await select();assert.equal(await duration.inputValue(),'2/1')
  const split = {batchId:'n13-split-bound',baseRevisionId:accepted.head,
    baseContentHash:attempts[0].receipt.newContentHash,operations:[{definitionId:'timeline.clip.split',
      target:{clipId:'red'},parameters:{rightClipId:'split-red',splitTimelineTime:'2/1'}}]}
  for (const method of ['preview','apply']) {
    const r=await post(method,split,method==='apply'?planned.digest:null)
    assert.equal(r.status(),422,await r.text());report['bound-split-'+method]=await r.json()
  }
  assert.deepEqual(await cp('bound'),accepted)
  await sample('reopened',2)
  const deliver=async(stage,id,seconds,frames,opacity=1)=>{
    const {path,...record}=await exportRevision(ctx,id,0,7000,stage)
    report[stage]=record;await sample(stage,seconds,frames,path,opacity)
  }
  await deliver('n13-dissolve',accepted.head,2,points)
  await duration.fill('1');const frozen=await preview();await confirm('edit');const edited=await cp('edited')
  await sample('shorter',1,[84,96]);await deliver('n13-shorter',edited.head,1,[84,96])
  await history('Undo','undo');await history('Redo','redo')
  const stale=await post('apply',{...frozen.body,batchId:'n13-stale'},frozen.digest)
  assert.equal(stale.status(),409,await stale.text());report.stale=await stale.json();await cp('stale')
  await select();await preview();const zero=await confirm('noop');assert.equal(zero.status,'NO_OP');await cp('noop')
  await region.getByLabel('Remove saved dissolve').check();await preview();await sample('removal-draft',0,[84,96])
  await confirm('removed');const removed=await cp('removed')
  await page.reload();await waitRevision(removed.head);await sample('removed',0);await deliver('n13-removed',removed.head,0,points)
  await historyAfterReload()
  async function historyAfterReload(){
    // Begin another session-local edit after reopen, then undo and redo its removal.
    await select();await duration.fill('2');await preview();await confirm('recreated');await cp('recreated')
    await history('Undo','undo-create');await history('Redo','redo-create')
    await region.getByLabel('Remove saved dissolve').check();await preview();await confirm('removed-again');await cp('removed-again')
    await history('Undo','undo-remove');await history('Redo','redo-remove');await history('Undo','restored')
  }
  const active=await ctx.page.request.get(ctx.base+'/api/timeline-git/products/'+ctx.project+'/revisions/current',{headers:ctx.headers})
  const state=await active.json()
  const combo=await write({batchId:'n13-opacity',baseRevisionId:state.revisionId,
    baseContentHash:state.contentHash??state.revision.semanticContext.timelineContentDigest,operations:[
    {definitionId:'timeline.clip.opacity.keyframes.set',target:{clipId:'blue'},parameters:{firstTime:'3',lastTime:'4',firstValue:0.5,lastValue:0.5}}]})
  await page.reload();await waitRevision(combo.newRevisionId)
  await sample('with-opacity',2,[96],null,0.5)
  await deliver('n13-with-opacity',combo.newRevisionId,2,[96],0.5);await cp('combo')
  report.status='PASS';save()
}
