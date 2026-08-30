import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { RouterProvider, createMemoryHistory, createRouter } from '@tanstack/react-router'
import { render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { platformClient } from '../foundation/platformClient'
import api, { platformClient as exportedPlatformClient } from '../api'
import { surfaceRegistry } from '../foundation/surfaceRegistry'
import { implementedRouteInventory, legacyRouteInventory, routeTree } from './routeTree'

describe('runtime route registration and deep-link restoration', () => {
  afterEach(() => vi.restoreAllMocks())

  it('registers every implemented page and preserved legacy link exactly once', () => {
    expect(new Set(implementedRouteInventory).size).toBe(implementedRouteInventory.length)
    expect(new Set(legacyRouteInventory).size).toBe(legacyRouteInventory.length)
    for (const surface of surfaceRegistry) expect(implementedRouteInventory).toContain(surface.routeTemplate as typeof implementedRouteInventory[number])
    const registered = (routeTree.children ?? []).map(child => (child.options as { path?: string }).path)
    for (const path of [...implementedRouteInventory, ...legacyRouteInventory]) expect(registered).toContain(path)
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
    expect(screen.getByText('Launch film')).toBeTruthy()
    expect(screen.getByText(/server cannot yet verify the Workspace-to-Project relationship/i)).toBeTruthy()
    expect((screen.getByRole('button', { name: 'Create semantic relationship' }) as HTMLButtonElement).disabled).toBe(true)
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

  it('preserves the historical default API export alongside the additive platform client', () => {
    expect(api.defaults.baseURL).toBe('/api/v1')
    expect(exportedPlatformClient).toBe(platformClient)
  })
})
