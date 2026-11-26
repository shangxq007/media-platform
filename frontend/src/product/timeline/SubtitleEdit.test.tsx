import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { expect, it, vi } from 'vitest'
import { FormalRenderAPI } from '../../api/formal-render'
import { SubtitleEdit } from './SubtitleEdit'
import { contentHash, projectId, revisionId, timelineId } from './types'

it('N18 exposes the saved font as a named selector and sends a scoped read-only draft', async () => {
  const hash = 'a'.repeat(64)
  const preview = vi.spyOn(FormalRenderAPI, 'previewBatch').mockResolvedValue({ batchDigest: hash, steps: [] })
  const apply = vi.spyOn(FormalRenderAPI, 'commitBatch')
  const onPreview = vi.fn()
  try {
    render(<SubtitleEdit head={{ projectId: projectId('project'), timelineId: timelineId('timeline'),
      revisionId: revisionId('rev'), contentHash: contentHash(btoa('a'.repeat(32))) }}
      tenant="tenant" disabled={false} access={{ blocked: false, canStart: () => true, claim: () => true, release: vi.fn() }}
      readback={vi.fn()} onAccepted={vi.fn()} onPreview={onPreview}
      cues={[{ cueId: 'legacy', text: '春天 🌻 Latin', width: 114, fontSize: 16, start: '0/1', end: '2/1', keys: [] }]} />)
    fireEvent.change(screen.getByRole('combobox', { name: 'Subtitle cue', exact: true }), { target: { value: 'legacy' } })
    const selector = screen.getByLabelText<HTMLSelectElement>('Subtitle font', { exact: true })
    expect(selector.value).toBe('noto-sc-color-emoji-v1')
    fireEvent.change(selector, { target: { value: 'noto-serif-sc-color-emoji-v1' } })
    fireEvent.click(screen.getByRole('button', { name: 'Preview subtitle', exact: true }))
    await screen.findByRole('button', { name: 'Confirm subtitle', exact: true })
    const body = preview.mock.calls[0][2]
    expect(body.baseRevisionId).toBe('rev')
    expect(body.operations[0]).toMatchObject({ definitionId: 'timeline.subtitle.cue.set',
      parameters: { cueId: 'legacy', fontProfile: 'noto-serif-sc-color-emoji-v1' } })
    expect(onPreview).toHaveBeenLastCalledWith(body)
    fireEvent.click(screen.getByRole('button', { name: 'Cancel subtitle', exact: true }))
    await waitFor(() => expect(onPreview).toHaveBeenLastCalledWith(null))
    expect(screen.queryByRole('button', { name: 'Confirm subtitle', exact: true })).toBeNull()
    expect(apply).not.toHaveBeenCalled()
  } finally {
    vi.restoreAllMocks()
  }
})
