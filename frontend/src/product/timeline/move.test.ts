import { describe, expect, it } from 'vitest'
import { describeMove, draggedStart, moveDeltaFromPixels, projectMove } from './move'
import { formatRational, rational, toSeconds, type ClipGeometry } from './trim'

const clip: ClipGeometry = {
  clipId: 'clip-0', trackId: 'track-1',
  start: rational(2n, 1n), end: rational(4n, 1n),
  sourceStart: rational(0n, 1n), sourceEnd: rational(2n, 1n),
  rate: rational(1n, 1n),
}

describe('non-ripple move projection', () => {
  it('keeps the duration and source range and only shifts the timeline placement', () => {
    const projection = projectMove(clip, rational(6n, 1n))
    expect(projection.valid).toBe(true)
    expect(formatRational(projection.start)).toBe('6/1')
    expect(formatRational(projection.end)).toBe('8/1')
    expect(toSeconds(projection.end) - toSeconds(projection.start)).toBe(2)
  })

  it('moves a clip to the timeline origin', () => {
    const projection = projectMove(clip, rational(0n, 1n))
    expect(projection.valid).toBe(true)
    expect(formatRational(projection.start)).toBe('0/1')
    expect(formatRational(projection.end)).toBe('2/1')
    expect(describeMove(projection)).toContain('0.00s → 2.00s')
  })

  it('rejects a negative target start', () => {
    const projection = projectMove(clip, rational(-1n, 2n))
    expect(projection.valid).toBe(false)
    expect(projection.reason).toMatch(/before the timeline origin/)
  })

  it('rejects a clip that has no positive duration', () => {
    const empty = { ...clip, end: rational(2n, 1n) }
    expect(projectMove(empty, rational(4n, 1n)).valid).toBe(false)
  })
})

describe('drag coordinates', () => {
  it('snaps the drag delta to 1/100 s and clamps the absolute start at zero', () => {
    expect(formatRational(moveDeltaFromPixels(160, 80))).toBe('2/1')
    expect(formatRational(draggedStart(clip, 160, 80))).toBe('4/1')
    expect(formatRational(draggedStart(clip, -400, 80))).toBe('0/1')
  })
})
