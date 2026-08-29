import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { RenderJobDashboard } from './RenderJobDashboard'
import { RenderResultDetailPage } from '../routes/app/renders/RenderResultDetailPage'
import { RenderResultsListPage } from '../routes/app/renders/RenderResultsListPage'

const hooks = vi.hoisted(() => ({
  useRenderWorkspaceScope: vi.fn(),
  useRenderJobs: vi.fn(),
  useRenderJob: vi.fn(),
  useProducts: vi.fn(),
  useProductDetail: vi.fn(),
}))

vi.mock('../api/render-jobs', () => ({
  useRenderWorkspaceScope: hooks.useRenderWorkspaceScope,
  useRenderJobs: hooks.useRenderJobs,
  useRenderJob: hooks.useRenderJob,
}))

vi.mock('../query/app/useProducts', () => ({
  useProducts: hooks.useProducts,
  useProductDetail: hooks.useProductDetail,
}))

const job = {
  id: 'job-1',
  projectId: 'project-1',
  timelineSnapshotId: 'snapshot-1',
  profile: 'preview',
  status: 'COMPLETED' as const,
}

describe('H4 bounded correction product surfaces', () => {
  beforeEach(() => {
    hooks.useRenderWorkspaceScope.mockReturnValue({ data: null, isLoading: false, error: null })
    hooks.useRenderJobs.mockReturnValue({ data: [], isLoading: false, error: null })
    hooks.useRenderJob.mockReturnValue({ data: null, error: null })
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

  it('fails the selected-job artifact list closed pending a scoped redacted summary', () => {
    hooks.useRenderWorkspaceScope.mockReturnValue({
      data: { tenantId: 'tenant-1', recentProjects: [{ id: 'project-1', name: 'Project' }] },
      isLoading: false,
      error: null,
    })
    hooks.useRenderJobs.mockReturnValue({ data: [job], isLoading: false, error: null })
    hooks.useRenderJob.mockImplementation((...args: unknown[]) => ({
      data: args[2] ? job : null,
      error: null,
    }))

    render(<RenderJobDashboard />)
    fireEvent.click(screen.getByText('job-1'))

    expect(screen.getByText('Artifacts unavailable')).toBeTruthy()
    expect(screen.getByText(/tenant\/project-scoped redacted artifact summary is required/i)).toBeTruthy()
  })
})
