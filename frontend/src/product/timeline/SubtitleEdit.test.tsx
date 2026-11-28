import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { expect, it, vi } from 'vitest'
import { FormalRenderAPI } from '../../api/formal-render'
import { SubtitleEdit } from './SubtitleEdit'
import { contentHash, projectId, revisionId, timelineId } from './types'

it('N18 exposes the saved font as a named selector and sends a scoped read-only draft', async () => {
  const hash = 'a'.repeat(64)
  const preview = vi.spyOn(FormalRenderAPI, 'previewBatch').mockResolvedValue({ batchDigest: hash, steps: [],
    resolvedSubtitleFont: { id: 'n18-fixture-serif-v1', label: 'Fixture Serif' } })
  const catalog = vi.spyOn(FormalRenderAPI, 'listSubtitleFonts')
    .mockRejectedValueOnce(new Error('offline')).mockResolvedValue([
      { id: 'noto-sc-color-emoji-v1', label: 'Sans', family: 'MP Cue Sans', style: 'Regular' },
      { id: 'n18-fixture-serif-v1', label: 'Fixture Serif', family: 'MP Cue Serif', style: 'Regular' },
    ])
  const apply = vi.spyOn(FormalRenderAPI, 'commitBatch')
  const onPreview = vi.fn()
  try {
    render(<SubtitleEdit head={{ projectId: projectId('project'), timelineId: timelineId('timeline'),
      revisionId: revisionId('rev'), contentHash: contentHash(btoa('a'.repeat(32))) }}
      tenant="tenant" disabled={false} access={{ blocked: false, canStart: () => true, claim: () => true, release: vi.fn() }}
      readback={vi.fn()} onAccepted={vi.fn()} onPreview={onPreview}
      cues={[{ cueId: 'legacy', text: '春天 🌻 Latin', width: 114, fontSize: 16, start: '0/1', end: '2/1', keys: [] }]} />)
    fireEvent.change(screen.getByRole('combobox', { name: /^Subtitle cue$/ }), { target: { value: 'legacy' } })
    const selector = screen.getByLabelText<HTMLSelectElement>('Subtitle font', { exact: true })
    expect(selector.value).toBe('noto-sc-color-emoji-v1')
    await screen.findByText('Subtitle font catalog unavailable. No font was substituted.')
    expect(selector.disabled).toBe(true)
    expect(screen.getByRole<HTMLButtonElement>('button', { name: /^Preview subtitle$/ }).disabled).toBe(true)
    expect(preview).not.toHaveBeenCalled()
    fireEvent.click(screen.getByRole('button', { name: /^Retry subtitle fonts$/ }))
    await screen.findByRole('option', { name: 'Fixture Serif' })
    expect(catalog).toHaveBeenCalledTimes(2)
    fireEvent.change(selector, { target: { value: 'n18-fixture-serif-v1' } })
    fireEvent.click(screen.getByRole('button', { name: /^Preview subtitle$/ }))
    await screen.findByRole('button', { name: /^Confirm subtitle$/ })
    const body = preview.mock.calls[0][2]
    expect(body.baseRevisionId).toBe('rev')
    expect(body.operations[0]).toMatchObject({ definitionId: 'timeline.subtitle.cue.set',
      parameters: { cueId: 'legacy', fontProfile: 'n18-fixture-serif-v1' } })
    expect(onPreview).toHaveBeenLastCalledWith(body)
    fireEvent.click(screen.getByRole('button', { name: /^Cancel subtitle$/ }))
    await waitFor(() => expect(onPreview).toHaveBeenLastCalledWith(null))
    expect(screen.queryByRole('button', { name: /^Confirm subtitle$/ })).toBeNull()
    expect(apply).not.toHaveBeenCalled()
  } finally {
    vi.restoreAllMocks()
  }
})

const serif = 'n18-fixture-serif-v1'

it('named subtitle editing keeps surviving inline and fade ranges and blocks a conflicting translation', async () => {
  const preview = vi.spyOn(FormalRenderAPI, 'previewBatch').mockResolvedValue({
    batchDigest: 'a'.repeat(64), steps: [],
    resolvedSubtitleFont: { id: 'noto-sc-color-emoji-v1', label: 'Sans' },
  })
  const write = vi.spyOn(FormalRenderAPI, 'commitBatch')
  try {
    render(<SubtitleEdit refId="alternate" head={{ projectId: projectId('project'), timelineId: timelineId('timeline'),
      revisionId: revisionId('rev'), contentHash: contentHash(btoa('a'.repeat(32))) }} tenant="tenant" disabled={false}
      access={{ blocked: false, canStart: () => true, claim: () => true, release: vi.fn() }}
      readback={vi.fn()} onAccepted={vi.fn()} onPreview={vi.fn()}
      cues={[{ cueId: 'one', text: 'OLD KEEP', width: 160, fontSize: 24, start: '0/1', end: '2/1', keys: [],
        fontRuns: [{ start: 4, end: 8, fontProfile: serif }],
        selectedTextFade: { start: 4, end: 8, time: '0/1', duration: '1/2' } }]} />)
    fireEvent.change(screen.getByLabelText('Subtitle cue'), { target: { value: 'one' } })
    const text = screen.getByLabelText('Subtitle text')
    const action = screen.getByRole<HTMLButtonElement>('button', { name: 'Preview subtitle' })
    fireEvent.change(text, { target: { value: 'NEW WORD' } })
    expect(screen.getByRole('alert').textContent).toContain('Keep that styled text intact')
    expect(action.disabled).toBe(true)
    expect(screen.queryByLabelText('Subtitle start (timeline seconds)')).toBeNull()
    expect(preview).not.toHaveBeenCalled()
    fireEvent.change(text, { target: { value: 'LONGER KEEP' } })
    fireEvent.click(action)
    await screen.findByRole('button', { name: 'Confirm subtitle' })
    expect(preview.mock.calls[0][2]).toMatchObject({ refId: 'alternate', baseRevisionId: 'rev',
      operations: [{ parameters: { cueId: 'one', text: 'LONGER KEEP', start: '0/1', end: '2/1',
        width: 160, keys: [], fontRuns: [{ start: 7, end: 11, fontProfile: serif }],
        selectedTextFade: { start: 7, end: 11, time: '0/1', duration: '1/2' } } }] })
    fireEvent.click(screen.getByRole('button', { name: 'Cancel subtitle' }))
    expect(write).not.toHaveBeenCalled()
  } finally {
    vi.restoreAllMocks()
  }
})

it.each([
  { name: 'types over the last letter', text: 'foo cat dog', run: [4, 7], selection: [6, 7], inputType: 'insertText',
    value: 'foo car dog', caret: 7, savedText: 'foo car dog', expected: [{ start: 4, end: 6, fontProfile: serif }] },
  { name: 'pastes over the whole word', text: 'foo cat dog', run: [4, 7], selection: [4, 7], inputType: 'insertFromPaste',
    value: 'foo car dog', caret: 7, savedText: 'foo car dog', expected: [] },
  { name: 'appends after a CRLF', text: '甲\r\nMiddleword', run: [3, 13], selection: [12, 12], inputType: 'insertText',
    value: '甲\nMiddleword!', caret: 13, savedText: '甲\r\nMiddleword!', expected: [{ start: 3, end: 13, fontProfile: serif }] },
  { name: 'deletes a repeated character before the styled occurrence', text: '中中中', run: [1, 2], selection: [1, 1],
    inputType: 'deleteContentBackward', value: '中中', caret: 0, savedText: '中中', expected: [{ start: 0, end: 1, fontProfile: serif }] },
  { name: 'inserts a repeated character before the styled occurrence', text: '中中', run: [0, 1], selection: [0, 0],
    inputType: 'insertText', value: '中中中', caret: 1, savedText: '中中中', expected: [{ start: 1, end: 2, fontProfile: serif }] },
  { name: 'styles neither a replacement nor the grapheme it joins', text: 'e🌻', run: [0, 2], selection: [1, 1],
    inputType: 'insertText', value: 'e\u0301🌻', caret: 2, savedText: 'e\u0301🌻', expected: [{ start: 2, end: 3, fontProfile: serif }] },
  { name: 'pastes identical text without inheriting the replaced style', text: 'foo cat dog', run: [4, 7], selection: [4, 7],
    inputType: 'insertFromPaste', value: 'foo cat dog', caret: 7, savedText: 'foo cat dog', expected: [] },
  { name: 'deletes a whole emoji before a CRLF and keeps the authored line break', text: '👩‍🚀甲\r\n中', run: [6, 7],
    selection: [0, 5], inputType: 'deleteContentBackward', value: '甲\n中', caret: 0, savedText: '甲\r\n中',
    expected: [{ start: 3, end: 4, fontProfile: serif }] },
  { name: 'discards an unused edit span at composition start', text: 'foo cat dog', run: [4, 7], selection: [4, 7],
    inputType: 'insertCompositionText', composing: true, value: 'foo car dog', caret: 7, savedText: 'foo car dog',
    expected: [{ start: 4, end: 6, fontProfile: serif }] },
])('N19 $name', async ({ text, run, selection, inputType, value, caret, savedText, expected, composing }) => {
  const preview = vi.spyOn(FormalRenderAPI, 'previewBatch').mockResolvedValue({ batchDigest: 'a'.repeat(64), steps: [],
    resolvedSubtitleFont: { id: 'noto-sc-color-emoji-v1', label: 'Sans' } })
  vi.spyOn(FormalRenderAPI, 'listSubtitleFonts').mockResolvedValue([
    { id: 'noto-sc-color-emoji-v1', label: 'Sans', family: 'MP Cue Sans', style: 'Regular' },
    { id: serif, label: 'Fixture Serif', family: 'MP Cue Serif', style: 'Regular' },
  ])
  try {
    render(<SubtitleEdit head={{ projectId: projectId('project'), timelineId: timelineId('timeline'),
      revisionId: revisionId('rev'), contentHash: contentHash(btoa('a'.repeat(32))) }} tenant="tenant" disabled={false}
      access={{ blocked: false, canStart: () => true, claim: () => true, release: vi.fn() }}
      readback={vi.fn()} onAccepted={vi.fn()} onPreview={vi.fn()}
      cues={[{ cueId: 'legacy', text, fontRuns: [{ start: run[0], end: run[1], fontProfile: serif }],
        width: 240, fontSize: 24, start: '0/1', end: '2/1', keys: [] }]} />)
    fireEvent.change(screen.getByLabelText('Subtitle cue'), { target: { value: 'legacy' } })
    await screen.findByRole('option', { name: 'Fixture Serif' })
    const textarea = screen.getByLabelText<HTMLTextAreaElement>('Subtitle text')
    textarea.setSelectionRange(selection[0], selection[1])
    fireEvent(textarea, new InputEvent('beforeinput', { inputType, bubbles: true, cancelable: true }))
    if (composing) {
      fireEvent.compositionStart(textarea)
      textarea.setSelectionRange(6, 7)
      fireEvent(textarea, new InputEvent('beforeinput', { inputType, bubbles: true, isComposing: true }))
    }
    fireEvent.input(textarea, { inputType, target: { value, selectionStart: caret, selectionEnd: caret } })
    if (composing) {
      fireEvent.compositionEnd(textarea)
      textarea.setSelectionRange(0, 7)
      fireEvent.input(textarea, { inputType, target: { value } })
    }
    fireEvent.click(screen.getByRole('button', { name: /^Preview subtitle$/ }))
    await screen.findByRole('button', { name: /^Confirm subtitle$/ })
    expect(preview.mock.calls[0][2].operations[0].parameters).toMatchObject({ text: savedText, fontRuns: expected })
  } finally {
    vi.restoreAllMocks()
  }
})
