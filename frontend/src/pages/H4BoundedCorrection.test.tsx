import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { RenderResultDetailPage } from '../routes/app/renders/RenderResultDetailPage'
import { RenderResultsListPage } from '../routes/app/renders/RenderResultsListPage'
import { StrictMode } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, waitFor, within } from '@testing-library/react'
import { WorkspaceSessionProvider } from '../foundation/workspaceSession'
import { RenderBrowser } from './RenderJobDashboard'
import type { RenderReadSource } from '../api/render-jobs'

const hooks = vi.hoisted(() => ({
  useRenderWorkspaceScope: vi.fn(),
  useProducts: vi.fn(),
  useProductDetail: vi.fn(),
}))

vi.mock('../api/render-jobs', async original => ({
  ...await original<typeof import('../api/render-jobs')>(),
  useRenderWorkspaceScope: hooks.useRenderWorkspaceScope,
}))

vi.mock('../query/app/useProducts', () => ({
  useProducts: hooks.useProducts,
  useProductDetail: hooks.useProductDetail,
}))

describe('H4 bounded correction product surfaces', () => {
  beforeEach(() => {
    hooks.useRenderWorkspaceScope.mockReturnValue({ data: null, isLoading: false, error: null })
    hooks.useProducts.mockReturnValue({ data: undefined, isLoading: false, error: null })
    hooks.useProductDetail.mockReturnValue({ data: undefined, isLoading: false, error: null })
  })

  afterEach(() => {
    cleanup()
    window.history.pushState({}, '', '/')
    vi.clearAllMocks()
  })

  it('fails both registered Product routes closed without authenticated workspace scope', () => {
    const list = render(<RenderResultsListPage />)
    expect(screen.getByText(/authenticated workspace with a recent project is required/i)).toBeTruthy()
    expect(hooks.useProducts).toHaveBeenCalledWith({ tenantId: undefined, projectId: undefined })
    list.unmount()

    window.history.pushState({}, '', '/app/renders/product-1')
    render(<RenderResultDetailPage />)
    expect(screen.getByText(/authenticated workspace with a recent project is required/i)).toBeTruthy()
    expect(hooks.useProductDetail).toHaveBeenCalledWith(
      { tenantId: undefined, projectId: undefined },
      'product-1'
    )
  })

  it('passes only authenticated workspace and recent-project IDs to Product queries', () => {
    hooks.useRenderWorkspaceScope.mockReturnValue({
      data: { tenantId: 'tenant-1', recentProjects: [{ id: 'project-1', name: 'Project' }] },
      isLoading: false,
      error: null,
    })

    const list = render(<RenderResultsListPage />)
    expect(hooks.useProducts).toHaveBeenCalledWith({ tenantId: 'tenant-1', projectId: 'project-1' })
    list.unmount()

    window.history.pushState({}, '', '/app/renders/product-1')
    render(<RenderResultDetailPage />)
    expect(hooks.useProductDetail).toHaveBeenCalledWith(
      { tenantId: 'tenant-1', projectId: 'project-1' },
      'product-1'
    )
  })

})


const oidc = vi.hoisted(() => ({ listeners: new Set<() => void>() }))
vi.mock('../auth/oidcClient', async original => ({ ...await original<typeof import('../auth/oidcClient')>(), subscribeOidcSessionRetirement: (listener: () => void) => { oidc.listeners.add(listener); return () => { oidc.listeners.delete(listener) } } }))
const project = (id = 'p') => ({ id, tenantId: 't', name: id === 'p' ? 'Film' : 'Second film', description: null, status: 'ACTIVE', createdAt: '2026-09-13T00:00:00Z' })
const job = (id = 'j', projectId = 'p') => ({ id, projectId, timelineSnapshotId: 'snapshot', profile: 'preview', status: 'FAILED' as const })
function source() { return { tenant: vi.fn<RenderReadSource['tenant']>(async () => 't'), projects: vi.fn<RenderReadSource['projects']>(async () => [project(), project('p2')]), project: vi.fn<RenderReadSource['project']>(async (_t, p) => project(p)), jobs: vi.fn<RenderReadSource['jobs']>(async (_t, p) => [job(p === 'p' ? 'j' : 'j2', p)]), job: vi.fn<RenderReadSource['job']>(async (_t, p, id) => job(id, p)) } }
function setup(value: RenderReadSource = source()) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const element = (s: RenderReadSource) => <StrictMode><QueryClientProvider client={client}><WorkspaceSessionProvider><RenderBrowser source={s} /></WorkspaceSessionProvider></QueryClientProvider></StrictMode>
  const view = render(element(value)); return { client, ...view, changeSource: (s: RenderReadSource) => view.rerender(element(s)) }
}
const select = async (id = 'p') => fireEvent.change(await screen.findByLabelText('Project'), { target: { value: id } })
const failure = (status: number) => ({ response: { status, data: { message: 'SECRET STACK TRACE' } } })
afterEach(() => { cleanup(); oidc.listeners.clear(); vi.restoreAllMocks() })

describe('Render core authorized read flow (explicit isolated sources)', () => {
  it('filters and sorts returned fields locally without more reads or carrying filters to another Project', async () => {
    const value = source(); value.jobs.mockResolvedValueOnce([job('z'), { ...job('a'), status: 'COMPLETED', profile: 'final' }])
    setup(value); await select(); await screen.findByRole('button', { name: 'Inspect a' })
    const rows = () => screen.queryAllByRole('button', { name: /^Inspect / }).map(row => row.textContent)
    expect(rows()).toEqual(['Inspect a', 'Inspect z'])
    fireEvent.change(screen.getByLabelText('Sort by job ID'), { target: { value: 'descending' } })
    expect(rows()).toEqual(['Inspect z', 'Inspect a'])
    fireEvent.change(screen.getByLabelText('Status'), { target: { value: 'COMPLETED' } })
    expect(rows()).toEqual(['Inspect a'])
    fireEvent.change(screen.getByLabelText('Search returned jobs'), { target: { value: 'missing' } })
    expect(screen.getByText('No returned jobs match these filters.')).toBeTruthy()
    expect(value.jobs).toHaveBeenCalledTimes(1)
    await select('p2'); await screen.findByRole('button', { name: 'Inspect j2' })
    expect((screen.getByLabelText('Status') as HTMLSelectElement).value).toBe('ALL')
  })
  it('rediscovers authorization after 403 and requires a fresh explicit Project selection', async () => {
    const value = source(); value.jobs.mockRejectedValueOnce(failure(403)); setup(value); await select()
    fireEvent.click(await screen.findByRole('button', { name: 'Reload authorized projects' }))
    expect((await screen.findByLabelText('Project') as HTMLSelectElement).value).toBe('')
    expect(value.jobs).toHaveBeenCalledTimes(1)
    const resolutionsBeforeSelection = value.project.mock.calls.length
    await select(); await screen.findByRole('button', { name: 'Inspect j' })
    expect(value.project.mock.calls.length).toBeGreaterThan(resolutionsBeforeSelection)
  })
  it('does not read jobs when Project resolution returns 404', async () => {
    const value = source(); value.project.mockRejectedValue(failure(404)); setup(value); await select()
    await screen.findByText('Project not found in the current scope.'); expect(value.jobs).not.toHaveBeenCalled()
    value.project.mockImplementation(async (_t, p) => project(p))
    fireEvent.click(screen.getByRole('button', { name: 'Retry job read' })); await screen.findByRole('button', { name: 'Inspect j' })
  })
  it('aborts route teardown reads and clears their cached records', async () => {
    const value = source(); let resolve!: (rows: ReturnType<typeof job>[]) => void
    value.jobs.mockImplementation(() => new Promise(done => { resolve = done }))
    const view = setup(value); await select(); await waitFor(() => expect(resolve).toBeDefined())
    const signal = value.jobs.mock.calls[0][2]; view.unmount()
    expect(signal.aborted).toBe(true); await act(async () => resolve([job('late-secret')]))
    expect(view.client.getQueryCache().getAll()).toHaveLength(0)
  })
  it('requires explicit Project selection, resolves before list, and displays only actual detail fields', async () => {
    const value = source(); setup(value); await screen.findByLabelText('Project')
    expect(value.project).not.toHaveBeenCalled(); expect(value.jobs).not.toHaveBeenCalled()
    await select(); const row = await screen.findByRole('button', { name: 'Inspect j' })
    expect(value.project).toHaveBeenCalledWith('t', 'p', expect.any(AbortSignal))
    expect(value.jobs).toHaveBeenCalledWith('t', 'p', expect.any(AbortSignal))
    fireEvent.click(row); await within(screen.getByRole('dialog')).findByText('snapshot')
    expect(value.job).toHaveBeenCalledWith('t', 'p', 'j', expect.any(AbortSignal))
    expect(screen.getByText(/Progress, times, failure details and Artifact availability are not provided/)).toBeTruthy()
    expect(screen.queryByRole('link', { name: /artifact/i })).toBeNull()
  })
  it('distinguishes empty discovery and empty authorized job results', async () => {
    const value = source(); value.projects.mockResolvedValue([]); setup(value)
    await screen.findByText('No authorized projects were returned.'); expect(value.jobs).not.toHaveBeenCalled()
    cleanup(); const empty = source(); empty.jobs.mockResolvedValue([]); setup(empty); await select()
    await screen.findByText('No Render jobs were returned for this authorized Project.')
  })
  it.each([401, 403])('clears records, selection and caches when detail returns %i', async status => {
    const value = source(), { client } = setup(value); await select(); fireEvent.click(await screen.findByRole('button', { name: 'Inspect j' })); await screen.findByText('snapshot')
    value.job.mockRejectedValue(failure(status)); fireEvent.click(screen.getByRole('button', { name: 'Refresh detail' }))
    await screen.findByRole('alert'); await waitFor(() => expect(screen.queryByText('snapshot')).toBeNull())
    expect(screen.queryByRole('button', { name: 'Inspect j' })).toBeNull(); expect(screen.queryByLabelText('Project')).toBeNull()
    expect(client.getQueryCache().getAll().filter(q => q.queryKey[1] === 'render').every(q => q.state.data === undefined)).toBe(true)
    expect(screen.queryByText('SECRET STACK TRACE')).toBeNull()
  })
  it('handles detail 404 by removing the old detail and unavailable selection/row', async () => {
    const value = source(); setup(value); await select(); fireEvent.click(await screen.findByRole('button', { name: 'Inspect j' })); await screen.findByText('snapshot')
    value.job.mockRejectedValue(failure(404)); fireEvent.click(screen.getByRole('button', { name: 'Refresh detail' }))
    await screen.findByText('This Render job is no longer available in this Project.')
    expect(screen.queryByText('snapshot')).toBeNull(); expect(screen.queryByRole('button', { name: 'Inspect j' })).toBeNull()
  })
  it('retries recoverable list/detail reads without exposing response error bodies', async () => {
    const value = source(); value.jobs.mockRejectedValueOnce(failure(503)); setup(value); await select()
    fireEvent.click(await screen.findByRole('button', { name: 'Retry job read' })); fireEvent.click(await screen.findByRole('button', { name: 'Inspect j' })); await screen.findByText('snapshot')
    value.job.mockRejectedValueOnce(failure(503)); fireEvent.click(screen.getByRole('button', { name: 'Refresh detail' }))
    fireEvent.click(await screen.findByRole('button', { name: 'Retry detail read' })); await screen.findByText('snapshot'); expect(screen.queryByText('SECRET STACK TRACE')).toBeNull()
  })
  it('cancels a Project read and rejects its late response after a Project switch', async () => {
    const value = source(); let resolve!: (rows: ReturnType<typeof job>[]) => void
    value.jobs.mockImplementationOnce(() => new Promise(done => { resolve = done })); setup(value); await select(); await waitFor(() => expect(resolve).toBeDefined())
    const signal = value.jobs.mock.calls[0][2] as AbortSignal
    await select('p2'); await screen.findByRole('button', { name: 'Inspect j2' }); expect(signal.aborted).toBe(true)
    await act(async () => resolve([job('late-secret')]))
    expect(screen.queryByRole('button', { name: 'Inspect late-secret' })).toBeNull()
  })
  it('retires in-flight responses on OIDC logout and route teardown', async () => {
    const value = source(); let resolve!: (rows: ReturnType<typeof job>[]) => void
    value.jobs.mockImplementation(() => new Promise(done => { resolve = done })); const view = setup(value); await select(); await waitFor(() => expect(resolve).toBeDefined())
    const signal = value.jobs.mock.calls[0][2] as AbortSignal
    act(() => oidc.listeners.forEach(listener => listener())); expect(signal.aborted).toBe(true)
    await act(async () => resolve([job('late-secret')])); expect(screen.queryByRole('button', { name: 'Inspect late-secret' })).toBeNull(); view.unmount()
  })
})
