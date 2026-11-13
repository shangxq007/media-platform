import { afterEach, expect, it, vi } from 'vitest'
import api from '../api'
import { platformClient } from './platformClient'

afterEach(() => vi.restoreAllMocks())

it('resolves NLE scope only from matching authenticated Workspace and Project reads', async () => {
  const get = vi.spyOn(api, 'get').mockImplementation(async path => ({ data: String(path).includes('/workspaces/')
    ? { id: 'workspace-1', tenantId: 'tenant-1' }
    : { id: 'project-1', tenantId: 'tenant-1', workspaceId: 'workspace-1', name: 'Video', status: 'ACTIVE' } }) as never)

  await expect(platformClient.project.getContext('workspace-1', 'project-1')).resolves.toEqual({ tenantId: 'tenant-1', name: 'Video' })
  expect(get.mock.calls.map(call => call[0])).toEqual(['/api/workspaces/workspace-1', '/api/identity/projects/project-1'])
})

it('rejects a Project from another Workspace before returning a tenant to the editor', async () => {
  vi.spyOn(api, 'get').mockImplementation(async path => ({ data: String(path).includes('/workspaces/')
    ? { id: 'workspace-1', tenantId: 'tenant-1' }
    : { id: 'project-1', tenantId: 'tenant-1', workspaceId: 'workspace-2', name: 'Foreign', status: 'ACTIVE' } }) as never)

  await expect(platformClient.project.getContext('workspace-1', 'project-1')).rejects.toMatchObject({ name: 'WORKSPACE_SCOPE_NOT_AVAILABLE' })
})
