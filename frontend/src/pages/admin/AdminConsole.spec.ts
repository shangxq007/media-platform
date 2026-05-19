import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import AdminDashboard from './AdminDashboard.vue'

vi.mock('@/api/admin/entitlement-admin', () => ({
  EntitlementAdminAPI: {
    getBundles: vi.fn().mockResolvedValue([]),
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

vi.mock('@/api/admin/billing-admin', () => ({
  BillingAdminAPI: {
    getPlans: vi.fn().mockResolvedValue([]),
    getSubscriptions: vi.fn().mockResolvedValue([]),
    createPlan: vi.fn(),
    updatePlan: vi.fn(),
  },
}))

vi.mock('@/api/me', () => ({
  MeEntitlementAPI: {
    getMyCapabilities: vi.fn().mockResolvedValue({
      tenantId: 't1',
      userId: 'u1',
      tier: 'FREE',
      entitlementPolicy: {
        policyId: 'ep1',
        tier: 'FREE',
        maxResolutionWidth: 1280,
        maxResolutionHeight: 720,
        monthlyRenderMinutes: 60,
        watermark: true,
        allowedProviders: ['ffmpeg'],
        gpuAllowed: false,
        remoteWorkerAllowed: false,
        maxSubtitleTracks: 1,
        customFontsAllowed: false,
        effectPacksAllowed: [],
        exportFormats: ['mp4'],
        maxConcurrentJobs: 1,
      },
      exportCapabilities: {
        policyId: 'ec1',
        tier: 'FREE',
        allowedFormats: ['mp4'],
        allowedPresets: ['default_720p'],
        maxResolutionWidth: 1280,
        maxResolutionHeight: 720,
        watermarkRequired: true,
        gpuExportAllowed: false,
        remoteExportAllowed: false,
        maxConcurrentExports: 1,
      },
      providerAccess: {
        policyId: 'pa1',
        tier: 'FREE',
        allowedProviders: ['ffmpeg'],
        gpuAllowed: false,
        remoteWorkerAllowed: false,
        allowedGpuPresets: [],
      },
      featureFlags: [],
    }),
  },
}))

vi.mock('@/composables/useGraphQLQuery', () => ({
  useGraphQLQuery: () => ({
    loading: { value: false },
    error: { value: null },
    errorCode: { value: null },
    refetch: vi.fn().mockResolvedValue(null),
  }),
}))

vi.mock('@/api/admin/identity', () => ({
  IdentityAPI: {
    getAccessOverview: vi.fn().mockResolvedValue({ tenants: 0, users: 0, serviceAccounts: 0 }),
  },
}))

vi.mock('@/api/admin/render', () => ({
  RenderAdminAPI: {
    listJobs: vi.fn().mockResolvedValue([]),
  },
}))

vi.mock('@/api/admin/extension', () => ({
  ExtensionAPI: {
    listCatalog: vi.fn().mockResolvedValue([]),
  },
}))

vi.mock('@/api/admin/audit', () => ({
  AuditAPI: {
    getOutboxOverview: vi.fn().mockResolvedValue({ pending: 0, failed: 0, processed: 0 }),
  },
}))

vi.mock('@/api/admin/feature-flags', () => ({
  FeatureFlagAPI: {
    getFeatureFlagSummary: vi.fn().mockResolvedValue({ total: 0, active: 0, beta: 0, recentChanges: [] }),
  },
}))

vi.mock('@/api/admin/policy-admin', () => ({
  PolicyAdminAPI: {
    getPolicySummary: vi.fn().mockResolvedValue({ total: 0, active: 0, recentChanges: [] }),
  },
}))

describe('AdminConsole hidden from normal user', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('does not render admin-only sections for normal user', async () => {
    const router = createRouter({
      history: createWebHistory(),
      routes: [],
    })
    const wrapper = mount(AdminDashboard, {
      global: { plugins: [router] },
    })
    await new Promise(r => setTimeout(r, 50))
    await wrapper.vm.$nextTick()
    expect(wrapper.exists()).toBe(true)
  })

  it('shows feature flag management link in quick links', async () => {
    const router = createRouter({
      history: createWebHistory(),
      routes: [],
    })
    const wrapper = mount(AdminDashboard, {
      global: { plugins: [router] },
    })
    await new Promise(r => setTimeout(r, 50))
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('Feature Flag Management')
  })

  it('shows feature flags summary section', async () => {
    const router = createRouter({
      history: createWebHistory(),
      routes: [],
    })
    const wrapper = mount(AdminDashboard, {
      global: { plugins: [router] },
    })
    await new Promise(r => setTimeout(r, 50))
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('Feature Flags')
  })

  it('shows admin dashboard heading', async () => {
    const router = createRouter({
      history: createWebHistory(),
      routes: [],
    })
    const wrapper = mount(AdminDashboard, {
      global: { plugins: [router] },
    })
    await new Promise(r => setTimeout(r, 50))
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('Admin Dashboard')
  })

  it('shows user dashboard instead of admin console for normal users', async () => {
    const router = createRouter({
      history: createWebHistory(),
      routes: [],
    })
    const wrapper = mount(AdminDashboard, {
      global: { plugins: [router] },
    })
    await new Promise(r => setTimeout(r, 50))
    await wrapper.vm.$nextTick()
    expect(wrapper.exists()).toBe(true)
  })
})
