import { expect, it } from 'vitest'
import { editRangeFonts, setRangeFont, subtitleSelection } from './subtitle-font-runs'
it('maps textarea selection through CRLF, supplementary characters and combining sequences', () => {
  const text = '甲\r\n🌻️e\u0301中👩‍🚀'
  expect(subtitleSelection(text, 2, 5)).toEqual({ start: 3, end: 5 })
  expect(subtitleSelection(text, 5, 7)).toEqual({ start: 5, end: 7 })
  expect(subtitleSelection(text, 5, 6)).toBeNull()
  expect(subtitleSelection(text, 8, 10)).toBeNull()
})
it('preserves surrounding overrides and gives inserted text the inherited font', () => {
  const runs = [{ start: 1, end: 4, fontProfile: 'serif-v1' }]
  expect(editRangeFonts('甲abc🌻', '甲a中c🌻', runs)).toEqual([
    { start: 1, end: 2, fontProfile: 'serif-v1' }, { start: 3, end: 4, fontProfile: 'serif-v1' },
  ])
  expect(setRangeFont(runs, { start: 1, end: 4 }, '')).toEqual([])
})
