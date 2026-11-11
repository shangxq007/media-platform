import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { RenderWorkbench } from '../product/render/RenderWorkbench'

const mock = vi.hoisted(() => ({
  tenant: vi.fn(), projects: vi.fn(), jobs: vi.fn(), head: vi.fn(), revision: vi.fn(), snapshot: vi.fn(), applyOperation: vi.fn(),
  submit: vi.fn(), incremental: vi.fn(), result: vi.fn(), lifecycle: vi.fn(), cancel: vi.fn(), download: vi.fn(),
  create: vi.fn(), get: vi.fn(), list: vi.fn(), deliveryDownload: vi.fn(), listeners: new Set<() => void>(),
}))
vi.mock('../api/render-jobs', () => ({ renderReadSource: { tenant: mock.tenant, projects: mock.projects, jobs: mock.jobs } }))
vi.mock('../api/formal-render', () => ({ FormalRenderAPI: mock }))
vi.mock('../api/delivery', () => ({ DeliveryAPI: { create: mock.create, get: mock.get, list: mock.list, download: mock.deliveryDownload } }))
vi.mock('../auth/oidcClient', () => ({ subscribeOidcSessionRetirement: (listener: () => void) => { mock.listeners.add(listener); return () => { mock.listeners.delete(listener) } } }))
const complete = { renderJobId: 'j', status: 'COMPLETED', finalArtifactId: 'artifact', failureCode: null }
const delivery = { deliveryId: 'delivery', projectId: 'p', sourceRenderJobId: 'j', timelineRevisionId: 'r', artifactId: 'artifact', status: 'READY', createdAt: 'now', resultPath: '/api/projects/p/deliveries/delivery/result' }
async function ready() { render(<RenderWorkbench />); await screen.findByText('r'); await waitFor(() => expect(screen.getByRole('button', { name: 'Submit render' }).closest('fieldset')?.disabled).toBe(false)) }
afterEach(() => { cleanup(); mock.listeners.clear(); vi.clearAllMocks() })
beforeEach(() => {
  mock.tenant.mockResolvedValue('t'); mock.projects.mockResolvedValue([{ id: 'p', tenantId: 't', name: 'Film' }, { id: 'q', tenantId: 't', name: 'Second' }]); mock.jobs.mockResolvedValue([])
  mock.head.mockResolvedValue({ revisionId: 'r', contentHash: 'hash' }); mock.revision.mockResolvedValue({ revisionId: 'r2', contentHash: 'hash2' }); mock.snapshot.mockResolvedValue({ canonicalTimelineJson: JSON.stringify({ tracks: [{ clips: [{ clipId: 'clip' }] }] }) })
  mock.list.mockResolvedValue([]); mock.result.mockResolvedValue(complete); mock.submit.mockResolvedValue({ renderJobId: 'j' }); mock.incremental.mockResolvedValue({ renderJobId: 'j2' }); mock.lifecycle.mockResolvedValue({ renderJobId: 'j', timelineRevisionId: 'r', projectId: 'p' }); mock.create.mockResolvedValue(delivery)
})
describe('Formal render workbench product and authorization boundaries', () => {
  it('reads only the authenticated catalogue and does not submit when scope is unavailable', async () => {
    mock.tenant.mockRejectedValue(new Error('Authentication required')); render(<RenderWorkbench />)
    await screen.findByRole('alert'); expect(mock.projects).not.toHaveBeenCalled(); expect(mock.head).not.toHaveBeenCalled(); expect(mock.submit).not.toHaveBeenCalled()
    expect(screen.queryByRole('button', { name: 'Submit render' })).toBeNull()
  })
  it('edits through Operation, submits a pinned formal job and creates an authorized delivery', async () => {
    await ready(); mock.applyOperation.mockResolvedValue({ revisionId: 'r2', contentHash: 'hash2' })
    fireEvent.click(screen.getByRole('button', { name: 'Apply operation' })); await screen.findByText('r2')
    expect(mock.applyOperation).toHaveBeenCalledWith('t', 'p', expect.objectContaining({ type: 'TRIM_CLIP', baseRevisionId: 'r', baseContentHash: 'hash', clipId: 'clip', delta: '1/4', edge: 'END', commandId: expect.any(String) }), expect.any(AbortSignal))
    fireEvent.click(screen.getByRole('button', { name: 'Submit render' })); await screen.findByRole('status')
    expect(mock.submit).toHaveBeenCalledWith(expect.objectContaining({ revisionId: 'r2', extent: { startMillis: 0, endMillis: 3000, frameRateNumerator: 4, frameRateDenominator: 1 } }), expect.any(String), expect.any(AbortSignal))
    fireEvent.click(screen.getByRole('button', { name: 'Create delivery' })); await screen.findByText('delivery')
    expect(mock.create).toHaveBeenCalledWith('p', { sourceRenderJobId: 'j', artifactId: 'artifact' }, expect.any(String))
    expect(screen.getByRole('button', { name: 'Download delivery' })).toBeTruthy()
  })
  it('uses the completed job and its exact revision as incremental reference', async () => {
    await ready(); fireEvent.click(screen.getByRole('button', { name: 'Submit render' })); await screen.findByRole('status')
    await waitFor(() => expect((screen.getByRole('button', { name: 'Submit incremental render' }) as HTMLButtonElement).disabled).toBe(false))
    mock.result.mockResolvedValue({ ...complete, renderJobId: 'j2' }); fireEvent.click(screen.getByRole('button', { name: 'Submit incremental render' }))
    await waitFor(() => expect(mock.incremental).toHaveBeenCalledWith(expect.objectContaining({ targetRevisionId: 'r', referenceRevisionId: 'r', referenceRenderJobId: 'j' }), expect.any(String), expect.any(AbortSignal)))
  })
  it('retains the same idempotency identity for an uncertain submit response', async () => {
    await ready(); mock.submit.mockRejectedValueOnce(new Error('Connection interrupted'))
    fireEvent.click(screen.getByRole('button', { name: 'Submit render' })); await screen.findByRole('alert')
    fireEvent.click(screen.getByRole('button', { name: 'Submit render' })); await screen.findByRole('status')
    expect(mock.submit.mock.calls[0][1]).toBe(mock.submit.mock.calls[1][1])
  })
  it('retires old project reads and ignores their late results after a project switch', async () => {
    let finish!: (value: unknown) => void; mock.snapshot.mockImplementationOnce(() => new Promise(resolve => { finish = resolve }))
    render(<RenderWorkbench />); await waitFor(() => expect(finish).toBeDefined()); const signal = mock.snapshot.mock.calls[0][2] as AbortSignal
    fireEvent.change(screen.getByRole('combobox', { name: 'Project' }), { target: { value: 'q' } }); await screen.findByText('r'); expect(signal.aborted).toBe(true)
    await act(async () => finish({ canonicalTimelineJson: JSON.stringify({ tracks: [{ clips: [{ clipId: 'late-secret' }] }] }) })); expect(screen.queryByText('late-secret')).toBeNull()
  })
  it('clears private results on session retirement and aborts the request domain', async () => {
    await ready(); fireEvent.click(screen.getByRole('button', { name: 'Submit render' })); await screen.findByRole('status'); const signal = mock.submit.mock.calls[0][2] as AbortSignal
    act(() => mock.listeners.forEach(listener => listener())); expect(signal.aborted).toBe(true)
    expect(screen.queryByText('j')).toBeNull(); expect(screen.queryByText('r')).toBeNull(); expect(screen.queryByRole('button', { name: 'Create delivery' })).toBeNull()
  })
  it('removes private state when a refreshed read denies project access', async () => {
    await ready(); fireEvent.click(screen.getByRole('button', { name: 'Submit render' })); await screen.findByRole('status')
    mock.head.mockRejectedValue(Object.assign(new Error('Request failed'), { response: { status: 403 } }))
    fireEvent.click(screen.getByRole('button', { name: 'Load current revision' })); await screen.findByRole('alert')
    await waitFor(() => expect(screen.queryByRole('status')).toBeNull()); expect(screen.queryByRole('button', { name: 'Create delivery' })).toBeNull()
  })
})
