/**
 * Read-only Timeline media-preview helpers. Preview is a derived view of the canonical snapshot;
 * this module never mutates the Timeline or performs a write.
 */
import { add, compare, multiply, rational, subtract, toSeconds, ZERO, type Rational } from './trim'
import type { ProjectedClip, ProjectedTransition, ProjectedVideoTransform, ScaleKey, TimelineProjection } from './timeline-projection'

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
    if (clip) {
      const blend = transitionForIncoming(projection, clip.clipId)
      if (blend?.outgoingHandle && compare(timelineTime, add(clip.start, blend.duration)) < 0) {
        const outgoing = track.clips.find(c => c.clipId === blend.outgoingClipId)
        if (outgoing) active.push(outgoing)
      }
      active.push(clip)
    }
  }
  return active
}

/** The canonical cross-dissolve whose incoming clip is this clip, if any. */
export function transitionForIncoming(projection: TimelineProjection, clipId: string): ProjectedTransition | null {
  const matches = projection.transitions.filter(transition => transition.incomingClipId === clipId)
  if (matches.length > 1) throw new Error('Conflicting incoming Timeline transitions')
  return matches[0] ?? null
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

/** Use the native control's actual decimal serialization rather than flooring a Number. */
export function decimalCursor(value: string): Rational | null {
  const match = /^(\d+)(?:\.(\d+))?(?:e([+-]?\d+))?$/i.exec(value)
  if (!match || value.length > 64 || Math.abs(Number(match[3] ?? 0)) > 324) return null
  const fraction = match[2] ?? ''
  const exponent = Number(match[3] ?? 0) - fraction.length
  const digits = BigInt(match[1] + fraction)
  return exponent < 0 ? rational(digits, 10n ** BigInt(-exponent))
    : { numerator: digits * 10n ** BigInt(exponent), denominator: 1n }
}

const binary = new DataView(new ArrayBuffer(8))

/** A continuous hardware clock produces doubles, not frame identities. Preserve its exact side of a cut. */
export function clockCursor(seconds: number): Rational {
  if (seconds <= 0) return ZERO
  binary.setFloat64(0, seconds)
  const bits = binary.getBigUint64(0)
  const encodedExponent = Number(bits >> 52n) & 0x7ff
  const fraction = bits & ((1n << 52n) - 1n)
  const significand = encodedExponent ? (1n << 52n) + fraction : fraction
  const exponent = (encodedExponent || 1) - 1075
  return exponent < 0 ? rational(significand, 1n << BigInt(-exponent))
    : { numerator: significand << BigInt(exponent), denominator: 1n }
}

/** Evaluate the uniform multiplier at the same exact cursor used by the clip. */
export function scaleAt(keys: readonly ScaleKey[] | undefined, time: Rational): number {
  if (!keys?.length) return 1
  const index = keys.findIndex(key => compare(time, key.time) <= 0)
  const right = index < 0 ? keys[keys.length - 1] : keys[index]
  const left = index <= 0 ? right : keys[index - 1]
  const fraction = left === right ? 0 : toSeconds(subtract(time, left.time)) / toSeconds(subtract(right.time, left.time))
  return (left.multiplierMicros + fraction * (right.multiplierMicros - left.multiplierMicros)) / 1_000_000
}
