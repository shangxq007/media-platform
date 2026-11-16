import { describe, expect, it } from 'vitest'
import {
  clampTrimDelta, compare, formatRational, maximumTrim, parseRational, pixelsToDelta, projectTrim,
  rational, subtract, toSeconds, type ClipGeometry,
} from './trim'

const clip: ClipGeometry = {
  clipId: 'clip-0', trackId: 'track-1',
  start: rational(0n, 1n), end: rational(6n, 1n),
  sourceStart: rational(0n, 1n), sourceEnd: rational(6n, 1n),
  rate: rational(1n, 1n),
}

describe('exact rational arithmetic', () => {
  it('parses the canonical transport forms and rejects everything else', () => {
    expect(parseRational('0')).toEqual(rational(0n, 1n))
    expect(parseRational('5/2')).toEqual(rational(5n, 2n))
    expect(parseRational('+3/4')).toEqual(rational(3n, 4n))
    for (const invalid of ['', '1/0', '1.5', 'abc', '-1/4x', '1/']) expect(parseRational(invalid)).toBeNull()
  })

  it('formats exactly and reduces', () => {
    expect(formatRational(rational(5n, 2n))).toBe('5/2')
    expect(formatRational(rational(2n, 4n))).toBe('1/2')
    expect(toSeconds(rational(3n, 2n))).toBe(1.5)
  })
})

describe('left boundary trim (START)', () => {
  it('advances the timeline start and the source in-point while keeping end and out-point', () => {
    const projection = projectTrim(clip, 'START', rational(2n, 1n))
    expect(projection.valid).toBe(true)
    expect(formatRational(projection.timelineStart)).toBe('2/1')
    expect(formatRational(projection.timelineEnd)).toBe('6/1')
    expect(formatRational(projection.sourceStart)).toBe('2/1')
    expect(formatRational(projection.sourceEnd)).toBe('6/1')
    expect(formatRational(projection.duration)).toBe('4/1')
  })

  it('scales the source delta by the clip rate', () => {
    const fast = { ...clip, rate: rational(2n, 1n) }
    const projection = projectTrim(fast, 'START', rational(1n, 1n))
    expect(formatRational(projection.sourceStart)).toBe('2/1')
    expect(formatRational(projection.duration)).toBe('5/1')
  })
})

describe('right boundary trim (END)', () => {
  it('retreats the timeline end and the source out-point while keeping start and in-point', () => {
    const projection = projectTrim(clip, 'END', rational(2n, 1n))
    expect(projection.valid).toBe(true)
    expect(formatRational(projection.timelineStart)).toBe('0/1')
    expect(formatRational(projection.timelineEnd)).toBe('4/1')
    expect(formatRational(projection.sourceStart)).toBe('0/1')
    expect(formatRational(projection.sourceEnd)).toBe('4/1')
    expect(formatRational(projection.duration)).toBe('4/1')
  })
})

describe('rejections mirror the canonical Operation rules', () => {
  it('rejects a zero or negative delta', () => {
    for (const delta of [rational(0n, 1n), rational(-1n, 2n)]) {
      const projection = projectTrim(clip, 'END', delta)
      expect(projection.valid).toBe(false)
      expect(projection.reason).toMatch(/positive/)
    }
  })

  it('rejects a delta equal to the visible duration', () => {
    const projection = projectTrim(clip, 'END', rational(6n, 1n))
    expect(projection.valid).toBe(false)
    expect(projection.reason).toMatch(/positive timeline duration/)
  })

  it('rejects a trim that would drive the source in-point past the out-point', () => {
    const narrow = { ...clip, sourceStart: rational(0n, 1n), sourceEnd: rational(1n, 1n), end: rational(6n, 1n) }
    const projection = projectTrim(narrow, 'START', rational(2n, 1n))
    expect(projection.valid).toBe(false)
    expect(projection.reason).toMatch(/positive source media range/)
  })

  it('rejects a source range whose out-point would not follow the in-point', () => {
    const inverted = { ...clip, sourceEnd: rational(0n, 1n) }
    const projection = projectTrim(inverted, 'END', rational(1n, 1n))
    expect(projection.valid).toBe(false)
  })
})

describe('drag coordinate conversion', () => {
  it('converts pixels at the strip scale into an exact 1/100 second delta', () => {
    expect(formatRational(pixelsToDelta(160, 80))).toBe('2/1')
    expect(formatRational(pixelsToDelta(40, 80))).toBe('1/2')
    expect(formatRational(pixelsToDelta(1, 80))).toBe('1/100')
    expect(formatRational(pixelsToDelta(-80, 80))).toBe('1/1')
  })

  it('returns zero for degenerate scales instead of NaN', () => {
    expect(formatRational(pixelsToDelta(100, 0))).toBe('0/1')
    expect(formatRational(pixelsToDelta(Number.NaN, 80))).toBe('0/1')
  })

  it('clamps a drag to what the clip can still give up', () => {
    const maximum = maximumTrim(clip)
    expect(compare(maximum, rational(6n, 1n))).toBeLessThan(0)
    expect(formatRational(clampTrimDelta(clip, rational(99n, 1n)))).toBe(formatRational(maximum))
    expect(formatRational(clampTrimDelta(clip, rational(1n, 2n)))).toBe('1/2')
    expect(compare(subtract(clip.end, clip.start), rational(0n, 1n))).toBeGreaterThan(0)
  })
})
