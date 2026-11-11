import { useEffect, useRef, useState } from 'react'
import { FormalRenderAPI, type ClipEdit, type RenderResult } from '../../api/formal-render'
import { DeliveryAPI, type Delivery } from '../../api/delivery'
import { renderReadSource, type RenderProject } from '../../api/render-jobs'
import type { RenderJobSummary } from '../../contracts/app/render-job'
import { subscribeOidcSessionRetirement } from '../../auth/oidcClient'

const terminal = new Set(['COMPLETED', 'FAILED', 'CANCELLED', 'REJECTED'])
function save(blob: Blob) { const url = URL.createObjectURL(blob); const a = document.createElement('a'); a.href = url; a.download = 'output.mp4'; a.click(); URL.revokeObjectURL(url) }

/** Product workflow over pinned revisions and formal jobs; no execution DAG or storage locator enters the UI. */
export function RenderWorkbench() {
  const [tenant, setTenant] = useState(''); const [projects, setProjects] = useState<RenderProject[]>([]); const [project, setProject] = useState('')
  const [revision, setRevision] = useState(''); const [hash, setHash] = useState(''); const [clips, setClips] = useState<string[]>([]); const [clip, setClip] = useState('')
  const [editType, setEditType] = useState<ClipEdit['type']>('TRIM_CLIP'); const [delta, setDelta] = useState('1/4')
  const [end, setEnd] = useState(3000); const [fps, setFps] = useState(4)
  const [job, setJob] = useState<RenderResult | null>(null); const [reference, setReference] = useState<{ job: string; revision: string } | null>(null)
  const [jobs, setJobs] = useState<RenderJobSummary[]>([])
  const [delivery, setDelivery] = useState<Delivery | null>(null); const [deliveries, setDeliveries] = useState<Delivery[]>([])
  const [busy, setBusy] = useState(false); const [error, setError] = useState('')
  const scope = useRef(new AbortController()); const keys = useRef(new Map<string, string>())
  const key = (intent: unknown) => { const value = JSON.stringify(intent); if (!keys.current.has(value)) keys.current.set(value, crypto.randomUUID()); return keys.current.get(value)! }
  async function loadRevision(id: string, signal: AbortSignal) {
    const info = id ? await FormalRenderAPI.revision(project, id, signal) : await FormalRenderAPI.head(project, signal)
    const snapshot = await FormalRenderAPI.snapshot(project, info.revisionId, signal)
    signal.throwIfAborted(); const doc = JSON.parse(snapshot.canonicalTimelineJson)
    const ids: string[] = doc.tracks.flatMap((track: { clips: { clipId: string }[] }) => track.clips.map(c => c.clipId))
    setRevision(info.revisionId); setHash(info.contentHash); setClips(ids); setClip(ids[0] ?? '')
  }
  async function action(run: (signal: AbortSignal) => Promise<void>) {
    const signal = scope.current.signal; setBusy(true); setError('')
    try { await run(signal); signal.throwIfAborted() }
    catch (failure) { if (!signal.aborted) { const status = (failure as { response?: { status?: number } })?.response?.status; if (status === 401 || status === 403) { setProject(''); setProjects([]); setTenant(''); setJob(null); setDelivery(null); setReference(null); setJobs([]); setDeliveries([]); setRevision(''); setHash(''); setClips([]); keys.current.clear() }; setError(status === 401 || status === 403 ? 'Project access is unavailable. Sign in again or select an authorized workspace.' : failure instanceof Error ? failure.message : 'Request failed') } }
    finally { if (!signal.aborted) setBusy(false) }
  }
  useEffect(() => {
    const abort = new AbortController()
    void (async () => { try { const t = await renderReadSource.tenant(abort.signal); const p = await renderReadSource.projects(t, abort.signal); abort.signal.throwIfAborted(); setTenant(t); setProjects(p); setProject(p[0]?.id ?? '') } catch (e) { if (!abort.signal.aborted) setError(e instanceof Error ? e.message : 'Workspace unavailable') } })()
    const stop = subscribeOidcSessionRetirement(() => { abort.abort(); scope.current.abort(); keys.current.clear(); setProject(''); setProjects([]); setTenant(''); setJob(null); setReference(null); setJobs([]); setDelivery(null); setDeliveries([]); setRevision(''); setHash(''); setBusy(false) })
    return () => { abort.abort(); scope.current.abort(); stop() }
  }, [])
  useEffect(() => {
    scope.current.abort(); scope.current = new AbortController(); setJob(null); setReference(null); setJobs([]); setDelivery(null); setDeliveries([]); setRevision(''); setHash(''); setClips([]); setJobs([]); setBusy(false)
    if (project) void action(async signal => { await loadRevision('', signal); const rows = await DeliveryAPI.list(project); const records = await renderReadSource.jobs(tenant, project, signal); signal.throwIfAborted(); setJobs(records); setDeliveries(rows) })
    return () => scope.current.abort()
  }, [project])
  useEffect(() => {
    if (!job || terminal.has(job.status)) return
    const id = job.renderJobId; const signal = scope.current.signal
    const timer = setInterval(() => { void (async () => { try {
      const result = await FormalRenderAPI.result(tenant, project, id, signal); const records = await renderReadSource.jobs(tenant, project, signal); signal.throwIfAborted(); setJob(result); setJobs(records)
      if (result.status === 'COMPLETED') { const info = await FormalRenderAPI.lifecycle(project, id, signal); signal.throwIfAborted(); setReference(previous => previous ?? { job: id, revision: info.timelineRevisionId }) }
    } catch (e) { if (!signal.aborted) setError(e instanceof Error ? e.message : 'Status unavailable') } })() }, 1000)
    return () => clearInterval(timer)
  }, [job?.renderJobId, job?.status, tenant, project])
  const extent = () => ({ startMillis: 0, endMillis: end, frameRateNumerator: fps, frameRateDenominator: 1 })
  const submit = (incremental: boolean) => action(async signal => {
    const output = { extent: extent(), outputFormat: 'MP4' as const, profile: 'default' }
    const result = incremental && reference
      ? await FormalRenderAPI.incremental({ ...output, targetRevisionId: revision, referenceRevisionId: reference.revision, referenceRenderJobId: reference.job }, key([project, revision, reference, output]), signal)
      : await FormalRenderAPI.submit({ ...output, revisionId: revision }, key([project, revision, output]), signal)
    const current = await FormalRenderAPI.result(tenant, project, result.renderJobId, signal); signal.throwIfAborted(); setJob(current); setDelivery(null)
    if (current.status === 'COMPLETED' && !reference) { const info = await FormalRenderAPI.lifecycle(project, current.renderJobId, signal); signal.throwIfAborted(); setReference({ job: current.renderJobId, revision: info.timelineRevisionId }) }
  })
  const apply = () => action(async signal => {
    const intent = { type: editType, baseRevisionId: revision, baseContentHash: hash, clipId: clip, ...(editType === 'DELETE_CLIP' ? {} : { delta }), ...(editType === 'TRIM_CLIP' ? { edge: 'END' as const } : {}), ...(editType === 'MOVE_CLIP' ? { absolute: true } : {}) }
    const result = await FormalRenderAPI.applyOperation(tenant, project, { ...intent, commandId: key([project, intent]) }, signal)
    await loadRevision(result.revisionId, signal)
  })
  return <main className="space-y-4 p-6" aria-label="Render workspace">
    <h1>Render and deliver</h1>
    <label>Project <select aria-label="Project" value={project} onChange={e => setProject(e.target.value)}>{projects.map(p => <option key={p.id} value={p.id}>{p.name}</option>)}</select></label>
    {!project && <p>Select an authorized project with a Timeline revision.</p>}
    {error && <p role="alert">{error}</p>}
    {project && <>
      <p>Revision <code>{revision || 'No Timeline revision'}</code></p>
      <button disabled={busy} onClick={() => void action(signal => loadRevision('', signal))}>Load current revision</button>
      <fieldset disabled={busy || !revision}><legend>Edit Timeline</legend>
        <label>Clip <select aria-label="Clip" value={clip} onChange={e => setClip(e.target.value)}>{clips.map(id => <option key={id}>{id}</option>)}</select></label>
        <label>Operation <select aria-label="Operation" value={editType} onChange={e => setEditType(e.target.value as ClipEdit['type'])}><option>TRIM_CLIP</option><option>MOVE_CLIP</option><option>DELETE_CLIP</option></select></label>
        {editType !== 'DELETE_CLIP' && <label>Exact delta <input aria-label="Exact delta" value={delta} onChange={e => setDelta(e.target.value)} /></label>}
        <button disabled={!clip} onClick={() => void apply()}>Apply operation</button>
      </fieldset>
      <fieldset disabled={busy || !revision}><legend>Render output</legend>
        <label>End (ms) <input type="number" min={1} aria-label="End (ms)" value={end} onChange={e => setEnd(Number(e.target.value))} /></label>
        <label>Frame rate <input type="number" min={1} aria-label="Frame rate" value={fps} onChange={e => setFps(Number(e.target.value))} /></label>
        <button onClick={() => void submit(false)}>Submit render</button>
        <button disabled={!reference} onClick={() => void submit(true)}>Submit incremental render</button>
        {reference && <p>Reference <code>{reference.job}</code> / <code>{reference.revision}</code></p>}
      </fieldset>
      <section aria-label="Existing render jobs"><h2>Render jobs</h2>{jobs.map(j => <button key={j.id} onClick={() => void action(async signal => { const value = await FormalRenderAPI.result(tenant, project, j.id, signal); signal.throwIfAborted(); setJob(value); setDelivery(null) })}>{j.id}: {j.status}</button>)}</section>
      {job && <section aria-label="Render result"><h2>RenderJob</h2><code>{job.renderJobId}</code><p role="status">{job.status}</p>{job.failureCode && <p>{job.failureCode}</p>}
        {!terminal.has(job.status) && <button disabled={busy} onClick={() => void action(async signal => { await FormalRenderAPI.cancel(tenant, project, job.renderJobId, signal); setJob(await FormalRenderAPI.result(tenant, project, job.renderJobId, signal)) })}>Cancel render</button>}
        {job.status === 'COMPLETED' && job.finalArtifactId && <>
          <button disabled={busy} onClick={() => void action(async signal => { const info = await FormalRenderAPI.lifecycle(project, job.renderJobId, signal); signal.throwIfAborted(); setReference({ job: info.renderJobId, revision: info.timelineRevisionId }) })}>Use as incremental reference</button>
          <button disabled={busy} onClick={() => void action(async signal => { const blob = await FormalRenderAPI.download(tenant, project, job.renderJobId, job.finalArtifactId!, signal); signal.throwIfAborted(); save(blob) })}>Download render</button>
          <button disabled={busy} onClick={() => void action(async signal => { const value = await DeliveryAPI.create(project, { sourceRenderJobId: job.renderJobId, artifactId: job.finalArtifactId! }, key([project, 'delivery', job.renderJobId])); signal.throwIfAborted(); setDelivery(value); const rows = await DeliveryAPI.list(project); signal.throwIfAborted(); setDeliveries(rows) })}>Create delivery</button>
        </>}
      </section>}
      <section aria-label="Deliveries"><h2>Deliveries</h2>{deliveries.map(d => <button key={d.deliveryId} onClick={() => void action(async signal => { const value = await DeliveryAPI.get(project, d.deliveryId); signal.throwIfAborted(); setDelivery(value) })}>{d.deliveryId}: {d.status}</button>)}
        {delivery && <div><code>{delivery.deliveryId}</code><p>{delivery.status}</p><button disabled={busy} onClick={() => void action(async signal => { const blob = await DeliveryAPI.download(project, delivery.deliveryId); signal.throwIfAborted(); save(blob) })}>Download delivery</button></div>}
      </section>
    </>}
  </main>
}
