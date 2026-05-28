import { describe, it, expect, vi, beforeEach } from 'vitest'

// Mock the api module
vi.mock('./index', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
  },
}))

import api from './index'
import { IncrementalRenderAPI } from './render-incremental'

describe('IncrementalRenderAPI', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('previewPlan sends tenantId as path param', async () => {
    vi.mocked(api.post).mockResolvedValue({ data: { mode: 'incremental' } })

    await IncrementalRenderAPI.previewPlan('t1', 'proj-1', {
      newTimelineJson: '{}',
    })

    expect(api.post).toHaveBeenCalledWith(
      '/tenants/t1/projects/proj-1/render-jobs/incremental/plan',
      expect.any(Object)
    )
  })

  it('submitJob sends tenantId in path and body', async () => {
    vi.mocked(api.post).mockResolvedValue({ data: { jobId: 'j1', status: 'QUEUED' } })

    await IncrementalRenderAPI.submitJob('t1', 'proj-1', {
      tenantId: 't1',
      projectId: 'proj-1',
    })

    expect(api.post).toHaveBeenCalledWith(
      '/tenants/t1/projects/proj-1/render-jobs/incremental/submit',
      expect.objectContaining({ tenantId: 't1', projectId: 'proj-1' })
    )
  })

  it('listJobs URL contains tenantId', async () => {
    vi.mocked(api.get).mockResolvedValue({ data: [] })

    await IncrementalRenderAPI.listJobs('t1', 'proj-1')

    expect(api.get).toHaveBeenCalledWith(
      '/tenants/t1/projects/proj-1/render-jobs'
    )
  })

  it('getJob URL contains tenantId', async () => {
    vi.mocked(api.get).mockResolvedValue({ data: { id: 'j1' } })

    await IncrementalRenderAPI.getJob('t1', 'proj-1', 'j1')

    expect(api.get).toHaveBeenCalledWith(
      '/tenants/t1/projects/proj-1/render-jobs/j1'
    )
  })

  it('presignCache URL contains tenantId', async () => {
    vi.mocked(api.get).mockResolvedValue({ data: { entries: [] } })

    await IncrementalRenderAPI.presignCache('t1', 'proj-1', 'j1')

    expect(api.get).toHaveBeenCalledWith(
      '/tenants/t1/projects/proj-1/render-jobs/j1/cache/presign',
      expect.any(Object)
    )
  })

  it('cleanupExpiredCache URL contains tenantId', async () => {
    vi.mocked(api.post).mockResolvedValue({ data: { jobsScanned: 0, objectsDeleted: 0, jobsUpdated: 0 } })

    await IncrementalRenderAPI.cleanupExpiredCache('t1', 'proj-1')

    expect(api.post).toHaveBeenCalledWith(
      '/tenants/t1/projects/proj-1/render/cache/cleanup'
    )
  })

  it('getJobTimeline URL contains tenantId', async () => {
    vi.mocked(api.get).mockResolvedValue({ data: { timelineJson: '{}' } })

    await IncrementalRenderAPI.getJobTimeline('t1', 'proj-1', 'j1')

    expect(api.get).toHaveBeenCalledWith(
      '/tenants/t1/projects/proj-1/render-jobs/j1/timeline'
    )
  })
})
