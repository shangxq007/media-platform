import { expect, it } from 'vitest'
import { editRangeFonts, setRangeFont, subtitleSelection, textareaText } from './subtitle-font-runs'
it('maps textarea selection through CRLF, supplementary characters and combining sequences', () => {
  const text = '甲\r\n🌻️e\u0301中👩‍🚀'
  expect(subtitleSelection(text, 2, 5)).toEqual({ start: 3, end: 5 })
  expect(subtitleSelection(text, 5, 7)).toEqual({ start: 5, end: 7 })
  expect(subtitleSelection(text, 5, 6)).toBeNull()
  expect(subtitleSelection(text, 8, 10)).toBeNull()
})
it('preserves surrounding overrides and gives inserted text the inherited font', () => {
  const runs = [{ start: 1, end: 4, fontProfile: 'serif-v1' }]
  expect(editRangeFonts('甲abc🌻', '甲a中c🌻', runs).fontRuns).toEqual([
    { start: 1, end: 2, fontProfile: 'serif-v1' }, { start: 3, end: 4, fontProfile: 'serif-v1' },
  ])
  expect(setRangeFont(runs, { start: 1, end: 4 }, '')).toEqual([])
})

it('uses the textarea caret to distinguish identical neighboring characters', () => {
  expect(editRangeFonts('中中', '中中中', [{ start: 0, end: 1, fontProfile: 'serif-v1' }], 1).fontRuns)
    .toEqual([{ start: 1, end: 2, fontProfile: 'serif-v1' }])
})

it.each([
  { edit: 'inserting', authored: '甲\r乙', input: { start: 2, end: 2, inputType: 'insertLineBreak' }, after: '甲\n\n乙',
    runs: [{ start: 2, end: 3, fontProfile: 'serif-v1' }], text: '甲\r\n\n乙',
    expected: [{ start: 4, end: 5, fontProfile: 'serif-v1' }] },
  { edit: 'deleting', authored: '甲\rX\n乙', input: { start: 2, end: 3, inputType: 'deleteContentBackward' },
    after: '甲\n\n乙', runs: [{ start: 4, end: 5, fontProfile: 'serif-v1' }], text: '甲\r\n\n乙',
    expected: [{ start: 4, end: 5, fontProfile: 'serif-v1' }] },
  { edit: 'inserting after multiple line endings', authored: '甲\r\nA\r\r乙', input: { start: 5, end: 5, inputType: 'insertLineBreak' },
    after: '甲\nA\n\n\n乙', text: '甲\r\nA\r\r\n\n乙',
    runs: [{ start: 4, end: 7, fontProfile: 'serif-v1' }],
    expected: [{ start: 4, end: 7, fontProfile: 'serif-v1' }, { start: 8, end: 9, fontProfile: 'serif-v1' }] },
])('preserves separate line breaks when $edit at a CR/LF boundary', ({ authored, after, input, runs, text, expected }) => {
  const result = editRangeFonts(authored, after, runs, input.start, input)
  expect(textareaText(result.text)).toBe(after)
  expect(result).toEqual({ text, fontRuns: expected })
})
