import { useEffect, useRef, useState } from 'react'
import { FormalRenderAPI } from '../../api/formal-render'
import { Button } from '../../components/design-system'
import type { SemanticBatch } from '../../contracts/semantic-batch.generated'
import type { CanonicalHeadReference, EditorCommandAccess } from './gateways'
import type { ProjectedTrack } from './timeline-projection'
import { compare, formatRational, rational, toSeconds, type Rational } from './trim'

interface Clipboard {
  head: CanonicalHeadReference
  trackId: string
  clipIds: string[]
}
interface Preview {
  clipboard: Clipboard
  body: SemanticBatch
  digest: string
  start: string
}
const sameHead = (first: CanonicalHeadReference, second: CanonicalHeadReference | null) =>
  first.projectId === second?.projectId && first.revisionId === second.revisionId && first.contentHash === second.contentHash
const MAX_COPY_CLIPS = 64

function pastePosition(seconds: number, exactEnd: Rational | null): Rational | null {
  if (!Number.isFinite(seconds) || seconds < 0) return null
  // The preview clamps to this endpoint. Use its authored value, not its numeric rendering.
  if (exactEnd && seconds === toSeconds(exactEnd)) return exactEnd
  if (!Number.isSafeInteger(Math.floor(seconds * 1_000_000))) return null
  if (seconds < 0.000001) return rational(0n, 1n)
  // Keep interior placement at microsecond resolution without binary multiplication error.
  // Truncate the decimal instead of rounding an interior position forward into a boundary.
  const [whole, fraction = ''] = String(seconds).split('.')
  return rational(BigInt(whole) * 1_000_000n + BigInt(fraction.slice(0, 6).padEnd(6, '0')), 1_000_000n)
}

function failure(error: unknown) {
  const response = (error as { response?: { status?: number; data?: { detail?: string } } })?.response
  return { status: response?.status, detail: response?.data?.detail }
}

export function ClipClipboard({ head, tenant, tracks, playhead, exactEnd, visible, disabled, access, readback }: {
  head: CanonicalHeadReference | null
  tenant: string | null
  tracks: readonly ProjectedTrack[]
  playhead: number
  exactEnd: Rational | null
  visible: boolean
  disabled: boolean
  access: EditorCommandAccess
  readback: () => Promise<CanonicalHeadReference | undefined>
}) {
  const [trackId, setTrackId] = useState('')
  const [selected, setSelected] = useState<string[]>([])
  const [clipboard, setClipboard] = useState<Clipboard | null>(null)
  const [plan, setPlan] = useState<Preview | null>(null)
  const [retry, setRetry] = useState<Preview | null>(null)
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState('')
  const [rejected, setRejected] = useState(false)
  const pending = useRef<AbortController | null>(null)
  useEffect(() => () => pending.current?.abort(), [])
  const track = tracks.find(t => t.trackId === trackId) ?? tracks[0]
  const locked = disabled || busy || access.blocked || !visible
  const freshCopy = clipboard !== null && sameHead(clipboard.head, head)
  const canPrepare = () => !locked && !pending.current && retry === null && access.canStart()

  function copy() {
    if (!canPrepare() || !track || !head) return
    const ids = track.clips.filter(clip => selected.includes(clip.clipId))
      .sort((a, b) => compare(a.start, b.start) || (a.clipId < b.clipId ? -1 : a.clipId === b.clipId ? 0 : 1))
      .map(clip => clip.clipId)
    if (!ids.length || ids.length > MAX_COPY_CLIPS) return
    setClipboard({ head, trackId: track.trackId, clipIds: ids })
    setPlan(null); setMessage(''); setRejected(false)
  }

  async function preview() {
    if (!canPrepare() || !clipboard || !freshCopy || !tenant || !head) return
    const position = pastePosition(playhead, exactEnd)
    if (!position) return
    const start = formatRational(position)
    const body: SemanticBatch = {
      batchId: crypto.randomUUID(), baseRevisionId: head.revisionId, baseContentHash: head.contentHash,
      operations: [{ definitionId: 'timeline.clip.paste', version: '1.0', parameters: {
        trackId: clipboard.trackId, sourceClipIds: clipboard.clipIds, timelineStart: start,
        newClipIds: clipboard.clipIds.map(() => crypto.randomUUID()),
      } }],
    }
    const controller = new AbortController()
    pending.current = controller
    setBusy(true); setPlan(null); setMessage(''); setRejected(false)
    try {
      const result = await FormalRenderAPI.previewBatch(tenant, head.projectId, body, controller.signal)
      if (!controller.signal.aborted) setPlan({ clipboard, body, digest: result.batchDigest, start })
    } catch (error) {
      if (!controller.signal.aborted) {
        setRejected(true)
        setMessage('Paste preview rejected: ' + (failure(error).detail ?? 'Could not validate this placement.'))
      }
    } finally {
      pending.current = null
      if (!controller.signal.aborted) setBusy(false)
    }
  }

  async function confirm() {
    const attempt = retry ?? plan
    if (!attempt || !tenant || !visible || pending.current || !access.canStart()
        || (!retry && (locked || !sameHead(attempt.clipboard.head, head))) || !access.claim()) return
    const controller = new AbortController()
    pending.current = controller
    setBusy(true); setMessage(''); setRejected(false)
    let retain = false
    try {
      const accepted = await FormalRenderAPI.commitBatch(tenant, attempt.clipboard.head.projectId, attempt.body,
        attempt.body.baseRevisionId, attempt.digest, controller.signal)
      if (controller.signal.aborted) return
      setPlan(null); setRetry(null)
      const current = await readback().catch(() => undefined)
      if (current?.revisionId === accepted.revisionId && current.contentHash === accepted.contentHash) {
        setClipboard({ ...attempt.clipboard, head: current })
      }
      setMessage(accepted.revisionId === attempt.body.baseRevisionId ? 'Paste created no revision.'
        : 'Paste accepted at revision ' + accepted.revisionId + '.'
        + (current?.revisionId === accepted.revisionId && current.contentHash === accepted.contentHash
          ? '' : current ? ' Current HEAD is ' + current.revisionId + '.' : ' Reload HEAD to refresh the editor.'))
    } catch (error) {
      if (controller.signal.aborted) return
      const { status, detail } = failure(error)
      setRejected(true)
      if (status === undefined || status >= 500 || status === 429) {
        setRetry(attempt)
        retain = true
        setMessage('Paste could not be confirmed. Retry sends the original confirmed batch.')
      } else {
        setPlan(null); setRetry(null)
        setMessage(status === 409 || status === 412 ? 'Paste conflict (HTTP ' + status + '). Reload HEAD and copy again.'
          : 'Paste rejected: ' + (detail ?? 'HTTP ' + status))
      }
    } finally {
      pending.current = null
      if (!retain) access.release()
      if (!controller.signal.aborted) setBusy(false)
    }
  }

  if (!visible) return null
  return <section aria-label="Clip clipboard">
    <fieldset disabled={locked || retry !== null || !head || !track}>
      <legend>Copy clips from one track</legend>
      <label>Copy track <select aria-label="Copy track" value={track?.trackId ?? ''}
        onChange={event => { setTrackId(event.target.value); setSelected([]) }}>
        {tracks.map(t => <option key={t.trackId} value={t.trackId}>{t.name}</option>)}
      </select></label>
      {track?.clips.map(clip => <label key={clip.clipId}><input type="checkbox" checked={selected.includes(clip.clipId)}
        disabled={selected.length >= MAX_COPY_CLIPS && !selected.includes(clip.clipId)}
        onChange={event => setSelected(event.target.checked
          ? [...selected, clip.clipId] : selected.filter(id => id !== clip.clipId))} /> Copy clip {clip.clipId}</label>)}
      <Button type="button" disabled={!selected.length} onClick={copy}>Copy selected clips</Button>
    </fieldset>
    {clipboard && <p role="status">{clipboard.clipIds.length} {clipboard.clipIds.length === 1 ? 'clip' : 'clips'} copied.
      {!freshCopy && ' HEAD changed since copying. Reload HEAD if needed and copy again.'}</p>}
    <Button type="button" disabled={locked || retry !== null || !freshCopy} onClick={() => void preview()}>Preview paste at playhead</Button>
    {plan && !retry && <p>Paste preview starts at {plan.start}s on {plan.clipboard.trackId}.
      <Button type="button" disabled={locked || !freshCopy} onClick={() => void confirm()}>Confirm paste</Button>
      <Button type="button" disabled={locked} onClick={() => setPlan(null)}>Discard paste preview</Button>
    </p>}
    {retry && <Button type="button" disabled={busy || access.blocked} onClick={() => void confirm()}>Retry confirmed paste</Button>}
    {message && <p role={rejected ? 'alert' : 'status'}>{message}</p>}
  </section>
}
