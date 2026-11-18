/**
 * Read-only projection of a canonical Timeline snapshot.
 *
 * The projection is derived from the authoritative snapshot on every HEAD refresh and is never a
 * second persistent Timeline authority: it holds no draft, performs no writes and can be rebuilt
 * from the same snapshot bytes at any time.
 */
import { add, compare, parseRational, type Rational } from './trim'

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
}

export interface ProjectedTrack {
  readonly trackId: string
  readonly name: string
  readonly type: string
  readonly clips: readonly ProjectedClip[]
}

export interface TimelineProjection {
  readonly tracks: readonly ProjectedTrack[]
  readonly clips: readonly ProjectedClip[]
}

const IDENTITY: Rational = { numerator: 1n, denominator: 1n }

function safeRational(value: unknown): Rational | null {
  if (typeof value !== 'string') return null
  return parseRational(value)
}

/** Projects a parsed canonical Timeline document; malformed clips are omitted, never invented. */
export function projectTimeline(document: unknown): TimelineProjection {
  const rawTracks = (document as { tracks?: unknown } | null)?.tracks
  const tracks: ProjectedTrack[] = []
  if (!Array.isArray(rawTracks)) return { tracks, clips: [] }
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
      })
    }
    tracks.push({
      trackId,
      name: typeof track.name === 'string' ? track.name : trackId,
      type: typeof track.type === 'string' ? track.type.toUpperCase() : 'VIDEO',
      clips,
    })
  }
  return { tracks, clips: tracks.flatMap(track => track.clips) }
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
