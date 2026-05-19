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
        { flagKey: 'export.gpu.v2', displayName: 'GPU Export V2', enabled: false, scope: 'USER', targetTier: 'TEAM', description: 'GPU-accelerated export pipeline' },
        { flagKey: 'nav.beta_features', displayName: 'Beta Features Nav', enabled: true, scope: 'USER', targetTier: 'PRO', description: 'Navigation entry for beta features panel' },
      ],
    }),
  },
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

  it('renders capabilities page after loading', async () => {
    const wrapper = mount(MyCapabilitiesPage)
    await new Promise(r => setTimeout(r, 10))
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('Capabilities')
  })

  it('shows feature flag availability', async () => {
    const wrapper = mount(MyCapabilitiesPage)
    await new Promise(r => setTimeout(r, 10))
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('Feature Flags')
    expect(wrapper.text()).toContain('Beta UI')
    expect(wrapper.text()).toContain('GPU Export V2')
  })

  it('shows enabled feature flags with correct status', async () => {
    const wrapper = mount(MyCapabilitiesPage)
    await new Promise(r => setTimeout(r, 10))
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('Beta UI')
    expect(wrapper.text()).toContain('Beta Features Nav')
  })

  it('shows disabled feature flags', async () => {
    const wrapper = mount(MyCapabilitiesPage)
    await new Promise(r => setTimeout(r, 10))
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('GPU Export V2')
  })

  it('renders tier badge', async () => {
    const wrapper = mount(MyCapabilitiesPage)
    await new Promise(r => setTimeout(r, 10))
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('PRO')
  })

  it('renders export formats count', async () => {
    const wrapper = mount(MyCapabilitiesPage)
    await new Promise(r => setTimeout(r, 10))
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('2')
  })

  it('renders render minutes quota', async () => {
    const wrapper = mount(MyCapabilitiesPage)
    await new Promise(r => setTimeout(r, 10))
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('600')
  })

  it('renders concurrent jobs limit', async () => {
    const wrapper = mount(MyCapabilitiesPage)
    await new Promise(r => setTimeout(r, 10))
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('2')
  })

  it('renders refresh button', async () => {
    const wrapper = mount(MyCapabilitiesPage)
    await new Promise(r => setTimeout(r, 10))
    await wrapper.vm.$nextTick()
    const buttons = wrapper.findAll('button')
    const refreshBtn = buttons.find(b => b.text().includes('Refresh'))
    expect(refreshBtn).toBeTruthy()
  })
})
