import { describe, it, expect, vi, beforeEach } from 'vitest'
import { WorkspaceEntitlementAPI } from './workspace'
import api from './index'

vi.mock('./index', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
    interceptors: { request: { use: vi.fn() }, response: { use: vi.fn() } }
  }
}))

describe('WorkspaceEntitlementAPI', () => {
  const wsId = 'ws-123'

  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('getMembers calls correct endpoint', async () => {
    vi.mocked(api.get).mockResolvedValueOnce({ data: [] })
    await WorkspaceEntitlementAPI.getMembers(wsId)
    expect(api.get).toHaveBeenCalledWith(`/workspace/${wsId}/members`)
  })

  it('getRoles calls correct endpoint', async () => {
    vi.mocked(api.get).mockResolvedValueOnce({ data: [] })
    await WorkspaceEntitlementAPI.getRoles(wsId)
    expect(api.get).toHaveBeenCalledWith(`/workspace/${wsId}/roles`)
  })

  it('createRole posts to correct endpoint', async () => {
    const role = { name: 'Editor', description: 'Can edit', permissions: ['READ', 'WRITE'] }
    vi.mocked(api.post).mockResolvedValueOnce({ data: { roleId: 'r1', ...role } })
    const result = await WorkspaceEntitlementAPI.createRole(wsId, role)
    expect(api.post).toHaveBeenCalledWith(`/workspace/${wsId}/roles`, role)
    expect(result.roleId).toBe('r1')
  })

  it('getEntitlementPool calls correct endpoint', async () => {
    vi.mocked(api.get).mockResolvedValueOnce({ data: [] })
    await WorkspaceEntitlementAPI.getEntitlementPool(wsId)
    expect(api.get).toHaveBeenCalledWith(`/workspace/${wsId}/entitlements/pool`)
  })

  it('grantMemberEntitlement posts correctly', async () => {
    vi.mocked(api.post).mockResolvedValueOnce({ data: { grantId: 'g1' } })
    await WorkspaceEntitlementAPI.grantMemberEntitlement(wsId, 'u1', 'gpu_rendering')
    expect(api.post).toHaveBeenCalledWith(`/workspace/${wsId}/entitlements/grants/member`, {
      memberId: 'u1', featureKey: 'gpu_rendering', expiresAt: undefined
    })
  })

  it('revokeMemberEntitlement calls delete', async () => {
    vi.mocked(api.delete).mockResolvedValueOnce({})
    await WorkspaceEntitlementAPI.revokeMemberEntitlement(wsId, 'g1')
    expect(api.delete).toHaveBeenCalledWith(`/workspace/${wsId}/entitlements/grants/g1`)
  })

  it('previewDecision posts correctly', async () => {
    vi.mocked(api.post).mockResolvedValueOnce({ data: { featureKey: 'gpu_rendering', granted: true } })
    await WorkspaceEntitlementAPI.previewDecision(wsId, 'u1', 'gpu_rendering')
    expect(api.post).toHaveBeenCalledWith(`/workspace/${wsId}/entitlements/preview`, {
      memberId: 'u1', featureKey: 'gpu_rendering'
    })
  })

  it('debugAccessDecision posts correctly', async () => {
    vi.mocked(api.post).mockResolvedValueOnce({ data: { requestId: 'req1', decision: 'GRANTED' } })
    await WorkspaceEntitlementAPI.debugAccessDecision(wsId, 'u1', 'gpu_rendering')
    expect(api.post).toHaveBeenCalledWith(`/workspace/${wsId}/entitlements/debug`, {
      memberId: 'u1', featureKey: 'gpu_rendering'
    })
  })

  it('updateQuotaAllocation puts correctly', async () => {
    vi.mocked(api.put).mockResolvedValueOnce({ data: { featureKey: 'render_minutes', totalQuota: 500 } })
    await WorkspaceEntitlementAPI.updateQuotaAllocation(wsId, 'render_minutes', 500)
    expect(api.put).toHaveBeenCalledWith(`/workspace/${wsId}/entitlements/quota/render_minutes`, { totalQuota: 500 })
  })
})
