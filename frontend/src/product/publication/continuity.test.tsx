import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createMemoryHistory, createRouter, RouterProvider } from '@tanstack/react-router'
import { StrictMode } from 'react'
import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { routeTree } from '../../app/routeTree'
import { platformClient } from '../../foundation/platformClient'
import { PublicationSourceProvider } from './PublicationWorkspace'
import { fixtureAccount, fixtureList, fixturePost, source } from './testing'

const oidc = vi.hoisted(() => ({ listeners: new Set<() => void>() }))
vi.mock('../../auth/oidcClient', async original => ({ ...await original<typeof import('../../auth/oidcClient')>(), subscribeOidcSessionRetirement: (listener: () => void) => { oidc.listeners.add(listener); return () => oidc.listeners.delete(listener) } }))
function setup() {
  vi.spyOn(platformClient.workspace, 'getHome').mockImplementation(async id => ({ workspace: { id, name: 'Workspace' }, tenantId: 'tenant', recentProjects: [] }))
  const value = source()
  value.getPosts.mockImplementation(async request => fixtureList({ items: [fixturePost({ projectId: request.projectId, connectedAccountId: request.connectedAccountId, bindingVersion: request.bindingVersion, scheduledAt: request.start.replace('01T00:00:00.000', '10T12:00:00.000') })] }))
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const router = createRouter({ routeTree, history: createMemoryHistory({ initialEntries: ['/w/w/projects/p/publication'] }), context: { queryClient: client } })
  render(<StrictMode><QueryClientProvider client={client}><PublicationSourceProvider source={value}><RouterProvider router={router} /></PublicationSourceProvider></QueryClientProvider></StrictMode>)
  return { value, router }
}
const leave = async (router: ReturnType<typeof setup>['router']) => act(async () => { await router.navigate({ to: '/operations/overview' }) })
const back = async (router: ReturnType<typeof setup>['router'], projectId = 'p', workspaceId = 'w') => act(async () => { await router.navigate({ to: '/w/$workspaceId/projects/$projectId/publication', params: { workspaceId, projectId } }) })
afterEach(() => { cleanup(); oidc.listeners.clear(); vi.restoreAllMocks() })

describe('Publication route browsing continuity', () => {
  it('restores choices, day and selected record only after fresh exact-account reads, including Workspace hydration', async () => {
    const { value, router } = setup(); await screen.findByRole('button', { name: 'Opening story' })
    fireEvent.change(screen.getByRole('searchbox'), { target: { value: 'Opening' } })
    fireEvent.change(screen.getByLabelText('Artifact availability'), { target: { value: 'AVAILABLE' } })
    fireEvent.change(screen.getByLabelText('Sort by planned publish time'), { target: { value: 'desc' } })
    const launcher = screen.getByRole('button', { name: 'Opening story' }); launcher.focus(); fireEvent.click(launcher)
    await screen.findByText('Post ID'); fireEvent.click(screen.getByRole('button', { name: 'Close publication details' }))
    fireEvent.click(screen.getByRole('button', { name: 'Calendar' }))
    fireEvent.change(screen.getByLabelText('Display timezone'), { target: { value: 'Asia/Shanghai' } })
    const date = new Date().toISOString().slice(0, 7) + '-10'
    fireEvent.click(screen.getByRole('button', { name: date }))
    const calls = value.getPosts.mock.calls.length
    await leave(router); await back(router)
    await waitFor(() => expect(value.getPosts.mock.calls.length).toBeGreaterThan(calls))
    const row = await within(screen.getByRole('region', { name: 'Selected day agenda' })).findByRole('button', { name: 'Opening story' })
    expect((screen.getByRole('searchbox') as HTMLInputElement).value).toBe('Opening')
    expect((screen.getByLabelText('Artifact availability') as HTMLSelectElement).value).toBe('AVAILABLE')
    expect((screen.getByLabelText('Sort by planned publish time') as HTMLSelectElement).value).toBe('desc')
    expect((screen.getByLabelText('Display timezone') as HTMLSelectElement).value).toBe('Asia/Shanghai')
    expect(row.getAttribute('aria-pressed')).toBe('true')
    expect(screen.queryByRole('dialog')).toBeNull()
  })

  it('keeps distinct list/calendar scroll positions and clamps a shorter returned list', async () => {
    const { router } = setup(); await screen.findByRole('button', { name: 'Opening story' })
    vi.spyOn(HTMLElement.prototype, 'scrollHeight', 'get').mockReturnValue(1000)
    vi.spyOn(HTMLElement.prototype, 'clientHeight', 'get').mockReturnValue(200)
    const results = screen.getByRole('region', { name: 'Publication results' }); results.scrollTop = 300; fireEvent.scroll(results)
    fireEvent.click(screen.getByRole('button', { name: 'Calendar' }))
    results.scrollTop = 160; fireEvent.scroll(results)
    fireEvent.click(screen.getByRole('button', { name: 'List' }))
    expect(results.scrollTop).toBe(300)
    await leave(router); await back(router); await screen.findByRole('button', { name: 'Opening story' })
    expect(screen.getByRole('region', { name: 'Publication results' }).scrollTop).toBe(300)
    vi.spyOn(HTMLElement.prototype, 'scrollHeight', 'get').mockReturnValue(240)
    fireEvent.click(screen.getByRole('button', { name: 'Refresh publications' }))
    await waitFor(() => expect(screen.getByRole('region', { name: 'Publication results' }).scrollTop).toBe(40))
  })

  it('discards old account binding choices and selection when returning to a re-bound account', async () => {
    const { value, router } = setup(); await screen.findByRole('button', { name: 'Opening story' })
    fireEvent.change(screen.getByRole('searchbox'), { target: { value: 'private filter' } })
    await leave(router)
    value.getAccounts.mockResolvedValue([fixtureAccount({ bindingVersion: 5 })])
    await back(router); await screen.findByRole('button', { name: 'Opening story' })
    expect((screen.getByRole('searchbox') as HTMLInputElement).value).toBe('')
    expect(value.getPosts).toHaveBeenLastCalledWith(expect.objectContaining({ bindingVersion: 5 }), expect.any(AbortSignal))
  })

  it('does not resurrect selection after an empty verified return followed by the same ID reappearing', async () => {
    const { value, router } = setup(); const row = await screen.findByRole('button', { name: 'Opening story' })
    fireEvent.click(row); await screen.findByText('Post ID'); fireEvent.click(screen.getByRole('button', { name: 'Close publication details' }))
    await leave(router)
    value.getPosts.mockResolvedValue(fixtureList({ items: [] }))
    await back(router); await screen.findByText('No planned publications in this bounded window')
    await leave(router)
    value.getPosts.mockResolvedValue(fixtureList())
    await back(router)
    expect((await screen.findByRole('button', { name: 'Opening story' })).getAttribute('aria-pressed')).toBe('false')
  })

  it('clears browsing memory on session retirement while the Publication route is unmounted', async () => {
    const { value, router } = setup(); await screen.findByRole('button', { name: 'Opening story' })
    fireEvent.change(screen.getByRole('searchbox'), { target: { value: 'private filter' } })
    await leave(router); const reads = value.getAccounts.mock.calls.length
    act(() => oidc.listeners.forEach(listener => listener()))
    await back(router); await screen.findByText('Publication source not connected')
    expect(screen.queryByRole('searchbox')).toBeNull()
    expect(value.getAccounts).toHaveBeenCalledTimes(reads)
  })

  it('rejects an old pending account/list result across project change without restoring another project filters', async () => {
    const { value, router } = setup(); await screen.findByRole('button', { name: 'Opening story' })
    fireEvent.change(screen.getByRole('searchbox'), { target: { value: 'old project filter' } })
    let resolve!: (value: ReturnType<typeof fixtureList>) => void
    value.getPosts.mockImplementationOnce(() => new Promise(yes => { resolve = yes }))
    fireEvent.click(screen.getByRole('button', { name: 'Refresh publications' }))
    await waitFor(() => expect(resolve).toBeTypeOf('function'))
    value.getAccounts.mockResolvedValue([fixtureAccount({ projectId: 'p2' })])
    await back(router, 'p2'); await screen.findByRole('button', { name: 'Opening story' })
    await act(async () => resolve(fixtureList({ items: [fixturePost({ contentText: 'LATE_OLD_CONTENT' })] })))
    expect((screen.getByRole('searchbox') as HTMLInputElement).value).toBe('')
    expect(document.body.textContent).not.toContain('LATE_OLD_CONTENT')
  })
})
