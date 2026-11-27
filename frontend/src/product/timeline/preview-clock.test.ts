import { describe, expect, it } from 'vitest'
import { clockCursor, decimalCursor } from './preview-clock'
import { decorationVisible, type SubtitleCue } from './subtitle-cues'
import { compare, parseRational } from './trim'

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
