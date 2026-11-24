import { DEFAULT_COMPOSITION_ASPECT, exactCompositionAspect } from '../../contracts/app/composition-aspect'
import { describe, expect, it } from 'vitest'
import { audioRouteFor, clipById, firstVideoTrack, projectTimeline, timelineEnd, videoTransformParameters,
  IDENTITY_VIDEO_TRANSFORM } from './timeline-projection'
import { formatRational, parseRational } from './trim'

const source = { sourceKind: 'MEDIA_STREAM', artifactId: 'artifact', mediaStreamId: 'stream', contentDigest: 'a'.repeat(64) }
const forward = { kind: 'CONSTANT_RATE', rate: { numerator: 1, denominator: 1 }, direction: 'FORWARD' }
const clip = { ...source, clipId: 'a', startTime: '0', endTime: '2/1', trimStart: '3', trimEnd: '5/1', temporalMapping: forward }
const track = { trackId: 'track-1', name: 'Video', type: 'video', clips: [clip] }
const document = {
  schemaVersion: 'timeline-1.0',
  tracks: [
    { ...track, clips: [clip, { ...source, clipId: 'b', startTime: '4/1', endTime: '7/1', trimStart: '0', trimEnd: '6',
      temporalMapping: { kind: 'CONSTANT_RATE', rate: { numerator: 2, denominator: 1 }, direction: 'REVERSE' } }] },
    { trackId: 'track-2', name: 'Audio', type: 'audio', clips: [] },
  ],
  audioMix: { routes: [
    { input: { trackId: 'track-1', clipId: 'a' }, gain: { linear: 0.5 }, mute: { muted: true }, balance: { value: -0.25 } },
  ] },
}

describe('canonical Timeline projection', () => {
  it('projects exact rational times and source traversal', () => {
    const projection = projectTimeline(document)
    expect(projection.tracks).toHaveLength(2)
    expect(projection.clips.map(item => item.clipId)).toEqual(['a', 'b'])
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

  it('rejects malformed clips and unsupported snapshots', () => {
    expect(() => projectTimeline({ ...document, tracks: [{ ...track, clips: [clip, { clipId: 'broken' }] }] })).toThrow()
    expect(() => projectTimeline(null)).toThrow()
    expect(() => projectTimeline({ ...document, tracks: 'nope' })).toThrow()
    expect(() => projectTimeline({ ...document, schemaVersion: 'timeline-unknown' })).toThrow()
  })

  it('selects video tracks only', () => {
    expect(firstVideoTrack(projectTimeline(document))?.trackId).toBe('track-1')
    expect(firstVideoTrack(projectTimeline({ ...document, audioMix: { routes: [] },
      tracks: [{ trackId: 'x', type: 'audio', clips: [] }] }))).toBeNull()
  })

  it('projects explicit audio route properties and rejects invalid gain', () => {
    expect(audioRouteFor(projectTimeline(document), 'track-1', 'a')).toEqual({
      trackId: 'track-1', clipId: 'a', gain: 0.5, mute: true, pan: -0.25,
    })
    expect(() => projectTimeline({ ...document, audioMix: { routes: [
      { ...document.audioMix.routes[0], gain: { linear: 9 } },
    ] } })).toThrow('Invalid audio route gain')
  })

  it('round trips fixed-point geometry and explicit portrait composition', () => {
    const transform = { ...IDENTITY_VIDEO_TRANSFORM, positionX: -0.105001, rotationDegrees: 45.000001 }
    const projected = projectTimeline({ ...document, metadata: { compositionAspectRatio: exactCompositionAspect(3, 4) },
      tracks: [{ ...track, clips: [{ ...clip, transform: videoTransformParameters(transform) }] }] })
    expect(projected.compositionAspectRatio).toEqual(exactCompositionAspect(3, 4))
    expect(clipById(projected, 'a')?.transform).toEqual(transform)
  })

  it('interprets omission only as the default and rejects malformed explicit ratios', () => {
    expect(projectTimeline(document).compositionAspectRatio).toEqual(DEFAULT_COMPOSITION_ASPECT)
    for (const value of [
      null, 'PORTRAIT_3_4', { ...exactCompositionAspect(3, 4), format: 'unsupported' },
      { ...exactCompositionAspect(3, 4), extra: 1 },
      { ...exactCompositionAspect(3, 4), numerator: 1.5 },
      { ...exactCompositionAspect(3, 4), numerator: 2_147_483_648 },
      { ...exactCompositionAspect(3, 4), numerator: 6, denominator: 8 },
    ]) {
      expect(() => projectTimeline({ ...document, metadata: { compositionAspectRatio: value } })).toThrow()
    }
  })

  it('rejects malformed authored transforms and unknown temporal mappings', () => {
    for (const changed of [
      { ...clip, transform: { positionX: '32' } },
      { ...clip, temporalMapping: { kind: 'UNSUPPORTED' } },
      { ...clip, effects: [{ effectKey: 'unknown' }] },
    ]) {
      expect(() => projectTimeline({ ...document, tracks: [{ ...track, clips: [changed] }] })).toThrow()
    }
  })
})
