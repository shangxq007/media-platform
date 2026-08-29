import { describe, expect, it } from 'vitest'
import {
  ArtifactAccessDescriptor,
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

  it('accepts only redacted on-demand artifact access descriptors', () => {
    expect(ArtifactAccessDescriptor.parse({
      productId: null,
      artifactId: null,
      accessType: 'SIGNED_URL',
      method: 'GET',
      url: 'https://example.invalid/signed',
      expiresAt: '2026-08-29T00:05:00Z',
      ttlSeconds: 300,
      mimeType: 'video/mp4',
      filename: 'output.mp4',
      sizeBytes: 100,
      status: 'READY',
      message: null,
      redacted: true,
    }).redacted).toBe(true)

    expect(ArtifactAccessDescriptor.safeParse({
      accessType: 'SIGNED_URL',
      redacted: false,
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
