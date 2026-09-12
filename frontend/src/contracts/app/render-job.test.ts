import { describe, expect, it } from 'vitest'
import { afterEach, vi } from 'vitest'
import api from '../../api'
import { renderReadSource as source } from '../../api/render-jobs'
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


const project = { id: 'p', tenantId: 't', name: 'Film', description: null, status: 'ACTIVE', createdAt: '2026-09-13T00:00:00Z' }
const job = { id: 'j', projectId: 'p', timelineSnapshotId: 's', profile: 'preview', status: 'FAILED' }
afterEach(() => vi.restoreAllMocks())
describe('accepted Render read adapter', () => {
  it('uses authenticated transport with explicit /api paths, URL encoding and AbortSignal', async () => {
    const get = vi.spyOn(api, 'get').mockResolvedValueOnce({ data: { tenantId: 't', recentProjects: [{ id: 'not-a-grant' }] } })
      .mockResolvedValueOnce({ data: [project] }).mockResolvedValueOnce({ data: project }).mockResolvedValueOnce({ data: [job] }).mockResolvedValueOnce({ data: job })
    const signal = new AbortController().signal
    expect(await source.tenant(signal)).toBe('t')
    expect(await source.projects('t', signal)).toEqual([project])
    expect(await source.project('t', 'p', signal)).toEqual(project)
    expect(await source.jobs('t', 'p', signal)).toEqual([job])
    expect(await source.job('t', 'p', 'j', signal)).toEqual(job)
    expect(get.mock.calls.map(call => call[0])).toEqual(['/api/me/dashboard', '/api/identity/tenants/t/projects', '/api/identity/projects/p', '/api/tenants/t/projects/p/render-jobs', '/api/tenants/t/projects/p/render-jobs/j'])
    for (const call of get.mock.calls) expect(call[1]).toEqual({ baseURL: '', signal })
    get.mockResolvedValue({ data: { ...job, id: 'job/one' } })
    await source.job('t', 'p', 'job/one', signal)
    expect(get).toHaveBeenLastCalledWith('/api/tenants/t/projects/p/render-jobs/job%2Fone', { baseURL: '', signal })
  })
  it.each([401, 403, 404, 503])('preserves the original HTTP %i failure', async status => {
    const error = { response: { status, data: { secret: 'not-user-facing' } } }
    vi.spyOn(api, 'get').mockRejectedValue(error)
    await expect(source.job('t', 'p', 'j', new AbortController().signal)).rejects.toBe(error)
  })
  it('accepts all nine exact job statuses and rejects aliases, extra fields and incomplete records', async () => {
    const get = vi.spyOn(api, 'get')
    for (const status of RENDER_JOB_STATUSES) { get.mockResolvedValue({ data: { ...job, status } }); expect((await source.job('t', 'p', 'j', new AbortController().signal)).status).toBe(status) }
    for (const invalid of [{ ...job, status: 'PROCESSING' }, { ...job, status: 'READY' }, { ...job, progress: 99 }, { ...job, id: undefined }]) {
      get.mockResolvedValue({ data: invalid }); await expect(source.job('t', 'p', 'j', new AbortController().signal)).rejects.toThrow()
    }
  })
  it('rejects foreign scope, duplicate IDs, unverified Project fields and missing tenant', async () => {
    const get = vi.spyOn(api, 'get'), signal = new AbortController().signal
    for (const data of [[{ ...project, tenantId: 'foreign' }], [project, project], [{ ...project, permission: true }], [{ ...project, createdAt: 'yesterday' }]]) {
      get.mockResolvedValue({ data }); await expect(source.projects('t', signal)).rejects.toThrow()
    }
    for (const data of [[{ ...job, projectId: 'foreign' }], [job, job]]) { get.mockResolvedValue({ data }); await expect(source.jobs('t', 'p', signal)).rejects.toThrow() }
    get.mockResolvedValue({ data: { ...job, id: 'other' } }); await expect(source.job('t', 'p', 'j', signal)).rejects.toThrow()
    get.mockResolvedValue({ data: { ...project, id: 'other' } }); await expect(source.project('t', 'p', signal)).rejects.toThrow()
    get.mockResolvedValue({ data: { tenantId: null } }); await expect(source.tenant(signal)).rejects.toThrow()
  })
  it('accepts empty authorized subsets without fallback and rejects a late aborted response', async () => {
    vi.spyOn(api, 'get').mockResolvedValue({ data: [] }); const abort = new AbortController()
    expect(await source.projects('t', abort.signal)).toEqual([]); expect(await source.jobs('t', 'p', abort.signal)).toEqual([])
    abort.abort(); await expect(source.jobs('t', 'p', abort.signal)).rejects.toThrow()
  })
})
