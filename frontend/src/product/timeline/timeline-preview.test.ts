import { describe, expect, it } from 'vitest'
import { activeClipAt, clampTimelineTime, sourceTimeAt, timelineDuration, clockCursor, decimalCursor, scaleAt } from './timeline-preview'
import { decorationVisible, type SubtitleCue } from './subtitle-cues'
import { projectTimeline } from './timeline-projection'
import { compare, formatRational, parseRational } from './trim'

const projection = projectTimeline({ schemaVersion: 'timeline-1.0', tracks: [{ trackId: 't', type: 'video', clips: [
  { clipId: 'a', sourceKind: 'MEDIA_STREAM', artifactId: 'artifact', mediaStreamId: 'stream', contentDigest: 'a'.repeat(64), startTime: '0', endTime: '2', trimStart: '4', trimEnd: '6', temporalMapping: { kind: 'CONSTANT_RATE', rate: { numerator: 1, denominator: 1 }, direction: 'FORWARD' } },
  { clipId: 'b', sourceKind: 'MEDIA_STREAM', artifactId: 'artifact', mediaStreamId: 'stream', contentDigest: 'a'.repeat(64), startTime: '3', endTime: '5', trimStart: '0', trimEnd: '4', temporalMapping: { kind: 'CONSTANT_RATE', rate: { numerator: 2, denominator: 1 }, direction: 'FORWARD' } },
  { clipId: 'r', sourceKind: 'MEDIA_STREAM', artifactId: 'artifact', mediaStreamId: 'stream', contentDigest: 'a'.repeat(64), startTime: '6', endTime: '7', trimStart: '0', trimEnd: '2', temporalMapping: { kind: 'CONSTANT_RATE', rate: { numerator: 1, denominator: 1 }, direction: 'REVERSE' } },
] }] })
const t = (v: string) => parseRational(v)!

describe('timeline media preview time mapping', () => {
  it('selects clips and gaps by Timeline time', () => {
    expect(activeClipAt(projection, t('1/2'))?.clipId).toBe('a')
    expect(activeClipAt(projection, t('2'))).toBeNull()
    expect(activeClipAt(projection, t('5/2'))).toBeNull()
    expect(activeClipAt(projection, t('7/2'))?.clipId).toBe('b')
  })

  it('uses canonical layer order and falls back at the upper clip end, ignoring audio for pixels', () => {
    const clip = (clipId: string, startTime: string, endTime: string) => ({
      clipId, startTime, endTime, trimStart: '0', trimEnd: '2',
      sourceKind: 'MEDIA_STREAM', artifactId: 'artifact', mediaStreamId: 'stream', contentDigest: 'a'.repeat(64),
      temporalMapping: { kind: 'CONSTANT_RATE', rate: { numerator: 1, denominator: 1 }, direction: 'FORWARD' },
    })
    const tracks = projectTimeline({ schemaVersion: 'timeline-1.0', tracks: [
      { trackId: 'lower', type: 'video', clips: [clip('under', '0', '3')] },
      { trackId: 'upper', type: 'video', clips: [clip('over', '1', '2')] },
      { trackId: 'music', type: 'audio', clips: [clip('sound', '0', '4')] },
    ] })
    expect(activeClipAt(tracks, t('0'))?.clipId).toBe('under')
    expect(activeClipAt(tracks, t('1'))?.clipId).toBe('over')
    expect(activeClipAt(tracks, t('2'))?.clipId).toBe('under')
    expect(activeClipAt(tracks, t('3'))).toBeNull()
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

const cue: SubtitleCue = { cueId: 'c', text: 'Fractional', start: '0/1', end: '1/1', keys: [], width: 200, fontSize: 24,
  decoration: { assetId: 'sunflower-v1', placement: 'right-of-cue-v1', enabled: true, start: '1/24', end: '1/12' } }

describe('exact preview cursor for a fractional decoration interval', () => {
  it('uses the frame identity when a frame time is provided', () => {
    expect([0, 1, 2, 3].map(frame => decorationVisible(cue, parseRational(frame + '/24')!)))
      .toEqual([false, true, false, false])
  })

  it('keeps continuous range values on their actual side of either cut', () => {
    expect(['0.0416666', '0.0416668', '0.0833332', '0.0833334']
      .map(position => decorationVisible(cue, decimalCursor(position)!))).toEqual([false, true, true, false])
    expect(decimalCursor('4.16668e-2')).toEqual(decimalCursor('0.0416668'))
  })

  it('does not round the playback double to a frame or a microsecond', () => {
    expect(compare(clockCursor(1 / 24), parseRational('1/24')!)).toBeLessThan(0)
    expect(compare(clockCursor(1 / 12), parseRational('1/12')!)).toBeLessThan(0)
    expect([0.0416666, 0.0416668, 0.0833332, 0.0833334]
      .map(position => decorationVisible(cue, clockCursor(position)))).toEqual([false, true, true, false])
  })
})

it('interpolates a uniform scale curve exactly at its rational keys and holds its ends', () => {
  const keys = [['3/2', 1_000_000], ['73/24', 1_500_000], ['9/2', 1_000_000]] as const
  const scale = keys.map(([time, multiplierMicros]) => ({ time: parseRational(time)!, multiplierMicros }))
  expect(['1/1','3/2','109/48','73/24','9/2','11/2'].map(t => scaleAt(scale, parseRational(t)!)))
    .toEqual([1,1,1.25,1.5,1,1])
  expect(scaleAt([], parseRational('73/24')!)).toBe(1)
})
