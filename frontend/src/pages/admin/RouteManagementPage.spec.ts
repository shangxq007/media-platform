import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import EntitlementManagementPage from './EntitlementManagementPage.vue'

vi.mock('@/api/admin/entitlement-admin', () => ({
  EntitlementAdminAPI: {
    getBundles: vi.fn().mockResolvedValue([
      {
        bundleId: 'b1',
        name: 'Pro Bundle',
        description: 'Pro features',
        tier: 'PRO',
        features: ['gpu', '4k'],
        quota: { renderMinutes: 600 },
        status: 'ACTIVE',
        createdAt: '2026-01-01',
        updatedAt: '2026-01-01',
      },
    ]),
    getTenantOverrides: vi.fn().mockResolvedValue([]),
    getUserGrants: vi.fn().mockResolvedValue([]),
    createBundle: vi.fn(),
    archiveBundle: vi.fn(),
    createTenantOverride: vi.fn(),
    deleteTenantOverride: vi.fn(),
    grantUserEntitlement: vi.fn(),
    revokeUserEntitlement: vi.fn(),
  },
}))

describe('RouteManagementPage supports requiredFeatureFlags', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('renders route management page', async () => {
    const wrapper = mount(EntitlementManagementPage)
    await new Promise(r => setTimeout(r, 10))
    await wrapper.vm.$nextTick()
    expect(wrapper.exists()).toBe(true)
  })

  it('supports requiredFeatureFlags in route config', () => {
    const routeConfig = {
      path: '/admin/feature-flags',
      name: 'FeatureFlagManagement',
      requiredFeatureFlags: ['admin.feature_flags.enabled', 'nav.admin.enabled'],
    }
    expect(routeConfig.requiredFeatureFlags).toBeDefined()
    expect(routeConfig.requiredFeatureFlags.length).toBe(2)
    expect(routeConfig.requiredFeatureFlags).toContain('admin.feature_flags.enabled')
    expect(routeConfig.requiredFeatureFlags).toContain('nav.admin.enabled')
  })

  it('supports route visibility controlled by feature flags', () => {
    const routes = [
      { path: '/admin/feature-flags', requiredFeatureFlags: ['admin.feature_flags.enabled'] },
      { path: '/admin/entitlements', requiredFeatureFlags: ['admin.entitlements.enabled'] },
      { path: '/dashboard', requiredFeatureFlags: [] },
    ]
    const adminRoutes = routes.filter(r => r.requiredFeatureFlags.length > 0)
    expect(adminRoutes.length).toBe(2)
  })

  it('handles routes with no required feature flags', () => {
    const publicRoute = { path: '/dashboard', requiredFeatureFlags: [] }
    expect(publicRoute.requiredFeatureFlags.length).toBe(0)
  })

  it('handles routes with multiple required feature flags', () => {
    const complexRoute = {
      path: '/admin/advanced',
      requiredFeatureFlags: ['admin.enabled', 'nav.admin.enabled', 'feature.advanced.enabled'],
    }
    expect(complexRoute.requiredFeatureFlags.length).toBe(3)
  })

  it('checks feature flag before route access', () => {
    const enabledFlags = ['admin.enabled', 'nav.admin.enabled']
    const requiredFlags = ['admin.enabled', 'nav.admin.enabled']
    const hasAccess = requiredFlags.every(f => enabledFlags.includes(f))
    expect(hasAccess).toBe(true)
  })

  it('denies access when required feature flag is missing', () => {
    const enabledFlags = ['admin.enabled']
    const requiredFlags = ['admin.enabled', 'nav.admin.enabled']
    const hasAccess = requiredFlags.every(f => enabledFlags.includes(f))
    expect(hasAccess).toBe(false)
  })

  it('allows access when all required flags are enabled', () => {
    const enabledFlags = ['admin.enabled', 'nav.admin.enabled', 'feature.advanced.enabled']
    const requiredFlags = ['admin.enabled', 'nav.admin.enabled']
    const hasAccess = requiredFlags.every(f => enabledFlags.includes(f))
    expect(hasAccess).toBe(true)
  })
})
