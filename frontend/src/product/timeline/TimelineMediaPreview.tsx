import { decorationVisible, savedSubtitleFont, selectedTextAlpha, subtitlePose, type SubtitleCue } from './subtitle-cues'
import type { SemanticBatch } from '../../contracts/semantic-batch.generated'
import { DEFAULT_COMPOSITION_ASPECT } from '../../contracts/app/composition-aspect'
import { previewRaster } from './composition-raster'
import { useEffect, useImperativeHandle, useLayoutEffect, useRef, useState, type Ref } from 'react'
import { Button } from '../../components/design-system'
import { FormalRenderAPI } from '../../api/formal-render'
import { assetGateway } from '../../api/app/asset.gateway'
import { parseRational, add, compare, subtract, toSeconds, formatRational, ZERO, type Rational } from './trim'
import { activeClipsAt, audioCrossfadePlacement, audioFadeEnvelope, decimalCursor, clockCursor, scaleAt, sourceTimeAt, timelineDuration, transitionForIncoming, wipeEdge } from './timeline-preview'
import { audioRouteFor, projectSnapshotJson, type ProjectedAudioFades, type ProjectedVideoEffect, type ProjectedClip, type ProjectedTransition, type ScaleKey, type TimelineProjection } from './timeline-projection'
import { projectId as validateProjectId } from './types'

interface SubtitleBitmaps {
  fixed: ImageBitmap
  selected?: ImageBitmap
  decoration?: { image: ImageBitmap; gap: number }
}

function closeSubtitle(images: SubtitleBitmaps) {
  images.fixed.close()
  images.selected?.close()
  images.decoration?.image.close()
}

type PreviewStatus = 'IDLE' | 'PREPARING' | 'READY' | 'PLAYING' | 'PAUSED' | 'FAILED'
interface AudioClip { clip: ProjectedClip; buffer: AudioBuffer; gain: number; mute: boolean; pan: number }
interface ScheduledAudio {
  source: AudioBufferSourceNode
  param: AudioParam
  authoredFade: AudioParam
  transitionGain: AudioParam
  transitionFades: ProjectedAudioFades
  playbackEnd: Rational
  clip: ProjectedClip
  gain: number
}
interface PreviewSession {
  revisionId: string | undefined
  controller: AbortController
  urls: string[]
  videos: Map<string, HTMLVideoElement>
  subtitleBlocks: Map<string, SubtitleBitmaps>
  subtitleSurface?: HTMLCanvasElement
  audioClips: AudioClip[]
  voiceoverAudition?: AudioClip
  scheduled: ScheduledAudio[]
  audio?: AudioContext
  mixer?: GainNode
  analyser?: AnalyserNode
  output?: AudioNode
  projection?: TimelineProjection
  colorSurface?: HTMLCanvasElement
  duration: number
  end: Rational
  seekInputEnd: number
  cursor: number
  cursorExact: Rational
  origin: number
  playing: boolean
  starting: boolean
  playAttempt: number
  frame?: number
}

const clock = (session: PreviewSession) => session.audio?.currentTime ?? performance.now() / 1000
const seekInputMax = (duration: number) => Math.max(0.01, duration)

function effectiveSubtitleCues(projection: TimelineProjection, plan: SemanticBatch | null | undefined, revisionId: string | undefined) {
  const edit = plan?.baseRevisionId === revisionId ? plan?.operations[0] : undefined
  const replacement = edit?.definitionId === 'timeline.subtitle.cue.set' || edit?.definitionId === 'timeline.subtitle.cue.remove'
    ? edit.parameters.cueId : null
  const cues = (projection.subtitles ?? []).filter(cue => cue.cueId !== replacement)
  if (edit?.definitionId === 'timeline.subtitle.cue.set') cues.push(edit.parameters)
  return { cues, replacement }
}

function nativeSeekInputEnd(duration: number) {
  // A range control can serialize its maximum with fewer digits than its max attribute.
  // Measure that serialization without touching the live slider or snapping nearby positions.
  const input = document.createElement('input')
  input.type = 'range'
  input.min = '0'
  input.max = String(seekInputMax(duration))
  input.step = 'any'
  input.value = input.max
  return input.valueAsNumber
}

function stopPlayback(session: PreviewSession) {
  session.playing = false
  if (session.frame !== undefined) cancelAnimationFrame(session.frame)
  for (const media of session.videos.values()) media.pause()
  for (const { source } of session.scheduled) {
    source.stop()
    source.disconnect()
  }
  session.scheduled = []
}

function loadVideo(media: HTMLVideoElement, url: string, signal: AbortSignal) {
  return new Promise<void>((resolve, reject) => {
    media.addEventListener('loadeddata', () => resolve(), { once: true, signal })
    media.addEventListener('error', () => reject(new Error('A video source could not be decoded.')), { once: true, signal })
    signal.addEventListener('abort', () => reject(signal.reason), { once: true })
    media.src = url
    media.load()
  })
}

/** Playback derived from the exact canonical revision. */
/** RGB code values, brightness then contrast, followed by BT.601 luma saturation. */
function applyBasicVideoColor(samples: Uint8ClampedArray, effect: ProjectedVideoEffect) {
  const gain = 1 + effect.brightness
  const saturation = effect.grayscale ? 0 : effect.saturation
  const adjust = (channel: number) =>
    Math.min(255, Math.max(0, Math.round((channel * gain - 127.5) * effect.contrast + 127.5)))
  for (let offset = 0; offset < samples.length; offset += 4) {
    const red = adjust(samples[offset])
    const green = adjust(samples[offset + 1])
    const blue = adjust(samples[offset + 2])
    const luma = 0.299 * red + 0.587 * green + 0.114 * blue
    samples[offset] = luma + saturation * (red - luma)
    samples[offset + 1] = luma + saturation * (green - luma)
    samples[offset + 2] = luma + saturation * (blue - luma)
  }
}

export interface PreviewTransport {
  pause(): Rational
  seek(position: Rational): void
  play(): void
}

export interface ComparisonPreview {
  audioContext(): AudioContext
  onLoading(): void
  onPrepared(end: Rational | null): void
  onStarted(): void
  onCursor(position: Rational): void
  onEnd(): void
}

export function TimelineMediaPreview({ revisionId, tenantId, projectId, onTime, transportRef, comparison, suspended, opacityPreview, dissolvePlan, positionPreview, scalePreview, gainPreview, fadePreview, subtitlePlan, audioCrossfadePlan, voiceoverPlan }: {
  revisionId: string | undefined
  tenantId: string | null
  projectId: string
  subtitlePlan?: SemanticBatch | null
  dissolvePlan?: SemanticBatch | null
  audioCrossfadePlan?: SemanticBatch | null
  voiceoverPlan?: SemanticBatch | null
  opacityPreview?: { revisionId: string; clipId: string; keys: import('./timeline-projection').OpacityKeys } | null
  positionPreview?: { revisionId: string; clipId: string; keys: readonly import('./timeline-projection').PositionKey[] } | null
  scalePreview?: { revisionId: string; clipId: string; keys: readonly import('./timeline-projection').ScaleKey[] } | null
  gainPreview?: { revisionId: string; clipId: string; keys: readonly ScaleKey[] } | null
  fadePreview?: ({ revisionId: string; clipId: string } & Partial<ProjectedAudioFades>) | null
  onTime?: (time: number) => void
  transportRef?: Ref<PreviewTransport>
  comparison?: ComparisonPreview
  suspended?: boolean
}) {
  const canvas = useRef<HTMLCanvasElement>(null)
  const host = useRef<HTMLElement>(null)
  const current = useRef<PreviewSession | null>(null)
  const dissolve = useRef(dissolvePlan)
  dissolve.current = dissolvePlan
  const transient = useRef(opacityPreview)
  transient.current = opacityPreview
  const positionDraft = useRef(positionPreview)
  positionDraft.current = positionPreview
  const scaleDraft = useRef(scalePreview)
  scaleDraft.current = scalePreview
  useEffect(() => { if (current.current?.projection) draw(current.current) }, [opacityPreview, dissolvePlan, positionPreview, scalePreview])
  const gainDraft = useRef(gainPreview)
  gainDraft.current = gainPreview
  const fadeDraft = useRef(fadePreview)
  fadeDraft.current = fadePreview
  useEffect(() => {
    const session = current.current
    if (!session?.playing || !session.audio) return
    const cursor = clockCursor(Math.max(0, session.audio.currentTime - session.origin))
    for (const item of session.scheduled) applyGainEnvelope(session, item, cursor)
  }, [gainPreview, fadePreview])
  function applyGainEnvelope(session: PreviewSession, item: ScheduledAudio, cursor: Rational) {
    const draft = gainDraft.current
    const keys = draft && draft.revisionId === session.revisionId && draft.clipId === item.clip.clipId
      ? draft.keys : item.clip.gainKeys
    const first = compare(cursor, item.clip.start) < 0 ? item.clip.start : cursor
    if (compare(first, item.playbackEnd) >= 0) return
    const when = session.origin + toSeconds(first)
    item.param.cancelScheduledValues(when)
    item.param.setValueAtTime(item.gain * scaleAt(keys, first), when)
    for (const key of keys ?? []) {
      if (compare(key.time, first) > 0) {
        item.param.linearRampToValueAtTime(item.gain * key.multiplierMicros / 1_000_000,
          session.origin + toSeconds(key.time))
      }
    }
    const requested = fadeDraft.current
    const edgeDraft = requested?.revisionId === session.revisionId && requested?.clipId === item.clip.clipId ? requested : undefined
    const fades = { fadeIn: edgeDraft?.fadeIn ?? item.clip.audioFades?.fadeIn ?? ZERO,
      fadeOut: edgeDraft?.fadeOut ?? item.clip.audioFades?.fadeOut ?? ZERO }
    // Multiplying two independent AudioParams gives the quadratic overlap,
    // including when a seek starts with both ramps already in progress.
    const at = (time: Rational) => session.origin + toSeconds(time)
    const schedule = (param: AudioParam, durations: ProjectedAudioFades, end: Rational) => {
      const edge = audioFadeEnvelope(durations, item.clip.start, end, first)
      param.cancelScheduledValues(when)
      param.setValueAtTime(edge.value, when)
      if (edge.attackEnd) param.linearRampToValueAtTime(1, at(edge.attackEnd))
      if (edge.releaseStart && (!edge.attackEnd || compare(edge.releaseStart, edge.attackEnd) > 0))
        param.setValueAtTime(1, at(edge.releaseStart))
      if (edge.releaseEnd) param.linearRampToValueAtTime(0, at(edge.releaseEnd))
    }
    schedule(item.authoredFade, fades, item.clip.end)
    schedule(item.transitionGain, item.transitionFades, item.playbackEnd)
  }
  // A changing audition changes source coverage as well as gain. Preserve
  // transport time and rebuild the scheduled sources on the same audio clock.
  useEffect(() => {
    const session = current.current
    if (!session?.playing || !session.audio) return
    const cursor = Math.min(session.duration, Math.max(0, clock(session) - session.origin))
    stopPlayback(session)
    session.cursor = cursor
    session.cursorExact = clockCursor(cursor)
    start(session)
  }, [audioCrossfadePlan])

  function activeAudioTransitions(session: PreviewSession): readonly ProjectedTransition[] {
    const saved = session.projection?.transitions ?? []
    const plan = audioCrossfadePlan
    if (!plan || plan.baseRevisionId !== session.revisionId) return saved
    const step = plan.operations[0]
    switch (step?.definitionId) {
      case 'timeline.transition.audio-crossfade.create':
        if (!('clipId' in step.target)) return saved
        return [...saved, { transitionId: step.parameters.transitionId, outgoingClipId: step.target.clipId,
          incomingClipId: step.parameters.incomingClipId, duration: parseRational(step.parameters.duration)!,
          outgoingHandle: true, audioCrossfade: true }]
      case 'timeline.transition.audio-crossfade.update':
        return saved.map(t => t.audioCrossfade && t.transitionId === step.target.transitionId
          ? { ...t, duration: parseRational(step.parameters.duration)! } : t)
      case 'timeline.transition.audio-crossfade.delete':
        return saved.filter(t => !t.audioCrossfade || t.transitionId !== step.target.transitionId)
      default:
        return saved
    }
  }
  const currentSubtitlePlan = useRef(subtitlePlan)
  currentSubtitlePlan.current = subtitlePlan
  const pendingSubtitle = useRef<{ plan: SemanticBatch; image: SubtitleBitmaps } | null>(null)
  const [cueTarget, setCueTarget] = useState<{ session: PreviewSession; width: number } | null>(null)
  useEffect(() => {
    const session = current.current
    const step = subtitlePlan?.baseRevisionId === revisionId ? subtitlePlan?.operations[0] : null
    if (session?.projection) refreshComposition(session)
    if (!tenantId || !subtitlePlan || step?.definitionId !== 'timeline.subtitle.cue.set'
        || !cueTarget || cueTarget.session !== session) return
    const controller = new AbortController()
    let bitmap: SubtitleBitmaps | null = null
    void loadSubtitle(tenantId, step.parameters, cueTarget.width, controller.signal).then(image => {
      if (controller.signal.aborted) { closeSubtitle(image); return }
      bitmap = image
      pendingSubtitle.current = { plan: subtitlePlan, image }
      if (current.current?.projection) draw(current.current)
    }).catch(error => {
      if (!controller.signal.aborted && current.current) fail(current.current, error)
    })
    return () => {
      controller.abort()
      if (bitmap) closeSubtitle(bitmap)
      if (pendingSubtitle.current?.plan === subtitlePlan) pendingSubtitle.current = null
    }
  }, [subtitlePlan, tenantId, projectId, revisionId, cueTarget])
  const timeCallback = useRef(onTime)
  timeCallback.current = onTime
  const comparisonRef = useRef(comparison)
  comparisonRef.current = comparison
  const suspendedRef = useRef(suspended)
  suspendedRef.current = suspended
  const [status, setStatus] = useState<PreviewStatus>('IDLE')
  const [audition, setAudition] = useState<'idle' | 'loading' | 'ready' | 'failed'>('idle')
  const auditionTransportReady = status === 'READY' || status === 'PLAYING' || status === 'PAUSED'

  useEffect(() => {
    const session = current.current
    if (!session?.audio || !auditionTransportReady) return
    const step = voiceoverPlan?.baseRevisionId === session.revisionId ? voiceoverPlan?.operations[0] : undefined
    const replacement = step?.definitionId === 'timeline.clip.voiceover.replace' && 'clipId' in step.target
      ? step : null

    const swap = (clip: AudioClip | undefined) => {
      const resume = session.playing
      if (resume) {
        session.cursor = Math.min(session.duration, Math.max(0, clock(session) - session.origin))
        session.cursorExact = clockCursor(session.cursor)
        stopPlayback(session)
      }
      session.voiceoverAudition = clip
      if (resume) start(session)
    }
    if (!replacement || !tenantId || !('clipId' in replacement.target)) {
      if (session.voiceoverAudition) swap(undefined)
      setAudition('idle')
      return
    }
    const clipId = replacement.target.clipId
    const saved = session.audioClips.find(item => item.clip.clipId === clipId)
    const sourceStart = parseRational(replacement.parameters.sourceStart)
    if (!saved || !sourceStart) return
    const controller = new AbortController()
    const audio = session.audio
    // Keep the saved transport and picture. Swap only this source after
    // decoding the server-previewed immutable pin.
    setAudition('loading')
    const prepare = async () => {
      const pin = replacement.parameters
      const media = await assetGateway.readSourceContent(tenantId, projectId, pin.artifactId, controller.signal)
      if (!media.ok) throw new Error(media.message)
      const buffer = await audio.decodeAudioData(await media.value.arrayBuffer())
      if (controller.signal.aborted || current.current !== session) return
      swap({ ...saved, buffer, clip: { ...saved.clip, artifactId: pin.artifactId, mediaStreamId: pin.mediaStreamId,
        contentDigest: pin.contentDigest, sourceStart,
        sourceEnd: add(sourceStart, subtract(saved.clip.end, saved.clip.start)) } })
      setAudition('ready')
    }
    void prepare().catch(() => {
      if (!controller.signal.aborted && current.current === session) {
        if (session.voiceoverAudition) swap(undefined)
        setAudition('failed')
      }
    })
    return () => controller.abort()
  }, [voiceoverPlan, auditionTransportReady, tenantId, projectId])

  const [duration, setDuration] = useState(0)
  const [time, setTime] = useState(0)
  const [exactSeek, setExactSeek] = useState('')
  const exactTarget = exactSeek.length <= 64 ? parseRational(exactSeek) ?? decimalCursor(exactSeek) : null
  const [message, setMessage] = useState('')
  const [retry, setRetry] = useState(0)

  function publishTime(seconds: number) {
    setTime(seconds)
    timeCallback.current?.(seconds)
  }

  function refreshComposition(session: PreviewSession) {
    if (!session.projection) return
    let end = timelineDuration(session.projection)
    for (const cue of effectiveSubtitleCues(session.projection, currentSubtitlePlan.current, session.revisionId).cues) {
      const cueEnd = parseRational(cue.end)!
      if (compare(cueEnd, end) > 0) end = cueEnd
    }
    session.end = end
    session.duration = toSeconds(end)
    session.seekInputEnd = nativeSeekInputEnd(session.duration)
    setDuration(session.duration)
    const clamped = compare(session.cursorExact, end) > 0
    if (session.playing && compare(session.cursorExact, end) >= 0) {
      stopPlayback(session)
      setStatus('PAUSED')
    }
    if (clamped) {
      session.cursor = session.duration
      session.cursorExact = end
      publishTime(session.cursor)
    }
    draw(session, clamped)
  }

  function fail(session: PreviewSession, error: unknown) {
    if (current.current !== session) return
    session.playAttempt++
    session.starting = false
    stopPlayback(session)
    if (host.current) host.current.dataset.audioRms = '0'
    setStatus('FAILED')
    comparisonRef.current?.onPrepared(null)
    setMessage(error instanceof Error ? error.message : 'Timeline media could not be played.')
  }

  async function loadSubtitle(tenant: string, cue: SubtitleCue, destinationWidth: number, signal: AbortSignal) {
    const { text, width, fontSize, fontProfile, selectedTextFade } = cue
    const selection = selectedTextFade && { start: selectedTextFade.start, end: selectedTextFade.end }
    const body = { text, width, fontSize, fontProfile: savedSubtitleFont(fontProfile), destinationWidth,
      ...(cue.fontRuns?.length ? { fontRuns: cue.fontRuns } : {}), ...(selection && { selection }) }
    const raster = async (input: typeof body & { paintSelected?: boolean }) =>
      createImageBitmap(await FormalRenderAPI.subtitleLayout(tenant, projectId, input, signal))
    const images: SubtitleBitmaps = { fixed: await raster(body) }
    try {
      if (selection) images.selected = await raster({ ...body, paintSelected: true })
      if (cue.decoration?.enabled) {
        const { assetId, placement } = cue.decoration
        const catalog = await FormalRenderAPI.listSubtitleDecorations(tenant, projectId, signal)
        const asset = catalog.find(item => item.id === assetId && item.placement === placement)
        if (!asset) throw new Error('Subtitle decoration is unavailable')
        const png = await FormalRenderAPI.subtitleDecorationRaster(tenant, projectId, asset.id, destinationWidth, signal)
        images.decoration = { image: await createImageBitmap(png),
          gap: Math.round(asset.gap * destinationWidth / asset.referenceWidth) }
      }
      return images
    } catch (error) {
      closeSubtitle(images)
      throw error
    }
  }

  function draw(session: PreviewSession, forceSeek = false) {
    const target = canvas.current
    if (!target) return
    const raster = previewRaster(session.projection?.compositionAspectRatio ?? DEFAULT_COMPOSITION_ASPECT)
    if (!raster) return
    const { width, height } = raster
    if (target.width !== width || target.height !== height) {
      target.width = width
      target.height = height
    }
    const context = target.getContext('2d')
    if (!context) return
    const time = session.cursorExact
    if (host.current) host.current.dataset.previewCursor = formatRational(time)
    let projection = session.projection
    const plan = dissolve.current
    const step = plan?.operations[0]
    if (projection && plan?.baseRevisionId === revisionId && step) {
      let transitions = projection.transitions
      if ((step.definitionId === 'timeline.transition.cross-dissolve.create'
          || step.definitionId === 'timeline.transition.wipe-left-to-right.create') && 'clipId' in step.target) {
        transitions = [...transitions, { ...step.parameters, outgoingClipId: step.target.clipId,
          ...(step.definitionId === 'timeline.transition.wipe-left-to-right.create' ? { wipeLeftToRight: true } : {}),
          outgoingHandle: true, duration: parseRational(step.parameters.duration)! }]
      } else if (step.definitionId === 'timeline.transition.cross-dissolve.update' || step.definitionId === 'timeline.transition.wipe-left-to-right.update') {
        transitions = transitions.map(t => t.transitionId === step.target.transitionId
          ? { ...t, duration: parseRational(step.parameters.duration)! } : t)
      } else if (step.definitionId === 'timeline.transition.cross-dissolve.delete' || step.definitionId === 'timeline.transition.wipe-left-to-right.delete') {
        transitions = transitions.filter(t => t.transitionId !== step.target.transitionId)
      }
      projection = { ...projection, transitions }
    }
    const clips = projection ? activeClipsAt(projection, time) : []
    const top = clips.length ? clips[clips.length - 1] : null
    if (host.current) host.current.dataset.activeClip = top?.clipId ?? ''
    const activeIds = new Set(clips.map(clip => clip.clipId))
    for (const [clipId, media] of session.videos) {
      if (!activeIds.has(clipId) && !media.paused) media.pause()
    }
    context.fillStyle = '#000'
    context.fillRect(0, 0, target.width, target.height)
    for (const clip of clips) {
      const media = session.videos['get'](clip.clipId)
      if (!media) continue
      const offset = Math.max(0, toSeconds(sourceTimeAt(clip, time)))
      const rate = toSeconds(clip.rate)
      if (Math.abs(media.currentTime - offset) > (forceSeek || !session.playing ? 0.001 : 0.12)) {
        media.currentTime = offset
      }
      if (session.playing && clip.direction === 'FORWARD' && media.paused && !media.seeking) {
        media.playbackRate = rate
        void media.play().catch(error => {
          if (session.playing && error.name !== 'AbortError') fail(session, error)
        })
      }
      if (media.seeking || media.readyState < 2) continue
      const effect = clip.effect.enabled ? clip.effect : null
      const now = toSeconds(time)
      const clipStart = toSeconds(clip.start)
      const clipEnd = toSeconds(clip.end)
      const fadeIn = effect && effect.fadeInSeconds > 0
        ? Math.min(1, Math.max(0, (now - clipStart) / effect.fadeInSeconds)) : 1
      const fadeOut = effect && effect.fadeOutSeconds > 0
        ? Math.min(1, Math.max(0, (clipEnd - now) / effect.fadeOutSeconds)) : 1
      const transition = projection
        ? transitionForIncoming(projection, clip.clipId) : null
      const transitionAlpha = transition && !transition.wipeLeftToRight
        ? Math.min(1, Math.max(0, (now - clipStart) / toSeconds(transition.duration))) : 1
      const t = clip.transform
      const sourceWidth = media.videoWidth
      const sourceHeight = media.videoHeight
      if (!sourceWidth || !sourceHeight) {
        fail(session, new Error('Source video dimensions are unavailable.'))
        return
      }
      const sx = sourceWidth * t.cropLeft
      const sy = sourceHeight * t.cropTop
      const sw = Math.max(1, sourceWidth * (t.cropRight - t.cropLeft))
      const sh = Math.max(1, sourceHeight * (t.cropBottom - t.cropTop))
      const zoom = scaleDraft.current
      const scale = scaleAt(zoom && zoom.revisionId === revisionId && zoom.clipId === clip.clipId ? zoom.keys : clip.scaleKeys, time)
      const dw = target.width * t.scaleX * scale
      const dh = target.height * t.scaleY * scale
      const radians = t.rotationDegrees * Math.PI / 180
      const rotatedWidth = Math.abs(dw * Math.cos(radians)) + Math.abs(dh * Math.sin(radians))
      const rotatedHeight = Math.abs(dw * Math.sin(radians)) + Math.abs(dh * Math.cos(radians))
      let sample: CanvasImageSource = media
      if (effect && (effect.brightness !== 0 || effect.contrast !== 1
          || effect.saturation !== 1 || effect.grayscale)) {
        const surface = session.colorSurface ??= document.createElement('canvas')
        if (surface.width !== sourceWidth || surface.height !== sourceHeight) {
          surface.width = sourceWidth
          surface.height = sourceHeight
        }
        const colorContext = surface.getContext('2d', { willReadFrequently: true })
        if (!colorContext) { fail(session, new Error('Preview color surface unavailable.')); return }
        colorContext.clearRect(0, 0, sourceWidth, sourceHeight)
        colorContext.drawImage(media, 0, 0)
        const frame = colorContext.getImageData(0, 0, sourceWidth, sourceHeight)
        applyBasicVideoColor(frame.data, effect)
        colorContext.putImageData(frame, 0, 0)
        sample = surface
      }
      context.save()
      if (transition?.wipeLeftToRight) {
        context.beginPath()
        context.rect(0, 0, wipeEdge(target.width, time, clip.start, transition.duration), target.height)
        context.clip()
      }
      const override = transient.current
      const keys = override && override.revisionId === revisionId && override.clipId === clip.clipId ? override.keys : clip.opacityKeys
      let opacity = t.opacity
      if (keys) {
        const progress = compare(time, keys.firstTime) <= 0 ? 0 : compare(time, keys.lastTime) >= 0 ? 1
          : toSeconds(subtract(time, keys.firstTime)) / toSeconds(subtract(keys.lastTime, keys.firstTime))
        opacity = keys.firstValue + progress * (keys.lastValue - keys.firstValue)
      }
      context.globalAlpha = opacity * fadeIn * fadeOut * transitionAlpha
      const preview = positionDraft.current
      const positions = preview && preview.revisionId === revisionId && preview.clipId === clip.clipId ? preview.keys : clip.positionKeys
      let { positionX, positionY } = t
      if (positions?.length) {
        const index = positions.findIndex(key => compare(time, key.time) <= 0)
        const right = index < 0 ? positions[positions.length - 1] : positions[index]
        const left = index <= 0 ? right : positions[index - 1]
        const fraction = left === right ? 0 : toSeconds(subtract(time, left.time)) / toSeconds(subtract(right.time, left.time))
        positionX = (left.xMicros + fraction * (right.xMicros - left.xMicros)) / 1000000
        positionY = (left.yMicros + fraction * (right.yMicros - left.yMicros)) / 1000000
      }
      context.translate(positionX * target.width + rotatedWidth / 2,
        positionY * target.height + rotatedHeight / 2)
      context.rotate(radians)
      context.drawImage(sample, sx, sy, sw, sh, -dw / 2, -dh / 2, dw, dh)
      context.restore()
    }
    const cuePlan = currentSubtitlePlan.current
    const { cues, replacement } = projection ? effectiveSubtitleCues(projection, cuePlan, session.revisionId)
      : { cues: [], replacement: null }
    const visibleCues: string[] = []
    const visibleDecorations: string[] = []
    for (const cue of cues.sort((a,b) => a.cueId < b.cueId ? -1 : a.cueId === b.cueId ? 0 : 1)) {
      const pose = subtitlePose(cue, time)
      const image = cue.cueId === replacement
        ? (pendingSubtitle.current?.plan === cuePlan ? pendingSubtitle.current?.image : null)
        : session.subtitleBlocks.get(cue.cueId)
      if (!pose || !image) continue
      const x = pose.x * target.width, y = pose.y * target.height
      const moving = cue.keys.some(key => key.xMicros !== cue.keys[0].xMicros || key.yMicros !== cue.keys[0].yMicros)
      context.save()
      context.globalAlpha = pose.alpha
      context.translate(moving ? x : Math.round(x), moving ? y : Math.round(y))
      if (image.selected) {
        const surface = session.subtitleSurface ??= document.createElement('canvas')
        if (surface.width !== image.fixed.width || surface.height !== image.fixed.height) {
          surface.width = image.fixed.width
          surface.height = image.fixed.height
        }
        const layers = surface.getContext('2d')!
        layers.globalAlpha = 1
        layers.clearRect(0, 0, surface.width, surface.height)
        layers.drawImage(image.fixed, 0, 0)
        layers.globalAlpha = selectedTextAlpha(cue, time)
        layers.drawImage(image.selected, 0, 0)
        context.drawImage(surface, 0, 0)
      } else {
        context.drawImage(image.fixed, 0, 0)
      }
      if (image.decoration && decorationVisible(cue, time)) {
        context.drawImage(image.decoration.image, image.fixed.width + image.decoration.gap, 0)
        visibleDecorations.push(cue.cueId)
      }
      context.restore()
      visibleCues.push(cue.cueId)
    }
    context.globalAlpha = 1
    if (host.current) {
      host.current.dataset.activeSubtitles = visibleCues.join(',')
      host.current.dataset.activeDecorations = visibleDecorations.join(',')
    }
  }

  useEffect(() => {
    const session: PreviewSession = {
      revisionId, controller: new AbortController(), urls: [], videos: new Map(), subtitleBlocks: new Map(), audioClips: [], scheduled: [],
      duration: 0, end: ZERO, seekInputEnd: 0, cursor: 0, cursorExact: ZERO, origin: 0, playing: false, starting: false, playAttempt: 0,
    }
    current.current = session
    setCueTarget(null)
    const signal = session.controller.signal
    publishTime(0)
    setDuration(0)
    setMessage('')
    draw(session)
    if (host.current) host.current.dataset.audioRms = '0'
    const sharedAudio = comparisonRef.current?.audioContext

    async function prepare(revision: string, tenant: string) {
      setStatus('PREPARING')
      comparisonRef.current?.onLoading()
      try {
        const snapshot = await FormalRenderAPI.snapshot(projectId, revision, signal)
        if (signal.aborted) return
        const projection = projectSnapshotJson(snapshot.canonicalTimelineJson)
        const raster = previewRaster(projection.compositionAspectRatio)
        if (!raster) {
          throw new Error('No supported preview raster exactly matches the authored composition aspect ratio.')
        }
        setCueTarget({ session, width: raster.width })
        for (const cue of projection.subtitles ?? []) {
          const bitmap = await loadSubtitle(tenant, cue, raster.width, signal)
          if (signal.aborted) { closeSubtitle(bitmap); return }
          session.subtitleBlocks.set(cue.cueId, bitmap)
        }
        const audioTracks = projection.tracks.filter(track => track.type === 'AUDIO')
        const videoTracks = projection.tracks.filter(track => track.type === 'VIDEO')
        const videoClips = videoTracks.flatMap(track => track.clips)
        // Embedded video audio participates only through an explicit canonical
        // AudioRoute. A second video track never implicitly adds its audio.
        const routedVideoClips = videoClips.filter(clip => audioRouteFor(projection, clip.trackId, clip.clipId) !== null)
        // Embedded video audio is opt-in through an explicit canonical AudioRoute.
        // No video-track-count heuristic silently adds or drops audio.
        const audioClips = [...audioTracks.flatMap(track => track.clips), ...routedVideoClips]
        const scopedSources = projection.clips.length ? await assetGateway.listSourcePins(tenant, validateProjectId(projectId), signal) : null
        if (signal.aborted) return
        if (scopedSources && !scopedSources.ok) throw new Error(scopedSources.message)
        const verifiedSources = new Map(scopedSources?.value.map(source => [String(source.artifactId), source]))
        for (const track of projection.tracks) {
          for (const clip of track.clips) {
            const source = verifiedSources.get(clip.artifactId)
            const selected = source?.streams.filter(stream => stream.kind === track.type)
            if (!source || source.contentDigest !== clip.contentDigest || selected?.length !== 1
                || selected[0].streamId !== clip.mediaStreamId) {
              throw new Error('Preview does not support the selected source stream for ' + clip.clipId)
            }
          }
        }
        for (const clip of audioClips) {
          const streams = verifiedSources.get(clip.artifactId)?.streams.filter(stream => stream.kind === 'AUDIO')
          if (streams?.length !== 1 || clip.direction !== 'FORWARD'
              || clip.rate.numerator !== clip.rate.denominator) {
            throw new Error('Preview does not support the selected audio stream or mapping for ' + clip.clipId)
          }
        }
        const artifacts = new Set([...videoClips, ...audioClips].map(clip => clip.artifactId))
        const blobs = new Map<string, Blob>()
        for (const id of artifacts) {
          if (!id) throw new Error('A Timeline clip has no source Artifact.')
          const result = await assetGateway.readSourceContent(tenant, projectId, id, signal)
          if (signal.aborted) return
          if (!result.ok) throw new Error(result.message)
          blobs.set(id, result.value)
        }
        const videoUrls = new Map<string, string>()
        const videosReady = videoClips.map(clip => {
          let url = videoUrls['get'](clip.artifactId)
          if (!url) {
            url = URL.createObjectURL(blobs['get'](clip.artifactId)!)
            session.urls.push(url)
            videoUrls.set(clip.artifactId, url)
          }
          const media = document.createElement('video')
          media.preload = 'auto'
          media.playsInline = true
          media.muted = true
          media.addEventListener('seeked', () => { if (!session.playing) draw(session) }, { signal })
          media.addEventListener('error', () => fail(session, new Error('A video source became unavailable.')), { signal })
          session.videos.set(clip.clipId, media)
          return loadVideo(media, url, signal)
        })
        await Promise.all(videosReady)
        if (signal.aborted) return
        if (audioClips.length) {
          const context = sharedAudio ? sharedAudio() : new AudioContext({ sampleRate: 48_000 })
          session.audio = context
          const mixer = context.createGain()
          mixer.gain.value = 0.5
          mixer.channelCount = 2
          mixer.channelCountMode = 'explicit'
          const limiter = context.createWaveShaper()
          limiter.curve = new Float32Array([-1, 1])
          const analyser = context.createAnalyser()
          analyser.fftSize = 4096
          mixer.connect(limiter).connect(analyser).connect(context.destination)
          session.mixer = mixer
          session.analyser = analyser
          session.output = analyser
          const buffers = new Map<string, AudioBuffer>()
          for (const clip of audioClips) {
            if (clip.direction !== 'FORWARD' || toSeconds(clip.rate) !== 1) {
              throw new Error('Audio track preview requires forward playback at normal speed.')
            }
            let buffer = buffers['get'](clip.artifactId)
            if (!buffer) {
              buffer = await context.decodeAudioData(await blobs['get'](clip.artifactId)!.arrayBuffer())
              if (signal.aborted) return
              buffers.set(clip.artifactId, buffer)
            }
            const route = audioRouteFor(projection, clip.trackId, clip.clipId)
            session.audioClips.push({ clip, buffer,
              gain: route?.gain ?? 1, mute: route?.mute ?? false, pan: route?.pan ?? 0 })
          }
        }
        session.projection = projection
        refreshComposition(session)
        setStatus('READY')
        comparisonRef.current?.onPrepared(session.end)
      } catch (error) {
        if (!signal.aborted) fail(session, error)
      }
    }

    if (revisionId && tenantId) void prepare(revisionId, tenantId)
    else setStatus('IDLE')
    return () => {
      session.controller.abort()
      stopPlayback(session)
      for (const media of session.videos.values()) {
        media.removeAttribute('src')
        media.load()
      }
      for (const bitmap of session.subtitleBlocks.values()) closeSubtitle(bitmap)
      for (const url of session.urls) URL.revokeObjectURL(url)
      if (sharedAudio) session.output?.disconnect()
      else if (session.audio) void session.audio.close()
      current.current = null
    }
  }, [projectId, retry, revisionId, tenantId])

  function start(session: PreviewSession) {
    const now = clock(session)
    session.origin = now - session.cursor
    session.playing = true
    const audio = session.audio
    if (audio && session.mixer) {
      const transitions = activeAudioTransitions(session)
      for (const saved of session.audioClips) {
        const replacement = session.voiceoverAudition
        const { clip, buffer, gain, mute, pan } = replacement?.clip.clipId === saved.clip.clipId ? replacement : saved
        if (mute) continue
        const { end: playbackEnd, transitionFades } = audioCrossfadePlacement(clip, transitions)
        const clipStart = toSeconds(clip.start)
        const start = Math.max(session.cursor, clipStart)
        const duration = toSeconds(playbackEnd) - start
        if (duration <= 0) continue
        const source = audio.createBufferSource()
        source.buffer = buffer
        const level = audio.createGain()
        level.gain.value = gain
        const authoredFade = audio.createGain()
        const transitionGain = audio.createGain()
        const scheduled = { source, param: level.gain, authoredFade: authoredFade.gain,
          transitionGain: transitionGain.gain, transitionFades, playbackEnd, clip, gain }
        applyGainEnvelope(session, scheduled, session.cursorExact)
        const splitter = audio.createChannelSplitter(2)
        const merger = audio.createChannelMerger(2)
        const left = audio.createGain()
        const right = audio.createGain()
        left.gain.value = (1 - pan) / 2
        right.gain.value = (1 + pan) / 2
        source.connect(level).connect(authoredFade).connect(transitionGain).connect(splitter)
        // Mono sources feed both sides; stereo sources preserve their channels.
        splitter.connect(left, 0)
        splitter.connect(right, Math.min(1, buffer.numberOfChannels - 1))
        left.connect(merger, 0, 0)
        right.connect(merger, 0, 1)
        merger.connect(session.mixer)
        source.start(session.origin + start, toSeconds(clip.sourceStart) + start - clipStart, duration)
        session.scheduled.push(scheduled)
      }
    }
    const samples = session.analyser ? new Float32Array(session.analyser.fftSize) : null
    const advance = () => {
      if (!session.playing) return
      const elapsed = Math.max(0, clock(session) - session.origin)
      const exact = clockCursor(elapsed)
      const reachedEnd = compare(exact, session.end) >= 0
      session.cursorExact = reachedEnd ? session.end : exact
      session.cursor = reachedEnd ? session.duration : elapsed
      publishTime(session.cursor)
      draw(session)
      comparisonRef.current?.onCursor(session.cursorExact)
      if (samples && host.current && session.analyser) {
        session.analyser.getFloatTimeDomainData(samples)
        let energy = 0
        for (const sample of samples) energy += sample * sample
        host.current.dataset.audioRms = Math.sqrt(energy / samples.length).toFixed(4)
      }
      if (compare(session.cursorExact, session.end) >= 0) {
        stopPlayback(session)
        setStatus('PAUSED')
        comparisonRef.current?.onEnd()
      } else {
        session.frame = requestAnimationFrame(advance)
      }
    }
    setStatus('PLAYING')
    session.frame = requestAnimationFrame(advance)
    comparisonRef.current?.onStarted()
  }

  function pause(): Rational {
    const session = current.current
    if (!session) return ZERO
    session.playAttempt++
    session.starting = false
    if (session.playing) {
      const now = clockCursor(Math.max(0, clock(session) - session.origin))
      session.cursorExact = compare(now, session.end) < 0 ? now : session.end
      session.cursor = toSeconds(session.cursorExact)
      stopPlayback(session)
      publishTime(session.cursor)
      draw(session, true)
      if (host.current) host.current.dataset.audioRms = '0'
      setStatus('PAUSED')
    }
    return session.cursorExact
  }

  async function play(resetOnEnd: boolean) {
    const session = current.current
    if (!session?.duration || session.playing || session.starting || suspendedRef.current) return
    if (!resetOnEnd && compare(session.cursorExact, session.end) >= 0) return
    const attempt = ++session.playAttempt
    session.starting = true
    try {
      if (session.audio) await session.audio.resume()
      if (current.current !== session || session.playAttempt !== attempt || suspendedRef.current) return
      if (compare(session.cursorExact, session.end) >= 0) {
        if (!resetOnEnd) return
        session.cursorExact = ZERO
        session.cursor = 0
        publishTime(0)
        draw(session, true)
      }
      start(session)
    } catch (error) {
      if (session.playAttempt === attempt) fail(session, error)
    } finally {
      if (session.playAttempt === attempt) session.starting = false
    }
  }

  function toggle() {
    if (current.current?.playing || current.current?.starting) pause()
    else void play(true)
  }

  useImperativeHandle(transportRef, () => ({ pause, seek, play: () => { void play(false) } }))
  useLayoutEffect(() => { if (suspended) pause() }, [suspended])

  function seek(position: Rational, nativeEnd = false) {
    const session = current.current
    if (!session?.projection) return
    const resume = session.playing
    stopPlayback(session)
    session.cursorExact = nativeEnd || compare(position, session.end) >= 0 ? session.end
      : compare(position, ZERO) < 0 ? ZERO : position
    session.cursor = toSeconds(session.cursorExact)
    publishTime(session.cursor)
    draw(session, true)
    if (resume) start(session)
  }

  return <section ref={host} aria-label="Timeline media preview" data-preview-status={status}
    data-preview-revision={revisionId} data-audition-status={audition} data-preview-time={time.toFixed(3)} data-audio-rms="0">
    <p role="status">{
      status === 'PREPARING' ? 'Preparing Timeline media preview…'
        : status === 'READY' ? 'Preview ready.'
          : status === 'PLAYING' ? 'Playing the current Timeline revision.'
            : status === 'PAUSED' ? 'Paused.' : status === 'FAILED' ? message : 'No preview is available.'
    }</p>
    {audition !== 'idle' && <p role="status">{audition === 'loading' ? 'Loading replacement audio…'
      : audition === 'ready' ? 'Auditioning replacement voiceover. Saved revision is unchanged.'
        : 'Replacement audio cannot be auditioned. The preview is playing saved media.'}</p>}
    <canvas ref={canvas} width={320} height={240} hidden={status === 'FAILED'} aria-label="Timeline preview canvas"
      style={{ display: status === 'FAILED' ? 'none' : 'block', width: '100%', maxWidth: 480, background: '#000', borderRadius: 4 }} />
    {!comparison && <><div style={{ display: 'flex', gap: 8, alignItems: 'center', marginTop: 8 }}>
      <Button type="button" disabled={!duration || (audition === 'loading' && status !== 'PLAYING') || !['READY', 'PAUSED', 'PLAYING'].includes(status)}
        onClick={() => void toggle()}>{status === 'PLAYING' ? 'Pause Timeline' : 'Play Timeline'}</Button>
      {/* The native input must be able to reach a canonical end that is off the centisecond grid. */}
      <input aria-label="Timeline preview seek" type="range" min={0} max={seekInputMax(duration)}
        step="any" value={time} onChange={event => {
          const cursor = decimalCursor(event.target.value)
          if (cursor) seek(cursor, event.target.valueAsNumber === current.current?.seekInputEnd)
        }} style={{ flex: 1, minWidth: 0 }} />
      <output aria-label="Timeline preview time">{time.toFixed(2)}s / {duration.toFixed(2)}s</output>
    </div>
    <details>
      <summary>Precise seek</summary>
      <label>Exact preview time (seconds) <input value={exactSeek} placeholder="1/24" maxLength={64}
        onChange={event => setExactSeek(event.target.value)} onKeyDown={event => {
          if (event.key === 'Enter' && exactTarget && exactTarget.numerator >= 0n && duration > 0
            && (status === 'READY' || status === 'PAUSED' || status === 'PLAYING')) {
            event.preventDefault()
            seek(exactTarget)
          }
        }} /></label>
      <Button type="button" disabled={!exactTarget || exactTarget.numerator < 0n || !duration
        || !['READY', 'PAUSED', 'PLAYING'].includes(status)}
        onClick={() => { if (exactTarget) seek(exactTarget) }}>Seek exact time</Button>
    </details></>}
    {status === 'FAILED' && <Button type="button" onClick={() => setRetry(value => value + 1)}>Retry preview</Button>}
  </section>
}
