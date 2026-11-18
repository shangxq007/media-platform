/**
 * Read-only Timeline media-preview helpers. Preview is a derived view of the canonical snapshot;
 * this module never mutates the Timeline or performs a write.
 */
import { compare, multiply, subtract, type Rational } from './trim'
import type { ProjectedClip, TimelineProjection } from './timeline-projection'

export function timelineDuration(projection: TimelineProjection): Rational {
  let end: Rational = { numerator: 0n, denominator: 1n }
  for (const clip of projection.clips) if (compare(clip.end, end) > 0) end = clip.end
  return end
}

/** The clip covering a Timeline time, or null for a gap. Clips are non-overlapping per track. */
export function activeClipAt(projection: TimelineProjection, timelineTime: Rational): ProjectedClip | null {
  let active: ProjectedClip | null = null
  for (const clip of projection.clips) {
    if (compare(timelineTime, clip.start) >= 0 && compare(timelineTime, clip.end) < 0) active = clip
  }
  return active
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
