import { describe, expect, it } from 'vitest'
import { decorationVisible, subtitlePose, type SubtitleCue } from './subtitle-cues'
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

it('opens and closes the independent decoration interval at exact cue-relative frame boundaries', () => {
  const cue: SubtitleCue = { cueId: 'cue', text: 'FadeWord', start: '1/1', end: '5/1', width: 240, fontSize: 24, keys: [],
    fontRuns: [{ start: 0, end: 4, fontProfile: 'noto-serif-sc-color-emoji-v1' }],
    selectedTextFade: { start: 0, end: 8, time: '1/1', duration: '1/1' },
    decoration: { assetId: 'sunflower-v1', placement: 'right-of-cue-v1', enabled: true, start: '1/1', end: '2/1' } }
  expect([47, 48, 60, 71, 72].map(frame => decorationVisible(cue, parseRational(`${frame}/24`)!)))
    .toEqual([false, true, true, true, false])
  expect(decorationVisible({ ...cue, decoration: { ...cue.decoration!, enabled: false } }, parseRational('5/2')!)).toBe(false)
})
