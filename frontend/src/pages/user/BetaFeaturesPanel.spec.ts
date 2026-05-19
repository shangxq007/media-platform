import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import BetaFeaturesPanel from './BetaFeaturesPanel.vue'

describe('BetaFeaturesPanel', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('renders loading state when loading prop is true', () => {
    const wrapper = mount(BetaFeaturesPanel, {
      props: { loading: true, featureFlags: [] },
    })
    expect(wrapper.text()).toContain('Loading features...')
  })

  it('renders empty state when no features', () => {
    const wrapper = mount(BetaFeaturesPanel, {
      props: { featureFlags: [] },
    })
    expect(wrapper.text()).toContain('No beta features')
  })

  it('renders enabled and disabled features', () => {
    const wrapper = mount(BetaFeaturesPanel, {
      props: {
        featureFlags: [
          { flagKey: 'beta_ui', displayName: 'Beta UI', enabled: true, scope: 'USER', targetTier: 'PRO', description: 'New UI' },
          { flagKey: 'experimental_ai', displayName: 'AI Assistant', enabled: false, scope: 'USER', targetTier: 'TEAM', description: 'AI-powered editing' },
        ],
      },
    })
    expect(wrapper.text()).toContain('Beta Features')
    expect(wrapper.text()).toContain('Beta UI')
    expect(wrapper.text()).toContain('AI Assistant')
    expect(wrapper.text()).toContain('1 of 2 enabled')
  })

  it('renders all features enabled', () => {
    const wrapper = mount(BetaFeaturesPanel, {
      props: {
        featureFlags: [
          { flagKey: 'f1', displayName: 'Feature 1', enabled: true, scope: 'USER', targetTier: 'PRO', description: 'Desc 1' },
          { flagKey: 'f2', displayName: 'Feature 2', enabled: true, scope: 'USER', targetTier: 'PRO', description: 'Desc 2' },
        ],
      },
    })
    expect(wrapper.text()).toContain('2 of 2 enabled')
  })

  it('renders all features disabled', () => {
    const wrapper = mount(BetaFeaturesPanel, {
      props: {
        featureFlags: [
          { flagKey: 'f1', displayName: 'Feature 1', enabled: false, scope: 'USER', targetTier: 'PRO', description: 'Desc 1' },
          { flagKey: 'f2', displayName: 'Feature 2', enabled: false, scope: 'USER', targetTier: 'PRO', description: 'Desc 2' },
        ],
      },
    })
    expect(wrapper.text()).toContain('0 of 2 enabled')
  })

  it('emits toggle-beta event when toggling a flag', async () => {
    const wrapper = mount(BetaFeaturesPanel, {
      props: {
        featureFlags: [
          { flagKey: 'beta_ui', displayName: 'Beta UI', enabled: true, scope: 'USER', targetTier: 'PRO', description: 'New UI' },
        ],
      },
    })
    const buttons = wrapper.findAll('button')
    const toggleBtn = buttons.find(b => b.text().includes('Enabled') || b.text().includes('Enable'))
    expect(toggleBtn).toBeTruthy()
    if (toggleBtn) {
      await toggleBtn.trigger('click')
      expect(wrapper.emitted('toggle-beta')).toBeTruthy()
    }
  })

  it('shows risk badges for features', () => {
    const wrapper = mount(BetaFeaturesPanel, {
      props: {
        featureFlags: [
          { flagKey: 'beta_stable', displayName: 'Stable Beta', enabled: true, scope: 'USER', targetTier: 'PRO', description: 'Stable' },
          { flagKey: 'experimental_risky', displayName: 'Risky Exp', enabled: false, scope: 'USER', targetTier: 'TEAM', description: 'Risky' },
        ],
      },
    })
    expect(wrapper.text()).toContain('Medium Risk')
    expect(wrapper.text()).toContain('High Risk')
  })

  it('shows low risk for non-beta non-experimental features', () => {
    const wrapper = mount(BetaFeaturesPanel, {
      props: {
        featureFlags: [
          { flagKey: 'stable_feature', displayName: 'Stable', enabled: true, scope: 'USER', targetTier: 'PRO', description: 'Stable feature' },
        ],
      },
    })
    expect(wrapper.text()).toContain('Low Risk')
  })

  it('shows feature descriptions', () => {
    const wrapper = mount(BetaFeaturesPanel, {
      props: {
        featureFlags: [
          { flagKey: 'beta_ui', displayName: 'Beta UI', enabled: true, scope: 'USER', targetTier: 'PRO', description: 'New UI experience' },
        ],
      },
    })
    expect(wrapper.text()).toContain('New UI experience')
  })

  it('shows scope and tier info', () => {
    const wrapper = mount(BetaFeaturesPanel, {
      props: {
        featureFlags: [
          { flagKey: 'beta_ui', displayName: 'Beta UI', enabled: true, scope: 'USER', targetTier: 'PRO', description: 'New UI' },
        ],
      },
    })
    expect(wrapper.text()).toContain('Scope: USER')
    expect(wrapper.text()).toContain('Tier: PRO')
  })

  it('filters out INTERNAL tier features', () => {
    const wrapper = mount(BetaFeaturesPanel, {
      props: {
        featureFlags: [
          { flagKey: 'beta_ui', displayName: 'Beta UI', enabled: true, scope: 'USER', targetTier: 'PRO', description: 'New UI' },
          { flagKey: 'internal_tool', displayName: 'Internal', enabled: true, scope: 'USER', targetTier: 'INTERNAL', description: 'Internal tool' },
        ],
      },
    })
    expect(wrapper.text()).toContain('Beta UI')
    expect(wrapper.text()).not.toContain('Internal')
    expect(wrapper.text()).toContain('1 of 1 enabled')
  })

  it('displays beta warning info box', () => {
    const wrapper = mount(BetaFeaturesPanel, {
      props: {
        featureFlags: [
          { flagKey: 'beta_ui', displayName: 'Beta UI', enabled: true, scope: 'USER', targetTier: 'PRO', description: 'New UI' },
        ],
      },
    })
    expect(wrapper.text()).toContain('Beta features are experimental')
  })
})
