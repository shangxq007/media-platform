import { describe, it, expect, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import EffectParameterEditor from './EffectParameterEditor.vue'
import type { EffectParameterDef } from '@/types'

describe('EffectParameterEditor', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('renders float parameter with slider and number input', () => {
    const def: EffectParameterDef = {
      type: 'float',
      defaultValue: 2.5,
      min: 0,
      max: 10,
      description: 'Blur radius'
    }
    const wrapper = mount(EffectParameterEditor, {
      props: { parameterName: 'radius', definition: def, modelValue: 2.5 }
    })
    expect(wrapper.find('input[type="range"]').exists()).toBe(true)
    expect(wrapper.find('input[type="number"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('radius')
  })

  it('renders int parameter with slider and number input', () => {
    const def: EffectParameterDef = {
      type: 'int',
      defaultValue: 5,
      min: 0,
      max: 100,
      description: 'Count'
    }
    const wrapper = mount(EffectParameterEditor, {
      props: { parameterName: 'count', definition: def, modelValue: 5 }
    })
    expect(wrapper.find('input[type="range"]').exists()).toBe(true)
    expect(wrapper.find('input[type="number"]').exists()).toBe(true)
  })

  it('renders boolean parameter with toggle', () => {
    const def: EffectParameterDef = {
      type: 'boolean',
      defaultValue: false,
      description: 'Enable'
    }
    const wrapper = mount(EffectParameterEditor, {
      props: { parameterName: 'enabled', definition: def, modelValue: true }
    })
    expect(wrapper.text()).toContain('On')
  })

  it('renders string parameter with text input', () => {
    const def: EffectParameterDef = {
      type: 'string',
      defaultValue: '',
      description: 'Text content'
    }
    const wrapper = mount(EffectParameterEditor, {
      props: { parameterName: 'text', definition: def, modelValue: 'Hello' }
    })
    const input = wrapper.find('input[type="text"]')
    expect(input.exists()).toBe(true)
    expect((input.element as HTMLInputElement).value).toBe('Hello')
  })

  it('renders color parameter with color picker and text input', () => {
    const def: EffectParameterDef = {
      type: 'color',
      defaultValue: '#ffffff',
      description: 'Color'
    }
    const wrapper = mount(EffectParameterEditor, {
      props: { parameterName: 'color', definition: def, modelValue: '#ff0000' }
    })
    expect(wrapper.find('input[type="color"]').exists()).toBe(true)
    expect(wrapper.find('input[type="text"]').exists()).toBe(true)
  })

  it('emits update on slider change for float', async () => {
    const def: EffectParameterDef = {
      type: 'float',
      defaultValue: 1.0,
      min: 0,
      max: 5,
      description: 'Duration'
    }
    const wrapper = mount(EffectParameterEditor, {
      props: { parameterName: 'duration', definition: def, modelValue: 1.0 }
    })
    const slider = wrapper.find('input[type="range"]')
    await slider.setValue('3.5')
    expect(wrapper.emitted('update:modelValue')).toBeTruthy()
  })

  it('emits update on text input change', async () => {
    const def: EffectParameterDef = {
      type: 'string',
      defaultValue: '',
      description: 'Text'
    }
    const wrapper = mount(EffectParameterEditor, {
      props: { parameterName: 'text', definition: def, modelValue: '' }
    })
    const input = wrapper.find('input[type="text"]')
    await input.setValue('New text')
    expect(wrapper.emitted('update:modelValue')).toBeTruthy()
    expect(wrapper.emitted('update:modelValue')![0]).toEqual(['New text'])
  })

  it('shows parameter description info icon', () => {
    const def: EffectParameterDef = {
      type: 'float',
      defaultValue: 0,
      min: 0,
      max: 1,
      description: 'Some helpful description'
    }
    const wrapper = mount(EffectParameterEditor, {
      props: { parameterName: 'opacity', definition: def, modelValue: 0.5 }
    })
    expect(wrapper.text()).toContain('ⓘ')
  })

  it('shows Off state for boolean false', () => {
    const def: EffectParameterDef = {
      type: 'boolean',
      defaultValue: false,
      description: 'Toggle'
    }
    const wrapper = mount(EffectParameterEditor, {
      props: { parameterName: 'toggle', definition: def, modelValue: false }
    })
    expect(wrapper.text()).toContain('Off')
  })
})
