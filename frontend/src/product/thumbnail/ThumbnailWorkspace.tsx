import { useEffect, useState } from 'react'
import { cancelThumbnail, getThumbnail, listThumbnailAssets, readThumbnailImage, retryThumbnail, submitThumbnail, type ThumbnailAsset, type ThumbnailExecution } from '../../api/thumbnail'
import { useParams } from '@tanstack/react-router'
import { useWorkspaceHome } from '../../foundation/platformClient'

const config = { baseUrl: '' }
const active = (execution: ThumbnailExecution) => ['ADMITTED', 'RUNNING', 'RETRYING'].includes(execution.state)

export function ThumbnailWorkspace() {
  const { workspaceId, projectId } = useParams({ strict: false }) as { workspaceId: string; projectId: string }
  const home = useWorkspaceHome(workspaceId)
  const tenantId = home.data?.tenantId ?? ''
  const [assets, setAssets] = useState<ThumbnailAsset[]>([])
  const [asset, setAsset] = useState('')
  const [time, setTime] = useState('0')
  const [execution, setExecution] = useState<ThumbnailExecution | null>(null)
  const [imageUrl, setImageUrl] = useState<string | null>(null)
  const [error, setError] = useState('')
  const [loadingAssets, setLoadingAssets] = useState(false)

  useEffect(() => {
    setExecution(null); setAsset(''); setAssets([]); setError('')
    if (!projectId || !tenantId || home.unavailable) return
    const controller = new AbortController()
    setLoadingAssets(true)
    void listThumbnailAssets(config, projectId, controller.signal).then(result => {
      if (result.success) setAssets(result.data.filter(a => a.mediaType === 'VIDEO' || /^video\//.test(a.mediaType)))
      else if (!controller.signal.aborted) setError(result.error.message)
    }).catch(() => { if (!controller.signal.aborted) setError('Source media could not be loaded.') })
      .finally(() => { if (!controller.signal.aborted) setLoadingAssets(false) })
    return () => controller.abort()
  }, [projectId, tenantId, home.unavailable])

  useEffect(() => {
    if (!execution || !active(execution) || !tenantId || home.unavailable) return
    let retired = false
    const timer = window.setInterval(() => {
      void getThumbnail(config, tenantId, projectId, execution.executionId).then(result => {
        if (retired) return
        if (result.success) setExecution(result.data)
        else setError(result.error.message)
      }).catch(() => { if (!retired) setError('Execution status could not be loaded.') })
    }, 1000)
    return () => { retired = true; window.clearInterval(timer) }
  }, [execution, tenantId, projectId, home.unavailable])

  useEffect(() => {
    setImageUrl(null)
    if (!execution || execution.state !== 'COMPLETED' || !tenantId || home.unavailable) return
    const controller = new AbortController()
    let ownedUrl: string | null = null
    void readThumbnailImage(config, tenantId, projectId, execution.executionId, controller.signal).then(blob => {
      if (controller.signal.aborted) return
      ownedUrl = URL.createObjectURL(blob)
      setImageUrl(ownedUrl)
    }).catch(() => { if (!controller.signal.aborted) setError('Image content could not be loaded.') })
    return () => { controller.abort(); if (ownedUrl) URL.revokeObjectURL(ownedUrl) }
  }, [execution, tenantId, projectId, home.unavailable])

  async function request() {
    if (!tenantId || !asset) return
    setError('')
    const result = await submitThumbnail(config, tenantId, projectId, { sourceAssetId: asset, timestampSeconds: Number(time), imageFormat: 'jpeg', idempotencyKey: `ui-${projectId}-${asset}-${time}` })
    if (result.success) setExecution(result.data); else setError(result.error.message)
  }
  async function control(operation: typeof cancelThumbnail) {
    if (!tenantId || !execution) return
    setError('')
    const result = await operation(config, tenantId, projectId, execution.executionId, execution.generation)
    if (result.success) setExecution(result.data); else setError(result.error.message)
  }
  if (home.unavailable || !tenantId) return <main aria-label="Media thumbnail"><p role="alert">Workspace session is unavailable or expired.</p></main>
  return <main aria-label="Media thumbnail">
    <h1>Media thumbnail</h1>
    <label>Source media<select aria-label="Source media" value={asset} onChange={e => setAsset(e.target.value)} disabled={loadingAssets}>
      <option value="">{loadingAssets ? 'Loading authorized media…' : 'Choose source media'}</option>
      {assets.map(a => <option key={a.id} value={a.id}>{a.filename || a.id}</option>)}
    </select></label>
    <label>Timestamp (seconds)<input type="number" min="0" step="0.01" value={time} onChange={e => setTime(e.target.value)} /></label>
    <button disabled={!asset || !time || Boolean(execution && active(execution))} onClick={() => void request().catch(() => setError('Request could not be submitted.'))}>Request thumbnail</button>
    {execution && active(execution) && <button onClick={() => void control(cancelThumbnail).catch(() => setError('Cancellation failed.'))}>Cancel</button>}
    {execution?.state === 'FAILED' && !execution.retryBlocked && <button onClick={() => void control(retryThumbnail).catch(() => setError('Retry failed.'))}>Retry</button>}
    {error && <p role="alert">{error}</p>}
    {execution && <section aria-live="polite"><p>Status: {execution.state}</p>
      {execution.failureCode && <p role="alert">Failed: {execution.failureCode}</p>}
      {execution.retryBlocked && <p role="alert">Execution requires reconciliation before retry.</p>}
      {imageUrl && <><img src={imageUrl} alt="Committed thumbnail" /><a href={imageUrl} download="thumbnail">Download image</a></>}
    </section>}
  </main>
}
