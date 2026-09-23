import { useEffect, useState } from 'react'
import type { ValidationIssue, WorkflowDraft } from '../api/composition'

/** Explicit mapping of platform ValidationIssue locations/paths to owned editors. */
export function validationTarget(issue: ValidationIssue): string | undefined {
  const path = issue.path.replace(/^workflows\[\d+\]\./, '')
  const location = issue.location || path
  if (issue.objectType === 'application' && (
    location === 'application:displayName' ||
    location === 'application.displayName' ||
    path === 'displayName' ||
    path === 'application:displayName' ||
    path === 'application.displayName'
  )) return 'application:displayName'
  if (/^(node|binding|parameter|output|port):/.test(location)) return location
  if (location.startsWith('asset:') || /^requiredAssets(?:\.|\[|$)/.test(path)) return 'asset:0'
  if (location.startsWith('entitlement:') || /^entitlements(?:\.|\[|$)/.test(path)) return 'entitlement:0'
  if (location.startsWith('workflow:') || /^workflowIds(?:\[|$)/.test(path)) return 'workflowIds'
  const indexed = /^(bindings|parameters|outputs)\[(\d+)\](?:\.(.+))?$/.exec(path)
  if (indexed) {
    const kind = { bindings: 'binding', parameters: 'parameter', outputs: 'output' }[indexed[1]]
    return kind === 'output' && indexed[3] === 'port' ? `port:output:${indexed[2]}` : `${kind}:${indexed[2]}${indexed[3] ? ':' + indexed[3] : ''}`
  }
  const step = /^steps\.([^.]+)/.exec(path) // node IDs normally arrive via location
  if (step) return `node:${step[1]}`
  const field = path.split(/[.[]/)[0]
  if (['entry','steps','bindings','parameters','outputs','requiredAssets','entitlements','executionModes','requiredCapabilities','workflowIds','version','name','displayName'].includes(field)) return field
  return undefined
}

export function JsonEditor({ label, target, value, onChange }: { label: string; target: string; value: unknown; onChange: (value: any) => void }) {
  const encoded = JSON.stringify(value, null, 2)
  const [text, setText] = useState(encoded)
  const [error, setError] = useState('')
  useEffect(() => { setText(encoded); setError('') }, [encoded])
  return <label>{label}<textarea data-editor-id={target} data-testid={target} aria-label={label} aria-invalid={!!error} value={text}
    onChange={event => {
      setText(event.target.value)
      try { const next: unknown = JSON.parse(event.target.value); if (typeof next !== typeof value || (Array.isArray(next) !== Array.isArray(value))) throw new Error(); onChange(next); setError('') }
      catch { setError('Enter valid JSON of the same type before saving.') }
    }} />{error && <span role="alert">{error}</span>}</label>
}

export function WorkflowEditors({ workflow: w, update }: { workflow: WorkflowDraft; update: (fn: (w: WorkflowDraft) => WorkflowDraft) => void }) {
  const field = (target: string, label: string, value: string, change: (value: string) => void) => <label key={target}>{label}<input data-editor-id={target} data-testid={target} aria-label={label} value={value} onChange={e => change(e.target.value)} /></label>
  const entry = w.entry ?? {name:'input', stepId:'', port:'input', contract:{name:'',version:'1'}}
  return <>
    <fieldset tabIndex={-1} data-editor-id="entry"><legend>Workflow entry</legend>
      {field('entry:name','Entry name',entry.name,v=>update(x=>({...x,entry:{...entry,name:v}})))}
      {field('entry:stepId','Entry node',entry.stepId,v=>update(x=>({...x,entry:{...entry,stepId:v}})))}
      {field('entry:port','Entry port',entry.port,v=>update(x=>({...x,entry:{...entry,port:v}})))}
      {field('entry:contract:name','Entry input contract',entry.contract.name,v=>update(x=>({...x,entry:{...entry,contract:{...entry.contract,name:v}}})))}
      {field('entry:contract:version','Entry input contract version',entry.contract.version,v=>update(x=>({...x,entry:{...entry,contract:{...entry.contract,version:v}}})))}
    </fieldset>
    <JsonEditor label="Workflow nodes" target="steps" value={w.steps} onChange={steps=>update(x=>({...x,steps}))}/>
    {w.steps.map((s,i)=><fieldset key={s.id} tabIndex={-1} data-editor-id={`node:${s.id}`} data-testid={`node:${s.id}`}><legend>Node {s.id}</legend>
      {field(`node:${s.id}:capabilityId`,`${s.id} capability`,s.capabilityId,v=>update(x=>({...x,steps:x.steps.map((t,j)=>j===i?{...t,capabilityId:v}:t)})))}
      {field(`node:${s.id}:capabilityVersion`,`${s.id} capability version range`,s.capabilityVersion,v=>update(x=>({...x,steps:x.steps.map((t,j)=>j===i?{...t,capabilityVersion:v}:t)})))}
      <JsonEditor label={`${s.id} input values`} target={`port:${s.id}:input`} value={s.inputs} onChange={inputs=>update(x=>({...x,steps:x.steps.map((t,j)=>j===i?{...t,inputs}:t)}))}/>
    </fieldset>)}
    <JsonEditor label="Workflow bindings" target="bindings" value={w.bindings} onChange={bindings=>update(x=>({...x,bindings}))}/>
    {w.bindings.map((b,i)=><fieldset key={i} tabIndex={-1} data-editor-id={`binding:${i}`} data-testid={`binding:${i}`}><legend>Binding {i}</legend>
      {(['fromStep','fromOutput','toStep','toInput','type'] as const).map(k=>field(`binding:${i}:${k}`,`Binding ${i} ${k}`,b[k],v=>update(x=>({...x,bindings:x.bindings.map((t,j)=>j===i?{...t,[k]:v}:t)}))))}
    </fieldset>)}
    <JsonEditor label="Workflow parameters" target="parameters" value={w.parameters} onChange={parameters=>update(x=>({...x,parameters}))}/>
    {w.parameters.map((p,i)=><JsonEditor key={i} label={`Parameter ${i}`} target={`parameter:${i}`} value={p} onChange={parameter=>update(x=>({...x,parameters:x.parameters.map((t,j)=>j===i?parameter:t)}))}/>)}
    <JsonEditor label="Workflow outputs" target="outputs" value={w.outputs} onChange={outputs=>update(x=>({...x,outputs}))}/>
    {w.outputs.map((o,i)=><fieldset key={i} tabIndex={-1} data-editor-id={`output:${i}`} data-testid={`output:${i}`}><legend>Output {i}</legend>
      {(['name','type','stepId','port'] as const).map(k=>field(k==='port'?`port:output:${i}`:`output:${i}:${k}`,`Output ${i} ${k}`,o[k],v=>update(x=>({...x,outputs:x.outputs.map((t,j)=>j===i?{...t,[k]:v}:t)}))))}
    </fieldset>)}
  </>
}
