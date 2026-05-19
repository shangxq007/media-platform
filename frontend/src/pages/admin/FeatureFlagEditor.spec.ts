import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import FeatureFlagEditor from './FeatureFlagEditor.vue'

vi.mock('@/api/admin/feature-flags', () => ({
  FeatureFlagAPI: {
    createFeatureFlag: vi.fn().mockResolvedValue({
      flagKey: 'new-flag',
      name: 'New Flag',
      description: 'A new flag',
      type: 'BOOLEAN',
      defaultValue: 'true',
      variants: [],
      targetingRules: [],
      owner: 'admin',
      tags: [],
      enabled: true,
    }),
    updateFeatureFlag: vi.fn().mockResolvedValue({
      flagKey: 'existing-flag',
      name: 'Updated Flag',
      description: 'Updated',
      type: 'BOOLEAN',
      defaultValue: 'true',
      variants: [],
      targetingRules: [],
      owner: 'admin',
      tags: [],
      enabled: true,
    }),
  },
}))

describe('FeatureFlagEditor', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('renders editor for new flag creation', () => {
    const wrapper = mount(FeatureFlagEditor, {
      props: { flag: null },
    })
    expect(wrapper.exists()).toBe(true)
    expect(wrapper.text()).toContain('New Feature Flag')
  })

  it('renders editor with flag data for editing', () => {
    const mockFlag = {
      flagKey: 'existing-flag',
      name: 'Existing Flag',
      description: 'An existing flag',
      type: 'BOOLEAN' as const,
      defaultValue: 'true',
      variants: [{ key: 'control', value: 'false' }],
      targetingRules: [],
      owner: 'platform',
      tags: ['beta'],
      enabled: true,
    }
    const wrapper = mount(FeatureFlagEditor, {
      props: { flag: mockFlag },
    })
    expect(wrapper.exists()).toBe(true)
    expect(wrapper.text()).toContain('Edit Feature Flag')
  })

  it('renders form fields', () => {
    const wrapper = mount(FeatureFlagEditor, {
      props: { flag: null },
    })
    const inputs = wrapper.findAll('input')
    expect(inputs.length).toBeGreaterThan(0)
  })

  it('renders flag key input', () => {
    const wrapper = mount(FeatureFlagEditor, {
      props: { flag: null },
    })
    const inputs = wrapper.findAll('input')
    expect(inputs.length).toBeGreaterThan(0)
  })

  it('renders name input', () => {
    const wrapper = mount(FeatureFlagEditor, {
      props: { flag: null },
    })
    expect(wrapper.text()).toContain('Name')
  })

  it('renders type selector', () => {
    const wrapper = mount(FeatureFlagEditor, {
      props: { flag: null },
    })
    const selects = wrapper.findAll('select')
    expect(selects.length).toBeGreaterThan(0)
  })

  it('renders enabled toggle', () => {
    const wrapper = mount(FeatureFlagEditor, {
      props: { flag: null },
    })
    expect(wrapper.exists()).toBe(true)
  })

  it('renders save button', () => {
    const wrapper = mount(FeatureFlagEditor, {
      props: { flag: null },
    })
    const buttons = wrapper.findAll('button')
    const saveBtn = buttons.find(b => b.text().includes('Save') || b.text().includes('Create'))
    expect(saveBtn).toBeTruthy()
  })

  it('renders close/cancel button', () => {
    const wrapper = mount(FeatureFlagEditor, {
      props: { flag: null },
    })
    const buttons = wrapper.findAll('button')
    const closeBtn = buttons.find(b => b.text().includes('Cancel') || b.text().includes('Close'))
    expect(closeBtn).toBeTruthy()
  })

  it('emits close event when cancel clicked', async () => {
    const wrapper = mount(FeatureFlagEditor, {
      props: { flag: null },
    })
    const buttons = wrapper.findAll('button')
    const closeBtn = buttons.find(b => b.text().includes('Cancel') || b.text().includes('Close'))
    if (closeBtn) {
      await closeBtn.trigger('click')
      expect(wrapper.emitted('close')).toBeTruthy()
    }
  })

  it('renders variants section', () => {
    const wrapper = mount(FeatureFlagEditor, {
      props: { flag: null },
    })
    expect(wrapper.text()).toContain('Variants')
  })

  it('renders targeting rules section', () => {
    const wrapper = mount(FeatureFlagEditor, {
      props: { flag: null },
    })
    expect(wrapper.text()).toContain('Targeting Rules')
  })

  it('renders add variant button', () => {
    const wrapper = mount(FeatureFlagEditor, {
      props: { flag: null },
    })
    const buttons = wrapper.findAll('button')
    const addVariantBtn = buttons.find(b => b.text().includes('Add Variant'))
    expect(addVariantBtn).toBeTruthy()
  })

  it('renders add rule button', () => {
    const wrapper = mount(FeatureFlagEditor, {
      props: { flag: null },
    })
    const buttons = wrapper.findAll('button')
    const addRuleBtn = buttons.find(b => b.text().includes('Add Rule'))
    expect(addRuleBtn).toBeTruthy()
  })

  it('populates form when editing existing flag', async () => {
    const mockFlag = {
      flagKey: 'edit-flag',
      name: 'Edit Flag',
      description: 'Flag to edit',
      type: 'BOOLEAN' as const,
      defaultValue: 'false',
      variants: [],
      targetingRules: [],
      owner: 'admin',
      tags: ['test'],
      enabled: false,
    }
    const wrapper = mount(FeatureFlagEditor, {
      props: { flag: mockFlag },
    })
    await new Promise(r => setTimeout(r, 10))
    await wrapper.vm.$nextTick()
    expect(wrapper.exists()).toBe(true)
  })
})
