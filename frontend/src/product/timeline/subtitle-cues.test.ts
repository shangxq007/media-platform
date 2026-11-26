import { describe, expect, it } from 'vitest'
import { subtitlePose, type SubtitleCue } from './subtitle-cues'
import { parseRational } from './trim'

const cue: SubtitleCue = {
  cueId: 'spring', text: '春天来了 🌻\uFE0F\nSpring is here', start: '1/3', end: '10/3',
  fontProfile: 'noto-sc-color-emoji-v1', width: 200, fontSize: 24,
  keys: [{ time: '1/3', xMicros: -100000, yMicros: 400000, opacityMicros: 1000000 },
    { time: '7/3', xMicros: 300000, yMicros: 600000, opacityMicros: 600000 }],
}

describe('whole subtitle pose', () => {
  it('uses one half-open visibility window, cue-local interpolation and endpoint holds', () => {
    const at = (time: string) => subtitlePose(cue, parseRational(time)!)
    expect(at('8/25')).toBeNull()
    expect(at('1/3')).toEqual({ x: -0.1, y: 0.4, alpha: 1 })
    expect(at('2/3')).toEqual({ x: -0.1, y: 0.4, alpha: 1 })
    expect(at('5/3')).toEqual({ x: 0.1, y: 0.5, alpha: 0.8 })
    expect(at('8/3')).toEqual({ x: 0.3, y: 0.6, alpha: 0.6 })
    expect(at('3')).toEqual({ x: 0.3, y: 0.6, alpha: 0.6 })
    expect(at('10/3')).toBeNull()
    expect(subtitlePose({ ...cue, keys: [] }, parseRational('1/3')!)).toEqual({ x: 0.1, y: 0.55, alpha: 1 })
  })
})
