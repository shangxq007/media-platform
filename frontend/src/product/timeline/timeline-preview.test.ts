import { describe, expect, it } from 'vitest'
import { activeClipAt, clampTimelineTime, sourceTimeAt, timelineDuration } from './timeline-preview'
import { projectTimeline } from './timeline-projection'
import { formatRational, parseRational } from './trim'

const projection = projectTimeline({ tracks: [{ trackId: 't', type: 'VIDEO', clips: [
  { clipId: 'a', startTime: '0', endTime: '2', trimStart: '4', trimEnd: '6', temporalMapping: { rate: { numerator: 1, denominator: 1 }, direction: 'FORWARD' } },
  { clipId: 'b', startTime: '3', endTime: '5', trimStart: '0', trimEnd: '4', temporalMapping: { rate: { numerator: 2, denominator: 1 }, direction: 'FORWARD' } },
  { clipId: 'r', startTime: '6', endTime: '7', trimStart: '0', trimEnd: '2', temporalMapping: { rate: { numerator: 1, denominator: 1 }, direction: 'REVERSE' } },
] }] })
const t = (v: string) => parseRational(v)!

describe('timeline media preview time mapping', () => {
  it('selects clips and gaps by Timeline time', () => {
    expect(activeClipAt(projection, t('1/2'))?.clipId).toBe('a')
    expect(activeClipAt(projection, t('2'))).toBeNull()
    expect(activeClipAt(projection, t('5/2'))).toBeNull()
    expect(activeClipAt(projection, t('7/2'))?.clipId).toBe('b')
  })

  it('maps forward source time with rate and trim', () => {
    expect(formatRational(sourceTimeAt(projection.clips[0], t('1/2')))).toBe('9/2')
    expect(formatRational(sourceTimeAt(projection.clips[1], t('7/2')))).toBe('1/1')
  })

  it('maps reverse source time backwards from trim end', () => {
    expect(formatRational(sourceTimeAt(projection.clips[2], t('13/2')))).toBe('3/2')
  })

  it('reports duration and clamps seek time', () => {
    expect(formatRational(timelineDuration(projection))).toBe('7/1')
    expect(formatRational(clampTimelineTime(t('99'), t('7')))).toBe('7/1')
    expect(formatRational(clampTimelineTime(t('-1'), t('7')))).toBe('0/1')
  })
})
