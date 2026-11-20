import { describe, expect, it } from 'vitest'
import { audioRouteFor, clipById, firstVideoTrack, projectTimeline, timelineEnd } from './timeline-projection'
import { formatRational, parseRational } from './trim'

const document = {
  tracks: [
    { trackId: 'track-1', name: 'Video', type: 'VIDEO', clips: [
      { clipId: 'a', startTime: '0', endTime: '2/1', trimStart: '3', trimEnd: '5/1',
        temporalMapping: { rate: { numerator: 1, denominator: 1 }, direction: 'FORWARD' } },
      { clipId: 'b', startTime: '4/1', endTime: '7/1', trimStart: '0', trimEnd: '3',
        temporalMapping: { rate: { numerator: 2, denominator: 1 }, direction: 'REVERSE' } },
      { clipId: 'broken', startTime: 'x' },
    ] },
    { trackId: 'track-2', name: 'Audio', type: 'AUDIO', clips: [] },
  ],
  audioMix: { routes: [
    { input: { trackId: 'track-2', clipId: 'a' }, gain: { linear: 0.5 }, mute: { muted: true }, balance: { value: -0.25 } },
    { input: { trackId: 'track-1', clipId: 'a' }, gain: { linear: 9 }, balance: { value: 2 } },
  ] },
}

describe('canonical Timeline projection', () => {
  it('projects clips with exact rational times and mapping', () => {
    const projection = projectTimeline(document)
    expect(projection.tracks).toHaveLength(2)
    expect(projection.clips.map(clip => clip.clipId)).toEqual(['a', 'b'])
    const a = clipById(projection, 'a')!
    expect(formatRational(a.start)).toBe('0/1')
    expect(formatRational(a.end)).toBe('2/1')
    expect(formatRational(a.sourceStart)).toBe('3/1')
    expect(a.direction).toBe('FORWARD')
    const b = clipById(projection, 'b')!
    expect(b.rate).toEqual({ numerator: 2n, denominator: 1n })
    expect(b.direction).toBe('REVERSE')
    expect(timelineEnd(projection)).toEqual(parseRational('7')!)
  })

  it('omits malformed clips instead of inventing state', () => {
    const projection = projectTimeline(document)
    expect(clipById(projection, 'broken')).toBeNull()
  })

  it('selects the first video track and tolerates a non-video first track', () => {
    const projection = projectTimeline(document)
    expect(firstVideoTrack(projection)?.trackId).toBe('track-1')
    expect(firstVideoTrack(projectTimeline({ tracks: [{ trackId: 'x', type: 'AUDIO', clips: [] }] }))?.trackId).toBe('x')
  })

  it('projects canonical audio route gain, mute and pan with safe defaults', () => {
    const projection = projectTimeline(document)
    expect(audioRouteFor(projection, 'track-2', 'a')).toEqual({
      trackId: 'track-2', clipId: 'a', gain: 0.5, mute: true, pan: -0.25,
    })
    expect(audioRouteFor(projection, 'track-1', 'a')).toEqual({
      trackId: 'track-1', clipId: 'a', gain: 1, mute: false, pan: 0,
    })
    expect(audioRouteFor(projection, 'track-2', 'missing')).toBeNull()
  })

  it('returns an empty projection for malformed documents', () => {
    expect(projectTimeline(null).clips).toEqual([])
    expect(projectTimeline({ tracks: 'nope' }).tracks).toEqual([])
  })
})
