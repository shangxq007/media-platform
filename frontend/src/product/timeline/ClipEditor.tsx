import { useEffect, useRef, useState, type KeyboardEvent as ReactKeyboardEvent, type PointerEvent as ReactPointerEvent } from 'react'
import { FormalRenderAPI, type ClipEdit } from '../../api/formal-render'
import type { CanonicalHeadReference } from './gateways'
import { useProjectContext } from '../../foundation/projectContext'
import {
  clampTrimDelta, compare, formatRational, parseRational, pixelsToDelta, projectTrim, subtract, toSeconds,
  type ClipGeometry, type Rational, type TrimEdge,
} from './trim'
import { describeMove, draggedStart, projectMove } from './move'

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
  const [splitTime, setSplitTime] = useState('')
  const [pending, setPending] = useState<PendingTrim | null>(null)
  const [previewed, setPreviewed] = useState<PreviewedTrim | null>(null)
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState('')
  const [actionFailed, setActionFailed] = useState(false)
  const keys = useRef<Record<string, string>>({})
  const generation = useRef(0)
  const dragOrigin = useRef<number | null>(null)
  const [moving, setMoving] = useState<{ clipId: string; start: Rational; originX: number } | null>(null)
  const moveOrigin = useRef<number | null>(null)

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

  function commandIdFor(clipId: string, kind: string, deltaValue: Rational, edgeValue?: TrimEdge): string {
    const fingerprint = JSON.stringify([project.projectId, clipId, kind, edgeValue ?? null,
      formatRational(deltaValue), head?.revisionId])
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
    const commandId = commandIdFor(active.clipId, 'TRIM_CLIP', deltaValue, trimEdge)
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


  function beginMove(clip: ClipGeometry, event: ReactPointerEvent<HTMLDivElement>) {
    if (!head || busy) return
    event.stopPropagation()
    setSelected(clip.clipId); setType('MOVE_CLIP')
    setPending(null); setPreviewed(null)
    moveOrigin.current = event.clientX
    setMoving({ clipId: clip.clipId, start: clip.start, originX: event.clientX })
    event.currentTarget.setPointerCapture?.(event.pointerId)
  }

  function dragMove(event: ReactPointerEvent<HTMLDivElement>) {
    if (moveOrigin.current === null || !moving) return
    const clip = clips.find(row => row.clipId === moving.clipId)
    if (!clip) return
    setMoving({ ...moving, start: draggedStart(clip, event.clientX - moveOrigin.current, TRIM_PIXELS_PER_SECOND) })
  }

  async function endMove(event: ReactPointerEvent<HTMLDivElement>) {
    const origin = moveOrigin.current
    moveOrigin.current = null
    const current = moving
    setMoving(null)
    if (origin === null || !current) return
    if (Math.abs(event.clientX - origin) < DRAG_THRESHOLD_PX) return
    const clip = clips.find(row => row.clipId === current.clipId)
    if (!clip) return
    const projection = projectMove(clip, current.start)
    if (!projection.valid) { setActionFailed(true); setMessage(projection.reason ?? 'Move rejected.'); return }
    await applyMove(clip, projection.start)
  }

  async function applyMove(clip: ClipGeometry, targetStart: Rational) {
    if (!head || !project.tenantId || busy) return
    setBusy(true); setMessage(''); setActionFailed(false)
    const commandId = commandIdFor(clip.clipId, 'MOVE_CLIP', targetStart)
    try {
      await FormalRenderAPI.previewOperation(project.tenantId, project.projectId, {
        type: 'MOVE_CLIP', baseRevisionId: head.revisionId, baseContentHash: head.contentHash,
        clipId: clip.clipId, delta: formatRational(targetStart), absolute: true, commandId,
      })
      const accepted = await FormalRenderAPI.applyOperation(project.tenantId, project.projectId, {
        type: 'MOVE_CLIP', baseRevisionId: head.revisionId, baseContentHash: head.contentHash,
        clipId: clip.clipId, delta: formatRational(targetStart), absolute: true, commandId,
      })
      const authoritative = await FormalRenderAPI.head(project.projectId)
      if (accepted.revisionId !== authoritative.revisionId || accepted.contentHash !== authoritative.contentHash) {
        throw new Error('Operation accepted, but authoritative HEAD has changed. Reload before editing again.')
      }
      await onRevisionChanged()
      const projection = projectMove(clip, targetStart)
      setMessage(`Clip moved to ${describeMove(projection)} at revision ${authoritative.revisionId}.`)
    } catch (error) {
      const status = (error as { response?: { status?: number } })?.response?.status
      setMessage(status === 409 || status === 412
        ? 'Revision conflict. The clip stays at its authoritative position; reload and retry.'
        : error instanceof Error ? error.message : 'Clip move failed.')
      setActionFailed(true)
      await onRevisionChanged()
    } finally { setBusy(false) }
  }

  async function deleteSelected() {
    if (!head || !project.tenantId || !selected || busy) return
    setBusy(true); setMessage(''); setActionFailed(false)
    const commandId = commandIdFor(selected, 'DELETE_CLIP', { numerator: 0n, denominator: 1n })
    const removed = clips.find(clip => clip.clipId === selected)
    try {
      const accepted = await FormalRenderAPI.applyOperation(project.tenantId, project.projectId, {
        type: 'DELETE_CLIP', baseRevisionId: head.revisionId, baseContentHash: head.contentHash,
        clipId: selected, commandId,
      })
      const authoritative = await FormalRenderAPI.head(project.projectId)
      if (accepted.revisionId !== authoritative.revisionId || accepted.contentHash !== authoritative.contentHash) {
        throw new Error('Operation accepted, but authoritative HEAD has changed. Reload before editing again.')
      }
      await onRevisionChanged()
      setMessage(`Clip ${removed?.clipId ?? selected} deleted at revision ${authoritative.revisionId}. `
        + 'The source media is retained.')
    } catch (error) {
      const status = (error as { response?: { status?: number } })?.response?.status
      setMessage(status === 409 || status === 412
        ? 'Revision conflict. Reload HEAD and retry the delete.'
        : error instanceof Error ? error.message : 'Clip delete failed.')
      setActionFailed(true)
      await onRevisionChanged()
    } finally { setBusy(false) }
  }

  async function splitSelected() {
    if (!head || !project.tenantId || !active || busy) return
    const parsed = parseRational(splitTime)
    if (!parsed || compare(parsed, active.start) <= 0 || compare(parsed, active.end) >= 0) {
      setActionFailed(true)
      setMessage('Split time must be strictly inside the selected clip (exclusive start and end).')
      return
    }
    const rightClipId = crypto.randomUUID()
    const fingerprint = JSON.stringify([project.projectId, active.clipId, 'SPLIT_CLIP',
      formatRational(parsed), head.revisionId, rightClipId])
    keys.current[fingerprint] ??= crypto.randomUUID()
    const commandId = keys.current[fingerprint]
    setBusy(true); setMessage(''); setActionFailed(false)
    try {
      await FormalRenderAPI.previewOperation(project.tenantId, project.projectId, {
        type: 'SPLIT_CLIP', baseRevisionId: head.revisionId, baseContentHash: head.contentHash,
        clipId: active.clipId, splitTime: formatRational(parsed), rightClipId, commandId,
      })
      const accepted = await FormalRenderAPI.applyOperation(project.tenantId, project.projectId, {
        type: 'SPLIT_CLIP', baseRevisionId: head.revisionId, baseContentHash: head.contentHash,
        clipId: active.clipId, splitTime: formatRational(parsed), rightClipId, commandId,
      })
      const authoritative = await FormalRenderAPI.head(project.projectId)
      if (accepted.revisionId !== authoritative.revisionId || accepted.contentHash !== authoritative.contentHash) {
        throw new Error('Split accepted, but authoritative HEAD has changed. Reload before editing again.')
      }
      setSelected(rightClipId)
      await onRevisionChanged()
      setMessage(`Clip split at ${formatRational(parsed)}s; left ${active.clipId} and right ${rightClipId} `
        + `are independent clips at revision ${authoritative.revisionId}.`)
    } catch (error) {
      const status = (error as { response?: { status?: number } })?.response?.status
      setMessage(status === 409 || status === 412
        ? 'Revision conflict. Reload HEAD and retry the split.'
        : error instanceof Error ? error.message : 'Split failed.')
      setActionFailed(true)
      await onRevisionChanged()
    } finally { setBusy(false) }
  }

  function onSectionKeyDown(event: ReactKeyboardEvent<HTMLElement>) {
    if (event.key !== 'Delete' && event.key !== 'Backspace') return
    const target = event.target as HTMLElement | null
    const tag = target?.tagName?.toLowerCase()
    if (tag === 'input' || tag === 'select' || tag === 'textarea' || target?.isContentEditable) return
    if (!selected || busy) return
    event.preventDefault()
    void deleteSelected()
  }

  const stripWidth = Math.max(240, ...clips.map(clip => toSeconds(clip.end) * TRIM_PIXELS_PER_SECOND + 40))
  const previewValid = previewed !== null && pending !== null
    && previewed.clipId === active?.clipId && previewed.edge === pending.edge
    && compare(previewed.delta, pending.delta) === 0

  return <section aria-label="Edit existing clip" tabIndex={0} onKeyDown={onSectionKeyDown}>
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
          const movingThis = moving?.clipId === clip.clipId
          const moveProjection = movingThis ? projectMove(clip, moving.start) : null
          const left = movingThis && moveProjection?.valid ? toSeconds(moveProjection.start) : start
          const right = movingThis && moveProjection?.valid ? toSeconds(moveProjection.end) : end
          return <div key={clip.clipId} data-clip-id={clip.clipId}
                      data-clip-body={clip.clipId}
                      aria-label={`Timeline clip ${clip.clipId}`}
                      onPointerDown={event => beginMove(clip, event)}
                      onClick={() => setSelected(clip.clipId)}
                      onPointerMove={dragMove}
                      onPointerUp={event => void endMove(event)}
                      onPointerCancel={() => { moveOrigin.current = null; setMoving(null) }}
                      title="Drag the clip body to move it; drag an orange edge to trim"
                      style={{ position: 'absolute', left: left * TRIM_PIXELS_PER_SECOND,
                               width: Math.max(6, (right - left) * TRIM_PIXELS_PER_SECOND), top: 0, height: 40,
                               background: isActive ? '#1d4ed8aa' : '#374151aa',
                               border: isActive ? '1px solid #60a5fa' : '1px solid #4b5563', borderRadius: 3,
                               display: 'flex', alignItems: 'center', justifyContent: 'center',
                               fontSize: 11, color: '#e5e7eb', whiteSpace: 'nowrap', overflow: 'hidden' }}>
            <span aria-hidden="true">{clip.clipId} · {toSeconds(projected ? projected.duration : subtract(clip.end, clip.start)).toFixed(2)}s
              · src {toSeconds(projected ? projected.sourceStart : clip.sourceStart).toFixed(2)}–{toSeconds(projected ? projected.sourceEnd : clip.sourceEnd).toFixed(2)}</span>
            <span role="slider" tabIndex={0} aria-label={`Trim start edge ${clip.clipId}`} aria-valuetext="trim start"
                  onPointerDown={event => { event.stopPropagation(); setSelected(clip.clipId); setType('TRIM_CLIP'); beginTrim('START', event) }}
                  onPointerMove={moveTrim} onPointerUp={event => void endTrim(event)} onPointerCancel={() => { dragOrigin.current = null; setPending(null) }}
                  style={{ position: 'absolute', left: -3, top: 0, width: 8, height: '100%', cursor: 'ew-resize',
                           background: '#f59e0b', borderRadius: 2 }} />
            <span role="slider" tabIndex={0} aria-label={`Trim end edge ${clip.clipId}`} aria-valuetext="trim end"
                  onPointerDown={event => { event.stopPropagation(); setSelected(clip.clipId); setType('TRIM_CLIP'); beginTrim('END', event) }}
                  onPointerMove={moveTrim} onPointerUp={event => void endTrim(event)} onPointerCancel={() => { dragOrigin.current = null; setPending(null) }}
                  style={{ position: 'absolute', right: -3, top: 0, width: 8, height: '100%', cursor: 'ew-resize',
                           background: '#f59e0b', borderRadius: 2 }} />
          </div>
        })}
        {clips.length === 0 && <p style={{ color: '#9ca3af', fontSize: 12 }}>No clips in this revision.</p>}
      </div>
    </div>

    {projection && !projection.valid && <p role="alert">{projection.reason}</p>}
    {moving && (() => { const clip = clips.find(row => row.clipId === moving.clipId); const projection = clip ? projectMove(clip, moving.start) : null; return projection
      ? <p role="status">Moving {moving.clipId} to {projection.valid ? describeMove(projection) : projection.reason}</p> : null })()}
    <button type="button" aria-label="Delete selected clip" disabled={!head || !selected || !project.tenantId || busy}
            onClick={() => void deleteSelected()}>Delete selected clip</button>
    {pending && active && <p role="status">
      Pending {pending.edge === 'START' ? 'left' : 'right'} trim by {formatRational(pending.delta)}s
      {' '}(source {formatRational(projectTrim(active, pending.edge, pending.delta).sourceStart)}s →
      {formatRational(projectTrim(active, pending.edge, pending.delta).sourceEnd)}s).
    </p>}
    {pending && <button type="button" disabled={busy} onClick={cancelTrim}>Cancel trim</button>}
    <button type="button" disabled={!pending || busy || !previewValid} onClick={() => void applyTrim()}>Apply trim</button>

    <label>Operation <select aria-label="Clip operation" value={type} onChange={event => { setType(event.target.value as ClipEdit['type']); setPending(null); setPreviewed(null) }}>
      <option value="MOVE_CLIP">Move</option><option value="TRIM_CLIP">Trim</option><option value="DELETE_CLIP">Delete</option><option value="SPLIT_CLIP">Split</option>
    </select></label>
    {type !== 'DELETE_CLIP' && type !== 'SPLIT_CLIP' && <label>Exact time <input aria-label="Exact time" value={delta} onChange={event => setDelta(event.target.value)} /></label>}
    {type === 'SPLIT_CLIP' && <label>Split time <input aria-label="Split time" value={splitTime} onChange={event => setSplitTime(event.target.value)} /></label>}
    {type === 'MOVE_CLIP' && <label><input type="checkbox" checked={absolute} onChange={event => setAbsolute(event.target.checked)} /> Absolute position</label>}
    {type === 'TRIM_CLIP' && <label>Edge <select aria-label="Trim edge" value={edge} onChange={event => setEdge(event.target.value as TrimEdge)}><option value="START">START</option><option value="END">END</option></select></label>}
    {type === 'TRIM_CLIP'
      ? <>
          <button type="button" disabled={!head || !selected || !project.tenantId || busy}
                  onClick={() => { const parsed = parseRational(delta); if (parsed) void previewTrim(edge, parsed) }}>Preview trim</button>
          <span> (drag a clip edge above, then apply) </span>
        </>
      : type === 'SPLIT_CLIP'
        ? <button type="button" aria-label="Split selected clip" disabled={!head || !selected || !project.tenantId || busy}
                  onClick={() => void splitSelected()}>Split clip</button>
        : <button type="button" disabled={!head || !selected || !project.tenantId || busy} onClick={() => void applySimple()}>Apply operation</button>}
    {clips.length === 0 && <p>No clips in this revision.</p>}
    {message && <p role={actionFailed ? 'alert' : 'status'}>{message}</p>}
  </section>
}
