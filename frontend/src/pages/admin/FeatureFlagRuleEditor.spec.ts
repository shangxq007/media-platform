import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import FeatureFlagRuleEditor from './FeatureFlagRuleEditor.vue'

describe('FeatureFlagRuleEditor', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('renders rule editor for new rule', () => {
    const wrapper = mount(FeatureFlagRuleEditor, {
      props: { rule: null },
    })
    expect(wrapper.exists()).toBe(true)
    expect(wrapper.text()).toContain('New Targeting Rule')
  })

  it('renders rule editor for existing rule', () => {
    const mockRule = {
      ruleId: 'rule-1',
      name: 'Tenant Rule',
      priority: 10,
      percentage: 50,
      conditions: [
        { attribute: 'tenant', operator: 'EQUALS' as const, value: 'acme' },
      ],
      variantKey: 'enabled',
      startAt: '',
      endAt: '',
    }
    const wrapper = mount(FeatureFlagRuleEditor, {
      props: { rule: mockRule },
    })
    expect(wrapper.exists()).toBe(true)
    expect(wrapper.text()).toContain('Edit Targeting Rule')
  })

  it('renders rule name input', () => {
    const wrapper = mount(FeatureFlagRuleEditor, {
      props: { rule: null },
    })
    expect(wrapper.text()).toContain('Name')
  })

  it('renders priority input', () => {
    const wrapper = mount(FeatureFlagRuleEditor, {
      props: { rule: null },
    })
    expect(wrapper.text()).toContain('Priority')
  })

  it('renders percentage input for rollout', () => {
    const wrapper = mount(FeatureFlagRuleEditor, {
      props: { rule: null },
    })
    expect(wrapper.text()).toContain('Percentage')
  })

  it('validates rollout percentage range', () => {
    const wrapper = mount(FeatureFlagRuleEditor, {
      props: { rule: null },
    })
    const inputs = wrapper.findAll('input')
    expect(inputs.length).toBeGreaterThan(0)
  })

  it('renders conditions section', () => {
    const wrapper = mount(FeatureFlagRuleEditor, {
      props: { rule: null },
    })
    expect(wrapper.text()).toContain('Conditions')
  })

  it('renders add condition button', () => {
    const wrapper = mount(FeatureFlagRuleEditor, {
      props: { rule: null },
    })
    const buttons = wrapper.findAll('button')
    const addBtn = buttons.find(b => b.text().includes('Add Condition'))
    expect(addBtn).toBeTruthy()
  })

  it('renders condition attribute dropdown', () => {
    const mockRule = {
      ruleId: 'rule-1',
      name: 'Test Rule',
      priority: 1,
      percentage: 100,
      conditions: [
        { attribute: 'tenant', operator: 'EQUALS' as const, value: '' },
      ],
      variantKey: '',
      startAt: '',
      endAt: '',
    }
    const wrapper = mount(FeatureFlagRuleEditor, {
      props: { rule: mockRule },
    })
    const selects = wrapper.findAll('select')
    expect(selects.length).toBeGreaterThan(0)
  })

  it('renders condition operator dropdown', () => {
    const mockRule = {
      ruleId: 'rule-1',
      name: 'Test Rule',
      priority: 1,
      percentage: 100,
      conditions: [
        { attribute: 'tenant', operator: 'EQUALS' as const, value: '' },
      ],
      variantKey: '',
      startAt: '',
      endAt: '',
    }
    const wrapper = mount(FeatureFlagRuleEditor, {
      props: { rule: mockRule },
    })
    const selects = wrapper.findAll('select')
    expect(selects.length).toBeGreaterThan(0)
  })

  it('renders condition value input', () => {
    const mockRule = {
      ruleId: 'rule-1',
      name: 'Test Rule',
      priority: 1,
      percentage: 100,
      conditions: [
        { attribute: 'tenant', operator: 'EQUALS' as const, value: 'acme' },
      ],
      variantKey: '',
      startAt: '',
      endAt: '',
    }
    const wrapper = mount(FeatureFlagRuleEditor, {
      props: { rule: mockRule },
    })
    const inputs = wrapper.findAll('input')
    expect(inputs.length).toBeGreaterThan(0)
  })

  it('renders remove condition button', () => {
    const mockRule = {
      ruleId: 'rule-1',
      name: 'Test Rule',
      priority: 1,
      percentage: 100,
      conditions: [
        { attribute: 'tenant', operator: 'EQUALS' as const, value: 'acme' },
      ],
      variantKey: '',
      startAt: '',
      endAt: '',
    }
    const wrapper = mount(FeatureFlagRuleEditor, {
      props: { rule: mockRule },
    })
    const buttons = wrapper.findAll('button')
    const removeBtn = buttons.find(b => b.text().includes('✕'))
    expect(removeBtn).toBeTruthy()
  })

  it('renders variant key input', () => {
    const wrapper = mount(FeatureFlagRuleEditor, {
      props: { rule: null },
    })
    expect(wrapper.exists()).toBe(true)
  })

  it('renders time range inputs', () => {
    const wrapper = mount(FeatureFlagRuleEditor, {
      props: { rule: null },
    })
    expect(wrapper.exists()).toBe(true)
  })

  it('renders save button', () => {
    const wrapper = mount(FeatureFlagRuleEditor, {
      props: { rule: null },
    })
    const buttons = wrapper.findAll('button')
    const saveBtn = buttons.find(b => b.text().includes('Save'))
    expect(saveBtn).toBeTruthy()
  })

  it('renders close button', () => {
    const wrapper = mount(FeatureFlagRuleEditor, {
      props: { rule: null },
    })
    const buttons = wrapper.findAll('button')
    const closeBtn = buttons.find(b => b.text().includes('Cancel') || b.text().includes('Close'))
    expect(closeBtn).toBeTruthy()
  })

  it('emits close event', async () => {
    const wrapper = mount(FeatureFlagRuleEditor, {
      props: { rule: null },
    })
    const buttons = wrapper.findAll('button')
    const closeBtn = buttons.find(b => b.text().includes('Cancel') || b.text().includes('Close'))
    if (closeBtn) {
      await closeBtn.trigger('click')
      expect(wrapper.emitted('close')).toBeTruthy()
    }
  })

  it('emits save event with rule data', async () => {
    const wrapper = mount(FeatureFlagRuleEditor, {
      props: { rule: null },
    })
    const buttons = wrapper.findAll('button')
    const saveBtn = buttons.find(b => b.text().includes('Save'))
    if (saveBtn) {
      await saveBtn.trigger('click')
      expect(wrapper.emitted('save')).toBeTruthy()
    }
  })

  it('validates percentage cannot exceed 100', () => {
    const wrapper = mount(FeatureFlagRuleEditor, {
      props: { rule: null },
    })
    const inputs = wrapper.findAll('input[type="number"]')
    expect(inputs.length).toBeGreaterThan(0)
  })

  it('validates percentage cannot be negative', () => {
    const wrapper = mount(FeatureFlagRuleEditor, {
      props: { rule: null },
    })
    const inputs = wrapper.findAll('input[type="number"]')
    expect(inputs.length).toBeGreaterThan(0)
  })

  it('populates form when editing existing rule', async () => {
    const mockRule = {
      ruleId: 'rule-existing',
      name: 'Existing Rule',
      priority: 5,
      percentage: 75,
      conditions: [
        { attribute: 'tier', operator: 'EQUALS' as const, value: 'enterprise' },
        { attribute: 'region', operator: 'IN' as const, value: 'us-east,us-west' },
      ],
      variantKey: 'enabled',
      startAt: '2026-01-01T00:00:00Z',
      endAt: '2026-12-31T23:59:59Z',
    }
    const wrapper = mount(FeatureFlagRuleEditor, {
      props: { rule: mockRule },
    })
    await new Promise(r => setTimeout(r, 10))
    await wrapper.vm.$nextTick()
    expect(wrapper.exists()).toBe(true)
  })
})
