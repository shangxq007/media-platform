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
