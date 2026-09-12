import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createMemoryHistory, createRootRoute, createRouter, RouterProvider } from '@tanstack/react-router'
import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { ProductAppShell } from './AppShell'
import type { ProjectContextValue } from '../../foundation/projectContext'

const project: ProjectContextValue = {
  workspaceId: 'workspace-1', tenantId: null, projectId: 'project-1', project: { kind: 'PROJECT', id: 'project-1' },
  status: 'BLOCKED', reason: 'Scoped relationship unavailable.',
}

async function renderShell() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const routeTree = createRootRoute({ component: () => <ProductAppShell surfaceId="nle" workspaceId="workspace-1" project={project}><h1>Editor center</h1></ProductAppShell> })
  const router = createRouter({ routeTree, history: createMemoryHistory({ initialEntries: ['/'] }) })
  await router.load()
  return render(<QueryClientProvider client={queryClient}><RouterProvider router={router} /></QueryClientProvider>)
}

describe('shared application shell', () => {
  it('exposes keyboard-reachable navigation and named panel controls', async () => {
    await renderShell()
    expect(screen.getByRole('navigation', { name: 'Global navigation' })).toBeTruthy()
    expect(screen.getByRole('navigation', { name: 'Project surface switcher' })).toBeTruthy()
    const toggle = screen.getByRole('button', { name: 'Toggle asset browser' })
    expect(toggle.getAttribute('aria-pressed')).toBe('true')
    fireEvent.click(toggle)
    expect(toggle.getAttribute('aria-pressed')).toBe('false')
    expect(screen.getByRole('link', { name: /Canvas/ }).getAttribute('href'))
      .toBe('/w/workspace-1/projects/project-1/canvas')
    expect(screen.getByRole('link', { name: /Workflow/ }).getAttribute('href'))
      .toBe('/w/workspace-1/projects/project-1/workflow')
  })

  it('opens a palette whose protected commands remain disabled', async () => {
    await renderShell()
    fireEvent.keyDown(window, { key: 'k', ctrlKey: true })
    expect(screen.getByRole('dialog', { name: 'Command palette' })).toBeTruthy()
    expect((screen.getByRole('button', { name: 'Apply timeline operation' }) as HTMLButtonElement).disabled).toBe(true)
  })
})
