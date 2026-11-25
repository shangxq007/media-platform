import { moveDeltaFromPixels } from './move'
import { add, compare, formatRational, rational, subtract, toSeconds, type Rational } from './trim'

export const TIMELINE_PIXELS_PER_SECOND = 80
export const DRAG_THRESHOLD_PX = 4
export const SNAP_THRESHOLD_PX = 8

export interface SnapClip {
  clipId: string
  trackId: string
  start: Rational
  end: Rational
}

export interface SnapEdge { time: Rational; label: string }
export interface SnapTarget extends SnapEdge { playhead?: boolean }
export interface SnapMatch { moving: SnapEdge; target: SnapTarget; delta: Rational }

export function playheadTime(seconds: number, exactEnd: Rational | null): Rational | null {
  if (!Number.isFinite(seconds) || seconds < 0) return null
  if (exactEnd && toSeconds(exactEnd) === seconds) return exactEnd
  const micros = Math.round(seconds * 1_000_000)
  return Number.isSafeInteger(micros) ? rational(BigInt(micros), 1_000_000n) : null
}

export function snapTargets(clips: readonly SnapClip[], track: string, excluded: ReadonlySet<string>,
  playhead: Rational | null): SnapTarget[] {
  const targets: SnapTarget[] = playhead ? [{ time: playhead, label: 'playhead', playhead: true }] : []
  for (const clip of clips) {
    if (clip.trackId !== track || excluded.has(clip.clipId)) continue
    targets.push({ time: clip.start, label: clip.clipId + ' start' }, { time: clip.end, label: clip.clipId + ' end' })
  }
  return targets
}

/** Eight screen pixels inclusive, then playhead, earlier target, leading edge, and target label. */
export function nearestSnap(edges: readonly SnapEdge[], targets: readonly SnapTarget[],
  pixels: number, scale: number): SnapMatch | undefined {
  if (!Number.isFinite(pixels) || !Number.isFinite(scale) || scale <= 0) return undefined
  let best: (SnapMatch & { distance: number; order: number }) | undefined
  for (const [order, moving] of edges.entries()) {
    for (const target of targets) {
      const delta = subtract(target.time, moving.time)
      const distance = Math.abs(toSeconds(delta) * scale - pixels)
      if (!Number.isFinite(distance) || distance > SNAP_THRESHOLD_PX + 1e-9) continue
      const tie = best ? (Number(!!best.target.playhead) - Number(!!target.playhead)
        || compare(target.time, best.target.time) || order - best.order
        || (target.label < best.target.label ? -1 : target.label > best.target.label ? 1 : 0)) : 0
      if (!best || distance < best.distance - 1e-9 || (Math.abs(distance - best.distance) <= 1e-9 && tie < 0)) {
        best = { moving, target, delta, distance, order }
      }
    }
  }
  return best
}

export function movePlacement(selected: readonly SnapClip[], clips: readonly SnapClip[],
  pixels: number, scale: number, enabled: boolean, playhead: Rational | null) {
  const free = moveDeltaFromPixels(pixels, scale)
  if (!enabled || !selected.length) return { delta: free, match: undefined }
  const leading = selected.reduce((a, b) => compare(a.start, b.start) <= 0 ? a : b)
  const trailing = selected.reduce((a, b) => compare(a.end, b.end) >= 0 ? a : b)
  const match = nearestSnap([
    { time: leading.start, label: leading.clipId + ' start' },
    { time: trailing.end, label: trailing.clipId + ' end' },
  ], snapTargets(clips, leading.trackId, new Set(selected.map(c => c.clipId)), playhead), pixels, scale)
  return { delta: match?.delta ?? free, match }
}

export function snapDescription(match: SnapMatch) {
  return 'Snapped ' + match.moving.label + ' to ' + match.target.label + ' at ' + formatRational(match.target.time) + 's.'
}

export function shiftedRange(clip: SnapClip, delta: Rational) {
  return { start: add(clip.start, delta), end: add(clip.end, delta) }
}
