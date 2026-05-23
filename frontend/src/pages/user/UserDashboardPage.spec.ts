import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import UserDashboardPage from './UserDashboardPage.vue'

vi.mock('@/api/me', () => ({
  MeEntitlementAPI: {
    getDashboard: vi.fn().mockResolvedValue({
      tenantId: 't1',
      userId: 'u1',
      timestamp: '2026-05-19T00:00:00Z',
      workspace: { id: 't1', name: 'Test Tenant', status: 'ACTIVE', role: 'ADMIN' },
      capabilities: {
        tier: 'PRO',
        monthlyRenderMinutes: 600,
        maxConcurrentJobs: 2,
        gpuAllowed: false,
        remoteWorkerAllowed: false,
        customFontsAllowed: true,
        watermark: false,
        allowedExportFormats: ['mp4', 'webm'],
        allowedPresets: ['default_720p', 'default_1080p'],
        exportFormats: ['mp4', 'webm'],
        exportPresets: ['default_720p', 'default_1080p'],
        maxExportResolutionWidth: 1920,
        maxExportResolutionHeight: 1080,
        gpuExportAllowed: false,
        maxConcurrentExports: 2,
      },
      featureFlags: [
        { flagKey: 'beta_ui', displayName: 'Beta UI', enabled: true, description: 'New UI' },
        { flagKey: 'experimental_ai', displayName: 'AI Assistant', enabled: false, description: 'AI-powered editing' },
      ],
      recentProjects: [],
      quickActions: [
        { key: 'new_project', label: 'New Project', icon: '➕', path: '/project/new', enabled: true, visible: true },
        { key: 'upload_media', label: 'Upload Media', icon: '📁', path: '/', enabled: true, visible: true },
      ],
      usage: {
        period: '2026-05',
        renderMinutesUsed: 120,
        renderMinutesLimit: 600,
        storageGbUsed: 5.2,
        storageGbLimit: 50,
        apiCallsUsed: 340,
        apiCallsLimit: 10000,
        exportsUsed: 8,
        exportsLimit: 100,
      },
      onboarding: {
        hasProjects: false,
        hasCompletedProfile: true,
        hasInvitedTeamMembers: false,
        hasCompletedFirstExport: false,
        hasSetBilling: false,
      },
    }),
    getMyCapabilities: vi.fn().mockResolvedValue({
      tenantId: 't1',
      userId: 'u1',
      tier: 'PRO',
      entitlementPolicy: {
        policyId: 'ep1',
        tier: 'PRO',
        maxResolutionWidth: 1920,
        maxResolutionHeight: 1080,
        monthlyRenderMinutes: 600,
        watermark: false,
        allowedProviders: ['ffmpeg'],
        gpuAllowed: false,
        remoteWorkerAllowed: false,
        maxSubtitleTracks: 3,
        customFontsAllowed: true,
        effectPacksAllowed: [],
        exportFormats: ['mp4', 'webm'],
        maxConcurrentJobs: 2,
      },
      exportCapabilities: {
        policyId: 'ec1',
        tier: 'PRO',
        allowedFormats: ['mp4', 'webm'],
        allowedPresets: ['default_720p', 'default_1080p'],
        maxResolutionWidth: 1920,
        maxResolutionHeight: 1080,
        watermarkRequired: false,
        gpuExportAllowed: false,
        remoteExportAllowed: false,
        maxConcurrentExports: 2,
      },
      providerAccess: {
        policyId: 'pa1',
        tier: 'PRO',
        allowedProviders: ['ffmpeg'],
        gpuAllowed: false,
        remoteWorkerAllowed: false,
        allowedGpuPresets: [],
      },
      featureFlags: [
        { flagKey: 'beta_ui', displayName: 'Beta UI', enabled: true, scope: 'USER', targetTier: 'PRO', description: 'New UI' },
        { flagKey: 'experimental_ai', displayName: 'AI Assistant', enabled: false, scope: 'USER', targetTier: 'TEAM', description: 'AI-powered editing' },
      ],
    }),
    getUsageSummary: vi.fn().mockResolvedValue({
      tenantId: 't1',
      userId: 'u1',
      period: '2026-05',
      renderMinutesUsed: 120,
      renderMinutesLimit: 600,
      storageGbUsed: 5.2,
      storageGbLimit: 50,
      apiCallsUsed: 340,
      apiCallsLimit: 10000,
      exportsUsed: 8,
      exportsLimit: 100,
      lastUpdatedAt: '2026-05-16T12:00:00Z',
    }),
    getCreditBalance: vi.fn().mockResolvedValue({
      walletId: 'w1',
      subjectId: 't1',
      subjectType: 'TENANT',
      balance: 29.99,
      currency: 'USD',
      heldBalance: 0,
    }),
  },
}))

describe('UserDashboardPage', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('renders non-editor modules for normal user', async () => {
    const router = createRouter({ history: createWebHistory(), routes: [] })
    const wrapper = mount(UserDashboardPage, {
      global: { plugins: [router] },
    })
    await new Promise(r => setTimeout(r, 50))
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('Dashboard')
  })

  it('renders feature flags section with correct count', async () => {
    const router = createRouter({ history: createWebHistory(), routes: [] })
    const wrapper = mount(UserDashboardPage, {
      global: { plugins: [router] },
    })
    await new Promise(r => setTimeout(r, 50))
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('Beta UI')
  })

  it('renders usage metrics on dashboard', async () => {
    const router = createRouter({ history: createWebHistory(), routes: [] })
    const wrapper = mount(UserDashboardPage, {
      global: { plugins: [router] },
    })
    await new Promise(r => setTimeout(r, 50))
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('120 / 600')
  })

  it('renders capability summary', async () => {
    const router = createRouter({ history: createWebHistory(), routes: [] })
    const wrapper = mount(UserDashboardPage, {
      global: { plugins: [router] },
    })
    await new Promise(r => setTimeout(r, 50))
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('PRO')
  })

  it('renders loading state initially', () => {
    const router = createRouter({ history: createWebHistory(), routes: [] })
    const wrapper = mount(UserDashboardPage, {
      global: { plugins: [router] },
    })
    expect(wrapper.text()).toContain('Loading')
  })

  it('shows enabled feature flags with correct badge', async () => {
    const router = createRouter({ history: createWebHistory(), routes: [] })
    const wrapper = mount(UserDashboardPage, {
      global: { plugins: [router] },
    })
    await new Promise(r => setTimeout(r, 50))
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('Beta Features Available')
    expect(wrapper.text()).toContain('Beta UI')
  })

  it('renders storage usage metric', async () => {
    const router = createRouter({ history: createWebHistory(), routes: [] })
    const wrapper = mount(UserDashboardPage, {
      global: { plugins: [router] },
    })
    await new Promise(r => setTimeout(r, 50))
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('5.2 / 50')
  })

  it('renders API calls usage metric', async () => {
    const router = createRouter({ history: createWebHistory(), routes: [] })
    const wrapper = mount(UserDashboardPage, {
      global: { plugins: [router] },
    })
    await new Promise(r => setTimeout(r, 50))
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('340 / 10000')
  })
})
