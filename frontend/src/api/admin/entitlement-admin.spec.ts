import { describe, it, expect, vi, beforeEach } from 'vitest'
import { EntitlementAdminAPI } from './entitlement-admin'
import api from '../index'

vi.mock('../index', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
    interceptors: { request: { use: vi.fn() }, response: { use: vi.fn() } }
  }
}))

describe('EntitlementAdminAPI', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('getBundles calls correct endpoint', async () => {
    vi.mocked(api.get).mockResolvedValueOnce({ data: [] })
    await EntitlementAdminAPI.getBundles()
    expect(api.get).toHaveBeenCalledWith('/admin/entitlements/bundles', { params: {} })
  })

  it('getBundles with status filter', async () => {
    vi.mocked(api.get).mockResolvedValueOnce({ data: [] })
    await EntitlementAdminAPI.getBundles('ACTIVE')
    expect(api.get).toHaveBeenCalledWith('/admin/entitlements/bundles', { params: { status: 'ACTIVE' } })
  })

  it('createBundle posts correctly', async () => {
    const bundle = { name: 'Pro Bundle', description: 'Pro features', tier: 'PRO', features: [], quota: {}, status: 'DRAFT' as const }
    vi.mocked(api.post).mockResolvedValueOnce({ data: { bundleId: 'b1', ...bundle } })
    const result = await EntitlementAdminAPI.createBundle(bundle)
    expect(api.post).toHaveBeenCalledWith('/admin/entitlements/bundles', bundle)
    expect(result.bundleId).toBe('b1')
  })

  it('archiveBundle posts correctly', async () => {
    vi.mocked(api.post).mockResolvedValueOnce({})
    await EntitlementAdminAPI.archiveBundle('b1')
    expect(api.post).toHaveBeenCalledWith('/admin/entitlements/bundles/b1/archive')
  })

  it('getTenantOverrides calls correct endpoint', async () => {
    vi.mocked(api.get).mockResolvedValueOnce({ data: [] })
    await EntitlementAdminAPI.getTenantOverrides('t1')
    expect(api.get).toHaveBeenCalledWith('/admin/entitlements/overrides', { params: { tenantId: 't1' } })
  })

  it('getUserGrants calls correct endpoint', async () => {
    vi.mocked(api.get).mockResolvedValueOnce({ data: [] })
    await EntitlementAdminAPI.getUserGrants('u1')
    expect(api.get).toHaveBeenCalledWith('/admin/entitlements/grants', { params: { userId: 'u1' } })
  })

  it('grantUserEntitlement posts correctly', async () => {
    const grant = { userId: 'u1', userEmail: 'u@test.com', featureKey: 'gpu', featureName: 'GPU', granted: true, reason: 'test', createdBy: 'admin' }
    vi.mocked(api.post).mockResolvedValueOnce({ data: { grantId: 'g1', ...grant } })
    const result = await EntitlementAdminAPI.grantUserEntitlement(grant)
    expect(api.post).toHaveBeenCalledWith('/admin/entitlements/grants', grant)
    expect(result.grantId).toBe('g1')
  })

  it('getQuotaPolicies calls correct endpoint', async () => {
    vi.mocked(api.get).mockResolvedValueOnce({ data: [] })
    await EntitlementAdminAPI.getQuotaPolicies('TIER', 'PRO')
    expect(api.get).toHaveBeenCalledWith('/admin/entitlements/quota-policies', { params: { scope: 'TIER', scopeId: 'PRO' } })
  })

  it('deleteQuotaPolicy calls delete', async () => {
    vi.mocked(api.delete).mockResolvedValueOnce({})
    await EntitlementAdminAPI.deleteQuotaPolicy('qp1')
    expect(api.delete).toHaveBeenCalledWith('/admin/entitlements/quota-policies/qp1')
  })
})
