import { useEffect, useRef, useState } from 'react'
import { FormalRenderAPI } from '../../api/formal-render'
import { Button, Input } from '../../components/design-system'
import type { SemanticBatch } from '../../contracts/semantic-batch.generated'
import type { CanonicalHeadReference, EditorCommandAccess } from './gateways'
import type { ProjectedTrack } from './timeline-projection'
import { add, formatRational, parseRational } from './trim'

interface Plan {
  tenant: string
  project: string
  body: SemanticBatch
  digest: string
  ids: readonly string[]
  destinations: { id: string; start: string; end: string }[]
}
function response(error: unknown) {
  return (error as { response?: { status?: number; data?: { detail?: string } } })?.response
}

export function MultiClipMove({ head, tenant, tracks, visible, disabled, access, readback }: {
  head: CanonicalHeadReference | null
  tenant: string | null
  tracks: readonly ProjectedTrack[]
  visible: boolean
  disabled: boolean
  access: EditorCommandAccess
  readback: () => Promise<CanonicalHeadReference | undefined>
}) {
  const [trackId, setTrackId] = useState('')
  const [selection, setSelection] = useState<{ revision: string; hash: string; track: string; ids: string[] }>()
  const [offset, setOffset] = useState('')
  const [preview, setPreview] = useState<Plan | null>(null)
  const [retry, setRetry] = useState<Plan | null>(null)
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState('')
  const [failed, setFailed] = useState(false)
  const request = useRef<AbortController | null>(null)
  useEffect(() => () => request.current?.abort(), [])
  useEffect(() => { setSelection(undefined); setPreview(null); setOffset('') }, [head?.revisionId, head?.contentHash, visible])
  const track = tracks.find(t => t.trackId === trackId) ?? tracks.find(t => t.clips.length > 0) ?? tracks[0]
  const ids = selection?.revision === head?.revisionId && selection?.hash === head?.contentHash
    && selection?.track === track?.trackId ? selection?.ids ?? [] : []
  const locked = disabled || busy || access.blocked || !visible
  const editable = () => !locked && !retry && !request.current && access.canStart()
  const matches = (plan: Plan) => head?.revisionId === plan.body.baseRevisionId
    && head.contentHash === plan.body.baseContentHash && plan.ids === ids


  async function prepare() {
    if (!editable() || !head || !tenant || ids.length < 2) return
    const delta = parseRational(offset)
    if (!delta) { setFailed(true); setMessage('Enter a signed rational offset, such as -1/30 or 1/1.'); return }
    const body: SemanticBatch = { batchId: crypto.randomUUID(), baseRevisionId: head.revisionId,
      baseContentHash: head.contentHash, operations: [{ definitionId: 'timeline.move', version: '1.0',
        target: { clipIds: ids }, parameters: { delta: formatRational(delta), absolute: false } }] }
    const controller = new AbortController()
    request.current = controller
    setBusy(true); setPreview(null); setMessage(''); setFailed(false)
    try {
      const result = await FormalRenderAPI.previewBatch(tenant, head.projectId, body, controller.signal)
      if (!controller.signal.aborted) setPreview({
        tenant, project: head.projectId, body, digest: result.batchDigest, ids,
        destinations: (track?.clips ?? []).filter(clip => ids.includes(clip.clipId)).map(clip => ({
          id: clip.clipId, start: formatRational(add(clip.start, delta)), end: formatRational(add(clip.end, delta)),
        })),
      })
    } catch (error) {
      if (!controller.signal.aborted) {
        setFailed(true)
        setMessage('Move preview rejected: ' + (response(error)?.data?.detail ?? 'Placement could not be verified.'))
      }
    } finally {
      request.current = null
      if (!controller.signal.aborted) setBusy(false)
    }
  }

  async function confirm() {
    const plan = retry ?? preview
    if (!plan || !head || !tenant || !visible || request.current
        || (!retry && (!editable() || !matches(plan))) || !access.claim()) return
    const controller = new AbortController()
    request.current = controller
    setBusy(true); setMessage(''); setFailed(false)
    let retain = false
    try {
      const result = await FormalRenderAPI.commitBatch(plan.tenant, plan.project, plan.body, plan.body.baseRevisionId,
        plan.digest, controller.signal)
      if (controller.signal.aborted) return
      setRetry(null); setPreview(null)
      const current = await readback().catch(() => undefined)
      setMessage(result.revisionId === plan.body.baseRevisionId ? 'Move made no change. No revision was added.'
        : 'Move accepted at revision ' + result.revisionId + '.'
          + (current?.revisionId === result.revisionId && current.contentHash === result.contentHash
            ? '' : ' Reload HEAD to refresh the editor.'))
    } catch (error) {
      if (controller.signal.aborted) return
      const status = response(error)?.status
      setFailed(true)
      if (status === undefined || status >= 500 || status === 429) {
        retain = true
        setRetry(plan)
        setMessage('Move could not be confirmed. Retry sends the original confirmed batch.')
      } else {
        setRetry(null); setPreview(null)
        setMessage(status === 409 || status === 412 ? 'Move conflict (HTTP ' + status + '). Reload HEAD and select again.'
          : 'Move rejected: ' + (response(error)?.data?.detail ?? 'HTTP ' + status))
      }
    } finally {
      request.current = null
      if (!retain) access.release()
      if (!controller.signal.aborted) setBusy(false)
    }
  }

  if (!visible) return null
  return <section aria-label="Move selected clips">
    <fieldset disabled={locked || !!retry || !head || !track}>
      <legend>Move clips together on one track</legend>
      <label>Move track <select aria-label="Move track" value={track?.trackId ?? ''} onChange={event => {
        setTrackId(event.target.value); setSelection(undefined); setPreview(null)
      }}>{tracks.map(t => <option key={t.trackId} value={t.trackId}>{t.name}</option>)}</select></label>
      {track?.clips.map(clip => <label key={clip.clipId}>
        <input type="checkbox" checked={ids.includes(clip.clipId)} onChange={event => {
          if (!head || !editable()) return
          setSelection({ revision: head.revisionId, hash: head.contentHash, track: track.trackId,
            ids: event.target.checked ? [...ids, clip.clipId] : ids.filter(id => id !== clip.clipId) })
          setPreview(null)
        }} /> Select for move {clip.clipId}
      </label>)}
      <p role="status">{ids.length} selected{ids.length === 1 && '. Use the existing clip drag to move one clip.'}</p>
      <Button type="button" disabled={!ids.length} onClick={() => { setSelection(undefined); setPreview(null) }}>Clear move selection</Button>
      <label>Move offset (seconds) <Input value={offset} onChange={event => { setOffset(event.target.value); setPreview(null) }} /></label>
      <Button type="button" disabled={ids.length < 2 || !offset} onClick={() => void prepare()}>Preview selected move</Button>
    </fieldset>
    {preview && !retry && matches(preview) && <>
      <p>Previewed destinations (seconds)</p>
      <ul>{preview.destinations.map(clip => <li key={clip.id}>{clip.id} · {clip.start} to {clip.end}</li>)}</ul>
      <Button type="button" disabled={locked} onClick={() => void confirm()}>Confirm selected move</Button>
    </>}
    {retry && <Button type="button" disabled={busy || access.blocked} onClick={() => void confirm()}>Retry confirmed move</Button>}
    {message && <p role={failed ? 'alert' : 'status'}>{message}</p>}
  </section>
}
