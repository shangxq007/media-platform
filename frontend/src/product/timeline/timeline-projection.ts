/**
 * Read-only projection of a canonical Timeline snapshot.
 *
 * The projection is derived from the authoritative snapshot on every HEAD refresh and is never a
 * second persistent Timeline authority: it holds no draft, performs no writes and can be rebuilt
 * from the same snapshot bytes at any time.
 */
import { add, compare, parseRational, type Rational } from './trim'

export interface ProjectedVideoEffect {
  readonly brightness: number
  readonly contrast: number
  readonly saturation: number
  readonly grayscale: boolean
  readonly fadeInSeconds: number
  readonly fadeOutSeconds: number
  readonly enabled: boolean
}

export const IDENTITY_VIDEO_EFFECT: ProjectedVideoEffect = {
  brightness: 0, contrast: 1, saturation: 1, grayscale: false,
  fadeInSeconds: 0, fadeOutSeconds: 0, enabled: true,
}

export interface ProjectedTransition {
  readonly transitionId: string
  readonly outgoingClipId: string
  readonly incomingClipId: string
  readonly duration: Rational
}

export interface ProjectedVideoTransform {
  readonly positionX: number
  readonly positionY: number
  readonly scaleX: number
  readonly scaleY: number
  readonly rotationDegrees: number
  readonly cropLeft: number
  readonly cropRight: number
  readonly cropTop: number
  readonly cropBottom: number
  readonly opacity: number
}

export const IDENTITY_VIDEO_TRANSFORM: ProjectedVideoTransform = {
  positionX: 0, positionY: 0, scaleX: 1, scaleY: 1, rotationDegrees: 0,
  cropLeft: 0, cropRight: 1, cropTop: 0, cropBottom: 1, opacity: 1,
}

function projectTransform(value: unknown): ProjectedVideoTransform {
  if (!value || typeof value !== 'object') return IDENTITY_VIDEO_TRANSFORM
  const t = value as Record<string, unknown>
  const number = (key: string, fallback: number) =>
    typeof t[key] === 'number' && Number.isFinite(t[key]) ? t[key] as number : fallback
  return {
    positionX: number('positionX', 0), positionY: number('positionY', 0),
    scaleX: number('scaleX', 1), scaleY: number('scaleY', 1),
    rotationDegrees: number('rotationDegrees', 0),
    cropLeft: number('cropLeft', 0), cropRight: number('cropRight', 1),
    cropTop: number('cropTop', 0), cropBottom: number('cropBottom', 1),
    opacity: number('opacity', 1),
  }
}

function projectEffect(value: unknown): ProjectedVideoEffect {
  if (value == null) return IDENTITY_VIDEO_EFFECT
  if (!Array.isArray(value)) throw new Error('Invalid Timeline effects')
  const effects = value.filter(item => item && typeof item === 'object' && item.effectKey === 'video.basic')
  if (!effects.length) return IDENTITY_VIDEO_EFFECT
  if (effects.length !== 1) throw new Error('Conflicting basic video effects')
  const p = (effects[0] as { parameters?: Record<string, unknown> }).parameters
  if (!p || Object.keys(p).length !== 7 || typeof p.grayscale !== 'boolean' || typeof p.enabled !== 'boolean') {
    throw new Error('Invalid basic video effect parameters')
  }
  const number = (key: string, min: number, max = Infinity) => {
    const value = p[key]
    if (typeof value !== 'number' || !Number.isFinite(value) || value < min || value > max) {
      throw new Error(`Invalid basic video effect ${key}`)
    }
    return value
  }
  return { brightness: number('brightness', -1, 1), contrast: number('contrast', 0, 2),
    saturation: number('saturation', 0, 2), grayscale: p.grayscale,
    fadeInSeconds: number('fadeInSeconds', 0), fadeOutSeconds: number('fadeOutSeconds', 0), enabled: p.enabled }
}

export interface ProjectedClip {
  readonly clipId: string
  readonly trackId: string
  readonly artifactId: string
  readonly contentDigest: string
  readonly mediaStreamId: string
  readonly start: Rational
  readonly end: Rational
  readonly sourceStart: Rational
  readonly sourceEnd: Rational
  readonly rate: Rational
  readonly direction: 'FORWARD' | 'REVERSE'
  readonly transform: ProjectedVideoTransform
  readonly effect: ProjectedVideoEffect
}

export interface ProjectedTrack {
  readonly trackId: string
  readonly name: string
  readonly type: string
  readonly clips: readonly ProjectedClip[]
}

export interface ProjectedAudioRoute {
  readonly trackId: string
  readonly clipId: string
  readonly gain: number
  readonly mute: boolean
  readonly pan: number
}

export interface TimelineProjection {
  readonly tracks: readonly ProjectedTrack[]
  readonly clips: readonly ProjectedClip[]
  readonly audioRoutes: readonly ProjectedAudioRoute[]
  readonly transitions: readonly ProjectedTransition[]
}

const IDENTITY: Rational = { numerator: 1n, denominator: 1n }

function safeRational(value: unknown): Rational | null {
  if (typeof value !== 'string') return null
  return parseRational(value)
}

/** Projects a parsed canonical Timeline document; malformed clips are omitted, never invented. */
export function projectTimeline(document: unknown): TimelineProjection {
  const rawTracks = (document as { tracks?: unknown } | null)?.tracks
  const rawMix = (document as { audioMix?: { routes?: unknown } } | null)?.audioMix
  const audioRoutes: ProjectedAudioRoute[] = []
  if (rawMix && Array.isArray(rawMix.routes)) {
    for (const rawRoute of rawMix.routes) {
      if (!rawRoute || typeof rawRoute !== 'object') continue
      const route = rawRoute as { input?: { trackId?: unknown; clipId?: unknown }; gain?: { linear?: unknown }; mute?: { muted?: unknown }; balance?: { value?: unknown } }
      const trackId = typeof route.input?.trackId === 'string' ? route.input.trackId : ''
      const clipId = typeof route.input?.clipId === 'string' ? route.input.clipId : ''
      if (!trackId || !clipId) continue
      const gain = typeof route.gain?.linear === 'number' && Number.isFinite(route.gain.linear)
        && route.gain.linear >= 0 && route.gain.linear <= 2 ? route.gain.linear : 1
      const pan = typeof route.balance?.value === 'number' && Number.isFinite(route.balance.value)
        && route.balance.value >= -1 && route.balance.value <= 1 ? route.balance.value : 0
      audioRoutes.push({ trackId, clipId, gain, mute: route.mute?.muted === true, pan })
    }
  }
  const transitions: ProjectedTransition[] = []
  const rawTransitions = (document as { transitions?: unknown } | null)?.transitions
  const incomingTransitions = new Set<string>()
  if (rawTransitions != null && !Array.isArray(rawTransitions)) throw new Error('Invalid Timeline transitions')
  if (Array.isArray(rawTransitions)) {
    for (const raw of rawTransitions) {
      if (!raw || typeof raw !== 'object') throw new Error('Invalid Timeline transition')
      const t = raw as Record<string, unknown>
      const duration = safeRational(t.duration)
      if (typeof t.transitionId !== 'string' || !t.transitionId
          || typeof t.outgoingClipId !== 'string' || typeof t.incomingClipId !== 'string'
          || t.outgoingClipId === t.incomingClipId || !duration || duration.numerator <= 0n
          || t.transitionDefinitionId !== 'video.cross-dissolve' || t.transitionDefinitionVersion !== '1.0'
          || t.mediaType !== 'VIDEO' || t.alignment !== 'START_AT_INCOMING' || t.temporalPolicy !== 'OVERLAP_TIMELINE'
          || (t.parameters as { curve?: unknown } | undefined)?.curve !== 'LINEAR') {
        throw new Error('Unsupported Timeline transition')
      }
      if (incomingTransitions.has(t.incomingClipId)) throw new Error('Conflicting incoming Timeline transitions')
      incomingTransitions.add(t.incomingClipId)
      transitions.push({ transitionId: t.transitionId, outgoingClipId: t.outgoingClipId,
        incomingClipId: t.incomingClipId, duration })
    }
  }
  const tracks: ProjectedTrack[] = []
  if (!Array.isArray(rawTracks)) return { tracks, clips: [], audioRoutes, transitions }
  for (const rawTrack of rawTracks) {
    if (!rawTrack || typeof rawTrack !== 'object') continue
    const track = rawTrack as Record<string, unknown>
    const trackId = typeof track.trackId === 'string' ? track.trackId : ''
    if (!trackId) continue
    const clips: ProjectedClip[] = []
    const rawClips = Array.isArray(track.clips) ? track.clips : []
    for (const rawClip of rawClips) {
      if (!rawClip || typeof rawClip !== 'object') continue
      const clip = rawClip as Record<string, unknown>
      const clipId = typeof clip.clipId === 'string' ? clip.clipId : ''
      const start = safeRational(clip.startTime)
      const end = safeRational(clip.endTime)
      const sourceStart = safeRational(clip.trimStart)
      const sourceEnd = safeRational(clip.trimEnd)
      if (!clipId || !start || !end || !sourceStart || !sourceEnd) continue
      const mapping = clip.temporalMapping as { rate?: { numerator?: number; denominator?: number }; direction?: string } | undefined
      const numerator = mapping?.rate?.numerator
      const denominator = mapping?.rate?.denominator
      const rate = Number.isSafeInteger(numerator) && Number.isSafeInteger(denominator)
        && (numerator as number) > 0 && (denominator as number) > 0
        ? { numerator: BigInt(numerator as number), denominator: BigInt(denominator as number) }
        : IDENTITY
      clips.push({
        clipId, trackId, start, end, sourceStart, sourceEnd, rate,
        artifactId: typeof clip.artifactId === 'string' ? clip.artifactId : '',
        contentDigest: typeof clip.contentDigest === 'string' ? clip.contentDigest : '',
        mediaStreamId: typeof clip.mediaStreamId === 'string' ? clip.mediaStreamId : '',
        direction: mapping?.direction === 'REVERSE' ? 'REVERSE' : 'FORWARD',
        transform: projectTransform(clip.transform),
        effect: projectEffect(clip.effects),
      })
    }
    tracks.push({
      trackId,
      name: typeof track.name === 'string' ? track.name : trackId,
      type: typeof track.type === 'string' ? track.type.toUpperCase() : 'VIDEO',
      clips,
    })
  }
  return { tracks, clips: tracks.flatMap(track => track.clips), audioRoutes, transitions }
}

export function projectSnapshotJson(canonicalTimelineJson: string): TimelineProjection {
  return projectTimeline(JSON.parse(canonicalTimelineJson))
}

export function clipById(projection: TimelineProjection, clipId: string): ProjectedClip | null {
  return projection.clips.find(clip => clip.clipId === clipId) ?? null
}

export function trackById(projection: TimelineProjection, trackId: string): ProjectedTrack | null {
  return projection.tracks.find(track => track.trackId === trackId) ?? null
}

export function audioRouteFor(projection: TimelineProjection, trackId: string, clipId: string): ProjectedAudioRoute | null {
  return projection.audioRoutes.find(route => route.trackId === trackId && route.clipId === clipId) ?? null
}

/** First track that can host a video clip (falls back to the first track). */
export function firstVideoTrack(projection: TimelineProjection): ProjectedTrack | null {
  return projection.tracks.find(track => track.type === 'VIDEO') ?? projection.tracks[0] ?? null
}

/** End of the latest clip in the projection, used as a default append position. */
export function timelineEnd(projection: TimelineProjection): Rational {
  let end: Rational = { numerator: 0n, denominator: 1n }
  for (const clip of projection.clips) if (compare(clip.end, end) > 0) end = clip.end
  return end
}

/** Start of the largest gap after the last clip, or 0 for an empty Timeline. */
export function defaultAppendStart(projection: TimelineProjection): Rational {
  return timelineEnd(projection)
}

export function addDuration(start: Rational, duration: Rational): Rational {
  return add(start, duration)
}
