import { describe, it, expect, vi, beforeEach } from 'vitest'
import { MeEntitlementAPI } from './me'
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

describe('MeEntitlementAPI', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('getMyCapabilities calls correct endpoint', async () => {
    const mockData = { tier: 'PRO', entitlementPolicy: {}, exportCapabilities: {}, providerAccess: {}, featureFlags: [] }
    vi.mocked(api.get).mockResolvedValueOnce({ data: mockData })
    const result = await MeEntitlementAPI.getMyCapabilities()
    expect(api.get).toHaveBeenCalledWith('/entitlements/me/capabilities')
    expect(result).toEqual(mockData)
  })

  it('getUsageSummary calls correct endpoint', async () => {
    const mockData = { tenantId: 't1', userId: 'u1', period: '2026-05', renderMinutesUsed: 10, renderMinutesLimit: 100 }
    vi.mocked(api.get).mockResolvedValueOnce({ data: mockData })
    const result = await MeEntitlementAPI.getUsageSummary()
    expect(api.get).toHaveBeenCalledWith('/entitlements/me/usage')
    expect(result).toEqual(mockData)
  })

  it('getBillingHistory calls with pagination', async () => {
    const mockData = { entries: [], total: 0 }
    vi.mocked(api.get).mockResolvedValueOnce({ data: mockData })
    await MeEntitlementAPI.getBillingHistory(1, 50)
    expect(api.get).toHaveBeenCalledWith('/billing/me/history', { params: { page: 1, size: 50 } })
  })

  it('getCreditBalance calls correct endpoint', async () => {
    const mockData = { walletId: 'w1', subjectId: 't1', subjectType: 'TENANT', balance: 100, currency: 'USD', heldBalance: 0 }
    vi.mocked(api.get).mockResolvedValueOnce({ data: mockData })
    const result = await MeEntitlementAPI.getCreditBalance()
    expect(api.get).toHaveBeenCalledWith('/billing/me/credits')
    expect(result).toEqual(mockData)
  })

  it('getCurrentPlan calls correct endpoint', async () => {
    const mockData = { planId: 'p1', name: 'Pro', tier: 'PRO', monthlyPrice: 29, currency: 'USD' }
    vi.mocked(api.get).mockResolvedValueOnce({ data: mockData })
    const result = await MeEntitlementAPI.getCurrentPlan()
    expect(api.get).toHaveBeenCalledWith('/billing/me/plan')
    expect(result).toEqual(mockData)
  })

  it('getUpgradeOptions calls correct endpoint', async () => {
    const mockData = [{ targetTier: 'TEAM', targetPlanId: 'p2', monthlyPrice: 99, currency: 'USD' }]
    vi.mocked(api.get).mockResolvedValueOnce({ data: mockData })
    const result = await MeEntitlementAPI.getUpgradeOptions()
    expect(api.get).toHaveBeenCalledWith('/billing/me/upgrades')
    expect(result).toEqual(mockData)
  })
})
