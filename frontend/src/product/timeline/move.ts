import { compare, formatRational, pixelsToDelta, rational, toSeconds, type ClipGeometry, type Rational } from './trim'

/** A projected non-ripple move of one clip to an absolute timeline start. */
export interface MoveProjection {
  readonly valid: boolean
  readonly start: Rational
  readonly end: Rational
  readonly reason?: string
}

/** Exact non-ripple move: keep duration and source range, only shift the timeline placement. */
export function projectMove(clip: ClipGeometry, targetStart: Rational): MoveProjection {
  const duration = {
    numerator: clip.end.numerator * clip.start.denominator - clip.start.numerator * clip.end.denominator,
    denominator: clip.start.denominator * clip.end.denominator,
  }
  if (compare(duration, rational(0n, 1n)) <= 0) {
    return { valid: false, start: clip.start, end: clip.end, reason: 'The clip has no positive duration and cannot be moved.' }
  }
  if (compare(targetStart, rational(0n, 1n)) < 0) {
    return { valid: false, start: clip.start, end: clip.end, reason: 'A clip cannot start before the timeline origin (0 s).' }
  }
  const end = {
    numerator: targetStart.numerator * duration.denominator + duration.numerator * targetStart.denominator,
    denominator: targetStart.denominator * duration.denominator,
  }
  return { valid: true, start: targetStart, end }
}

/** Signed drag delta at the strip scale, snapped to 1/100 s (pixelsToDelta is unsigned). */
export function moveDeltaFromPixels(pixels: number, pixelsPerSecond: number): Rational {
  const magnitude = pixelsToDelta(Math.abs(pixels), pixelsPerSecond)
  return pixels < 0
    ? { numerator: -magnitude.numerator, denominator: magnitude.denominator }
    : magnitude
}

/** Absolute target start after dragging from the clip's current start, clamped to the origin. */
export function draggedStart(clip: ClipGeometry, pixels: number, pixelsPerSecond: number): Rational {
  const delta = moveDeltaFromPixels(pixels, pixelsPerSecond)
  const target = {
    numerator: clip.start.numerator * delta.denominator + delta.numerator * clip.start.denominator,
    denominator: clip.start.denominator * delta.denominator,
  }
  return compare(target, rational(0n, 1n)) < 0 ? rational(0n, 1n) : target
}

export function describeMove(projection: MoveProjection): string {
  return `${toSeconds(projection.start).toFixed(2)}s → ${toSeconds(projection.end).toFixed(2)}s (start ${formatRational(projection.start)})`
}
