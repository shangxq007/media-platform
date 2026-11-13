import { useEffect, useRef, useState } from 'react'
import { FormalRenderAPI, type ClipEdit } from '../../api/formal-render'
import type { CanonicalHeadReference } from './gateways'
import { useProjectContext } from '../../foundation/projectContext'

interface Clip { clipId: string; trackId: string }
interface Props { head: CanonicalHeadReference | null; onRevisionChanged: () => Promise<void> }

export function ClipEditor({ head, onRevisionChanged }: Props) {
  const project = useProjectContext()
  const [clips, setClips] = useState<Clip[]>([])
  const [selected, setSelected] = useState('')
  const [type, setType] = useState<ClipEdit['type']>('MOVE_CLIP')
  const [delta, setDelta] = useState('1/4')
  const [absolute, setAbsolute] = useState(false)
  const [edge, setEdge] = useState<'START' | 'END'>('END')
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState('')
  const [actionFailed, setActionFailed] = useState(false)
  const keys = useRef<Record<string, string>>({})
  const generation = useRef(0)
  async function refresh() {
    if (!head) { setClips([]); setSelected(''); return }
    const owned = ++generation.current
    try {
      const snapshot = await FormalRenderAPI.snapshot(project.projectId, head.revisionId)
      const document = JSON.parse(snapshot.canonicalTimelineJson) as { tracks?: { trackId?: string; clips?: { clipId?: string }[] }[] }
      const rows = (document.tracks ?? []).flatMap(track => (track.clips ?? []).filter(clip => !!clip.clipId)
        .map(clip => ({ clipId: clip.clipId!, trackId: track.trackId ?? '' })))
      if (owned !== generation.current) return
      setClips(rows); setSelected(current => rows.some(row => row.clipId === current) ? current : rows[0]?.clipId ?? '')
    } catch (error) { if (owned === generation.current) { setClips([]); setActionFailed(false); setMessage(error instanceof Error ? error.message : 'Clip readback failed.') } }
  }
  useEffect(() => { void refresh(); return () => { generation.current += 1 } }, [head?.revisionId, project.projectId])
  useEffect(() => { setMessage(''); setActionFailed(false); keys.current = {} }, [project.projectId])
  async function apply() {
    if (!head || !project.tenantId || !selected || busy) return
    const intent = { type, baseRevisionId: head.revisionId, baseContentHash: head.contentHash, clipId: selected,
      ...(type === 'DELETE_CLIP' ? {} : { delta }), ...(type === 'MOVE_CLIP' ? { absolute } : {}),
      ...(type === 'TRIM_CLIP' ? { edge } : {}) }
    const fingerprint = JSON.stringify([project.projectId, intent])
    keys.current[fingerprint] ??= crypto.randomUUID()
    setBusy(true); setMessage(''); setActionFailed(false)
    try {
      const accepted = await FormalRenderAPI.applyOperation(project.tenantId, project.projectId,
        { ...intent, commandId: keys.current[fingerprint] })
      const authoritative = await FormalRenderAPI.head(project.projectId)
      if (accepted.revisionId !== authoritative.revisionId || accepted.contentHash !== authoritative.contentHash) {
        throw new Error('Operation accepted, but authoritative HEAD has changed. Reload before editing again.')
      }
      await onRevisionChanged()
      setMessage(`Operation accepted at revision ${authoritative.revisionId}.`)
    } catch (error) {
      const status = (error as { response?: { status?: number } })?.response?.status
      setMessage(status === 409 || status === 412 ? 'Revision conflict. Reload HEAD and retry with a new operation.'
        : error instanceof Error ? error.message : 'Clip operation failed.')
      setActionFailed(true)
    } finally { setBusy(false) }
  }
  return <section aria-label="Edit existing clip">
    <h3>Edit existing clip</h3>
    <button type="button" disabled={!head || busy} onClick={() => void refresh()}>Refresh clips</button>
    <label>Clip <select aria-label="Clip" value={selected} onChange={event => setSelected(event.target.value)}>
      {clips.map(clip => <option key={clip.clipId} value={clip.clipId}>{clip.clipId} {clip.trackId && `(${clip.trackId})`}</option>)}
    </select></label>
    <label>Operation <select aria-label="Clip operation" value={type} onChange={event => setType(event.target.value as ClipEdit['type'])}>
      <option value="MOVE_CLIP">Move</option><option value="TRIM_CLIP">Trim</option><option value="DELETE_CLIP">Delete</option>
    </select></label>
    {type !== 'DELETE_CLIP' && <label>Exact time <input aria-label="Exact time" value={delta} onChange={event => setDelta(event.target.value)} /></label>}
    {type === 'MOVE_CLIP' && <label><input type="checkbox" checked={absolute} onChange={event => setAbsolute(event.target.checked)} /> Absolute position</label>}
    {type === 'TRIM_CLIP' && <label>Edge <select value={edge} onChange={event => setEdge(event.target.value as 'START' | 'END')}><option>START</option><option>END</option></select></label>}
    <button type="button" disabled={!head || !selected || !project.tenantId || busy} onClick={() => void apply()}>Apply operation</button>
    {clips.length === 0 && <p>No clips in this revision.</p>}
    {message && <p role={actionFailed ? 'alert' : 'status'}>{message}</p>}
  </section>
}
