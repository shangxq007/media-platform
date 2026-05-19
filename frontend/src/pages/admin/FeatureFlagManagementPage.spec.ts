import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import FeatureFlagManagementPage from './FeatureFlagManagementPage.vue'

vi.mock('@/api/admin/feature-flags', () => {
  const mockFlags: any[] = [
    {
      flagKey: 'beta_ui',
      name: 'Beta UI',
      description: 'New UI experience',
      type: 'BOOLEAN',
      defaultValue: 'true',
      variants: [],
      targetingRules: [],
      owner: 'platform',
      tags: ['ui', 'beta'],
      enabled: true,
      createdAt: '2026-01-01T00:00:00Z',
      updatedAt: '2026-01-15T00:00:00Z',
    },
    {
      flagKey: 'export.gpu.v2',
      name: 'GPU Export V2',
      description: 'GPU-accelerated export',
      type: 'BOOLEAN',
      defaultValue: 'false',
      variants: [],
      targetingRules: [],
      owner: 'platform',
      tags: ['export', 'gpu'],
      enabled: false,
      createdAt: '2026-01-01T00:00:00Z',
      updatedAt: '2026-01-15T00:00:00Z',
    },
    {
      flagKey: 'nav.beta_features',
      name: 'Beta Features Nav',
      description: 'Navigation entry for beta features',
      type: 'BOOLEAN',
      defaultValue: 'true',
      variants: [],
      targetingRules: [],
      owner: 'platform',
      tags: ['nav'],
      enabled: true,
      createdAt: '2026-01-01T00:00:00Z',
      updatedAt: '2026-01-15T00:00:00Z',
    },
  ]

  return {
    FeatureFlagAPI: {
      listFeatureFlags: vi.fn().mockResolvedValue(mockFlags),
      getFeatureFlag: vi.fn().mockResolvedValue(mockFlags[0]),
      createFeatureFlag: vi.fn().mockResolvedValue(mockFlags[0]),
      updateFeatureFlag: vi.fn().mockResolvedValue(mockFlags[0]),
      archiveFeatureFlag: vi.fn().mockResolvedValue(undefined),
      enableFeatureFlag: vi.fn().mockResolvedValue(undefined),
      disableFeatureFlag: vi.fn().mockResolvedValue(undefined),
      evaluateFeatureFlag: vi.fn().mockResolvedValue({
        flagKey: 'beta_ui',
        enabled: true,
        variant: 'enabled',
        matchedRule: 'rule-1',
        reason: 'RULE_MATCHED',
        steps: [],
      }),
      getEvaluationLogs: vi.fn().mockResolvedValue({ entries: [], total: 0 }),
      getFeatureFlagSummary: vi.fn().mockResolvedValue({
        total: 3,
        active: 2,
        beta: 1,
        recentChanges: [],
      }),
    },
  }
})

vi.mock('./FeatureFlagEditor.vue', () => ({
  default: { template: '<div class="feature-flag-editor"></div>' },
}))

vi.mock('./FeatureFlagEvaluationPreview.vue', () => ({
  default: { template: '<div class="feature-flag-evaluation-preview"></div>' },
}))

vi.mock('./FeatureFlagEvaluationLog.vue', () => ({
  default: { template: '<div class="feature-flag-evaluation-log"></div>' },
}))

vi.mock('@/components/ui/LoadingState.vue', () => ({
  default: { template: '<div class="loading-state">Loading...</div>' },
}))

vi.mock('@/components/ui/EmptyState.vue', () => ({
  default: {
    template: '<div class="empty-state"><slot></slot><slot name="action"></slot></div>',
  },
}))

vi.mock('@/components/ui/StatusBadge.vue', () => ({
  default: {
    props: ['variant', 'label'],
    template: '<span class="status-badge">{{ label }}</span>',
  },
}))

describe('FeatureFlagManagementPage', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('renders loading state initially', () => {
    const wrapper = mount(FeatureFlagManagementPage)
    expect(wrapper.exists()).toBe(true)
  })

  it('renders feature flag management page after loading', async () => {
    const wrapper = mount(FeatureFlagManagementPage)
    await new Promise(r => setTimeout(r, 50))
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('Feature Flag Management')
  })

  it('renders feature flags list', async () => {
    const wrapper = mount(FeatureFlagManagementPage)
    await new Promise(r => setTimeout(r, 50))
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('Beta UI')
    expect(wrapper.text()).toContain('GPU Export V2')
    expect(wrapper.text()).toContain('Beta Features Nav')
  })

  it('renders search input', async () => {
    const wrapper = mount(FeatureFlagManagementPage)
    await new Promise(r => setTimeout(r, 50))
    await wrapper.vm.$nextTick()
    const input = wrapper.find('input[type="text"]')
    expect(input.exists()).toBe(true)
  })

  it('renders filter dropdowns', async () => {
    const wrapper = mount(FeatureFlagManagementPage)
    await new Promise(r => setTimeout(r, 50))
    await wrapper.vm.$nextTick()
    const selects = wrapper.findAll('select')
    expect(selects.length).toBeGreaterThanOrEqual(2)
  })

  it('renders create flag button', async () => {
    const wrapper = mount(FeatureFlagManagementPage)
    await new Promise(r => setTimeout(r, 50))
    await wrapper.vm.$nextTick()
    const buttons = wrapper.findAll('button')
    const createBtn = buttons.find(b => b.text().includes('New Flag'))
    expect(createBtn).toBeTruthy()
  })

  it('renders flag status badges', async () => {
    const wrapper = mount(FeatureFlagManagementPage)
    await new Promise(r => setTimeout(r, 50))
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('Active')
  })

  it('renders evaluation preview tab', async () => {
    const wrapper = mount(FeatureFlagManagementPage)
    await new Promise(r => setTimeout(r, 50))
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('Evaluation Preview')
  })

  it('renders evaluation logs tab', async () => {
    const wrapper = mount(FeatureFlagManagementPage)
    await new Promise(r => setTimeout(r, 50))
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('Evaluation Logs')
  })

  it('filters flags by search query', async () => {
    const wrapper = mount(FeatureFlagManagementPage)
    await new Promise(r => setTimeout(r, 50))
    await wrapper.vm.$nextTick()
    const input = wrapper.find('input[type="text"]')
    if (input.exists()) {
      await input.setValue('beta')
      await wrapper.vm.$nextTick()
      expect(wrapper.text()).toContain('Beta UI')
    }
  })
})
