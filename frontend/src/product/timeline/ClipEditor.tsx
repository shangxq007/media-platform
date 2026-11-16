import { useEffect, useRef, useState, type PointerEvent as ReactPointerEvent } from 'react'
import { FormalRenderAPI, type ClipEdit } from '../../api/formal-render'
import type { CanonicalHeadReference } from './gateways'
import { useProjectContext } from '../../foundation/projectContext'
import {
  clampTrimDelta, compare, formatRational, parseRational, pixelsToDelta, projectTrim, subtract, toSeconds,
  type ClipGeometry, type Rational, type TrimEdge,
} from './trim'

interface Props { head: CanonicalHeadReference | null; onRevisionChanged: () => Promise<void> }

/** Horizontal scale of the trim strip: one second of timeline occupies this many pixels. */
export const TRIM_PIXELS_PER_SECOND = 80
const DRAG_THRESHOLD_PX = 4

interface PendingTrim { edge: TrimEdge; delta: Rational }
interface PreviewedTrim extends PendingTrim { clipId: string; revisionId: string; contentHash: string; commandId: string }

const DEFAULT_RATE: Rational = { numerator: 1n, denominator: 1n }

function geometryOf(document: unknown): ClipGeometry[] {
  const tracks = (document as { tracks?: { trackId?: string; clips?: unknown[] }[] }).tracks ?? []
  const rows: ClipGeometry[] = []
  for (const track of tracks) {
    for (const raw of track.clips ?? []) {
      const clip = raw as Record<string, unknown>
      const clipId = typeof clip.clipId === 'string' ? clip.clipId : ''
      if (!clipId) continue
      const start = parseRational(clip.startTime as string)
      const end = parseRational(clip.endTime as string)
      const sourceStart = parseRational(clip.trimStart as string)
      const sourceEnd = parseRational(clip.trimEnd as string)
      if (!start || !end || !sourceStart || !sourceEnd) continue
      const mapping = clip.temporalMapping as { rate?: { numerator?: number; denominator?: number } } | undefined
      const rate = mapping?.rate?.numerator && mapping?.rate?.denominator
        ? { numerator: BigInt(mapping.rate.numerator), denominator: BigInt(mapping.rate.denominator) }
        : DEFAULT_RATE
      rows.push({ clipId, trackId: track.trackId ?? '', start, end, sourceStart, sourceEnd, rate })
    }
  }
  return rows
}

export function ClipEditor({ head, onRevisionChanged }: Props) {
  const project = useProjectContext()
  const [clips, setClips] = useState<ClipGeometry[]>([])
  const [selected, setSelected] = useState('')
  const [type, setType] = useState<ClipEdit['type']>('MOVE_CLIP')
  const [delta, setDelta] = useState('1/4')
  const [absolute, setAbsolute] = useState(false)
  const [edge, setEdge] = useState<TrimEdge>('END')
  const [pending, setPending] = useState<PendingTrim | null>(null)
  const [previewed, setPreviewed] = useState<PreviewedTrim | null>(null)
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState('')
  const [actionFailed, setActionFailed] = useState(false)
  const keys = useRef<Record<string, string>>({})
  const generation = useRef(0)
  const dragOrigin = useRef<number | null>(null)

  async function refresh() {
    if (!head) { setClips([]); setSelected(''); return }
    const owned = ++generation.current
    try {
      const snapshot = await FormalRenderAPI.snapshot(project.projectId, head.revisionId)
      const rows = geometryOf(JSON.parse(snapshot.canonicalTimelineJson))
      if (owned !== generation.current) return
      setClips(rows)
      setSelected(current => rows.some(row => row.clipId === current) ? current : rows[0]?.clipId ?? '')
    } catch (error) {
      if (owned === generation.current) {
        setClips([]); setActionFailed(false)
        setMessage(error instanceof Error ? error.message : 'Clip readback failed.')
      }
    }
  }

  useEffect(() => { void refresh(); return () => { generation.current += 1 } }, [head?.revisionId, project.projectId])
  useEffect(() => { setMessage(''); setActionFailed(false); keys.current = {} }, [project.projectId])

  const active = clips.find(clip => clip.clipId === selected) ?? null
  const projection = active && pending ? projectTrim(active, pending.edge, pending.delta) : null

  function commandIdFor(clipId: string, edgeValue: TrimEdge, deltaValue: Rational): string {
    const fingerprint = JSON.stringify([project.projectId, clipId, edgeValue, formatRational(deltaValue), head?.revisionId])
    keys.current[fingerprint] ??= crypto.randomUUID()
    return keys.current[fingerprint]
  }

  function beginTrim(trimEdge: TrimEdge, event: ReactPointerEvent<HTMLSpanElement>) {
    if (!head || !active || busy) return
    dragOrigin.current = event.clientX
    setPending({ edge: trimEdge, delta: { numerator: 0n, denominator: 1n } })
    event.currentTarget.setPointerCapture?.(event.pointerId)
  }

  function moveTrim(event: ReactPointerEvent<HTMLSpanElement>) {
    if (dragOrigin.current === null || !active || !pending) return
    const raw = pixelsToDelta(event.clientX - dragOrigin.current, TRIM_PIXELS_PER_SECOND)
    setPending({ edge: pending.edge, delta: clampTrimDelta(active, raw) })
  }

  async function endTrim(event: ReactPointerEvent<HTMLSpanElement>) {
    const origin = dragOrigin.current
    dragOrigin.current = null
    if (origin === null || !pending) return
    const moved = Math.abs(event.clientX - origin)
    if (moved < DRAG_THRESHOLD_PX) { setPending(null); return }
    await previewTrim(pending.edge, pending.delta)
  }

  function cancelTrim() {
    setPending(null); setPreviewed(null); setActionFailed(false)
    setMessage('Trim preview discarded. No revision was written.')
  }

  async function previewTrim(trimEdge: TrimEdge, deltaValue: Rational) {
    if (!head || !project.tenantId || !active || busy) return
    if (compare(deltaValue, { numerator: 0n, denominator: 1n }) <= 0) { setPending(null); return }
    setBusy(true); setMessage(''); setActionFailed(false)
    const commandId = commandIdFor(active.clipId, trimEdge, deltaValue)
    try {
      await FormalRenderAPI.previewOperation(project.tenantId, project.projectId, {
        type: 'TRIM_CLIP', baseRevisionId: head.revisionId, baseContentHash: head.contentHash,
        clipId: active.clipId, delta: formatRational(deltaValue), edge: trimEdge, commandId,
      })
      setPreviewed({ edge: trimEdge, delta: deltaValue, clipId: active.clipId,
        revisionId: head.revisionId, contentHash: head.contentHash, commandId })
      setPending({ edge: trimEdge, delta: deltaValue })
      const projected = projectTrim(active, trimEdge, deltaValue)
      setMessage(`Preview valid: source ${formatRational(projected.sourceStart)}s → ${formatRational(projected.sourceEnd)}s, `
        + `${toSeconds(projected.duration).toFixed(2)}s visible. Nothing was saved yet.`)
    } catch (error) {
      const status = (error as { response?: { status?: number } })?.response?.status
      setPreviewed(null)
      setActionFailed(true)
      setMessage(status === 409 || status === 412
        ? 'Revision conflict. Reload HEAD before previewing this trim.'
        : error instanceof Error ? error.message : 'Trim preview was rejected.')
    } finally { setBusy(false) }
  }

  async function applyTrim() {
    if (!head || !project.tenantId || !active || busy || !previewed) return
    if (previewed.clipId !== active.clipId || previewed.revisionId !== head.revisionId
        || previewed.contentHash !== head.contentHash) {
      setPreviewed(null); setActionFailed(true)
      setMessage('The timeline changed after the preview. Preview the trim again.')
      return
    }
    setBusy(true); setMessage(''); setActionFailed(false)
    try {
      const accepted = await FormalRenderAPI.applyOperation(project.tenantId, project.projectId, {
        type: 'TRIM_CLIP', baseRevisionId: head.revisionId, baseContentHash: head.contentHash,
        clipId: active.clipId, delta: formatRational(previewed.delta), edge: previewed.edge,
        commandId: previewed.commandId,
      })
      const authoritative = await FormalRenderAPI.head(project.projectId)
      if (accepted.revisionId !== authoritative.revisionId || accepted.contentHash !== authoritative.contentHash) {
        throw new Error('Operation accepted, but authoritative HEAD has changed. Reload before editing again.')
      }
      setPending(null); setPreviewed(null)
      await onRevisionChanged()
      setMessage(`Trim applied at revision ${authoritative.revisionId}.`)
    } catch (error) {
      const status = (error as { response?: { status?: number } })?.response?.status
      setMessage(status === 409 || status === 412
        ? 'Revision conflict. Reload HEAD and retry with a new operation.'
        : error instanceof Error ? error.message : 'Trim failed.')
      setActionFailed(true)
    } finally { setBusy(false) }
  }

  async function applySimple() {
    if (!head || !project.tenantId || !selected || busy) return
    const intent = { type, baseRevisionId: head.revisionId, baseContentHash: head.contentHash, clipId: selected,
      ...(type === 'DELETE_CLIP' ? {} : { delta }), ...(type === 'MOVE_CLIP' ? { absolute } : {}) }
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

  const stripWidth = Math.max(240, ...clips.map(clip => toSeconds(clip.end) * TRIM_PIXELS_PER_SECOND + 40))
  const previewValid = previewed !== null && pending !== null
    && previewed.clipId === active?.clipId && previewed.edge === pending.edge
    && compare(previewed.delta, pending.delta) === 0

  return <section aria-label="Edit existing clip">
    <h3>Edit existing clip</h3>
    <button type="button" disabled={!head || busy} onClick={() => void refresh()}>Refresh clips</button>
    <label>Clip <select aria-label="Clip" value={selected} onChange={event => { setSelected(event.target.value); setPending(null); setPreviewed(null) }}>
      {clips.map(clip => <option key={clip.clipId} value={clip.clipId}>{clip.clipId} {clip.trackId && `(${clip.trackId})`}</option>)}
    </select></label>

    <div role="group" aria-label="Timeline trim" data-pixels-per-second={TRIM_PIXELS_PER_SECOND}
         style={{ position: 'relative', height: 64, marginTop: 8, marginBottom: 8, overflowX: 'auto',
                  background: '#111827', border: '1px solid #1f2937', borderRadius: 4 }}>
      <div style={{ position: 'relative', width: stripWidth, height: 48, margin: 8 }}>
        {clips.map(clip => {
          const isActive = clip.clipId === selected
          const projected = isActive && projection?.valid ? projection : null
          const start = projected ? toSeconds(projected.timelineStart) : toSeconds(clip.start)
          const end = projected ? toSeconds(projected.timelineEnd) : toSeconds(clip.end)
          return <div key={clip.clipId} data-clip-id={clip.clipId}
                      aria-label={`Timeline clip ${clip.clipId}`}
                      style={{ position: 'absolute', left: start * TRIM_PIXELS_PER_SECOND,
                               width: Math.max(6, (end - start) * TRIM_PIXELS_PER_SECOND), top: 0, height: 40,
                               background: isActive ? '#1d4ed8aa' : '#374151aa',
                               border: isActive ? '1px solid #60a5fa' : '1px solid #4b5563', borderRadius: 3,
                               display: 'flex', alignItems: 'center', justifyContent: 'center',
                               fontSize: 11, color: '#e5e7eb', whiteSpace: 'nowrap', overflow: 'hidden' }}>
            <span aria-hidden="true">{clip.clipId} · {toSeconds(projected ? projected.duration : subtract(clip.end, clip.start)).toFixed(2)}s
              · src {toSeconds(projected ? projected.sourceStart : clip.sourceStart).toFixed(2)}–{toSeconds(projected ? projected.sourceEnd : clip.sourceEnd).toFixed(2)}</span>
            <span role="slider" tabIndex={0} aria-label={`Trim start edge ${clip.clipId}`} aria-valuetext="trim start"
                  onPointerDown={event => { setSelected(clip.clipId); setType('TRIM_CLIP'); beginTrim('START', event) }}
                  onPointerMove={moveTrim} onPointerUp={event => void endTrim(event)} onPointerCancel={() => { dragOrigin.current = null; setPending(null) }}
                  style={{ position: 'absolute', left: -3, top: 0, width: 8, height: '100%', cursor: 'ew-resize',
                           background: '#f59e0b', borderRadius: 2 }} />
            <span role="slider" tabIndex={0} aria-label={`Trim end edge ${clip.clipId}`} aria-valuetext="trim end"
                  onPointerDown={event => { setSelected(clip.clipId); setType('TRIM_CLIP'); beginTrim('END', event) }}
                  onPointerMove={moveTrim} onPointerUp={event => void endTrim(event)} onPointerCancel={() => { dragOrigin.current = null; setPending(null) }}
                  style={{ position: 'absolute', right: -3, top: 0, width: 8, height: '100%', cursor: 'ew-resize',
                           background: '#f59e0b', borderRadius: 2 }} />
          </div>
        })}
        {clips.length === 0 && <p style={{ color: '#9ca3af', fontSize: 12 }}>No clips in this revision.</p>}
      </div>
    </div>

    {projection && !projection.valid && <p role="alert">{projection.reason}</p>}
    {pending && active && <p role="status">
      Pending {pending.edge === 'START' ? 'left' : 'right'} trim by {formatRational(pending.delta)}s
      {' '}(source {formatRational(projectTrim(active, pending.edge, pending.delta).sourceStart)}s →
      {formatRational(projectTrim(active, pending.edge, pending.delta).sourceEnd)}s).
    </p>}
    {pending && <button type="button" disabled={busy} onClick={cancelTrim}>Cancel trim</button>}
    <button type="button" disabled={!pending || busy || !previewValid} onClick={() => void applyTrim()}>Apply trim</button>

    <label>Operation <select aria-label="Clip operation" value={type} onChange={event => { setType(event.target.value as ClipEdit['type']); setPending(null); setPreviewed(null) }}>
      <option value="MOVE_CLIP">Move</option><option value="TRIM_CLIP">Trim</option><option value="DELETE_CLIP">Delete</option>
    </select></label>
    {type !== 'DELETE_CLIP' && <label>Exact time <input aria-label="Exact time" value={delta} onChange={event => setDelta(event.target.value)} /></label>}
    {type === 'MOVE_CLIP' && <label><input type="checkbox" checked={absolute} onChange={event => setAbsolute(event.target.checked)} /> Absolute position</label>}
    {type === 'TRIM_CLIP' && <label>Edge <select aria-label="Trim edge" value={edge} onChange={event => setEdge(event.target.value as TrimEdge)}><option value="START">START</option><option value="END">END</option></select></label>}
    {type === 'TRIM_CLIP'
      ? <>
          <button type="button" disabled={!head || !selected || !project.tenantId || busy}
                  onClick={() => { const parsed = parseRational(delta); if (parsed) void previewTrim(edge, parsed) }}>Preview trim</button>
          <span> (drag a clip edge above, then apply) </span>
        </>
      : <button type="button" disabled={!head || !selected || !project.tenantId || busy} onClick={() => void applySimple()}>Apply operation</button>}
    {clips.length === 0 && <p>No clips in this revision.</p>}
    {message && <p role={actionFailed ? 'alert' : 'status'}>{message}</p>}
  </section>
}
