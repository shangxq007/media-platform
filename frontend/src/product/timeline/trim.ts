/**
 * Exact rational time arithmetic and non-destructive trim projection for the NLE clip editor.
 *
 * The canonical Operation authority (`OperationPlanner.planTrim`) defines the semantics this file
 * mirrors for local feedback only: a TRIM_CLIP moves ONE edge by a positive exact rational delta.
 *   - START: the clip's timeline start advances, the source in-point advances by delta x rate,
 *            and the timeline end / source out-point stay unchanged.
 *   - END:   the clip's timeline end retreats, the source out-point retreats by delta x rate,
 *            and the timeline start / source in-point stay unchanged.
 * Nothing here writes state: the server evaluates the same command in the preview and apply calls.
 */

export interface Rational {
  numerator: bigint
  denominator: bigint
}

export type TrimEdge = 'START' | 'END'

export interface ClipGeometry {
  clipId: string
  trackId: string
  start: Rational
  end: Rational
  sourceStart: Rational
  sourceEnd: Rational
  rate: Rational
}

export interface TrimProjection {
  edge: TrimEdge
  delta: Rational
  timelineStart: Rational
  timelineEnd: Rational
  sourceStart: Rational
  sourceEnd: Rational
  duration: Rational
  valid: boolean
  reason?: string
}

/** Minimum visible duration the editor will let a single drag create. */
export const MIN_VISIBLE_DURATION: Rational = { numerator: 1n, denominator: 100n }

function absolute(value: bigint): bigint {
  return value < 0n ? -value : value
}

function gcd(left: bigint, right: bigint): bigint {
  let a = absolute(left)
  let b = absolute(right)
  while (b !== 0n) {
    const remainder = a % b
    a = b
    b = remainder
  }
  return a === 0n ? 1n : a
}

export function rational(numerator: bigint, denominator: bigint): Rational {
  if (denominator === 0n) throw new Error('rational denominator must not be zero')
  const sign = denominator < 0n ? -1n : 1n
  const divisor = gcd(numerator, denominator)
  return { numerator: (sign * numerator) / divisor, denominator: (sign * denominator) / divisor }
}

/** Accepts the canonical exact forms the Operation transport accepts: "n", "-n", "n/d", "+n/d". */
export function parseRational(value: string | null | undefined): Rational | null {
  if (typeof value !== 'string') return null
  const match = /^([+-]?\d+)(?:\/([1-9]\d*))?$/.exec(value.trim())
  if (!match) return null
  return rational(BigInt(match[1]), match[2] ? BigInt(match[2]) : 1n)
}

export function formatRational(value: Rational): string {
  return `${value.numerator}/${value.denominator}`
}

export function add(left: Rational, right: Rational): Rational {
  return rational(left.numerator * right.denominator + right.numerator * left.denominator,
    left.denominator * right.denominator)
}

export function subtract(left: Rational, right: Rational): Rational {
  return rational(left.numerator * right.denominator - right.numerator * left.denominator,
    left.denominator * right.denominator)
}

export function multiply(left: Rational, right: Rational): Rational {
  return rational(left.numerator * right.numerator, left.denominator * right.denominator)
}

export function compare(left: Rational, right: Rational): number {
  const a = left.numerator * right.denominator
  const b = right.numerator * left.denominator
  return a < b ? -1 : a > b ? 1 : 0
}

export function toSeconds(value: Rational): number {
  return Number(value.numerator) / Number(value.denominator)
}

export const ZERO: Rational = { numerator: 0n, denominator: 1n }

/** Largest delta a single-edge trim may take while leaving a positive media range on both sides. */
export function maximumTrim(clip: ClipGeometry): Rational {
  const visible = subtract(clip.end, clip.start)
  const source = subtract(clip.sourceEnd, clip.sourceStart)
  const usable = compare(visible, source) <= 0 ? visible : source
  const maximum = subtract(usable, MIN_VISIBLE_DURATION)
  return compare(maximum, ZERO) <= 0 ? ZERO : maximum
}

/**
 * Projects the clip after trimming `edge` by `delta`. Returns an explicit invalid projection instead
 * of silently clamping, so the editor can show the exact rejection the server would return.
 */
export function projectTrim(clip: ClipGeometry, edge: TrimEdge, delta: Rational): TrimProjection {
  const visible = subtract(clip.end, clip.start)
  const sourceRange = subtract(clip.sourceEnd, clip.sourceStart)
  const sourceDelta = multiply(delta,
    // delta x rate: a 1x clip moves the source window by exactly the timeline delta.
    clip.rate)
  const timelineStart = edge === 'START' ? add(clip.start, delta) : clip.start
  const timelineEnd = edge === 'START' ? clip.end : subtract(clip.end, delta)
  const sourceStart = edge === 'START' ? add(clip.sourceStart, sourceDelta) : clip.sourceStart
  const sourceEnd = edge === 'START' ? clip.sourceEnd : subtract(clip.sourceEnd, sourceDelta)
  const duration = subtract(timelineEnd, timelineStart)
  const projection: TrimProjection = {
    edge, delta, timelineStart, timelineEnd, sourceStart, sourceEnd, duration, valid: true,
  }
  if (compare(delta, ZERO) <= 0) return { ...projection, valid: false, reason: 'Trim delta must be positive.' }
  if (compare(delta, visible) >= 0) {
    return { ...projection, valid: false, reason: 'Trim must leave a positive timeline duration.' }
  }
  if (compare(sourceDelta, ZERO) <= 0 || compare(sourceDelta, sourceRange) >= 0) {
    return { ...projection, valid: false, reason: 'Trim must leave a positive source media range.' }
  }
  if (compare(sourceStart, ZERO) < 0) {
    return { ...projection, valid: false, reason: 'Trim must not move the source in-point below zero.' }
  }
  if (compare(sourceStart, sourceEnd) >= 0) {
    return { ...projection, valid: false, reason: 'Trim must leave the source out-point after the in-point.' }
  }
  return projection
}

/** Pixels dragged at a fixed scale become an exact rational delta snapped to 1/100 second. */
export function pixelsToDelta(pixels: number, pixelsPerSecond: number): Rational {
  if (!Number.isFinite(pixels) || !Number.isFinite(pixelsPerSecond) || pixelsPerSecond <= 0) return ZERO
  const hundredths = BigInt(Math.max(0, Math.round((Math.abs(pixels) / pixelsPerSecond) * 100)))
  return rational(hundredths, 100n)
}

/** Clamp a dragged delta to what the clip can still give up, keeping the projection positive. */
export function clampTrimDelta(clip: ClipGeometry, delta: Rational): Rational {
  const maximum = maximumTrim(clip)
  return compare(delta, maximum) > 0 ? maximum : delta
}
