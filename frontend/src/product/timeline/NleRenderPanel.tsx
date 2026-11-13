import { useEffect, useRef, useState } from 'react'
import { FormalRenderAPI, type RenderResult } from '../../api/formal-render'
import { DeliveryAPI, type Delivery } from '../../api/delivery'
import { renderReadSource } from '../../api/render-jobs'
import type { RenderJobSummary } from '../../contracts/app/render-job'
import type { CanonicalHeadReference } from './gateways'
import { useProjectContext } from '../../foundation/projectContext'

const terminal = new Set(['COMPLETED', 'FAILED', 'CANCELLED', 'REJECTED'])
function save(blob: Blob) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a'); link.href = url; link.download = 'render-output.mp4'; link.click()
  setTimeout(() => URL.revokeObjectURL(url), 0)
}

export function NleRenderPanel({ head }: { head: CanonicalHeadReference | null }) {
  const project = useProjectContext()
  const [endMillis, setEndMillis] = useState(3000)
  const [fps, setFps] = useState(24)
  const [jobs, setJobs] = useState<RenderJobSummary[]>([])
  const [job, setJob] = useState<RenderResult | null>(null)
  const [deliveries, setDeliveries] = useState<Delivery[]>([])
  const [delivery, setDelivery] = useState<Delivery | null>(null)
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState('')
  const [actionFailed, setActionFailed] = useState(false)
  const keys = useRef<Record<string, string>>({})
  const generation = useRef(0)
  const key = (value: unknown) => { const serialized = JSON.stringify(value); return (keys.current[serialized] ??= crypto.randomUUID()) }
  async function refresh() {
    if (!project.tenantId || !project.projectId) return
    const owned = ++generation.current
    const abort = new AbortController()
    try {
      const [jobRows, deliveryRows] = await Promise.all([
        renderReadSource.jobs(project.tenantId, project.projectId, abort.signal),
        DeliveryAPI.list(project.projectId, 50, abort.signal),
      ])
      if (owned !== generation.current) return
      setJobs(jobRows); setDeliveries(deliveryRows); setMessage('')
    } catch (error) { if (owned === generation.current) { setActionFailed(false); setMessage(error instanceof Error ? error.message : 'Render history unavailable.') } }
  }
  useEffect(() => { setJob(null); setDelivery(null); setJobs([]); setDeliveries([]); keys.current = {}; void refresh()
    return () => { generation.current += 1 }
  }, [project.tenantId, project.projectId])
  useEffect(() => {
    if (!job || terminal.has(job.status) || !project.tenantId) return
    const id = job.renderJobId; const timer = setInterval(() => { void (async () => {
      try { const result = await FormalRenderAPI.result(project.tenantId!, project.projectId, id); setJob(result)
        if (terminal.has(result.status)) await refresh() }
      catch (error) { setMessage(error instanceof Error ? error.message : 'Render status unavailable.') }
    })() }, 1000)
    return () => clearInterval(timer)
  }, [job?.renderJobId, job?.status, project.tenantId, project.projectId])
  async function run(action: () => Promise<void>) {
    setBusy(true); setMessage(''); setActionFailed(false)
    try { await action() } catch (error) { setActionFailed(true); setMessage(error instanceof Error ? error.message : 'Render request failed.') }
    finally { setBusy(false) }
  }
  function submit() {
    if (!head || !project.tenantId) return
    const intent = { revisionId: head.revisionId, extent: { startMillis: 0, endMillis,
      frameRateNumerator: fps, frameRateDenominator: 1 }, outputFormat: 'MP4' as const, profile: 'default' }
    void run(async () => {
      const accepted = await FormalRenderAPI.submit(intent, key([project.projectId, intent]))
      const current = await FormalRenderAPI.result(project.tenantId!, project.projectId, accepted.renderJobId)
      setJob(current); await refresh()
      setMessage(`RenderJob ${accepted.renderJobId} accepted for revision ${head.revisionId}.`)
    })
  }
  async function selectJob(id: string) {
    if (!project.tenantId) return
    await run(async () => { setJob(await FormalRenderAPI.result(project.tenantId!, project.projectId, id)); setDelivery(null) })
  }
  return <section aria-label="Render and delivery">
    <h3>Render current revision</h3>
    <p>{head ? 'Current authoritative revision is ready to render.' : 'Authoritative revision unavailable.'}</p>
    <label>Output end (ms) <input type="number" min={1} value={endMillis} onChange={event => setEndMillis(Number(event.target.value))} /></label>
    <label>Frame rate <input type="number" min={1} value={fps} onChange={event => setFps(Number(event.target.value))} /></label>
    <button type="button" disabled={!head || !project.tenantId || busy || endMillis <= 0 || fps <= 0} onClick={submit}>Submit RenderJob</button>
    <button type="button" disabled={busy} onClick={() => void refresh()}>Refresh jobs</button>
    {message && <p role={actionFailed ? 'alert' : 'status'}>{message}</p>}
    <h4>Render jobs</h4>
    {jobs.length === 0 ? <p>No RenderJobs yet.</p> : <ul>{jobs.map(item => <li key={item.id}>
      <button type="button" onClick={() => void selectJob(item.id)}>{item.id}: {item.status}</button>
    </li>)}</ul>}
    {job && <div aria-label="Current RenderJob"><p>Job <code>{job.renderJobId}</code> · <span role="status">{job.status}</span></p>
      {job.failureCode && <p role="alert">{job.failureCode}</p>}
      {!terminal.has(job.status) && project.tenantId && <button type="button" disabled={busy} onClick={() => void run(async () => {
        await FormalRenderAPI.cancel(project.tenantId!, project.projectId, job.renderJobId)
        setJob(await FormalRenderAPI.result(project.tenantId!, project.projectId, job.renderJobId))
      })}>Cancel render</button>}
      {job.status === 'COMPLETED' && job.finalArtifactId && <button type="button" disabled={busy} onClick={() => void run(async () => {
        const created = await DeliveryAPI.create(project.projectId, { sourceRenderJobId: job.renderJobId, artifactId: job.finalArtifactId! },
          key([project.projectId, job.renderJobId, 'delivery']))
        setDelivery(created); await refresh()
      })}>Create delivery</button>}
    </div>}
    <h4>Deliveries</h4>
    {deliveries.map(item => <button type="button" key={item.deliveryId} onClick={() => setDelivery(item)}>{item.deliveryId}: {item.status}</button>)}
    {delivery && <div><p>Delivery <code>{delivery.deliveryId}</code> · {delivery.status}</p>
      <button type="button" disabled={busy || delivery.status !== 'READY'} onClick={() => void run(async () => {
        save(await DeliveryAPI.download(project.projectId, delivery.deliveryId))
      })}>Download output video</button></div>}
  </section>
}
