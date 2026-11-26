import type { SemanticStep } from '../../contracts/semantic-batch.generated'
import { decodeCompositionAspect, DEFAULT_COMPOSITION_ASPECT, type CompositionAspectRatio } from '../../contracts/app/composition-aspect'
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
  readonly outgoingHandle?: boolean
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

const TRANSFORM_FIELDS = {
  positionX: 'positionXMicros', positionY: 'positionYMicros',
  scaleX: 'scaleXMicros', scaleY: 'scaleYMicros', rotationDegrees: 'rotationMicroDegrees',
  cropLeft: 'cropLeftMicros', cropRight: 'cropRightMicros',
  cropTop: 'cropTopMicros', cropBottom: 'cropBottomMicros', opacity: 'opacityMicros',
} as const
const UNIT = 1_000_000
const FORMAT = 'composition-transform-micro-v1'

function object(value: unknown, label: string): Record<string, unknown> {
  if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error('Invalid ' + label)
  return value as Record<string, unknown>
}

function entries(value: unknown, label: string): unknown[] {
  if (!Array.isArray(value)) throw new Error('Invalid ' + label)
  return value
}

function text(value: unknown, label: string): string {
  if (typeof value !== 'string' || !value.trim()) throw new Error('Invalid ' + label)
  return value
}

function projectedNumber(value: unknown, label: string, min: number, max: number): number {
  if (typeof value !== 'number' || !Number.isFinite(value) || value < min || value > max) {
    throw new Error('Invalid ' + label)
  }
  return value
}

type TransformParameters = Extract<SemanticStep, { definitionId: 'timeline.clip.transform.set' }>['parameters']

export function videoTransformParameters(draft: ProjectedVideoTransform): TransformParameters {
  const encoded = Object.fromEntries(Object.entries(TRANSFORM_FIELDS).map(([property, wire]) => {
    const value = draft[property as keyof ProjectedVideoTransform]
    if (!Number.isFinite(value)) throw new Error('Invalid transform ' + property)
    return [wire, Math.round(value * UNIT)]
  }))
  const parameters = { ...encoded, format: FORMAT }
  projectTransform(parameters)
  return parameters as TransformParameters
}

function projectTransform(value: unknown): ProjectedVideoTransform {
  if (value == null) return IDENTITY_VIDEO_TRANSFORM
  const fields = object(value, 'Timeline transform')
  if (fields.format !== FORMAT || Object.keys(fields).length !== 11) {
    throw new Error('Unsupported Timeline transform format')
  }
  const exact = (property: keyof ProjectedVideoTransform, min: number, max: number) => {
    const v = projectedNumber(fields[TRANSFORM_FIELDS[property]], property, min * UNIT, max * UNIT)
    if (!Number.isInteger(v)) throw new Error('Nonintegral Timeline transform ' + property)
    return v / UNIT
  }
  const transform = {
    positionX: exact('positionX', -16, 16), positionY: exact('positionY', -16, 16),
    scaleX: exact('scaleX', 1 / UNIT, 10), scaleY: exact('scaleY', 1 / UNIT, 10),
    rotationDegrees: exact('rotationDegrees', -180, 180 - 1 / UNIT),
    cropLeft: exact('cropLeft', 0, 1), cropRight: exact('cropRight', 0, 1),
    cropTop: exact('cropTop', 0, 1), cropBottom: exact('cropBottom', 0, 1),
    opacity: exact('opacity', 0, 1),
  }
  if (transform.cropLeft >= transform.cropRight || transform.cropTop >= transform.cropBottom) {
    throw new Error('Invalid Timeline crop rectangle')
  }
  return transform
}

function projectEffect(value: unknown): ProjectedVideoEffect {
  if (value == null) return IDENTITY_VIDEO_EFFECT
  const effects = entries(value, 'Timeline effects')
  if (!effects.length) return IDENTITY_VIDEO_EFFECT
  if (effects.length !== 1) throw new Error('Unsupported Timeline effect stack')
  const entry = object(effects[0], 'Timeline effect')
  if (entry.effectKey !== 'video.basic') throw new Error('Unsupported Timeline effect definition')
  const p = object(entry.parameters, 'basic video effect parameters')
  if (p.format !== 'rgb-basic-v1' || Object.keys(p).length !== 8 || typeof p.grayscale !== 'boolean' || typeof p.enabled !== 'boolean') {
    throw new Error('Invalid basic video effect parameters')
  }
  const number = (key: string, min: number, max = Infinity) =>
    projectedNumber(p[key], 'basic video effect ' + key, min, max)
  return { brightness: number('brightness', -1, 1), contrast: number('contrast', 0, 2),
    saturation: number('saturation', 0, 2), grayscale: p.grayscale,
    fadeInSeconds: number('fadeInSeconds', 0), fadeOutSeconds: number('fadeOutSeconds', 0), enabled: p.enabled }
}

export interface OpacityKeys {
  firstTime: Rational
  firstValue: number
  lastTime: Rational
  lastValue: number
}

export interface ProjectedClip {
  readonly opacityKeys?: OpacityKeys
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
  readonly type: 'VIDEO' | 'AUDIO'
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
  readonly compositionAspectRatio: CompositionAspectRatio
  readonly tracks: readonly ProjectedTrack[]
  readonly clips: readonly ProjectedClip[]
  readonly audioRoutes: readonly ProjectedAudioRoute[]
  readonly transitions: readonly ProjectedTransition[]
}

function exactTime(value: unknown, label: string): Rational {
  const time = typeof value === 'string' ? parseRational(value) : null
  if (!time || time.numerator < 0n || time.denominator <= 0n) throw new Error('Invalid ' + label)
  return time
}

function projectClip(value: unknown, trackId: string): ProjectedClip {
  const clip = object(value, 'Timeline clip')
  if (clip.sourceKind !== 'MEDIA_STREAM') throw new Error('Unsupported Timeline clip source')
  const start = exactTime(clip.startTime, 'clip start')
  const end = exactTime(clip.endTime, 'clip end')
  const sourceStart = exactTime(clip.trimStart, 'source start')
  const sourceEnd = exactTime(clip.trimEnd, 'source end')
  if (compare(start, end) >= 0 || compare(sourceStart, sourceEnd) >= 0) throw new Error('Invalid clip range')
  const mapping = object(clip.temporalMapping, 'Timeline temporal mapping')
  if (mapping.kind !== 'CONSTANT_RATE' || (mapping.direction !== 'FORWARD' && mapping.direction !== 'REVERSE')) {
    throw new Error('Unsupported Timeline temporal mapping')
  }
  const rate = object(mapping.rate, 'Timeline playback rate')
  const numerator = projectedNumber(rate.numerator, 'playback rate numerator', 1, Number.MAX_SAFE_INTEGER)
  const denominator = projectedNumber(rate.denominator, 'playback rate denominator', 1, Number.MAX_SAFE_INTEGER)
  if (!Number.isSafeInteger(numerator) || !Number.isSafeInteger(denominator)) throw new Error('Inexact Timeline rate')
  const contentDigest = text(clip.contentDigest, 'clip content digest')
  if (!/^[a-f0-9]{64}$/.test(contentDigest)) throw new Error('Invalid clip content digest')
  return {
    clipId: text(clip.clipId, 'clip identity'), trackId, start, end, sourceStart, sourceEnd,
    artifactId: text(clip.artifactId, 'clip artifact'), contentDigest,
    mediaStreamId: text(clip.mediaStreamId, 'clip media stream'),
    rate: { numerator: BigInt(numerator), denominator: BigInt(denominator) },
    direction: mapping.direction, transform: projectTransform(clip.transform), effect: projectEffect(clip.effects),
  }
}

/** Reject unsupported semantics at the read-only projection boundary. */
export function projectTimeline(document: unknown): TimelineProjection {
  const root = object(document, 'Timeline snapshot')
  if (root.schemaVersion !== 'timeline-1.0') throw new Error('Unsupported Timeline schema version')
  for (const field of ['textElements']) {
    if (root[field] !== undefined && entries(root[field], field).length) {
      throw new Error('Unsupported Timeline preview semantics: ' + field)
    }
  }
  const metadata = root.metadata == null ? {} : object(root.metadata, 'Timeline metadata')
  const aspect = metadata.compositionAspectRatio === undefined ? DEFAULT_COMPOSITION_ASPECT
    : decodeCompositionAspect(metadata.compositionAspectRatio)
  const curves = entries(root.automations ?? [], 'automations').map(c => object(c, 'automation'))
  const consumed = new Set()
  const tracks: ProjectedTrack[] = entries(root.tracks, 'Timeline tracks').map(raw => {
    const track = object(raw, 'Timeline track')
    if (track.type !== 'video' && track.type !== 'audio') throw new Error('Unsupported Timeline track type')
    const trackId = text(track.trackId, 'track identity')
    return { trackId, name: typeof track.name === 'string' ? track.name : trackId, type: track.type === 'video' ? 'VIDEO' : 'AUDIO',
      clips: entries(track.clips, 'track clips').map(rawClip => {
        const clip = projectClip(rawClip, trackId)
        const effects = entries(object(rawClip, 'clip').effects ?? [], 'effects').map(e => object(e, 'effect'))
        const bound = curves.filter(c => effects.some(e => e.id != null && e.id === c.targetEntityId))
        if (!bound.length) return clip
        const curve = bound[0]
        const keys = entries(curve.keyframes, 'opacity keys').map(k => object(k, 'opacity key'))
        if (bound.length !== 1 || curve.parameterPath !== 'opacity' || track.type !== 'video'
          || curve.valueType !== 'float' || curve.extrapolation !== 'HOLD' || keys.length !== 2
          || keys.some(k => k.interpolation !== 'LINEAR')) throw new Error('Unsupported Timeline preview semantics: automation curve. Its saved keys are preserved.')
        const firstTime = exactTime(keys[0].time, 'first opacity time'), lastTime = exactTime(keys[1].time, 'last opacity time')
        if (compare(firstTime, clip.start) < 0 || compare(firstTime, lastTime) >= 0 || compare(lastTime, clip.end) >= 0)
          throw new Error('Opacity keys lie outside the visible clip interval')
        consumed.add(curve)
        return { ...clip, opacityKeys: { firstTime, lastTime, firstValue: projectedNumber(keys[0].value, 'opacity', 0, 1),
          lastValue: projectedNumber(keys[1].value, 'opacity', 0, 1) } }
      }) }
  })
  if (consumed.size !== curves.length) throw new Error('Unsupported Timeline preview semantics: automations')
  const clips = tracks.flatMap(track => track.clips)
  if (new Set(tracks.map(track => track.trackId)).size !== tracks.length ||
      new Set(clips.map(clip => clip.clipId)).size !== clips.length) throw new Error('Duplicate Timeline identity')

  const rawMix = root.audioMix === undefined ? { routes: [] } : object(root.audioMix, 'Timeline audio mix')
  const audioRoutes = entries(rawMix.routes, 'Timeline audio routes').map(raw => {
    const route = object(raw, 'Timeline audio route')
    const input = object(route.input, 'audio route input')
    const trackId = text(input.trackId, 'audio route track')
    const clipId = text(input.clipId, 'audio route clip')
    if (!clips.some(clip => clip.trackId === trackId && clip.clipId === clipId)) throw new Error('Unmatched audio route')
    const mute = object(route.mute, 'audio route mute').muted
    if (typeof mute !== 'boolean') throw new Error('Invalid audio route mute')
    return { trackId, clipId, mute,
      gain: projectedNumber(object(route.gain, 'audio route gain').linear, 'audio route gain', 0, 2),
      pan: projectedNumber(object(route.balance, 'audio route balance').value, 'audio route balance', -1, 1) }
  })

  const incoming = new Set<string>()
  const transitions = entries(root.transitions === undefined ? [] : root.transitions, 'Timeline transitions').map(raw => {
    const t = object(raw, 'Timeline transition')
    const transitionId = text(t.transitionId, 'transition identity')
    const outgoingClipId = text(t.outgoingClipId, 'outgoing clip')
    const incomingClipId = text(t.incomingClipId, 'incoming clip')
    const duration = exactTime(t.duration, 'transition duration')
    const outgoingHandle = t.alignment === 'START_AT_CUT' && t.temporalPolicy === 'USE_SOURCE_HANDLES'
    if (outgoingClipId === incomingClipId || duration.numerator <= 0n || incoming.has(incomingClipId)
        || !clips.some(clip => clip.clipId === incomingClipId) || !clips.some(clip => clip.clipId === outgoingClipId)
        || t.transitionDefinitionId !== 'video.cross-dissolve' || t.transitionDefinitionVersion !== '1.0'
        || t.mediaType !== 'VIDEO' || (!outgoingHandle && (t.alignment !== 'START_AT_INCOMING' || t.temporalPolicy !== 'OVERLAP_TIMELINE'))
        || object(t.parameters, 'transition parameters').curve !== 'LINEAR') {
      throw new Error('Unsupported Timeline transition')
    }
    incoming.add(incomingClipId)
    return { transitionId, outgoingClipId, incomingClipId, duration, ...(outgoingHandle ? { outgoingHandle } : {}) }
  })
  return { compositionAspectRatio: aspect, tracks, clips, audioRoutes, transitions }
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

/** First track that can host a video clip. */
export function firstVideoTrack(projection: TimelineProjection): ProjectedTrack | null {
  return projection.tracks.find(track => track.type === 'VIDEO') ?? null
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
