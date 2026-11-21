import { useEffect, useRef, useState, type KeyboardEvent as ReactKeyboardEvent, type PointerEvent as ReactPointerEvent } from 'react'
import { FormalRenderAPI, type ClipEdit } from '../../api/formal-render'
import { audioRouteFor, IDENTITY_VIDEO_EFFECT, IDENTITY_VIDEO_TRANSFORM, projectSnapshotJson, type ProjectedAudioRoute, type ProjectedTrack, type ProjectedTransition, type ProjectedVideoEffect, type ProjectedVideoTransform } from './timeline-projection'
import type { CanonicalHeadReference } from './gateways'
import { Button, Input } from '../../components/design-system'
import { useProjectContext } from '../../foundation/projectContext'
import {
  clampTrimDelta, compare, formatRational, parseRational, pixelsToDelta, projectTrim, subtract, toSeconds,
  type ClipGeometry, type Rational, type TrimEdge,
} from './trim'
import { describeMove, draggedStart, projectMove } from './move'

interface Props {
  head: CanonicalHeadReference | null
  onRevisionChanged: () => Promise<void>
  focusClipId?: string | null
  playhead?: number
  selectedTrackId?: string | null
  onSelectTrack?: (trackId: string) => void
}

/** Horizontal scale of the trim strip: one second of timeline occupies this many pixels. */
export const TRIM_PIXELS_PER_SECOND = 80
const DRAG_THRESHOLD_PX = 4

interface PendingTrim { edge: TrimEdge; delta: Rational }
interface PreviewedTrim extends PendingTrim { clipId: string; revisionId: string; contentHash: string; commandId: string }

const DEFAULT_RATE: Rational = { numerator: 1n, denominator: 1n }

function geometryOf(clips: ReturnType<typeof projectSnapshotJson>['clips']): ClipGeometry[] {
  return clips.map(clip => ({
    clipId: clip.clipId, trackId: clip.trackId, start: clip.start, end: clip.end,
    sourceStart: clip.sourceStart, sourceEnd: clip.sourceEnd, rate: clip.rate,
  }))
}

export function ClipEditor({ head, onRevisionChanged, focusClipId = null, playhead = 0,
  selectedTrackId, onSelectTrack }: Props) {
  const project = useProjectContext()
  const [tracks, setTracks] = useState<readonly ProjectedTrack[]>([])
  const [audioRoutes, setAudioRoutes] = useState<readonly ProjectedAudioRoute[]>([])
  const [transforms, setTransforms] = useState<Record<string, ProjectedVideoTransform>>({})
  const [effects, setEffects] = useState<Record<string, ProjectedVideoEffect>>({})
  const [transitions, setTransitions] = useState<readonly ProjectedTransition[]>([])
  const [destinationTrackId, setDestinationTrackId] = useState('')
  const trackCommands = useRef<Partial<Record<'VIDEO' | 'AUDIO', { revision: string; id: string }>>>({})
  const [clips, setClips] = useState<ClipGeometry[]>([])
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
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState('')
  const [actionFailed, setActionFailed] = useState(false)
  const [retryCommand, setRetryCommand] = useState<{ command: ClipEdit; label: string } | null>(null)
  const keys = useRef<Record<string, string>>({})
  const generation = useRef(0)
  const dragOrigin = useRef<number | null>(null)
  const [moving, setMoving] = useState<{ clipId: string; start: Rational; originX: number } | null>(null)
  const moveOrigin = useRef<number | null>(null)

  async function refresh() {
    if (!head) { setTracks([]); setAudioRoutes([]); setTransforms({}); setEffects({}); setTransitions([]); setClips([]); setSelected(''); return }
    const owned = ++generation.current
    try {
      const snapshot = await FormalRenderAPI.snapshot(project.projectId, head.revisionId)
      const projection = projectSnapshotJson(snapshot.canonicalTimelineJson)
      const rows = geometryOf(projection.clips)
      if (owned !== generation.current) return
      setTracks(projection.tracks)
      setAudioRoutes(projection.audioRoutes)
      setTransforms(Object.fromEntries(projection.clips.map(clip => [clip.clipId, clip.transform])))
      setEffects(Object.fromEntries(projection.clips.map(clip => [clip.clipId, clip.effect])))
      setTransitions(projection.transitions)
      setClips(rows)
      setSelected(current => rows.some(row => row.clipId === current) ? current : rows[0]?.clipId ?? '')
    } catch (error) {
      if (owned === generation.current) {
        setTracks([]); setClips([]); setActionFailed(false)
        setMessage(error instanceof Error ? error.message : 'Clip readback failed.')
      }
    }
  }

  useEffect(() => { void refresh(); return () => { generation.current += 1 } }, [head?.revisionId, project.projectId])
  useEffect(() => { setPending(null); setPreviewed(null); setMoving(null); setRetryCommand(null) }, [head?.revisionId])
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
    const command: ClipEdit = {
      type: 'TRIM_CLIP', baseRevisionId: head.revisionId, baseContentHash: head.contentHash,
      clipId: active.clipId, delta: formatRational(deltaValue), edge: trimEdge, commandId,
    }
    try {
      await FormalRenderAPI.previewOperation(project.tenantId, project.projectId, command)
      setPreviewed({ edge: trimEdge, delta: deltaValue, clipId: active.clipId,
        revisionId: head.revisionId, contentHash: head.contentHash, commandId })
      setPending({ edge: trimEdge, delta: deltaValue })
      setRetryCommand({ command, label: 'Trim' })
      const projected = projectTrim(active, trimEdge, deltaValue)
      setMessage(`Preview valid: source ${formatRational(projected.sourceStart)}s → ${formatRational(projected.sourceEnd)}s, `
        + `${toSeconds(projected.duration).toFixed(2)}s visible. Nothing was saved yet.`)
    } catch (error) { handleFailure(error, 'Trim preview', null, false) }
    finally { setBusy(false) }
  }

  async function applyTrim() {
    if (!head || !project.tenantId || !active || busy || !previewed) return
    if (previewed.clipId !== active.clipId || previewed.revisionId !== head.revisionId
        || previewed.contentHash !== head.contentHash) {
      setPreviewed(null); setActionFailed(true)
      setMessage('The timeline changed after the preview. Preview the trim again.')
      return
    }
    const command: ClipEdit = {
      type: 'TRIM_CLIP', baseRevisionId: head.revisionId, baseContentHash: head.contentHash,
      clipId: active.clipId, delta: formatRational(previewed.delta), edge: previewed.edge,
      commandId: previewed.commandId,
    }
    setBusy(true); setMessage(''); setActionFailed(false)
    try {
      const accepted = await FormalRenderAPI.applyOperation(project.tenantId, project.projectId, command)
      const authoritative = await FormalRenderAPI.head(project.projectId)
      if (accepted.revisionId !== authoritative.revisionId || accepted.contentHash !== authoritative.contentHash) {
        throw headMismatch()
      }
      setPending(null); setPreviewed(null); setRetryCommand(null)
      await onRevisionChanged()
      setMessage(`Trim applied at revision ${authoritative.revisionId}.`)
    } catch (error) { handleFailure(error, 'Trim', command, true) }
    finally { setBusy(false) }
  }

  async function applySimple() {
    if (!head || !project.tenantId || !selected || busy) return
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
    if (!head || !project.tenantId || !active || !activeTrack || busy) return
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
    if (!head || !project.tenantId || !active || busy) return
    if (![tx, ty, sx, sy, rotation, cropLeft, cropRight, cropTop, cropBottom, opacity].every(Number.isFinite)
        || sx <= 0 || sy <= 0 || sx > 10 || sy > 10
        || opacity < 0 || opacity > 1
        || cropLeft < 0 || cropRight > 1 || cropTop < 0 || cropBottom > 1
        || cropLeft >= cropRight || cropTop >= cropBottom) {
      setActionFailed(true); setMessage('Invalid video transform: scale must be (0,10], opacity [0,1], crop must be a non-empty normalized rectangle.')
      return
    }
    const fingerprint = JSON.stringify([project.projectId, active.clipId, 'VIDEO_TRANSFORM',
      tx, ty, sx, sy, rotation, cropLeft, cropRight, cropTop, cropBottom, opacity, head.revisionId])
    keys.current[fingerprint] ??= crypto.randomUUID()
    setBusy(true); setMessage(''); setActionFailed(false)
    try {
      const accepted = await FormalRenderAPI.applyVideoTransform(project.tenantId, project.projectId, {
        baseRevisionId: head.revisionId, baseContentHash: head.contentHash, clipId: active.clipId,
        positionX: tx, positionY: ty, scaleX: sx, scaleY: sy, rotationDegrees: rotation,
        cropLeft, cropRight, cropTop, cropBottom, opacity, commandId: keys.current[fingerprint] })
      const authoritative = await FormalRenderAPI.head(project.projectId)
      if (accepted.revisionId !== authoritative.revisionId || accepted.contentHash !== authoritative.contentHash) {
        throw headMismatch()
      }
      await onRevisionChanged()
      setMessage(`Video transform applied at revision ${authoritative.revisionId}.`)
    } catch (error) { handleFailure(error, 'Video transform', null, true) }
    finally { setBusy(false) }
  }

  function resetTransform() {
    setTx(0); setTy(0); setSx(1); setSy(1); setRotation(0)
    setCropLeft(0); setCropRight(1); setCropTop(0); setCropBottom(1); setOpacity(1)
    setMessage('Transform reset in the editor; apply to save the default transform.')
  }

  async function applyEffect() {
    if (!head || !project.tenantId || !active || busy) return
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
    if (!head || !project.tenantId || !active || busy) return
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
    if (!head || !project.tenantId || !active || busy || !incomingClipId) return
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
    if (!head || !project.tenantId || !active || busy || !transitionId.trim()) return
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
    const command: ClipEdit = {
      type: 'MOVE_CLIP', baseRevisionId: head.revisionId, baseContentHash: head.contentHash,
      clipId: clip.clipId, delta: formatRational(targetStart), absolute: true, commandId,
    }
    try {
      await FormalRenderAPI.previewOperation(project.tenantId, project.projectId, command)
    } catch (error) {
      setActionFailed(true)
      setMessage(error instanceof Error ? error.message : 'Move preview was rejected.')
      void onRevisionChanged()
      setBusy(false)
      return
    }
    try {
      setRetryCommand({ command, label: 'Move' })
      const accepted = await FormalRenderAPI.applyOperation(project.tenantId, project.projectId, command)
      const authoritative = await FormalRenderAPI.head(project.projectId)
      if (accepted.revisionId !== authoritative.revisionId || accepted.contentHash !== authoritative.contentHash) {
        throw headMismatch()
      }
      setRetryCommand(null)
      await onRevisionChanged()
      const projection = projectMove(clip, targetStart)
      setMessage(`Clip moved to ${describeMove(projection)} at revision ${authoritative.revisionId}.`)
    } catch (error) { handleFailure(error, 'Move', command, true) }
    finally { setBusy(false) }
  }

  async function deleteSelected() {
    if (!head || !project.tenantId || !selected || busy) return
    setBusy(true); setMessage(''); setActionFailed(false)
    const commandId = commandIdFor(selected, 'DELETE_CLIP', { numerator: 0n, denominator: 1n })
    const removed = clips.find(clip => clip.clipId === selected)
    const command: ClipEdit = {
      type: 'DELETE_CLIP', baseRevisionId: head.revisionId, baseContentHash: head.contentHash,
      clipId: selected, commandId,
    }
    setRetryCommand({ command, label: 'Delete' })
    try {
      const accepted = await FormalRenderAPI.applyOperation(project.tenantId, project.projectId, command)
      const authoritative = await FormalRenderAPI.head(project.projectId)
      if (accepted.revisionId !== authoritative.revisionId || accepted.contentHash !== authoritative.contentHash) {
        throw headMismatch()
      }
      setRetryCommand(null)
      await onRevisionChanged()
      setMessage(`Clip ${removed?.clipId ?? selected} deleted at revision ${authoritative.revisionId}. `
        + 'The source media is retained.')
    } catch (error) { handleFailure(error, 'Delete', command, true) }
    finally { setBusy(false) }
  }

  function headMismatch(): Error {
    return Object.assign(new Error('Operation accepted, but the authoritative HEAD has changed. Reload before editing again.'),
      { response: { status: 409 } })
  }

  function handleFailure(error: unknown, label: string, command: ClipEdit | null, keepForRetry: boolean) {
    const status = (error as { response?: { status?: number } })?.response?.status
    const conflict = status === 409 || status === 412
    setPreviewed(null); setPending(null); setMoving(null)
    setActionFailed(true)
    if (conflict) {
      setRetryCommand(null)
      setMessage(`${label} rejected: the authoritative HEAD has changed since this draft was loaded. Reloading the authoritative revision; re-preview before editing again.`)
      void onRevisionChanged()
      return
    }
    const retryable = keepForRetry && command !== null && (status === undefined || status >= 500)
    if (retryable) {
      setRetryCommand({ command, label })
      setMessage(`${label} could not be confirmed (${status ?? 'network error'}). The command is retained for a safe retry with the same command ID; no success is claimed.`)
      return
    }
    setRetryCommand(null)
    setMessage(error instanceof Error ? error.message : `${label} failed.`)
  }

  async function retryApply() {
    if (!head || !project.tenantId || !retryCommand || busy) return
    setBusy(true); setMessage(''); setActionFailed(false)
    try {
      const accepted = await FormalRenderAPI.applyOperation(project.tenantId, project.projectId, retryCommand.command)
      const authoritative = await FormalRenderAPI.head(project.projectId)
      if (accepted.revisionId !== authoritative.revisionId || accepted.contentHash !== authoritative.contentHash) {
        throw headMismatch()
      }
      setRetryCommand(null)
      await onRevisionChanged()
      setMessage(`${retryCommand.label} applied at revision ${authoritative.revisionId}.`)
    } catch (error) { handleFailure(error, retryCommand.label, retryCommand.command, true) }
    finally { setBusy(false) }
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
    if (event.key !== 'Delete' && event.key !== 'Backspace') return
    const target = event.target as HTMLElement | null
    const tag = target?.tagName?.toLowerCase()
    if (tag === 'input' || tag === 'select' || tag === 'textarea' || target?.isContentEditable) return
    if (!selected || busy) return
    event.preventDefault()
    void deleteSelected()
  }

  async function addTrack(trackType: 'VIDEO' | 'AUDIO') {
    if (!head || !project.tenantId || busy) return
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
    && previewed.clipId === active?.clipId && previewed.edge === pending.edge
    && compare(previewed.delta, pending.delta) === 0
  const splitCandidate = type === 'SPLIT_CLIP' && active ? parseRational(splitTime) : null
  const splitMarker = splitCandidate && active
    && compare(splitCandidate, active.start) > 0 && compare(splitCandidate, active.end) < 0
    ? { clipId: active.clipId, at: splitCandidate } : null

  return <section aria-label="Edit existing clip" tabIndex={0} onKeyDown={onSectionKeyDown}>
    <div className="ff-nle-buttons">
    <Button type="button" disabled={!head || busy} onClick={() => void refresh()}>Refresh clips</Button>
    <Button type="button" disabled={!head || busy || !project.tenantId} onClick={() => void addTrack('VIDEO')}>Add video track</Button>
    <Button type="button" disabled={!head || busy || !project.tenantId} onClick={() => void addTrack('AUDIO')}>Add audio track</Button>
    </div>
    <label className="ff-nle-clip-picker">Clip <select className="ff-input" aria-label="Clip" value={selected} onChange={event => { setSelected(event.target.value); setPending(null); setPreviewed(null); setRetryCommand(null) }}>
      {clips.map(clip => <option key={clip.clipId} value={clip.clipId}>{clip.clipId} {clip.trackId && `(${clip.trackId})`}</option>)}
    </select></label>

    <div role="group" aria-label="Timeline trim" data-pixels-per-second={TRIM_PIXELS_PER_SECOND}
         style={{ position: 'relative', marginTop: 8, marginBottom: 8, overflowX: 'auto',
                  background: '#111827', border: '1px solid #1f2937', borderRadius: 4 }}>
      <div style={{ position: 'relative', width: stripWidth + 100, margin: 8 }}>
        <div aria-label="Timeline ruler" style={{ position: 'relative', height: 24, marginLeft: 100 }}>
          {ticks.map(tick => <span key={tick} style={{ position: 'absolute', left: tick * TRIM_PIXELS_PER_SECOND }}>{tick}s</span>)}
        </div>
        <i aria-label={`Playhead at ${playhead.toFixed(2)} seconds`} style={{ position: 'absolute', top: 20, bottom: 0,
          left: 100 + playhead * TRIM_PIXELS_PER_SECOND, borderLeft: '2px solid #facc15', zIndex: 3, pointerEvents: 'none' }} />
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
            {splitMarker?.clipId === clip.clipId && <span data-split-marker aria-hidden="true"
              style={{ position: 'absolute', left: (toSeconds(splitMarker.at) - left) * TRIM_PIXELS_PER_SECOND - 1,
                       top: -4, width: 2, height: 48, background: '#facc15', zIndex: 2, pointerEvents: 'none' }} />}
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
                </div>
              </div>
            })}
          </div>
        })}
      </div>
    </div>

    {projection && !projection.valid && <p role="alert">{projection.reason}</p>}
    {moving && (() => { const clip = clips.find(row => row.clipId === moving.clipId); const projection = clip ? projectMove(clip, moving.start) : null; return projection
      ? <p role="status">Moving {moving.clipId} to {projection.valid ? describeMove(projection) : projection.reason}</p> : null })()}
    <Button type="button" aria-label="Delete selected clip" disabled={!head || !selected || !project.tenantId || busy}
            onClick={() => void deleteSelected()}>Delete selected clip</Button>
    {active && activeTrack?.type === 'VIDEO' && <fieldset className="ff-nle-transform-controls" aria-label="Video transform controls" disabled={!head || !project.tenantId || busy}>
      <legend>Video transform · {active.clipId}</legend>
      <div className="ff-nle-transform-grid">
        <label>Position X <Input aria-label="Video position X" type="number" step={1} value={tx} onChange={event => setTx(Number(event.target.value))} /></label>
        <label>Position Y <Input aria-label="Video position Y" type="number" step={1} value={ty} onChange={event => setTy(Number(event.target.value))} /></label>
        <label>Scale X <Input aria-label="Video scale X" type="number" step={0.05} value={sx} onChange={event => setSx(Number(event.target.value))} /></label>
        <label>Scale Y <Input aria-label="Video scale Y" type="number" step={0.05} value={sy} onChange={event => setSy(Number(event.target.value))} /></label>
        <label>Rotation° <Input aria-label="Video rotation" type="number" step={1} value={rotation} onChange={event => setRotation(Number(event.target.value))} /></label>
        <label>Opacity <Input aria-label="Video opacity" type="number" min={0} max={1} step={0.05} value={opacity} onChange={event => setOpacity(Number(event.target.value))} /></label>
        <label>Crop L <Input aria-label="Video crop left" type="number" min={0} max={1} step={0.01} value={cropLeft} onChange={event => setCropLeft(Number(event.target.value))} /></label>
        <label>Crop R <Input aria-label="Video crop right" type="number" min={0} max={1} step={0.01} value={cropRight} onChange={event => setCropRight(Number(event.target.value))} /></label>
        <label>Crop T <Input aria-label="Video crop top" type="number" min={0} max={1} step={0.01} value={cropTop} onChange={event => setCropTop(Number(event.target.value))} /></label>
        <label>Crop B <Input aria-label="Video crop bottom" type="number" min={0} max={1} step={0.01} value={cropBottom} onChange={event => setCropBottom(Number(event.target.value))} /></label>
      </div>
      <Button type="button" disabled={!head || !active || busy} onClick={() => void applyTransform()}>Apply video transform</Button>
      <Button type="button" disabled={busy} onClick={resetTransform}>Reset transform</Button>
      <p role="status">Canonical transform: position {activeTransform.positionX.toFixed(0)},{activeTransform.positionY.toFixed(0)} · scale {activeTransform.scaleX.toFixed(2)},{activeTransform.scaleY.toFixed(2)} · rotation {activeTransform.rotationDegrees.toFixed(0)}° · opacity {activeTransform.opacity.toFixed(2)}</p>
    </fieldset>}
    {active && activeTrack?.type === 'VIDEO' && <fieldset className="ff-nle-effect-controls" aria-label="Video effect controls" disabled={!head || !project.tenantId || busy}>
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
      <Button type="button" disabled={!head || busy} onClick={() => void applyEffect()}>Apply video effect</Button>
      <Button type="button" disabled={busy} onClick={() => void removeEffect()}>Remove video effect</Button>
      <p role="status">Canonical effect: brightness {activeEffect.brightness.toFixed(2)} · contrast {activeEffect.contrast.toFixed(2)} · saturation {activeEffect.saturation.toFixed(2)} · grayscale {String(activeEffect.grayscale)} · fade {activeEffect.fadeInSeconds}/{activeEffect.fadeOutSeconds}s · enabled {String(activeEffect.enabled)}</p>
    </fieldset>}
    {active && activeTrack?.type === 'VIDEO' && <fieldset className="ff-nle-transition-controls" aria-label="Transition controls" disabled={!head || !project.tenantId || busy}>
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
      <Button type="button" disabled={!head || busy || !incomingClipId} onClick={() => void createTransition()}>Create cross dissolve</Button>
      <Button type="button" disabled={busy} onClick={() => void deleteTransition()}>Delete cross dissolve</Button>
      <ul aria-label="Canonical transitions">
        {transitions.map(transition => <li key={transition.transitionId}>{transition.transitionId}: {transition.outgoingClipId} → {transition.incomingClipId} ({formatRational(transition.duration)}s)</li>)}
      </ul>
    </fieldset>}
    {active && (activeTrack?.type === 'AUDIO' || activeTrack?.type === 'VIDEO') && <fieldset className="ff-nle-audio-controls" aria-label="Audio mix controls" disabled={!head || !project.tenantId || busy}>
      <legend>Audio mix · {active.clipId}</legend>
      <label>Gain <Input aria-label="Audio gain" type="number" min={0} max={2} step={0.05} value={audioGain}
        onChange={event => setAudioGain(Number(event.target.value))} /></label>
      <label><input aria-label="Mute clip" type="checkbox" checked={audioMute}
        onChange={event => setAudioMute(event.target.checked)} /> Mute</label>
      <label>Pan <input aria-label="Audio pan" type="range" min={-1} max={1} step={0.05} value={audioPan}
        onChange={event => setAudioPan(Number(event.target.value))} /> <output>{audioPan.toFixed(2)}</output></label>
      <Button type="button" disabled={!head || !active || busy} onClick={() => void applyAudio()}>Apply audio mix</Button>
      <p role="status">{activeAudioRoute
        ? `Canonical route: gain ${activeAudioRoute.gain.toFixed(2)}, mute ${activeAudioRoute.mute ? 'on' : 'off'}, pan ${activeAudioRoute.pan.toFixed(2)}`
        : 'No canonical audio route yet; applying a value creates the route (including embedded video audio).'}</p>
    </fieldset>}
    {pending && active && <p role="status">
      Pending {pending.edge === 'START' ? 'left' : 'right'} trim by {formatRational(pending.delta)}s
      {' '}(source {formatRational(projectTrim(active, pending.edge, pending.delta).sourceStart)}s →
      {formatRational(projectTrim(active, pending.edge, pending.delta).sourceEnd)}s).
    </p>}
    {pending && <Button type="button" disabled={busy} onClick={cancelTrim}>Cancel trim</Button>}
    <Button type="button" disabled={!pending || busy || !previewValid} onClick={() => void applyTrim()}>Apply trim</Button>

    <div className="ff-nle-clip-controls">
    <label>Operation <select className="ff-input" aria-label="Clip operation" value={type} onChange={event => { setType(event.target.value as ClipEdit['type']); setPending(null); setPreviewed(null); setRetryCommand(null) }}>
      <option value="MOVE_CLIP">Move</option><option value="TRIM_CLIP">Trim</option><option value="DELETE_CLIP">Delete</option><option value="SPLIT_CLIP">Split</option>
    </select></label>
    {type !== 'DELETE_CLIP' && type !== 'SPLIT_CLIP' && <label>Exact time <Input aria-label="Exact time" value={delta} onChange={event => setDelta(event.target.value)} /></label>}
    {type === 'SPLIT_CLIP' && <label>Split time <Input aria-label="Split time" value={splitTime} onChange={event => setSplitTime(event.target.value)} /></label>}
    {type === 'MOVE_CLIP' && <>
      <label><input type="checkbox" checked={absolute} onChange={event => setAbsolute(event.target.checked)} /> Absolute position</label>
      <label>Destination track <select className="ff-input" aria-label="Destination track" value={destinationTrackId} onChange={event => setDestinationTrackId(event.target.value)}>
        <option value="">Current track</option>
        {tracks.filter(track => track.type === activeTrack?.type && track.trackId !== activeTrack?.trackId).map(track =>
          <option key={track.trackId} value={track.trackId}>{track.name}</option>)}
      </select></label>
    </>}
    {type === 'TRIM_CLIP' && <label>Edge <select className="ff-input" aria-label="Trim edge" value={edge} onChange={event => setEdge(event.target.value as TrimEdge)}><option value="START">START</option><option value="END">END</option></select></label>}
    {type === 'TRIM_CLIP'
      ? <>
          <Button type="button" disabled={!head || !selected || !project.tenantId || busy}
                  onClick={() => { const parsed = parseRational(delta); if (parsed) void previewTrim(edge, parsed) }}>Preview trim</Button>
          <span> (drag a clip edge above, then apply) </span>
        </>
      : type === 'SPLIT_CLIP'
        ? <Button type="button" aria-label="Split selected clip" disabled={!head || !selected || !project.tenantId || busy}
                  onClick={() => void splitSelected()}>Split clip</Button>
        : <Button type="button" disabled={!head || !selected || !project.tenantId || busy} onClick={() => void applySimple()}>Apply operation</Button>}
    </div>
    {clips.length === 0 && <p>No clips in this revision.</p>}
    {message && <p role={actionFailed ? 'alert' : 'status'}>{message}</p>}
    {retryCommand && <Button type="button" disabled={busy} onClick={() => void retryApply()}>Retry {retryCommand.label} with the same command ID</Button>}
  </section>
}
