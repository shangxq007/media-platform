import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { FormalRenderAPI } from '../../api/formal-render'
import { ClipEditor } from './ClipEditor'
import type { CanonicalHeadReference } from './gateways'
import { contentHash, projectId, revisionId, timelineId } from './types'

vi.mock('../../foundation/projectContext', () => ({
  useProjectContext: () => ({ workspaceId: 'workspace-1', tenantId: 'tenant-1',
    projectId: 'project-1', project: { id: 'project-1' }, status: 'RESOLVED', reason: '' }),
}))

vi.mock('../../api/formal-render', () => ({
  FormalRenderAPI: {
    snapshot: vi.fn(), previewOperation: vi.fn(), applyOperation: vi.fn(), head: vi.fn(),
  },
}))

const api = vi.mocked(FormalRenderAPI)

const head: CanonicalHeadReference = {
  projectId: projectId('project-1'), timelineId: timelineId('project-1'),
  revisionId: revisionId('revision-R0'), contentHash: contentHash('A'.repeat(43) + '='),
}

const snapshot = {
  schemaVersion: 'timeline-1.0',
  canonicalTimelineJson: JSON.stringify({
    schemaVersion: 'timeline-1.0',
    tracks: [{ trackId: 'track-1', name: 'Video', type: 'video', clips: [{
      clipId: 'clip-0', startTime: '0', endTime: '6', trimStart: '0', trimEnd: '6',
      temporalMapping: { kind: 'CONSTANT_RATE', rate: { numerator: 1, denominator: 1 }, direction: 'FORWARD' },
    }] }],
  }),
}

function renderEditor(onRevisionChanged = vi.fn(async () => {})) {
  render(<ClipEditor head={head} onRevisionChanged={onRevisionChanged} />)
  return { onRevisionChanged }
}

beforeEach(() => {
  vi.clearAllMocks()
  api.snapshot.mockResolvedValue(snapshot)
  api.previewOperation.mockResolvedValue({
    status: 'VALID', definitionId: 'timeline.trim', clipId: 'clip-0', type: 'TRIM_CLIP',
    edge: 'START', delta: '2/1',
  })
  api.applyOperation.mockResolvedValue({ revisionId: 'revision-R1', contentHash: 'B'.repeat(42) + 'E=' })
  api.head.mockResolvedValue({ ...head, revisionId: 'revision-R1', contentHash: 'B'.repeat(42) + 'E=' } as never)
})

describe('ClipEditor trim interaction', () => {
  it('renders the clip strip with both edge handles from the canonical snapshot', async () => {
    renderEditor()
    await waitFor(() => expect(screen.getByLabelText('Timeline clip clip-0')).toBeTruthy())
    expect(screen.getByLabelText('Trim start edge clip-0')).toBeTruthy()
    expect(screen.getByLabelText('Trim end edge clip-0')).toBeTruthy()
    const strip = screen.getByRole('group', { name: 'Timeline trim' })
    expect(strip.getAttribute('data-pixels-per-second')).toBe('80')
  })

  it('previews a dragged left edge without writing a revision', async () => {
    renderEditor()
    await waitFor(() => expect(screen.getByLabelText('Trim start edge clip-0')).toBeTruthy())
    const handle = screen.getByLabelText('Trim start edge clip-0')
    fireEvent.pointerDown(handle, { clientX: 100, pointerId: 1 })
    fireEvent.pointerMove(handle, { clientX: 260, pointerId: 1 })
    fireEvent.pointerUp(handle, { clientX: 260, pointerId: 1 })
    await waitFor(() => expect(api.previewOperation).toHaveBeenCalledTimes(1))
    const command = api.previewOperation.mock.calls[0][2]
    expect(command.type).toBe('TRIM_CLIP')
    expect(command.edge).toBe('START')
    expect(command.delta).toBe('2/1')
    expect(command.baseRevisionId).toBe(head.revisionId)
    expect(api.applyOperation).not.toHaveBeenCalled()
    await waitFor(() => expect(screen.getByText(/Preview valid/)).toBeTruthy())
  })

  it('applies the previewed trim with the same command id and refreshes the authoritative head', async () => {
    const { onRevisionChanged } = renderEditor()
    await waitFor(() => expect(screen.getByLabelText('Trim start edge clip-0')).toBeTruthy())
    const handle = screen.getByLabelText('Trim start edge clip-0')
    fireEvent.pointerDown(handle, { clientX: 100, pointerId: 1 })
    fireEvent.pointerMove(handle, { clientX: 180, pointerId: 1 })
    fireEvent.pointerUp(handle, { clientX: 180, pointerId: 1 })
    await waitFor(() => expect(api.previewOperation).toHaveBeenCalledTimes(1))
    const apply = screen.getByRole('button', { name: 'Apply trim' }) as HTMLButtonElement
    await waitFor(() => expect(apply.disabled).toBe(false))
    fireEvent.click(apply)
    await waitFor(() => expect(api.applyOperation).toHaveBeenCalledTimes(1))
    expect(api.applyOperation.mock.calls[0][2].commandId).toBe(api.previewOperation.mock.calls[0][2].commandId)
    expect(api.applyOperation.mock.calls[0][2].delta).toBe('1/1')
    await waitFor(() => expect(onRevisionChanged).toHaveBeenCalledTimes(1))
    expect(await screen.findByText(/Trim applied at revision revision-R1/)).toBeTruthy()
  })

  it('cancelling a pending trim never writes a revision', async () => {
    renderEditor()
    await waitFor(() => expect(screen.getByLabelText('Trim start edge clip-0')).toBeTruthy())
    const handle = screen.getByLabelText('Trim start edge clip-0')
    fireEvent.pointerDown(handle, { clientX: 100, pointerId: 1 })
    fireEvent.pointerMove(handle, { clientX: 260, pointerId: 1 })
    fireEvent.pointerUp(handle, { clientX: 260, pointerId: 1 })
    await waitFor(() => expect(screen.getByRole('button', { name: 'Cancel trim' })).toBeTruthy())
    fireEvent.click(screen.getByRole('button', { name: 'Cancel trim' }))
    expect(api.applyOperation).not.toHaveBeenCalled()
    expect(await screen.findByText(/No revision was written/)).toBeTruthy()
  })

  it('shows the canonical rejection and keeps apply disabled', async () => {
    api.previewOperation.mockRejectedValue(new Error('Trim must leave a positive media range.'))
    renderEditor()
    await waitFor(() => expect(screen.getByLabelText('Trim start edge clip-0')).toBeTruthy())
    const handle = screen.getByLabelText('Trim start edge clip-0')
    fireEvent.pointerDown(handle, { clientX: 100, pointerId: 1 })
    fireEvent.pointerMove(handle, { clientX: 900, pointerId: 1 })
    fireEvent.pointerUp(handle, { clientX: 900, pointerId: 1 })
    await waitFor(() => expect(screen.getByRole('alert')).toBeTruthy())
    expect(screen.getByRole('alert').textContent).toMatch(/positive media range/)
    expect((screen.getByRole('button', { name: 'Apply trim' }) as HTMLButtonElement).disabled).toBe(true)
    expect(api.applyOperation).not.toHaveBeenCalled()
  })

  it('refuses to apply when the authoritative revision changed after preview', async () => {
    const { onRevisionChanged } = renderEditor()
    await waitFor(() => expect(screen.getByLabelText('Trim start edge clip-0')).toBeTruthy())
    const handle = screen.getByLabelText('Trim start edge clip-0')
    fireEvent.pointerDown(handle, { clientX: 100, pointerId: 1 })
    fireEvent.pointerMove(handle, { clientX: 180, pointerId: 1 })
    fireEvent.pointerUp(handle, { clientX: 180, pointerId: 1 })
    await waitFor(() => expect(api.previewOperation).toHaveBeenCalledTimes(1))
    // The server accepts under a different head; the editor must not present a false success.
    api.head.mockResolvedValue({ ...head, revisionId: 'revision-OTHER' } as never)
    fireEvent.click(screen.getByRole('button', { name: 'Apply trim' }))
    await waitFor(() => expect(screen.getByRole('alert')).toBeTruthy())
    expect(screen.getByRole('alert').textContent).toMatch(/HEAD has changed/)
    expect(onRevisionChanged).not.toHaveBeenCalled()
  })
})
