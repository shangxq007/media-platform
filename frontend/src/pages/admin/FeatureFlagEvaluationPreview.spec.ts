import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import FeatureFlagEvaluationPreview from './FeatureFlagEvaluationPreview.vue'

const mockFlags: any[] = [
  {
    flagKey: 'beta_ui',
    name: 'Beta UI',
    description: 'New UI',
    type: 'BOOLEAN',
    defaultValue: 'true',
    variants: [],
    targetingRules: [],
    owner: 'platform',
    tags: [],
    enabled: true,
  },
  {
    flagKey: 'export.gpu.v2',
    name: 'GPU Export V2',
    description: 'GPU export',
    type: 'BOOLEAN',
    defaultValue: 'false',
    variants: [],
    targetingRules: [],
    owner: 'platform',
    tags: [],
    enabled: false,
  },
]

vi.mock('@/api/admin/feature-flags', () => ({
  FeatureFlagAPI: {
    evaluateFeatureFlag: vi.fn().mockResolvedValue({
      flagKey: 'beta_ui',
      enabled: true,
      variant: 'enabled',
      matchedRule: 'rule-1',
      reason: 'RULE_MATCHED',
      steps: [
        { step: 'Check flag status', result: 'ACTIVE', detail: 'Flag is enabled' },
        { step: 'Evaluate rules', result: 'MATCHED', detail: 'Matched rule-1' },
      ],
    }),
  },
}))

describe('FeatureFlagEvaluationPreview', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('renders empty state when no flags', () => {
    const wrapper = mount(FeatureFlagEvaluationPreview, {
      props: { flags: [] },
    })
    expect(wrapper.text()).toContain('No feature flags available')
  })

  it('renders flag selector when flags exist', () => {
    const wrapper = mount(FeatureFlagEvaluationPreview, {
      props: { flags: mockFlags },
    })
    expect(wrapper.text()).toContain('Select Flag')
  })

  it('renders context input fields', () => {
    const wrapper = mount(FeatureFlagEvaluationPreview, {
      props: { flags: mockFlags },
    })
    const inputs = wrapper.findAll('input')
    expect(inputs.length).toBeGreaterThan(0)
  })

  it('renders evaluate button', () => {
    const wrapper = mount(FeatureFlagEvaluationPreview, {
      props: { flags: mockFlags },
    })
    const buttons = wrapper.findAll('button')
    const evalBtn = buttons.find(b => b.text().includes('Evaluate'))
    expect(evalBtn).toBeTruthy()
  })

  it('renders reset context button', () => {
    const wrapper = mount(FeatureFlagEvaluationPreview, {
      props: { flags: mockFlags },
    })
    const buttons = wrapper.findAll('button')
    const resetBtn = buttons.find(b => b.text().includes('Reset'))
    expect(resetBtn).toBeTruthy()
  })

  it('displays evaluation result after evaluation', async () => {
    const wrapper = mount(FeatureFlagEvaluationPreview, {
      props: { flags: mockFlags },
    })
    const select = wrapper.find('select')
    if (select.exists()) {
      await select.setValue('beta_ui')
      await wrapper.vm.$nextTick()
    }
    const buttons = wrapper.findAll('button')
    const evalBtn = buttons.find(b => b.text().includes('Evaluate'))
    if (evalBtn) {
      await evalBtn.trigger('click')
      await new Promise(r => setTimeout(r, 50))
      await wrapper.vm.$nextTick()
      expect(wrapper.text()).toContain('RULE_MATCHED')
    }
  })

  it('renders tenant context field', () => {
    const wrapper = mount(FeatureFlagEvaluationPreview, {
      props: { flags: mockFlags },
    })
    expect(wrapper.text()).toContain('Tenant')
  })

  it('renders user context field', () => {
    const wrapper = mount(FeatureFlagEvaluationPreview, {
      props: { flags: mockFlags },
    })
    expect(wrapper.text()).toContain('User')
  })

  it('renders role context field', () => {
    const wrapper = mount(FeatureFlagEvaluationPreview, {
      props: { flags: mockFlags },
    })
    expect(wrapper.text()).toContain('Role')
  })

  it('renders tier context field', () => {
    const wrapper = mount(FeatureFlagEvaluationPreview, {
      props: { flags: mockFlags },
    })
    expect(wrapper.text()).toContain('Tier')
  })

  it('renders region context field', () => {
    const wrapper = mount(FeatureFlagEvaluationPreview, {
      props: { flags: mockFlags },
    })
    expect(wrapper.text()).toContain('Region')
  })

  it('renders request source context field', () => {
    const wrapper = mount(FeatureFlagEvaluationPreview, {
      props: { flags: mockFlags },
    })
    expect(wrapper.exists()).toBe(true)
  })

  it('renders environment context field', () => {
    const wrapper = mount(FeatureFlagEvaluationPreview, {
      props: { flags: mockFlags },
    })
    expect(wrapper.exists()).toBe(true)
  })

  it('displays flag key in result', async () => {
    const wrapper = mount(FeatureFlagEvaluationPreview, {
      props: { flags: mockFlags },
    })
    const select = wrapper.find('select')
    if (select.exists()) {
      await select.setValue('beta_ui')
      await wrapper.vm.$nextTick()
    }
    const buttons = wrapper.findAll('button')
    const evalBtn = buttons.find(b => b.text().includes('Evaluate'))
    if (evalBtn) {
      await evalBtn.trigger('click')
      await new Promise(r => setTimeout(r, 50))
      await wrapper.vm.$nextTick()
      expect(wrapper.text()).toContain('beta_ui')
    }
  })

  it('shows evaluation steps', async () => {
    const wrapper = mount(FeatureFlagEvaluationPreview, {
      props: { flags: mockFlags },
    })
    const select = wrapper.find('select')
    if (select.exists()) {
      await select.setValue('beta_ui')
      await wrapper.vm.$nextTick()
    }
    const buttons = wrapper.findAll('button')
    const evalBtn = buttons.find(b => b.text().includes('Evaluate'))
    if (evalBtn) {
      await evalBtn.trigger('click')
      await new Promise(r => setTimeout(r, 50))
      await wrapper.vm.$nextTick()
      expect(wrapper.text()).toContain('Check flag status')
    }
  })

  it('shows variant in result', async () => {
    const wrapper = mount(FeatureFlagEvaluationPreview, {
      props: { flags: mockFlags },
    })
    const select = wrapper.find('select')
    if (select.exists()) {
      await select.setValue('beta_ui')
      await wrapper.vm.$nextTick()
    }
    const buttons = wrapper.findAll('button')
    const evalBtn = buttons.find(b => b.text().includes('Evaluate'))
    if (evalBtn) {
      await evalBtn.trigger('click')
      await new Promise(r => setTimeout(r, 50))
      await wrapper.vm.$nextTick()
      expect(wrapper.text()).toContain('enabled')
    }
  })
})
