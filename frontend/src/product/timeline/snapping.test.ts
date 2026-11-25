import { describe, expect, it } from 'vitest'
import { formatRational, parseRational, type Rational } from './trim'
import { movePlacement, nearestSnap, playheadTime, snapTargets, type SnapClip } from './snapping'

const time = (s: string): Rational => parseRational(s)!
const clip = (clipId: string, start: string, end: string, trackId = 'edited'): SnapClip =>
  ({ clipId, start: time(start), end: time(end), trackId })
const edge = (label: string, at: string) => ({ label, time: time(at) })

describe('editor snapping', () => {
  it('uses an inclusive eight screen pixels at the actual scale', () => {
    expect(nearestSnap([edge('start', '0')], [edge('target', '1')], 72, 80)?.target.label).toBe('target')
    expect(nearestSnap([edge('start', '0')], [edge('target', '1')], 71.9, 80)).toBeUndefined()
    expect(nearestSnap([edge('start', '0')], [edge('target', '1')], 72, 90)).toBeUndefined()
  })

  it('breaks equal distances by playhead, earlier target, then leading edge', () => {
    const start = edge('start', '0'), end = edge('end', '2')
    const targets = [edge('late', '11/10'), edge('early', '9/10')]
    expect(nearestSnap([start, end], targets, 80, 80)?.target.label).toBe('early')
    expect(nearestSnap([start, end], [{ ...targets[0], playhead: true }, targets[1]], 80, 80)?.target.label).toBe('late')
    expect(nearestSnap([start, end], [...targets, edge('right', '3'), edge('left', '1')], 80, 80)?.moving.label).toBe('start')
  })

  it('excludes all moving clips and other tracks, keeps a single exact common delta', () => {
    const l = clip('A', '0', '1'), r = clip('B', '2', '3'), c = clip('C', '0', '61/30')
    const other = clip('D', '1', '2', 'other')
    expect(snapTargets([l, r, c, other], 'edited', new Set(['A','B']), null).map(t => t.label)).toEqual(['C start','C end'])
    expect(movePlacement([l, r], [l, r, other], 6, 80, true, null).match).toBeUndefined()
    const result = movePlacement([l, r], [l, r, c], 160, 80, true, null)
    expect(result.match?.moving.label).toBe('A start')
    expect(result.match?.target.label).toBe('C end')
    expect(formatRational(result.delta)).toBe('61/30')
    expect(formatRational(movePlacement([l, r], [l, r, c], 160, 80, false, null).delta)).toBe('2/1')
  })

  it('keeps an endpoint off the microsecond grid exact without snapping nearby playhead values to it', () => {
    const exact = time('61/30')
    expect(playheadTime(61/30, exact)).toBe(exact)
    expect(playheadTime(2.0333, exact)).not.toEqual(exact)
  })
})
