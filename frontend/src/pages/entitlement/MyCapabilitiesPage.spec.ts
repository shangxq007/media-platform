import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import MyCapabilitiesPage from './MyCapabilitiesPage.vue'

vi.mock('@/api/me', () => ({
  MeEntitlementAPI: {
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
        maxConcurrentJobs: 2
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
        maxConcurrentExports: 2
      },
      providerAccess: {
        policyId: 'pa1',
        tier: 'PRO',
        allowedProviders: ['ffmpeg'],
        gpuAllowed: false,
        remoteWorkerAllowed: false,
        allowedGpuPresets: []
      },
      featureFlags: [
        { flagKey: 'beta_ui', displayName: 'Beta UI', enabled: true, scope: 'USER', targetTier: 'PRO', description: 'New UI' }
      ]
    }),
    getEntitlementExplanation: vi.fn().mockResolvedValue({
      featureKey: 'gpu_rendering',
      featureName: 'GPU Rendering',
      available: false,
      reason: 'Requires TEAM tier',
      currentTier: 'PRO',
      requiredTier: 'TEAM',
      upgradeOptions: [{ targetTier: 'TEAM', targetPlanId: 'p2', targetPlanName: 'Team', monthlyPrice: 99, annualPrice: 990, currency: 'USD', additionalFeatures: ['GPU'], additionalQuota: {} }],
      violations: ['Tier too low']
    })
  }
}))

describe('MyCapabilitiesPage', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('renders loading state initially', () => {
    const wrapper = mount(MyCapabilitiesPage)
    expect(wrapper.text()).toContain('Loading capabilities...')
  })

  it('renders capabilities after loading', async () => {
    const wrapper = mount(MyCapabilitiesPage)
    await new Promise(r => setTimeout(r, 10))
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('My Capabilities')
    expect(wrapper.text()).toContain('PRO')
  })

  it('renders feature flags section', async () => {
    const wrapper = mount(MyCapabilitiesPage)
    await new Promise(r => setTimeout(r, 10))
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('Feature Flags')
    expect(wrapper.text()).toContain('Beta UI')
  })

  it('renders entitlement policy section', async () => {
    const wrapper = mount(MyCapabilitiesPage)
    await new Promise(r => setTimeout(r, 10))
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('Entitlement Policy')
    expect(wrapper.text()).toContain('600')
  })
})
