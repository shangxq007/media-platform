import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import EntitlementManagementPage from './EntitlementManagementPage.vue'

vi.mock('@/api/admin/entitlement-admin', () => ({
  EntitlementAdminAPI: {
    getBundles: vi.fn().mockResolvedValue([
      { bundleId: 'b1', name: 'Pro Bundle', description: 'Pro features', tier: 'PRO', features: ['gpu', '4k'], quota: { renderMinutes: 600 }, status: 'ACTIVE', createdAt: '2026-01-01', updatedAt: '2026-01-01' }
    ]),
    getTenantOverrides: vi.fn().mockResolvedValue([
      { overrideId: 'o1', tenantId: 't1', tenantName: 'Acme', featureKey: 'gpu', featureName: 'GPU', overrideType: 'GRANT' as const, reason: 'VIP', createdBy: 'admin', createdAt: '2026-01-01' }
    ]),
    getUserGrants: vi.fn().mockResolvedValue([
      { grantId: 'g1', userId: 'u1', userEmail: 'u@test.com', featureKey: 'gpu', featureName: 'GPU', granted: true, reason: 'test', createdBy: 'admin', createdAt: '2026-01-01' }
    ]),
    createBundle: vi.fn(),
    archiveBundle: vi.fn(),
    createTenantOverride: vi.fn(),
    deleteTenantOverride: vi.fn(),
    grantUserEntitlement: vi.fn(),
    revokeUserEntitlement: vi.fn()
  }
}))

describe('EntitlementManagementPage', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('renders loading state initially', () => {
    const wrapper = mount(EntitlementManagementPage)
    expect(wrapper.text()).toContain('Loading...')
  })

  it('renders tabs for bundles, overrides, grants', async () => {
    const wrapper = mount(EntitlementManagementPage)
    await new Promise(r => setTimeout(r, 10))
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('Bundles')
    expect(wrapper.text()).toContain('Tenant Overrides')
    expect(wrapper.text()).toContain('User Grants')
  })

  it('shows bundle count in tab', async () => {
    const wrapper = mount(EntitlementManagementPage)
    await new Promise(r => setTimeout(r, 10))
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('Bundles (1)')
  })
})
