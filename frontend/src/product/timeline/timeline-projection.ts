import { subtitleDecorationId } from '../../contracts/app/render-job'
import type { SemanticStep } from '../../contracts/semantic-batch.generated'
import { decodeCompositionAspect, DEFAULT_COMPOSITION_ASPECT, type CompositionAspectRatio } from '../../contracts/app/composition-aspect'
/**
 * Read-only projection of a canonical Timeline snapshot.
 *
 * The projection is derived from the authoritative snapshot on every HEAD refresh and is never a
 * second persistent Timeline authority: it holds no draft, performs no writes and can be rebuilt
 * from the same snapshot bytes at any time.
 */
import { add, compare, formatRational, parseRational, subtract, ZERO, type Rational } from './trim'
import { savedSubtitleFont, type SubtitleCue } from './subtitle-cues'

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
  readonly audioCrossfade?: true
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
  const effects = entries(value, 'Timeline effects').filter(raw => {
    const key = object(raw, 'Timeline effect').effectKey
    return key !== 'audio.fade_in' && key !== 'audio.fade_out'
  })
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

export interface PositionKey {
  readonly time: Rational
  readonly xMicros: number
  readonly yMicros: number
}

export interface ScaleKey {
  readonly time: Rational
  readonly multiplierMicros: number
}

export function scaleMultiplierBounds(transform: ProjectedVideoTransform) {
  const x = Math.round(transform.scaleX * 1_000_000)
  const y = Math.round(transform.scaleY * 1_000_000)
  return { min: Math.ceil(1_000_000 / Math.min(x, y)), max: Math.floor(10_000_000_000_000 / Math.max(x, y)) }
}

export interface OpacityKeys {
  firstTime: Rational
  firstValue: number
  lastTime: Rational
  lastValue: number
}

export interface ProjectedAudioFades {
  readonly fadeIn: Rational
  readonly fadeOut: Rational
}

export interface ProjectedClip {
  readonly opacityKeys?: OpacityKeys
  readonly positionKeys?: readonly PositionKey[]
  readonly scaleKeys?: readonly ScaleKey[]
  readonly gainKeys?: readonly ScaleKey[]
  readonly audioFades?: ProjectedAudioFades
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
  readonly subtitles?: readonly SubtitleCue[]
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

function projectSubtitle(raw: unknown): SubtitleCue {
  const element = object(raw, 'TextElement')
  if (!element.subtitleCue) throw new Error('Unsupported Timeline preview semantics: textElements')
  const motion = object(element.subtitleCue, 'subtitle motion')
  const fontProfile = savedSubtitleFont(motion.fontProfile)
  const fontRational = (raw: unknown) => {
    const value = object(raw, 'exact subtitle time or dimension')
    if (!Number.isSafeInteger(value.numerator) || !Number.isSafeInteger(value.denominator)) throw new Error('Invalid subtitle rational')
    const rational = parseRational(String(value.numerator) + '/' + String(value.denominator))
    if (!rational) throw new Error('Invalid subtitle rational')
    return rational
  }
  const start = fontRational(element.start), duration = fontRational(element.duration)
  const styled = object(element.styledText, 'styled subtitle')
  const frame = object(element.frame, 'subtitle frame')
  const styleRuns = entries(styled.styleRuns, 'subtitle style runs').map(run => object(run, 'subtitle style run'))
  const style = object(styleRuns[0].style, 'subtitle style')
  const canonicalStyleRuns = styleRuns.every(run => run.range != null)
    ? styleRuns.map(run => {
      const range = object(run.range, 'subtitle style range')
      return { start: projectedNumber(range.start, 'style range start', 0, 384),
        end: projectedNumber(range.end, 'style range end', 0, 384),
        styleIdentity: JSON.stringify(object(run.style, 'canonical text style')) }
    }) : null
  const hasPinnedTextRanges = (Array.isArray(styled.semanticRuns) && styled.semanticRuns.length > 0)
    || (Array.isArray(element.resolvedFontRuns) && element.resolvedFontRuns.length > 0)
  const size = fontRational(object(style.fontSize, 'subtitle font size').value)
  const width = fontRational(frame.widthConstraint)
  if (size.denominator !== 1n || width.denominator !== 1n) throw new Error('Invalid subtitle layout dimensions')
  const fontRuns = entries(motion.fontRuns ?? [], 'subtitle font ranges').map(raw => {
    const run = object(raw, 'subtitle font range')
    const start = projectedNumber(run.start, 'font range start', 0, 383)
    const end = projectedNumber(run.end, 'font range end', 1, 384)
    if (!Number.isInteger(start) || !Number.isInteger(end) || end <= start) throw new Error('Invalid subtitle font range')
    return { start, end, fontProfile: savedSubtitleFont(text(run.fontProfile, 'font range profile')) }
  })
  let selectedTextFade: SubtitleCue['selectedTextFade']
  if (motion.selectedTextFade != null) {
    const fade = object(motion.selectedTextFade, 'selected text fade')
    const from = projectedNumber(fade.start, 'fade selection start', 0, 383)
    const to = projectedNumber(fade.end, 'fade selection end', 1, 384)
    const time = parseRational(text(fade.time, 'fade start'))
    const length = parseRational(text(fade.duration, 'fade duration'))
    if (!Number.isInteger(from) || !Number.isInteger(to) || to <= from || !time || time.numerator < 0n
      || !length || length.numerator <= 0n || compare(add(time, length), duration) > 0) {
      throw new Error('Invalid selected text fade')
    }
    selectedTextFade = { start: from, end: to, time: formatRational(time), duration: formatRational(length) }
  }
  let decoration: SubtitleCue['decoration']
  if (motion.decoration != null) {
    const config = object(motion.decoration, 'subtitle decoration')
    const begin = parseRational(text(config.start, 'decoration start'))
    const finish = parseRational(text(config.end, 'decoration end'))
    if (!subtitleDecorationId(config.assetId) || config.placement !== 'right-of-cue-v1' || typeof config.enabled !== 'boolean'
        || !begin || !finish || begin.numerator < 0n || compare(begin, finish) >= 0 || compare(finish, duration) > 0) {
      throw new Error('Invalid subtitle decoration')
    }
    decoration = { assetId: config.assetId, placement: config.placement, enabled: config.enabled,
      start: formatRational(begin), end: formatRational(finish) }
  }
  const keys = entries(motion.keys, 'subtitle keys').map(row => {
    const k = object(row, 'subtitle key')
    const time = parseRational(text(k.time, 'subtitle cue-local key time'))
    if (!time || time.numerator < 0n || compare(time, duration) >= 0) throw new Error('Invalid subtitle key time')
    const xMicros = projectedNumber(k.xMicros, 'subtitle X', -16000000, 16000000)
    const yMicros = projectedNumber(k.yMicros, 'subtitle Y', -16000000, 16000000)
    const opacityMicros = projectedNumber(k.opacityMicros, 'subtitle opacity', 0, 1000000)
    if (![xMicros,yMicros,opacityMicros].every(Number.isInteger)) throw new Error('Subtitle key values must be integral')
    return { time: formatRational(time), xMicros, yMicros, opacityMicros }
  })
  if (keys.length === 1 || keys.length > 16 ||
      keys.some((k,i) => i > 0 && compare(parseRational(keys[i-1].time)!, parseRational(k.time)!) >= 0))
    throw new Error('Invalid subtitle motion')
  return { cueId: text(element.id, 'subtitle identity'), text: text(styled.content, 'subtitle text'),
    start: formatRational(start), end: formatRational(add(start, duration)), width: Number(width.numerator),
    fontSize: Number(size.numerator), fontProfile, keys, canonicalStyleRuns, hasPinnedTextRanges,
    ...(fontRuns.length ? { fontRuns } : {}), ...(selectedTextFade && { selectedTextFade }), ...(decoration && { decoration }) }
}

/** Reject unsupported semantics at the read-only projection boundary. */
export function projectTimeline(document: unknown): TimelineProjection {
  const root = object(document, 'Timeline snapshot')
  if (root.schemaVersion !== 'timeline-1.0') throw new Error('Unsupported Timeline schema version')
  const subtitles = entries(root.textElements ?? [], 'textElements').map(projectSubtitle)
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
        let clip = projectClip(rawClip, trackId)
        const channels = curves.filter(c => c.targetEntityId === clip.clipId &&
          (c.parameterPath === 'transform.positionXMicros' || c.parameterPath === 'transform.positionYMicros'))
        if (channels.length) {
          const x = channels.find(c => c.parameterPath === 'transform.positionXMicros')
          const y = channels.find(c => c.parameterPath === 'transform.positionYMicros')
          if (track.type !== 'video' || channels.length !== 2 || !x || !y ||
            channels.some(c => c.valueType !== 'float' || c.extrapolation !== 'HOLD'))
            throw new Error('Invalid paired position channels')
          const xKeys = entries(x.keyframes, 'X position keys'), yKeys = entries(y.keyframes, 'Y position keys')
          if (xKeys.length < 2 || xKeys.length > 16 || xKeys.length !== yKeys.length)
            throw new Error('Position channels require matching keys')
          const positionKeys = xKeys.map((raw, i) => {
            const a = object(raw, 'X position key'), b = object(yKeys[i], 'Y position key')
            const time = exactTime(a.time, 'position time')
            const xMicros = projectedNumber(a.value, 'X position', -16000000, 16000000)
            const yMicros = projectedNumber(b.value, 'Y position', -16000000, 16000000)
            if (a.interpolation !== 'LINEAR' || b.interpolation !== 'LINEAR'
              || compare(time, exactTime(b.time, 'position time')) !== 0
              || compare(time, clip.start) < 0 || compare(time, clip.end) >= 0
              || !Number.isInteger(xMicros) || !Number.isInteger(yMicros))
              throw new Error('Invalid position key')
            return { time, xMicros, yMicros }
          })
          if (positionKeys.some((key, i) => i > 0 && compare(positionKeys[i - 1].time, key.time) >= 0))
            throw new Error('Position key times must strictly increase')
          channels.forEach(c => consumed.add(c))
          clip = { ...clip, positionKeys }
        }
        const scales = curves.filter(c => c.targetEntityId === clip.clipId && c.parameterPath === 'transform.scaleMultiplierMicros')
        if (scales.length) {
          const channel = scales[0]
          const keys = entries(channel.keyframes, 'scale keys')
          if (track.type !== 'video' || scales.length !== 1 || channel.valueType !== 'float'
            || channel.extrapolation !== 'HOLD' || keys.length < 2 || keys.length > 16) {
            throw new Error('Invalid scale channel')
          }
          const limits = scaleMultiplierBounds(clip.transform)
          const scaleKeys = keys.map(raw => {
            const key = object(raw, 'scale key')
            const time = exactTime(key.time, 'scale time')
            const multiplierMicros = projectedNumber(key.value, 'scale multiplier', limits.min, limits.max)
            if (key.interpolation !== 'LINEAR' || !Number.isSafeInteger(multiplierMicros)
              || compare(time, clip.start) < 0 || compare(time, clip.end) >= 0) throw new Error('Invalid scale key')
            return { time, multiplierMicros }
          })
          if (scaleKeys.some((key, i) => i > 0 && compare(scaleKeys[i - 1].time, key.time) >= 0))
            throw new Error('Scale key times must strictly increase')
          consumed.add(channel)
          clip = { ...clip, scaleKeys }
        }
        const gains = curves.filter(c => c.targetEntityId === clip.clipId && c.parameterPath === 'audio.gainMultiplierMicros')
        if (gains.length) {
          const channel = gains[0]
          const keys = entries(channel.keyframes, 'gain keys')
          if (track.type !== 'audio' || gains.length !== 1 || channel.valueType !== 'float'
            || channel.extrapolation !== 'HOLD' || keys.length < 2 || keys.length > 16) {
            throw new Error('Invalid gain channel')
          }
          const gainKeys = keys.map(raw => {
            const key = object(raw, 'gain key')
            const time = exactTime(key.time, 'gain time')
            const multiplierMicros = projectedNumber(key.value, 'gain multiplier', 0, UNIT)
            if (key.interpolation !== 'LINEAR' || !Number.isSafeInteger(multiplierMicros)
              || compare(time, clip.start) < 0 || compare(time, clip.end) >= 0) throw new Error('Invalid gain key')
            return { time, multiplierMicros }
          })
          if (gainKeys.some((key, i) => i > 0 && compare(gainKeys[i - 1].time, key.time) >= 0))
            throw new Error('Gain key times must strictly increase')
          consumed.add(channel)
          clip = { ...clip, gainKeys }
        }
        const effects = entries(object(rawClip, 'clip').effects ?? [], 'effects').map(e => object(e, 'effect'))
        const edge = (key: string): Rational => {
          const found = effects.filter(effect => effect.effectKey === key)
          if (!found.length) return ZERO
          const parameters = object(found[0].parameters, 'audio fade parameters')
          const duration = exactTime(parameters.duration, 'audio fade duration')
          if (track.type !== 'audio' || found.length !== 1 || Object.keys(parameters).length !== 1
            || duration.numerator === 0n || formatRational(duration) !== parameters.duration)
            throw new Error('Invalid audio fade')
          return duration
        }
        const fadeIn = edge('audio.fade_in'), fadeOut = edge('audio.fade_out')
        if (fadeIn.numerator || fadeOut.numerator) {
          if (compare(add(fadeIn, fadeOut), subtract(clip.end, clip.start)) > 0)
            throw new Error('Audio fade durations exceed the clip interval')
          clip = { ...clip, audioFades: { fadeIn, fadeOut } }
        }
        const bound = curves.filter(c => !consumed.has(c) && effects.some(e => e.id != null && e.id === c.targetEntityId))
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
    const audioCrossfade = t.transitionDefinitionId === 'audio.crossfade'
    const supported = audioCrossfade ? t.mediaType === 'AUDIO' && outgoingHandle
      : t.transitionDefinitionId === 'video.cross-dissolve' && t.mediaType === 'VIDEO'
        && (outgoingHandle || (t.alignment === 'START_AT_INCOMING' && t.temporalPolicy === 'OVERLAP_TIMELINE'))
    if (outgoingClipId === incomingClipId || duration.numerator <= 0n || incoming.has(incomingClipId)
        || !clips.some(clip => clip.clipId === incomingClipId) || !clips.some(clip => clip.clipId === outgoingClipId)
        || !supported || t.transitionDefinitionVersion !== '1.0'
        || object(t.parameters, 'transition parameters').curve !== 'LINEAR') {
      throw new Error('Unsupported Timeline transition')
    }
    incoming.add(incomingClipId)
    return { transitionId, outgoingClipId, incomingClipId, duration, ...(outgoingHandle ? { outgoingHandle } : {}), ...(audioCrossfade ? { audioCrossfade: true as const } : {}) }
  })
  return { compositionAspectRatio: aspect, tracks, clips, audioRoutes, transitions, ...(subtitles.length ? { subtitles } : {}) }
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
