import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { FormalRenderAPI } from '../../api/formal-render'
import { versionlessApi } from '../../api/app/versionless-api'
import { ClipEditor } from './ClipEditor'
import type { CanonicalHeadReference } from './gateways'
import { contentHash, projectId, revisionId, timelineId } from './types'

vi.mock('../../api/oidc-transport', () => ({
  sessionBoundRequest: async (call: (config: object) => Promise<{ data: unknown }>) => (await call({})).data,
}))

vi.mock('../../foundation/projectContext', () => ({
  useProjectContext: () => ({ workspaceId: 'workspace-1', tenantId: 'tenant-1',
    projectId: 'project-1', project: { id: 'project-1' }, status: 'RESOLVED', reason: '' }),
}))

vi.mock('../../api/formal-render', () => ({
  FormalRenderAPI: {
    snapshot: vi.fn(), previewOperation: vi.fn(), applyConfirmedClip: vi.fn(), applyOperation: vi.fn(), head: vi.fn(),
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
      clipId: 'clip-0', sourceKind: 'MEDIA_STREAM', artifactId: 'artifact', mediaStreamId: 'stream', contentDigest: 'a'.repeat(64), startTime: '0', endTime: '6', trimStart: '0', trimEnd: '6',
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
    batchDigest: 'a'.repeat(64), steps: [{ changes: [{ identity: 'clip-0', primary: true }] }],
  })
  api.applyOperation.mockResolvedValue({ revisionId: 'revision-R1', contentHash: 'B'.repeat(42) + 'E=' })
  api.applyConfirmedClip.mockResolvedValue({ revisionId: 'revision-R1', contentHash: 'B'.repeat(42) + 'E=' })
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
    expect(api.applyConfirmedClip).not.toHaveBeenCalled()
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
    await waitFor(() => expect(api.applyConfirmedClip).toHaveBeenCalledTimes(1))
    expect(api.applyConfirmedClip.mock.calls[0][2].commandId).toBe(api.previewOperation.mock.calls[0][2].commandId)
    expect(api.applyConfirmedClip.mock.calls[0][3]).toBe('a'.repeat(64))
    expect(api.applyConfirmedClip.mock.calls[0][2].delta).toBe('1/1')
    await waitFor(() => expect(onRevisionChanged).toHaveBeenCalledTimes(1))
    expect(await screen.findByText(/Trim applied at revision revision-R1/)).toBeTruthy()
  })

  it.each(['lost committed response', 'retryable server error'])('retries the exact confirmed HTTP trim after %s', async failure => {
    const { FormalRenderAPI: gateway } = await vi.importActual<typeof import('../../api/formal-render')>('../../api/formal-render')
    const trace: { action: string; body: string; confirmation?: string }[] = []
    let committed = false
    let revisions = 0
    const transport = vi.spyOn(versionlessApi, 'post').mockImplementation(async (path, body, config) => {
      const action = path.substring(path.lastIndexOf('/') + 1)
      trace.push({ action, body: JSON.stringify(body), confirmation: config?.headers?.['If-Match'] as string | undefined })
      if (action === 'preview') return { data: { batchDigest: 'a'.repeat(64), steps: [{ changes: [] }] } }
      if (trace.length === 2 && failure === 'retryable server error') throw Object.assign(new Error('unavailable'), { response: { status: 503 } })
      if (!committed) { committed = true; revisions++ }
      if (trace.length === 2) throw new Error('Response lost after commit')
      return { data: { status: 'APPLIED', newRevisionId: 'revision-R1', newContentHash: 'B'.repeat(42) + 'E=' } }
    })
    try {
      api.previewOperation.mockImplementationOnce(gateway.previewOperation.bind(gateway))
      api.applyConfirmedClip.mockImplementationOnce(gateway.applyConfirmedClip.bind(gateway))
        .mockImplementationOnce(gateway.applyConfirmedClip.bind(gateway))
      const { onRevisionChanged } = renderEditor()
      const handle = await screen.findByLabelText('Trim start edge clip-0')
      fireEvent.pointerDown(handle, { clientX: 100, pointerId: 1 })
      fireEvent.pointerMove(handle, { clientX: 180, pointerId: 1 })
      fireEvent.pointerUp(handle, { clientX: 180, pointerId: 1 })
      await screen.findByText(/Preview valid/)
      expect(screen.queryByRole('button', { name: /Retry Trim/ })).toBeNull()
      fireEvent.click(screen.getByRole('button', { name: 'Apply trim' }))
      fireEvent.click(await screen.findByRole('button', { name: /Retry Trim/ }))
      await waitFor(() => expect(onRevisionChanged).toHaveBeenCalledOnce())
      expect(trace.map(row => row.action)).toEqual(['preview', 'apply', 'apply'])
      expect(trace[1]).toEqual(trace[2])
      expect(trace[1].body).toBe(trace[0].body)
      expect(trace[1].confirmation).toBe('"'+ 'a'.repeat(64) +'"')
      expect(revisions).toBe(1)
      expect(api.applyOperation).not.toHaveBeenCalled()
      console.info('G4_TRIM_TRACE', JSON.stringify({ failure, revisions, trace }))
    } finally { transport.mockRestore() }
  })

  it.each([409, 412, 503])('discards a failed trim after status %i or explicit cancellation', async status => {
    const { onRevisionChanged } = renderEditor()
    api.applyConfirmedClip.mockRejectedValueOnce(Object.assign(new Error('Apply failed'), { response: { status } }))
    const handle = await screen.findByLabelText('Trim start edge clip-0')
    fireEvent.pointerDown(handle, { clientX: 100, pointerId: 1 })
    fireEvent.pointerMove(handle, { clientX: 180, pointerId: 1 })
    fireEvent.pointerUp(handle, { clientX: 180, pointerId: 1 })
    await screen.findByText(/Preview valid/)
    fireEvent.click(screen.getByRole('button', { name: 'Apply trim' }))
    if (status === 503) {
      await screen.findByRole('button', { name: /Retry Trim/ })
      fireEvent.click(screen.getByRole('button', { name: 'Cancel trim' }))
    } else {
      await screen.findByText(/re-preview before editing again/)
      expect(onRevisionChanged).toHaveBeenCalledOnce()
    }
    expect(screen.queryByRole('button', { name: /Retry Trim/ })).toBeNull()
    expect((screen.getByRole('button', { name: 'Apply trim' }) as HTMLButtonElement).disabled).toBe(true)
    expect(api.applyConfirmedClip).toHaveBeenCalledOnce()
    expect(api.previewOperation).toHaveBeenCalledOnce()
  })

  it('cancelling a pending trim never writes a revision', async () => {
    renderEditor()
    await waitFor(() => expect(screen.getByLabelText('Trim start edge clip-0')).toBeTruthy())
    const handle = screen.getByLabelText('Trim start edge clip-0')
    fireEvent.pointerDown(handle, { clientX: 100, pointerId: 1 })
    fireEvent.pointerMove(handle, { clientX: 260, pointerId: 1 })
    fireEvent.pointerUp(handle, { clientX: 260, pointerId: 1 })
    await screen.findByText(/Preview valid/)
    await waitFor(() => expect(screen.getByRole('button', { name: 'Cancel trim' })).toBeTruthy())
    fireEvent.click(screen.getByRole('button', { name: 'Cancel trim' }))
    expect(api.applyConfirmedClip).not.toHaveBeenCalled()
    expect(await screen.findByText(/No revision was written/)).toBeTruthy()
    expect(screen.queryByRole('button', { name: /Retry Trim/ })).toBeNull()
    expect((screen.getByRole('button', { name: 'Apply trim' }) as HTMLButtonElement).disabled).toBe(true)
    expect(api.applyOperation).not.toHaveBeenCalled()
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
    expect(api.applyConfirmedClip).not.toHaveBeenCalled()
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
    await waitFor(() => expect(onRevisionChanged).toHaveBeenCalledTimes(1))
  })
})


const multiClipSnapshot = {
  schemaVersion: 'timeline-1.0',
  canonicalTimelineJson: JSON.stringify({
    schemaVersion: 'timeline-1.0',
    tracks: [{ trackId: 'track-1', name: 'Video', type: 'video', clips: [
      { clipId: 'clip-a', sourceKind: 'MEDIA_STREAM', artifactId: 'artifact', mediaStreamId: 'stream', contentDigest: 'a'.repeat(64), startTime: '0', endTime: '2', trimStart: '0', trimEnd: '2',
        temporalMapping: { kind: 'CONSTANT_RATE', rate: { numerator: 1, denominator: 1 }, direction: 'FORWARD' } },
      { clipId: 'clip-b', sourceKind: 'MEDIA_STREAM', artifactId: 'artifact', mediaStreamId: 'stream', contentDigest: 'a'.repeat(64), startTime: '2', endTime: '4', trimStart: '0', trimEnd: '2',
        temporalMapping: { kind: 'CONSTANT_RATE', rate: { numerator: 1, denominator: 1 }, direction: 'FORWARD' } },
      { clipId: 'clip-c', sourceKind: 'MEDIA_STREAM', artifactId: 'artifact', mediaStreamId: 'stream', contentDigest: 'a'.repeat(64), startTime: '4', endTime: '6', trimStart: '0', trimEnd: '2',
        temporalMapping: { kind: 'CONSTANT_RATE', rate: { numerator: 1, denominator: 1 }, direction: 'FORWARD' } },
    ] }],
  }),
}

describe('ClipEditor move and delete interaction', () => {
  async function renderMulti() {
    api.snapshot.mockResolvedValue(multiClipSnapshot)
    const onRevisionChanged = vi.fn(async () => {})
    render(<ClipEditor head={head} onRevisionChanged={onRevisionChanged} />)
    await waitFor(() => expect(screen.getByLabelText('Timeline clip clip-a')).toBeTruthy())
    return { onRevisionChanged }
  }

  it('moves a clip by dragging its body: server preview then apply with one command id', async () => {
    const { onRevisionChanged } = await renderMulti()
    const body = screen.getByLabelText('Timeline clip clip-c')
    fireEvent.pointerDown(body, { clientX: 100, pointerId: 1 })
    fireEvent.pointerMove(body, { clientX: 100 - 160, pointerId: 1 })
    fireEvent.pointerUp(body, { clientX: 100 - 160, pointerId: 1 })
    await waitFor(() => expect(api.previewOperation).toHaveBeenCalledTimes(1))
    const preview = api.previewOperation.mock.calls[0][2]
    expect(preview.type).toBe('MOVE_CLIP')
    expect(preview.absolute).toBe(true)
    expect(preview.clipId).toBe('clip-c')
    expect(preview.delta).toBe('2/1')
    await waitFor(() => expect(api.applyOperation).toHaveBeenCalledTimes(1))
    const applied = api.applyOperation.mock.calls[0][2]
    expect(applied.type).toBe('MOVE_CLIP')
    expect(applied.commandId).toBe(preview.commandId)
    await waitFor(() => expect(onRevisionChanged).toHaveBeenCalledTimes(1))
    expect(await screen.findByText(/Clip moved to/)).toBeTruthy()
  })

  it('does not move a clip when the drag is below the threshold', async () => {
    await renderMulti()
    const body = screen.getByLabelText('Timeline clip clip-a')
    fireEvent.pointerDown(body, { clientX: 100, pointerId: 1 })
    fireEvent.pointerMove(body, { clientX: 102, pointerId: 1 })
    fireEvent.pointerUp(body, { clientX: 102, pointerId: 1 })
    await new Promise(resolve => setTimeout(resolve, 20))
    expect(api.applyOperation).not.toHaveBeenCalled()
    expect(api.previewOperation).not.toHaveBeenCalled()
  })

  it('deletes the selected clip with the Delete key', async () => {
    const { onRevisionChanged } = await renderMulti()
    const section = screen.getByRole('region', { name: 'Edit existing clip' })
    fireEvent.click(screen.getByLabelText('Timeline clip clip-b'))
    fireEvent.keyDown(section, { key: 'Delete' })
    await waitFor(() => expect(api.applyOperation).toHaveBeenCalledTimes(1))
    const applied = api.applyOperation.mock.calls[0][2]
    expect(applied.type).toBe('DELETE_CLIP')
    expect(applied.clipId).toBe('clip-b')
    expect(applied.delta).toBeUndefined()
    await waitFor(() => expect(onRevisionChanged).toHaveBeenCalledTimes(1))
    expect(await screen.findByText(/deleted at revision/)).toBeTruthy()
  })

  it('ignores Delete while a form control has focus', async () => {
    await renderMulti()
    const select = screen.getByLabelText('Clip') as HTMLSelectElement
    fireEvent.change(select, { target: { value: 'clip-a' } })
    fireEvent.keyDown(select, { key: 'Delete' })
    await new Promise(resolve => setTimeout(resolve, 20))
    expect(api.applyOperation).not.toHaveBeenCalled()
  })

  it('deletes through the explicit button as well', async () => {
    await renderMulti()
    fireEvent.click(screen.getByLabelText('Timeline clip clip-a'))
    fireEvent.click(screen.getByRole('button', { name: 'Delete selected clip' }))
    await waitFor(() => expect(api.applyOperation).toHaveBeenCalledTimes(1))
    expect(api.applyOperation.mock.calls[0][2].type).toBe('DELETE_CLIP')
  })

  it('shows the failure and refreshes from the server when the move is rejected', async () => {
    const { onRevisionChanged } = await renderMulti()
    api.previewOperation.mockRejectedValue(new Error('move rejected by the canonical authority'))
    const body = screen.getByLabelText('Timeline clip clip-a')
    fireEvent.pointerDown(body, { clientX: 100, pointerId: 1 })
    fireEvent.pointerMove(body, { clientX: 260, pointerId: 1 })
    fireEvent.pointerUp(body, { clientX: 260, pointerId: 1 })
    await waitFor(() => expect(screen.getByRole('alert')).toBeTruthy())
    expect(screen.getByRole('alert').textContent).toMatch(/move rejected/)
    expect(onRevisionChanged).toHaveBeenCalled()
    expect(api.applyOperation).not.toHaveBeenCalled()
  })
})


describe('ClipEditor overlap rejection', () => {
  it('surfaces a canonical same-track overlap rejection and never applies the move', async () => {
    api.snapshot.mockResolvedValue(multiClipSnapshot)
    const onRevisionChanged = vi.fn(async () => {})
    api.previewOperation.mockRejectedValue(Object.assign(new Error('Clips overlap on the same track'),
      { response: { status: 422 } }))
    render(<ClipEditor head={head} onRevisionChanged={onRevisionChanged} />)
    await waitFor(() => expect(screen.getByLabelText('Timeline clip clip-c')).toBeTruthy())
    const body = screen.getByLabelText('Timeline clip clip-c')
    fireEvent.pointerDown(body, { clientX: 100, pointerId: 1 })
    fireEvent.pointerMove(body, { clientX: 100 - 240, pointerId: 1 })
    fireEvent.pointerUp(body, { clientX: 100 - 240, pointerId: 1 })
    await waitFor(() => expect(screen.getByRole('alert')).toBeTruthy())
    expect(screen.getByRole('alert').textContent).toMatch(/overlap/i)
    expect(api.applyOperation).not.toHaveBeenCalled()
    expect(onRevisionChanged).toHaveBeenCalled()
  })
})

describe('ClipEditor split interaction', () => {
  it('previews and applies one canonical SPLIT_CLIP with a stable right identity', async () => {
    const { onRevisionChanged } = renderEditor()
    await waitFor(() => expect(screen.getByLabelText('Timeline clip clip-0')).toBeTruthy())
    fireEvent.change(screen.getByLabelText('Clip operation'), { target: { value: 'SPLIT_CLIP' } })
    fireEvent.change(screen.getByLabelText('Split time'), { target: { value: '3/1' } })
    fireEvent.click(screen.getByLabelText('Split selected clip'))
    await waitFor(() => expect(api.previewOperation).toHaveBeenCalledTimes(1))
    const preview = api.previewOperation.mock.calls[0][2]
    expect(preview.type).toBe('SPLIT_CLIP')
    expect(preview.clipId).toBe('clip-0')
    expect(preview.splitTime).toBe('3/1')
    expect(preview.rightClipId).toMatch(/^[0-9a-f-]{36}$/)
    await waitFor(() => expect(api.applyOperation).toHaveBeenCalledTimes(1))
    const applied = api.applyOperation.mock.calls[0][2]
    expect(applied.commandId).toBe(preview.commandId)
    expect(applied.rightClipId).toBe(preview.rightClipId)
    expect(applied.splitTime).toBe('3/1')
    await waitFor(() => expect(onRevisionChanged).toHaveBeenCalledTimes(1))
    expect(await screen.findByText(/Clip split at 3/)).toBeTruthy()
  })

  it('rejects a split at the clip boundary without calling the canonical API', async () => {
    renderEditor()
    await waitFor(() => expect(screen.getByLabelText('Timeline clip clip-0')).toBeTruthy())
    fireEvent.change(screen.getByLabelText('Clip operation'), { target: { value: 'SPLIT_CLIP' } })
    fireEvent.change(screen.getByLabelText('Split time'), { target: { value: '0' } })
    fireEvent.click(screen.getByLabelText('Split selected clip'))
    await waitFor(() => expect(screen.getByRole('alert')).toBeTruthy())
    expect(screen.getByRole('alert').textContent).toMatch(/strictly inside/)
    expect(api.previewOperation).not.toHaveBeenCalled()
    expect(api.applyOperation).not.toHaveBeenCalled()
  })
})

describe('ClipEditor conflict and retry', () => {
  it('retries a transient apply failure with the same command id', async () => {
    const { onRevisionChanged } = renderEditor()
    await waitFor(() => expect(screen.getByLabelText('Timeline clip clip-0')).toBeTruthy())
    api.applyOperation.mockRejectedValueOnce(new Error('network down'))
    fireEvent.click(screen.getByRole('button', { name: 'Delete selected clip' }))
    await waitFor(() => expect(screen.getByRole('button', { name: /Retry delete/i })).toBeTruthy())
    const first = api.applyOperation.mock.calls[0][2]
    fireEvent.click(screen.getByRole('button', { name: /Retry delete/i }))
    await waitFor(() => expect(api.applyOperation).toHaveBeenCalledTimes(2))
    expect(api.applyOperation.mock.calls[1][2].commandId).toBe(first.commandId)
    await waitFor(() => expect(onRevisionChanged).toHaveBeenCalled())
  })

  it('treats a 409 apply result as a conflict and reloads without retrying', async () => {
    const { onRevisionChanged } = renderEditor()
    await waitFor(() => expect(screen.getByLabelText('Timeline clip clip-0')).toBeTruthy())
    api.applyOperation.mockRejectedValueOnce(Object.assign(new Error('stale'), { response: { status: 409 } }))
    fireEvent.click(screen.getByRole('button', { name: 'Delete selected clip' }))
    await waitFor(() => expect(screen.getByRole('alert')).toBeTruthy())
    expect(screen.getByRole('alert').textContent).toMatch(/HEAD has changed/)
    expect(screen.queryByRole('button', { name: /Retry delete/i })).toBeNull()
    await waitFor(() => expect(onRevisionChanged).toHaveBeenCalled())
  })

  it('shows a split marker strictly inside the selected clip', async () => {
    renderEditor()
    await waitFor(() => expect(screen.getByLabelText('Timeline clip clip-0')).toBeTruthy())
    fireEvent.change(screen.getByLabelText('Clip operation'), { target: { value: 'SPLIT_CLIP' } })
    fireEvent.change(screen.getByLabelText('Split time'), { target: { value: '3/1' } })
    await waitFor(() => expect(document.querySelector('[data-split-marker]')).toBeTruthy())
    fireEvent.change(screen.getByLabelText('Split time'), { target: { value: '0' } })
    await waitFor(() => expect(document.querySelector('[data-split-marker]')).toBeNull())
  })
})
