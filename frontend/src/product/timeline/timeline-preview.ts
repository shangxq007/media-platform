/**
 * Read-only Timeline media-preview helpers. Preview is a derived view of the canonical snapshot;
 * this module never mutates the Timeline or performs a write.
 */
import { compare, multiply, subtract, type Rational } from './trim'
import type { ProjectedClip, ProjectedVideoTransform, TimelineProjection } from './timeline-projection'

export function timelineDuration(projection: TimelineProjection): Rational {
  let end: Rational = { numerator: 0n, denominator: 1n }
  for (const clip of projection.clips) if (compare(clip.end, end) > 0) end = clip.end
  return end
}

/** All video clips covering a Timeline time, bottom-to-top in canonical layer order. */
export function activeClipsAt(projection: TimelineProjection, timelineTime: Rational): ProjectedClip[] {
  const active: ProjectedClip[] = []
  for (const track of projection.tracks) {
    if (track.type !== 'VIDEO') continue
    const clip = track.clips.find(item =>
      compare(timelineTime, item.start) >= 0 && compare(timelineTime, item.end) < 0)
    if (clip) active.push(clip)
  }
  return active
}

/** The topmost video clip covering a Timeline time, or null for a video gap. */
export function activeClipAt(projection: TimelineProjection, timelineTime: Rational): ProjectedClip | null {
  return activeClipsAt(projection, timelineTime).length ? activeClipsAt(projection, timelineTime)[activeClipsAt(projection, timelineTime).length - 1] : null
}

/**
 * Source-media time for a Timeline time. Mirrors the canonical constant-rate mapping:
 * FORWARD trimStart + offset*rate; REVERSE trimEnd - offset*rate.
 */
export function sourceTimeAt(clip: ProjectedClip, timelineTime: Rational): Rational {
  const offset = subtract(timelineTime, clip.start)
  const delta = multiply(offset, clip.rate)
  return clip.direction === 'REVERSE' ? subtract(clip.sourceEnd, delta) : {
    numerator: clip.sourceStart.numerator * delta.denominator + delta.numerator * clip.sourceStart.denominator,
    denominator: clip.sourceStart.denominator * delta.denominator,
  }
}

/** Clamp a requested Timeline time into [0, duration]. */
export function clampTimelineTime(time: Rational, duration: Rational): Rational {
  if (compare(time, { numerator: 0n, denominator: 1n }) < 0) return { numerator: 0n, denominator: 1n }
  return compare(time, duration) > 0 ? duration : time
}
