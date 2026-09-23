import { useEffect, useState } from 'react'
import { compositionApi, type CapabilityAvailability, type WorkflowDraft } from '../api/composition'

export function CompositionFoundationPage() {
  const [capabilities,setCapabilities]=useState<CapabilityAvailability[]>([]); const [selected,setSelected]=useState<string[]>([]); const [status,setStatus]=useState(''); const [name,setName]=useState('New workflow')
  useEffect(()=>{compositionApi.capabilities().then(setCapabilities).catch(e=>setStatus(e?.response?.status===401?'Session expired':'Unable to load catalog'))},[])
  const draft: WorkflowDraft={id:'draft-'+Date.now(),version:'1.0',name,steps:selected.map((id,i)=>({id:'step-'+i,capabilityId:id,capabilityVersion:'1.0',inputs:{},requiredAssets:[],alternatives:[]})),bindings:[],parameters:[],requiredCapabilities:selected,executionModes:['ASYNCHRONOUS'],requiredAssets:[],estimate:{units:0,unit:'quota-unit',quotaUnits:0},reliability:{cancellable:true,retryable:true,maxRetries:1},lifecycle:'DRAFT',tenantId:'',workspaceId:'',revision:0}
  async function save(){try{await compositionApi.saveWorkflow(draft);setStatus('Draft saved')}catch(e:any){setStatus(e?.response?.status===403?'Workspace access denied':'Save failed')}}
  async function validate(){try{const r=await compositionApi.validateWorkflow(draft);setStatus(r.ready?'Ready to publish':r.issues.map((x:any)=>x.code).join(', '))}catch{setStatus('Validation failed')}}
  return <main className="foundation-page"><h1>Composition foundation</h1><p>Browse capabilities and build a provider-neutral Template Workflow draft.</p><label>Workflow name <input value={name} onChange={e=>setName(e.target.value)} /></label><h2>Available capabilities</h2><ul>{capabilities.map(c=><li key={c.capabilityId}><label><input type="checkbox" checked={selected.includes(c.capabilityId)} onChange={e=>setSelected(v=>e.target.checked?[...v,c.capabilityId]:v.filter(x=>x!==c.capabilityId))}/>{c.capabilityId} v{c.version} — {c.summary} ({c.availability})</label></li>)}</ul><button onClick={save}>Save draft</button><button onClick={validate}>Check publish readiness</button>{status&&<p role="status">{status}</p>}</main>
}
