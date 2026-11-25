import { aspectLabel, type CompositionAspectRatio } from '../../contracts/app/composition-aspect'
import { COMPOSITION_PRESETS, customCompositionAspect } from './composition-raster'
import { useEffect, useRef, useState, type KeyboardEvent as ReactKeyboardEvent, type PointerEvent as ReactPointerEvent } from 'react'
import { FormalRenderAPI, type ClipEdit } from '../../api/formal-render'
import { audioRouteFor, IDENTITY_VIDEO_EFFECT, IDENTITY_VIDEO_TRANSFORM, projectSnapshotJson, videoTransformParameters, type ProjectedClip, type ProjectedAudioRoute, type ProjectedTrack, type ProjectedTransition, type ProjectedVideoEffect, type ProjectedVideoTransform } from './timeline-projection'
import { readEditingBranch } from '../../api/app/timeline-query.gateway'
import { projectId } from './types'
import type { CanonicalHeadReference, EditorCommandAccess } from './gateways'
import { Button, Input } from '../../components/design-system'
import { useProjectContext } from '../../foundation/projectContext'
import {
  add, clampTrimDelta, compare, formatRational, parseRational, pixelsToDelta, projectTrim, subtract, toSeconds,
  type ClipGeometry, type Rational, type TrimEdge, ZERO,
} from './trim'
import { describeMove, draggedStart, projectMove } from './move'
import { DRAG_THRESHOLD_PX, TIMELINE_PIXELS_PER_SECOND, movePlacement, nearestSnap, shiftedRange, snapDescription, snapTargets, type SnapMatch } from './snapping'
import type { MultiMoveGesture } from './MultiClipMove'

interface Props {
  head: CanonicalHeadReference | null
  historicalRevisionId?: string
  editingBranch?: string
  trimAccess?: EditorCommandAccess
  onDraft?: (hasDraft: boolean) => void
  onWriteLock?: (locked: boolean) => void
  onRevisionChanged: () => Promise<void>
  onTrimAccepted?: (base: string, hash: string, accepted: { revisionId: string; contentHash: string }) => void
  onTrimReadback?: () => Promise<CanonicalHeadReference | undefined>
  focusClipId?: string | null
  playhead?: number
  snap?: boolean
  snapPlayhead?: Rational | null
  multiMove?: MultiMoveGesture
  selectedTrackId?: string | null
  onSelectTrack?: (trackId: string) => void
}

/** Horizontal scale of the trim strip: one second of timeline occupies this many pixels. */
export const TRIM_PIXELS_PER_SECOND = TIMELINE_PIXELS_PER_SECOND

interface PendingTrim { edge: TrimEdge; delta: Rational; match?: SnapMatch }
interface PreviewedTrim extends PendingTrim { digest: string; command: ClipEdit }
interface RetryClip { command: ClipEdit; label: string; digest?: string }
interface ConfirmedTrim { command: ClipEdit; digest: string; tenantId: string; projectId: string }

const DEFAULT_RATE: Rational = { numerator: 1n, denominator: 1n }

type EditorClip = ClipGeometry & Pick<ProjectedClip, 'artifactId' | 'contentDigest' | 'mediaStreamId'>

function geometryOf(clips: readonly ProjectedClip[]): EditorClip[] {
  return clips.map(clip => ({
    clipId: clip.clipId, trackId: clip.trackId, start: clip.start, end: clip.end,
    sourceStart: clip.sourceStart, sourceEnd: clip.sourceEnd, rate: clip.rate,
    artifactId: clip.artifactId, contentDigest: clip.contentDigest, mediaStreamId: clip.mediaStreamId,
  }))
}

export function ClipEditor({ head, historicalRevisionId, onRevisionChanged, onTrimReadback, onTrimAccepted, focusClipId = null, playhead = 0,
  selectedTrackId, onSelectTrack, snap = false, snapPlayhead = null, multiMove, editingBranch = 'main', onDraft, onWriteLock, trimAccess }: Props) {
  const project = useProjectContext()
  const trimOnly = editingBranch !== "main"
  const [formChanged, setFormChanged] = useState(false)
  const [aspectRatio, setAspectRatio] = useState<CompositionAspectRatio | null>(null)
  const [aspectWidth, setAspectWidth] = useState('')
  const [aspectHeight, setAspectHeight] = useState('')
  const viewedRevisionId = historicalRevisionId ?? head?.revisionId
  const [tracks, setTracks] = useState<readonly ProjectedTrack[]>([])
  const [audioRoutes, setAudioRoutes] = useState<readonly ProjectedAudioRoute[]>([])
  const [transforms, setTransforms] = useState<Record<string, ProjectedVideoTransform>>({})
  const [effects, setEffects] = useState<Record<string, ProjectedVideoEffect>>({})
  const [transitions, setTransitions] = useState<readonly ProjectedTransition[]>([])
  const [destinationTrackId, setDestinationTrackId] = useState('')
  const trackCommands = useRef<Partial<Record<'VIDEO' | 'AUDIO', { revision: string; id: string }>>>({})
  const [clips, setClips] = useState<EditorClip[]>([])
  const [selected, setSelected] = useState('')
  const [audioGain, setAudioGain] = useState(1)
  const [audioMute, setAudioMute] = useState(false)
  const [audioPan, setAudioPan] = useState(0)
  const [tx, setTx] = useState(0)
  const [ty, setTy] = useState(0)
  const [sx, setSx] = useState(1)
  const [sy, setSy] = useState(1)
  const [rotation, setRotation] = useState(0)
  const [cropLeft, setCropLeft] = useState(0)
  const [cropRight, setCropRight] = useState(1)
  const [cropTop, setCropTop] = useState(0)
  const [cropBottom, setCropBottom] = useState(1)
  const [opacity, setOpacity] = useState(1)
  const [brightness, setBrightness] = useState(0)
  const [contrast, setContrast] = useState(1)
  const [saturation, setSaturation] = useState(1)
  const [grayscale, setGrayscale] = useState(false)
  const [fadeIn, setFadeIn] = useState(0)
  const [fadeOut, setFadeOut] = useState(0)
  const [effectEnabled, setEffectEnabled] = useState(true)
  const [transitionId, setTransitionId] = useState('')
  const [incomingClipId, setIncomingClipId] = useState('')
  const [transitionDuration, setTransitionDuration] = useState('1/1')
  const [type, setType] = useState<ClipEdit['type']>('MOVE_CLIP')
  const [delta, setDelta] = useState('1/4')
  const [absolute, setAbsolute] = useState(false)
  const [edge, setEdge] = useState<TrimEdge>('END')
  const [splitTime, setSplitTime] = useState('')
  const [pending, setPending] = useState<PendingTrim | null>(null)
  const [previewed, setPreviewed] = useState<PreviewedTrim | null>(null)
  const [busy, setBusyState] = useState(false)
  const busyRef = useRef(false)
  function setBusy(value: boolean) {
    busyRef.current = value
    setBusyState(value)
  }
  const [message, setMessage] = useState('')
  const [actionFailed, setActionFailed] = useState(false)
  const [retryCommand, setRetryCommandState] = useState<RetryClip | null>(null)
  const retryCommandRef = useRef<RetryClip | null>(null)
  function setRetryCommand(value: RetryClip | null) {
    retryCommandRef.current = value
    setRetryCommandState(value)
  }
  // A confirmed trim exclusively owns its frozen request until acknowledgement, rejection or explicit discard.
  const [confirmedTrim, setConfirmedTrimState] = useState<ConfirmedTrim | null>(null)
  const confirmedTrimRef = useRef<ConfirmedTrim | null>(null)
  function setConfirmedTrim(value: ConfirmedTrim | null) {
    confirmedTrimRef.current = value
    setConfirmedTrimState(value)
  }
  const trimBlocked = trimAccess?.blocked ?? false
  const writeBlocked = busy || confirmedTrim !== null || trimBlocked
  const editsDisabled = writeBlocked || retryCommand !== null
  const canRetry = () => !busyRef.current && confirmedTrimRef.current === null && trimAccess?.canStart() !== false
  const canEdit = () => canRetry() && retryCommandRef.current === null
  useEffect(() => {
    onWriteLock?.(busy || confirmedTrim !== null || retryCommand !== null)
    return () => onWriteLock?.(false)
  }, [busy, confirmedTrim, retryCommand, onWriteLock])
  useEffect(() => setFormChanged(false), [head?.revisionId])

  const keys = useRef<Record<string, string>>({})
  const generation = useRef(0)
  const trimDrag = useRef<{ clip: ClipGeometry; edge: TrimEdge; x: number; pointer: number } | null>(null)
  const moveDrag = useRef<{ clip: ClipGeometry; x: number; pointer: number } | null>(null)
  const groupPointer = useRef<number | null>(null)
  const [moving, setMoving] = useState<{ clipId: string; start: Rational; match?: SnapMatch } | null>(null)
  const multiFeedback = multiMove?.feedback
  const currentSnap = pending?.match ?? moving?.match ?? multiFeedback?.match
  useEffect(() => {
    trimDrag.current = null; moveDrag.current = null; groupPointer.current = null
    setPending(null); setPreviewed(null); setMoving(null)
  }, [head?.revisionId, head?.contentHash, trimBlocked])
  useEffect(() => {
    onDraft?.(formChanged || pending !== null || previewed !== null || moving !== null || !!multiFeedback)
    return () => onDraft?.(false)
  }, [onDraft, formChanged, pending, previewed, moving, multiFeedback])

  async function refresh() {
    if (!viewedRevisionId) { setAspectRatio(null); setTracks([]); setAudioRoutes([]); setTransforms({}); setEffects({}); setTransitions([]); setClips([]); setSelected(''); return }
    const owned = ++generation.current
    try {
      const snapshot = await FormalRenderAPI.snapshot(project.projectId, viewedRevisionId)
      const projection = projectSnapshotJson(snapshot.canonicalTimelineJson)
      const rows = geometryOf(projection.clips)
      if (owned !== generation.current) return
      setAspectRatio(projection.compositionAspectRatio)
      setAspectWidth(String(projection.compositionAspectRatio.numerator))
      setAspectHeight(String(projection.compositionAspectRatio.denominator))
      setTracks(projection.tracks)
      setAudioRoutes(projection.audioRoutes)
      setTransforms(Object.fromEntries(projection.clips.map(clip => [clip.clipId, clip.transform])))
      setEffects(Object.fromEntries(projection.clips.map(clip => [clip.clipId, clip.effect])))
      setTransitions(projection.transitions)
      setClips(rows)
      setSelected(current => rows.some(row => row.clipId === current) ? current : rows[0]?.clipId ?? '')
    } catch (error) {
      if (owned === generation.current) {
        setAspectRatio(null); setTracks([]); setAudioRoutes([]); setTransforms({}); setEffects({}); setTransitions([]); setClips([]); setActionFailed(true)
        setMessage(error instanceof Error ? error.message : 'Clip readback failed.')
      }
    }
  }

  useEffect(() => { void refresh(); return () => { generation.current += 1 } }, [viewedRevisionId, project.projectId])
  useEffect(() => { setPending(null); setPreviewed(null); setMoving(null); setRetryCommand(null) }, [head?.revisionId, head?.contentHash])
  useEffect(() => { setRetryCommand(null); setPreviewed(null); setPending(null) }, [project.tenantId, project.projectId])
  useEffect(() => {
    if (focusClipId && clips.some(clip => clip.clipId === focusClipId)) setSelected(focusClipId)
  }, [focusClipId, clips])
  useEffect(() => { setMessage(''); setActionFailed(false); keys.current = {} }, [project.projectId])

  const active = clips.find(clip => clip.clipId === selected) ?? null
  const activeTrack = tracks.find(track => track.trackId === active?.trackId)
  const activeAudioRoute = audioRoutes.find(route => route.trackId === active?.trackId && route.clipId === active?.clipId) ?? null
  useEffect(() => {
    setAudioGain(activeAudioRoute?.gain ?? 1)
    setAudioMute(activeAudioRoute?.mute ?? false)
    setAudioPan(activeAudioRoute?.pan ?? 0)
  }, [active?.clipId, activeAudioRoute?.gain, activeAudioRoute?.mute, activeAudioRoute?.pan, head?.revisionId])
  const activeTransform = active ? transforms[active.clipId] ?? IDENTITY_VIDEO_TRANSFORM : IDENTITY_VIDEO_TRANSFORM
  const activeEffect = active ? effects[active.clipId] ?? IDENTITY_VIDEO_EFFECT : IDENTITY_VIDEO_EFFECT
  useEffect(() => {
    setTx(activeTransform.positionX); setTy(activeTransform.positionY)
    setSx(activeTransform.scaleX); setSy(activeTransform.scaleY)
    setRotation(activeTransform.rotationDegrees)
    setCropLeft(activeTransform.cropLeft); setCropRight(activeTransform.cropRight)
    setCropTop(activeTransform.cropTop); setCropBottom(activeTransform.cropBottom)
    setOpacity(activeTransform.opacity)
  }, [active?.clipId, activeTransform.positionX, activeTransform.positionY, activeTransform.scaleX,
    activeTransform.scaleY, activeTransform.rotationDegrees, activeTransform.cropLeft,
    activeTransform.cropRight, activeTransform.cropTop, activeTransform.cropBottom,
    activeTransform.opacity, head?.revisionId])
  useEffect(() => {
    setBrightness(activeEffect.brightness); setContrast(activeEffect.contrast)
    setSaturation(activeEffect.saturation); setGrayscale(activeEffect.grayscale)
    setFadeIn(activeEffect.fadeInSeconds); setFadeOut(activeEffect.fadeOutSeconds)
    setEffectEnabled(activeEffect.enabled)
    setTransitionId(active ? `transition-${active.clipId}` : '')
    setIncomingClipId('')
  }, [active?.clipId, activeEffect.brightness, activeEffect.contrast, activeEffect.saturation,
    activeEffect.grayscale, activeEffect.fadeInSeconds, activeEffect.fadeOutSeconds,
    activeEffect.enabled, head?.revisionId])
  useEffect(() => { setDestinationTrackId('') }, [selected, head?.revisionId])
  const projection = active && pending ? projectTrim(active, pending.edge, pending.delta) : null

  function commandIdFor(clipId: string, kind: string, deltaValue: Rational, edgeValue?: TrimEdge): string {
    const fingerprint = JSON.stringify([project.projectId, clipId, kind, edgeValue ?? null,
      formatRational(deltaValue), head?.revisionId])
    keys.current[fingerprint] ??= crypto.randomUUID()
    return keys.current[fingerprint]
  }

  function beginTrim(clip: ClipGeometry, trimEdge: TrimEdge, event: ReactPointerEvent<HTMLSpanElement>) {
    event.stopPropagation()
    if (!head || !canEdit() || trimDrag.current || moveDrag.current || groupPointer.current !== null) return
    event.preventDefault() // Do not start a native text drag after moving or canceling an edge.
    setSelected(clip.clipId); setType('TRIM_CLIP')
    setPreviewed(null)
    trimDrag.current = { clip, edge: trimEdge, x: event.clientX, pointer: event.pointerId }
    setPending({ edge: trimEdge, delta: ZERO })
    event.currentTarget.focus({ preventScroll: true })
    event.currentTarget.setPointerCapture?.(event.pointerId)
  }

  function trimPlacement(drag: NonNullable<typeof trimDrag.current>, x: number): PendingTrim {
    const { clip, edge } = drag
    const pixels = x - drag.x
    const match = snap && !trimOnly ? nearestSnap(
      [{ time: edge === 'START' ? clip.start : clip.end, label: clip.clipId + (edge === 'START' ? ' start' : ' end') }],
      snapTargets(clips, clip.trackId, new Set([clip.clipId]), snapPlayhead), pixels, TRIM_PIXELS_PER_SECOND) : undefined
    const delta = match
      ? edge === 'START' ? match.delta : subtract(ZERO, match.delta)
      : clampTrimDelta(clip, pixelsToDelta(pixels, TRIM_PIXELS_PER_SECOND))
    return { edge, delta, match }
  }

  function moveTrim(event: ReactPointerEvent<HTMLSpanElement>) {
    event.stopPropagation()
    const drag = trimDrag.current
    if (drag?.pointer !== event.pointerId || !drag || !canEdit()) return
    setPending(trimPlacement(drag, event.clientX))
  }

  function cancelTrimDrag(event?: ReactPointerEvent<HTMLSpanElement>) {
    event?.stopPropagation()
    if (!trimDrag.current || (event && event.pointerId !== trimDrag.current.pointer)) return
    trimDrag.current = null
    setPending(null)
    setPreviewed(null)
  }

  async function endTrim(event: ReactPointerEvent<HTMLSpanElement>) {
    event.stopPropagation()
    const drag = trimDrag.current
    if (!drag || drag.pointer !== event.pointerId) return
    trimDrag.current = null
    if (Math.abs(event.clientX - drag.x) < DRAG_THRESHOLD_PX || !canEdit()) { setPending(null); return }
    const draft = trimPlacement(drag, event.clientX)
    setPending(draft)
    if (draft.match) {
      const projected = projectTrim(drag.clip, draft.edge, draft.delta)
      if (!projected.valid) return
    }
    await previewTrim(draft.edge, draft.delta, draft.match, drag.clip)
  }

  async function cancelTrim() {
    if (busyRef.current) return
    trimDrag.current = null
    setPending(null); setPreviewed(null); setActionFailed(false)
    if (!confirmedTrimRef.current) {
      setMessage('Trim preview discarded. No revision was written.')
      return
    }
    setBusy(true)
    let refreshed = false
    try {
      if (onTrimReadback) refreshed = Boolean(await onTrimReadback())
      else { await onRevisionChanged(); refreshed = true }
    } catch {
      // Discard is explicit even if the branch cannot be read back.
    } finally {
      setActionFailed(!refreshed)
      setMessage('Trim retry discarded. The request may already have been applied.'
        + (refreshed ? '' : ' Reload the branch to refresh its current head.'))
      setConfirmedTrim(null)
      trimAccess?.release()
      setBusy(false)
    }
  }

  async function previewTrim(trimEdge: TrimEdge, deltaValue: Rational, match?: SnapMatch, clip: ClipGeometry | null = active) {
    if (!head || !project.tenantId || !clip || !canEdit()) return
    setPreviewed(null); setRetryCommand(null)
    if (compare(deltaValue, { numerator: 0n, denominator: 1n }) <= 0) { setPending(null); return }
    setBusy(true); setMessage(''); setActionFailed(false)
    const commandId = commandIdFor(clip.clipId, 'TRIM_CLIP', deltaValue, trimEdge)
    const command: ClipEdit = {
      type: 'TRIM_CLIP', refId: trimOnly ? editingBranch : undefined,
      baseRevisionId: head.revisionId, baseContentHash: head.contentHash,
      clipId: clip.clipId, delta: formatRational(deltaValue), edge: trimEdge, commandId,
    }
    try {
      const plan = await FormalRenderAPI.previewOperation(project.tenantId, project.projectId, command)
      if (trimAccess?.canStart() === false) return
      setPreviewed({ digest: plan.batchDigest, edge: trimEdge, delta: deltaValue, match, command })
      setPending({ edge: trimEdge, delta: deltaValue, match })
      const projected = projectTrim(clip, trimEdge, deltaValue)
      setMessage(`Preview valid: source ${formatRational(projected.sourceStart)}s → ${formatRational(projected.sourceEnd)}s, `
        + `${toSeconds(projected.duration).toFixed(2)}s visible. Planned changes: ${plan.steps.flatMap(step => step.changes).length}.`)
    } catch (error) { handleFailure(error, 'Trim preview', null, false) }
    finally { setBusy(false) }
  }

  async function readCommandHead(command: ClipEdit) {
    if (!command.refId) return FormalRenderAPI.head(project.projectId)
    const result = await readEditingBranch(projectId(project.projectId), command.refId)
    if (!result.ok) throw new Error(result.message)
    return result.value.head
  }

  async function applyTrim() {
    if (!head || !project.tenantId || !active || !canEdit() || !previewed) return
    if (previewed.command.clipId !== active.clipId || previewed.command.baseRevisionId !== head.revisionId
        || previewed.command.baseContentHash !== head.contentHash) {
      setPreviewed(null); setActionFailed(true)
      setMessage('The timeline changed after the preview. Preview the trim again.')
      return
    }
    await submitConfirmedTrim({ command: previewed.command, digest: previewed.digest,
      tenantId: project.tenantId, projectId: project.projectId })
  }

  async function submitConfirmedTrim(attempt: ConfirmedTrim) {
    if (busyRef.current || (confirmedTrimRef.current && confirmedTrimRef.current !== attempt)
        || attempt.tenantId !== project.tenantId || attempt.projectId !== project.projectId
        || trimAccess?.claim() === false) return
    setConfirmedTrim(attempt)
    setBusy(true); setMessage(''); setActionFailed(false)
    let retainCommand = false
    try {
      const accepted = await FormalRenderAPI.applyConfirmedClip(attempt.tenantId, attempt.projectId, attempt.command, attempt.digest)
      setPending(null); setPreviewed(null)
      if (!trimOnly) onTrimAccepted?.(attempt.command.baseRevisionId, attempt.command.baseContentHash, accepted)
      try {
        const current = onTrimReadback ? await onTrimReadback() : await readCommandHead(attempt.command)
        if (!onTrimReadback) await onRevisionChanged()
        if (!current) throw new Error('Branch readback unavailable.')
        if (current.revisionId !== accepted.revisionId) {
          setMessage(`Trim applied at revision ${accepted.revisionId}. The branch is now at revision ${current.revisionId}.`)
        } else if (current.contentHash !== accepted.contentHash) {
          setActionFailed(true)
          setMessage(`Trim accepted at revision ${accepted.revisionId}. The current content hash could not be verified.`)
        } else {
          setMessage(`Trim applied at revision ${accepted.revisionId}.`)
        }
      } catch {
        setActionFailed(true)
        setMessage(`Trim accepted at revision ${accepted.revisionId}. Reload the branch to refresh its current head.`)
      }
    } catch (error) {
      const status = httpStatus(error)
      if (isUncertainOutcome(status)) {
        retainCommand = true
        setPending(null); setPreviewed(null); setActionFailed(true)
        showUnconfirmed('Trim', status)
      } else {
        handleFailure(error, 'Trim', null, false, false)
        if (status === 409 || status === 412) await onRevisionChanged()
      }
    } finally {
      if (!retainCommand) {
        setConfirmedTrim(null)
        trimAccess?.release()
      }
      setBusy(false)
    }
  }

  function retryTrim() {
    if (confirmedTrimRef.current) void submitConfirmedTrim(confirmedTrimRef.current)
  }

  async function applySimple() {
    if (!head || !project.tenantId || !selected || !canEdit()) return
    const intent = { type, baseRevisionId: head.revisionId, baseContentHash: head.contentHash, clipId: selected,
      ...(type === 'DELETE_CLIP' ? {} : { delta }), ...(type === 'MOVE_CLIP' ? { absolute, ...(destinationTrackId ? { destinationTrackId } : {}) } : {}) }
    const fingerprint = JSON.stringify([project.projectId, intent])
    keys.current[fingerprint] ??= crypto.randomUUID()
    const command: ClipEdit = { ...intent, commandId: keys.current[fingerprint] } as ClipEdit
    setRetryCommand({ command, label: type.replace('_CLIP', '').toLowerCase() })
    setBusy(true); setMessage(''); setActionFailed(false)
    try {
      const accepted = await FormalRenderAPI.applyOperation(project.tenantId, project.projectId, command)
      const authoritative = await FormalRenderAPI.head(project.projectId)
      if (accepted.revisionId !== authoritative.revisionId || accepted.contentHash !== authoritative.contentHash) {
        throw headMismatch()
      }
      setRetryCommand(null)
      await onRevisionChanged()
      setMessage(`Operation accepted at revision ${authoritative.revisionId}.`)
    } catch (error) { handleFailure(error, type.replace('_CLIP', ''), command, true) }
    finally { setBusy(false) }
  }


  async function applyAudio() {
    if (!head || !project.tenantId || !active || !activeTrack || !canEdit()) return
    if (activeTrack.type !== 'AUDIO' && activeTrack.type !== 'VIDEO') return
    if (!Number.isFinite(audioGain) || audioGain < 0 || audioGain > 2) {
      setActionFailed(true); setMessage('Gain must be within 0.0–2.0.'); return
    }
    if (!Number.isFinite(audioPan) || audioPan < -1 || audioPan > 1) {
      setActionFailed(true); setMessage('Pan must be within -1.0–1.0.'); return
    }
    const fingerprint = JSON.stringify([project.projectId, active.clipId, 'AUDIO_MIX',
      audioGain, audioMute, audioPan, head.revisionId])
    keys.current[fingerprint] ??= crypto.randomUUID()
    const command = { baseRevisionId: head.revisionId, baseContentHash: head.contentHash,
      trackId: active.trackId, clipId: active.clipId, gain: audioGain, mute: audioMute,
      pan: audioPan, commandId: keys.current[fingerprint] }
    setBusy(true); setMessage(''); setActionFailed(false)
    try {
      const accepted = await FormalRenderAPI.applyAudioMix(project.tenantId, project.projectId, command)
      const authoritative = await FormalRenderAPI.head(project.projectId)
      if (accepted.revisionId !== authoritative.revisionId || accepted.contentHash !== authoritative.contentHash) {
        throw headMismatch()
      }
      await onRevisionChanged()
      setMessage(`Audio mix applied at revision ${authoritative.revisionId}: gain ${audioGain.toFixed(2)}, mute ${audioMute ? 'on' : 'off'}, pan ${audioPan.toFixed(2)}.`)
    } catch (error) { handleFailure(error, 'Audio mix', null, true) }
    finally { setBusy(false) }
  }

  async function applyTransform() {
    if (!head || !project.tenantId || !active || !canEdit()) return
    setBusy(true); setMessage(''); setActionFailed(false)
    try {
      const parameters = videoTransformParameters({ positionX: tx, positionY: ty, scaleX: sx, scaleY: sy,
        rotationDegrees: rotation, cropLeft, cropRight, cropTop, cropBottom, opacity })
      const fingerprint = JSON.stringify([project.projectId, active.clipId, parameters, head.revisionId])
      keys.current[fingerprint] ??= crypto.randomUUID()
      const accepted = await FormalRenderAPI.applyVideoTransform(project.tenantId, project.projectId, {
        baseRevisionId: head.revisionId, baseContentHash: head.contentHash, clipId: active.clipId,
        parameters, commandId: keys.current[fingerprint] })
      const authoritative = await FormalRenderAPI.head(project.projectId)
      if (accepted.revisionId !== authoritative.revisionId || accepted.contentHash !== authoritative.contentHash) {
        throw headMismatch()
      }
      await onRevisionChanged()
      setMessage(`Video transform applied at revision ${authoritative.revisionId}.`)
    } catch (error) { handleFailure(error, 'Video transform', null, true) }
    finally { setBusy(false) }
  }

  async function applyAspect(resolve: () => CompositionAspectRatio | Promise<CompositionAspectRatio>,
      input?: { artifactId: string; contentDigest: string; streamId: string }) {
    if (!head || !project.tenantId || !aspectRatio || !canEdit()) return
    setBusy(true); setMessage(''); setActionFailed(false)
    try {
      const next = await resolve()
      if (aspectLabel(next) === aspectLabel(aspectRatio)) {
        setMessage('The composition already uses this exact ratio.')
        return
      }
      const fingerprint = JSON.stringify([project.projectId, head.revisionId, 'composition-aspect', next, input])
      keys.current[fingerprint] ??= crypto.randomUUID()
      const accepted = await FormalRenderAPI.setCompositionAspect(project.tenantId, project.projectId, {
        baseRevisionId: head.revisionId, baseContentHash: head.contentHash,
        aspectRatio: next, ...(input && { input }), commandId: keys.current[fingerprint] })
      const authoritative = await FormalRenderAPI.head(project.projectId)
      if (accepted.revisionId !== authoritative.revisionId || accepted.contentHash !== authoritative.contentHash) {
        throw headMismatch()
      }
      await onRevisionChanged()
      setMessage('Composition aspect ratio saved.')
    } catch (error) { handleFailure(error, 'Composition aspect ratio', null, true) }
    finally { setBusy(false) }
  }

  function matchSourceAspect() {
    if (!active || activeTrack?.type !== 'VIDEO' || !project.tenantId) return
    void applyAspect(async () => {
      try {
        return await FormalRenderAPI.sourceDisplayAspect(project.tenantId!, project.projectId,
          active.artifactId, active.contentDigest, active.mediaStreamId)
      } catch {
        throw new Error('Match Source unavailable for the selected stream.')
      }
    }, { artifactId: active.artifactId, contentDigest: active.contentDigest, streamId: active.mediaStreamId })
  }

  function resetTransform() {
    if (!canEdit()) return
    setTx(0); setTy(0); setSx(1); setSy(1); setRotation(0)
    setCropLeft(0); setCropRight(1); setCropTop(0); setCropBottom(1); setOpacity(1)
    setMessage('Transform reset in the editor; apply to save the default transform.')
  }

  async function applyEffect() {
    if (!head || !project.tenantId || !active || !canEdit()) return
    if (![brightness, contrast, saturation, fadeIn, fadeOut].every(Number.isFinite)
        || brightness < -1 || brightness > 1 || contrast < 0 || contrast > 2
        || saturation < 0 || saturation > 2 || fadeIn < 0 || fadeOut < 0) {
      setActionFailed(true); setMessage('Invalid video effect: brightness [-1,1], contrast/saturation [0,2], fades >= 0.')
      return
    }
    const fingerprint = JSON.stringify([project.projectId, active.clipId, 'VIDEO_EFFECT',
      brightness, contrast, saturation, grayscale, fadeIn, fadeOut, effectEnabled, head.revisionId])
    keys.current[fingerprint] ??= crypto.randomUUID()
    setBusy(true); setMessage(''); setActionFailed(false)
    try {
      const accepted = await FormalRenderAPI.applyVideoEffect(project.tenantId, project.projectId, {
        baseRevisionId: head.revisionId, baseContentHash: head.contentHash, clipId: active.clipId,
        brightness, contrast, saturation, grayscale, fadeInSeconds: fadeIn,
        fadeOutSeconds: fadeOut, enabled: effectEnabled, commandId: keys.current[fingerprint] })
      const authoritative = await FormalRenderAPI.head(project.projectId)
      if (accepted.revisionId !== authoritative.revisionId || accepted.contentHash !== authoritative.contentHash) {
        throw headMismatch()
      }
      await onRevisionChanged()
      setMessage(`Video effect applied at revision ${authoritative.revisionId}.`)
    } catch (error) { handleFailure(error, 'Video effect', null, true) }
    finally { setBusy(false) }
  }

  async function removeEffect() {
    if (!head || !project.tenantId || !active || !canEdit()) return
    const fingerprint = JSON.stringify([project.projectId, active.clipId, 'REMOVE_VIDEO_EFFECT', head.revisionId])
    keys.current[fingerprint] ??= crypto.randomUUID()
    setBusy(true); setMessage(''); setActionFailed(false)
    try {
      const accepted = await FormalRenderAPI.removeVideoEffect(project.tenantId, project.projectId, {
        baseRevisionId: head.revisionId, baseContentHash: head.contentHash,
        clipId: active.clipId, commandId: keys.current[fingerprint] })
      const authoritative = await FormalRenderAPI.head(project.projectId)
      if (accepted.revisionId !== authoritative.revisionId || accepted.contentHash !== authoritative.contentHash) {
        throw headMismatch()
      }
      await onRevisionChanged()
      setMessage(`Video effect removed at revision ${authoritative.revisionId}.`)
    } catch (error) { handleFailure(error, 'Remove video effect', null, true) }
    finally { setBusy(false) }
  }

  async function createTransition() {
    if (!head || !project.tenantId || !active || !canEdit() || !incomingClipId) return
    if (!transitionId.trim() || !transitionDuration.trim()) {
      setActionFailed(true); setMessage('Transition id and exact duration are required.')
      return
    }
    const fingerprint = JSON.stringify([project.projectId, active.clipId, 'CREATE_TRANSITION',
      transitionId, incomingClipId, transitionDuration, head.revisionId])
    keys.current[fingerprint] ??= crypto.randomUUID()
    setBusy(true); setMessage(''); setActionFailed(false)
    try {
      const accepted = await FormalRenderAPI.createTransition(project.tenantId, project.projectId, {
        baseRevisionId: head.revisionId, baseContentHash: head.contentHash,
        outgoingClipId: active.clipId, transitionId: transitionId.trim(),
        incomingClipId, duration: transitionDuration.trim(), commandId: keys.current[fingerprint] })
      const authoritative = await FormalRenderAPI.head(project.projectId)
      if (accepted.revisionId !== authoritative.revisionId || accepted.contentHash !== authoritative.contentHash) {
        throw headMismatch()
      }
      await onRevisionChanged()
      setMessage(`Cross dissolve created at revision ${authoritative.revisionId}.`)
    } catch (error) { handleFailure(error, 'Create transition', null, true) }
    finally { setBusy(false) }
  }

  async function deleteTransition() {
    if (!head || !project.tenantId || !active || !canEdit() || !transitionId.trim()) return
    const fingerprint = JSON.stringify([project.projectId, active.clipId, 'DELETE_TRANSITION', transitionId, head.revisionId])
    keys.current[fingerprint] ??= crypto.randomUUID()
    setBusy(true); setMessage(''); setActionFailed(false)
    try {
      const accepted = await FormalRenderAPI.deleteTransition(project.tenantId, project.projectId, {
        baseRevisionId: head.revisionId, baseContentHash: head.contentHash,
        outgoingClipId: active.clipId, transitionId: transitionId.trim(),
        commandId: keys.current[fingerprint] })
      const authoritative = await FormalRenderAPI.head(project.projectId)
      if (accepted.revisionId !== authoritative.revisionId || accepted.contentHash !== authoritative.contentHash) {
        throw headMismatch()
      }
      await onRevisionChanged()
      setMessage(`Cross dissolve deleted at revision ${authoritative.revisionId}.`)
    } catch (error) { handleFailure(error, 'Delete transition', null, true) }
    finally { setBusy(false) }
  }

  function beginMove(clip: ClipGeometry, event: ReactPointerEvent<HTMLDivElement>) {
    if (!head || !canEdit() || moveDrag.current || trimDrag.current || groupPointer.current !== null) return
    event.stopPropagation()
    event.preventDefault() // Pointer capture owns this gesture, not browser text selection.
    if (multiMove?.ids.includes(clip.clipId)) {
      if (!multiMove.begin(clip.clipId, event.pointerId, event.clientX)) return
      groupPointer.current = event.pointerId
    } else {
      moveDrag.current = { clip, x: event.clientX, pointer: event.pointerId }
      setMoving({ clipId: clip.clipId, start: clip.start })
    }
    setSelected(clip.clipId); setType('MOVE_CLIP')
    setPending(null); setPreviewed(null)
    event.currentTarget.focus({ preventScroll: true })
    event.currentTarget.setPointerCapture?.(event.pointerId)
  }

  function singlePlacement(drag: NonNullable<typeof moveDrag.current>, x: number) {
    const pixels = x - drag.x
    const { match } = movePlacement([drag.clip], clips, pixels, TRIM_PIXELS_PER_SECOND, snap, snapPlayhead)
    return { clipId: drag.clip.clipId, match, start: match
      ? add(drag.clip.start, match.delta) : draggedStart(drag.clip, pixels, TRIM_PIXELS_PER_SECOND) }
  }

  function dragMove(event: ReactPointerEvent<HTMLDivElement>) {
    if (groupPointer.current === event.pointerId) {
      multiMove?.move(event.pointerId, event.clientX)
    } else if (moveDrag.current?.pointer === event.pointerId && moveDrag.current && canEdit()) {
      setMoving(singlePlacement(moveDrag.current, event.clientX))
    }
  }

  function cancelMove(event?: ReactPointerEvent<HTMLDivElement>) {
    if (groupPointer.current !== null && (!event || groupPointer.current === event.pointerId)) {
      multiMove?.cancel(groupPointer.current)
      groupPointer.current = null
    }
    if (moveDrag.current && (!event || moveDrag.current.pointer === event.pointerId)) {
      moveDrag.current = null
      setMoving(null)
    }
  }

  async function endMove(event: ReactPointerEvent<HTMLDivElement>) {
    if (groupPointer.current === event.pointerId) {
      groupPointer.current = null
      multiMove?.end(event.pointerId, event.clientX)
      return
    }
    const drag = moveDrag.current
    if (!drag || drag.pointer !== event.pointerId) return
    cancelMove(event)
    if (!canEdit() || Math.abs(event.clientX - drag.x) < DRAG_THRESHOLD_PX) return
    const { start, match } = singlePlacement(drag, event.clientX)
    const projection = projectMove(drag.clip, start)
    if (!projection.valid) { setActionFailed(true); setMessage(projection.reason ?? 'Move rejected.'); return }
    await applyMove(drag.clip, projection.start, match)
  }

  async function previewAndCommit(command: ClipEdit, label: string, success: (revision: string) => string) {
    if (!project.tenantId || !canEdit()) return
    setBusy(true); setMessage(''); setActionFailed(false); setPending(null); setPreviewed(null)
    let attempt: RetryClip | undefined
    try {
      const plan = await FormalRenderAPI.previewOperation(project.tenantId, project.projectId, command)
      if (trimAccess?.claim() === false) return
      attempt = { command, label, digest: plan.batchDigest }
      setRetryCommand(attempt)
      const accepted = await FormalRenderAPI.applyConfirmedClip(project.tenantId, project.projectId, command, plan.batchDigest)
      const authoritative = await readCommandHead(command)
      if (accepted.revisionId !== authoritative.revisionId || accepted.contentHash !== authoritative.contentHash) {
        throw headMismatch()
      }
      setRetryCommand(null)
      if (onTrimReadback) await onTrimReadback()
      else await onRevisionChanged()
      setMessage(success(authoritative.revisionId))
    } catch (error) {
      handleFailure(error, attempt ? label : label + ' preview', attempt?.command ?? null, !!attempt, false, attempt?.digest)
      if (!retryCommandRef.current) {
        if (label === 'Move' || [409, 412].includes(httpStatus(error) ?? 0)) {
          if (attempt && onTrimReadback) await onTrimReadback()
          else await onRevisionChanged()
        }
      }
    } finally {
      if (attempt && !retryCommandRef.current) trimAccess?.release()
      setBusy(false)
    }
  }

  async function applyMove(clip: ClipGeometry, targetStart: Rational, match?: SnapMatch) {
    if (!head || !canEdit()) return
    await previewAndCommit({
      type: 'MOVE_CLIP', baseRevisionId: head.revisionId, baseContentHash: head.contentHash,
      clipId: clip.clipId, delta: formatRational(targetStart), absolute: true,
      commandId: commandIdFor(clip.clipId, 'MOVE_CLIP', targetStart),
    }, 'Move', revision => (match ? snapDescription(match) + ' ' : '')
      + `Clip moved to ${describeMove(projectMove(clip, targetStart))} at revision ${revision}.`)
  }

  async function deleteSelected() {
    if (!head || !selected || !canEdit()) return
    const clipId = selected
    await previewAndCommit({
      type: 'DELETE_CLIP', baseRevisionId: head.revisionId, baseContentHash: head.contentHash,
      clipId, commandId: commandIdFor(clipId, 'DELETE_CLIP', ZERO),
    }, 'Delete', revision => `Clip ${clipId} deleted at revision ${revision}. The source media is retained.`)
  }

  function headMismatch(): Error {
    return Object.assign(new Error('Operation accepted, but the authoritative HEAD has changed. Reload before editing again.'),
      { response: { status: 409 } })
  }

  function httpStatus(error: unknown) {
    return (error as { response?: { status?: number } })?.response?.status
  }

  function isUncertainOutcome(status?: number) {
    return status === undefined || status >= 500 || status === 429
  }

  function showUnconfirmed(label: string, status?: number) {
    setMessage(`${label} could not be confirmed (${status ?? 'network error'}). The command is retained for a safe retry with the same command ID; no success is claimed.`)
  }

  function handleFailure(error: unknown, label: string, command: ClipEdit | null, keepForRetry: boolean,
    refreshOnConflict = true, digest?: string) {
    const status = httpStatus(error)
    const conflict = status === 409 || status === 412
    setPreviewed(null); setPending(null); setMoving(null)
    setActionFailed(true)
    if (conflict) {
      setRetryCommand(null)
      setMessage(`${label} rejected: the authoritative HEAD has changed since this draft was loaded. Reloading the authoritative revision; re-preview before editing again.`)
      if (refreshOnConflict) void onRevisionChanged()
      return
    }
    if (keepForRetry && command && isUncertainOutcome(status)) {
      setRetryCommand({ command, label, digest })
      showUnconfirmed(label, status)
      return
    }
    setRetryCommand(null)
    setMessage(error instanceof Error ? error.message : `${label} failed.`)
  }

  async function retryApply() {
    const attempt = retryCommandRef.current
    if (!head || !project.tenantId || !attempt || !canRetry()) return
    setBusy(true); setMessage(''); setActionFailed(false)
    try {
      const accepted = attempt.digest
        ? await FormalRenderAPI.applyConfirmedClip(project.tenantId, project.projectId, attempt.command, attempt.digest)
        : await FormalRenderAPI.applyOperation(project.tenantId, project.projectId, attempt.command)
      const authoritative = await readCommandHead(attempt.command)
      if (accepted.revisionId !== authoritative.revisionId || accepted.contentHash !== authoritative.contentHash) {
        throw headMismatch()
      }
      setRetryCommand(null)
      if (attempt.digest && onTrimReadback) await onTrimReadback()
      else await onRevisionChanged()
      setMessage(`${attempt.label} applied at revision ${authoritative.revisionId}.`)
    } catch (error) {
      handleFailure(error, attempt.label, attempt.command, true, !attempt.digest, attempt.digest)
      if (attempt.digest && !retryCommandRef.current) {
        if ([409, 412].includes(httpStatus(error) ?? 0)) {
          if (onTrimReadback) await onTrimReadback()
          else await onRevisionChanged()
        }
      }
    } finally {
      if (attempt.digest && !retryCommandRef.current) trimAccess?.release()
      setBusy(false)
    }
  }

  async function splitSelected() {
    if (!head || !project.tenantId || !active || !canEdit()) return
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
    const command: ClipEdit = {
      type: 'SPLIT_CLIP', baseRevisionId: head.revisionId, baseContentHash: head.contentHash,
      clipId: active.clipId, splitTime: formatRational(parsed), rightClipId, commandId,
    }
    setBusy(true); setMessage(''); setActionFailed(false)
    try {
      await FormalRenderAPI.previewOperation(project.tenantId, project.projectId, command)
      setRetryCommand({ command, label: 'Split' })
      const accepted = await FormalRenderAPI.applyOperation(project.tenantId, project.projectId, command)
      const authoritative = await FormalRenderAPI.head(project.projectId)
      if (accepted.revisionId !== authoritative.revisionId || accepted.contentHash !== authoritative.contentHash) {
        throw headMismatch()
      }
      setRetryCommand(null)
      setSelected(rightClipId)
      await onRevisionChanged()
      setMessage(`Clip split at ${formatRational(parsed)}s; left ${active.clipId} and right ${rightClipId} `
        + `are independent clips at revision ${authoritative.revisionId}.`)
    } catch (error) { handleFailure(error, 'Split', command, true) }
    finally { setBusy(false) }
  }

  function onSectionKeyDown(event: ReactKeyboardEvent<HTMLElement>) {
    if (event.key === 'Escape' && (trimDrag.current || moveDrag.current || groupPointer.current !== null)) {
      event.preventDefault()
      cancelTrimDrag(); cancelMove()
      return
    }
    if (event.key !== 'Delete' && event.key !== 'Backspace') return
    const target = event.target as HTMLElement | null
    const tag = target?.tagName?.toLowerCase()
    if (tag === 'input' || tag === 'select' || tag === 'textarea' || target?.isContentEditable) return
    if (!selected || !canEdit()) return
    event.preventDefault()
    void deleteSelected()
  }

  async function addTrack(trackType: 'VIDEO' | 'AUDIO') {
    if (!head || !project.tenantId || !canEdit()) return
    let command = trackCommands.current[trackType]
    if (command?.revision !== head.revisionId) {
      command = { revision: head.revisionId, id: crypto.randomUUID() }
      trackCommands.current[trackType] = command
    }
    setBusy(true)
    setMessage('')
    setActionFailed(false)
    try {
      await FormalRenderAPI.addTrack(project.tenantId, project.projectId, {
        baseRevisionId: head.revisionId, baseContentHash: head.contentHash,
        trackId: `track-${command.id}`, trackType, commandId: command.id,
      })
      await onRevisionChanged()
      setMessage(`${trackType === 'VIDEO' ? 'Video' : 'Audio'} track added.`)
    } catch (error) {
      handleFailure(error, 'Add track', null, true)
    } finally {
      setBusy(false)
    }
  }

  const stripWidth = Math.max(240, ...clips.map(clip => toSeconds(clip.end) * TRIM_PIXELS_PER_SECOND + 40))
  const tickStep = Math.max(1, Math.ceil(stripWidth / (TRIM_PIXELS_PER_SECOND * 12)))
  const ticks = Array.from({ length: Math.floor(stripWidth / (TRIM_PIXELS_PER_SECOND * tickStep)) + 1 },
    (_, index) => index * tickStep)
  const previewValid = previewed !== null && pending !== null
    && previewed.command.clipId === active?.clipId && previewed.edge === pending.edge
    && compare(previewed.delta, pending.delta) === 0
  const splitCandidate = type === 'SPLIT_CLIP' && active ? parseRational(splitTime) : null
  const splitMarker = splitCandidate && active
    && compare(splitCandidate, active.start) > 0 && compare(splitCandidate, active.end) < 0
    ? { clipId: active.clipId, at: splitCandidate } : null

  return <section aria-label={historicalRevisionId ? "Historical timeline" : "Edit existing clip"}
    data-viewed-revision={viewedRevisionId} tabIndex={historicalRevisionId ? undefined : 0}
    onKeyDown={historicalRevisionId || trimOnly ? undefined : onSectionKeyDown} onChangeCapture={() => { if (canEdit()) setFormChanged(true) }}>
    {!historicalRevisionId && <><div className="ff-nle-buttons">
    <Button type="button" disabled={!head || editsDisabled} onClick={() => { if (canEdit()) void refresh() }}>Refresh clips</Button>
    {!trimOnly && <><Button type="button" disabled={!head || editsDisabled || !project.tenantId} onClick={() => void addTrack('VIDEO')}>Add video track</Button>
    <Button type="button" disabled={!head || editsDisabled || !project.tenantId} onClick={() => void addTrack('AUDIO')}>Add audio track</Button></>}
    </div>
    <label className="ff-nle-clip-picker">Clip <select className="ff-input" aria-label="Clip" disabled={editsDisabled || retryCommand !== null} value={selected} onChange={event => { if (!canEdit()) return; setSelected(event.target.value); setPending(null); setPreviewed(null); setRetryCommand(null) }}>
      {clips.map(clip => <option key={clip.clipId} value={clip.clipId}>{clip.clipId} {clip.trackId && `(${clip.trackId})`}</option>)}
    </select></label></>}

    <div role="group" aria-label="Timeline trim" data-pixels-per-second={TRIM_PIXELS_PER_SECOND}
         style={{ position: 'relative', marginTop: 8, marginBottom: 8, overflowX: 'auto',
                  background: '#111827', border: '1px solid #1f2937', borderRadius: 4 }}>
      <div style={{ position: 'relative', width: stripWidth + 100, margin: 8 }}>
        <div aria-label="Timeline ruler" style={{ position: 'relative', height: 24, marginLeft: 100 }}>
          {ticks.map(tick => <span key={tick} style={{ position: 'absolute', left: tick * TRIM_PIXELS_PER_SECOND }}>{tick}s</span>)}
        </div>
        <i aria-label={`Playhead at ${playhead.toFixed(2)} seconds`} style={{ position: 'absolute', top: 20, bottom: 0,
          left: 100 + playhead * TRIM_PIXELS_PER_SECOND, borderLeft: '2px solid #facc15', zIndex: 3, pointerEvents: 'none' }} />
        {currentSnap && <i data-snap-guide aria-hidden="true" style={{ position: 'absolute', top: 20, bottom: 0,
          left: 100 + toSeconds(currentSnap.target.time) * TRIM_PIXELS_PER_SECOND, borderLeft: '2px solid #38bdf8',
          zIndex: 4, pointerEvents: 'none' }} />}
        {(['VIDEO', 'AUDIO'] as const).map(type => {
          const group = tracks.filter(track => track.type === type)
          const ordered = type === 'VIDEO' ? [...group].reverse() : group
          return <div key={type} role="group" aria-label={type === 'VIDEO' ? 'Video tracks' : 'Audio tracks'}>
            {ordered.map(track => {
              const label = `${type === 'VIDEO' ? 'V' : 'A'}${group.indexOf(track) + 1}`
              return <div key={track.trackId} style={{ display: 'flex', minHeight: 46, borderTop: '1px solid #374151' }}>
                <Button type="button" aria-label={`${label} ${track.name} ${type.toLowerCase()} track`}
                  className="ff-nle-track-label" aria-pressed={selectedTrackId === track.trackId} onClick={() => onSelectTrack?.(track.trackId)}
                  style={{ flex: '0 0 100px', textAlign: 'left' }}>
                  {label}{track.name !== label && ` · ${track.name}`}
                </Button>
                <div style={{ position: 'relative', width: stripWidth }}>
        {clips.filter(clip => clip.trackId === track.trackId).map(clip => {
          const isActive = clip.clipId === selected
          const projected = isActive && projection?.valid ? projection : null
          const start = projected ? toSeconds(projected.timelineStart) : toSeconds(clip.start)
          const end = projected ? toSeconds(projected.timelineEnd) : toSeconds(clip.end)
          const movingThis = moving?.clipId === clip.clipId
          const moveProjection = movingThis ? projectMove(clip, moving.start) : null
          const grouped = multiMove?.ids.includes(clip.clipId)
          const groupPosition = grouped && multiFeedback ? shiftedRange(clip, multiFeedback.delta) : null
          const left = groupPosition ? toSeconds(groupPosition.start) : movingThis && moveProjection?.valid ? toSeconds(moveProjection.start) : start
          const right = groupPosition ? toSeconds(groupPosition.end) : movingThis && moveProjection?.valid ? toSeconds(moveProjection.end) : end
          return <div key={clip.clipId} data-clip-id={clip.clipId}
                      data-clip-body={clip.clipId} data-multimove-selected={grouped || undefined}
                      tabIndex={historicalRevisionId ? undefined : -1}
                      aria-label={`Timeline clip ${clip.clipId}`}
                      onPointerDown={historicalRevisionId || trimOnly || editsDisabled || retryCommand ? undefined : event => beginMove(clip, event)}
                      onClick={() => { if (historicalRevisionId || canEdit()) setSelected(clip.clipId) }}
                      onPointerMove={dragMove}
                      onPointerUp={event => void endMove(event)}
                      onPointerCancel={cancelMove} onLostPointerCapture={cancelMove}
                      title={historicalRevisionId ? undefined : trimOnly ? "Drag a trim handle to preview a trim" : "Drag the clip body to move it; drag an orange edge to trim"}
                      style={{ position: 'absolute', left: left * TRIM_PIXELS_PER_SECOND,
                               width: Math.max(6, (right - left) * TRIM_PIXELS_PER_SECOND), top: 0, height: 40,
                               background: isActive ? '#1d4ed8aa' : '#374151aa',
                               border: grouped ? '2px solid #38bdf8' : isActive ? '1px solid #60a5fa' : '1px solid #4b5563', borderRadius: 3,
                               display: 'flex', alignItems: 'center', justifyContent: 'center',
                               fontSize: 11, color: '#e5e7eb', whiteSpace: 'nowrap', overflow: 'hidden',
                               userSelect: historicalRevisionId || trimOnly ? undefined : 'none',
                               touchAction: historicalRevisionId || trimOnly ? undefined : 'none' }}>
            {splitMarker?.clipId === clip.clipId && <span data-split-marker aria-hidden="true"
              style={{ position: 'absolute', left: (toSeconds(splitMarker.at) - left) * TRIM_PIXELS_PER_SECOND - 1,
                       top: -4, width: 2, height: 48, background: '#facc15', zIndex: 2, pointerEvents: 'none' }} />}
            <span aria-hidden="true">{clip.clipId} · {toSeconds(projected ? projected.duration : subtract(clip.end, clip.start)).toFixed(2)}s
              · src {toSeconds(projected ? projected.sourceStart : clip.sourceStart).toFixed(2)}–{toSeconds(projected ? projected.sourceEnd : clip.sourceEnd).toFixed(2)}</span>
            {!historicalRevisionId && <><span role="slider" tabIndex={editsDisabled ? -1 : 0} aria-disabled={editsDisabled} aria-label={`Trim start edge ${clip.clipId}`} aria-valuetext="trim start"
                  onPointerDown={event => beginTrim(clip, 'START', event)}
                  onPointerMove={moveTrim} onPointerUp={event => void endTrim(event)} onPointerCancel={cancelTrimDrag} onLostPointerCapture={cancelTrimDrag}
                  style={{ position: 'absolute', left: -3, top: 0, width: 8, height: '100%', cursor: 'ew-resize',
                           background: '#f59e0b', borderRadius: 2, touchAction: 'none', userSelect: 'none' }} />
            <span role="slider" tabIndex={editsDisabled ? -1 : 0} aria-disabled={editsDisabled} aria-label={`Trim end edge ${clip.clipId}`} aria-valuetext="trim end"
                  onPointerDown={event => beginTrim(clip, 'END', event)}
                  onPointerMove={moveTrim} onPointerUp={event => void endTrim(event)} onPointerCancel={cancelTrimDrag} onLostPointerCapture={cancelTrimDrag}
                  style={{ position: 'absolute', right: -3, top: 0, width: 8, height: '100%', cursor: 'ew-resize',
                           background: '#f59e0b', borderRadius: 2, touchAction: 'none', userSelect: 'none' }} /></>}
          </div>
        })}
                </div>
              </div>
            })}
          </div>
        })}
      </div>
    </div>

    {currentSnap && <p role="status" data-snap-feedback>{snapDescription(currentSnap)}</p>}
    {historicalRevisionId && message && <p role="alert">{message}</p>}
    {!historicalRevisionId && <>{projection && !projection.valid && <p role="alert">{projection.reason}</p>}
    {multiFeedback && <p role="status">Moving {multiMove?.ids.length} selected clips by {formatRational(multiFeedback.delta)}s.</p>}
    {moving && (() => { const clip = clips.find(row => row.clipId === moving.clipId); const projection = clip ? projectMove(clip, moving.start) : null; return projection
      ? <p role="status">Moving {moving.clipId} to {projection.valid ? describeMove(projection) : projection.reason}</p> : null })()}
    {!trimOnly && <><Button type="button" aria-label="Delete selected clip" disabled={!head || !selected || !project.tenantId || editsDisabled}
            onClick={() => void deleteSelected()}>Delete selected clip</Button>
    <fieldset disabled={!head || !project.tenantId || !aspectRatio || editsDisabled}>
      <legend>Composition aspect</legend>
      <label>Composition aspect ratio
        <select className="ff-input" aria-label="Composition aspect ratio"
          value={aspectRatio ? aspectLabel(aspectRatio) : ''}
          onChange={event => {
            const preset = COMPOSITION_PRESETS.find(item => aspectLabel(item.ratio) === event.target.value)
            if (preset) void applyAspect(() => preset.ratio)
          }}>
          <option value="" disabled>Aspect unavailable</option>
          {aspectRatio && !COMPOSITION_PRESETS.some(preset => aspectLabel(preset.ratio) === aspectLabel(aspectRatio))
            && <option value={aspectLabel(aspectRatio)}>Custom {aspectLabel(aspectRatio)}</option>}
          {COMPOSITION_PRESETS.map(preset => <option key={aspectLabel(preset.ratio)} value={aspectLabel(preset.ratio)}>
            {preset.label}
          </option>)}
        </select>
      </label>
      <div className="ff-nle-transform-grid">
        <label>Custom aspect width <Input aria-label="Custom aspect width" inputMode="numeric" value={aspectWidth}
          onChange={event => setAspectWidth(event.target.value)} /></label>
        <label>Custom aspect height <Input aria-label="Custom aspect height" inputMode="numeric" value={aspectHeight}
          onChange={event => setAspectHeight(event.target.value)} /></label>
      </div>
      <Button type="button" disabled={editsDisabled} onClick={() => void applyAspect(() => customCompositionAspect(aspectWidth, aspectHeight))}>
        Apply custom aspect
      </Button>
      <Button type="button" disabled={editsDisabled || activeTrack?.type !== 'VIDEO'} onClick={matchSourceAspect}>Match Source</Button>
    </fieldset>
    {active && activeTrack?.type === 'VIDEO' && <fieldset className="ff-nle-transform-controls" aria-label="Video transform controls" disabled={!head || !project.tenantId || editsDisabled}>
      <legend>Video transform · {active.clipId}</legend>
      <div className="ff-nle-transform-grid">
        <label>Position X (canvas fraction) <Input aria-label="Video position X" type="number" step={0.000001} value={tx} onChange={event => setTx(Number(event.target.value))} /></label>
        <label>Position Y (canvas fraction) <Input aria-label="Video position Y" type="number" step={0.000001} value={ty} onChange={event => setTy(Number(event.target.value))} /></label>
        <label>Scale X <Input aria-label="Video scale X" type="number" step={0.05} value={sx} onChange={event => setSx(Number(event.target.value))} /></label>
        <label>Scale Y <Input aria-label="Video scale Y" type="number" step={0.05} value={sy} onChange={event => setSy(Number(event.target.value))} /></label>
        <label>Rotation° <Input aria-label="Video rotation" type="number" step={1} value={rotation} onChange={event => setRotation(Number(event.target.value))} /></label>
        <label>Opacity <Input aria-label="Video opacity" type="number" min={0} max={1} step={0.05} value={opacity} onChange={event => setOpacity(Number(event.target.value))} /></label>
        <label>Crop L <Input aria-label="Video crop left" type="number" min={0} max={1} step={0.01} value={cropLeft} onChange={event => setCropLeft(Number(event.target.value))} /></label>
        <label>Crop R <Input aria-label="Video crop right" type="number" min={0} max={1} step={0.01} value={cropRight} onChange={event => setCropRight(Number(event.target.value))} /></label>
        <label>Crop T <Input aria-label="Video crop top" type="number" min={0} max={1} step={0.01} value={cropTop} onChange={event => setCropTop(Number(event.target.value))} /></label>
        <label>Crop B <Input aria-label="Video crop bottom" type="number" min={0} max={1} step={0.01} value={cropBottom} onChange={event => setCropBottom(Number(event.target.value))} /></label>
      </div>
      <Button type="button" disabled={!head || !active || editsDisabled} onClick={() => void applyTransform()}>Apply video transform</Button>
      <Button type="button" disabled={editsDisabled} onClick={resetTransform}>Reset transform</Button>
      <p role="status">Canonical transform: position {activeTransform.positionX.toFixed(6)},{activeTransform.positionY.toFixed(6)} · scale {activeTransform.scaleX.toFixed(2)},{activeTransform.scaleY.toFixed(2)} · rotation {activeTransform.rotationDegrees.toFixed(0)}° · opacity {activeTransform.opacity.toFixed(2)}</p>
    </fieldset>}
    {active && activeTrack?.type === 'VIDEO' && <fieldset className="ff-nle-effect-controls" aria-label="Video effect controls" disabled={!head || !project.tenantId || editsDisabled}>
      <legend>Video effect · {active.clipId}</legend>
      <div className="ff-nle-transform-grid">
        <label>Brightness <Input aria-label="Video brightness" type="number" step={0.05} min={-1} max={1} value={brightness} onChange={event => setBrightness(Number(event.target.value))} /></label>
        <label>Contrast <Input aria-label="Video contrast" type="number" step={0.05} min={0} max={2} value={contrast} onChange={event => setContrast(Number(event.target.value))} /></label>
        <label>Saturation <Input aria-label="Video saturation" type="number" step={0.05} min={0} max={2} value={saturation} onChange={event => setSaturation(Number(event.target.value))} /></label>
        <label>Fade in (s) <Input aria-label="Video fade in" type="number" step={0.1} min={0} value={fadeIn} onChange={event => setFadeIn(Number(event.target.value))} /></label>
        <label>Fade out (s) <Input aria-label="Video fade out" type="number" step={0.1} min={0} value={fadeOut} onChange={event => setFadeOut(Number(event.target.value))} /></label>
        <label><Input aria-label="Video grayscale" type="checkbox" checked={grayscale} onChange={event => setGrayscale(event.target.checked)} /> Grayscale</label>
        <label><Input aria-label="Video effect enabled" type="checkbox" checked={effectEnabled} onChange={event => setEffectEnabled(event.target.checked)} /> Effect enabled</label>
      </div>
      <Button type="button" disabled={!head || editsDisabled} onClick={() => void applyEffect()}>Apply video effect</Button>
      <Button type="button" disabled={editsDisabled} onClick={() => void removeEffect()}>Remove video effect</Button>
      <p role="status">Canonical effect: brightness {activeEffect.brightness.toFixed(2)} · contrast {activeEffect.contrast.toFixed(2)} · saturation {activeEffect.saturation.toFixed(2)} · grayscale {String(activeEffect.grayscale)} · fade {activeEffect.fadeInSeconds}/{activeEffect.fadeOutSeconds}s · enabled {String(activeEffect.enabled)}</p>
    </fieldset>}
    {active && activeTrack?.type === 'VIDEO' && <fieldset className="ff-nle-transition-controls" aria-label="Transition controls" disabled={!head || !project.tenantId || editsDisabled}>
      <legend>Cross dissolve · outgoing {active.clipId}</legend>
      <div className="ff-nle-transform-grid">
        <label>Transition id <Input aria-label="Transition id" value={transitionId} onChange={event => setTransitionId(event.target.value)} /></label>
        <label>Incoming clip <select aria-label="Transition incoming clip" value={incomingClipId} onChange={event => setIncomingClipId(event.target.value)}>
          <option value="">Select video clip</option>
          {tracks.filter(track => track.type === 'VIDEO').flatMap(track => track.clips)
            .filter(clip => clip.clipId !== active.clipId)
            .map(clip => <option key={clip.clipId} value={clip.clipId}>{clip.clipId} · {clip.trackId}</option>)}
        </select></label>
        <label>Duration <Input aria-label="Transition duration" value={transitionDuration} onChange={event => setTransitionDuration(event.target.value)} /></label>
      </div>
      <Button type="button" disabled={!head || editsDisabled || !incomingClipId} onClick={() => void createTransition()}>Create cross dissolve</Button>
      <Button type="button" disabled={editsDisabled} onClick={() => void deleteTransition()}>Delete cross dissolve</Button>
      <ul aria-label="Canonical transitions">
        {transitions.map(transition => <li key={transition.transitionId}>{transition.transitionId}: {transition.outgoingClipId} → {transition.incomingClipId} ({formatRational(transition.duration)}s)</li>)}
      </ul>
    </fieldset>}
    {active && (activeTrack?.type === 'AUDIO' || activeTrack?.type === 'VIDEO') && <fieldset className="ff-nle-audio-controls" aria-label="Audio mix controls" disabled={!head || !project.tenantId || editsDisabled}>
      <legend>Audio mix · {active.clipId}</legend>
      <label>Gain <Input aria-label="Audio gain" type="number" min={0} max={2} step={0.05} value={audioGain}
        onChange={event => setAudioGain(Number(event.target.value))} /></label>
      <label><input aria-label="Mute clip" type="checkbox" checked={audioMute}
        onChange={event => setAudioMute(event.target.checked)} /> Mute</label>
      <label>Pan <input aria-label="Audio pan" type="range" min={-1} max={1} step={0.05} value={audioPan}
        onChange={event => setAudioPan(Number(event.target.value))} /> <output>{audioPan.toFixed(2)}</output></label>
      <Button type="button" disabled={!head || !active || editsDisabled} onClick={() => void applyAudio()}>Apply audio mix</Button>
      <p role="status">{activeAudioRoute
        ? `Canonical route: gain ${activeAudioRoute.gain.toFixed(2)}, mute ${activeAudioRoute.mute ? 'on' : 'off'}, pan ${activeAudioRoute.pan.toFixed(2)}`
        : 'No canonical audio route yet; applying a value creates the route (including embedded video audio).'}</p>
    </fieldset>}
    </>}
    {trimOnly && <p>Trim is available on this editing branch. Other clip and track edits are available on main.</p>}
    {pending && active && <p role="status">
      Pending {pending.edge === 'START' ? 'left' : 'right'} trim by {formatRational(pending.delta)}s
      {' '}(source {formatRational(projectTrim(active, pending.edge, pending.delta).sourceStart)}s →
      {formatRational(projectTrim(active, pending.edge, pending.delta).sourceEnd)}s).
    </p>}
    {(pending || confirmedTrim) && <Button type="button" disabled={busy} onClick={() => void cancelTrim()}>Cancel trim</Button>}
    <Button type="button" disabled={editsDisabled || !pending || !previewValid} onClick={() => void applyTrim()}>Apply trim</Button>

    {!trimOnly && <div className="ff-nle-clip-controls">
    <label>Operation <select className="ff-input" aria-label="Clip operation" value={type} disabled={editsDisabled} onChange={event => { if (!canEdit()) return; setType(event.target.value as ClipEdit['type']); setPending(null); setPreviewed(null); setRetryCommand(null) }}>
      <option value="MOVE_CLIP">Move</option><option value="TRIM_CLIP">Trim</option><option value="DELETE_CLIP">Delete</option><option value="SPLIT_CLIP">Split</option>
    </select></label>
    {type !== 'DELETE_CLIP' && type !== 'SPLIT_CLIP' && <label>Exact time <Input aria-label="Exact time" disabled={editsDisabled} value={delta} onChange={event => setDelta(event.target.value)} /></label>}
    {type === 'SPLIT_CLIP' && <label>Split time <Input aria-label="Split time" disabled={editsDisabled} value={splitTime} onChange={event => setSplitTime(event.target.value)} /></label>}
    {type === 'MOVE_CLIP' && <>
      <label><input type="checkbox" checked={absolute} disabled={editsDisabled} onChange={event => setAbsolute(event.target.checked)} /> Absolute position</label>
      <label>Destination track <select className="ff-input" aria-label="Destination track" disabled={editsDisabled} value={destinationTrackId} onChange={event => setDestinationTrackId(event.target.value)}>
        <option value="">Current track</option>
        {tracks.filter(track => track.type === activeTrack?.type && track.trackId !== activeTrack?.trackId).map(track =>
          <option key={track.trackId} value={track.trackId}>{track.name}</option>)}
      </select></label>
    </>}
    {type === 'TRIM_CLIP' && <label>Edge <select className="ff-input" aria-label="Trim edge" disabled={editsDisabled} value={edge} onChange={event => setEdge(event.target.value as TrimEdge)}><option value="START">START</option><option value="END">END</option></select></label>}
    {type === 'TRIM_CLIP'
      ? <>
          <Button type="button" disabled={!head || !selected || !project.tenantId || editsDisabled}
                  onClick={() => { const parsed = parseRational(delta); if (parsed) void previewTrim(edge, parsed) }}>Preview trim</Button>
          <span> (drag a clip edge above, then apply) </span>
        </>
      : type === 'SPLIT_CLIP'
        ? <Button type="button" aria-label="Split selected clip" disabled={!head || !selected || !project.tenantId || editsDisabled}
                  onClick={() => void splitSelected()}>Split clip</Button>
        : <Button type="button" disabled={!head || !selected || !project.tenantId || editsDisabled} onClick={() => void applySimple()}>Apply operation</Button>}
    </div>}
    {clips.length === 0 && <p>No clips in this revision.</p>}
    {message && <p role={actionFailed ? 'alert' : 'status'}>{message}</p>}
    {confirmedTrim && !busy && <Button type="button" disabled={trimBlocked} onClick={retryTrim}>Retry Trim with the same command ID</Button>}
    {retryCommand && <Button type="button" disabled={writeBlocked} onClick={() => void retryApply()}>Retry {retryCommand.label} with the same command ID</Button>}</>}
  </section>
}
