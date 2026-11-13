import { useEffect, useRef, useState } from 'react'
import { assetGateway } from '../../api/app/asset.gateway'
import type { AssetSourcePin } from './gateways'
import { projectId } from './types'

interface Props { tenantId: string | null; project: string; onSelect: (source: AssetSourcePin) => void }

export function SourceBrowser({ tenantId, project, onSelect }: Props) {
  const [sources, setSources] = useState<readonly AssetSourcePin[]>([])
  const [file, setFile] = useState<File | null>(null)
  const [busy, setBusy] = useState(false)
  const [progress, setProgress] = useState(0)
  const [message, setMessage] = useState('')
  const [uploadFailed, setUploadFailed] = useState(false)
  const [selected, setSelected] = useState('')
  const key = useRef<string | null>(null)
  const generation = useRef(0)
  async function refresh() {
    if (!tenantId || !project) return
    const owned = ++generation.current
    const result = await assetGateway.listSourcePins(tenantId, projectId(project))
    if (owned !== generation.current) return
    if (result.ok) { setSources(result.value); setMessage(''); setUploadFailed(false) }
    else { setSources([]); setMessage(`${result.code}: ${result.message}`); setUploadFailed(false) }
  }
  useEffect(() => { setSources([]); setSelected(''); setFile(null); key.current = null; void refresh()
    return () => { generation.current += 1 }
  }, [tenantId, project])
  async function upload() {
    if (!tenantId || !project || !file || busy) return
    const commandId = key.current ?? `source-${crypto.randomUUID()}`
    key.current = commandId
    setBusy(true); setMessage(''); setUploadFailed(false); setProgress(0)
    try {
      const result = await assetGateway.uploadSource(tenantId, projectId(project), file, commandId, setProgress)
      if (!result.ok) { setMessage(`${result.code}: ${result.message}`); setUploadFailed(true); return }
      setMessage(`Accepted ${result.value.name} as Source Artifact ${result.value.artifactId}.`)
      setSelected(result.value.artifactId)
      onSelect(result.value)
      const listed = await assetGateway.listSourcePins(tenantId, projectId(project))
      if (listed.ok) setSources(listed.value)
    } catch (error) { setMessage(error instanceof Error ? error.message : 'Source upload failed.'); setUploadFailed(true) }
    finally { setBusy(false) }
  }
  return <section aria-label="Source media">
    <h3>Source media</h3>
    <label>Upload video or audio (64 MiB maximum) <input type="file" accept="video/mp4,video/webm,video/quicktime,audio/wav,audio/mpeg,audio/flac"
      onChange={event => { setFile(event.target.files?.[0] ?? null); key.current = null; setProgress(0); setMessage('') }} /></label>
    <button type="button" disabled={!file || !tenantId || busy} onClick={() => void upload()}>{busy ? 'Uploading…' : 'Upload source'}</button>
    {busy && <progress aria-label="Upload progress" max={100} value={progress}>{progress}%</progress>}
    <button type="button" disabled={!tenantId || busy} onClick={() => void refresh()}>Refresh sources</button>
    {message && <p role={uploadFailed ? 'alert' : 'status'}>{message}</p>}
    {sources.length === 0 ? <p>No accepted source media in this project.</p> : <ul>{sources.map(source =>
      <li key={source.artifactId}><button type="button" aria-pressed={selected === source.artifactId} onClick={() => { setSelected(source.artifactId); onSelect(source) }}>
        {source.name} · {source.streams.map(stream => stream.kind).join(', ')} · {source.duration} · {source.byteLength} bytes
      </button><small> Artifact {source.artifactId}</small></li>)}</ul>}
  </section>
}
