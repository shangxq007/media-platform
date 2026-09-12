import { StrictMode } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { RouterProvider, createMemoryHistory, createRouter } from '@tanstack/react-router'
import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { platformClient } from '../foundation/platformClient'
import api, { platformClient as exportedPlatformClient } from '../api'
import { surfaceRegistry } from '../foundation/surfaceRegistry'
import { PublicationSourceProvider } from '../product/publication/PublicationWorkspace'
import { fixtureAccount, fixtureList, fixturePost, source } from '../product/publication/testing'
import { implementedRouteInventory, legacyRouteInventory, routeTree } from './routeTree'
import { timelineQueryGateway } from '../api/app/timeline-query.gateway'
import { contentHash, projectId, revisionId, timelineId } from '../product/timeline/types'
import { renderReadSource } from '../api/render-jobs'

const oidcRetirement = vi.hoisted(() => ({ listeners: new Set<() => void>() }))
vi.mock('../auth/oidcClient', async importOriginal => ({
  ...await importOriginal<typeof import('../auth/oidcClient')>(),
  subscribeOidcSessionRetirement: (listener: () => void) => {
    oidcRetirement.listeners.add(listener)
    return () => oidcRetirement.listeners.delete(listener)
  },
}))

describe('runtime route registration and deep-link restoration', () => {
  afterEach(() => {
    cleanup()
    oidcRetirement.listeners.clear()
    vi.restoreAllMocks()
  })

  it('registers every implemented page and preserved legacy link exactly once', () => {
    expect(new Set(implementedRouteInventory).size).toBe(implementedRouteInventory.length)
    expect(new Set(legacyRouteInventory).size).toBe(legacyRouteInventory.length)
    for (const surface of surfaceRegistry) expect(implementedRouteInventory).toContain(surface.routeTemplate as typeof implementedRouteInventory[number])
    const registered = (routeTree.children ?? []).map(child => (child.options as { path?: string }).path)
    for (const path of [...implementedRouteInventory, ...legacyRouteInventory]) expect(registered).toContain(path)
  })

  it('redirects the legacy Render entry to authorized discovery without choosing a Project', async () => {
    vi.spyOn(renderReadSource, 'tenant').mockResolvedValue('render-tenant')
    vi.spyOn(renderReadSource, 'projects').mockResolvedValue([])
    const jobs = vi.spyOn(renderReadSource, 'jobs')
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const router = createRouter({ routeTree, history: createMemoryHistory({ initialEntries: ['/render-jobs'] }), context: { queryClient } })
    render(<QueryClientProvider client={queryClient}><RouterProvider router={router} /></QueryClientProvider>)
    await screen.findByText('No authorized projects were returned.')
    expect(router.state.location.pathname).toBe('/operations/renders')
    expect(jobs).not.toHaveBeenCalled()
  })

  it('restores Workspace, Project, and surface identity from a creative deep link and fails unauthorized commands closed', async () => {
    vi.spyOn(platformClient.workspace, 'getHome').mockResolvedValue({
      workspace: { id: 'workspace-1', name: 'Editorial' }, tenantId: 'tenant-1',
      recentProjects: [{ id: 'project-1', name: 'Launch film' }],
    })
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const history = createMemoryHistory({ initialEntries: ['/w/workspace-1/projects/project-1/canvas'] })
    const router = createRouter({ routeTree, history, context: { queryClient } })
    render(<QueryClientProvider client={queryClient}><RouterProvider router={router} /></QueryClientProvider>)
    await waitFor(() => expect(screen.getByRole('heading', { name: 'Infinite canvas' })).toBeTruthy())
    expect(await within(screen.getByRole('navigation', { name: 'Breadcrumb' })).findByText('Launch film')).toBeTruthy()
    expect(screen.getByText(/server cannot yet verify the Workspace-to-Project relationship/i)).toBeTruthy()
    expect((screen.getByRole('button', { name: 'Create semantic relationship' }) as HTMLButtonElement).disabled).toBe(true)
    const canvas = screen.getByRole('region', { name: 'Infinite canvas workspace' })
    fireEvent.keyDown(canvas, { key: 'ArrowLeft' })
    const canvasStatus = screen.getByText((_content, element) => element?.classList.contains('ff-canvas-status') ?? false)
    expect(canvasStatus.textContent).toContain('Viewport: 24, 0')
  })

  it('remounts the ProjectFrame subtree when same-route Workspace or Project parameters change', async () => {
    vi.spyOn(platformClient.workspace, 'getHome').mockResolvedValue({
      workspace: { id: 'workspace-1', name: 'Editorial' }, tenantId: 'tenant-1',
      recentProjects: [{ id: 'project-1', name: 'First film' }, { id: 'project-2', name: 'Second film' }],
    })
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const history = createMemoryHistory({ initialEntries: ['/w/workspace-1/projects/project-1/canvas'] })
    const router = createRouter({ routeTree, history, context: { queryClient } })
    render(<QueryClientProvider client={queryClient}><RouterProvider router={router} /></QueryClientProvider>)
    const canvas = await screen.findByRole('region', { name: 'Infinite canvas workspace' })
    fireEvent.keyDown(canvas, { key: 'ArrowLeft' })
    expect(screen.getByText((_content, element) => element?.classList.contains('ff-canvas-status') ?? false).textContent).toContain('Viewport: 24, 0')

    await act(async () => {
      await router.navigate({
        to: '/w/$workspaceId/projects/$projectId/canvas',
        params: { workspaceId: 'workspace-1', projectId: 'project-2' },
      })
    })
    expect((await screen.findAllByText('Second film')).length).toBeGreaterThan(0)
    expect(screen.getByText((_content, element) => element?.classList.contains('ff-canvas-status') ?? false).textContent).toContain('Viewport: 0, 0')
    expect(screen.getByText('project-2')).toBeTruthy()
  })

  it('reaches the post-H7 edit route and loads explicit canonical HEAD authority', async () => {
    vi.spyOn(platformClient.workspace, 'getHome').mockResolvedValue({
      workspace: { id: 'workspace-1', name: 'Editorial' }, tenantId: 'tenant-1',
      recentProjects: [{ id: 'project-1', name: 'Launch film' }],
    })
    vi.spyOn(timelineQueryGateway, 'getHead').mockResolvedValue({ ok: true, value: {
      projectId: projectId('project-1'), timelineId: timelineId('project-1'),
      revisionId: revisionId('revision-R0'), contentHash: contentHash('a'.repeat(64)),
    } })
    vi.spyOn(timelineQueryGateway, 'listRevisions').mockResolvedValue({ ok: true, value: [] })
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const history = createMemoryHistory({ initialEntries: ['/w/workspace-1/projects/project-1/edit'] })
    const router = createRouter({ routeTree, history, context: { queryClient } })
    render(<QueryClientProvider client={queryClient}><RouterProvider router={router} /></QueryClientProvider>)
    await waitFor(() => expect(screen.getByRole('heading', { name: 'Timeline editor' })).toBeTruthy())
    expect(await screen.findByText('revision-R0')).toBeTruthy()
    expect(screen.getByText('ASSET GATEWAY · UNAVAILABLE')).toBeTruthy()
  })

  it('renders the Workspace to Projects entry without synthesizing a Project selection', async () => {
    vi.spyOn(platformClient.workspace, 'getHome').mockResolvedValue({
      workspace: { id: 'workspace-1', name: 'Editorial' }, tenantId: 'tenant-1',
      recentProjects: [{ id: 'project-1', name: 'Launch film' }],
    })
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const history = createMemoryHistory({ initialEntries: ['/w/workspace-1/home'] })
    const router = createRouter({ routeTree, history, context: { queryClient } })
    render(<QueryClientProvider client={queryClient}><RouterProvider router={router} /></QueryClientProvider>)
    await waitFor(() => expect(screen.getByRole('heading', { name: 'Editorial' })).toBeTruthy())
    expect(screen.getByText('Launch film')).toBeTruthy()
    expect(screen.getByRole('link', { name: 'View project list' }).getAttribute('href')).toBe('/w/workspace-1/projects')
    expect((screen.getByRole('button', { name: 'Open' }) as HTMLButtonElement).disabled).toBe(true)
  })

  it('handles Workspace API errors without inventing an empty state', async () => {
    vi.spyOn(platformClient.workspace, 'getHome').mockRejectedValue(new Error('offline'))
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const history = createMemoryHistory({ initialEntries: ['/w/workspace-1/home'] })
    const router = createRouter({ routeTree, history, context: { queryClient } })
    render(<QueryClientProvider client={queryClient}><RouterProvider router={router} /></QueryClientProvider>)
    await waitFor(() => expect(screen.getByRole('heading', { name: 'Workspace unavailable' })).toBeTruthy())
  })

  it('exposes the canonical typed platform client through the shared transport module', () => {
    expect(api.defaults.baseURL).toBe('/api/v1')
    expect(exportedPlatformClient).toBe(platformClient)
  })

  it('uses the authenticated Publication source, permits explicit fixture verification, and retires the active source on OIDC session retirement', async () => {
    const account = fixtureAccount({ projectId: 'project-1' })
    const post = fixturePost({ projectId: 'project-1' })
    vi.spyOn(platformClient.publication, 'getAccounts').mockResolvedValue([account])
    vi.spyOn(platformClient.publication, 'getPosts').mockResolvedValue(fixtureList({ items: [post] }))
    vi.spyOn(platformClient.publication, 'getPost').mockResolvedValue(post)
    vi.spyOn(platformClient.workspace, 'getHome').mockResolvedValue({
      workspace: { id: 'workspace-1', name: 'Editorial' }, tenantId: 'tenant-1', recentProjects: [],
    })
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false, staleTime: Infinity } } })
    const router = createRouter({
      routeTree,
      history: createMemoryHistory({ initialEntries: ['/w/workspace-1/projects/project-1/publication'] }),
      context: { queryClient },
    })
    const view = render(<StrictMode><QueryClientProvider client={queryClient}><RouterProvider router={router} /></QueryClientProvider></StrictMode>)

    await screen.findByRole('button', { name: 'Opening story' })
    expect(platformClient.publication.getAccounts).toHaveBeenCalledWith('project-1', expect.any(AbortSignal))
    expect(platformClient.publication.getPosts).toHaveBeenCalledWith(expect.objectContaining({
      projectId: 'project-1', connectedAccountId: 'a', bindingVersion: 4,
    }), expect.any(AbortSignal))
    expect(screen.getByRole('navigation', { name: 'Project surface switcher' }).querySelector('[aria-current="page"]')?.getAttribute('href')).toBe('/w/workspace-1/projects/project-1/publication')

    const injected = source({ accounts: [account], list: fixtureList({ items: [post] }), detail: post })
    view.rerender(<StrictMode><QueryClientProvider client={queryClient}><PublicationSourceProvider source={injected}><RouterProvider router={router} /></PublicationSourceProvider></QueryClientProvider></StrictMode>)
    const row = await screen.findByRole('button', { name: 'Opening story' })
    await waitFor(() => expect(injected.getPosts).toHaveBeenCalled())
    fireEvent.click(row)
    await screen.findByRole('dialog')
    const readsBeforeRetirement = injected.getPosts.mock.calls.length

    act(() => {
      for (const listener of oidcRetirement.listeners) listener()
    })
    expect(screen.queryByRole('dialog')).toBeNull()
    expect(screen.queryByRole('button', { name: 'Opening story' })).toBeNull()
    expect(injected.getPosts).toHaveBeenCalledTimes(readsBeforeRetirement)
  })
})
