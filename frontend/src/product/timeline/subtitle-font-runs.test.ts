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

it('remaps a selected-text fade without animating inserted or replaced graphemes', () => {
  const fade = { start: 1, end: 4, time: '1/1', duration: '1/1' }
  const edit = (after: string, start: number, end: number, inputType = 'insertText') =>
    editRangeFonts('甲cat乙', after, [], start + 1, { start, end, inputType }, fade).selectedTextFade
  expect(edit('甲Xcat乙', 1, 1)).toEqual({ ...fade, start: 2, end: 5 })
  expect(edit('甲catX乙', 4, 4)).toEqual(fade)
  expect(edit('甲car乙', 3, 4)).toEqual({ ...fade, end: 3 })
  expect(edit('甲car乙', 1, 4, 'insertFromPaste')).toBeUndefined()
  expect(edit('甲cXat乙', 2, 2)).toBeUndefined()
  expect(edit('甲ct乙', 2, 3, 'deleteContentBackward')).toEqual({ ...fade, end: 3 })
})

it('retains a fade on the original repeated word and maps emoji across CR/LF edits', () => {
  const repeated = { start: 0, end: 3, time: '0/1', duration: '1/2' }
  expect(editRangeFonts('catcat', 'catcatcat', [], 3, { start: 0, end: 0, inputType: 'insertFromPaste' }, repeated)
    .selectedTextFade).toEqual({ ...repeated, start: 3, end: 6 })
  const emoji = { ...repeated, start: 2, end: 4 }
  const edited = editRangeFonts('甲\r🌻️', '甲\n\n🌻️', [], 3, { start: 2, end: 2, inputType: 'insertLineBreak' }, emoji)
  expect(edited.text).toBe('甲\r\n\n🌻️')
  expect(edited.selectedTextFade).toEqual({ ...emoji, start: 4, end: 6 })
  expect(subtitleSelection(edited.text, 3, 5)).toBeNull()
})
