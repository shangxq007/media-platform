import { StrictMode } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { RouterProvider, createMemoryHistory, createRouter } from '@tanstack/react-router'
import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { routeTree } from '../../app/routeTree'
import api from '../../api'
import { platformClient, type WorkspaceHomeProjection } from '../../foundation/platformClient'

const retirement = vi.hoisted(() => ({ listeners: new Set<() => void>() }))
vi.mock('../../auth/oidcClient', async original => ({ ...await original<typeof import('../../auth/oidcClient')>(), subscribeOidcSessionRetirement: (listener: () => void) => { retirement.listeners.add(listener); return () => retirement.listeners.delete(listener) } }))
const home = (workspaceId = 'w'): WorkspaceHomeProjection => ({ workspace: { id: workspaceId, name: 'Workspace' }, tenantId: workspaceId, recentProjects: [
  { id: 'a', name: 'Alpha', description: 'Alpha description', status: 'ACTIVE', createdAt: '2026-01-01' },
  { id: 'b', name: 'Beta', status: 'ARCHIVED', createdAt: null },
] })
function deferred<T>() { let resolve!: (value: T) => void; let reject!: (reason: unknown) => void; const promise = new Promise<T>((yes, no) => { resolve = yes; reject = no }); return { promise, resolve, reject } }
function mount() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const router = createRouter({ routeTree, history: createMemoryHistory({ initialEntries: ['/w/w/projects'] }), context: { queryClient: client } })
  render(<StrictMode><QueryClientProvider client={client}><RouterProvider router={router} /></QueryClientProvider></StrictMode>)
  return { client, router }
}
afterEach(() => { cleanup(); retirement.listeners.clear(); vi.restoreAllMocks() })

describe('authenticated recent project browsing route', () => {
  it('uses getHome through the real shared transport; accepts nullable createdAt and rejects Workspace mismatch', async () => {
    const get = vi.spyOn(api, 'get').mockResolvedValue({ data: { ...home(), timestamp: 'now' } })
    mount()
    await screen.findByRole('button', { name: 'Summary: Alpha' })
    expect(get).toHaveBeenCalledWith('/api/me/dashboard', { baseURL: '', signal: expect.any(AbortSignal) })
    expect(screen.getByText(/up to 5/)).toBeTruthy()
    expect(screen.getAllByRole('button', { name: 'Open project' }).every(button => (button as HTMLButtonElement).disabled)).toBe(true)
    await expect(platformClient.workspace.getHome('another')).rejects.toMatchObject({ name: 'WORKSPACE_SCOPE_NOT_AVAILABLE' })
  })
  it('composes filters, distinguishes no matches, and clears active conditions', async () => {
    vi.spyOn(platformClient.workspace, 'getHome').mockResolvedValue(home())
    mount(); await screen.findByRole('button', { name: 'Summary: Alpha' })
    fireEvent.change(screen.getByRole('searchbox'), { target: { value: 'beta' } })
    fireEvent.change(screen.getByLabelText('Status', { selector: 'select' }), { target: { value: 'ACTIVE' } })
    expect(screen.getByText('No matching recent projects')).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: 'Clear filters' }))
    expect(screen.getAllByRole('button', { name: /^Summary:/ })).toHaveLength(2)
  })
  it('restores conditions, selection and clamped scroll after navigation away/back, with fresh data', async () => {
    const get = vi.spyOn(platformClient.workspace, 'getHome').mockResolvedValue(home())
    const { router } = mount(); await screen.findByRole('button', { name: 'Summary: Alpha' })
    fireEvent.change(screen.getByRole('searchbox'), { target: { value: 'alp' } })
    fireEvent.change(screen.getByLabelText('Status', { selector: 'select' }), { target: { value: 'ACTIVE' } })
    fireEvent.change(screen.getByLabelText('Sort recent projects'), { target: { value: 'name-desc' } })
    const launcher = screen.getByRole('button', { name: 'Summary: Alpha' }); launcher.focus(); fireEvent.click(launcher)
    const dialog = screen.getByRole('dialog'); expect(dialog.contains(document.activeElement)).toBe(true)
    fireEvent.keyDown(dialog, { key: 'Escape' }); expect(document.activeElement).toBe(launcher)
    const scroller = screen.getByRole('main')
    vi.spyOn(HTMLElement.prototype, 'scrollHeight', 'get').mockReturnValue(1000)
    vi.spyOn(HTMLElement.prototype, 'clientHeight', 'get').mockReturnValue(300)
    scroller.scrollTop = 450; fireEvent.scroll(scroller)
    await act(async () => { await router.navigate({ to: '/operations/overview' }) })
    await screen.findByRole('heading', { name: 'Operations overview' })
    await act(async () => { await router.navigate({ to: '/w/$workspaceId/projects', params: { workspaceId: 'w' } }) })
    await screen.findByRole('button', { name: 'Summary: Alpha' })
    expect((screen.getByRole('searchbox') as HTMLInputElement).value).toBe('alp')
    expect((screen.getByLabelText('Status', { selector: 'select' }) as HTMLSelectElement).value).toBe('ACTIVE')
    expect((screen.getByLabelText('Sort recent projects') as HTMLSelectElement).value).toBe('name-desc')
    expect(screen.getByText('Selected: Alpha')).toBeTruthy()
    expect(screen.getByRole('main').scrollTop).toBe(450)
    expect(get.mock.calls.length).toBeGreaterThan(1)
    vi.spyOn(HTMLElement.prototype, 'scrollHeight', 'get').mockReturnValue(350)
    fireEvent.click(screen.getByRole('button', { name: 'Refresh' }))
    await waitFor(() => expect(screen.getByRole('main').scrollTop).toBe(50))
  })
  it('shows initial failure and retry, then refresh failure without losing the last view', async () => {
    const get = vi.spyOn(platformClient.workspace, 'getHome').mockRejectedValue(new Error('offline'))
    mount(); await screen.findByRole('button', { name: 'Retry' })
    expect(screen.queryByText('No recent projects')).toBeNull()
    get.mockResolvedValue(home())
    fireEvent.click(screen.getByRole('button', { name: 'Retry' })); await screen.findByRole('button', { name: 'Summary: Alpha' })
    get.mockRejectedValueOnce(new Error('offline'))
    fireEvent.click(screen.getByRole('button', { name: 'Refresh' })); await screen.findByText(/Showing the last successful response/)
    expect(screen.getByRole('button', { name: 'Summary: Alpha' })).toBeTruthy()
  })
  it('retires superseded refreshes, keeps selection if present and removes summary when absent', async () => {
    const get = vi.spyOn(platformClient.workspace, 'getHome').mockResolvedValue(home())
    mount(); const launcher = await screen.findByRole('button', { name: 'Summary: Alpha' }); fireEvent.click(launcher)
    fireEvent.click(screen.getByRole('button', { name: 'Refresh' }))
    await waitFor(() => expect(screen.queryByText('Refreshing recent projects…')).toBeNull())
    expect(screen.getByRole('dialog')).toBeTruthy()
    const first = deferred<WorkspaceHomeProjection>(), second = deferred<WorkspaceHomeProjection>()
    get.mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise)
    fireEvent.click(screen.getByRole('button', { name: 'Refresh' })); await screen.findByText('Refreshing recent projects…')
    fireEvent.click(screen.getByRole('button', { name: 'Refresh' }))
    await act(async () => { second.resolve({ ...home(), recentProjects: [home().recentProjects[1]] }) })
    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull())
    await act(async () => { first.resolve(home()) })
    expect(screen.queryByRole('button', { name: 'Summary: Alpha' })).toBeNull()
    expect(screen.queryByRole('button', { name: 'Clear selection' })).toBeNull()
    expect(document.activeElement).toBe(screen.getByRole('searchbox'))
  })
  it('clears data and summary on access invalidation and ignores a retired session request', async () => {
    const get = vi.spyOn(platformClient.workspace, 'getHome').mockResolvedValue(home())
    const { client } = mount(); fireEvent.click(await screen.findByRole('button', { name: 'Summary: Alpha' }))
    get.mockRejectedValueOnce({ response: { status: 403 } })
    fireEvent.click(screen.getByRole('button', { name: 'Refresh' }))
    await screen.findByText('Workspace access unavailable')
    expect(screen.queryByRole('dialog')).toBeNull(); expect(screen.queryByText('Alpha')).toBeNull()
    expect(client.getQueriesData({ queryKey: ['platform', 'workspace'] }).every(([, data]) => data === undefined)).toBe(true)
  })
  it('rejects an in-flight principal session result, including after leaving the Projects route', async () => {
    const pending = deferred<WorkspaceHomeProjection>()
    vi.spyOn(platformClient.workspace, 'getHome').mockReturnValue(pending.promise)
    const { router } = mount(); await screen.findByRole('searchbox')
    expect(screen.getByLabelText('Loading recent projects')).toBeTruthy()
    await act(async () => { await router.navigate({ to: '/operations/overview' }) })
    act(() => { retirement.listeners.forEach(listener => listener()) })
    await act(async () => { pending.resolve(home()); await router.navigate({ to: '/w/$workspaceId/projects', params: { workspaceId: 'w' } }) })
    await screen.findByText('Workspace access unavailable')
    expect(screen.queryByRole('button', { name: 'Summary: Alpha' })).toBeNull()
  })
  it('starts a fresh browsing binding on Workspace switch and drops the old pending result', async () => {
    const pending = deferred<WorkspaceHomeProjection>()
    const get = vi.spyOn(platformClient.workspace, 'getHome').mockResolvedValue(home())
    const { router } = mount(); await screen.findByRole('searchbox')
    await screen.findByRole('button', { name: 'Summary: Alpha' })
    fireEvent.change(screen.getByRole('searchbox'), { target: { value: 'private query' } })
    get.mockImplementation(id => id === 'w' ? pending.promise : Promise.resolve({ ...home(id), recentProjects: [] }))
    fireEvent.click(screen.getByRole('button', { name: 'Refresh' }))
    await act(async () => { await router.navigate({ to: '/w/$workspaceId/projects', params: { workspaceId: 'new' } }) })
    await screen.findByText('No recent projects')
    await act(async () => { pending.resolve(home()) })
    expect((screen.getByRole('searchbox') as HTMLInputElement).value).toBe('')
    expect(screen.queryByRole('button', { name: 'Summary: Alpha' })).toBeNull()
    expect(within(screen.getByRole('main')).queryByText('Alpha description')).toBeNull()
  })
})
