import { describe, expect, it } from 'vitest'
import {
  RENDER_JOB_STATUSES,
  RenderJobSummary,
  RenderWorkspaceScope,
} from './render-job'

describe('render application transport projections', () => {
  it('accepts every backend RenderJobStatus and rejects the retired PROCESSING alias', () => {
    for (const status of RENDER_JOB_STATUSES) {
      expect(RenderJobSummary.safeParse({
        id: 'job-1',
        projectId: 'project-1',
        timelineSnapshotId: 'snapshot-1',
        profile: 'default',
        status,
      }).success).toBe(true)
    }

    expect(RenderJobSummary.safeParse({
      id: 'job-1',
      projectId: 'project-1',
      timelineSnapshotId: 'snapshot-1',
      profile: 'default',
      status: 'PROCESSING',
    }).success).toBe(false)
  })

  it('projects authenticated workspace scope without consuming capability or tier payloads', () => {
    const scope = RenderWorkspaceScope.parse({
      tenantId: 'tenant-1',
      recentProjects: [{ id: 'project-1', name: 'Project', status: 'ACTIVE' }],
      capabilities: { tier: 'ENTERPRISE' },
    })

    expect(scope).toEqual({
      tenantId: 'tenant-1',
      recentProjects: [{ id: 'project-1', name: 'Project' }],
    })
  })
})
