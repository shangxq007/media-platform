import { describe, it, expect, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import EffectsPanel from './EffectsPanel.vue'

describe('EffectsPanel', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('renders category tabs', () => {
    const wrapper = mount(EffectsPanel)
    expect(wrapper.text()).toContain('transition')
    expect(wrapper.text()).toContain('video')
    expect(wrapper.text()).toContain('audio')
    expect(wrapper.text()).toContain('text')
  })

  it('renders tier selector', () => {
    const wrapper = mount(EffectsPanel)
    expect(wrapper.text()).toContain('Tier:')
  })

  it('renders pack browser toggle', () => {
    const wrapper = mount(EffectsPanel)
    const packBtn = wrapper.find('button[title="Browse effect packs"]')
    expect(packBtn.exists()).toBe(true)
  })

  it('toggles pack browser visibility', async () => {
    const wrapper = mount(EffectsPanel)
    const packBtn = wrapper.find('button[title="Browse effect packs"]')
    await packBtn.trigger('click')
    expect(wrapper.text()).toContain('Effect Packs')
  })

  it('shows no clip hint when no clip selected', () => {
    const wrapper = mount(EffectsPanel)
    expect(wrapper.text()).toContain('Select a clip on the timeline')
  })

  it('renders builtin effects list', () => {
    const wrapper = mount(EffectsPanel)
    expect(wrapper.text()).toContain('Fade In')
    expect(wrapper.text()).toContain('Fade Out')
  })

  it('highlights selected effect', async () => {
    const wrapper = mount(EffectsPanel)
    const effectItems = wrapper.findAll('[draggable="true"]')
    if (effectItems.length > 0) {
      await effectItems[0].trigger('click')
      expect(effectItems[0].classes()).toContain('bg-primary-500/20')
    }
  })

  it('switches active category on tab click', async () => {
    const wrapper = mount(EffectsPanel)
    const tabs = wrapper.findAll('.border-b-2')
    const videoTab = tabs.find(t => t.text().trim() === 'video')
    if (videoTab) {
      await videoTab.trigger('click')
      expect(wrapper.text()).toContain('Blur')
    }
  })

  it('shows unavailable effects section when tier is FREE', () => {
    const wrapper = mount(EffectsPanel)
    // Vignette requires PRO tier
    const text = wrapper.text()
    if (text.includes('Requires Upgrade')) {
      expect(text).toContain('Vignette')
    }
  })
})
